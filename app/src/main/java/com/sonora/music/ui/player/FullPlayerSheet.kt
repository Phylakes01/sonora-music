package com.sonora.music.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.sonora.music.model.LyricsResult
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
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val FullPlayerBg = Color(0xFF101012)
private val LyricsPreviewCardBg = Color(0xFF1A1A1D)
private val LyricsPreviewBorder = Color(0xFF28282E)
private val LyricsShowAllPillBg = Color(0xFF29292F)

/**
 * Flagship Now Playing sheet featuring silky muted artwork wash fading into #101012,
 * breathing spring-scaled artwork, tabular-num seek scrubber, white 66.dp Play/Pause button,
 * unboxed utilities, and an inline synced lyrics preview card.
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hasMusicVideo = track.hasMusicVideo || track.youtubeVideoId.isNotBlank()
    val isVideoActive = state.isVideoMode && track.youtubeVideoId.isNotBlank()

    val accentColor by animateColorAsState(
        targetValue = state.dominantAccent,
        animationSpec = tween(durationMillis = 550),
        label = "full_player_accent"
    )
    val topWashColor = lerp(FullPlayerBg, accentColor.copy(alpha = 1f), 0.35f)
    val upperMidWashColor = lerp(FullPlayerBg, accentColor.copy(alpha = 1f), 0.20f)
    val lowerMidWashColor = lerp(FullPlayerBg, accentColor.copy(alpha = 1f), 0.08f)

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

    // Breathing artwork spring scale (1.0f playing, 0.92f paused)
    val artworkScale by animateFloatAsState(
        targetValue = if (playback.isPlaying) 1.0f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "full_artwork_breathing_scale"
    )
    val artworkElevation by animateDpAsState(
        targetValue = if (playback.isPlaying) 22.dp else 10.dp,
        animationSpec = tween(durationMillis = 320),
        label = "full_artwork_elevation"
    )

    var heartBurst by remember { mutableStateOf(false) }
    LaunchedEffect(heartBurst) {
        if (heartBurst) {
            delay(150L)
            heartBurst = false
        }
    }
    val heartScale by animateFloatAsState(
        targetValue = if (heartBurst) 1.24f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "full_heart_scale"
    )

    var playBounce by remember { mutableStateOf(false) }
    LaunchedEffect(playBounce) {
        if (playBounce) {
            delay(110L)
            playBounce = false
        }
    }
    val playScale by animateFloatAsState(
        targetValue = if (playBounce) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "full_play_scale"
    )

    // Smooth debounced seek handling for SonoraSlimSlider
    var pendingSeekMs by remember(track.id) { mutableStateOf<Float?>(null) }
    LaunchedEffect(pendingSeekMs) {
        val target = pendingSeekMs ?: return@LaunchedEffect
        delay(80L)
        actions.seekTo(target.toLong())
        pendingSeekMs = null
    }

    var showRemainingTime by remember(track.id) { mutableStateOf(false) }
    val durationMs = playback.durationMs.coerceAtLeast(track.durationSec * 1000L).coerceAtLeast(0L)
    val displayPositionMs = pendingSeekMs?.toLong()
        ?: playback.positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))

    val contextSubtitle = remember(track, downloadState?.status, isVideoActive) {
        when {
            isVideoActive -> "Official Music Video"
            downloadState?.status == DownloadStatus.DOWNLOADED -> "Offline Downloads"
            track.isLive || track.source == TrackSource.RADIO -> "Live Radio"
            track.source == TrackSource.YOUTUBE -> "Official Music Video"
            track.source == TrackSource.PODCAST -> "Podcast"
            track.source == TrackSource.AUDIOBOOK -> "Audiobook"
            track.source == TrackSource.LOCAL -> "Local Files"
            track.album.isNotBlank() -> track.album
            track.genre.isNotBlank() -> track.genre
            else -> "Sonora"
        }
    }

    val canOpenArtist = track.artistId.isNotBlank() ||
        (track.artist.isNotBlank() && !track.artist.equals("Featured Artist", ignoreCase = true))

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
                    0.26f to upperMidWashColor,
                    0.50f to lowerMidWashColor,
                    0.74f to FullPlayerBg,
                    1.0f to FullPlayerBg
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
                        if (scrollState.value == 0) {
                            scope.launch {
                                val next = (dismissOffsetY.value + dragAmount).coerceAtLeast(0f)
                                dismissOffsetY.snapTo(next)
                            }
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
            // Top bar: Down chevron, centered two-line context, overflow
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
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp
                        ),
                        color = TextSecondary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contextSubtitle,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.5.sp,
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

            if (hasMusicVideo) {
                Spacer(modifier = Modifier.height(10.dp))
                SongVideoModeToggle(
                    isVideoMode = isVideoActive,
                    onSelectMode = { videoSelected ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        actions.onSetVideoMode(videoSelected)
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            if (isVideoActive) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .shadow(
                                elevation = 20.dp,
                                shape = RoundedCornerShape(14.dp),
                                ambientColor = Color.Black,
                                spotColor = Color.Black
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    ) {
                        YouTubeVideoPlayer(
                            videoId = track.youtubeVideoId,
                            title = track.title,
                            artist = track.displayArtist,
                            onVideoEnded = { actions.skipNext() },
                            onFallbackOpenExternal = { url -> openExternalYoutubeUrl(context, url) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF1F1F26),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OndemandVideo,
                                    contentDescription = null,
                                    tint = EmeraldAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (track.isVideoOnly) "Official MV" else "Hybrid MV",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF22222A),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { actions.openFullTrackExternal(track) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Watch on YouTube",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            } else {
                // Breathing square rounded-14dp artwork with soft elevation shadow
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val artSize = minOf(maxWidth, 340.dp)
                    Box(
                        modifier = Modifier
                            .size(artSize)
                            .graphicsLayer {
                                scaleX = artworkScale
                                scaleY = artworkScale
                            }
                            .shadow(
                                elevation = artworkElevation,
                                shape = RoundedCornerShape(14.dp),
                                ambientColor = Color.Black,
                                spotColor = Color.Black
                            )
                    ) {
                        SonoraArtwork(
                            artworkUrl = track.artworkUrl,
                            title = track.title,
                            cornerRadius = 14.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Track title + tappable artist on left, Heart + Full MP3 Download on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
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
                        modifier = Modifier.clickable(enabled = canOpenArtist) {
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

                IconButton(
                    onClick = {
                        heartBurst = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        actions.toggleLike(track)
                    }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike track" else "Like track",
                        tint = if (isLiked) CoralHeart else TextSecondary,
                        modifier = Modifier
                            .size(25.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            if (!isVideoActive) {
                // Clean SonoraSlimSlider + crisp tabular-nums timestamps (or LIVE indicator)
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatMs(displayPositionMs),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = TextSecondary
                            )
                            val remainingMs = (durationMs - displayPositionMs).coerceAtLeast(0L)
                            Text(
                                text = if (showRemainingTime) {
                                    "-${formatMs(remainingMs)}"
                                } else {
                                    formatMs(durationMs)
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = TextMuted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showRemainingTime = !showRemainingTime }
                                    .padding(horizontal = 2.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Classic transport bar: 3-state Shuffle, Prev, Play/Pause (or Video Queue pill), Next, Repeat
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
                        modifier = Modifier.size(23.dp)
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

                if (isVideoActive) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF23232B),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .height(46.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(enabled = !track.isVideoOnly) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                actions.onSetVideoMode(false)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (track.isVideoOnly) {
                                    Icons.Rounded.OndemandVideo
                                } else {
                                    Icons.Rounded.MusicNote
                                },
                                contentDescription = null,
                                tint = EmeraldAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (track.isVideoOnly) "In-App Video" else "Switch to Audio",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = TextPrimary
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .scale(playScale)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                playBounce = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                actions.togglePlayPause()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (playback.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(58.dp),
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
                        modifier = Modifier.size(23.dp)
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

            Spacer(modifier = Modifier.height(20.dp))

            // Clean unboxed utility row: Output Device, Lyrics, Queue, Equalizer, Share
            val hasLyrics = state.currentLyrics?.syncedLines?.isNotEmpty() == true ||
                !state.currentLyrics?.plainLyrics.isNullOrBlank()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { actions.openOutputPicker() },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SpeakerGroup,
                            contentDescription = activeDevice?.name ?: "Output device",
                            tint = if (activeDevice != null && activeDevice.typeLabel != "Speaker") {
                                EmeraldAccent
                            } else {
                                TextSecondary
                            },
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { actions.openLyrics() },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (hasLyrics) EmeraldAccent else TextSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                IconButton(
                    onClick = { actions.openQueue() },
                    modifier = Modifier.size(42.dp)
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
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Equalizer",
                        tint = if (state.audioSettings.eqEnabled) EmeraldAccent else TextSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                IconButton(
                    onClick = { actions.shareTrack(track) },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share track",
                        tint = TextSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Inline Synced Lyrics Preview Card
            InlineLyricsPreviewCard(
                isLive = track.isLive,
                lyrics = state.currentLyrics,
                isLoading = state.isLoadingLyrics,
                positionMs = displayPositionMs,
                onShowAll = { actions.openLyrics() }
            )

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun InlineLyricsPreviewCard(
    isLive: Boolean,
    lyrics: LyricsResult?,
    isLoading: Boolean,
    positionMs: Long,
    onShowAll: () -> Unit
) {
    val syncedLines = lyrics?.syncedLines.orEmpty()
    val plainLines = remember(lyrics?.plainLyrics) {
        lyrics?.plainLyrics
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(3)
            ?.toList()
            .orEmpty()
    }
    val activeIndex = if (syncedLines.isNotEmpty()) {
        syncedLines.indexOfLast { it.timeMs <= positionMs }
    } else {
        -1
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LyricsPreviewCardBg)
            .border(1.dp, LyricsPreviewBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onShowAll)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Lyrics",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
                if (syncedLines.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldAccent)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LyricsShowAllPillBg)
                    .clickable(onClick = onShowAll)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Show all",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLive -> {
                Text(
                    text = "Live radio broadcast — lyrics unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            isLoading -> {
                Text(
                    text = "Fetching synced lyrics...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            syncedLines.isNotEmpty() -> {
                val startIdx = if (activeIndex >= 0) activeIndex else 0
                val previewSlice = syncedLines.subList(
                    startIdx,
                    (startIdx + 3).coerceAtMost(syncedLines.size)
                )
                AnimatedContent(
                    targetState = activeIndex to previewSlice.firstOrNull()?.text.orEmpty(),
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    },
                    label = "inline_synced_lyrics"
                ) { (currentIdx, _) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        previewSlice.forEachIndexed { offset, line ->
                            val isActiveLine = currentIdx >= 0 && offset == 0
                            val alpha = when {
                                isActiveLine -> 1f
                                offset == 1 -> 0.56f
                                else -> 0.38f
                            }
                            Text(
                                text = line.text.ifBlank { "♪" },
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = if (isActiveLine) 16.5.sp else 14.5.sp,
                                    lineHeight = if (isActiveLine) 23.sp else 20.sp,
                                    fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = Color.White.copy(alpha = alpha),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            plainLines.isNotEmpty() -> {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    plainLines.forEachIndexed { idx, line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 14.5.sp,
                                lineHeight = 20.sp,
                                fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = Color.White.copy(alpha = if (idx == 0) 0.92f else 0.65f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            lyrics?.isInstrumental == true -> {
                Text(
                    text = "Instrumental track — tap to open lyrics view",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            else -> {
                Text(
                    text = "Tap to open lyrics view",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
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
                    contentDescription = "Downloaded full MP3",
                    tint = EmeraldAccent,
                    modifier = Modifier.size(22.dp)
                )
            }

            DownloadStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Retry full MP3 download",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Download full MP3",
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

@Composable
private fun SongVideoModeToggle(
    isVideoMode: Boolean,
    onSelectMode: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFF1E1E24).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val songBg by animateColorAsState(
                targetValue = if (!isVideoMode) Color(0xFF34343D) else Color.Transparent,
                animationSpec = tween(durationMillis = 180),
                label = "song_pill_bg"
            )
            val videoBg by animateColorAsState(
                targetValue = if (isVideoMode) Color(0xFF34343D) else Color.Transparent,
                animationSpec = tween(durationMillis = 180),
                label = "video_pill_bg"
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(songBg)
                    .clickable { onSelectMode(false) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = if (!isVideoMode) Color.White else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Song",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.5.sp,
                        fontWeight = if (!isVideoMode) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (!isVideoMode) Color.White else TextSecondary
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(videoBg)
                    .clickable { onSelectMode(true) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.OndemandVideo,
                    contentDescription = null,
                    tint = if (isVideoMode) EmeraldAccent else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Video",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.5.sp,
                        fontWeight = if (isVideoMode) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isVideoMode) Color.White else TextSecondary
                )
            }
        }
    }
}

private fun openExternalYoutubeUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
