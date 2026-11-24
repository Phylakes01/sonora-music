package com.sonora.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import com.sonora.music.model.PlaybackState
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.theme.CoralHeart
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MiniPlayerBaseColor = Color(0xFF1F1F22)

/**
 * Crisp solid docked MiniPlayer (#1F1F22 softly tinted with muted artwork tone),
 * 46.dp 6.dp-radius cover, horizontal swipe-to-skip, spring play/pause,
 * heart toggle, and crisp 2.dp bottom progress line.
 */
@Composable
fun MiniPlayer(
    playback: PlaybackState,
    isLiked: Boolean,
    accentColor: Color,
    onOpenFullPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
    isVideoMode: Boolean = false,
    onMiniVideoSlotPositioned: ((IntRect) -> Unit)? = null
) {
    val track = playback.currentTrack ?: return
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    val dragOffset = remember { Animatable(0f) }

    val cardColor by animateColorAsState(
        targetValue = lerp(MiniPlayerBaseColor, accentColor.copy(alpha = 1f), 0.16f),
        animationSpec = tween(durationMillis = 360),
        label = "mini_card_color"
    )

    var likeBounce by remember { mutableStateOf(false) }
    LaunchedEffect(likeBounce) {
        if (likeBounce) {
            delay(140L)
            likeBounce = false
        }
    }
    val likeScale by animateFloatAsState(
        targetValue = if (likeBounce) 1.22f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mini_like_scale"
    )

    var playBounce by remember { mutableStateOf(false) }
    LaunchedEffect(playBounce) {
        if (playBounce) {
            delay(120L)
            playBounce = false
        }
    }
    val playScale by animateFloatAsState(
        targetValue = if (playBounce) 0.82f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mini_play_spring"
    )

    val progressFraction = if (track.isLive) {
        1f
    } else if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "mini_progress"
    )

    val cardShape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(cardColor)
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val delta = dragOffset.value
                            scope.launch {
                                if (delta <= -swipeThresholdPx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSkipNext()
                                } else if (delta >= swipeThresholdPx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSkipPrev()
                                }
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch { dragOffset.animateTo(0f) }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val clamped = (dragOffset.value + dragAmount).coerceIn(-160f, 160f)
                                dragOffset.snapTo(clamped)
                            }
                        }
                    )
                }
                .clickable(onClick = onOpenFullPlayer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 7.dp)
                    .graphicsLayer { translationX = dragOffset.value * 0.40f },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isVideoMode && track.youtubeVideoId.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF101014))
                            .onGloballyPositioned { coords ->
                                onMiniVideoSlotPositioned?.invoke(coords.boundsInRoot().roundToIntRect())
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SonoraArtwork(
                            artworkUrl = track.artworkUrl,
                            title = track.title,
                            cornerRadius = 6.dp,
                            modifier = Modifier
                                .width(96.dp)
                                .height(54.dp)
                        )
                    }
                } else {
                    SonoraArtwork(
                        artworkUrl = track.artworkUrl,
                        title = track.title,
                        cornerRadius = 6.dp,
                        modifier = Modifier.size(46.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (track.isLive) "LIVE · ${track.displayArtist}" else track.displayArtist,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        likeBounce = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleLike()
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) CoralHeart else TextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(likeScale)
                    )
                }

                IconButton(
                    onClick = {
                        playBounce = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTogglePlayPause()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .scale(playScale)
                ) {
                    if (playback.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        AnimatedContent(
                            targetState = playback.isPlaying,
                            transitionSpec = {
                                (scaleIn(initialScale = 0.78f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(120))) togetherWith
                                    (scaleOut(targetScale = 0.78f, animationSpec = tween(100)) + fadeOut(tween(100)))
                            },
                            label = "mini_play_icon"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // Crisp 2.dp white progress line on bottom edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(2.dp)
                        .background(Color.White)
                )
            }
        }
    }
}
