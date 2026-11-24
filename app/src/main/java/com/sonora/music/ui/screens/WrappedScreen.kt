package com.sonora.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.model.WrappedStats
import com.sonora.music.model.WrappedTopItem
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianSurface
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Story Progress Bars
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
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fillFraction.coerceIn(0f, 1f))
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Clean Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "LISTENING STATS",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "${currentSlide + 1}/$WrappedSlideCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share stats",
                            tint = TextPrimary
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentSlide,
                    transitionSpec = {
                        (slideInHorizontally(
                            animationSpec = tween(220)
                        ) { fullWidth -> fullWidth / 4 * slideDirection } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(180)
                            ) { fullWidth -> -fullWidth / 4 * slideDirection } + fadeOut(tween(160)))
                    },
                    label = "stats_slide_content"
                ) { slideIdx ->
                    when (slideIdx) {
                        0 -> WrappedMinutesSlide(stats = stats)
                        1 -> WrappedTopTracksSlide(items = stats.topTracks)
                        2 -> WrappedTopArtistsSlide(items = stats.topArtists)
                        3 -> WrappedTopGenresSlide(items = stats.topGenres)
                        else -> WrappedPersonaSlide(
                            stats = stats,
                            onShare = onShare,
                            onClose = onClose
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentSlide > 0) "Tap left for previous" else "Hold to pause",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Text(
                    text = if (currentSlide < WrappedSlideCount - 1) "Tap right for next" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun WrappedMinutesSlide(stats: WrappedStats) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        revealed = true
    }

    val animatedMinutes by animateIntAsState(
        targetValue = if (revealed) stats.totalMinutes else 0,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "wrapped_mins"
    )
    val animatedPlays by animateIntAsState(
        targetValue = if (revealed) stats.totalPlays else 0,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "wrapped_plays"
    )
    val animatedTracks by animateIntAsState(
        targetValue = if (revealed) stats.uniqueTracks else 0,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "wrapped_tracks"
    )
    val animatedArtists by animateIntAsState(
        targetValue = if (revealed) stats.uniqueArtists else 0,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "wrapped_artists"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$animatedMinutes",
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.5).sp
            ),
            color = EmeraldAccent
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "MINUTES PLAYED",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (stats.totalPlays > 0) "Your listening summary" else "No listening activity yet",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (stats.totalPlays > 0) {
                "Calculated directly from your recent listening history."
            } else {
                "Play songs, podcasts, or radio to track your listening stats."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricStatTile("Plays", "$animatedPlays", Modifier.weight(1f))
            MetricStatTile("Tracks", "$animatedTracks", Modifier.weight(1f))
            MetricStatTile("Artists", "$animatedArtists", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianStroke),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
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
private fun WrappedTopTracksSlide(items: List<WrappedTopItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Top Songs",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your most replayed tracks",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(18.dp))

        if (items.isEmpty()) {
            WrappedEmptySlideCard("Listen to tracks to see your Top 5 songs.")
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    items.take(5).forEachIndexed { idx, item ->
                        WrappedRankedRow(
                            rank = idx + 1,
                            item = item,
                            isCircle = false
                        )
                        if (idx < items.take(5).lastIndex) {
                            HorizontalDivider(color = ObsidianStroke, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WrappedTopArtistsSlide(items: List<WrappedTopItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Top Artists",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your most played artists",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(18.dp))

        if (items.isEmpty()) {
            WrappedEmptySlideCard("Play songs across artists to see your Top Artists.")
        } else {
            val lead = items.first()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianStroke),
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
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#1 ARTIST",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldAccent
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lead.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${lead.playCount} plays • ${lead.totalMinutes}m",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (items.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, ObsidianStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        items.drop(1).take(4).forEachIndexed { idx, item ->
                            WrappedRankedRow(
                                rank = idx + 2,
                                item = item,
                                isCircle = true
                            )
                            if (idx < items.drop(1).take(4).lastIndex) {
                                HorizontalDivider(color = ObsidianStroke, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WrappedTopGenresSlide(items: List<WrappedTopItem>) {
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
            text = "Top Genres",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Breakdown of your recent listening",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (items.isEmpty()) {
            WrappedEmptySlideCard("Listen to a few genres to unlock this breakdown.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items.take(5).forEachIndexed { idx, genre ->
                    val score = maxOf(genre.playCount, genre.totalMinutes)
                    val targetFraction = (score.toFloat() / maxPlays.toFloat()).coerceIn(0.18f, 1f)
                    val barAnim = remember(genre.name) { Animatable(0f) }
                    LaunchedEffect(animateBars) {
                        if (animateBars) {
                            delay(idx * 70L)
                            barAnim.animateTo(
                                targetValue = targetFraction,
                                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
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
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(ObsidianCard)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(barAnim.value)
                                    .clip(CircleShape)
                                    .background(if (idx == 0) EmeraldAccent else TextPrimary)
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
        Text(
            text = "LISTENING PROFILE",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold
            ),
            color = EmeraldAccent
        )
        Spacer(modifier = Modifier.height(8.dp))
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

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianStroke),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.totalMinutes}",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text("Minutes", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stats.topGenres.firstOrNull()?.name ?: "Mixed",
                        style = MaterialTheme.typography.titleLarge,
                        color = EmeraldAccent,
                        maxLines = 1
                    )
                    Text("Top Genre", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.totalPlays}",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text("Streams", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onShare,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldAccent,
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Stats", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onClose,
            shape = CircleShape,
            border = BorderStroke(1.dp, ObsidianStroke),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Text("Done", color = TextPrimary)
        }
    }
}

@Composable
private fun WrappedRankedRow(
    rank: Int,
    item: WrappedTopItem,
    isCircle: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (rank == 1) EmeraldAccent else TextSecondary,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.width(8.dp))
        SonoraArtwork(
            artworkUrl = item.artworkUrl,
            title = item.name,
            isCircle = isCircle,
            cornerRadius = 4.dp,
            modifier = Modifier.size(44.dp)
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
            color = TextSecondary
        )
    }
}

@Composable
private fun WrappedEmptySlideCard(message: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianStroke),
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
