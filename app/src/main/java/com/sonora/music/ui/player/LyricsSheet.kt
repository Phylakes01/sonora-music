package com.sonora.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.model.LyricsResult
import com.sonora.music.model.Track
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianSurface
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Interactive karaoke lyrics sheet with auto-scrolling synced lines, tap-to-seek,
 * plain lyrics fallback, and graceful instrumental/unavailable states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    track: Track?,
    lyrics: LyricsResult?,
    isLoading: Boolean,
    positionMs: Long,
    accentColor: Color,
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val syncedLines = lyrics?.syncedLines.orEmpty()
    val activeIndex = if (syncedLines.isNotEmpty()) {
        syncedLines.indexOfLast { it.timeMs <= positionMs }
    } else {
        -1
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(index = (activeIndex - 2).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            ObsidianSurface,
                            ObsidianBg
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = track?.title ?: "Lyrics",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val modeLabel = when {
                            syncedLines.isNotEmpty() -> "SYNCED"
                            !lyrics?.plainLyrics.isNullOrBlank() -> "LYRICS"
                            else -> null
                        }
                        if (modeLabel != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(accentColor.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = modeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accentColor
                                )
                            }
                        }
                    }
                    Text(
                        text = track?.displayArtist ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (syncedLines.isNotEmpty() && activeIndex >= 0) {
                    IconButton(onClick = {
                        scope.launch {
                            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Jump to current line",
                            tint = accentColor
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close lyrics",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = accentColor)
                            Text(
                                text = "Fetching synced lyrics...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                syncedLines.isNotEmpty() -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 28.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(syncedLines, key = { index, line -> "${line.timeMs}_$index" }) { index, line ->
                            val isActive = index == activeIndex
                            val isPast = activeIndex >= 0 && index < activeIndex
                            val textColor by animateColorAsState(
                                targetValue = when {
                                    isActive -> TextPrimary
                                    isPast -> TextSecondary.copy(alpha = 0.55f)
                                    else -> TextMuted.copy(alpha = 0.72f)
                                },
                                label = "lyric_line_color"
                            )
                            val lineScale by animateFloatAsState(
                                targetValue = if (isActive) 1.03f else 1f,
                                animationSpec = spring(),
                                label = "lyric_line_scale"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(lineScale)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isActive) accentColor.copy(alpha = 0.14f) else Color.Transparent
                                    )
                                    .border(
                                        width = if (isActive) 1.dp else 0.dp,
                                        color = if (isActive) accentColor.copy(alpha = 0.35f) else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSeekTo(line.timeMs)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = line.text.ifBlank { "♪" },
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = if (isActive) 22.sp else 18.sp,
                                        lineHeight = if (isActive) 30.sp else 26.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold
                                    ),
                                    color = textColor
                                )
                            }
                        }
                    }
                }

                !lyrics?.plainLyrics.isNullOrBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = lyrics!!.plainLyrics!!,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 18.sp,
                                lineHeight = 30.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextPrimary.copy(alpha = 0.92f)
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(ObsidianCard.copy(alpha = 0.72f))
                                .border(1.dp, ObsidianStroke, RoundedCornerShape(24.dp))
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = if (lyrics?.isInstrumental == true) {
                                    "Pure Instrumental"
                                } else {
                                    "Lyrics Unavailable"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = if (lyrics?.isInstrumental == true) {
                                    "No vocals on this track — lean back and watch the wave."
                                } else {
                                    "We couldn't match lyrics for this track yet."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
