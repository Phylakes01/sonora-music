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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.sonora.music.ui.theme.CoralHeart
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
import com.sonora.music.ui.theme.VioletPulse
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

fun Modifier.sonoraPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
    shape: Shape = RoundedCornerShape(14.dp),
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
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
            ObsidianCardElevated.copy(alpha = 0.95f),
            Color(0xFF283246),
            ObsidianCardElevated.copy(alpha = 0.95f),
            ObsidianCard
        ),
        start = Offset(translate, translate * 0.4f),
        end = Offset(translate + 340f, translate * 0.4f + 220f)
    )
}

@Composable
fun SonoraAmbientBackdrop(
    accentColor: Color = LocalSonoraAccent.current,
    secondaryColor: Color = CyanGlow,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = ObsidianBg)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.19f),
                    accentColor.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.16f, size.height * 0.08f),
                radius = size.maxDimension * 0.56f
            ),
            radius = size.maxDimension * 0.56f,
            center = Offset(size.width * 0.16f, size.height * 0.08f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.88f, size.height * 0.22f),
                radius = size.maxDimension * 0.44f
            ),
            radius = size.maxDimension * 0.44f,
            center = Offset(size.width * 0.88f, size.height * 0.22f)
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
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
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
                color = LocalSonoraAccent.current,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun SonoraPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color = LocalSonoraAccent.current,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.20f) else ObsidianCard,
        animationSpec = tween(220),
        label = "chip_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.72f) else ObsidianStrokeSoft,
        animationSpec = tween(220),
        label = "chip_border"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accentColor else TextSecondary,
        animationSpec = tween(220),
        label = "chip_content"
    )
    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.sonoraPressable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) TextPrimary else TextSecondary,
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
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.16f),
        border = BorderStroke(0.75.dp, tint.copy(alpha = 0.42f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = tint,
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
    cornerRadius: Dp = 12.dp,
    colorStart: Long = 0xFF1ED760L,
    colorEnd: Long = 0xFF0A2E2AL,
    isCircle: Boolean = false,
    showVinylHole: Boolean = false
) {
    val shape: Shape = if (isCircle) CircleShape else RoundedCornerShape(cornerRadius)
    val resolvedPalette = remember(title, colorStart, colorEnd) {
        val isDefault = colorStart == 0xFF1ED760L && colorEnd == 0xFF0A2E2AL
        if (isDefault && title.isNotBlank()) {
            val idx = abs(title.hashCode()) % PlaylistCoverPalettes.size
            PlaylistCoverPalettes[idx]
        } else {
            colorStart to colorEnd
        }
    }
    var imageFailed by remember(artworkUrl) { mutableStateOf(false) }
    val monogram = remember(title) { extractMonogram(title) }
    val context = LocalContext.current

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(resolvedPalette.first),
                        Color(resolvedPalette.second)
                    )
                )
            )
            .border(1.dp, ObsidianStrokeSoft, shape),
        contentAlignment = Alignment.Center
    ) {
        // Subtle diagonal sheen & monogram fallback underneath Coil image
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.34f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                ),
                color = Color.White.copy(alpha = 0.90f),
                maxLines = 1
            )
        }

        if (!artworkUrl.isNullOrBlank() && !imageFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .crossfade(280)
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
                    .fillMaxSize(0.30f)
                    .clip(CircleShape)
                    .background(ObsidianBg.copy(alpha = 0.92f))
                    .border(2.dp, Color.White.copy(alpha = 0.24f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.38f)
                        .clip(CircleShape)
                        .background(ObsidianSurface)
                        .border(1.dp, ObsidianStroke, CircleShape)
                )
            }
        }
    }
}

