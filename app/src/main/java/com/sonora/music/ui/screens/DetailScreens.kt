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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.model.Artist
import com.sonora.music.model.DetailRoute
import com.sonora.music.model.DownloadStatus
import com.sonora.music.model.Playlist
import com.sonora.music.model.ShuffleMode
import com.sonora.music.model.SonoraActions
import com.sonora.music.model.SonoraUiState
import com.sonora.music.model.Track
import com.sonora.music.ui.components.ShimmerRowPlaceholder
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.components.TrackRow
import com.sonora.music.ui.components.formatBytesMb
import com.sonora.music.ui.components.sonoraPressable
import com.sonora.music.ui.theme.CoralHeart
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.EmeraldDeep
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianCardElevated
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianSurface
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary

@Composable
fun DetailScreenHost(
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    onOpenAddTrackPicker: (Playlist) -> Unit,
    onOpenRenamePlaylist: (Playlist) -> Unit,
    onOpenShareImportPlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val route = state.detail.route
    if (route is DetailRoute.None) return

    var confirmDeletePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var confirmClearDownloads by remember { mutableStateOf(false) }

    Surface(
        color = ObsidianBg,
        modifier = modifier.fillMaxSize()
    ) {
        when (route) {
            is DetailRoute.PlaylistDetail -> {
                val userMatch = state.library.userPlaylists.find { it.id == route.playlist.id }
                val livePlaylist = userMatch
                    ?: state.detail.playlist?.takeIf { it.id == route.playlist.id }
                    ?: route.playlist
                val tracks = if (userMatch != null) {
                    userMatch.tracks
                } else {
                    state.detail.tracks.ifEmpty { livePlaylist.tracks }
                }
                PlaylistDetailView(
                    playlist = livePlaylist,
                    tracks = tracks,
                    isLoading = state.detail.isLoading && tracks.isEmpty(),
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    onAddTrack = { onOpenAddTrackPicker(livePlaylist) },
                    onRename = { onOpenRenamePlaylist(livePlaylist) },
                    onShareCode = { onOpenShareImportPlaylist(livePlaylist) },
                    onDeleteRequest = { confirmDeletePlaylist = livePlaylist }
                )
            }

            is DetailRoute.ArtistDetail -> {
                val artist = state.detail.artist ?: route.artist
                val tracks = state.detail.tracks
                ArtistDetailView(
                    artist = artist,
                    tracks = tracks,
                    isFollowing = artist.id in state.library.followedArtistIds,
                    isLoading = state.detail.isLoading && tracks.isEmpty(),
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow
                )
            }

            is DetailRoute.PodcastDetail -> {
                val show = state.detail.podcast ?: route.show
                val episodes = state.detail.tracks
                MediaCollectionDetailView(
                    contextLabel = "PODCAST",
                    title = show.title,
                    subtitle = "${show.author} • ${episodes.size} episodes",
                    description = null,
                    artworkUrl = show.artworkUrl,
                    tracks = episodes,
                    isLoading = state.detail.isLoading && episodes.isEmpty(),
                    emptyText = "No episodes available for this show.",
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    secondaryActions = if (episodes.isNotEmpty()) {
                        listOf(
                            DetailActionSpec(Icons.Rounded.DownloadForOffline, "Download") {
                                actions.downloadPlaylist(episodes)
                            }
                        )
                    } else emptyList()
                )
            }

            is DetailRoute.AudiobookDetail -> {
                val book = state.detail.audiobook ?: route.book
                val chapters = state.detail.tracks
                MediaCollectionDetailView(
                    contextLabel = "AUDIOBOOK",
                    title = book.title,
                    subtitle = "${book.author.ifBlank { "LibriVox" }} • ${chapters.size} chapters",
                    description = book.description.takeIf { it.isNotBlank() },
                    artworkUrl = book.artworkUrl,
                    tracks = chapters,
                    isLoading = state.detail.isLoading && chapters.isEmpty(),
                    emptyText = "No audio chapters found for this title.",
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    secondaryActions = if (chapters.isNotEmpty()) {
                        listOf(
                            DetailActionSpec(Icons.Rounded.DownloadForOffline, "Download") {
                                actions.downloadPlaylist(chapters)
                            }
                        )
                    } else emptyList()
                )
            }

            is DetailRoute.LikedSongs -> {
                val tracks = state.library.likedSongs
                SpecialCollectionDetailView(
                    contextLabel = "PLAYLIST",
                    title = "Liked Songs",
                    subtitle = "${tracks.size} songs",
                    icon = Icons.Rounded.Favorite,
                    headerTint = Color(0xFF4527A0),
                    coverBrush = Brush.linearGradient(listOf(Color(0xFF4527A0), Color(0xFF281A5E))),
                    tracks = tracks,
                    emptyTitle = "Songs you like will appear here",
                    emptySubtitle = "Save songs by tapping the heart icon.",
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    secondaryActions = listOfNotNull(
                        if (tracks.isNotEmpty()) {
                            DetailActionSpec(Icons.Rounded.Shuffle, "Shuffle") {
                                if (state.playback.shuffleMode == ShuffleMode.OFF) {
                                    actions.toggleShuffle()
                                }
                                actions.playTrack(tracks.random(), tracks)
                            }
                        } else null,
                        if (tracks.isNotEmpty()) {
                            DetailActionSpec(Icons.Rounded.DownloadForOffline, "Download") {
                                actions.downloadPlaylist(tracks)
                            }
                        } else null
                    )
                )
            }

            is DetailRoute.Downloads -> {
                val tracks = state.library.downloadedTracks
                val usedMb = formatBytesMb(state.library.totalDownloadedBytes)
                SpecialCollectionDetailView(
                    contextLabel = "DOWNLOADS",
                    title = "Downloads",
                    subtitle = "${tracks.size} tracks • $usedMb",
                    icon = Icons.Rounded.DownloadForOffline,
                    headerTint = EmeraldDeep,
                    coverBrush = Brush.linearGradient(listOf(EmeraldDeep, Color(0xFF0E3820))),
                    tracks = tracks,
                    emptyTitle = "No downloads yet",
                    emptySubtitle = "Downloaded tracks and episodes appear here for offline playback.",
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    secondaryActions = listOfNotNull(
                        if (tracks.isNotEmpty()) {
                            DetailActionSpec(Icons.Rounded.DeleteOutline, "Clear All") {
                                confirmClearDownloads = true
                            }
                        } else null
                    )
                )
            }

            is DetailRoute.LocalFiles -> {
                val tracks = state.library.localTracks
                SpecialCollectionDetailView(
                    contextLabel = "LOCAL FILES",
                    title = "Local Audio",
                    subtitle = "${tracks.size} files on device",
                    icon = Icons.Rounded.LibraryMusic,
                    headerTint = Color(0xFF2D3748),
                    coverBrush = Brush.linearGradient(listOf(ObsidianCardElevated, ObsidianCard)),
                    tracks = tracks,
                    emptyTitle = if (state.library.hasLocalAudioPermission) {
                        "No audio files found"
                    } else {
                        "Audio permission required"
                    },
                    emptySubtitle = if (state.library.hasLocalAudioPermission) {
                        "Tap Scan Device to discover MP3, FLAC, WAV, and M4A files."
                    } else {
                        "Allow audio access to browse songs stored on this device."
                    },
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    secondaryActions = listOf(
                        if (!state.library.hasLocalAudioPermission) {
                            DetailActionSpec(Icons.Rounded.FolderOpen, "Grant Access") {
                                actions.requestLocalAudioPermission()
                            }
                        } else {
                            DetailActionSpec(Icons.Rounded.Refresh, "Scan Device") {
                                actions.scanLocalFiles()
                            }
                        }
                    )
                )
            }

            is DetailRoute.JamQueue -> {
                JamQueueDetailView(
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    onShareJamCode = { jamPlaylist -> onOpenShareImportPlaylist(jamPlaylist) }
                )
            }

            DetailRoute.None -> Unit
        }
    }

    confirmDeletePlaylist?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeletePlaylist = null },
            containerColor = ObsidianSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("This removes the playlist from your library.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.deletePlaylist(target.id)
                        confirmDeletePlaylist = null
                        actions.closeDetail()
                    }
                ) {
                    Text("Delete", color = CoralHeart, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePlaylist = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            containerColor = ObsidianSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Remove all downloads?") },
            text = { Text("Frees ${formatBytesMb(state.library.totalDownloadedBytes)} of storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.deleteAllDownloads()
                        confirmClearDownloads = false
                    }
                ) {
                    Text("Remove", color = CoralHeart, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDownloads = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

private data class DetailActionSpec(
    val icon: ImageVector,
    val label: String,
    val isActive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    contextLabel: String,
    trailingActions: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = contextLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextSecondary
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            trailingActions()
        }
    }
}

@Composable
private fun DetailPrimaryPlayButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (enabled) EmeraldAccent else ObsidianCard,
        modifier = Modifier
            .size(52.dp)
            .sonoraPressable(enabled = enabled, shape = CircleShape, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = if (enabled) Color.Black else TextMuted,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: Playlist,
    tracks: List<Track>,
    isLoading: Boolean,
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    onAddTrack: () -> Unit,
    onRename: () -> Unit,
    onShareCode: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val washColor = Color(playlist.coverColorStart)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            washColor.copy(alpha = 0.22f),
                            ObsidianBg
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 148.dp)
        ) {
            item(key = "pl_top_bar") {
                DetailTopBar(
                    onBack = { actions.closeDetail() },
                    contextLabel = "PLAYLIST",
                    trailingActions = {
                        if (playlist.isUserCreated) {
                            IconButton(onClick = onRename) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit playlist",
                                    tint = TextPrimary
                                )
                            }
                            IconButton(onClick = onDeleteRequest) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete playlist",
                                    tint = TextSecondary
                                )
                            }
                        } else {
                            IconButton(onClick = { actions.sharePlaylist(playlist) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Share playlist",
                                    tint = TextPrimary
                                )
                            }
                        }
                        IconButton(onClick = onShareCode) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = "Share Code",
                                tint = TextPrimary
                            )
                        }
                    }
                )
            }

            item(key = "pl_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        SonoraArtwork(
                            artworkUrl = playlist.artworkUrl ?: tracks.firstOrNull()?.artworkUrl,
                            title = playlist.name,
                            cornerRadius = 8.dp,
                            colorStart = playlist.coverColorStart,
                            colorEnd = playlist.coverColorEnd,
                            modifier = Modifier.size(180.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    if (playlist.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = playlist.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${playlist.authorName} • ${tracks.size} tracks",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Bar: Secondary icons left + Spotify Green Play circle right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (tracks.isNotEmpty()) {
                                IconButton(
                                    onClick = { actions.downloadPlaylist(tracks) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DownloadForOffline,
                                        contentDescription = "Download all",
                                        tint = TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (state.playback.shuffleMode == ShuffleMode.OFF) {
                                            actions.toggleShuffle()
                                        }
                                        actions.playTrack(tracks.random(), tracks)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle play",
                                        tint = if (state.playback.shuffleMode != ShuffleMode.OFF) EmeraldAccent else TextSecondary
                                    )
                                }
                            }
                            if (playlist.isUserCreated) {
                                OutlinedButton(
                                    onClick = onAddTrack,
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, ObsidianStroke),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        DetailPrimaryPlayButton(
                            enabled = tracks.isNotEmpty(),
                            onClick = {
                                if (tracks.isNotEmpty()) actions.playTrack(tracks.first(), tracks)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = ObsidianStroke, thickness = 1.dp)
                }
            }

            if (isLoading) {
                items(6) { ShimmerRowPlaceholder() }
            } else if (tracks.isEmpty()) {
                item(key = "pl_empty") {
                    DetailEmptyCard(
                        title = "No songs in this playlist yet",
                        subtitle = if (playlist.isUserCreated) {
                            "Tap Add above or use the menu on any song."
                        } else {
                            "No tracks available right now."
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(tracks, key = { idx, track -> "pl_tr_${idx}_${track.id}" }) { idx, track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    TrackRow(
                        track = track,
                        index = idx + 1,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && state.playback.isPlaying,
                        isLiked = track.id in state.likedTrackIds,
                        downloadState = state.downloads[track.id],
                        onClick = { actions.playTrack(track, tracks) },
                        onLikeClick = { actions.toggleLike(track) },
                        onDownloadClick = {
                            if (state.downloads[track.id]?.status == DownloadStatus.DOWNLOADED) {
                                actions.deleteDownload(track.id)
                            } else {
                                actions.downloadTrack(track)
                            }
                        },
                        onMoreClick = { onTrackOverflow(track) },
                        trailingContent = if (playlist.isUserCreated) {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (idx > 0) actions.reorderPlaylistTrack(playlist.id, idx, idx - 1)
                                        },
                                        enabled = idx > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowUp,
                                            contentDescription = "Move up",
                                            tint = if (idx > 0) TextSecondary else TextMuted.copy(alpha = 0.35f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (idx < tracks.lastIndex) {
                                                actions.reorderPlaylistTrack(playlist.id, idx, idx + 1)
                                            }
                                        },
                                        enabled = idx < tracks.lastIndex,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Move down",
                                            tint = if (idx < tracks.lastIndex) TextSecondary else TextMuted.copy(alpha = 0.35f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { actions.removeTrackFromPlaylist(playlist.id, track.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailView(
    artist: Artist,
    tracks: List<Track>,
    isFollowing: Boolean,
    isLoading: Boolean,
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF2B2B2B),
                            ObsidianBg
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 148.dp)
        ) {
            item(key = "artist_top_bar") {
                DetailTopBar(
                    onBack = { actions.closeDetail() },
                    contextLabel = "ARTIST"
                )
            }

            item(key = "artist_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        SonoraArtwork(
                            artworkUrl = artist.avatarUrl,
                            title = artist.name,
                            isCircle = true,
                            modifier = Modifier.size(144.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    if (artist.followerCount > 0 || artist.trackCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                if (artist.followerCount > 0) {
                                    append("${formatArtistCount(artist.followerCount)} fans")
                                }
                                if (artist.trackCount > 0) {
                                    if (isNotEmpty()) append(" • ")
                                    append("${artist.trackCount} tracks")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { actions.toggleFollowArtist(artist) },
                                shape = CircleShape,
                                border = BorderStroke(
                                    1.dp,
                                    if (isFollowing) EmeraldAccent else ObsidianStroke
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    color = if (isFollowing) EmeraldAccent else TextPrimary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            IconButton(onClick = { actions.startArtistRadio(artist) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Radio,
                                    contentDescription = "Artist Radio",
                                    tint = TextSecondary
                                )
                            }
                        }

                        DetailPrimaryPlayButton(
                            enabled = tracks.isNotEmpty(),
                            onClick = {
                                if (tracks.isNotEmpty()) actions.playTrack(tracks.first(), tracks)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = ObsidianStroke, thickness = 1.dp)
                }
            }

            if (isLoading) {
                items(6) { ShimmerRowPlaceholder() }
            } else if (tracks.isEmpty()) {
                item(key = "artist_empty") {
                    DetailEmptyCard(
                        title = "No songs loaded",
                        subtitle = "Try starting Artist Radio.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            } else {
                item(key = "artist_popular_title") {
                    Text(
                        text = "Popular",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                itemsIndexed(tracks, key = { idx, track -> "art_tr_${idx}_${track.id}" }) { idx, track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    TrackRow(
                        track = track,
                        index = idx + 1,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && state.playback.isPlaying,
                        isLiked = track.id in state.likedTrackIds,
                        downloadState = state.downloads[track.id],
                        onClick = { actions.playTrack(track, tracks) },
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
        }
    }
}

@Composable
private fun MediaCollectionDetailView(
    contextLabel: String,
    title: String,
    subtitle: String,
    description: String?,
    artworkUrl: String?,
    tracks: List<Track>,
    isLoading: Boolean,
    emptyText: String,
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    secondaryActions: List<DetailActionSpec>
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF282828),
                            ObsidianBg
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 148.dp)
        ) {
            item(key = "media_top_bar") {
                DetailTopBar(
                    onBack = { actions.closeDetail() },
                    contextLabel = contextLabel
                )
            }

            item(key = "media_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        SonoraArtwork(
                            artworkUrl = artworkUrl,
                            title = title,
                            cornerRadius = 8.dp,
                            modifier = Modifier.size(168.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    if (!description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            secondaryActions.forEach { spec ->
                                OutlinedButton(
                                    onClick = spec.onClick,
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, ObsidianStroke),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = spec.icon,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(spec.label, color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        DetailPrimaryPlayButton(
                            enabled = tracks.isNotEmpty(),
                            onClick = {
                                if (tracks.isNotEmpty()) actions.playTrack(tracks.first(), tracks)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = ObsidianStroke, thickness = 1.dp)
                }
            }

            if (isLoading) {
                items(6) { ShimmerRowPlaceholder() }
            } else if (tracks.isEmpty()) {
                item(key = "media_empty") {
                    DetailEmptyCard(
                        title = "Nothing available",
                        subtitle = emptyText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(tracks, key = { idx, track -> "med_tr_${idx}_${track.id}" }) { idx, track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    TrackRow(
                        track = track,
                        index = idx + 1,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && state.playback.isPlaying,
                        isLiked = track.id in state.likedTrackIds,
                        downloadState = state.downloads[track.id],
                        onClick = { actions.playTrack(track, tracks) },
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
        }
    }
}

@Composable
private fun SpecialCollectionDetailView(
    contextLabel: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    headerTint: Color,
    coverBrush: Brush,
    tracks: List<Track>,
    emptyTitle: String,
    emptySubtitle: String,
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    secondaryActions: List<DetailActionSpec>
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            headerTint.copy(alpha = 0.25f),
                            ObsidianBg
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 148.dp)
        ) {
            item(key = "spec_top_bar") {
                DetailTopBar(
                    onBack = { actions.closeDetail() },
                    contextLabel = contextLabel
                )
            }

            item(key = "spec_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(136.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(coverBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            secondaryActions.forEach { spec ->
                                OutlinedButton(
                                    onClick = spec.onClick,
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, ObsidianStroke),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = spec.icon,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(spec.label, color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        DetailPrimaryPlayButton(
                            enabled = tracks.isNotEmpty(),
                            onClick = {
                                if (tracks.isNotEmpty()) actions.playTrack(tracks.first(), tracks)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = ObsidianStroke, thickness = 1.dp)
                }
            }

            if (tracks.isEmpty()) {
                item(key = "spec_empty") {
                    DetailEmptyCard(
                        title = emptyTitle,
                        subtitle = emptySubtitle,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(tracks, key = { idx, track -> "spec_tr_${idx}_${track.id}" }) { idx, track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    TrackRow(
                        track = track,
                        index = idx + 1,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && state.playback.isPlaying,
                        isLiked = track.id in state.likedTrackIds,
                        downloadState = state.downloads[track.id],
                        onClick = { actions.playTrack(track, tracks) },
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
        }
    }
}

@Composable
private fun JamQueueDetailView(
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    onShareJamCode: (Playlist) -> Unit
) {
    val tracks = state.playback.queue
    val currentIndex = state.playback.currentIndex

    val jamPlaylist = remember(tracks) {
        Playlist(
            id = "jam_queue",
            name = "Sonora Jam Queue",
            description = "${tracks.size} tracks",
            tracks = tracks,
            coverColorStart = 0xFF1DB954L,
            coverColorEnd = 0xFF181818L,
            isUserCreated = false
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 148.dp)
        ) {
            item(key = "jam_top_bar") {
                DetailTopBar(
                    onBack = { actions.closeDetail() },
                    contextLabel = "QUEUE",
                    trailingActions = {
                        IconButton(onClick = { onShareJamCode(jamPlaylist) }) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = "Share or Merge Queue",
                                tint = TextPrimary
                            )
                        }
                    }
                )
            }

            item(key = "jam_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Jam Queue",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${tracks.size} tracks queued",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onShareJamCode(jamPlaylist) },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, ObsidianStroke),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share / Merge", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                            }
                            if (tracks.size > 1) {
                                IconButton(onClick = { actions.clearQueue() }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Clear Up Next",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }

                        DetailPrimaryPlayButton(
                            enabled = tracks.isNotEmpty(),
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    val target = tracks.getOrNull(currentIndex) ?: tracks.first()
                                    actions.playTrack(target, tracks)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = ObsidianStroke, thickness = 1.dp)
                }
            }

            if (tracks.isEmpty()) {
                item(key = "jam_empty") {
                    DetailEmptyCard(
                        title = "Queue is empty",
                        subtitle = "Play any song or merge a playlist code.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(tracks, key = { idx, track -> "jam_${idx}_${track.id}" }) { idx, track ->
                    val isCurrent = idx == currentIndex
                    TrackRow(
                        track = track,
                        index = idx + 1,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && state.playback.isPlaying,
                        isLiked = track.id in state.likedTrackIds,
                        downloadState = state.downloads[track.id],
                        onClick = { actions.playTrack(track, tracks) },
                        onLikeClick = { actions.toggleLike(track) },
                        onDownloadClick = {
                            if (state.downloads[track.id]?.status == DownloadStatus.DOWNLOADED) {
                                actions.deleteDownload(track.id)
                            } else {
                                actions.downloadTrack(track)
                            }
                        },
                        onMoreClick = { onTrackOverflow(track) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (idx > 0) actions.moveQueueItem(idx, idx - 1) },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = "Move up",
                                        tint = if (idx > 0) TextSecondary else TextMuted.copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { if (idx < tracks.lastIndex) actions.moveQueueItem(idx, idx + 1) },
                                    enabled = idx < tracks.lastIndex,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Move down",
                                        tint = if (idx < tracks.lastIndex) TextSecondary else TextMuted.copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { actions.removeQueueItem(idx) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailEmptyCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianStroke),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatArtistCount(count: Long): String {
    return when {
        count >= 1_000_000L -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000L -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}
