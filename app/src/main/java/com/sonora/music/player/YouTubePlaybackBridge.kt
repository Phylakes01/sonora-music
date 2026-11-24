package com.sonora.music.player

import com.sonora.music.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local reactive bridge connecting the active in-app YouTube player,
 * UI controls, Picture-in-Picture RemoteActions, and Media3 YouTubeMirrorPlayer
 * notification/lock-screen controls.
 */
object YouTubePlaybackBridge {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _activeTrack = MutableStateFlow<Track?>(null)
    val activeTrack: StateFlow<Track?> = _activeTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    @Volatile
    var onCommandPlayPause: ((Boolean) -> Unit)? = null

    @Volatile
    var onCommandSeekTo: ((Long) -> Unit)? = null

    @Volatile
    var onCommandSkipNext: (() -> Unit)? = null

    @Volatile
    var onCommandSkipPrev: (() -> Unit)? = null

    @Volatile
    var onCommandSkipToIndex: ((Int) -> Unit)? = null

    fun setActiveVideo(
        track: Track,
        queue: List<Track>,
        index: Int,
        initialPlaying: Boolean = true
    ) {
        val safeQueue = queue.ifEmpty { listOf(track) }
        val safeIndex = index.coerceIn(0, safeQueue.lastIndex)
        val fallbackDuration = if (track.isLive) 0L else (track.durationSec * 1000L).coerceAtLeast(0L)
        _activeTrack.value = track
        _queue.value = safeQueue
        _currentIndex.value = safeIndex
        _positionMs.value = 0L
        _durationMs.value = fallbackDuration
        _isPlaying.value = initialPlaying
    }

    fun reportPlayerState(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        _isPlaying.value = isPlaying
        if (positionMs >= 0L) {
            _positionMs.value = positionMs
        }
        if (durationMs > 0L) {
            _durationMs.value = durationMs
        }
    }

    fun requestPlayPause(play: Boolean) {
        _isPlaying.value = play
        onCommandPlayPause?.invoke(play)
    }

    fun requestTogglePlayPause() {
        val target = !_isPlaying.value
        requestPlayPause(target)
    }

    fun requestSeekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        _positionMs.value = clamped
        onCommandSeekTo?.invoke(clamped)
    }

    fun requestSkipNext() {
        onCommandSkipNext?.invoke()
    }

    fun requestSkipPrev() {
        onCommandSkipPrev?.invoke()
    }

    fun requestSkipToIndex(index: Int) {
        val q = _queue.value
        if (index in q.indices) {
            onCommandSkipToIndex?.invoke(index)
        }
    }

    fun clear() {
        _isPlaying.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        _activeTrack.value = null
        _queue.value = emptyList()
        _currentIndex.value = 0
    }
}
