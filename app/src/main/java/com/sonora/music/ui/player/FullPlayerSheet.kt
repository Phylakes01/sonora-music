package com.sonora.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.sonora.music.ui.theme.CoralHeart
import com.sonora.music.ui.theme.CyanGlow
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianCardElevated
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import com.sonora.music.ui.theme.VioletPulse
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class PlayerVisualMode(val label: String) {
    COVER("Cover"),
    VINYL("Vinyl"),
    WAVE("Wave")
}

/**
 * Full-screen kinetic Obsidian player overlay with swipe-down dismiss, multi-stop animated
 * aurora backdrop, 3D tilt Cover / rotating Vinyl / live Soundwave modes, heart burst
 * animation, glowing seek scrubber, 3-state Smart Shuffle, and quick utility dock.
 */
@Composable
fun FullPlayerSheet(
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
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
        animationSpec = tween(durationMillis = 650),
        label = "full_player_accent"
    )

    var visualMode by remember { mutableStateOf(PlayerVisualMode.COVER) }

    // Swipe-down-to-dismiss gesture state (works smoothly with NestedScrollConnection)
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

    // Heart burst animation state
    val heartBurstRing = remember { Animatable(0f) }
    var previousLiked by remember(track.id) { mutableStateOf(isLiked) }
    LaunchedEffect(isLiked) {
        if (isLiked && !previousLiked) {
            heartBurstRing.snapTo(0f)
            heartBurstRing.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520)
            )
        }
        previousLiked = isLiked
    }
    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.22f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "heart_scale"
    )

    // Interactive seek tracking to avoid jumping while dragging
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragMs by remember { mutableFloatStateOf(0f) }
    val durationMs = playback.durationMs.coerceAtLeast(track.durationSec * 1000L).coerceAtLeast(0L)
    val displayPositionMs = if (isDraggingSlider) {
        sliderDragMs.toLong()
    } else {
        playback.positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
    }

    val qualityBadge = when {
        downloadState?.status == DownloadStatus.DOWNLOADED -> "OFFLINE MP3"
        track.isLive || track.source == TrackSource.RADIO -> "LIVE RADIO"
        track.source == TrackSource.LOCAL -> "LOCAL"
        else -> "STREAMING"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, dismissOffsetY.value.roundToInt()) }
            .clip(RoundedCornerShape(topStart = if (dismissOffsetY.value > 4f) 28.dp else 0.dp, topEnd = if (dismissOffsetY.value > 4f) 28.dp else 0.dp))
            .background(ObsidianBg)
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
        // Multi-stop animated ambient aurora backdrop
        AmbientAuroraBackdrop(
            accentColor = accentColor,
            isPlaying = playback.isPlaying,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Subtle drag pill
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.24f))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Top bar: dismiss, source/quality badge, output device pill, overflow
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accentColor.copy(alpha = 0.16f))
                                .border(1.dp, accentColor.copy(alpha = 0.42f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = qualityBadge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = accentColor
                            )
                        }

                        // Output device pill
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(ObsidianCard.copy(alpha = 0.72f))
                                .border(1.dp, ObsidianStroke, RoundedCornerShape(50))
                                .clickable { actions.openOutputPicker() }
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SpeakerGroup,
                                contentDescription = "Audio output",
                                tint = CyanGlow,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = activeDevice?.name ?: "Phone Speaker",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(86.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = { onTrackOverflow(track) }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Track options",
                        tint = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Visual Mode Switcher: Cover | Vinyl | Wave
            VisualModeSwitcher(
                selectedMode = visualMode,
                accentColor = accentColor,
                onSelect = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    visualMode = it
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Dynamic Center Artwork / Vinyl / Wave Stage
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val stageSize = minOf(maxWidth * 0.82f, 316.dp)
                when (visualMode) {
                    PlayerVisualMode.COVER -> {
                        InteractiveTiltCoverCard(
                            track = track,
                            accentColor = accentColor,
                            isPlaying = playback.isPlaying,
                            modifier = Modifier.size(stageSize)
                        )
                    }

                    PlayerVisualMode.VINYL -> {
                        RotatingVinylTurntable(
                            track = track,
                            isPlaying = playback.isPlaying,
                            accentColor = accentColor,
                            modifier = Modifier.size(stageSize)
                        )
                    }

                    PlayerVisualMode.WAVE -> {
                        Box(
                            modifier = Modifier.size(stageSize),
                            contentAlignment = Alignment.Center
                        ) {
                            SoundwaveCanvas(
                                isPlaying = playback.isPlaying,
                                accentColor = accentColor,
                                innerRadiusFraction = 0.25f,
                                barCount = 68,
                                modifier = Modifier.fillMaxSize()
                            )
                            SonoraArtwork(
                                artworkUrl = track.artworkUrl,
                                title = track.title,
                                modifier = Modifier
                                    .size(stageSize * 0.46f)
                                    .border(2.dp, accentColor.copy(alpha = 0.65f), CircleShape),
                                isCircle = true
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Track Info + Heart Burst + Download + Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.displayArtist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (track.artistId.isNotBlank()) accentColor else TextSecondary,
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

                // Heart Burst Button
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (heartBurstRing.value > 0f && heartBurstRing.value < 1f) {
                        val p = heartBurstRing.value
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = CoralHeart.copy(alpha = (1f - p) * 0.85f),
                                radius = size.minDimension * (0.35f + p * 0.55f),
                                style = Stroke(width = 3.dp.toPx() * (1f - p * 0.7f))
                            )
                            for (i in 0 until 6) {
                                val a = (i / 6f) * (2f * PI).toFloat()
                                val dist = size.minDimension * (0.32f + p * 0.45f)
                                drawCircle(
                                    color = CoralHeart.copy(alpha = 1f - p),
                                    radius = 2.5.dp.toPx() * (1f - p * 0.5f),
                                    center = Offset(
                                        x = center.x + dist * cos(a),
                                        y = center.y + dist * sin(a)
                                    )
                                )
                            }
                        }
                    }

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
                }

                // Download button with progress
                if (!track.isLive && track.source != TrackSource.LOCAL) {
                    PlayerDownloadButton(
                        downloadState = downloadState,
                        accentColor = accentColor,
                        onClick = {
                            if (downloadState?.status == DownloadStatus.DOWNLOADED) {
                                actions.deleteDownload(track.id)
                            } else {
                                actions.downloadTrack(track)
                            }
                        }
                    )
                }

                IconButton(onClick = { actions.shareTrack(track) }) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share track",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Seek slider or Live Radio pulse pill
            if (track.isLive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CoralHeart.copy(alpha = 0.14f))
                        .border(1.dp, CoralHeart.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
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
                            text = "LIVE GLOBAL STREAM",
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
                            color = CoralHeart
                        )
                    }
                }
            } else {
                val safeMax = durationMs.coerceAtLeast(1L).toFloat()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = displayPositionMs.toFloat().coerceIn(0f, safeMax),
                        onValueChange = { value ->
                            isDraggingSlider = true
                            sliderDragMs = value
                        },
                        onValueChangeFinished = {
                            actions.seekTo(sliderDragMs.toLong())
                            isDraggingSlider = false
                        },
                        valueRange = 0f..safeMax,
                        colors = SliderDefaults.colors(
                            thumbColor = TextPrimary,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
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

            Spacer(modifier = Modifier.height(14.dp))

            // Main Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 3-state Shuffle (OFF, SHUFFLE, SMART_SHUFFLE glowing ✦)
                Box(
                    modifier = Modifier
                        .size(48.dp)
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
                        tint = if (shuffleActive) accentColor else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (playback.shuffleMode == ShuffleMode.SMART_SHUFFLE) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Smart Shuffle",
                            tint = CyanGlow,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(13.dp)
                        )
                    } else if (playback.shuffleMode == ShuffleMode.SHUFFLE) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        actions.skipPrev()
                    },
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous track",
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Glowing Play / Pause button with buffering ring
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.48f),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.minDimension * 0.68f
                            )
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(accentColor, CyanGlow)
                            )
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            actions.togglePlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (playback.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(66.dp),
                            color = ObsidianBg.copy(alpha = 0.75f),
                            strokeWidth = 3.dp
                        )
                    }
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        tint = ObsidianBg,
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        actions.skipNext()
                    },
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next track",
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // 3-state Repeat
                Box(
                    modifier = Modifier
                        .size(48.dp)
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
                        tint = if (repeatActive) accentColor else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (playback.repeatMode == RepeatMode.ALL) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Bottom quick action dock: Lyrics, Queue/Jam, Audio Studio, Track Radio, Add to Playlist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ObsidianCard.copy(alpha = 0.65f))
                    .border(1.dp, ObsidianStroke, RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerToolAction(
                    icon = Icons.Rounded.Lyrics,
                    label = "Lyrics",
                    tint = if (state.currentLyrics?.syncedLines?.isNotEmpty() == true) accentColor else TextSecondary,
                    onClick = { actions.openLyrics() }
                )
                PlayerToolAction(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = "Queue",
                    tint = TextSecondary,
                    onClick = { actions.openQueue() }
                )
                PlayerToolAction(
                    icon = Icons.Rounded.Equalizer,
                    label = "Studio",
                    tint = if (state.audioSettings.eqEnabled) accentColor else TextSecondary,
                    onClick = { actions.openAudioStudio() }
                )
                PlayerToolAction(
                    icon = Icons.Rounded.Radio,
                    label = "Radio",
                    tint = TextSecondary,
                    onClick = { actions.startRadio(track) }
                )
                PlayerToolAction(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    label = "Playlist",
                    tint = TextSecondary,
                    onClick = { onAddToPlaylist(track) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AmbientAuroraBackdrop(
    accentColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val drift by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.45f,
        animationSpec = tween(durationMillis = 800),
        label = "aurora_drift"
    )
    Canvas(modifier = modifier) {
        drawRect(color = ObsidianBg)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.36f * drift + 0.10f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.25f, size.height * 0.22f),
                radius = size.width * 0.85f
            ),
            radius = size.width * 0.85f,
            center = Offset(size.width * 0.25f, size.height * 0.22f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CyanGlow.copy(alpha = 0.16f * drift + 0.04f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.82f, size.height * 0.34f),
                radius = size.width * 0.68f
            ),
            radius = size.width * 0.68f,
            center = Offset(size.width * 0.82f, size.height * 0.34f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VioletPulse.copy(alpha = 0.18f * drift + 0.04f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, size.height * 0.78f),
                radius = size.width * 0.78f
            ),
            radius = size.width * 0.78f,
            center = Offset(size.width * 0.5f, size.height * 0.78f)
        )
    }
}

