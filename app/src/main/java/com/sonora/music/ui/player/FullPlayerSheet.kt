package com.sonora.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.model.Artist
import com.sonora.music.model.DetailRoute
import com.sonora.music.model.DownloadState
import com.sonora.music.model.DownloadStatus
import com.sonora.music.model.RepeatMode
import com.sonora.music.model.ShuffleMode
import com.sonora.music.model.SonoraActions
import com.sonora.music.model.SonoraUiState
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.components.SonoraSlimSlider
import com.sonora.music.ui.theme.CoralHeart
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Calm, matte full-screen player sheet with darkened artwork wash fading into #121212,
 * large square artwork, slim seek scrubber, solid white Play/Pause button, and unboxed utilities.
 */
@Composable
fun FullPlayerSheet(
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    @Suppress("UNUSED_PARAMETER") onAddToPlaylist: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.playback.currentTrack ?: return
    val playback = state.playback
    val downloadState = state.downloads[track.id]
    val isLiked = track.id in state.likedTrackIds
    val activeDevice = state.outputDevices.firstOrNull { it.isActive }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val accentColor by animateColorAsState(
        targetValue = state.dominantAccent,
        animationSpec = tween(durationMillis = 550),
        label = "full_player_accent"
    )
    val topWashColor = lerp(ObsidianBg, accentColor.copy(alpha = 1f), 0.34f)
    val midWashColor = lerp(ObsidianBg, accentColor.copy(alpha = 1f), 0.12f)

    // Swipe-down-to-dismiss gesture state
    val scrollState = rememberScrollState()
    val dismissOffsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 140.dp.toPx() }
    val nestedDismissConnection = remember(track.id) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && dismissOffsetY.value > 0f && available.y < 0f) {
                    val consumedY = available.y.coerceAtLeast(-dismissOffsetY.value)
                    scope.launch { dismissOffsetY.snapTo((dismissOffsetY.value + consumedY).coerceAtLeast(0f)) }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && scrollState.value == 0 && available.y > 0f) {
                    scope.launch { dismissOffsetY.snapTo(dismissOffsetY.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dismissOffsetY.value > 0f) {
                    if (dismissOffsetY.value >= dismissThresholdPx || available.y > 1400f) {
                        actions.closeFullPlayer()
                        dismissOffsetY.snapTo(0f)
                    } else {
                        dismissOffsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "full_heart_scale"
    )

    // Smooth debounced seek handling for SonoraSlimSlider
    var pendingSeekMs by remember(track.id) { mutableStateOf<Float?>(null) }
    LaunchedEffect(pendingSeekMs) {
        val target = pendingSeekMs ?: return@LaunchedEffect
        delay(90L)
        actions.seekTo(target.toLong())
        pendingSeekMs = null
    }

    val durationMs = playback.durationMs.coerceAtLeast(track.durationSec * 1000L).coerceAtLeast(0L)
    val displayPositionMs = pendingSeekMs?.toLong()
        ?: playback.positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))

    val contextSubtitle = remember(track, downloadState?.status) {
        when {
            downloadState?.status == DownloadStatus.DOWNLOADED -> "Offline Downloads"
            track.isLive || track.source == TrackSource.RADIO -> "Live Radio"
            track.source == TrackSource.PODCAST -> "Podcast"
            track.source == TrackSource.AUDIOBOOK -> "Audiobook"
            track.source == TrackSource.LOCAL -> "Local Files"
            track.album.isNotBlank() -> track.album
            track.genre.isNotBlank() -> track.genre
            else -> "Sonora"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, dismissOffsetY.value.roundToInt()) }
            .clip(
                RoundedCornerShape(
                    topStart = if (dismissOffsetY.value > 4f) 24.dp else 0.dp,
                    topEnd = if (dismissOffsetY.value > 4f) 24.dp else 0.dp
                )
            )
            .background(
                Brush.verticalGradient(
                    0.0f to topWashColor,
                    0.42f to midWashColor,
                    0.72f to ObsidianBg,
                    1.0f to ObsidianBg
                )
            )
            .nestedScroll(nestedDismissConnection)
            .pointerInput(track.id) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dismissOffsetY.value >= dismissThresholdPx) {
                                actions.closeFullPlayer()
                                dismissOffsetY.snapTo(0f)
                            } else {
                                dismissOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dismissOffsetY.animateTo(0f) }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        scope.launch {
                            val next = (dismissOffsetY.value + dragAmount).coerceAtLeast(0f)
                            dismissOffsetY.snapTo(next)
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Minimal top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { actions.closeFullPlayer() }) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Minimize player",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp
                        ),
                        color = TextSecondary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = contextSubtitle,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { onTrackOverflow(track) }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Track options",
                        tint = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Clean large square rounded-10.dp artwork with subtle shadow
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val artSize = minOf(maxWidth, 344.dp)
                SonoraArtwork(
                    artworkUrl = track.artworkUrl,
                    title = track.title,
                    cornerRadius = 10.dp,
                    modifier = Modifier
                        .size(artSize)
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(10.dp),
                            ambientColor = Color.Black,
                            spotColor = Color.Black
                        )
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Track title + tappable artist on left, Heart + Download on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = track.displayArtist,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = track.artistId.isNotBlank()) {
                            actions.closeFullPlayer()
                            actions.openDetail(
                                DetailRoute.ArtistDetail(
                                    Artist(
                                        id = track.artistId,
                                        name = track.displayArtist
                                    )
                                )
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    actions.toggleLike(track)
                }) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike track" else "Like track",
                        tint = if (isLiked) CoralHeart else TextSecondary,
                        modifier = Modifier
                            .size(26.dp)
                            .scale(heartScale)
                    )
                }

                if (!track.isLive && track.source != TrackSource.LOCAL) {
                    PlayerDownloadButton(
                        downloadState = downloadState,
                        onClick = {
                            if (downloadState?.status == DownloadStatus.DOWNLOADED) {
                                actions.deleteDownload(track.id)
                            } else {
                                actions.downloadTrack(track)
                            }
                        }
                    )
                }
            }

            if (!playback.isBuffering && track.isPreviewOnly) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ObsidianCard)
                            .border(1.dp, ObsidianStroke, RoundedCornerShape(50))
                            .clickable { actions.openFullTrackExternal(track) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "30s Preview · Open Full Song",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextPrimary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open full song",
                            tint = TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Seek scrubber or Live radio indicator
            if (track.isLive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CoralHeart)
                    )
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "Live radio broadcast",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                val safeMax = durationMs.coerceAtLeast(1L).toFloat()
                Column(modifier = Modifier.fillMaxWidth()) {
                    SonoraSlimSlider(
                        value = displayPositionMs.toFloat().coerceIn(0f, safeMax),
                        onValueChange = { value -> pendingSeekMs = value },
                        valueRange = 0f..safeMax,
                        activeColor = Color.White,
                        inactiveColor = Color(0xFF4D4D4D),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMs(displayPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        val remainingMs = (durationMs - displayPositionMs).coerceAtLeast(0L)
                        Text(
                            text = "-${formatMs(remainingMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transport bar: Shuffle, Prev, Solid White 64.dp Play/Pause, Next, Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            actions.toggleShuffle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val shuffleActive = playback.shuffleMode != ShuffleMode.OFF
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle mode",
                        tint = if (shuffleActive) EmeraldAccent else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (playback.shuffleMode == ShuffleMode.SMART_SHUFFLE) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Smart Shuffle",
                            tint = EmeraldAccent,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                                .size(12.dp)
                        )
                    } else if (playback.shuffleMode == ShuffleMode.SHUFFLE) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 5.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(EmeraldAccent)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        actions.skipPrev()
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous track",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            actions.togglePlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (playback.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(54.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            strokeWidth = 2.5.dp
                        )
                    }
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        actions.skipNext()
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next track",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            actions.cycleRepeat()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val repeatActive = playback.repeatMode != RepeatMode.OFF
                    Icon(
                        imageVector = if (playback.repeatMode == RepeatMode.ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat mode",
                        tint = if (repeatActive) EmeraldAccent else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (playback.repeatMode == RepeatMode.ALL) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 5.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(EmeraldAccent)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Clean unboxed bottom utility row: Output Device (left), Lyrics, Queue, EQ (Tune), Share (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { actions.openOutputPicker() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SpeakerGroup,
                        contentDescription = activeDevice?.name ?: "Output device",
                        tint = TextSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { actions.openLyrics() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = "Lyrics",
                            tint = if (state.currentLyrics?.syncedLines?.isNotEmpty() == true) EmeraldAccent else TextSecondary,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    IconButton(
                        onClick = { actions.openQueue() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Queue",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = { actions.openAudioStudio() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Audio Equalizer",
                            tint = if (state.audioSettings.eqEnabled) EmeraldAccent else TextSecondary,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    IconButton(
                        onClick = { actions.shareTrack(track) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share track",
                            tint = TextSecondary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PlayerDownloadButton(
    downloadState: DownloadState?,
    onClick: () -> Unit
) {
    val status = downloadState?.status ?: DownloadStatus.NONE
    IconButton(onClick = onClick) {
        when (status) {
            DownloadStatus.DOWNLOADING -> {
                CircularProgressIndicator(
                    progress = { (downloadState?.progress ?: 0f).coerceIn(0.05f, 1f) },
                    modifier = Modifier.size(22.dp),
                    color = EmeraldAccent,
                    strokeWidth = 2.5.dp
                )
            }

            DownloadStatus.DOWNLOADED -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Downloaded offline",
                    tint = EmeraldAccent,
                    modifier = Modifier.size(22.dp)
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Download track",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
