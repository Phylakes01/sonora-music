package com.sonora.music.ui.player

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.model.PlaybackState
import com.sonora.music.model.Track
import com.sonora.music.ui.components.AnimatedPlayingBars
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary

private val QueueSheetBg = Color(0xFF161618)
private val QueueCardBg = Color(0xFF1E1E22)
private val QueueCardBorder = Color(0xFF2A2A30)
private val QueueDivider = Color(0xFF26262A)

/**
 * Refined matte #161618 Queue & Jam sheet displaying Now Playing and Up Next tracks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    playback: PlaybackState,
    @Suppress("UNUSED_PARAMETER") accentColor: Color,
    onPlayTrackInQueue: (Track, List<Track>) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveItem: (index: Int) -> Unit,
    onClearUpNext: () -> Unit,
    onOpenJamShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentTrack = playback.queue.getOrNull(playback.currentIndex) ?: playback.currentTrack
    val upNextStartIndex = (playback.currentIndex + 1).coerceAtLeast(0)
    val upNextTracks = if (upNextStartIndex < playback.queue.size) {
        playback.queue.subList(upNextStartIndex, playback.queue.size)
    } else {
        emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = QueueSheetBg,
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
                .fillMaxHeight(0.86f)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Queue",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "${upNextTracks.size} up next",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = TextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF28282E))
                            .clickable(onClick = onOpenJamShare)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GroupAdd,
                            contentDescription = "Jam Queue Share",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Jam Share",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                            color = TextPrimary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close queue",
                            tint = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = QueueDivider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentTrack != null) {
                    item(key = "now_playing_header") {
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp
                            ),
                            color = TextSecondary
                        )
                    }

                    item(key = "now_playing_card") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(QueueCardBg)
                                .border(1.dp, QueueCardBorder, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SonoraArtwork(
                                artworkUrl = currentTrack.artworkUrl,
                                title = currentTrack.title,
                                modifier = Modifier.size(48.dp),
                                cornerRadius = 6.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentTrack.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentTrack.displayArtist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            AnimatedPlayingBars(
                                isPlaying = playback.isPlaying,
                                color = EmeraldAccent,
                                modifier = Modifier.size(width = 18.dp, height = 14.dp)
                            )
                        }
                    }

                    item(key = "queue_section_divider") {
                        HorizontalDivider(
                            color = QueueDivider,
                            thickness = 1.dp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                item(key = "up_next_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "UP NEXT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp
                            ),
                            color = TextSecondary
                        )

                        if (upNextTracks.isNotEmpty()) {
                            TextButton(onClick = onClearUpNext) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Clear Up Next",
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                if (upNextTracks.isEmpty()) {
                    item(key = "empty_up_next") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(QueueCardBg)
                                .border(1.dp, QueueCardBorder, RoundedCornerShape(12.dp))
                                .padding(22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Queue is clear. Add songs or enable Smart Shuffle.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = upNextTracks,
                        key = { idx, t -> "${t.id}_${upNextStartIndex + idx}" }
                    ) { offsetIdx, track ->
                        val absIndex = upNextStartIndex + offsetIdx
                        val canMoveUp = absIndex > upNextStartIndex
                        val canMoveDown = absIndex < playback.queue.lastIndex

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(QueueCardBg)
                                .border(1.dp, QueueCardBorder, RoundedCornerShape(12.dp))
                                .clickable { onPlayTrackInQueue(track, playback.queue) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SonoraArtwork(
                                artworkUrl = track.artworkUrl,
                                title = track.title,
                                modifier = Modifier.size(44.dp),
                                cornerRadius = 6.dp
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        if (track.isSmartPick) append("Smart Pick · ")
                                        append(track.displayArtist)
                                        if (!track.isLive && track.durationSec > 0) {
                                            append(" · ${track.formattedDuration}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 12.sp,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = if (track.isSmartPick) EmeraldAccent else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onMoveItem(absIndex, absIndex - 1) },
                                enabled = canMoveUp,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Move up",
                                    tint = if (canMoveUp) TextPrimary else TextMuted.copy(alpha = 0.3f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            IconButton(
                                onClick = { onMoveItem(absIndex, absIndex + 1) },
                                enabled = canMoveDown,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "Move down",
                                    tint = if (canMoveDown) TextPrimary else TextMuted.copy(alpha = 0.3f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            IconButton(
                                onClick = { onRemoveItem(absIndex) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove from queue",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
