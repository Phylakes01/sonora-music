package com.sonora.music.ui.player

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.HorizontalDivider
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
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private val LyricsSheetBg = Color(0xFF161618)
private val LyricsActiveCardBg = Color(0xFF1F1F24)
private val LyricsCardBorder = Color(0xFF2B2B32)
private val LyricsDivider = Color(0xFF26262A)

/**
 * Refined matte #161618 lyrics sheet with auto-scrolling synced lines, tap-to-seek,
 * crisp dividers, smooth karaoke typography, and plain/instrumental states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    track: Track?,
    lyrics: LyricsResult?,
    isLoading: Boolean,
    positionMs: Long,
    @Suppress("UNUSED_PARAMETER") accentColor: Color,
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
        containerColor = LyricsSheetBg,
        contentColor = TextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34343A))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp, vertical = 12.dp)
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
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
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
                                    .background(Color(0xFF28282E))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = modeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = EmeraldAccent
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
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
                            tint = EmeraldAccent
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

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = LyricsDivider, thickness = 1.dp)
            if (syncedLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap any line to seek",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

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
                            CircularProgressIndicator(color = EmeraldAccent)
                            Text(
                                text = "Fetching lyrics...",
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
                        contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(syncedLines, key = { index, line -> "${line.timeMs}_$index" }) { index, line ->
                            val isActive = index == activeIndex
                            val isPast = activeIndex >= 0 && index < activeIndex
                            val textColor by animateColorAsState(
                                targetValue = when {
                                    isActive -> Color.White
                                    isPast -> TextSecondary.copy(alpha = 0.48f)
                                    else -> TextSecondary.copy(alpha = 0.80f)
                                },
                                label = "lyric_line_color"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isActive) LyricsActiveCardBg else Color.Transparent)
                                    .then(
                                        if (isActive) {
                                            Modifier.border(1.dp, LyricsCardBorder, RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier
                                        }
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
                                        lineHeight = if (isActive) 30.sp else 25.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
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
                            .padding(vertical = 10.dp)
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
                                .clip(RoundedCornerShape(14.dp))
                                .background(LyricsActiveCardBg)
                                .border(1.dp, LyricsCardBorder, RoundedCornerShape(14.dp))
                                .padding(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A2A30)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = if (lyrics?.isInstrumental == true) {
                                    "Instrumental"
                                } else {
                                    "Lyrics Unavailable"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = if (lyrics?.isInstrumental == true) {
                                    "This track is instrumental."
                                } else {
                                    "Lyrics aren't available for this track yet."
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