@Composable
private fun VisualModeSwitcher(
    selectedMode: PlayerVisualMode,
    accentColor: Color,
    onSelect: (PlayerVisualMode) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ObsidianCard.copy(alpha = 0.72f))
            .border(1.dp, ObsidianStroke, RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PlayerVisualMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) accentColor else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(mode) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (mode == PlayerVisualMode.WAVE) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = if (selected) ObsidianBg else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = if (selected) ObsidianBg else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun InteractiveTiltCoverCard(
    track: Track,
    accentColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val density = LocalDensity.current.density
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cover_play_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.40f),
                            CyanGlow.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension * 0.62f
                )
            }
            .graphicsLayer {
                rotationX = tiltX.value
                rotationY = tiltY.value
                cameraDistance = 12f * density
            }
            .pointerInput(track.id) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            launch { tiltX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                            launch { tiltY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            launch { tiltX.animateTo(0f) }
                            launch { tiltY.animateTo(0f) }
                        }
                    },
                    onDrag = { _, dragAmount ->
                        scope.launch {
                            val nextY = (tiltY.value + dragAmount.x * 0.18f).coerceIn(-18f, 18f)
                            val nextX = (tiltX.value - dragAmount.y * 0.18f).coerceIn(-18f, 18f)
                            tiltY.snapTo(nextY)
                            tiltX.snapTo(nextX)
                        }
                    }
                )
            }
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(28.dp), ambientColor = accentColor)
            .clip(RoundedCornerShape(28.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        SonoraArtwork(
            artworkUrl = track.artworkUrl,
            title = track.title,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 28.dp
        )
    }
}

