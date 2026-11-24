package com.sonora.music.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.sonora.music.download.DownloadManager
import com.sonora.music.model.AudioOutputDevice
import com.sonora.music.model.AudioSettings
import com.sonora.music.model.PlaybackState
import com.sonora.music.model.RepeatMode
import com.sonora.music.model.ShuffleMode
import com.sonora.music.model.SleepTimerState
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

object QueueEngine {

    fun shufflePreservingCurrent(
        queue: List<Track>,
        currentIndex: Int,
        random: Random = Random.Default
    ): List<Track> {
        if (queue.isEmpty()) return emptyList()
        if (currentIndex !in queue.indices) return queue.shuffled(random)
        val current = queue[currentIndex]
        val remaining = queue.filterIndexed { index, _ -> index != currentIndex }.shuffled(random)
        return listOf(current) + remaining
    }

    fun weaveSmartPicks(
        queue: List<Track>,
        currentIndex: Int,
        smartPicks: List<Track>,
        interval: Int = 2
    ): List<Track> {
        if (queue.isEmpty()) return smartPicks.distinctBy { it.id }.map { it.copy(isSmartPick = true) }

        val safeIndex = currentIndex.coerceIn(-1, queue.lastIndex)
        val prefixCount = if (safeIndex >= 0) safeIndex + 1 else 0
        val prefix = queue.take(prefixCount)
        val upNext = queue.drop(prefixCount).filterNot { it.isSmartPick }

        val existingIds = (prefix + upNext).mapTo(HashSet()) { it.id }
        val filteredPicks = smartPicks
            .asSequence()
            .filter { it.id.isNotBlank() && it.id !in existingIds }
            .distinctBy { it.id }
            .map { it.copy(isSmartPick = true) }
            .toList()

        if (filteredPicks.isEmpty()) return prefix + upNext

        val step = interval.coerceAtLeast(1)
        val woven = ArrayList<Track>(upNext.size + filteredPicks.size)
        var pickIdx = 0
        var sinceLastPick = 0

        for (track in upNext) {
            woven += track
            sinceLastPick++
            if (sinceLastPick >= step && pickIdx < filteredPicks.size) {
                woven += filteredPicks[pickIdx++]
                sinceLastPick = 0
            }
        }
        while (pickIdx < filteredPicks.size) {
            woven += filteredPicks[pickIdx++]
        }

        return prefix + woven
    }

    fun moveItem(queue: List<Track>, fromIndex: Int, toIndex: Int): List<Track> {
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) {
            return queue
        }
        val mutable = queue.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return mutable
    }

    fun computeAdjustedIndexOnMove(currentIndex: Int, fromIndex: Int, toIndex: Int): Int {
        return when {
            currentIndex < 0 -> currentIndex
            fromIndex == currentIndex -> toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
    }

    fun computeCrossfadeVolume(
        positionMs: Long,
        durationMs: Long,
        crossfadeSeconds: Int,
        fadeInEnabled: Boolean = true
    ): Float {
        if (crossfadeSeconds <= 0 || durationMs <= 0L) return 1f
        val fadeMs = (crossfadeSeconds * 1000L).coerceAtMost(durationMs / 2L)
        if (fadeMs <= 0L) return 1f

        val pos = positionMs.coerceIn(0L, durationMs)
        val remaining = (durationMs - pos).coerceAtLeast(0L)

        if (remaining < fadeMs) {
            val ratio = (remaining.toDouble() / fadeMs.toDouble()).coerceIn(0.0, 1.0)
            return sin(ratio * PI / 2.0).toFloat().coerceIn(0f, 1f)
        }
        if (fadeInEnabled && pos < fadeMs) {
            val ratio = (pos.toDouble() / fadeMs.toDouble()).coerceIn(0.0, 1.0)
            return sin(ratio * PI / 2.0).toFloat().coerceIn(0f, 1f)
        }
        return 1f
    }

    fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }

    fun nextShuffleMode(current: ShuffleMode): ShuffleMode = when (current) {
        ShuffleMode.OFF -> ShuffleMode.SHUFFLE
        ShuffleMode.SHUFFLE -> ShuffleMode.SMART_SHUFFLE
        ShuffleMode.SMART_SHUFFLE -> ShuffleMode.OFF
    }

    private val EXP_TOKEN_REGEX = Regex("""\bexp=(\d+)""")

    fun isStreamUrlFresh(streamUrl: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean {
        if (streamUrl.isBlank()) return false
        val match = EXP_TOKEN_REGEX.find(streamUrl) ?: return true
        val expSec = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return true
        return expSec > nowEpochSeconds + 60L
    }

    fun isThirtySecondPreviewUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.contains("dzcdn.net") ||
            lower.contains("audio-ssl.itunes.apple.com") ||
            lower.contains("itunes.apple.com") ||
            lower.contains("cdns-preview") ||
            lower.contains("cdnt-preview")
    }

    fun isPlayableFullStreamUrl(url: String): Boolean =
        url.isNotBlank() && !isThirtySecondPreviewUrl(url)

    fun isVideoOnlyTrack(track: Track): Boolean = track.isVideoOnly

    fun shouldPlayWithExoPlayer(track: Track): Boolean {
        if (track.isVideoOnly) return false
        return !track.localUri.isNullOrBlank() ||
            isPlayableFullStreamUrl(track.streamUrl) ||
            track.isLive
    }

    fun resolveNextQueueIndex(
        currentIndex: Int,
        queueSize: Int,
        repeatMode: RepeatMode
    ): Int? {
        if (queueSize <= 0) return null
        return when {
            currentIndex + 1 in 0 until queueSize -> currentIndex + 1
            repeatMode == RepeatMode.ALL -> 0
            else -> null
        }
    }

    fun resolvePrevQueueIndex(
        currentIndex: Int,
        queueSize: Int,
        repeatMode: RepeatMode
    ): Int? {
        if (queueSize <= 0) return null
        return when {
            currentIndex - 1 in 0 until queueSize -> currentIndex - 1
            repeatMode == RepeatMode.ALL -> queueSize - 1
            else -> null
        }
    }

    fun computeVideoTransitionState(
        previous: PlaybackState,
        queue: List<Track>,
        index: Int
    ): PlaybackState? {
        val track = queue.getOrNull(index) ?: return null
        return previous.copy(
            currentTrack = track,
            queue = queue,
            currentIndex = index,
            isPlaying = true,
            isBuffering = false,
            positionMs = 0L,
            durationMs = if (track.isLive) 0L else (track.durationSec * 1000L).coerceAtLeast(0L),
            errorMessage = null
        )
    }
}

