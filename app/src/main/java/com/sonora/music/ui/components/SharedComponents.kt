package com.sonora.music.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sonora.music.model.DownloadState
import com.sonora.music.model.DownloadStatus
import com.sonora.music.model.Playlist
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import com.sonora.music.ui.theme.CyanGlow
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.LocalSonoraAccent
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianCardElevated
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianStrokeSoft
import com.sonora.music.ui.theme.ObsidianSurface
import com.sonora.music.ui.theme.PlaylistCoverPalettes
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

fun Modifier.sonoraPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    shape: Shape = RoundedCornerShape(10.dp),
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "sonora_press_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

@Composable
fun rememberSonoraShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "sonora_shimmer")
    val translate by transition.animateFloat(
        initialValue = -350f,
        targetValue = 1100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonora_shimmer_offset"
    )
    return Brush.linearGradient(
        colors = listOf(
            ObsidianCard,
            ObsidianCardElevated,
            ObsidianCard
        ),
        start = Offset(translate, translate * 0.35f),
        end = Offset(translate + 340f, translate * 0.35f + 220f)
    )
}

@Composable
fun SonoraBrandLogo(
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val echoPathLeft = remember { Path() }
    val echoPathRight = remember { Path() }
    val ribbonPath = remember { Path() }
    val spinePath = remember { Path() }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val minDim = minOf(w, h)
        val borderStroke = 1.dp.toPx().coerceAtMost(minDim * 0.08f)
        val inset = borderStroke / 2f
        val cornerRadius = minDim * 0.27f

        // Deep #0D1114 rounded-square backing + subtle 1.dp #24282E hairline
        drawRoundRect(
            color = Color(0xFF0D1114),
            topLeft = Offset(inset, inset),
            size = Size(w - borderStroke, h - borderStroke),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
        drawRoundRect(
            color = Color(0xFF24282E),
            topLeft = Offset(inset, inset),
            size = Size(w - borderStroke, h - borderStroke),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = borderStroke)
        )

        // Map ic_launcher_foreground.xml's inner 76x76 safe zone (16..92) onto Canvas bounds
        fun px(v: Float): Float = ((v - 16f) / 76f) * w
        fun py(v: Float): Float = ((v - 16f) / 76f) * h
        val scale = minDim / 76f

        // Symmetrical whisper echo arcs
        echoPathLeft.reset()
        echoPathLeft.moveTo(px(34f), py(35f))
        echoPathLeft.cubicTo(px(31f), py(42f), px(32f), py(48f), px(36f), py(53f))
        drawPath(
            path = echoPathLeft,
            color = Color(0xFF24E56F).copy(alpha = 0.45f),
            style = Stroke(width = 3.2f * scale, cap = StrokeCap.Round)
        )

        echoPathRight.reset()
        echoPathRight.moveTo(px(74f), py(73f))
        echoPathRight.cubicTo(px(77f), py(66f), px(76f), py(60f), px(72f), py(55f))
        drawPath(
            path = echoPathRight,
            color = Color(0xFF24E56F).copy(alpha = 0.45f),
            style = Stroke(width = 3.2f * scale, cap = StrokeCap.Round)
        )

        // Primary sculpted Sonora "S" acoustic ribbon
        ribbonPath.reset()
        ribbonPath.moveTo(px(66f), py(36f))
        ribbonPath.cubicTo(px(60f), py(28f), px(43f), py(29f), px(40f), py(40f))
        ribbonPath.cubicTo(px(38f), py(48f), px(46f), py(52f), px(54f), py(54f))
        ribbonPath.cubicTo(px(62f), py(56f), px(70f), py(60f), px(68f), py(68f))
        ribbonPath.cubicTo(px(65f), py(79f), px(48f), py(80f), px(42f), py(72f))
        drawPath(
            path = ribbonPath,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color(0xFF24E56F),
                    0.52f to Color(0xFF1DB954),
                    1f to Color(0xFF128E3E)
                ),
                start = Offset(px(38f), py(28f)),
                end = Offset(px(70f), py(80f))
            ),
            style = Stroke(
                width = 7.8f * scale,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Subtle highlight harmonic spine
        spinePath.reset()
        spinePath.moveTo(px(64f), py(35f))
        spinePath.cubicTo(px(59f), py(29f), px(45f), py(30f), px(42f), py(39f))
        drawPath(
            path = spinePath,
            color = Color(0xFFB8FFE0).copy(alpha = 0.30f),
            style = Stroke(width = 2.0f * scale, cap = StrokeCap.Round)
        )

        // Central crisp white pulse dot
        drawCircle(
            color = Color.White.copy(alpha = 0.94f),
            radius = 3.2f * scale,
            center = Offset(px(54f), py(54f))
        )
    }
}

