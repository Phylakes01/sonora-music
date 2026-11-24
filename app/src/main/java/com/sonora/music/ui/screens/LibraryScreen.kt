package com.sonora.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.music.model.DetailRoute
import com.sonora.music.model.DownloadStatus
import com.sonora.music.model.Playlist
import com.sonora.music.model.SonoraActions
import com.sonora.music.model.SonoraUiState
import com.sonora.music.model.Track
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.components.SonoraPillChip
import com.sonora.music.ui.components.SonoraSectionHeader
import com.sonora.music.ui.components.TrackRow
import com.sonora.music.ui.components.formatBytesMb
import com.sonora.music.ui.components.sonoraPressable
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.EmeraldDeep
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianSurface
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary

private enum class LibraryFilter(val label: String) {
    ALL("All"),
    PLAYLISTS("Playlists"),
    LIKED("Liked"),
    DOWNLOADS("Downloads"),
    LOCAL("Local Files"),
    JAM("Jam")
}

@Composable
fun LibraryScreen(
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    onOpenCreatePlaylist: () -> Unit,
    onOpenImportDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val library = state.library
    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 148.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clean Top Bar
            item(key = "library_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Library",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF202024),
                            border = BorderStroke(1.dp, Color(0xFF2B2B30)),
                            modifier = Modifier
                                .size(36.dp)
                                .sonoraPressable(shape = CircleShape, onClick = onOpenImportDialog)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = "Import Share Code",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF202024),
                            border = BorderStroke(1.dp, Color(0xFF2B2B30)),
                            modifier = Modifier
                                .size(36.dp)
                                .sonoraPressable(shape = CircleShape, onClick = onOpenCreatePlaylist)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Create Playlist",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Flat Filter Chips
            item(key = "library_filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(LibraryFilter.entries, key = { it.name }) { filter ->
                        SonoraPillChip(
                            label = filter.label,
                            selected = selectedFilter == filter,
                            accentColor = EmeraldAccent,
                            onClick = { selectedFilter = filter }
                        )
                    }
                }
            }

            // Pinned Collections: Liked Songs & Downloads
            if (selectedFilter == LibraryFilter.ALL ||
                selectedFilter == LibraryFilter.LIKED ||
                selectedFilter == LibraryFilter.DOWNLOADS
            ) {
                if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.LIKED) {
                    item(key = "pinned_liked") {
                        PinnedLibraryRow(
                            title = "Liked Songs",
                            subtitle = "Playlist • ${library.likedSongs.size} songs",
                            icon = Icons.Rounded.Favorite,
                            coverBrush = Brush.linearGradient(
                                listOf(Color(0xFF4527A0), Color(0xFF281A5E))
                            ),
                            onClick = { actions.openDetail(DetailRoute.LikedSongs) },
                            onQuickPlay = if (library.likedSongs.isNotEmpty()) {
                                { actions.playTrack(library.likedSongs.first(), library.likedSongs) }
                            } else null
                        )
                    }
                }

                if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.DOWNLOADS) {
                    item(key = "pinned_downloads") {
                        val mbUsed = formatBytesMb(library.totalDownloadedBytes)
                        PinnedLibraryRow(
                            title = "Downloads",
                            subtitle = "${library.downloadedTracks.size} tracks • $mbUsed",
                            icon = Icons.Rounded.DownloadDone,
                            coverBrush = Brush.linearGradient(
                                listOf(EmeraldDeep, Color(0xFF0E3820))
                            ),
                            onClick = { actions.openDetail(DetailRoute.Downloads) },
                            onQuickPlay = if (library.downloadedTracks.isNotEmpty()) {
                                { actions.playTrack(library.downloadedTracks.first(), library.downloadedTracks) }
                            } else null
                        )
                    }
                }
            }

            // Jam Queue Row
            if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.JAM) {
                item(key = "jam_queue_row") {
                    PinnedLibraryRow(
                        title = "Jam Queue",
                        subtitle = if (state.playback.queue.isEmpty()) {
                            "Share or merge queue codes"
                        } else {
                            "${state.playback.queue.size} tracks queued"
                        },
                        icon = Icons.Rounded.Groups,
                        coverBrush = Brush.linearGradient(
                            listOf(ObsidianCard, ObsidianCard)
                        ),
                        onClick = { actions.openDetail(DetailRoute.JamQueue) },
                        onQuickPlay = if (state.playback.queue.isNotEmpty()) {
                            { actions.openQueue() }
                        } else null
                    )
                }
            }

            // User Playlists
            if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.PLAYLISTS) {
                if (selectedFilter == LibraryFilter.ALL && library.userPlaylists.isNotEmpty()) {
                    item(key = "playlists_divider") {
                        HorizontalDivider(
                            color = ObsidianStroke,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                if (library.userPlaylists.isEmpty()) {
                    item(key = "playlists_empty") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenCreatePlaylist)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF202024)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Create playlist",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Build a mix or import a code",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(library.userPlaylists, key = { "lib_pl_${it.id}" }) { playlist ->
                        UserPlaylistRow(
                            playlist = playlist,
                            onClick = { actions.openDetail(DetailRoute.PlaylistDetail(playlist)) },
                            onQuickPlay = {
                                if (playlist.tracks.isNotEmpty()) {
                                    actions.playTrack(playlist.tracks.first(), playlist.tracks)
                                } else {
                                    actions.openDetail(DetailRoute.PlaylistDetail(playlist))
                                }
                            }
                        )
                    }
                }
            }

            // Liked Tracks when filtered to Liked
            if (selectedFilter == LibraryFilter.LIKED && library.likedSongs.isNotEmpty()) {
                item(key = "liked_tracks_header") {
                    SonoraSectionHeader(
                        title = "Songs",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(library.likedSongs.take(15), key = { "lib_liked_${it.id}" }) { track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    TrackRow(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && state.playback.isPlaying,
                        isLiked = true,
                        downloadState = state.downloads[track.id],
                        onClick = { actions.playTrack(track, library.likedSongs) },
                        onLikeClick = { actions.toggleLike(track) },
                        onDownloadClick = {
                            if (state.downloads[track.id]?.status == DownloadStatus.DOWNLOADED) {
                                actions.deleteDownload(track.id)
                            } else {
                                actions.downloadTrack(track)
                            }
                        },
                        onMoreClick = { onTrackOverflow(track) }
                    )
                }
            }

            // Downloaded Tracks when filtered to Downloads
            if (selectedFilter == LibraryFilter.DOWNLOADS && library.downloadedTracks.isNotEmpty()) {
                item(key = "dl_tracks_header") {
                    SonoraSectionHeader(
                        title = "Downloaded",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(library.downloadedTracks.take(15), key = { "lib_dl_${it.id}" }) { track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    TrackRow(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && state.playback.isPlaying,
                        isLiked = track.id in state.likedTrackIds,
                        downloadState = state.downloads[track.id],
                        onClick = { actions.playTrack(track, library.downloadedTracks) },
                        onLikeClick = { actions.toggleLike(track) },
                        onDownloadClick = { actions.deleteDownload(track.id) },
                        onMoreClick = { onTrackOverflow(track) }
                    )
                }
            }

            // Local Files Scan Card
            if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.LOCAL) {
                item(key = "local_files_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SonoraSectionHeader(
                            title = "Local Files",
                            actionLabel = if (library.localTracks.isNotEmpty()) "View All" else null,
                            onActionClick = { actions.openDetail(DetailRoute.LocalFiles) }
                        )

                        LocalFilesCard(
                            hasPermission = library.hasLocalAudioPermission,
                            isScanning = library.isScanningLocal,
                            trackCount = library.localTracks.size,
                            onRequestPermission = { actions.requestLocalAudioPermission() },
                            onScanDevice = { actions.scanLocalFiles() },
                            onOpenAll = { actions.openDetail(DetailRoute.LocalFiles) }
                        )
                    }
                }

                if (library.hasLocalAudioPermission && library.localTracks.isNotEmpty()) {
                    val visibleCount = if (selectedFilter == LibraryFilter.LOCAL) 15 else 4
                    items(library.localTracks.take(visibleCount), key = { "lib_local_${it.id}" }) { track ->
                        val isCurrent = state.playback.currentTrack?.id == track.id
                        TrackRow(
                            track = track,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && state.playback.isPlaying,
                            isLiked = track.id in state.likedTrackIds,
                            downloadState = null,
                            onClick = { actions.playTrack(track, library.localTracks) },
                            onLikeClick = { actions.toggleLike(track) },
                            onDownloadClick = {},
                            onMoreClick = { onTrackOverflow(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedLibraryRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    coverBrush: Brush,
    onClick: () -> Unit,
    onQuickPlay: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(coverBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onQuickPlay != null) {
            Surface(
                shape = CircleShape,
                color = ObsidianCard,
                modifier = Modifier
                    .size(34.dp)
                    .sonoraPressable(shape = CircleShape, onClick = onQuickPlay)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = TextMuted
            )
        }
    }
}

@Composable
private fun UserPlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onQuickPlay: () -> Unit
) {
    val count = maxOf(playlist.tracks.size, playlist.trackIds.size)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SonoraArtwork(
            artworkUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
            title = playlist.name,
            cornerRadius = 8.dp,
            colorStart = playlist.coverColorStart,
            colorEnd = playlist.coverColorEnd,
            modifier = Modifier.size(56.dp)
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
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Playlist • $count tracks",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = CircleShape,
            color = ObsidianCard,
            modifier = Modifier
                .size(34.dp)
                .sonoraPressable(shape = CircleShape, onClick = onQuickPlay)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play playlist",
                    tint = TextPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun LocalFilesCard(
    hasPermission: Boolean,
    isScanning: Boolean,
    trackCount: Int,
    onRequestPermission: () -> Unit,
    onScanDevice: () -> Unit,
    onOpenAll: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A1A1D),
        border = BorderStroke(1.dp, ObsidianStroke),
        modifier = Modifier
            .fillMaxWidth()
            .sonoraPressable(
                shape = RoundedCornerShape(12.dp),
                onClick = if (hasPermission) onOpenAll else onRequestPermission
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF202024)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasPermission) Icons.Rounded.LibraryMusic else Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasPermission) "Device Audio ($trackCount files)" else "Show Local Audio Files",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (hasPermission) {
                            "Scan storage for MP3, FLAC, WAV, and M4A files"
                        } else {
                            "Allow audio access to play files on this device"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasPermission) {
                Button(
                    onClick = onRequestPermission,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldAccent,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Access", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onScanDevice,
                        enabled = !isScanning,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, ObsidianStroke),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                color = EmeraldAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isScanning) "Scanning..." else "Scan Device",
                            color = TextPrimary
                        )
                    }

                    if (trackCount > 0) {
                        Button(
                            onClick = onOpenAll,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldAccent,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Browse", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