class PlaybackController(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val smartPickProvider: suspend (List<Track>, Set<String>) -> List<Track>,
    private val onTrackListened: (Track, Long) -> Unit,
    private val trackResolver: suspend (Track) -> Track = { it },
    private val onVideoTrackSelected: (Track) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val effectsManager = AudioEffectsManager()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private val _activeAudioSessionId = MutableStateFlow(0)
    val activeAudioSessionId: StateFlow<Int> = _activeAudioSessionId.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var isReleased = false

    private var audioSettings = AudioSettings()
    private var originalQueue: List<Track> = emptyList()
    private var currentQueue: List<Track> = emptyList()
    private var currentIndex: Int = -1
    private var shuffleMode: ShuffleMode = ShuffleMode.OFF
    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var hasPendingPlayRequest: Boolean = false
    private var fadeInOnCurrentTrack = false

    private var positionJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var smartShuffleJob: Job? = null
    private var sessionObserverJob: Job? = null
    private var playResolveJob: Job? = null
    private var preResolveJob: Job? = null
    private var transitionResolveJob: Job? = null
    private var queueGeneration: Long = 0L
    private var isResolvingActiveTrack: Boolean = false
    private var controllerHasCurrentQueue: Boolean = false
    private val retriedStreamTrackIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val resolvedTrackIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val resolvedTracks = ConcurrentHashMap<String, Track>()
    private val unplayableTrackIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private var trackedTrack: Track? = null
    private var accumulatedListenMs: Long = 0L
    private var lastListenTickMs: Long = 0L
    private var hasReportedCurrentPlay: Boolean = false
    private var trackerPlaying: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isResolvingActiveTrack || !controllerHasCurrentQueue) return
            if (currentQueue.getOrNull(currentIndex)?.isVideoOnly == true) return
            updateListenTrackerPlaying(isPlaying)
            if (!isPlaying) {
                setPlayerVolume(1f)
            }
            syncPlaybackStateFromPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (isResolvingActiveTrack || !controllerHasCurrentQueue) return
            if (currentQueue.getOrNull(currentIndex)?.isVideoOnly == true) return
            if (playbackState == Player.STATE_ENDED) {
                flushListenTracker(forceIfMinimumMet = true)
                setPlayerVolume(1f)
                if (_playbackState.value.sleepTimer.endOfTrack) {
                    cancelSleepTimer()
                }
            }
            syncPlaybackStateFromPlayer()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isResolvingActiveTrack || !controllerHasCurrentQueue) return
            val controller = mediaController
            val newIdx = controller?.currentMediaItemIndex ?: -1
            val prevTrack = trackedTrack
            val nextTrack = currentQueue.getOrNull(newIdx)

            if (prevTrack != null && prevTrack.id != nextTrack?.id) {
                flushListenTracker(forceIfMinimumMet = true)
                retriedStreamTrackIds.remove(prevTrack.id)
            }

            if (newIdx in currentQueue.indices) {
                currentIndex = newIdx
            }

            if (nextTrack != null && nextTrack.isVideoOnly) {
                activateVideoOnlyTrack(newIdx, nextTrack, invokeCallback = true)
                return
            }

            resetListenTrackerFor(nextTrack, isPlayingNow = controller?.isPlaying == true)

            if (_playbackState.value.sleepTimer.endOfTrack &&
                (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            ) {
                controller?.pause()
                cancelSleepTimer()
            }

            fadeInOnCurrentTrack = (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            if (!fadeInOnCurrentTrack) {
                setPlayerVolume(1f)
            }

            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                _playbackState.update { it.copy(errorMessage = null) }
            }
            syncPlaybackStateFromPlayer()

            if (nextTrack != null && newIdx in currentQueue.indices) {
                val cachedUpgrade = if (!hasOfflineOrLocalFile(nextTrack) &&
                    (nextTrack.streamUrl.isBlank() || isThirtySecondPreviewUrl(nextTrack.streamUrl) || isRemoteResolvableTrack(nextTrack))
                ) {
                    resolvedTracks[nextTrack.id]?.takeIf {
                        isPlayableFullStreamUrl(it.streamUrl) && it.streamUrl != nextTrack.streamUrl
                    }
                } else {
                    null
                }

                if (cachedUpgrade != null && controller != null) {
                    val updated = updateResolvedTrackInQueues(cachedUpgrade)
                    if (newIdx < controller.mediaItemCount &&
                        controller.getMediaItemAt(newIdx).mediaId == nextTrack.id
                    ) {
                        controller.replaceMediaItem(newIdx, buildMediaItem(updated))
                        if (controller.currentMediaItemIndex == newIdx) {
                            controller.seekTo(newIdx, 0L)
                            controller.prepare()
                            controller.play()
                        }
                    }
                } else if (needsTransitionResolution(nextTrack) || !hasPlayableMedia(nextTrack)) {
                    if (!hasPlayableMedia(nextTrack) && controller != null) {
                        controller.pause()
                        isResolvingActiveTrack = true
                        _playbackState.update { it.copy(isPlaying = false, isBuffering = true) }
                    }
                    resolveAndUpdateTransitionTrack(newIdx, nextTrack, queueGeneration)
                }

                if (controller != null &&
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
                ) {
                    launchPreResolveNext(controller, newIdx, queueGeneration)
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (isResolvingActiveTrack || !controllerHasCurrentQueue) return
            if (currentQueue.getOrNull(currentIndex)?.isVideoOnly == true) return
            val idx = mediaController?.currentMediaItemIndex ?: -1
            if (idx in currentQueue.indices) {
                currentIndex = idx
            }
            syncPlaybackStateFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!controllerHasCurrentQueue) return
            setPlayerVolume(1f)
            val controller = mediaController
            val failedIndex = controller?.currentMediaItemIndex?.takeIf { it in currentQueue.indices } ?: currentIndex
            val failedTrack = currentQueue.getOrNull(failedIndex)
            if (failedTrack != null && failedTrack.isVideoOnly) {
                activateVideoOnlyTrack(failedIndex, failedTrack, invokeCallback = true)
                return
            }
            if (controller != null &&
                failedTrack != null &&
                !hasOfflineOrLocalFile(failedTrack) &&
                retriedStreamTrackIds.add(failedTrack.id)
            ) {
                _playbackState.update { it.copy(isBuffering = true, errorMessage = null) }
                retryTrackWithFreshUrl(controller, failedIndex, failedTrack, error, queueGeneration)
                return
            }

            val msg = if (failedTrack != null && !hasPlayableMedia(failedTrack)) {
                "Full song stream unavailable for ${failedTrack.title}"
            } else {
                error.localizedMessage ?: "Playback failed for this stream"
            }
            _errorEvents.tryEmit(msg)
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = msg
                )
            }
        }
    }

    fun connect() {
        if (isReleased || controllerFuture != null) return

        sessionObserverJob = scope.launch {
            SonoraPlayerBridge.audioSessionIdFlow.collect { sessionId ->
                _activeAudioSessionId.value = sessionId
                if (sessionId > 0) {
                    effectsManager.attachToSession(sessionId, audioSettings)
                }
            }
        }

        val sessionToken = SessionToken(
            context,
            ComponentName(context, SonoraPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val controller = future.get()
                    if (isReleased) {
                        controller.release()
                        return@addListener
                    }
                    mediaController = controller
                    controller.addListener(playerListener)
                    controller.repeatMode = toMedia3RepeatMode(repeatMode)
                    controller.setPlaybackSpeed(audioSettings.playbackSpeed.coerceIn(0.5f, 2.5f))

                    val sessionId = SonoraPlayerBridge.audioSessionIdFlow.value
                    if (sessionId > 0) {
                        _activeAudioSessionId.value = sessionId
                        effectsManager.attachToSession(sessionId, audioSettings)
                    }

                    if (hasPendingPlayRequest && currentQueue.isNotEmpty()) {
                        hasPendingPlayRequest = false
                        applyCurrentQueueToController(controller)
                    } else if (currentQueue.isEmpty() && controller.mediaItemCount > 0) {
                        restoreQueueFromController(controller)
                    } else {
                        syncPlaybackStateFromPlayer()
                    }

                    startPositionTicker()
                } catch (t: Throwable) {
                    _playbackState.update {
                        it.copy(errorMessage = t.localizedMessage ?: "Could not connect to playback service")
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun release() {
        if (isReleased) return
        isReleased = true

        flushListenTracker(forceIfMinimumMet = true)
        positionJob?.cancel()
        sleepTimerJob?.cancel()
        smartShuffleJob?.cancel()
        sessionObserverJob?.cancel()
        playResolveJob?.cancel()
        preResolveJob?.cancel()
        transitionResolveJob?.cancel()

        effectsManager.release()
        mediaController?.removeListener(playerListener)
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        scope.cancel()
    }

    fun playQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        startPausedForVideo: Boolean = false
    ) {
        if (tracks.isEmpty()) return

        queueGeneration++
        flushListenTracker(forceIfMinimumMet = true)
        smartShuffleJob?.cancel()
        playResolveJob?.cancel()
        preResolveJob?.cancel()
        transitionResolveJob?.cancel()
        retriedStreamTrackIds.clear()
        unplayableTrackIds.clear()

        val clampedStart = startIndex.coerceIn(0, tracks.lastIndex)
        val initialQueue = tracks.map(::applyCachedResolution)
        originalQueue = initialQueue

        val (targetQueue, targetIndex) = if (shuffleMode == ShuffleMode.OFF || startPausedForVideo) {
            initialQueue to clampedStart
        } else {
            QueueEngine.shufflePreservingCurrent(initialQueue, clampedStart) to 0
        }

        currentQueue = targetQueue
        currentIndex = targetIndex
        fadeInOnCurrentTrack = false
        val current = currentQueue.getOrNull(currentIndex)

        if (current != null && (current.isVideoOnly || startPausedForVideo)) {
            _playbackState.update {
                it.copy(
                    shuffleMode = shuffleMode,
                    repeatMode = repeatMode,
                    playbackSpeed = audioSettings.playbackSpeed
                )
            }
            activateVideoOnlyTrack(
                index = currentIndex,
                track = current,
                invokeCallback = current.isVideoOnly || startPausedForVideo
            )
            if (mediaController == null) {
                connect()
            }
            return
        }

        isResolvingActiveTrack = current != null && needsTrackResolution(current)
        resetListenTrackerFor(current, isPlayingNow = false)

        _playbackState.update {
            it.copy(
                currentTrack = current,
                queue = currentQueue,
                currentIndex = currentIndex,
                positionMs = 0L,
                durationMs = if (current == null || current.isLive) 0L else current.durationSec * 1000L,
                isPlaying = false,
                isBuffering = true,
                shuffleMode = shuffleMode,
                repeatMode = repeatMode,
                playbackSpeed = audioSettings.playbackSpeed,
                errorMessage = null
            )
        }

        val controller = mediaController
        if (controller == null) {
            hasPendingPlayRequest = true
            connect()
            return
        }

        if (controller.isPlaying || controller.playWhenReady) {
            controller.pause()
        }

        hasPendingPlayRequest = false
        applyCurrentQueueToController(controller)
    }

    private fun activateVideoOnlyTrack(
        index: Int,
        track: Track,
        invokeCallback: Boolean = true
    ) {
        isResolvingActiveTrack = false
        controllerHasCurrentQueue = false
        hasPendingPlayRequest = false
        mediaController?.pause()
        setPlayerVolume(1f)
        currentIndex = index
        resetListenTrackerFor(track, isPlayingNow = false)
        _playbackState.update { state ->
            QueueEngine.computeVideoTransitionState(state, currentQueue, index) ?: state
        }
        if (invokeCallback) {
            onVideoTrackSelected(track)
        }
    }

    private fun applyCurrentQueueToController(controller: MediaController) {
        val generation = queueGeneration
        val safeIndex = currentIndex.coerceIn(0, (currentQueue.size - 1).coerceAtLeast(0))
        val activeTrack = currentQueue.getOrNull(safeIndex) ?: return

        if (activeTrack.isVideoOnly) {
            activateVideoOnlyTrack(safeIndex, activeTrack, invokeCallback = true)
            return
        }

        if (needsTrackResolution(activeTrack)) {
            isResolvingActiveTrack = true
            playResolveJob?.cancel()
            playResolveJob = scope.launch {
                val effectiveTrack = resolveTrackWithCache(activeTrack)
                if (isReleased || generation != queueGeneration) return@launch
                if (currentQueue.getOrNull(safeIndex)?.id != activeTrack.id) return@launch

                isResolvingActiveTrack = false
                if (!hasPlayableMedia(effectiveTrack)) {
                    handleUnplayableTrack(controller, safeIndex, activeTrack, generation)
                    return@launch
                }

                updateResolvedTrackInQueues(effectiveTrack)
                startControllerQueue(controller, safeIndex)
                launchPreResolveNext(controller, safeIndex, generation)
            }
            return
        }

        isResolvingActiveTrack = false
        if (!hasPlayableMedia(activeTrack)) {
            handleUnplayableTrack(controller, safeIndex, activeTrack, generation)
            return
        }
        startControllerQueue(controller, safeIndex)
        launchPreResolveNext(controller, safeIndex, generation)
    }

    private fun handleUnplayableTrack(
        controller: MediaController,
        index: Int,
        track: Track,
        generation: Long
    ) {
        if (isReleased || generation != queueGeneration) return
        val errorMsg = "Full song stream unavailable for ${track.title}"
        unplayableTrackIds.add(track.id)
        _errorEvents.tryEmit(errorMsg)

        val nextCandidateIdx = ((index + 1)..currentQueue.lastIndex)
            .firstOrNull { currentQueue[it].id !in unplayableTrackIds }
        if (nextCandidateIdx != null) {
            val nextTrack = currentQueue[nextCandidateIdx]
            currentIndex = nextCandidateIdx
            resetListenTrackerFor(nextTrack, isPlayingNow = false)
            _playbackState.update {
                it.copy(
                    currentTrack = nextTrack,
                    currentIndex = nextCandidateIdx,
                    positionMs = 0L,
                    durationMs = if (nextTrack.isLive) 0L else nextTrack.durationSec * 1000L,
                    isPlaying = false,
                    isBuffering = true,
                    errorMessage = errorMsg
                )
            }
            applyCurrentQueueToController(controller)
        } else {
            isResolvingActiveTrack = false
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = errorMsg
                )
            }
        }
    }

    private fun startControllerQueue(controller: MediaController, safeIndex: Int) {
        val items = currentQueue.map(::buildMediaItem)
        if (items.isEmpty()) return
        val clamped = safeIndex.coerceIn(0, items.lastIndex)
        controllerHasCurrentQueue = true
        controller.setMediaItems(items, clamped, 0L)
        controller.repeatMode = toMedia3RepeatMode(repeatMode)
        controller.setPlaybackSpeed(audioSettings.playbackSpeed.coerceIn(0.5f, 2.5f))
        setPlayerVolume(1f)
        controller.prepare()
        controller.play()

        if (shuffleMode == ShuffleMode.SMART_SHUFFLE) {
            launchSmartShuffleWeave()
        }
    }

    private fun launchPreResolveNext(
        controller: MediaController,
        anchorIndex: Int,
        generation: Long
    ) {
        preResolveJob?.cancel()
        val endIndex = (anchorIndex + 3).coerceAtMost(currentQueue.lastIndex)
        if (anchorIndex + 1 > endIndex) return

        val snapshotTargets = (anchorIndex + 1..endIndex).mapNotNull { idx ->
            currentQueue.getOrNull(idx)?.let { idx to it }
        }
        if (snapshotTargets.isEmpty()) return

        preResolveJob = scope.launch {
            for ((expectedIdx, targetTrack) in snapshotTargets) {
                if (!isActive || isReleased || generation != queueGeneration) return@launch
                if (!needsTrackResolution(targetTrack)) continue

                val resolved = resolveTrackWithCache(targetTrack)
                if (!isActive || isReleased || generation != queueGeneration) return@launch

                val actualIdx = if (currentQueue.getOrNull(expectedIdx)?.id == targetTrack.id) {
                    expectedIdx
                } else {
                    currentQueue.indexOfFirst { it.id == targetTrack.id }
                }
                if (actualIdx !in currentQueue.indices) continue

                val previousTrack = currentQueue[actualIdx]
                val updatedTrack = updateResolvedTrackInQueues(resolved)

                val didUpgradeOrChange = updatedTrack.streamUrl != previousTrack.streamUrl ||
                    updatedTrack.isPreviewOnly != previousTrack.isPreviewOnly ||
                    updatedTrack.durationSec != previousTrack.durationSec

                if (didUpgradeOrChange &&
                    actualIdx < controller.mediaItemCount &&
                    controller.getMediaItemAt(actualIdx).mediaId == targetTrack.id
                ) {
                    val isCurrentlyPlayingThis = controller.currentMediaItemIndex == actualIdx
                    if (!isCurrentlyPlayingThis) {
                        controller.replaceMediaItem(actualIdx, buildMediaItem(updatedTrack))
                    } else if (hasPlayableMedia(updatedTrack) && updatedTrack.streamUrl != previousTrack.streamUrl) {
                        transitionResolveJob?.cancel()
                        controller.replaceMediaItem(actualIdx, buildMediaItem(updatedTrack))
                        controller.seekTo(actualIdx, 0L)
                        controller.prepare()
                        controller.play()
                    }
                }
            }
        }
    }

    private fun resolveAndUpdateTransitionTrack(index: Int, track: Track, generation: Long) {
        transitionResolveJob?.cancel()
        transitionResolveJob = scope.launch {
            val resolved = resolveTrackWithCache(track)
            if (isReleased || generation != queueGeneration) return@launch
            if (currentQueue.getOrNull(index)?.id != track.id) return@launch

            isResolvingActiveTrack = false
            val updatedTrack = updateResolvedTrackInQueues(resolved)
            val controller = mediaController ?: return@launch

            if (!hasPlayableMedia(updatedTrack)) {
                if (controller.currentMediaItemIndex == index) {
                    handleUnplayableTrack(controller, index, track, generation)
                }
                return@launch
            }

            val upgradedStream = updatedTrack.streamUrl != track.streamUrl || !hasPlayableMedia(track)
            if (!upgradedStream) {
                syncPlaybackStateFromPlayer()
                return@launch
            }

            if (index < controller.mediaItemCount &&
                controller.getMediaItemAt(index).mediaId == track.id
            ) {
                controller.replaceMediaItem(index, buildMediaItem(updatedTrack))
                if (controller.currentMediaItemIndex == index) {
                    controller.seekTo(index, 0L)
                    controller.prepare()
                    controller.play()
                }
            }
        }
    }

    private fun retryTrackWithFreshUrl(
        controller: MediaController,
        failedIndex: Int,
        failedTrack: Track,
        error: PlaybackException,
        generation: Long
    ) {
        playResolveJob?.cancel()
        isResolvingActiveTrack = true
        playResolveJob = scope.launch {
            resolvedTracks.remove(failedTrack.id)
            resolvedTrackIds.remove(failedTrack.id)
            val resolved = withContext(Dispatchers.IO) {
                try {
                    trackResolver(failedTrack)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    failedTrack.copy(streamUrl = "")
                }
            }
            if (isReleased || generation != queueGeneration) return@launch
            if (currentQueue.getOrNull(failedIndex)?.id != failedTrack.id) {
                isResolvingActiveTrack = false
                return@launch
            }

            isResolvingActiveTrack = false
            val validStream = isPlayableFullStreamUrl(resolved.streamUrl)
            if (validStream) {
                resolvedTrackIds.add(failedTrack.id)
                resolvedTracks[failedTrack.id] = resolved
            }

            if (validStream && resolved.streamUrl != failedTrack.streamUrl) {
                val updatedTrack = updateResolvedTrackInQueues(resolved)
                if (failedIndex < controller.mediaItemCount &&
                    controller.getMediaItemAt(failedIndex).mediaId == failedTrack.id
                ) {
                    controller.replaceMediaItem(failedIndex, buildMediaItem(updatedTrack))
                    controller.seekTo(failedIndex, 0L)
                    controller.prepare()
                    controller.play()
                    return@launch
                }
            }

            if (!validStream) {
                handleUnplayableTrack(controller, failedIndex, failedTrack, generation)
                return@launch
            }

            val msg = error.localizedMessage ?: "Playback failed for this stream"
            _errorEvents.tryEmit(msg)
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = msg
                )
            }
        }
    }

    private suspend fun resolveTrackWithCache(track: Track): Track {
        val cached = resolvedTracks[track.id]
        if (cached != null &&
            isPlayableFullStreamUrl(cached.streamUrl) &&
            QueueEngine.isStreamUrlFresh(cached.streamUrl)
        ) {
            resolvedTrackIds.add(track.id)
            return mergeResolvedTrack(track, cached)
        }

        val resolved = withContext(Dispatchers.IO) {
            try {
                trackResolver(track)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                track.copy(streamUrl = if (isThirtySecondPreviewUrl(track.streamUrl)) "" else track.streamUrl)
            }
        }

        if (isPlayableFullStreamUrl(resolved.streamUrl)) {
            resolvedTrackIds.add(track.id)
            resolvedTracks[track.id] = resolved
            return mergeResolvedTrack(track, resolved)
        }

        return mergeResolvedTrack(track, resolved.copy(streamUrl = "", isPreviewOnly = false))
    }

    private fun applyCachedResolution(track: Track): Track {
        if (hasOfflineOrLocalFile(track)) return track
        val cached = resolvedTracks[track.id] ?: return track
        if (!isPlayableFullStreamUrl(cached.streamUrl)) return track
        if (!QueueEngine.isStreamUrlFresh(cached.streamUrl)) return track
        return mergeResolvedTrack(track, cached)
    }

    private fun mergeResolvedTrack(original: Track, resolved: Track): Track {
        val resolvedStream = resolved.streamUrl.takeIf { isPlayableFullStreamUrl(it) }
        val originalStream = original.streamUrl.takeIf { isPlayableFullStreamUrl(it) }
        val effectiveStream = resolvedStream ?: originalStream.orEmpty()
        return original.copy(
            streamUrl = effectiveStream,
            source = if (resolvedStream != null) resolved.source else original.source,
            durationSec = maxOf(original.durationSec, resolved.durationSec),
            isPreviewOnly = if (effectiveStream.isNotBlank()) false else resolved.isPreviewOnly,
            album = original.album.ifBlank { resolved.album },
            artistId = original.artistId.ifBlank { resolved.artistId },
            artworkUrl = original.artworkUrl ?: resolved.artworkUrl,
            externalUrl = original.externalUrl.ifBlank { resolved.externalUrl },
            youtubeVideoId = original.youtubeVideoId.ifBlank { resolved.youtubeVideoId },
            hasMusicVideo = original.hasMusicVideo ||
                resolved.hasMusicVideo ||
                original.youtubeVideoId.isNotBlank() ||
                resolved.youtubeVideoId.isNotBlank()
        )
    }

    private fun hasOfflineOrLocalFile(track: Track): Boolean {
        return downloadManager.getOfflineFile(track.id) != null || !track.localUri.isNullOrBlank()
    }

    private fun isPlayableFullStreamUrl(url: String): Boolean {
        return QueueEngine.isPlayableFullStreamUrl(url)
    }

    private fun hasPlayableMedia(track: Track): Boolean {
        return hasOfflineOrLocalFile(track) || isPlayableFullStreamUrl(track.streamUrl) || track.isVideoOnly
    }

    private fun isRemoteResolvableTrack(track: Track): Boolean {
        if (track.isVideoOnly) return false
        return !hasOfflineOrLocalFile(track) &&
            !track.isLive &&
            (track.source == TrackSource.DEEZER ||
                track.source == TrackSource.ITUNES ||
                track.streamUrl.isBlank() ||
                isThirtySecondPreviewUrl(track.streamUrl))
    }

    private fun needsTrackResolution(track: Track): Boolean {
        if (track.isVideoOnly) return false
        if (hasOfflineOrLocalFile(track)) return false
        if (track.isLive) return track.streamUrl.isBlank()
        if (track.streamUrl.isBlank() || isThirtySecondPreviewUrl(track.streamUrl) || track.isPreviewOnly) {
            return true
        }
        if (!QueueEngine.isStreamUrlFresh(track.streamUrl)) return true
        if ((track.source == TrackSource.DEEZER || track.source == TrackSource.ITUNES) &&
            !resolvedTrackIds.contains(track.id)
        ) {
            return true
        }
        return false
    }

    private fun needsTransitionResolution(track: Track): Boolean {
        if (track.isVideoOnly) return false
        if (hasOfflineOrLocalFile(track) || track.isLive) return false
        return track.streamUrl.isBlank() ||
            isThirtySecondPreviewUrl(track.streamUrl) ||
            ((track.source == TrackSource.DEEZER || track.source == TrackSource.ITUNES) &&
                !resolvedTrackIds.contains(track.id))
    }

    private fun updateResolvedTrackInQueues(resolvedTrack: Track): Track {
        var updatedSample: Track? = null
        currentQueue = currentQueue.map { item ->
            if (item.id == resolvedTrack.id) {
                mergeResolvedTrack(item, resolvedTrack).also { updatedSample = it }
            } else {
                item
            }
        }
        originalQueue = originalQueue.map { item ->
            if (item.id == resolvedTrack.id) {
                mergeResolvedTrack(item, resolvedTrack)
            } else {
                item
            }
        }
        _playbackState.update { state ->
            val current = state.currentTrack
            val isCurrentUpdated = current?.id == resolvedTrack.id
            val nextCurrent = if (isCurrentUpdated) {
                mergeResolvedTrack(current, resolvedTrack)
            } else {
                current
            }
            val nextDurationMs = if (isCurrentUpdated && nextCurrent != null) {
                if (nextCurrent.isLive) 0L
                else (nextCurrent.durationSec * 1000L).takeIf { it > 0L } ?: state.durationMs
            } else {
                state.durationMs
            }
            state.copy(
                currentTrack = nextCurrent,
                durationMs = nextDurationMs,
                queue = currentQueue
            )
        }
        return updatedSample ?: resolvedTrack
    }

    fun pause() {
        hasPendingPlayRequest = false
        mediaController?.pause()
        val current = currentQueue.getOrNull(currentIndex)
        if (current?.isVideoOnly == true || !controllerHasCurrentQueue) {
            _playbackState.update { it.copy(isPlaying = false, isBuffering = false) }
        }
    }

    fun play() {
        val current = currentQueue.getOrNull(currentIndex) ?: return
        if (current.isVideoOnly) {
            activateVideoOnlyTrack(currentIndex, current, invokeCallback = true)
            return
        }
        val controller = mediaController
        if (controller == null) {
            hasPendingPlayRequest = true
            connect()
            return
        }
        if (!controllerHasCurrentQueue) {
            hasPendingPlayRequest = false
            applyCurrentQueueToController(controller)
            return
        }
        if (controller.playbackState == Player.STATE_IDLE && currentQueue.isNotEmpty()) {
            controller.prepare()
        } else if (controller.playbackState == Player.STATE_ENDED) {
            controller.seekTo(0L)
        }
        controller.play()
    }

    fun skipToQueueIndex(index: Int, startPausedForVideo: Boolean = false) {
        if (index !in currentQueue.indices) return
        queueGeneration++
        flushListenTracker(forceIfMinimumMet = true)
        playResolveJob?.cancel()
        preResolveJob?.cancel()
        transitionResolveJob?.cancel()

        currentIndex = index
        fadeInOnCurrentTrack = false
        val target = currentQueue[index]

        if (target.isVideoOnly || startPausedForVideo) {
            activateVideoOnlyTrack(index, target, invokeCallback = target.isVideoOnly || startPausedForVideo)
            return
        }

        isResolvingActiveTrack = needsTrackResolution(target)
        resetListenTrackerFor(target, isPlayingNow = false)
        _playbackState.update {
            it.copy(
                currentTrack = target,
                currentIndex = index,
                positionMs = 0L,
                durationMs = if (target.isLive) 0L else target.durationSec * 1000L,
                isPlaying = false,
                isBuffering = true,
                errorMessage = null
            )
        }

        val controller = mediaController
        if (controller == null) {
            hasPendingPlayRequest = true
            connect()
            return
        }
        if (controller.isPlaying || controller.playWhenReady) {
            controller.pause()
        }
        hasPendingPlayRequest = false
        applyCurrentQueueToController(controller)
    }

    fun togglePlayPause() {
        val currentTrack = currentQueue.getOrNull(currentIndex)
        if (currentTrack?.isVideoOnly == true) {
            _playbackState.update { it.copy(isPlaying = !it.isPlaying, isBuffering = false) }
            return
        }
        val controller = mediaController
        if (controller == null || !controllerHasCurrentQueue) {
            if (currentQueue.isNotEmpty()) {
                play()
            }
            return
        }

        if (controller.isPlaying || controller.playWhenReady) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && currentQueue.isNotEmpty()) {
                controller.prepare()
            } else if (controller.playbackState == Player.STATE_ENDED) {
                if (currentQueue.isNotEmpty() && controller.currentMediaItemIndex >= currentQueue.lastIndex) {
                    controller.seekToDefaultPosition(0)
                } else {
                    controller.seekTo(0L)
                }
            }
            controller.play()
        }
    }

    fun skipNext() {
        fadeInOnCurrentTrack = false
        setPlayerVolume(1f)
        flushListenTracker(forceIfMinimumMet = true)

        val currentTrack = currentQueue.getOrNull(currentIndex)
        val nextIdx = QueueEngine.resolveNextQueueIndex(currentIndex, currentQueue.size, repeatMode)
        if (nextIdx != null) {
            val nextTrack = currentQueue[nextIdx]
            if (nextTrack.isVideoOnly || currentTrack?.isVideoOnly == true || !controllerHasCurrentQueue) {
                skipToQueueIndex(nextIdx)
                return
            }
        }

        val controller = mediaController ?: return
        when {
            controller.hasNextMediaItem() -> {
                controller.seekToNextMediaItem()
                controller.play()
            }
            repeatMode == RepeatMode.ALL && currentQueue.isNotEmpty() -> {
                controller.seekToDefaultPosition(0)
                controller.play()
            }
            else -> {
                controller.seekTo(controller.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
                controller.pause()
            }
        }
    }

    fun skipPrev() {
        fadeInOnCurrentTrack = false
        setPlayerVolume(1f)

        val currentTrack = currentQueue.getOrNull(currentIndex)
        if (currentTrack?.isVideoOnly == true || !controllerHasCurrentQueue) {
            val prevIdx = QueueEngine.resolvePrevQueueIndex(currentIndex, currentQueue.size, repeatMode)
            if (prevIdx != null) {
                skipToQueueIndex(prevIdx)
            }
            return
        }

        val controller = mediaController ?: return
        if (controller.currentPosition > 3_000L && currentTrack?.isLive != true) {
            controller.seekTo(0L)
            syncPlaybackStateFromPlayer()
            return
        }

        val prevIdx = QueueEngine.resolvePrevQueueIndex(currentIndex, currentQueue.size, repeatMode)
        if (prevIdx != null && currentQueue[prevIdx].isVideoOnly) {
            skipToQueueIndex(prevIdx)
            return
        }

        if (controller.hasPreviousMediaItem()) {
            flushListenTracker(forceIfMinimumMet = true)
            controller.seekToPreviousMediaItem()
            controller.play()
        } else if (repeatMode == RepeatMode.ALL && currentQueue.isNotEmpty()) {
            flushListenTracker(forceIfMinimumMet = true)
            controller.seekToDefaultPosition(currentQueue.lastIndex)
            controller.play()
        } else {
            controller.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        fadeInOnCurrentTrack = false
        setPlayerVolume(1f)
        val clamped = positionMs.coerceAtLeast(0L)
        mediaController?.seekTo(clamped)
        _playbackState.update { it.copy(positionMs = clamped) }
    }

    fun toggleShuffle() {
        smartShuffleJob?.cancel()
        val nextMode = QueueEngine.nextShuffleMode(shuffleMode)
        shuffleMode = nextMode

        if (currentQueue.isEmpty()) {
            _playbackState.update { it.copy(shuffleMode = nextMode) }
            return
        }

        when (nextMode) {
            ShuffleMode.SHUFFLE -> {
                val shuffled = QueueEngine.shufflePreservingCurrent(currentQueue, currentIndex)
                replaceQueueKeepingCurrentItem(shuffled, newCurrentIndex = 0)
            }
            ShuffleMode.SMART_SHUFFLE -> {
                if (originalQueue.isEmpty()) originalQueue = currentQueue
                _playbackState.update { it.copy(shuffleMode = ShuffleMode.SMART_SHUFFLE) }
                launchSmartShuffleWeave()
                return
            }
            ShuffleMode.OFF -> {
                val activeTrack = currentQueue.getOrNull(currentIndex)
                val baseList = originalQueue.filterNot { it.isSmartPick }.ifEmpty {
                    currentQueue.filterNot { it.isSmartPick }
                }
                val restoredQueue: List<Track>
                val restoredIndex: Int
                if (activeTrack != null) {
                    val idxInBase = baseList.indexOfFirst { it.id == activeTrack.id }
                    if (idxInBase >= 0) {
                        restoredQueue = baseList
                        restoredIndex = idxInBase
                    } else {
                        restoredQueue = listOf(activeTrack) + baseList
                        restoredIndex = 0
                    }
                } else {
                    restoredQueue = baseList
                    restoredIndex = 0
                }
                replaceQueueKeepingCurrentItem(restoredQueue, newCurrentIndex = restoredIndex)
            }
        }

        _playbackState.update {
            it.copy(
                shuffleMode = nextMode,
                queue = currentQueue,
                currentIndex = currentIndex,
                currentTrack = currentQueue.getOrNull(currentIndex)
            )
        }
    }

    fun cycleRepeat() {
        repeatMode = QueueEngine.nextRepeatMode(repeatMode)
        mediaController?.repeatMode = toMedia3RepeatMode(repeatMode)
        _playbackState.update { it.copy(repeatMode = repeatMode) }
    }

    fun playNext(track: Track) {
        val effectiveTrack = applyCachedResolution(track)
        if (currentQueue.isEmpty()) {
            playQueue(listOf(effectiveTrack), 0)
            return
        }
        val insertIndex = (currentIndex + 1).coerceIn(0, currentQueue.size)
        val updated = currentQueue.toMutableList().apply { add(insertIndex, effectiveTrack) }
        currentQueue = updated

        val activeId = updated.getOrNull(currentIndex)?.id
        originalQueue = if (activeId != null) {
            val origIdx = originalQueue.indexOfFirst { it.id == activeId }
            if (origIdx >= 0) {
                originalQueue.toMutableList().apply { add(origIdx + 1, effectiveTrack) }
            } else {
                originalQueue + effectiveTrack
            }
        } else {
            originalQueue + effectiveTrack
        }

        if (controllerHasCurrentQueue) {
            mediaController?.let { mc ->
                mc.addMediaItem(insertIndex.coerceIn(0, mc.mediaItemCount), buildMediaItem(effectiveTrack))
            }
        }
        syncQueueMetadataOnly()
    }

    fun addToQueue(track: Track) {
        val effectiveTrack = applyCachedResolution(track)
        if (currentQueue.isEmpty()) {
            playQueue(listOf(effectiveTrack), 0)
            return
        }
        currentQueue = currentQueue + effectiveTrack
        originalQueue = originalQueue + effectiveTrack
        if (controllerHasCurrentQueue) {
            mediaController?.addMediaItem(buildMediaItem(effectiveTrack))
        }
        syncQueueMetadataOnly()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) {
            return
        }
        currentQueue = QueueEngine.moveItem(currentQueue, fromIndex, toIndex)
        currentIndex = QueueEngine.computeAdjustedIndexOnMove(currentIndex, fromIndex, toIndex)
        if (controllerHasCurrentQueue) {
            mediaController?.let { mc ->
                if (fromIndex < mc.mediaItemCount && toIndex < mc.mediaItemCount) {
                    mc.moveMediaItem(fromIndex, toIndex)
                }
            }
        }
        syncQueueMetadataOnly()
    }

    fun removeQueueItem(index: Int) {
        if (index !in currentQueue.indices) return
        val mutable = currentQueue.toMutableList()
        val removed = mutable.removeAt(index)
        currentQueue = mutable

        if (originalQueue.size > 1) {
            originalQueue = originalQueue.filterNot { it.id == removed.id && it.isSmartPick == removed.isSmartPick }
        }

        currentIndex = when {
            currentQueue.isEmpty() -> -1
            index < currentIndex -> currentIndex - 1
            index == currentIndex -> currentIndex.coerceAtMost(currentQueue.lastIndex)
            else -> currentIndex
        }

        if (controllerHasCurrentQueue) {
            mediaController?.let { mc ->
                if (index < mc.mediaItemCount) {
                    mc.removeMediaItem(index)
                }
            }
        }
        syncQueueMetadataOnly()
    }

    fun clearUpNext() {
        if (currentQueue.isEmpty() || currentIndex < 0 || currentIndex >= currentQueue.lastIndex) {
            return
        }
        val keepCount = currentIndex + 1
        val currentTrack = currentQueue[currentIndex]
        currentQueue = currentQueue.take(keepCount)
        originalQueue = listOf(currentTrack)
        if (controllerHasCurrentQueue) {
            mediaController?.let { mc ->
                if (mc.mediaItemCount > keepCount) {
                    mc.removeMediaItems(keepCount, mc.mediaItemCount)
                }
            }
        }
        syncQueueMetadataOnly()
    }

    fun appendTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val effectiveTracks = tracks.map(::applyCachedResolution)
        if (currentQueue.isEmpty()) {
            playQueue(effectiveTracks, 0)
            return
        }
        currentQueue = currentQueue + effectiveTracks
        originalQueue = originalQueue + effectiveTracks
        if (controllerHasCurrentQueue) {
            mediaController?.addMediaItems(effectiveTracks.map(::buildMediaItem))
        }
        syncQueueMetadataOnly()
    }

    fun setAudioSettings(settings: AudioSettings) {
        audioSettings = settings
        effectsManager.applySettings(settings)
        mediaController?.setPlaybackSpeed(settings.playbackSpeed.coerceIn(0.5f, 2.5f))
        if (settings.crossfadeSeconds <= 0) {
            setPlayerVolume(1f)
        }
        _playbackState.update { it.copy(playbackSpeed = settings.playbackSpeed) }
    }

    fun setSleepTimer(minutes: Int, endOfTrack: Boolean = false) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        if (endOfTrack) {
            _playbackState.update {
                it.copy(
                    sleepTimer = SleepTimerState(
                        active = true,
                        remainingMs = 0L,
                        endOfTrack = true,
                        label = "End of track"
                    )
                )
            }
            return
        }

        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }

        val durationMs = minutes * 60_000L
        val endElapsedMs = SystemClock.elapsedRealtime() + durationMs

        sleepTimerJob = scope.launch {
            while (isActive) {
                val remaining = (endElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    mediaController?.pause()
                    setPlayerVolume(1f)
                    _playbackState.update { it.copy(sleepTimer = SleepTimerState()) }
                    break
                }
                _playbackState.update {
                    it.copy(
                        sleepTimer = SleepTimerState(
                            active = true,
                            remainingMs = remaining,
                            endOfTrack = false,
                            label = formatTimerLabel(remaining)
                        )
                    )
                }
                delay(1_000L)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _playbackState.update { it.copy(sleepTimer = SleepTimerState()) }
    }

    fun getOutputDevices(): List<AudioOutputDevice> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return listOf(AudioOutputDevice("speaker", "Device Speaker", "Speaker", isActive = true))

        val activeRouteNames = getActiveMediaRouteNames()
        val rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val mapped = mutableListOf<AudioOutputDevice>()
        val seenKeys = HashSet<String>()

        for (device in rawDevices) {
            val typeLabel = mapDeviceTypeLabel(device.type) ?: continue
            val name = device.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: typeLabel
            val dedupKey = "$name|$typeLabel"
            if (!seenKeys.add(dedupKey)) continue

            val matchesActiveRoute = activeRouteNames.any { routeName ->
                routeName.equals(name, ignoreCase = true) ||
                    (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER &&
                        routeName.contains("phone", ignoreCase = true))
            }

            mapped += AudioOutputDevice(
                id = device.id.toString(),
                name = name,
                typeLabel = typeLabel,
                isActive = matchesActiveRoute
            )
        }

        if (mapped.isEmpty()) {
            return listOf(AudioOutputDevice("speaker", "Device Speaker", "Speaker", isActive = true))
        }

        if (mapped.none { it.isActive }) {
            val preferredIdx = mapped.indexOfFirst { it.typeLabel != "Speaker" }
                .takeIf { it >= 0 } ?: 0
            mapped[preferredIdx] = mapped[preferredIdx].copy(isActive = true)
        }

        return mapped
    }

    fun openSystemOutputSwitcher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val handled = runCatching {
                MediaRouter2.getInstance(context).showSystemOutputSwitcher()
            }.getOrDefault(false)
            if (handled) return
        }

        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent(Settings.Panel.ACTION_VOLUME))
            }
            add(Intent(Settings.ACTION_SOUND_SETTINGS))
        }

        for (intent in intents) {
            val launched = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
    }

    private fun launchSmartShuffleWeave() {
        smartShuffleJob?.cancel()
        val generation = queueGeneration
        val snapshotQueue = currentQueue.toList()
        val snapshotIndex = currentIndex
        if (snapshotQueue.isEmpty()) return

        val seeds = buildList {
            snapshotQueue.getOrNull(snapshotIndex)?.let { add(it) }
            addAll(snapshotQueue.take(6))
        }.distinctBy { it.id }
        val excludeIds = snapshotQueue.mapTo(HashSet()) { it.id }

        smartShuffleJob = scope.launch {
            val picks = withContext(Dispatchers.IO) {
                runCatching { smartPickProvider(seeds, excludeIds) }
                    .getOrDefault(emptyList())
            }
            if (generation != queueGeneration ||
                shuffleMode != ShuffleMode.SMART_SHUFFLE ||
                currentQueue.isEmpty() ||
                picks.isEmpty()
            ) {
                return@launch
            }

            val resolvedPicks = picks.map(::applyCachedResolution)
            val woven = QueueEngine.weaveSmartPicks(currentQueue, currentIndex, resolvedPicks)
            updateUpNextInPlayer(woven)
            syncQueueMetadataOnly()
            mediaController?.let { mc ->
                launchPreResolveNext(mc, currentIndex, generation)
            }
        }
    }

    private fun replaceQueueKeepingCurrentItem(newQueue: List<Track>, newCurrentIndex: Int) {
        val controller = mediaController
        currentQueue = newQueue
        currentIndex = newCurrentIndex.coerceIn(-1, newQueue.lastIndex)

        if (controller == null || newQueue.isEmpty()) return
        val activeControllerIndex = controller.currentMediaItemIndex
        if (activeControllerIndex < 0 || activeControllerIndex >= controller.mediaItemCount) {
            return
        }

        // Remove items before and after the active item so current playback is uninterrupted.
        if (controller.mediaItemCount > activeControllerIndex + 1) {
            controller.removeMediaItems(activeControllerIndex + 1, controller.mediaItemCount)
        }
        if (activeControllerIndex > 0) {
            controller.removeMediaItems(0, activeControllerIndex)
        }

        if (currentIndex > 0) {
            controller.addMediaItems(0, newQueue.take(currentIndex).map(::buildMediaItem))
        }
        if (currentIndex < newQueue.lastIndex) {
            controller.addMediaItems(newQueue.drop(currentIndex + 1).map(::buildMediaItem))
        }
    }

    private fun updateUpNextInPlayer(newQueue: List<Track>) {
        val controller = mediaController
        currentQueue = newQueue
        if (controller == null || controller.mediaItemCount == 0) {
            return
        }
        val anchor = controller.currentMediaItemIndex.takeIf { it in 0 until controller.mediaItemCount }
            ?: currentIndex.coerceIn(0, controller.mediaItemCount - 1)
        if (controller.mediaItemCount > anchor + 1) {
            controller.removeMediaItems(anchor + 1, controller.mediaItemCount)
        }
        if (currentIndex >= 0 && currentIndex < newQueue.lastIndex) {
            controller.addMediaItems(newQueue.drop(currentIndex + 1).map(::buildMediaItem))
        }
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null &&
                    controller.isPlaying &&
                    !isResolvingActiveTrack &&
                    controllerHasCurrentQueue &&
                    currentQueue.getOrNull(currentIndex)?.isVideoOnly != true
                ) {
                    tickListenAccumulator()
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val track = currentQueue.getOrNull(currentIndex)
                    val dur = resolveDurationMs(controller, track)

                    if (track != null && !track.isLive && repeatMode != RepeatMode.ONE && audioSettings.crossfadeSeconds > 0) {
                        val vol = QueueEngine.computeCrossfadeVolume(
                            positionMs = pos,
                            durationMs = dur,
                            crossfadeSeconds = audioSettings.crossfadeSeconds,
                            fadeInEnabled = fadeInOnCurrentTrack
                        )
                        setPlayerVolume(vol)
                        if (pos >= audioSettings.crossfadeSeconds * 1000L) {
                            fadeInOnCurrentTrack = false
                        }
                    } else {
                        setPlayerVolume(1f)
                    }

                    _playbackState.update {
                        it.copy(
                            currentTrack = track,
                            isPlaying = true,
                            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                            positionMs = pos,
                            durationMs = dur,
                            queue = currentQueue,
                            currentIndex = currentIndex
                        )
                    }
                }
                delay(250L)
            }
        }
    }

    private fun syncPlaybackStateFromPlayer() {
        val controller = mediaController ?: return
        if (!controllerHasCurrentQueue || currentQueue.getOrNull(currentIndex)?.isVideoOnly == true) {
            return
        }
        if (!isResolvingActiveTrack) {
            val idx = controller.currentMediaItemIndex
            if (idx in currentQueue.indices) {
                currentIndex = idx
            }
        }
        val track = currentQueue.getOrNull(currentIndex)
        val dur = if (isResolvingActiveTrack) {
            if (track == null || track.isLive) 0L else track.durationSec * 1000L
        } else {
            resolveDurationMs(controller, track)
        }
        val pos = if (isResolvingActiveTrack) 0L else controller.currentPosition.coerceAtLeast(0L)

        _playbackState.update {
            it.copy(
                currentTrack = track,
                isPlaying = if (isResolvingActiveTrack) false else controller.isPlaying,
                isBuffering = isResolvingActiveTrack || controller.playbackState == Player.STATE_BUFFERING,
                positionMs = pos,
                durationMs = dur,
                queue = currentQueue,
                currentIndex = currentIndex,
                shuffleMode = shuffleMode,
                repeatMode = repeatMode,
                playbackSpeed = audioSettings.playbackSpeed
            )
        }
    }

    private fun syncQueueMetadataOnly() {
        _playbackState.update {
            it.copy(
                queue = currentQueue,
                currentIndex = currentIndex,
                currentTrack = currentQueue.getOrNull(currentIndex)
            )
        }
    }

    private fun resolveDurationMs(controller: MediaController, track: Track?): Long {
        if (track?.isLive == true) return 0L
        val controllerDuration = controller.duration
        if (controllerDuration != C.TIME_UNSET && controllerDuration > 0L) {
            return controllerDuration
        }
        return ((track?.durationSec ?: 0) * 1000L).coerceAtLeast(0L)
    }

    private fun resetListenTrackerFor(track: Track?, isPlayingNow: Boolean) {
        trackedTrack = track
        accumulatedListenMs = 0L
        hasReportedCurrentPlay = false
        trackerPlaying = isPlayingNow
        lastListenTickMs = if (isPlayingNow) SystemClock.elapsedRealtime() else 0L
    }

    private fun updateListenTrackerPlaying(isPlayingNow: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (trackerPlaying && lastListenTickMs > 0L) {
            val delta = (now - lastListenTickMs).coerceIn(0L, 2_000L)
            accumulatedListenMs += delta
        }
        trackerPlaying = isPlayingNow
        lastListenTickMs = if (isPlayingNow) now else 0L
        checkListenThresholdReached()
    }

    private fun tickListenAccumulator() {
        val now = SystemClock.elapsedRealtime()
        if (trackerPlaying && lastListenTickMs > 0L) {
            val delta = (now - lastListenTickMs).coerceIn(0L, 2_000L)
            accumulatedListenMs += delta
        }
        lastListenTickMs = now
        checkListenThresholdReached()
    }

    private fun checkListenThresholdReached() {
        val track = trackedTrack ?: return
        if (!hasReportedCurrentPlay && accumulatedListenMs >= LISTEN_REPORT_THRESHOLD_MS) {
            hasReportedCurrentPlay = true
            if (!audioSettings.privateSession) {
                onTrackListened(track, accumulatedListenMs)
            }
        }
    }

    private fun flushListenTracker(forceIfMinimumMet: Boolean) {
        if (trackerPlaying && lastListenTickMs > 0L) {
            val delta = (SystemClock.elapsedRealtime() - lastListenTickMs).coerceIn(0L, 2_000L)
            accumulatedListenMs += delta
            lastListenTickMs = SystemClock.elapsedRealtime()
        }
        val track = trackedTrack ?: return
        val minRequired = if (forceIfMinimumMet) SKIP_REPORT_MINIMUM_MS else LISTEN_REPORT_THRESHOLD_MS
        if (!hasReportedCurrentPlay && accumulatedListenMs >= minRequired) {
            hasReportedCurrentPlay = true
            if (!audioSettings.privateSession) {
                onTrackListened(track, accumulatedListenMs)
            }
        }
    }

    private fun restoreQueueFromController(controller: MediaController) {
        val restored = (0 until controller.mediaItemCount).map { index ->
            val item = controller.getMediaItemAt(index)
            val meta = item.mediaMetadata
            Track(
                id = item.mediaId.ifBlank { "restored_$index" },
                title = meta.title?.toString().orEmpty().ifBlank { "Playing Track" },
                artist = meta.artist?.toString().orEmpty(),
                album = meta.albumTitle?.toString().orEmpty(),
                artworkUrl = meta.artworkUri?.toString(),
                streamUrl = item.localConfiguration?.uri?.toString().orEmpty()
            )
        }
        originalQueue = restored
        currentQueue = restored
        currentIndex = controller.currentMediaItemIndex.coerceIn(-1, restored.lastIndex)
        resetListenTrackerFor(currentQueue.getOrNull(currentIndex), controller.isPlaying)
        syncPlaybackStateFromPlayer()
    }

    private fun buildMediaItem(track: Track): MediaItem {
        val offlineFile = downloadManager.getOfflineFile(track.id)
        val resolvedUri: Uri = when {
            offlineFile != null && offlineFile.length() > 0L -> Uri.fromFile(offlineFile)
            !track.localUri.isNullOrBlank() -> Uri.parse(track.localUri)
            isPlayableFullStreamUrl(track.streamUrl) -> Uri.parse(track.streamUrl)
            else -> Uri.EMPTY
        }

        val artwork = track.artworkUrl?.takeIf { it.isNotBlank() }
            ?: track.artworkMirrors.firstOrNull { it.isNotBlank() }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.displayArtist)
            .setAlbumTitle(track.album.ifBlank { track.genre })
            .apply {
                artwork?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(resolvedUri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun setPlayerVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        SonoraPlayerBridge.activePlayer?.volume = clamped
        mediaController?.volume = clamped
    }

    private fun toMedia3RepeatMode(mode: RepeatMode): Int = when (mode) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    private fun getActiveMediaRouteNames(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return runCatching {
            MediaRouter2.getInstance(context).systemController.selectedRoutes
                .mapNotNull { it.name?.toString()?.takeIf { n -> n.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    private fun mapDeviceTypeLabel(type: Int): String? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth LE"
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Audio"
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI"
        AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing Aid"
        else -> null
    }

    private fun formatTimerLabel(remainingMs: Long): String {
        val totalSeconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    companion object {
        private const val LISTEN_REPORT_THRESHOLD_MS = 15_000L
        private const val SKIP_REPORT_MINIMUM_MS = 3_000L

        fun isThirtySecondPreviewUrl(url: String): Boolean =
            QueueEngine.isThirtySecondPreviewUrl(url)
    }
}