@Composable
fun SonoraAmbientBackdrop(
    accentColor: Color = LocalSonoraAccent.current,
    secondaryColor: Color = CyanGlow,
    modifier: Modifier = Modifier
) {
    // Plain matte charcoal backdrop — zero glowing blobs
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    )
}

@Composable
fun SonoraSlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color(0xFF4D4D4D),
    modifier: Modifier = Modifier
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentRange by rememberUpdatedState(valueRange)
    var isDragging by remember { mutableStateOf(false) }

    val start = currentRange.start
    val end = currentRange.endInclusive
    val span = (end - start).takeIf { it > 0f } ?: 1f
    val normalized = ((value - start) / span).coerceIn(0f, 1f)

    val thumbRadiusDp = if (isDragging) 6.5.dp else 5.5.dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                val thumbRadPx = 5.5.dp.toPx()

                fun xToValue(x: Float): Float {
                    val s = currentRange.start
                    val e = currentRange.endInclusive
                    val sp = e - s
                    if (sp <= 0f || size.width <= 0) return s
                    val usable = (size.width - thumbRadPx * 2f).coerceAtLeast(1f)
                    val frac = ((x - thumbRadPx) / usable).coerceIn(0f, 1f)
                    return s + frac * sp
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!dragging) {
                                currentOnValueChange(xToValue(change.position.x))
                            }
                            isDragging = false
                            break
                        }
                        val dx = abs(change.position.x - down.position.x)
                        val dy = abs(change.position.y - down.position.y)
                        if (!dragging) {
                            if (dx > touchSlop && dx >= dy) {
                                dragging = true
                                isDragging = true
                                change.consume()
                                currentOnValueChange(xToValue(change.position.x))
                            } else if (dy > touchSlop && dy > dx) {
                                isDragging = false
                                break
                            }
                        } else {
                            change.consume()
                            currentOnValueChange(xToValue(change.position.x))
                        }
                    }
                }
            }
    ) {
        val trackHeight = 3.dp.toPx()
        val thumbRadius = thumbRadiusDp.toPx()
        val leftPad = 5.5.dp.toPx()
        val rightPad = size.width - leftPad
        val usableWidth = (rightPad - leftPad).coerceAtLeast(1f)
        val centerY = size.height / 2f
        val thumbX = leftPad + normalized * usableWidth

        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(leftPad, centerY - trackHeight / 2f),
            size = Size(usableWidth, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        )

        val activeWidth = (thumbX - leftPad).coerceAtLeast(0f)
        if (activeWidth > 0f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(leftPad, centerY - trackHeight / 2f),
                size = Size(activeWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )
        }

        drawCircle(
            color = activeColor,
            radius = thumbRadius,
            center = Offset(thumbX, centerY)
        )
    }
}

@Composable
fun SonoraSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!actionLabel.isNullOrBlank() && onActionClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun SonoraPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color = EmeraldAccent,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) accentColor else ObsidianCard,
        animationSpec = tween(160),
        label = "chip_bg"
    )
    val contentColor = if (selected) Color.Black else TextPrimary
    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = modifier.sonoraPressable(shape = CircleShape, pressedScale = 0.96f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SonoraBadge(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = ObsidianCard,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            ),
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

fun formatBytesMb(bytes: Long): String {
    if (bytes <= 0L) return "0.0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 100.0) {
        "%.0f MB".format(mb)
    } else {
        "%.1f MB".format(mb)
    }
}

private fun extractMonogram(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "S"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
    }
}

