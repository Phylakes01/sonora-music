package com.sonora.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.model.WrappedStats
import com.sonora.music.model.WrappedTopItem
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.components.SonoraBadge
import com.sonora.music.ui.theme.AmberGold
import com.sonora.music.ui.theme.CyanGlow
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianStrokeSoft
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import com.sonora.music.ui.theme.VioletPulse
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val WrappedSlideCount = 5
private const val SlideDurationMs = 5600L

@Composable
fun WrappedScreen(
    stats: WrappedStats,
    onClose: () -> Unit,
    onShare: () -> Unit
) {
    var currentSlide by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var slideDirection by remember { mutableIntStateOf(1) }
    val slideProgress = remember(currentSlide) { Animatable(0f) }

    val auraPalette = remember(stats.auraColors) {
        val raw = stats.auraColors.map { Color(it) }
        when {
            raw.size >= 3 -> raw
            raw.size == 2 -> listOf(raw[0], raw[1], VioletPulse)
            raw.size == 1 -> listOf(raw[0], CyanGlow, VioletPulse)
            else -> listOf(EmeraldAccent, CyanGlow, VioletPulse)
        }
    }

    // Smooth auto-progress for story bars, respecting touch hold pause
    LaunchedEffect(currentSlide, isPaused) {
        if (isPaused) return@LaunchedEffect
        val remaining = (1f - slideProgress.value).coerceIn(0f, 1f)
        val duration = (SlideDurationMs * remaining).toInt().coerceAtLeast(150)
        slideProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
        )
        if (currentSlide < WrappedSlideCount - 1) {
            slideDirection = 1
            currentSlide += 1
        }
    }

    val infinite = rememberInfiniteTransition(label = "wrapped_aura")
    val auraAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .pointerInput(currentSlide) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        if (offset.x < width * 0.34f) {
                            if (currentSlide > 0) {
                                slideDirection = -1
                                currentSlide -= 1
                            }
                        } else {
                            if (currentSlide < WrappedSlideCount - 1) {
                                slideDirection = 1
                                currentSlide += 1
                            }
                        }
                    }
                )
            }
    ) {
        // Sound Aura Background Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c1 = auraPalette[0]
            val c2 = auraPalette[1]
            val c3 = auraPalette[2]
            val cx1 = size.width * (0.5f + 0.28f * cos(auraAngle))
            val cy1 = size.height * (0.28f + 0.12f * sin(auraAngle))
            val cx2 = size.width * (0.5f - 0.28f * sin(auraAngle))
            val cy2 = size.height * (0.72f + 0.14f * cos(auraAngle))

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c1.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(cx1, cy1),
                    radius = size.maxDimension * 0.62f
                ),
                radius = size.maxDimension * 0.62f,
                center = Offset(cx1, cy1)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c2.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(cx2, cy2),
                    radius = size.maxDimension * 0.56f
                ),
                radius = size.maxDimension * 0.56f,
                center = Offset(cx2, cy2)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c3.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = size.maxDimension * 0.52f
                ),
                radius = size.maxDimension * 0.52f,
                center = Offset(size.width * 0.5f, size.height * 0.5f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // Top 5 Story Progress Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until WrappedSlideCount) {
                    val fillFraction = when {
                        i < currentSlide -> 1f
                        i == currentSlide -> slideProgress.value
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.20f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fillFraction.coerceIn(0f, 1f))
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(auraPalette[0], auraPalette[1])
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = auraPalette[0],
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "SONORA WRAPPED",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = TextPrimary
                    )
                    SonoraBadge(
                        text = "${currentSlide + 1}/$WrappedSlideCount",
                        tint = auraPalette[1]
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share Wrapped",
                            tint = TextPrimary
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Wrapped",
                            tint = TextPrimary
                        )
                    }
                }
            }

            // Animated Slide Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentSlide,
                    transitionSpec = {
                        (slideInHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) { fullWidth -> fullWidth / 4 * slideDirection } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(220)
                            ) { fullWidth -> -fullWidth / 4 * slideDirection } + fadeOut(tween(180)))
                    },
                    label = "wrapped_slide_content"
                ) { slideIdx ->
                    when (slideIdx) {
                        0 -> WrappedMinutesSlide(stats = stats, primaryColor = auraPalette[0], secondaryColor = auraPalette[1])
                        1 -> WrappedTopTracksSlide(items = stats.topTracks, primaryColor = auraPalette[0])
                        2 -> WrappedTopArtistsSlide(items = stats.topArtists, primaryColor = auraPalette[1], tertiaryColor = auraPalette[2])
                        3 -> WrappedTopGenresSlide(items = stats.topGenres, palette = auraPalette)
                        else -> WrappedPersonaSlide(
                            stats = stats,
                            palette = auraPalette,
                            onShare = onShare,
                            onClose = onClose
                        )
                    }
                }
            }

            // Bottom Hint Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentSlide > 0) "‹ Tap left for previous" else "Hold anywhere to pause",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Text(
                    text = if (currentSlide < WrappedSlideCount - 1) "Tap right for next ›" else "All Features Unlocked",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun WrappedMinutesSlide(
    stats: WrappedStats,
    primaryColor: Color,
    secondaryColor: Color
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        revealed = true
    }

    val animatedMinutes by animateIntAsState(
        targetValue = if (revealed) stats.totalMinutes else 0,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "wrapped_mins"
    )
    val animatedPlays by animateIntAsState(
        targetValue = if (revealed) stats.totalPlays else 0,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "wrapped_plays"
    )
    val animatedTracks by animateIntAsState(
        targetValue = if (revealed) stats.uniqueTracks else 0,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "wrapped_tracks"
    )
    val animatedArtists by animateIntAsState(
        targetValue = if (revealed) stats.uniqueArtists else 0,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "wrapped_artists"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(230.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f
                listOf(1f, 0.80f, 0.62f).forEachIndexed { i, ratio ->
                    drawCircle(
                        brush = Brush.sweepGradient(listOf(primaryColor, secondaryColor, primaryColor)),
                        radius = maxR * ratio,
                        center = center,
                        alpha = 0.35f + i * 0.18f,
                        style = Stroke(width = (3.5f - i * 0.7f).dp.toPx())
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$animatedMinutes",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.5).sp
                    ),
                    color = TextPrimary
                )
                Text(
                    text = "MINUTES STREAMED",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp),
                    color = primaryColor
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = if (stats.totalPlays > 0) "Your Sound Universe in Numbers" else "Your Sonic Story Awaits",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (stats.totalPlays > 0) {
                "Every stream, deep cut, and late-night repeat captured cleanly on-device."
            } else {
                "Play your favorite tracks, podcasts, or radio to light up your personal Wrapped."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricStatTile("Total Plays", "$animatedPlays", primaryColor, Modifier.weight(1f))
            MetricStatTile("Unique Songs", "$animatedTracks", secondaryColor, Modifier.weight(1f))
            MetricStatTile("Artists", "$animatedArtists", VioletPulse, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricStatTile(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianCard.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.40f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = tint
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WrappedTopTracksSlide(
    items: List<WrappedTopItem>,
    primaryColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Tracks on Heavy Rotation",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "The songs that defined your sessions",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(18.dp))

        if (items.isEmpty()) {
            WrappedEmptySlideCard("Listen to a few tracks to reveal your Top 5 songs.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.take(5).forEachIndexed { idx, item ->
                    WrappedRankedRow(
                        rank = idx + 1,
                        item = item,
                        isCircle = false,
                        accent = if (idx == 0) primaryColor else CyanGlow
                    )
                }
            }
        }
    }
}

@Composable
private fun WrappedTopArtistsSlide(
    items: List<WrappedTopItem>,
    primaryColor: Color,
    tertiaryColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Your Top Artists",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Voices and producers leading your listening hours",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(18.dp))

        if (items.isEmpty()) {
            WrappedEmptySlideCard("Stream music from different creators to unlock your Top Artists.")
        } else {
            val lead = items.first()
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ObsidianCard.copy(alpha = 0.84f),
                border = BorderStroke(1.2.dp, primaryColor.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SonoraArtwork(
                        artworkUrl = lead.artworkUrl,
                        title = lead.name,
                        isCircle = true,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        SonoraBadge(text = "#1 TOP ARTIST", tint = primaryColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lead.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${lead.playCount} plays • ${lead.totalMinutes} mins",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.drop(1).take(4).forEachIndexed { idx, item ->
                    WrappedRankedRow(
                        rank = idx + 2,
                        item = item,
                        isCircle = true,
                        accent = tertiaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun WrappedTopGenresSlide(
    items: List<WrappedTopItem>,
    palette: List<Color>
) {
    var animateBars by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        animateBars = true
    }

    val maxPlays = remember(items) {
        items.maxOfOrNull { maxOf(it.playCount, it.totalMinutes) }?.coerceAtLeast(1) ?: 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Your Sound DNA",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "How your taste spreads across vibes & genres",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(22.dp))

        if (items.isEmpty()) {
            WrappedEmptySlideCard("Explore genres on Home or Search to paint your Sound DNA.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items.take(5).forEachIndexed { idx, genre ->
                    val barColor = palette[idx % palette.size]
                    val score = maxOf(genre.playCount, genre.totalMinutes)
                    val targetFraction = (score.toFloat() / maxPlays.toFloat()).coerceIn(0.18f, 1f)
                    val barAnim = remember(genre.name) { Animatable(0f) }
                    LaunchedEffect(animateBars) {
                        if (animateBars) {
                            delay(idx * 90L)
                            barAnim.animateTo(
                                targetValue = targetFraction,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${idx + 1}. ${genre.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${genre.playCount} plays • ${genre.totalMinutes}m",
                                style = MaterialTheme.typography.bodyMedium,
                                color = barColor
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(CircleShape)
                                .background(ObsidianCard)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(barAnim.value)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(barColor, palette[(idx + 1) % palette.size])
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WrappedPersonaSlide(
    stats: WrappedStats,
    palette: List<Color>,
    onShare: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(138.dp)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(palette[0], palette[1], palette[2], palette[0])
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(ObsidianBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = palette[0],
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        SonoraBadge(text = "YOUR LISTENER PERSONA", tint = AmberGold)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stats.personaTitle,
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stats.personaSubtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ObsidianCard.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, ObsidianStrokeSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.totalMinutes}",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette[0]
                    )
                    Text("Minutes", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stats.topGenres.firstOrNull()?.name ?: "Eclectic",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette[1],
                        maxLines = 1
                    )
                    Text("Top Genre", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.totalPlays}",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette[2]
                    )
                    Text("Streams", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        Button(
            onClick = onShare,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette[0],
                contentColor = ObsidianBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Your Wrapped", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onClose,
            shape = CircleShape,
            border = BorderStroke(1.dp, ObsidianStroke),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Back to Sonora", color = TextPrimary)
        }
    }
}

@Composable
private fun WrappedRankedRow(
    rank: Int,
    item: WrappedTopItem,
    isCircle: Boolean,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianCard.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, ObsidianStrokeSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (rank == 1) accent else TextSecondary,
                modifier = Modifier.width(34.dp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(6.dp))
            SonoraArtwork(
                artworkUrl = item.artworkUrl,
                title = item.name,
                isCircle = isCircle,
                cornerRadius = 10.dp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "${item.playCount}x",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
}

@Composable
private fun WrappedEmptySlideCard(message: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianCard.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, ObsidianStrokeSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}