@Composable
private fun RotatingVinylTurntable(
    track: Track,
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var rotationAngle by remember(track.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, track.id) {
        if (!isPlaying) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos).coerceAtLeast(0L)) / 1_000_000_000f
            lastNanos = now
            rotationAngle = (rotationAngle + dt * 42f) % 360f
        }
    }

    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 14f else -18f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tonearm_angle"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Spinning vinyl record
        Box(
            modifier = Modifier
                .fillMaxSize(0.88f)
                .graphicsLayer { rotationZ = rotationAngle },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1E28),
                            Color(0xFF0A0C10),
                            Color(0xFF141822)
                        )
                    ),
                    radius = radius
                )
                // Grooves
                for (i in 1..11) {
                    val r = radius * (0.48f + i * 0.044f)
                    drawCircle(
                        color = Color.White.copy(alpha = if (i % 3 == 0) 0.12f else 0.05f),
                        radius = r,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                // Metallic sheen
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            accentColor.copy(alpha = 0.22f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.45f),
                    radius = radius,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            SonoraArtwork(
                artworkUrl = track.artworkUrl,
                title = track.title,
                modifier = Modifier
                    .fillMaxSize(0.45f)
                    .border(2.dp, accentColor.copy(alpha = 0.55f), CircleShape),
                isCircle = true,
                showVinylHole = true
            )
        }

        // Animated Tonearm overlay pivoted from top-right
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.88f, 0.12f)
                    rotationZ = tonearmAngle
                }
        ) {
            val pivot = Offset(size.width * 0.88f, size.height * 0.12f)
            val elbow = Offset(size.width * 0.78f, size.height * 0.42f)
            val needle = Offset(size.width * 0.68f, size.height * 0.53f)

            drawCircle(
                color = ObsidianCardElevated,
                radius = 12.dp.toPx(),
                center = pivot
            )
            drawCircle(
                color = accentColor,
                radius = 5.dp.toPx(),
                center = pivot
            )
            drawLine(
                color = Color(0xFFD1D7E3),
                start = pivot,
                end = elbow,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFD1D7E3),
                start = elbow,
                end = needle,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = accentColor,
                radius = 4.dp.toPx(),
                center = needle
            )
        }
    }
}

@Composable
private fun PlayerDownloadButton(
    downloadState: DownloadState?,
    accentColor: Color,
    onClick: () -> Unit
) {
    val status = downloadState?.status ?: DownloadStatus.NONE
    IconButton(onClick = onClick) {
        when (status) {
            DownloadStatus.DOWNLOADING -> {
                CircularProgressIndicator(
                    progress = { (downloadState?.progress ?: 0f).coerceIn(0.05f, 1f) },
                    modifier = Modifier.size(22.dp),
                    color = accentColor,
                    strokeWidth = 2.5.dp
                )
            }

            DownloadStatus.DOWNLOADED -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Downloaded offline",
                    tint = accentColor,
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

@Composable
private fun PlayerToolAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = tint
        )
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