@Composable
fun SonoraArtwork(
    artworkUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    colorStart: Long = 0xFF1ED760L,
    colorEnd: Long = 0xFF0A2E2AL,
    isCircle: Boolean = false,
    showVinylHole: Boolean = false
) {
    val shape: Shape = if (isCircle) CircleShape else RoundedCornerShape(cornerRadius)
    val hasExplicitCustomPalette = colorStart != 0xFF1ED760L || colorEnd != 0xFF0A2E2AL
    var imageFailed by remember(artworkUrl) { mutableStateOf(false) }
    val showImage = !artworkUrl.isNullOrBlank() && !imageFailed
    val monogram = remember(title) { extractMonogram(title) }
    val context = LocalContext.current

    val fallbackBg = if (hasExplicitCustomPalette) {
        Brush.verticalGradient(listOf(Color(colorStart), Color(colorEnd)))
    } else {
        Brush.verticalGradient(listOf(ObsidianCardElevated, ObsidianCard))
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(ObsidianCard),
        contentAlignment = Alignment.Center
    ) {
        if (!showImage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = monogram,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showVinylHole) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.28f)
                    .clip(CircleShape)
                    .background(ObsidianBg)
                    .border(1.dp, ObsidianStroke, CircleShape)
            )
        }
    }
}

