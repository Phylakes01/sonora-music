package com.sonora.music.download

import android.content.Context
import com.sonora.music.data.local.SonoraStore
import com.sonora.music.data.network.HttpClientProvider
import com.sonora.music.model.DownloadState
import com.sonora.music.model.DownloadStatus
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import com.sonora.music.player.QueueEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class DownloadManager(
    private val context: Context,
    private val store: SonoraStore,
    private val client: OkHttpClient = HttpClientProvider.client,
    private val trackResolver: suspend (Track) -> Track = { it }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val stateLock = Any()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val concurrencySemaphore = Semaphore(3)

    private val _downloadsState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadsState: StateFlow<Map<String, DownloadState>> = _downloadsState.asStateFlow()

    private val _downloadedTracks = MutableStateFlow<List<Track>>(emptyList())
    val downloadedTracks: StateFlow<List<Track>> = _downloadedTracks.asStateFlow()

    private val _downloadErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val downloadErrors: SharedFlow<String> = _downloadErrors.asSharedFlow()

    init {
        cleanupPartialFiles()
        restorePersistedDownloads()
    }

    fun getOfflineFile(trackId: String): File? {
        if (trackId.isBlank()) return null
        val file = targetFile(trackId)
        return if (file.exists() && file.length() > 0L) file else null
    }

    fun downloadTrack(track: Track) {
        if (track.isLive || track.source == TrackSource.LOCAL) {
            return
        }
        if ((track.source == TrackSource.PODCAST || track.source == TrackSource.AUDIOBOOK) &&
            track.streamUrl.isBlank()
        ) {
            _downloadErrors.tryEmit("Full song stream unavailable for ${track.title}")
            return
        }
        val existingFile = getOfflineFile(track.id)
        if (existingFile != null) {
            val persisted = _downloadedTracks.value.firstOrNull { it.id == track.id } ?: track
            if (!isLegacyPreviewDownload(persisted, existingFile)) {
                ensureTrackRecorded(persisted, existingFile)
                return
            }
            existingFile.delete()
        }
        if (activeJobs[track.id]?.isActive == true) {
            return
        }

        updateTrackState(
            track.id,
            DownloadState(status = DownloadStatus.DOWNLOADING, progress = 0f)
        )

        val job = scope.launch {
            try {
                concurrencySemaphore.withPermit {
                    performDownload(track)
                }
            } catch (_: CancellationException) {
                partFile(track.id).delete()
                removeTrackState(track.id)
            } catch (t: Throwable) {
                partFile(track.id).delete()
                updateTrackState(
                    track.id,
                    DownloadState(status = DownloadStatus.FAILED, progress = 0f)
                )
                _downloadErrors.tryEmit(
                    t.message?.takeIf { it.isNotBlank() }
                        ?: "Full song stream unavailable for ${track.title}"
                )
            } finally {
                activeJobs.remove(track.id)
            }
        }
        activeJobs[track.id] = job
    }

    fun downloadPlaylist(tracks: List<Track>) {
        tracks.forEach { track ->
            downloadTrack(track)
        }
    }

    fun deleteDownload(trackId: String) {
        activeJobs.remove(trackId)?.cancel()
        targetFile(trackId).delete()
        partFile(trackId).delete()

        synchronized(stateLock) {
            val updated = _downloadedTracks.value.filterNot { it.id == trackId }
            _downloadedTracks.value = updated
            runCatching { store.saveDownloadedTracks(updated) }
            _downloadsState.update { current -> current - trackId }
        }
    }

    fun deleteAllDownloads() {
        val jobs = activeJobs.values.toList()
        activeJobs.clear()
        jobs.forEach { it.cancel() }

        downloadsDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }

        synchronized(stateLock) {
            _downloadedTracks.value = emptyList()
            runCatching { store.saveDownloadedTracks(emptyList()) }
            _downloadsState.value = emptyMap()
        }
    }

    fun totalStorageBytes(): Long {
        val files = downloadsDir.listFiles() ?: return 0L
        return files.sumOf { if (it.isFile && !it.name.endsWith(".part")) it.length() else 0L }
    }

    private suspend fun performDownload(track: Track) {
        val part = partFile(track.id)
        val dest = targetFile(track.id)
        part.delete()

        val shouldResolve = track.streamUrl.isBlank() ||
            isThirtySecondPreviewUrl(track.streamUrl) ||
            track.source == TrackSource.DEEZER ||
            track.source == TrackSource.ITUNES
        val resolvedTrack = if (shouldResolve) {
            withContext(Dispatchers.IO) {
                try {
                    trackResolver(track)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    track
                }
            }
        } else {
            track
        }
        val resolvedUrl = when {
            resolvedTrack.streamUrl.startsWith("http", ignoreCase = true) &&
                !isThirtySecondPreviewUrl(resolvedTrack.streamUrl) -> resolvedTrack.streamUrl
            track.streamUrl.startsWith("http", ignoreCase = true) &&
                !isThirtySecondPreviewUrl(track.streamUrl) -> track.streamUrl
            else -> ""
        }
        if (resolvedUrl.isBlank() || isThirtySecondPreviewUrl(resolvedUrl)) {
            throw IOException("Full song stream unavailable for ${track.title}")
        }

        val request = Request.Builder()
            .url(resolvedUrl)
            .header("Accept", "*/*")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} downloading track ${track.id}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength()
            var bytesRead = 0L
            var lastReportedProgress = -1f
            var lastReportedTime = 0L

            body.byteStream().use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read

                        val now = System.currentTimeMillis()
                        val progress = if (totalBytes > 0L) {
                            (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                        } else {
                            0f
                        }
                        if (now - lastReportedTime >= 120L || progress - lastReportedProgress >= 0.03f) {
                            lastReportedTime = now
                            lastReportedProgress = progress
                            updateTrackState(
                                track.id,
                                DownloadState(
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = progress,
                                    fileSizeBytes = bytesRead
                                )
                            )
                        }
                    }
                    output.fd.sync()
                }
            }

            if (bytesRead <= 0L) {
                throw IOException("Downloaded zero bytes for track ${track.id}")
            }
        }

        if (dest.exists()) {
            dest.delete()
        }
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }

        val recordedTrack = resolvedTrack.copy(
            streamUrl = resolvedUrl,
            isPreviewOnly = false
        )
        ensureTrackRecorded(recordedTrack, dest)
    }

    private fun ensureTrackRecorded(track: Track, file: File) {
        val offlineTrack = track.copy(
            localUri = "file://${file.absolutePath}",
            isPreviewOnly = false
        )
        synchronized(stateLock) {
            val existing = _downloadedTracks.value.filterNot { it.id == track.id }
            val updated = listOf(offlineTrack) + existing
            _downloadedTracks.value = updated
            runCatching { store.saveDownloadedTracks(updated) }
            _downloadsState.update { current ->
                current + (
                    track.id to DownloadState(
                        status = DownloadStatus.DOWNLOADED,
                        progress = 1f,
                        fileSizeBytes = file.length(),
                        localPath = file.absolutePath
                    )
                )
            }
        }
    }

    private fun updateTrackState(trackId: String, state: DownloadState) {
        synchronized(stateLock) {
            _downloadsState.update { current -> current + (trackId to state) }
        }
    }

    private fun removeTrackState(trackId: String) {
        synchronized(stateLock) {
            _downloadsState.update { current -> current - trackId }
        }
    }

    private fun restorePersistedDownloads() {
        val saved = runCatching { store.loadDownloadedTracks() }.getOrDefault(emptyList())
        val valid = saved.filter { track ->
            val file = getOfflineFile(track.id) ?: return@filter false
            if (isLegacyPreviewDownload(track, file)) {
                runCatching { file.delete() }
                false
            } else {
                true
            }
        }
        if (valid.size != saved.size) {
            runCatching { store.saveDownloadedTracks(valid) }
        }
        _downloadedTracks.value = valid
        _downloadsState.value = valid.mapNotNull { track ->
            val file = getOfflineFile(track.id) ?: return@mapNotNull null
            track.id to DownloadState(
                status = DownloadStatus.DOWNLOADED,
                progress = 1f,
                fileSizeBytes = file.length(),
                localPath = file.absolutePath
            )
        }.toMap()
    }

    private fun isLegacyPreviewDownload(track: Track, file: File): Boolean {
        if (track.source == TrackSource.LOCAL ||
            track.source == TrackSource.PODCAST ||
            track.source == TrackSource.AUDIOBOOK ||
            track.source == TrackSource.RADIO ||
            track.isLive
        ) {
            return false
        }
        return track.isPreviewOnly ||
            isThirtySecondPreviewUrl(track.streamUrl) ||
            track.streamUrl.isBlank() ||
            track.source == TrackSource.DEEZER ||
            track.source == TrackSource.ITUNES ||
            file.length() < MIN_FULL_MUSIC_FILE_BYTES
    }

    private fun cleanupPartialFiles() {
        downloadsDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".part")) {
                runCatching { file.delete() }
            }
        }
    }

    private fun targetFile(trackId: String): File = File(downloadsDir, "${hashKey(trackId)}.mp3")

    private fun partFile(trackId: String): File = File(downloadsDir, "${hashKey(trackId)}.part")

    private fun hashKey(trackId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(trackId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MIN_FULL_MUSIC_FILE_BYTES = 700_000L

        fun isThirtySecondPreviewUrl(url: String): Boolean =
            QueueEngine.isThirtySecondPreviewUrl(url)
    }
}
