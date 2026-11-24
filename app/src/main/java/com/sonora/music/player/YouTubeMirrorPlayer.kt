package com.sonora.music.player

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sonora.music.model.Track

/**
 * Media3 SimpleBasePlayer mirroring active YouTube video playback state into the shared
 * MediaSession so Android 13-15 notification shade & lock screen display title, artist,
 * thumbnail artwork, progress, and working Prev/Play-Pause/Next/Seek controls.
 */
@OptIn(UnstableApi::class)
class YouTubeMirrorPlayer(looper: Looper) : SimpleBasePlayer(looper) {

    @Volatile
    private var snapshotQueue: List<Track> = emptyList()

    @Volatile
    private var snapshotIndex: Int = 0

    @Volatile
    private var snapshotIsPlaying: Boolean = false

    @Volatile
    private var snapshotPositionMs: Long = 0L

    @Volatile
    private var snapshotDurationMs: Long = 0L

    fun updateFromBridge(
        queue: List<Track>,
        currentIndex: Int,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        snapshotQueue = queue
        snapshotIndex = if (queue.isEmpty()) 0 else currentIndex.coerceIn(0, queue.lastIndex)
        snapshotIsPlaying = isPlaying
        snapshotPositionMs = positionMs.coerceAtLeast(0L)
        snapshotDurationMs = durationMs.coerceAtLeast(0L)
        invalidateState()
    }

    fun updateProgressState(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        snapshotIsPlaying = isPlaying
        snapshotPositionMs = positionMs.coerceAtLeast(0L)
        if (durationMs > 0L) {
            snapshotDurationMs = durationMs
        }
        invalidateState()
    }

    override fun getState(): State {
        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_TIMELINE
            )
            .build()

        val activeQueue = snapshotQueue
        val safeIndex = if (activeQueue.isEmpty()) 0 else snapshotIndex.coerceIn(0, activeQueue.lastIndex)

        val playlist = activeQueue.mapIndexed { idx, track ->
            val resolvedArt = track.artworkUrl?.takeIf { it.isNotBlank() }
                ?: track.youtubeVideoId.takeIf { it.isNotBlank() }?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
                ?: track.artworkMirrors.firstOrNull { it.isNotBlank() }
            val artUri = resolvedArt?.let { runCatching { Uri.parse(it) }.getOrNull() }

            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.displayArtist)
                .setAlbumTitle(track.album.ifBlank { "Official Music Video" })
                .apply {
                    artUri?.let { setArtworkUri(it) }
                }
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(track.effectiveYoutubeUrl.ifBlank { "https://www.youtube.com/watch?v=${track.youtubeVideoId}" })
                .setMediaMetadata(metadata)
                .build()

            val effectiveDurationMs = if (idx == safeIndex && snapshotDurationMs > 0L) {
                snapshotDurationMs
            } else {
                (track.durationSec * 1000L).coerceAtLeast(0L)
            }
            val durationUs = if (effectiveDurationMs > 0L) {
                effectiveDurationMs * 1000L
            } else {
                C.TIME_UNSET
            }

            MediaItemData.Builder(track.id)
                .setMediaItem(mediaItem)
                .setMediaMetadata(metadata)
                .setIsSeekable(true)
                .setDurationUs(durationUs)
                .build()
        }

        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(snapshotIsPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (playlist.isEmpty()) Player.STATE_IDLE else Player.STATE_READY)
            .setPlaylist(playlist)
            .setContentPositionMs(
                if (snapshotIsPlaying) {
                    PositionSupplier.getExtrapolating(snapshotPositionMs, 1f)
                } else {
                    PositionSupplier.getConstant(snapshotPositionMs)
                }
            )

        if (playlist.isNotEmpty()) {
            builder.setCurrentMediaItemIndex(safeIndex)
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        snapshotIsPlaying = playWhenReady
        invalidateState()
        YouTubePlaybackBridge.requestPlayPause(playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: @Player.Command Int
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                YouTubePlaybackBridge.requestSkipNext()
            }
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                if (snapshotPositionMs > 3_500L && seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS) {
                    snapshotPositionMs = 0L
                    invalidateState()
                    YouTubePlaybackBridge.requestSeekTo(0L)
                } else {
                    YouTubePlaybackBridge.requestSkipPrev()
                }
            }
            else -> {
                val targetIndex = mediaItemIndex.coerceIn(0, (snapshotQueue.size - 1).coerceAtLeast(0))
                if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != snapshotIndex && snapshotQueue.isNotEmpty()) {
                    YouTubePlaybackBridge.requestSkipToIndex(targetIndex)
                } else if (positionMs != C.TIME_UNSET) {
                    snapshotPositionMs = positionMs.coerceAtLeast(0L)
                    invalidateState()
                    YouTubePlaybackBridge.requestSeekTo(snapshotPositionMs)
                }
            }
        }
        return Futures.immediateVoidFuture()
    }
}