@Composable
fun AnimatedPlayingBars(
    isPlaying: Boolean,
    color: Color = EmeraldAccent,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "eq_bars")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eq_phase"
    )
    val idleScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.25f,
        animationSpec = tween(durationMillis = 200),
        label = "eq_idle"
    )

    Canvas(modifier = modifier.size(16.dp)) {
        val barCount = 3
        val gap = size.width * 0.14f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val phases = floatArrayOf(0f, 1.8f, 3.1f)
        val speeds = floatArrayOf(1f, 1.35f, 0.9f)
        for (i in 0 until barCount) {
            val raw = (sin((phase * speeds[i] + phases[i]).toDouble()).toFloat() + 1f) * 0.5f
            val fraction = (0.22f + raw * 0.78f * idleScale).coerceIn(0.20f, 1f)
            val barHeight = size.height * fraction
            val left = i * (barWidth + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    index: Int? = null,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    downloadState: DownloadState?,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    var heartBurstTrigger by remember { mutableIntStateOf(0) }
    val heartScale by animateFloatAsState(
        targetValue = if (heartBurstTrigger > 0) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "heart_scale"
    )
    LaunchedEffect(heartBurstTrigger) {
        if (heartBurstTrigger > 0) {
            delay(150)
            heartBurstTrigger = 0
        }
    }

    val isDownloaded = downloadState?.status == DownloadStatus.DOWNLOADED
    val subtitleText = remember(
        track.id,
        track.artist,
        track.durationSec,
        track.isLive,
        track.source,
        track.genre
    ) {
        when {
            track.isLive || track.source == TrackSource.RADIO -> {
                "LIVE • ${track.genre.ifBlank { track.displayArtist }}"
            }
            track.durationSec > 0 -> "${track.displayArtist} • ${track.formattedDuration}"
            else -> track.displayArtist
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) {
                    AnimatedPlayingBars(isPlaying = isPlaying, color = EmeraldAccent)
                } else {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            SonoraArtwork(
                artworkUrl = track.artworkUrl,
                title = track.title,
                cornerRadius = 6.dp,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrent && index == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.56f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedPlayingBars(isPlaying = isPlaying, color = EmeraldAccent)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.ifBlank { "Untitled Track" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = if (isCurrent) EmeraldAccent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = EmeraldAccent,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        if (!track.isLive && track.source != TrackSource.LOCAL) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDownloadClick),
                contentAlignment = Alignment.Center
            ) {
                when (downloadState?.status) {
                    DownloadStatus.DOWNLOADING -> {
                        CircularProgressIndicator(
                            progress = { downloadState.progress.coerceIn(0.08f, 1f) },
                            modifier = Modifier.size(16.dp),
                            color = EmeraldAccent,
                            trackColor = ObsidianStroke,
                            strokeWidth = 2.dp
                        )
                    }
                    DownloadStatus.DOWNLOADED -> {
                        Icon(
                            imageVector = Icons.Rounded.DownloadDone,
                            contentDescription = "Downloaded",
                            tint = EmeraldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DownloadStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Retry download",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.DownloadForOffline,
                            contentDescription = "Download offline",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = {
                heartBurstTrigger++
                onLikeClick()
            },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isLiked) "Unlike" else "Like",
                tint = if (isLiked) EmeraldAccent else TextMuted,
                modifier = Modifier
                    .size(19.dp)
                    .graphicsLayer {
                        scaleX = heartScale
                        scaleY = heartScale
                    }
            )
        }

        if (trailingContent != null) {
            trailingContent()
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Track options",
                tint = TextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
fun ShimmerRowPlaceholder() {
    val brush = rememberSonoraShimmerBrush()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.66f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun ShimmerCardPlaceholder() {
    val brush = rememberSonoraShimmerBrush()
    Column(
        modifier = Modifier.width(144.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.80f)
                .height(13.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.52f)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(
    track: Track,
    isLiked: Boolean,
    downloadState: DownloadState?,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onStartRadio: () -> Unit,
    onDownloadToggle: () -> Unit,
    onGoToArtist: () -> Unit,
    onShare: () -> Unit,
    onOpenFullTrack: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(ObsidianStroke)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SonoraArtwork(
                    artworkUrl = track.artworkUrl,
                    title = track.title,
                    cornerRadius = 6.dp,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            track.isLive || track.source == TrackSource.RADIO -> {
                                "LIVE • ${track.genre.ifBlank { track.displayArtist }}"
                            }
                            downloadState?.status == DownloadStatus.DOWNLOADED -> {
                                if (track.durationSec > 0) {
                                    "Offline MP3 • ${track.displayArtist} • ${track.formattedDuration}"
                                } else {
                                    "Offline MP3 • ${track.displayArtist}"
                                }
                            }
                            track.durationSec > 0 -> "${track.displayArtist} • ${track.formattedDuration}"
                            else -> track.displayArtist
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = ObsidianStroke, thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            if (!track.isLive &&
                track.source != TrackSource.LOCAL &&
                track.source != TrackSource.PODCAST &&
                track.source != TrackSource.AUDIOBOOK
            ) {
                SheetActionItem(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    label = "Open Full Song",
                    tint = TextPrimary,
                    onClick = {
                        onOpenFullTrack()
                        onDismiss()
                    }
                )
            }

            SheetActionItem(
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                label = "Play Next",
                onClick = {
                    onPlayNext()
                    onDismiss()
                }
            )
            SheetActionItem(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Add to Queue",
                onClick = {
                    onAddToQueue()
                    onDismiss()
                }
            )
            SheetActionItem(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                label = "Add to Playlist",
                onClick = {
                    onDismiss()
                    onAddToPlaylist()
                }
            )
            SheetActionItem(
                icon = Icons.Rounded.Radio,
                label = "Start Track Radio",
                onClick = {
                    onStartRadio()
                    onDismiss()
                }
            )

            if (!track.isLive && track.source != TrackSource.LOCAL) {
                val isDownloaded = downloadState?.status == DownloadStatus.DOWNLOADED
                val isDownloading = downloadState?.status == DownloadStatus.DOWNLOADING
                SheetActionItem(
                    icon = if (isDownloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.DownloadForOffline,
                    label = when {
                        isDownloaded -> "Remove Download"
                        isDownloading -> "Downloading (${(downloadState.progress * 100).toInt()}%)"
                        else -> "Download Full MP3"
                    },
                    tint = if (isDownloaded) EmeraldAccent else TextPrimary,
                    onClick = {
                        onDownloadToggle()
                        onDismiss()
                    }
                )
            }

            if (track.artistId.isNotBlank() || track.artist.isNotBlank()) {
                SheetActionItem(
                    icon = Icons.Rounded.Person,
                    label = "Go to Artist",
                    onClick = {
                        onDismiss()
                        onGoToArtist()
                    }
                )
            }

            SheetActionItem(
                icon = Icons.Rounded.Share,
                label = if (isLiked) "Share Liked Track" else "Share",
                onClick = {
                    onDismiss()
                    onShare()
                }
            )
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    label: String,
    tint: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CreatePlaylistDialog(
    initialName: String = "",
    initialDescription: String = "",
    isEditMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, desc: String, colorStart: Long, colorEnd: Long) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    var selectedPaletteIndex by remember { mutableIntStateOf(0) }
    val selectedPalette = PlaylistCoverPalettes[selectedPaletteIndex]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianStroke),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isEditMode) "Edit Playlist" else "New Playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

                SonoraArtwork(
                    artworkUrl = null,
                    title = name.ifBlank { "Playlist" },
                    cornerRadius = 12.dp,
                    colorStart = selectedPalette.first,
                    colorEnd = selectedPalette.second,
                    modifier = Modifier.size(96.dp)
                )

                if (!isEditMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlaylistCoverPalettes.forEachIndexed { index, pair ->
                            val isSelected = index == selectedPaletteIndex
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(pair.first), Color(pair.second))
                                        )
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) TextPrimary else ObsidianStroke,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedPaletteIndex = index },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected cover",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    placeholder = { Text("My Playlist") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextPrimary,
                        unfocusedBorderColor = ObsidianStroke,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TextPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = EmeraldAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Add a description") },
                    maxLines = 3,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextPrimary,
                        unfocusedBorderColor = ObsidianStroke,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TextPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = EmeraldAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, ObsidianStroke),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            val trimmed = name.trim()
                            if (trimmed.isNotEmpty()) {
                                onConfirm(
                                    trimmed,
                                    description.trim(),
                                    selectedPalette.first,
                                    selectedPalette.second
                                )
                            }
                        },
                        enabled = name.trim().isNotEmpty(),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldAccent,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isEditMode) "Save" else "Create",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNewClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Add to Playlist",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${track.title} • ${track.displayArtist}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ObsidianCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .sonoraPressable(shape = RoundedCornerShape(8.dp)) {
                        onDismiss()
                        onCreateNewClick()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(EmeraldAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Playlist",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "New Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No playlists yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val containsTrack = playlist.trackIds.contains(track.id) ||
                            playlist.tracks.any { it.id == track.id }
                        val count = maxOf(playlist.tracks.size, playlist.trackIds.size)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelectPlaylist(playlist)
                                    onDismiss()
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SonoraArtwork(
                                artworkUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
                                title = playlist.name,
                                cornerRadius = 6.dp,
                                colorStart = playlist.coverColorStart,
                                colorEnd = playlist.coverColorEnd,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$count tracks",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            if (containsTrack) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Added",
                                    tint = EmeraldAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportExportPlaylistDialog(
    title: String,
    exportCode: String?,
    onDismiss: () -> Unit,
    onShareExport: (String) -> Unit,
    onImportCode: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var importText by remember { mutableStateOf("") }
    var mergeIntoJamQueue by remember { mutableStateOf(false) }
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(1800)
            copiedFeedback = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianStroke),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                if (!exportCode.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Share Code",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, ObsidianStroke),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exportCode,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp
                            ),
                            color = TextPrimary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Sonora Code", exportCode))
                                copiedFeedback = true
                            },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, ObsidianStroke),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (copiedFeedback) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (copiedFeedback) "Copied" else "Copy",
                                color = TextPrimary
                            )
                        }
                        Button(
                            onClick = { onShareExport(exportCode) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldAccent,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = ObsidianStroke)
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Import Code",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clipText.isNullOrBlank()) {
                                importText = clipText.trim()
                            }
                        }
                    ) {
                        Text("Paste", color = EmeraldAccent)
                    }
                }

                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("Paste SONORA-PL-V1:... or link") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextPrimary,
                        unfocusedBorderColor = ObsidianStroke,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = EmeraldAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mergeIntoJamQueue = !mergeIntoJamQueue }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = if (mergeIntoJamQueue) EmeraldAccent else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (mergeIntoJamQueue) "Merge into Jam Queue" else "Save as Playlist",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = if (mergeIntoJamQueue) {
                                    "Add tracks directly to active playback queue"
                                } else {
                                    "Create a new playlist in Your Library"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = mergeIntoJamQueue,
                            onCheckedChange = { mergeIntoJamQueue = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = EmeraldAccent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, ObsidianStroke),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            val trimmed = importText.trim()
                            if (trimmed.isNotEmpty()) {
                                onImportCode(trimmed, mergeIntoJamQueue)
                                onDismiss()
                            }
                        },
                        enabled = importText.trim().isNotEmpty(),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldAccent,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (mergeIntoJamQueue) "Merge Jam" else "Import",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