@Composable
fun AnimatedPlayingBars(
    isPlaying: Boolean,
    color: Color = LocalSonoraAccent.current,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "eq_bars")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eq_phase"
    )
    val idleScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.25f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "eq_idle"
    )

    Canvas(modifier = modifier.size(18.dp)) {
        val barCount = 4
        val gap = size.width * 0.11f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val phases = floatArrayOf(0f, 1.4f, 2.7f, 0.9f)
        val speeds = floatArrayOf(1f, 1.3f, 0.85f, 1.15f)
        for (i in 0 until barCount) {
            val raw = (sin((phase * speeds[i] + phases[i]).toDouble()).toFloat() + 1f) * 0.5f
            val fraction = (0.20f + raw * 0.80f * idleScale).coerceIn(0.18f, 1f)
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
    val accent = LocalSonoraAccent.current
    val rowBg by animateColorAsState(
        targetValue = if (isCurrent) accent.copy(alpha = 0.11f) else Color.Transparent,
        animationSpec = tween(250),
        label = "track_row_bg"
    )
    val borderStroke = if (isCurrent) BorderStroke(1.dp, accent.copy(alpha = 0.35f)) else null

    var heartBurstTrigger by remember { mutableIntStateOf(0) }
    val heartScale by animateFloatAsState(
        targetValue = if (heartBurstTrigger > 0) 1.32f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "heart_scale"
    )
    LaunchedEffect(heartBurstTrigger) {
        if (heartBurstTrigger > 0) {
            delay(180)
            heartBurstTrigger = 0
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = rowBg,
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .sonoraPressable(shape = RoundedCornerShape(14.dp), pressedScale = 0.985f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (index != null) {
                Box(
                    modifier = Modifier.width(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrent) {
                        AnimatedPlayingBars(isPlaying = isPlaying, color = accent)
                    } else {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                SonoraArtwork(
                    artworkUrl = track.artworkUrl,
                    title = track.title,
                    cornerRadius = 10.dp,
                    modifier = Modifier.fillMaxSize()
                )
                if (isCurrent && index == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.56f))
                            .border(1.2.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedPlayingBars(isPlaying = isPlaying, color = accent)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.ifBlank { "Untitled Track" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) accent else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = buildString {
                            append(track.displayArtist)
                            if (track.durationSec > 0 && !track.isLive) {
                                append(" • ")
                                append(track.formattedDuration)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (downloadState?.status == DownloadStatus.DOWNLOADED) {
                        SonoraBadge(text = "OFFLINE", tint = EmeraldAccent)
                    }
                    if (track.isLive || track.source == TrackSource.RADIO) {
                        SonoraBadge(text = "LIVE", tint = CoralHeart)
                    } else if (track.source == TrackSource.LOCAL) {
                        SonoraBadge(text = "LOCAL", tint = CyanGlow)
                    }
                    if (track.isSmartPick) {
                        SonoraBadge(text = "✦ Smart Pick", tint = VioletPulse)
                    }
                }
            }

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
                                modifier = Modifier.size(18.dp),
                                color = accent,
                                trackColor = ObsidianStrokeSoft,
                                strokeWidth = 2.2.dp
                            )
                        }
                        DownloadStatus.DOWNLOADED -> {
                            Icon(
                                imageVector = Icons.Rounded.DownloadDone,
                                contentDescription = "Downloaded",
                                tint = EmeraldAccent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        DownloadStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Retry download",
                                tint = CoralHeart,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Rounded.DownloadForOffline,
                                contentDescription = "Download offline",
                                tint = TextMuted,
                                modifier = Modifier.size(19.dp)
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
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) CoralHeart else TextMuted,
                    modifier = Modifier
                        .size(20.dp)
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
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Track options",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ShimmerRowPlaceholder() {
    val brush = rememberSonoraShimmerBrush()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.44f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(brush)
        )
    }
}

@Composable
fun ShimmerCardPlaceholder() {
    val brush = rememberSonoraShimmerBrush()
    Column(
        modifier = Modifier.width(152.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(152.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(11.dp)
                .clip(RoundedCornerShape(6.dp))
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
    onShare: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = LocalSonoraAccent.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(40.dp)
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
                    cornerRadius = 12.dp,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = track.displayArtist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        SonoraBadge(
                            text = track.source.badgeLabel,
                            tint = if (track.isLive) CoralHeart else accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = ObsidianStrokeSoft)
            Spacer(modifier = Modifier.height(8.dp))

            SheetActionItem(
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                label = "Play Next",
                tint = accent,
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
                tint = CyanGlow,
                onClick = {
                    onStartRadio()
                    onDismiss()
                }
            )

            if (!track.isLive && track.source != TrackSource.LOCAL) {
                val isDownloaded = downloadState?.status == DownloadStatus.DOWNLOADED
                val isDownloading = downloadState?.status == DownloadStatus.DOWNLOADING
                SheetActionItem(
                    icon = when {
                        isDownloaded -> Icons.Rounded.DeleteOutline
                        else -> Icons.Rounded.DownloadForOffline
                    },
                    label = when {
                        isDownloaded -> "Remove Offline Download"
                        isDownloading -> "Downloading (${(downloadState.progress * 100).toInt()}%)"
                        else -> "Download for Offline"
                    },
                    tint = if (isDownloaded) CoralHeart else EmeraldAccent,
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
                label = if (isLiked) "Share Liked Track" else "Share Track",
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 13.dp),
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
    val accent = LocalSonoraAccent.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
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
                    text = if (isEditMode) "Edit Playlist" else "Create Playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Unlimited playlists • Free forever",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                SonoraArtwork(
                    artworkUrl = null,
                    title = name.ifBlank { "New Mix" },
                    cornerRadius = 18.dp,
                    colorStart = selectedPalette.first,
                    colorEnd = selectedPalette.second,
                    modifier = Modifier.size(104.dp)
                )

                if (!isEditMode) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlaylistCoverPalettes.forEachIndexed { index, pair ->
                            val isSelected = index == selectedPaletteIndex
                            val swatchScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.18f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "palette_scale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer {
                                        scaleX = swatchScale
                                        scaleY = swatchScale
                                    }
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(pair.first), Color(pair.second))
                                        )
                                    )
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) TextPrimary else ObsidianStroke,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedPaletteIndex = index },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected palette",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
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
                    placeholder = { Text("Late Night Obsidian") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = ObsidianStroke,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = accent,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Deep synths, dark basslines & night drives") },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = ObsidianStroke,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = accent,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = accent
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
                        shape = RoundedCornerShape(14.dp),
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
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color(0xFF04140A)
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
    val accent = LocalSonoraAccent.current

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
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${track.title} • ${track.displayArtist}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.48f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .sonoraPressable(shape = RoundedCornerShape(16.dp)) {
                        onDismiss()
                        onCreateNewClick()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Playlist",
                            tint = Color(0xFF04140A),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "New Playlist",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Create a custom mix starting with this track",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom playlists yet — create one above!",
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
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianCard,
                            border = BorderStroke(
                                1.dp,
                                if (containsTrack) accent.copy(alpha = 0.45f) else ObsidianStrokeSoft
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .sonoraPressable(shape = RoundedCornerShape(14.dp)) {
                                    onSelectPlaylist(playlist)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SonoraArtwork(
                                    artworkUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
                                    title = playlist.name,
                                    cornerRadius = 10.dp,
                                    colorStart = playlist.coverColorStart,
                                    colorEnd = playlist.coverColorEnd,
                                    modifier = Modifier.size(46.dp)
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
                                        contentDescription = "Already added",
                                        tint = accent,
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
    val accent = LocalSonoraAccent.current
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
            shape = RoundedCornerShape(24.dp),
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
                    .padding(22.dp)
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
                            tint = accent,
                            modifier = Modifier.size(24.dp)
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
                        text = "Shareable Playlist / Jam Code",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
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
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (copiedFeedback) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (copiedFeedback) "Copied!" else "Copy Code",
                                color = TextPrimary
                            )
                        }
                        Button(
                            onClick = { onShareExport(exportCode) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color(0xFF04140A)
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

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = ObsidianStrokeSoft)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Import Playlist / Jam Code",
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
                        Text("Paste", color = accent)
                    }
                }

                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("Paste SONORA-PL-V1:... or sonora://playlist?code=...") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = ObsidianStroke,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianCard,
                    border = BorderStroke(1.dp, ObsidianStrokeSoft),
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
                            tint = if (mergeIntoJamQueue) VioletPulse else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (mergeIntoJamQueue) "Merge into Jam Queue" else "Import as Playlist",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = if (mergeIntoJamQueue) {
                                    "Weaves imported tracks straight into active playback queue"
                                } else {
                                    "Saves as a new playlist in your library"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = mergeIntoJamQueue,
                            onCheckedChange = { mergeIntoJamQueue = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBg,
                                checkedTrackColor = VioletPulse
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
                        shape = RoundedCornerShape(14.dp),
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
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mergeIntoJamQueue) VioletPulse else accent,
                            contentColor = ObsidianBg
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
