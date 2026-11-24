package com.sonora.music

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sonora.music.data.local.SonoraStore
import com.sonora.music.data.network.AudiobookApi
import com.sonora.music.data.network.LyricsApi
import com.sonora.music.data.network.MusicCatalogApi
import com.sonora.music.data.network.PodcastApi
import com.sonora.music.data.network.RadioApi
import com.sonora.music.data.repository.RecommendationEngine
import com.sonora.music.download.DownloadManager
import com.sonora.music.localfiles.LocalAudioScanner
import com.sonora.music.model.AppTab
import com.sonora.music.model.Artist
import com.sonora.music.model.AudioSettings
import com.sonora.music.model.DetailRoute
import com.sonora.music.model.DetailState
import com.sonora.music.model.EqPreset
import com.sonora.music.model.LibraryState
import com.sonora.music.model.ListeningEvent
import com.sonora.music.model.Playlist
import com.sonora.music.model.SearchCategory
import com.sonora.music.model.SonoraActions
import com.sonora.music.model.SonoraUiState
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import com.sonora.music.player.PlaybackController
import com.sonora.music.ui.theme.EmeraldAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs

class SonoraViewModel(application: Application) : AndroidViewModel(application), SonoraActions {

    private val store = SonoraStore(application)
    private val catalogApi = MusicCatalogApi()
    private val lyricsApi = LyricsApi()
    private val podcastApi = PodcastApi()
    private val audiobookApi = AudiobookApi()
    private val radioApi = RadioApi()
    private val recommendationEngine = RecommendationEngine(catalogApi)
    private val downloadManager = DownloadManager(
        context = application,
        store = store,
        trackResolver = { catalogApi.resolvePlayableTrack(it) }
    )
    private val localAudioScanner = LocalAudioScanner(application)

    private val playbackController = PlaybackController(
        context = application,
        downloadManager = downloadManager,
        smartPickProvider = { seeds, excludeIds ->
            recommendationEngine.getSmartShufflePicks(seeds, excludeIds, count = 5)
        },
        onTrackListened = { track, listenedMs ->
            recordListeningEvent(track, listenedMs)
        },
        trackResolver = { catalogApi.resolvePlayableTrack(it) }
    )

    private val _uiState = MutableStateFlow(SonoraUiState())
    val uiState: StateFlow<SonoraUiState> = _uiState.asStateFlow()

    private var permissionRequester: (() -> Unit)? = null
    private var initialLoadJob: Job? = null
    private var homeJob: Job? = null
    private var preWarmJob: Job? = null
    private var searchJob: Job? = null
    private var genreJob: Job? = null
    private var detailJob: Job? = null
    private var lyricsJob: Job? = null
    private var accentJob: Job? = null
    private var activeTrackMetaId: String? = null

    init {
        initialLoadJob = loadInitialLocalState()
        observePlaybackAndDownloads()
        playbackController.connect()
        refreshHome()
        refreshOutputDevices()
        if (hasLocalAudioPermission()) {
            scanLocalFiles()
        }
    }

    fun registerLocalPermissionRequester(requester: () -> Unit) {
        permissionRequester = requester
    }

    fun onLocalAudioPermissionResult(granted: Boolean) {
        _uiState.update { state ->
            state.copy(library = state.library.copy(hasLocalAudioPermission = granted))
        }
        if (granted) {
            scanLocalFiles()
        } else {
            showSnackbar("Storage permission is needed to scan local audio files")
        }
    }

    fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme.equals("sonora", ignoreCase = true) &&
            data.host.equals("playlist", ignoreCase = true)
        ) {
            val raw = data.toString()
            _uiState.update {
                it.copy(
                    isFullPlayerOpen = false,
                    isWrappedOpen = false,
                    isLyricsOpen = false,
                    isQueueOpen = false,
                    isAudioStudioOpen = false,
                    isAiGeneratorOpen = false
                )
            }
            importPlaylistCode(raw, mergeIntoJamQueue = false)
            selectTab(AppTab.LIBRARY)
        }
    }

    private fun loadInitialLocalState(): Job = viewModelScope.launch(Dispatchers.IO) {
        val playlists = store.loadPlaylists()
        val liked = store.loadLikedSongs()
        val followed = store.loadFollowedArtistIds()
        val recentSearches = store.loadRecentSearches()
        val audioSettings = store.loadAudioSettings()
        val history = store.loadHistory()
        val wrapped = recommendationEngine.computeWrapped(history, liked)
        val hasPerm = hasLocalAudioPermission()

        val initialRecentTracks = history
            .asReversed()
            .distinctBy { it.trackId }
            .take(15)
            .map { ev ->
                val inferredSource = when {
                    ev.trackId.startsWith("dz_") -> TrackSource.DEEZER
                    ev.trackId.startsWith("it_") -> TrackSource.ITUNES
                    ev.trackId.startsWith("pod_") -> TrackSource.PODCAST
                    ev.trackId.startsWith("ab_") -> TrackSource.AUDIOBOOK
                    ev.trackId.startsWith("radio_") -> TrackSource.RADIO
                    ev.trackId.startsWith("local_") -> TrackSource.LOCAL
                    else -> TrackSource.DEEZER
                }
                Track(
                    id = ev.trackId,
                    title = ev.title,
                    artist = ev.artist,
                    genre = ev.genre,
                    artworkUrl = ev.artworkUrl,
                    source = inferredSource,
                    isLive = inferredSource == TrackSource.RADIO,
                    isPreviewOnly = false
                )
            }

        _uiState.update { state ->
            state.copy(
                audioSettings = audioSettings,
                wrappedStats = wrapped,
                home = state.home.copy(recentlyPlayed = initialRecentTracks),
                search = state.search.copy(recentSearches = recentSearches),
                library = state.library.copy(
                    userPlaylists = playlists,
                    likedSongs = liked,
                    followedArtistIds = followed,
                    hasLocalAudioPermission = hasPerm,
                    downloadedTracks = downloadManager.downloadedTracks.value,
                    totalDownloadedBytes = downloadManager.totalStorageBytes()
                ),
                downloads = downloadManager.downloadsState.value
            )
        }

        withContext(Dispatchers.Main) {
            playbackController.setAudioSettings(audioSettings)
        }
    }

    private fun observePlaybackAndDownloads() {
        viewModelScope.launch {
            playbackController.playbackState.collect { pb ->
                val newTrack = pb.currentTrack
                val trackChanged = newTrack?.id != activeTrackMetaId
                if (trackChanged) {
                    activeTrackMetaId = newTrack?.id
                    onCurrentTrackChanged(newTrack)
                }
                _uiState.update { state ->
                    val updatedDetail = if (state.detail.route is DetailRoute.JamQueue) {
                        state.detail.copy(tracks = pb.queue)
                    } else {
                        state.detail
                    }
                    state.copy(
                        playback = pb,
                        detail = updatedDetail
                    )
                }
            }
        }

        viewModelScope.launch {
            downloadManager.downloadsState.collect { dlMap ->
                _uiState.update { state ->
                    state.copy(
                        downloads = dlMap,
                        library = state.library.copy(
                            totalDownloadedBytes = downloadManager.totalStorageBytes()
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            downloadManager.downloadedTracks.collect { dlTracks ->
                _uiState.update { state ->
                    val updatedDetail = if (state.detail.route is DetailRoute.Downloads) {
                        state.detail.copy(tracks = dlTracks)
                    } else {
                        state.detail
                    }
                    state.copy(
                        library = state.library.copy(
                            downloadedTracks = dlTracks,
                            totalDownloadedBytes = downloadManager.totalStorageBytes()
                        ),
                        detail = updatedDetail
                    )
                }
            }
        }
    }

    private fun onCurrentTrackChanged(track: Track?) {
        lyricsJob?.cancel()
        accentJob?.cancel()

        if (track == null) {
            _uiState.update {
                it.copy(
                    currentLyrics = null,
                    isLoadingLyrics = false,
                    dominantAccent = EmeraldAccent
                )
            }
            return
        }

        // Fetch lyrics asynchronously
        _uiState.update { it.copy(currentLyrics = null, isLoadingLyrics = true) }
        lyricsJob = viewModelScope.launch {
            val result = runCatching { lyricsApi.fetchLyrics(track) }.getOrNull()
            _uiState.update {
                it.copy(currentLyrics = result, isLoadingLyrics = false)
            }
        }

        // Extract dynamic vibrant accent color from artwork
        accentJob = viewModelScope.launch {
            val accent = extractAccentColor(track)
            _uiState.update { it.copy(dominantAccent = accent) }
        }
    }

    private suspend fun extractAccentColor(track: Track): Color = withContext(Dispatchers.IO) {
        val candidates = buildList {
            track.artworkUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(track.artworkMirrors.filter { it.isNotBlank() })
        }.distinct()

        val imageLoader = Coil.imageLoader(getApplication())
        for (url in candidates) {
            val bitmap = runCatching {
                val request = ImageRequest.Builder(getApplication())
                    .data(url)
                    .allowHardware(false)
                    .size(96, 96)
                    .build()
                val result = imageLoader.execute(request)
                val drawable = (result as? SuccessResult)?.drawable
                when (drawable) {
                    is BitmapDrawable -> drawable.bitmap
                    null -> null
                    else -> {
                        val w = drawable.intrinsicWidth.coerceIn(1, 96)
                        val h = drawable.intrinsicHeight.coerceIn(1, 96)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                            val canvas = Canvas(bmp)
                            drawable.setBounds(0, 0, w, h)
                            drawable.draw(canvas)
                        }
                    }
                }
            }.getOrNull()

            if (bitmap != null) {
                val dominant = sampleVibrantColor(bitmap)
                if (dominant != null) return@withContext dominant
            }
        }

        // Fallback muted hue from track title
        val palette = listOf(
            EmeraldAccent,
            Color(0xFF4EA8DE),
            Color(0xFF7B68EE),
            Color(0xFFE94F64),
            Color(0xFFE0A458)
        )
        palette[abs(track.title.hashCode()) % palette.size]
    }

    private fun sampleVibrantColor(bitmap: Bitmap): Color? {
        val stepX = (bitmap.width / 16).coerceAtLeast(1)
        val stepY = (bitmap.height / 16).coerceAtLeast(1)
        val bucketCounts = HashMap<Int, Int>()
        val bucketColors = HashMap<Int, Int>()
        val hsv = FloatArray(3)

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha >= 180) {
                    android.graphics.Color.colorToHSV(pixel, hsv)
                    val sat = hsv[1]
                    val value = hsv[2]
                    if (sat >= 0.22f && value in 0.20f..0.96f) {
                        val qH = (hsv[0] / 15f).toInt()
                        val qS = (sat * 4f).toInt()
                        val key = (qH shl 4) or qS
                        bucketCounts[key] = (bucketCounts[key] ?: 0) + 1
                        if (!bucketColors.containsKey(key)) {
                            bucketColors[key] = pixel
                        }
                    }
                }
                x += stepX
            }
            y += stepY
        }

        val bestEntry = bucketCounts.maxByOrNull { (key, count) ->
            val sample = bucketColors[key] ?: 0
            android.graphics.Color.colorToHSV(sample, hsv)
            count * (0.4f + 0.6f * hsv[1])
        } ?: return null

        val bestPixel = bucketColors[bestEntry.key] ?: return null
        android.graphics.Color.colorToHSV(bestPixel, hsv)
        hsv[1] = hsv[1].coerceIn(0.38f, 0.78f)
        hsv[2] = hsv[2].coerceIn(0.48f, 0.84f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun recordListeningEvent(track: Track, listenedMs: Long) {
        if (_uiState.value.audioSettings.privateSession) return
        viewModelScope.launch(Dispatchers.IO) {
            val event = ListeningEvent(
                trackId = track.id,
                title = track.title,
                artist = track.displayArtist,
                genre = track.genre.ifBlank { "Music" },
                artworkUrl = track.artworkUrl,
                listenedMs = listenedMs
            )
            store.appendHistory(event)
            val history = store.loadHistory()
            val liked = _uiState.value.library.likedSongs
            val wrapped = recommendationEngine.computeWrapped(history, liked)
            _uiState.update { state ->
                val recent = (listOf(track) + state.home.recentlyPlayed.filterNot { it.id == track.id }).take(15)
                state.copy(
                    wrappedStats = wrapped,
                    home = state.home.copy(recentlyPlayed = recent)
                )
            }
        }
    }

    override fun playTrack(track: Track, queue: List<Track>) {
        val effectiveQueue = if (queue.any { it.id == track.id }) queue else listOf(track) + queue
        val startIndex = effectiveQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackController.playQueue(effectiveQueue, startIndex)

        if (!_uiState.value.audioSettings.privateSession) {
            _uiState.update { state ->
                val updatedRecent = (listOf(track) + state.home.recentlyPlayed.filterNot { it.id == track.id }).take(15)
                state.copy(home = state.home.copy(recentlyPlayed = updatedRecent))
            }
        }
    }

    override fun togglePlayPause() = playbackController.togglePlayPause()

    override fun skipNext() = playbackController.skipNext()

    override fun skipPrev() = playbackController.skipPrev()

    override fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    override fun toggleShuffle() = playbackController.toggleShuffle()

    override fun cycleRepeat() = playbackController.cycleRepeat()

    override fun playNext(track: Track) {
        playbackController.playNext(track)
        showSnackbar("Playing next: ${track.title}")
    }

    override fun addToQueue(track: Track) {
        playbackController.addToQueue(track)
        showSnackbar("Added to queue: ${track.title}")
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackController.moveQueueItem(fromIndex, toIndex)
    }

    override fun removeQueueItem(index: Int) {
        playbackController.removeQueueItem(index)
    }

    override fun clearQueue() {
        playbackController.clearUpNext()
        showSnackbar("Cleared Up Next queue")
    }

    override fun startRadio(seedTrack: Track) {
        showSnackbar("Tuning ${seedTrack.title} Radio...")
        viewModelScope.launch {
            val radioTracks = recommendationEngine.buildTrackRadio(seedTrack)
            val fullQueue = listOf(seedTrack) + radioTracks.filterNot { it.id == seedTrack.id }
            if (fullQueue.size > 1) {
                playbackController.playQueue(fullQueue, 0)
                showSnackbar("Playing ${seedTrack.title} Radio (${fullQueue.size} tracks)")
            } else {
                playbackController.playQueue(listOf(seedTrack), 0)
                showSnackbar("Couldn't expand radio right now")
            }
        }
    }

    override fun startArtistRadio(artist: Artist) {
        showSnackbar("Tuning ${artist.name} Radio...")
        viewModelScope.launch {
            val tracks = recommendationEngine.buildArtistRadio(artist)
            if (tracks.isNotEmpty()) {
                playbackController.playQueue(tracks, 0)
                showSnackbar("Playing ${artist.name} Radio")
            } else {
                showSnackbar("No radio tracks available for ${artist.name}")
            }
        }
    }

    override fun toggleLike(track: Track) {
        val current = _uiState.value.library.likedSongs
        val exists = current.any { it.id == track.id }
        val updated = if (exists) {
            current.filterNot { it.id == track.id }
        } else {
            listOf(track) + current
        }

        viewModelScope.launch(Dispatchers.IO) {
            store.saveLikedSongs(updated)
            val wrapped = recommendationEngine.computeWrapped(store.loadHistory(), updated)
            _uiState.update { state ->
                val updatedDetail = if (state.detail.route is DetailRoute.LikedSongs) {
                    state.detail.copy(tracks = updated)
                } else {
                    state.detail
                }
                state.copy(
                    library = state.library.copy(likedSongs = updated),
                    wrappedStats = wrapped,
                    detail = updatedDetail
                )
            }
        }
    }

    override fun toggleFollowArtist(artist: Artist) {
        val current = _uiState.value.library.followedArtistIds
        val updated = if (artist.id in current) current - artist.id else current + artist.id
        _uiState.update { it.copy(library = it.library.copy(followedArtistIds = updated)) }
        viewModelScope.launch(Dispatchers.IO) {
            store.saveFollowedArtistIds(updated)
        }
        val msg = if (artist.id in updated) "Following ${artist.name}" else "Unfollowed ${artist.name}"
        showSnackbar(msg)
    }

    override fun createPlaylist(
        name: String,
        description: String,
        colorStart: Long,
        colorEnd: Long,
        initialTrack: Track?
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val newPlaylist = Playlist(
            id = "pl_${UUID.randomUUID()}",
            name = cleanName,
            description = description.trim(),
            trackIds = initialTrack?.let { listOf(it.id) } ?: emptyList(),
            tracks = initialTrack?.let { listOf(it) } ?: emptyList(),
            coverColorStart = colorStart,
            coverColorEnd = colorEnd,
            artworkUrl = initialTrack?.artworkUrl,
            isUserCreated = true
        )
        val updated = listOf(newPlaylist) + _uiState.value.library.userPlaylists
        _uiState.update { it.copy(library = it.library.copy(userPlaylists = updated)) }
        viewModelScope.launch(Dispatchers.IO) { store.savePlaylists(updated) }
        showSnackbar("Created playlist \"$cleanName\"")
    }

    override fun renamePlaylist(playlistId: String, newName: String, newDescription: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        val updated = _uiState.value.library.userPlaylists.map { pl ->
            if (pl.id == playlistId) pl.copy(name = cleanName, description = newDescription.trim()) else pl
        }
        _uiState.update { state ->
            val currentRoute = state.detail.route
            val updatedRoute = if (currentRoute is DetailRoute.PlaylistDetail && currentRoute.playlist.id == playlistId) {
                DetailRoute.PlaylistDetail(
                    currentRoute.playlist.copy(name = cleanName, description = newDescription.trim())
                )
            } else {
                currentRoute
            }
            val updatedPlaylist = state.detail.playlist?.let {
                if (it.id == playlistId) it.copy(name = cleanName, description = newDescription.trim()) else it
            }
            state.copy(
                library = state.library.copy(userPlaylists = updated),
                detail = state.detail.copy(route = updatedRoute, playlist = updatedPlaylist)
            )
        }
        viewModelScope.launch(Dispatchers.IO) { store.savePlaylists(updated) }
        showSnackbar("Updated \"$cleanName\"")
    }

    override fun deletePlaylist(playlistId: String) {
        val updated = _uiState.value.library.userPlaylists.filterNot { it.id == playlistId }
        _uiState.update { state ->
            val isViewingDeleted = (state.detail.route as? DetailRoute.PlaylistDetail)?.playlist?.id == playlistId
            state.copy(
                library = state.library.copy(userPlaylists = updated),
                detail = if (isViewingDeleted) DetailState() else state.detail
            )
        }
        viewModelScope.launch(Dispatchers.IO) { store.savePlaylists(updated) }
        showSnackbar("Playlist deleted")
    }

    override fun addTrackToPlaylist(playlistId: String, track: Track) {
        var addedName: String? = null
        val updated = _uiState.value.library.userPlaylists.map { pl ->
            if (pl.id == playlistId) {
                addedName = pl.name
                if (pl.tracks.any { it.id == track.id }) {
                    pl
                } else {
                    val nextTracks = pl.tracks + track
                    pl.copy(
                        tracks = nextTracks,
                        trackIds = nextTracks.map { it.id },
                        artworkUrl = pl.artworkUrl ?: track.artworkUrl
                    )
                }
            } else {
                pl
            }
        }
        syncPlaylistUpdate(playlistId, updated)
        showSnackbar("Added to ${addedName ?: "playlist"}")
    }

    override fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val updated = _uiState.value.library.userPlaylists.map { pl ->
            if (pl.id == playlistId) {
                val nextTracks = pl.tracks.filterNot { it.id == trackId }
                pl.copy(tracks = nextTracks, trackIds = nextTracks.map { it.id })
            } else {
                pl
            }
        }
        syncPlaylistUpdate(playlistId, updated)
    }

    override fun reorderPlaylistTrack(playlistId: String, fromIndex: Int, toIndex: Int) {
        val updated = _uiState.value.library.userPlaylists.map { pl ->
            if (pl.id == playlistId && fromIndex in pl.tracks.indices && toIndex in pl.tracks.indices) {
                val mutable = pl.tracks.toMutableList()
                val moved = mutable.removeAt(fromIndex)
                mutable.add(toIndex, moved)
                pl.copy(tracks = mutable, trackIds = mutable.map { it.id })
            } else {
                pl
            }
        }
        syncPlaylistUpdate(playlistId, updated)
    }

    private fun syncPlaylistUpdate(playlistId: String, updatedPlaylists: List<Playlist>) {
        val matching = updatedPlaylists.firstOrNull { it.id == playlistId }
        _uiState.update { state ->
            val currentRoute = state.detail.route
            val isViewing = currentRoute is DetailRoute.PlaylistDetail && currentRoute.playlist.id == playlistId
            state.copy(
                library = state.library.copy(userPlaylists = updatedPlaylists),
                detail = if (isViewing && matching != null) {
                    state.detail.copy(
                        route = DetailRoute.PlaylistDetail(matching),
                        playlist = matching,
                        tracks = matching.tracks
                    )
                } else {
                    state.detail
                }
            )
        }
        viewModelScope.launch(Dispatchers.IO) { store.savePlaylists(updatedPlaylists) }
    }

    override fun exportPlaylistCode(playlist: Playlist): String {
        return store.encodePlaylistCode(
            name = playlist.name,
            description = playlist.description,
            tracks = playlist.tracks,
            colorStart = playlist.coverColorStart,
            colorEnd = playlist.coverColorEnd
        )
    }

    override fun exportQueueCode(): String {
        val queue = _uiState.value.playback.queue
        return store.encodePlaylistCode(
            name = "Sonora Jam Queue",
            description = "Collaborative Jam Session",
            tracks = queue,
            colorStart = 0xFF1ED760L,
            colorEnd = 0xFF0A2E2AL
        )
    }

    override fun importPlaylistCode(code: String, mergeIntoJamQueue: Boolean) {
        val decoded = store.decodePlaylistCode(code)
        if (decoded == null || decoded.tracks.isEmpty()) {
            showSnackbar("Invalid or empty Sonora playlist code")
            return
        }
        if (mergeIntoJamQueue) {
            if (_uiState.value.playback.currentTrack == null) {
                playbackController.playQueue(decoded.tracks, 0)
            } else {
                playbackController.appendTracks(decoded.tracks)
            }
            showSnackbar("Merged ${decoded.tracks.size} tracks into Jam Queue")
        } else {
            val imported = decoded.copy(
                id = "pl_${UUID.randomUUID()}",
                isUserCreated = true,
                isSystemMix = false,
                createdAt = System.currentTimeMillis()
            )
            val updated = listOf(imported) + _uiState.value.library.userPlaylists
            _uiState.update { it.copy(library = it.library.copy(userPlaylists = updated)) }
            viewModelScope.launch(Dispatchers.IO) { store.savePlaylists(updated) }
            showSnackbar("Imported \"${imported.name}\" (${imported.tracks.size} tracks)")
        }
    }

    override fun downloadTrack(track: Track) {
        downloadManager.downloadTrack(track)
        showSnackbar("Downloading \"${track.title}\" for offline playback")
    }

    override fun downloadPlaylist(tracks: List<Track>) {
        val downloadable = tracks.filter {
            !it.isLive &&
                it.source != TrackSource.LOCAL &&
                (it.streamUrl.isNotBlank() || it.source == TrackSource.DEEZER || it.source == TrackSource.ITUNES)
        }
        if (downloadable.isEmpty()) {
            showSnackbar("No downloadable tracks in this list")
            return
        }
        downloadManager.downloadPlaylist(downloadable)
        showSnackbar("Downloading ${downloadable.size} tracks for offline listening")
    }

    override fun deleteDownload(trackId: String) {
        downloadManager.deleteDownload(trackId)
        showSnackbar("Removed offline download")
    }

    override fun deleteAllDownloads() {
        downloadManager.deleteAllDownloads()
        showSnackbar("Cleared all offline downloads")
    }

    override fun scanLocalFiles() {
        if (!hasLocalAudioPermission()) {
            requestLocalAudioPermission()
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(library = it.library.copy(isScanningLocal = true, hasLocalAudioPermission = true))
            }
            val found = localAudioScanner.scanAudioFiles()
            _uiState.update { state ->
                val updatedDetail = if (state.detail.route is DetailRoute.LocalFiles) {
                    state.detail.copy(tracks = found, isLoading = false)
                } else {
                    state.detail
                }
                state.copy(
                    library = state.library.copy(localTracks = found, isScanningLocal = false),
                    detail = updatedDetail
                )
            }
        }
    }

    override fun requestLocalAudioPermission() {
        if (hasLocalAudioPermission()) {
            scanLocalFiles()
        } else {
            permissionRequester?.invoke()
        }
    }

    override fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(search = it.search.copy(query = query)) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    search = it.search.copy(
                        isSearching = false,
                        songs = emptyList(),
                        artists = emptyList(),
                        playlists = emptyList(),
                        podcasts = emptyList(),
                        audiobooks = emptyList(),
                        radioStations = emptyList()
                    )
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(350)
            executeSearch(query.trim(), saveToHistoryAfterSettle = true)
        }
    }

    private suspend fun executeSearch(cleanQuery: String, saveToHistoryAfterSettle: Boolean) {
        _uiState.update { it.copy(search = it.search.copy(isSearching = true)) }
        supervisorScope {
            val songsDef = async { runCatching { catalogApi.searchTracks(cleanQuery, limit = 30) }.getOrDefault(emptyList()) }
            val artistsDef = async { runCatching { catalogApi.searchArtists(cleanQuery, limit = 15) }.getOrDefault(emptyList()) }
            val playlistsDef = async { runCatching { catalogApi.searchPlaylists(cleanQuery, limit = 15) }.getOrDefault(emptyList()) }
            val podcastsDef = async { runCatching { podcastApi.searchPodcasts(cleanQuery, limit = 16, country = "PH") }.getOrDefault(emptyList()) }
            val booksDef = async { runCatching { audiobookApi.searchAudiobooks(cleanQuery, limit = 16) }.getOrDefault(emptyList()) }
            val radioDef = async { runCatching { radioApi.searchStations(cleanQuery, limit = 20) }.getOrDefault(emptyList()) }

            val songs = songsDef.await()
            val artists = artistsDef.await()
            val playlists = playlistsDef.await()
            val podcasts = podcastsDef.await()
            val books = booksDef.await()
            val radios = radioDef.await()

            _uiState.update { state ->
                state.copy(
                    search = state.search.copy(
                        isSearching = false,
                        songs = songs,
                        artists = artists,
                        playlists = playlists,
                        podcasts = podcasts,
                        audiobooks = books,
                        radioStations = radios
                    )
                )
            }
        }

        if (saveToHistoryAfterSettle && cleanQuery.length >= 2) {
            delay(850)
            recordRecentSearch(cleanQuery)
        }
    }

    private fun recordRecentSearch(query: String) {
        val current = _uiState.value.search.recentSearches
        val updated = (listOf(query) + current.filterNot { it.equals(query, ignoreCase = true) }).take(12)
        _uiState.update { it.copy(search = it.search.copy(recentSearches = updated)) }
        viewModelScope.launch(Dispatchers.IO) {
            store.saveRecentSearches(updated)
        }
    }

    override fun selectSearchCategory(category: SearchCategory) {
        _uiState.update { it.copy(search = it.search.copy(category = category)) }
    }

    override fun applyRecentSearch(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(search = it.search.copy(query = query)) }
        searchJob = viewModelScope.launch {
            executeSearch(query.trim(), saveToHistoryAfterSettle = false)
            recordRecentSearch(query.trim())
        }
    }

    override fun removeRecentSearch(query: String) {
        val updated = _uiState.value.search.recentSearches.filterNot { it == query }
        _uiState.update { it.copy(search = it.search.copy(recentSearches = updated)) }
        viewModelScope.launch(Dispatchers.IO) {
            store.saveRecentSearches(updated)
        }
    }

    override fun openAiGenerator(initialPrompt: String) {
        _uiState.update {
            it.copy(
                isAiGeneratorOpen = true,
                search = it.search.copy(
                    aiPrompt = initialPrompt.ifBlank { it.search.aiPrompt },
                    generatedAiPlaylist = null
                )
            )
        }
    }

    override fun closeAiGenerator() {
        _uiState.update { it.copy(isAiGeneratorOpen = false) }
    }

    override fun generateAiPlaylist(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank()) return
        _uiState.update {
            it.copy(
                search = it.search.copy(
                    aiPrompt = clean,
                    isGeneratingAiPlaylist = true,
                    generatedAiPlaylist = null
                )
            )
        }
        viewModelScope.launch {
            val generated = recommendationEngine.generateAiPlaylist(clean)
            _uiState.update {
                it.copy(
                    search = it.search.copy(
                        isGeneratingAiPlaylist = false,
                        generatedAiPlaylist = generated
                    )
                )
            }
        }
    }

    override fun saveGeneratedPlaylist(playlist: Playlist) {
        val toSave = playlist.copy(
            id = "pl_${UUID.randomUUID()}",
            isUserCreated = true,
            isSystemMix = false
        )
        val updated = listOf(toSave) + _uiState.value.library.userPlaylists
        _uiState.update {
            it.copy(
                library = it.library.copy(userPlaylists = updated),
                isAiGeneratorOpen = false
            )
        }
        viewModelScope.launch(Dispatchers.IO) { store.savePlaylists(updated) }
        showSnackbar("Saved \"${toSave.name}\" to Library")
    }

    override fun openDetail(route: DetailRoute) {
        detailJob?.cancel()
        when (route) {
            DetailRoute.None -> {
                _uiState.update { it.copy(detail = DetailState()) }
            }

            is DetailRoute.PlaylistDetail -> {
                val latestUserPlaylist = _uiState.value.library.userPlaylists.firstOrNull {
                    it.id == route.playlist.id
                } ?: route.playlist
                val needsFetch = latestUserPlaylist.tracks.isEmpty() && !latestUserPlaylist.isUserCreated
                _uiState.update {
                    it.copy(
                        detail = DetailState(
                            route = DetailRoute.PlaylistDetail(latestUserPlaylist),
                            isLoading = needsFetch,
                            playlist = latestUserPlaylist,
                            tracks = latestUserPlaylist.tracks
                        )
                    )
                }
                if (needsFetch) {
                    detailJob = viewModelScope.launch {
                        val fetched = runCatching {
                            catalogApi.getPlaylistTracks(latestUserPlaylist.id)
                        }.getOrDefault(emptyList())
                        val hydrated = latestUserPlaylist.copy(
                            tracks = fetched,
                            trackIds = fetched.map { t -> t.id }
                        )
                        _uiState.update {
                            it.copy(
                                detail = it.detail.copy(
                                    isLoading = false,
                                    playlist = hydrated,
                                    tracks = fetched
                                )
                            )
                        }
                    }
                }
            }

            is DetailRoute.ArtistDetail -> {
                _uiState.update {
                    it.copy(
                        detail = DetailState(
                            route = route,
                            isLoading = true,
                            artist = route.artist
                        )
                    )
                }
                detailJob = viewModelScope.launch {
                    val tracks = runCatching {
                        catalogApi.getArtistTopTracks(
                            artistId = route.artist.id,
                            artistName = route.artist.name,
                            limit = 25
                        )
                    }.getOrDefault(emptyList())
                    _uiState.update {
                        it.copy(detail = it.detail.copy(isLoading = false, tracks = tracks))
                    }
                }
            }

            is DetailRoute.PodcastDetail -> {
                _uiState.update {
                    it.copy(
                        detail = DetailState(
                            route = route,
                            isLoading = true,
                            podcast = route.show
                        )
                    )
                }
                detailJob = viewModelScope.launch {
                    val episodes = podcastApi.getEpisodes(route.show, limit = 40)
                    _uiState.update {
                        it.copy(detail = it.detail.copy(isLoading = false, tracks = episodes))
                    }
                }
            }

            is DetailRoute.AudiobookDetail -> {
                _uiState.update {
                    it.copy(
                        detail = DetailState(
                            route = route,
                            isLoading = true,
                            audiobook = route.book
                        )
                    )
                }
                detailJob = viewModelScope.launch {
                    val chapters = audiobookApi.getChapters(route.book)
                    _uiState.update {
                        it.copy(detail = it.detail.copy(isLoading = false, tracks = chapters))
                    }
                }
            }

            DetailRoute.LikedSongs -> {
                _uiState.update {
                    it.copy(
                        detail = DetailState(
                            route = route,
                            isLoading = false,
                            tracks = it.library.likedSongs
                        )
                    )
                }
            }

            DetailRoute.Downloads -> {
                _uiState.update {
                    it.copy(
                        detail = DetailState(
                            route = route,
                            isLoading = false,
                            tracks = it.library.downloadedTracks
                        )
                    )
                }
            }

            DetailRoute.LocalFiles -> {
                _uiState.update {
                    it.copy(
                        detail = DetailState(
                            route = route,
                            isLoading = false,
                            tracks = it.library.localTracks
                        )
                    )
                }
                if (_uiState.value.library.localTracks.isEmpty() && hasLocalAudioPermission()) {
                    scanLocalFiles()
                }
            }

            DetailRoute.JamQueue -> {
                _uiState.update {
                    it.copy(
                        detail = DetailState(
                            route = route,
                            isLoading = false,
                            tracks = it.playback.queue
                        )
                    )
                }
            }
        }
    }

    override fun closeDetail() {
        detailJob?.cancel()
        _uiState.update { it.copy(detail = DetailState()) }
    }

    override fun selectTab(tab: AppTab) {
        _uiState.update {
            it.copy(
                currentTab = tab,
                detail = DetailState()
            )
        }
    }

    override fun dismissIntroOverlay() {
        _uiState.update { it.copy(showIntroOverlay = false) }
    }

    override fun openFullPlayer() {
        if (_uiState.value.playback.currentTrack != null) {
            refreshOutputDevices()
            _uiState.update { it.copy(isFullPlayerOpen = true) }
        }
    }

    override fun closeFullPlayer() {
        _uiState.update { it.copy(isFullPlayerOpen = false) }
    }

    override fun openLyrics() {
        _uiState.update { it.copy(isLyricsOpen = true) }
    }

    override fun closeLyrics() {
        _uiState.update { it.copy(isLyricsOpen = false) }
    }

    override fun openQueue() {
        _uiState.update { it.copy(isQueueOpen = true) }
    }

    override fun closeQueue() {
        _uiState.update { it.copy(isQueueOpen = false) }
    }

    override fun openAudioStudio() {
        refreshOutputDevices()
        _uiState.update { it.copy(isAudioStudioOpen = true) }
    }

    override fun closeAudioStudio() {
        _uiState.update { it.copy(isAudioStudioOpen = false) }
    }

    override fun openWrapped() {
        viewModelScope.launch(Dispatchers.IO) {
            val wrapped = recommendationEngine.computeWrapped(
                store.loadHistory(),
                _uiState.value.library.likedSongs
            )
            _uiState.update { it.copy(wrappedStats = wrapped, isWrappedOpen = true) }
        }
    }

    override fun closeWrapped() {
        _uiState.update { it.copy(isWrappedOpen = false) }
    }

    override fun updateAudioSettings(transform: (AudioSettings) -> AudioSettings) {
        val updated = transform(_uiState.value.audioSettings)
        _uiState.update { it.copy(audioSettings = updated) }
        playbackController.setAudioSettings(updated)
        viewModelScope.launch(Dispatchers.IO) {
            store.saveAudioSettings(updated)
        }
    }

    override fun applyEqPreset(preset: EqPreset) {
        updateAudioSettings {
            it.copy(
                eqEnabled = true,
                eqPreset = preset,
                bandLevelsMb = preset.bandOffsetsMb
            )
        }
    }

    override fun setSleepTimer(minutes: Int, endOfTrack: Boolean) {
        playbackController.setSleepTimer(minutes, endOfTrack)
        val msg = if (endOfTrack) "Sleep timer set for end of track" else "Sleep timer set for ${minutes}m"
        showSnackbar(msg)
    }

    override fun cancelSleepTimer() {
        playbackController.cancelSleepTimer()
        showSnackbar("Sleep timer cancelled")
    }

    override fun openOutputPicker() {
        playbackController.openSystemOutputSwitcher()
        refreshOutputDevices()
    }

    override fun refreshOutputDevices() {
        val devices = playbackController.getOutputDevices()
        _uiState.update { it.copy(outputDevices = devices) }
    }

    override fun shareTrack(track: Track) {
        val shareLink = track.externalUrl.ifBlank { track.streamUrl }
        val body = buildString {
            append("Listening to \"${track.title}\" by ${track.displayArtist} on Sonora")
            if (shareLink.isNotBlank() && !track.isLive) {
                append("\n$shareLink")
            }
        }
        launchShareSheet("Share Track", body)
    }

    override fun sharePlaylist(playlist: Playlist) {
        val code = exportPlaylistCode(playlist)
        val deepLink = "sonora://playlist?code=${Uri.encode(code)}"
        val body = "Listen to \"${playlist.name}\" on Sonora!\nOpen link: $deepLink\n\nOr import code:\n$code"
        launchShareSheet("Share Playlist", body)
    }

    override fun shareWrapped() {
        val stats = _uiState.value.wrappedStats
        val topTrack = stats.topTracks.firstOrNull()?.name ?: "Discovering new waves"
        val topArtist = stats.topArtists.firstOrNull()?.name ?: "Independent Artists"
        val body = buildString {
            appendLine("My Sonora Sound Profile: ${stats.personaTitle}")
            appendLine("${stats.totalMinutes} mins • ${stats.totalPlays} plays • ${stats.uniqueArtists} artists")
            appendLine("Top Artist: $topArtist")
            append("Top Track: $topTrack")
        }
        launchShareSheet("Share Sonora Wrapped", body)
    }

    private fun launchShareSheet(subject: String, text: String) {
        val app = getApplication<Application>()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { app.startActivity(chooser) }
    }

    override fun selectHomeFilter(filter: String) {
        _uiState.update { it.copy(home = it.home.copy(selectedFilter = filter)) }
    }

    override fun openFullTrackExternal(track: Track) {
        val rawQuery = listOf(track.title, track.displayArtist)
            .filter { it.isNotBlank() && it != "Featured Artist" }
            .joinToString(" ")
        val encodedQuery = Uri.encode(rawQuery)
        val url = "https://www.youtube.com/results?search_query=$encodedQuery"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            getApplication<Application>().startActivity(intent)
            true
        }.getOrDefault(false)
        if (!launched) {
            showSnackbar("No browser app found to open full song")
        }
    }

    override fun loadMoreGenre(genre: String) {
        genreJob?.cancel()
        _uiState.update { it.copy(home = it.home.copy(selectedGenre = genre)) }
        if (genre.equals("All", ignoreCase = true)) {
            _uiState.update {
                it.copy(home = it.home.copy(genreTracks = it.home.phTopTracks.ifEmpty { it.home.trendingTracks }))
            }
            return
        }
        if (genre.equals("OPM", ignoreCase = true) && _uiState.value.home.opmTracks.isNotEmpty()) {
            _uiState.update {
                it.copy(home = it.home.copy(genreTracks = it.home.opmTracks))
            }
            return
        }
        genreJob = viewModelScope.launch {
            val tracks = runCatching { catalogApi.getGenreTracks(genre, limit = 30) }.getOrDefault(emptyList())
            _uiState.update { state ->
                if (state.home.selectedGenre == genre) {
                    state.copy(home = state.home.copy(genreTracks = tracks))
                } else {
                    state
                }
            }
        }
    }

    override fun refreshHome() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _uiState.update { it.copy(home = it.home.copy(isLoading = true, errorMessage = null)) }
            val offlineNow = !isNetworkConnected()

            supervisorScope {
                val phTopDef = async { runCatching { catalogApi.getPhTopTracks(40) }.getOrDefault(emptyList()) }
                val opmDef = async { runCatching { catalogApi.getOpmTrendingTracks(35) }.getOrDefault(emptyList()) }
                val globalTopDef = async { runCatching { catalogApi.getGlobalTopTracks(40) }.getOrDefault(emptyList()) }
                val phArtistsDef = async { runCatching { catalogApi.getPhTrendingArtists() }.getOrDefault(emptyList()) }
                val globalArtistsDef = async { runCatching { catalogApi.getGlobalTrendingArtists() }.getOrDefault(emptyList()) }
                val playlistsDef = async { runCatching { catalogApi.getFeaturedPlaylists() }.getOrDefault(emptyList()) }
                val podcastsDef = async {
                    runCatching { podcastApi.searchPodcasts("philippines", limit = 16, country = "PH") }.getOrDefault(emptyList())
                }
                val audiobooksDef = async { runCatching { audiobookApi.searchAudiobooks("", limit = 16) }.getOrDefault(emptyList()) }
                val radioDef = async { runCatching { radioApi.getPhStations(20) }.getOrDefault(emptyList()) }

                initialLoadJob?.join()
                val currentState = _uiState.value
                val history = withContext(Dispatchers.IO) { store.loadHistory() }
                val madeForYouDef = async {
                    runCatching {
                        recommendationEngine.buildMadeForYou(
                            history = history,
                            likedSongs = currentState.library.likedSongs,
                            followedArtistIds = currentState.library.followedArtistIds
                        )
                    }.getOrDefault(emptyList())
                }

                val phTopTracks = phTopDef.await()
                val opmTracks = opmDef.await()
                val globalTopTracks = globalTopDef.await()
                val phArtists = phArtistsDef.await()
                val globalArtists = globalArtistsDef.await()
                val playlists = playlistsDef.await()
                val podcasts = podcastsDef.await()
                val audiobooks = audiobooksDef.await()
                val liveRadio = radioDef.await()
                val madeForYou = madeForYouDef.await()

                val selectedGenre = _uiState.value.home.selectedGenre
                val genreTracks = when {
                    selectedGenre.equals("All", ignoreCase = true) -> phTopTracks
                    selectedGenre.equals("OPM", ignoreCase = true) -> opmTracks
                    else -> runCatching { catalogApi.getGenreTracks(selectedGenre, limit = 30) }.getOrDefault(emptyList())
                }

                val latestState = _uiState.value
                val quickPicks = (
                    latestState.home.recentlyPlayed.take(2) +
                        latestState.library.likedSongs.take(2) +
                        phTopTracks.take(6)
                    )
                    .distinctBy { it.id }
                    .take(6)

                val allRemoteEmpty = phTopTracks.isEmpty() &&
                    opmTracks.isEmpty() &&
                    globalTopTracks.isEmpty() &&
                    playlists.isEmpty() &&
                    podcasts.isEmpty() &&
                    liveRadio.isEmpty()

                _uiState.update { state ->
                    state.copy(
                        home = state.home.copy(
                            isLoading = false,
                            isOffline = offlineNow || allRemoteEmpty,
                            quickPicks = quickPicks,
                            madeForYou = madeForYou,
                            phTopTracks = phTopTracks,
                            opmTracks = opmTracks,
                            phArtists = phArtists,
                            globalTopTracks = globalTopTracks,
                            globalArtists = globalArtists,
                            trendingTracks = phTopTracks,
                            genreTracks = genreTracks,
                            trendingPlaylists = playlists,
                            podcasts = podcasts,
                            audiobooks = audiobooks,
                            liveRadio = liveRadio,
                            errorMessage = if (allRemoteEmpty) "Offline mode — saved downloads & local tracks ready" else null
                        )
                    )
                }

                preWarmQuickPicks(quickPicks)
            }
        }
    }

    private fun preWarmQuickPicks(quickPicks: List<Track>) {
        preWarmJob?.cancel()
        val targets = quickPicks
            .filter { it.source == TrackSource.DEEZER || it.source == TrackSource.ITUNES }
            .take(6)
        if (targets.isEmpty()) return

        preWarmJob = viewModelScope.launch(Dispatchers.IO) {
            supervisorScope {
                targets.chunked(2).forEach { chunk ->
                    chunk.map { track ->
                        async {
                            try {
                                catalogApi.resolvePlayableTrack(track)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Throwable) {
                                null
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }

    override fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    private fun hasLocalAudioPermission(): Boolean {
        val app = getApplication<Application>()
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNetworkConnected(): Boolean {
        val app = getApplication<Application>()
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onCleared() {
        super.onCleared()
        playbackController.release()
    }
}
