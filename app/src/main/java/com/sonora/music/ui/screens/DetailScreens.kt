package com.sonora.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.sonora.music.model.DetailRoute
import com.sonora.music.model.DownloadStatus
import com.sonora.music.model.Playlist
import com.sonora.music.model.ShuffleMode
import com.sonora.music.model.SonoraActions
import com.sonora.music.model.SonoraUiState
import com.sonora.music.model.Track
import com.sonora.music.ui.components.ShimmerRowPlaceholder
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.components.SonoraBadge
import com.sonora.music.ui.components.TrackRow
import com.sonora.music.ui.components.formatBytesMb
import com.sonora.music.ui.components.sonoraPressable
import com.sonora.music.ui.theme.AmberGold
import com.sonora.music.ui.theme.CoralHeart
import com.sonora.music.ui.theme.CyanGlow
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.LocalSonoraAccent
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianStrokeSoft
import com.sonora.music.ui.theme.ObsidianSurface
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import com.sonora.music.ui.theme.VioletPulse

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
                    badgeText = "PODCAST SHOW",
                    badgeColor = CyanGlow,
                    title = show.title,
                    subtitle = "${show.author} • ${show.genre} • ${episodes.size} episodes",
                    description = null,
                    artworkUrl = show.artworkUrl,
                    colorStart = 0xFF00E5FFL,
                    colorEnd = 0xFF0F2744L,
                    tracks = episodes,
                    isLoading = state.detail.isLoading && episodes.isEmpty(),
                    emptyText = "No RSS audio episodes found for this show.",
                    primaryPlayLabel = "Play Latest",
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    extraSecondaryAction = if (episodes.isNotEmpty()) {
                        DetailPillAction("Download", Icons.Rounded.DownloadForOffline, EmeraldAccent) {
                            actions.downloadPlaylist(episodes)
                        }
                    } else null
                )
            }

            is DetailRoute.AudiobookDetail -> {
                val book = state.detail.audiobook ?: route.book
                val chapters = state.detail.tracks
                MediaCollectionDetailView(
                    badgeText = "FREE CLASSIC AUDIOBOOK",
                    badgeColor = AmberGold,
                    title = book.title,
                    subtitle = "${book.author.ifBlank { "LibriVox" }} • ${chapters.size} chapters",
                    description = book.description.takeIf { it.isNotBlank() },
                    artworkUrl = book.artworkUrl,
                    colorStart = 0xFFFFB74DL,
                    colorEnd = 0xFF3F230CL,
                    tracks = chapters,
                    isLoading = state.detail.isLoading && chapters.isEmpty(),
                    emptyText = "No MP3 chapters available for this edition.",
                    primaryPlayLabel = "Start Book",
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    extraSecondaryAction = if (chapters.isNotEmpty()) {
                        DetailPillAction("Save Book", Icons.Rounded.DownloadForOffline, EmeraldAccent) {
                            actions.downloadPlaylist(chapters)
                        }
                    } else null
                )
            }

            is DetailRoute.LikedSongs -> {
                val tracks = state.library.likedSongs
                SpecialCollectionDetailView(
                    badgeText = "FAVORITES",
                    title = "Liked Songs",
                    subtitle = "${tracks.size} favorite tracks",
                    icon = Icons.Rounded.Favorite,
                    accentStart = CoralHeart,
                    accentEnd = Color(0xFF450E24),
                    tracks = tracks,
                    emptyTitle = "No Liked Songs yet",
                    emptySubtitle = "Tap the heart on any song, podcast, or radio station to pin it here.",
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    secondaryActions = listOfNotNull(
                        if (tracks.isNotEmpty()) {
                            DetailPillAction("Smart Shuffle", Icons.Rounded.AutoAwesome, VioletPulse) {
                                if (state.playback.shuffleMode != ShuffleMode.SMART_SHUFFLE) {
                                    actions.toggleShuffle()
                                }
                                actions.playTrack(tracks.random(), tracks)
                            }
                        } else null,
                        if (tracks.isNotEmpty()) {
                            DetailPillAction("Download All", Icons.Rounded.DownloadForOffline, EmeraldAccent) {
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
                    badgeText = "OFFLINE VAULT",
                    title = "Offline Downloads",
                    subtitle = "${tracks.size} tracks • $usedMb used",
                    icon = Icons.Rounded.DownloadForOffline,
                    accentStart = EmeraldAccent,
                    accentEnd = Color(0xFF0A2E2A),
                    tracks = tracks,
                    emptyTitle = "No offline downloads yet",
                    emptySubtitle = "Download tracks, mixes, or audiobooks to listen smoothly without internet.",
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    secondaryActions = listOfNotNull(
                        if (tracks.isNotEmpty()) {
                            DetailPillAction("Clear All", Icons.Rounded.DeleteOutline, CoralHeart) {
                                confirmClearDownloads = true
                            }
                        } else null
                    )
                )
            }

            is DetailRoute.LocalFiles -> {
                val tracks = state.library.localTracks
                SpecialCollectionDetailView(
                    badgeText = "DEVICE STORAGE",
                    title = "Local Audio Files",
                    subtitle = "${tracks.size} local tracks discovered",
                    icon = Icons.Rounded.LibraryMusic,
                    accentStart = CyanGlow,
                    accentEnd = Color(0xFF0F2744),
                    tracks = tracks,
                    emptyTitle = if (state.library.hasLocalAudioPermission) {
                        "No audio files scanned yet"
                    } else {
                        "Audio permission required"
                    },
                    emptySubtitle = if (state.library.hasLocalAudioPermission) {
                        "Tap Scan Device Audio to load MP3, FLAC, WAV, and M4A tracks."
                    } else {
                        "Grant access to your device's audio library to play offline files."
                    },
                    state = state,
                    actions = actions,
                    onTrackOverflow = onTrackOverflow,
                    secondaryActions = listOf(
                        if (!state.library.hasLocalAudioPermission) {
                            DetailPillAction("Grant Access", Icons.Rounded.FolderOpen, CyanGlow) {
                                actions.requestLocalAudioPermission()
                            }
                        } else {
                            DetailPillAction("Scan Device", Icons.Rounded.Refresh, CyanGlow) {
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
            text = { Text("This removes the playlist from your library. Downloaded songs stay in Offline Vault.") },
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
            title = { Text("Clear all offline downloads?") },
            text = { Text("Frees ${formatBytesMb(state.library.totalDownloadedBytes)} of storage from your device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.deleteAllDownloads()
                        confirmClearDownloads = false
                    }
                ) {
                    Text("Clear All", color = CoralHeart, fontWeight = FontWeight.Bold)
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

private data class DetailPillAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    badgeText: String,
    badgeColor: Color,
    trailingActions: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = CircleShape,
                color = ObsidianCard.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, ObsidianStrokeSoft),
                modifier = Modifier.sonoraPressable(shape = CircleShape, onClick = onBack)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(20.dp)
                )
            }
            SonoraBadge(text = badgeText, tint = badgeColor)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            trailingActions()
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
    val startColor = Color(playlist.coverColorStart)
    val endColor = Color(playlist.coverColorEnd)
    val accent = LocalSonoraAccent.current

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            startColor.copy(alpha = 0.34f),
                            endColor.copy(alpha = 0.40f),
                            ObsidianBg
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 164.dp)
        ) {
            item(key = "pl_top_bar") {
                DetailTopBar(
                    onBack = { actions.closeDetail() },
                    badgeText = if (playlist.isSystemMix) "MADE FOR YOU" else "PLAYLIST",
                    badgeColor = startColor,
                    trailingActions = {
                        if (playlist.isUserCreated) {
                            IconButton(onClick = onRename) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Rename playlist",
                                    tint = TextPrimary
                                )
                            }
                            IconButton(onClick = onDeleteRequest) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete playlist",
                                    tint = CoralHeart
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
                                contentDescription = "Share / Collab Code",
                                tint = CyanGlow
                            )
                        }
                    }
                )
            }

            item(key = "pl_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SonoraArtwork(
                        artworkUrl = playlist.artworkUrl ?: tracks.firstOrNull()?.artworkUrl,
                        title = playlist.name,
                        cornerRadius = 22.dp,
                        colorStart = playlist.coverColorStart,
                        colorEnd = playlist.coverColorEnd,
                        modifier = Modifier.size(184.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    if (playlist.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = playlist.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${playlist.authorName} • ${tracks.size} tracks",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (tracks.isNotEmpty()) actions.playTrack(tracks.first(), tracks)
                            },
                            enabled = tracks.isNotEmpty(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (state.playback.shuffleMode != ShuffleMode.SMART_SHUFFLE) {
                                    actions.toggleShuffle()
                                }
                                if (tracks.isNotEmpty()) {
                                    actions.playTrack(tracks.random(), tracks)
                                }
                            },
                            enabled = tracks.isNotEmpty(),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, VioletPulse.copy(alpha = 0.65f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = VioletPulse,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Smart Mix", color = TextPrimary, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (tracks.isNotEmpty()) {
                            DetailActionPill(
                                action = DetailPillAction("Download All", Icons.Rounded.DownloadForOffline, EmeraldAccent) {
                                    actions.downloadPlaylist(tracks)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (playlist.isUserCreated) {
                            DetailActionPill(
                                action = DetailPillAction("Add Tracks", Icons.Rounded.Add, accent) {
                                    onAddTrack()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        DetailActionPill(
                            action = DetailPillAction("Collab Code", Icons.Rounded.QrCode2, CyanGlow) {
                                onShareCode()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (isLoading) {
                items(6) { ShimmerRowPlaceholder() }
            } else if (tracks.isEmpty()) {
                item(key = "pl_empty") {
                    DetailEmptyCard(
                        title = "Playlist is empty",
                        subtitle = if (playlist.isUserCreated) {
                            "Tap Add Tracks above or use the 3-dot menu on any song to build this mix."
                        } else {
                            "No tracks returned for this playlist right now."
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(tracks, key = { idx, track -> "pl_tr_${idx}_${track.id}" }) { idx, track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    Box(modifier = Modifier.padding(horizontal = 10.dp)) {
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
                                                contentDescription = "Remove from playlist",
                                                tint = CoralHeart.copy(alpha = 0.85f),
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
}

@Composable
private fun ArtistDetailView(
    artist: com.sonora.music.model.Artist,
    tracks: List<Track>,
    isFollowing: Boolean,
    isLoading: Boolean,
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit
) {
    val accent = LocalSonoraAccent.current
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            VioletPulse.copy(alpha = 0.35f),
                            ObsidianBg
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 164.dp)
        ) {
            item(key = "artist_top_bar") {
                DetailTopBar(
                    onBack = { actions.closeDetail() },
                    badgeText = "ARTIST",
                    badgeColor = VioletPulse
                )
            }

            item(key = "artist_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SonoraArtwork(
                        artworkUrl = artist.avatarUrl,
                        title = artist.name,
                        isCircle = true,
                        colorStart = 0xFF8C6EFFL,
                        colorEnd = 0xFF1B1540L,
                        modifier = Modifier.size(144.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            if (artist.handle.isNotBlank()) append("@${artist.handle} • ")
                            append("${artist.followerCount} followers")
                            if (artist.trackCount > 0) append(" • ${artist.trackCount} tracks")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    if (artist.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = artist.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (tracks.isNotEmpty()) actions.playTrack(tracks.first(), tracks)
                            },
                            enabled = tracks.isNotEmpty(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { actions.toggleFollowArtist(artist) },
                            shape = CircleShape,
                            border = BorderStroke(
                                1.dp,
                                if (isFollowing) EmeraldAccent else ObsidianStroke
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isFollowing) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = EmeraldAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                color = if (isFollowing) EmeraldAccent else TextPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = { actions.startArtistRadio(artist) },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Radio,
                                contentDescription = "Artist Radio",
                                tint = CyanGlow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                items(6) { ShimmerRowPlaceholder() }
            } else if (tracks.isEmpty()) {
                item(key = "artist_empty") {
                    DetailEmptyCard(
                        title = "No tracks loaded yet",
                        subtitle = "Try starting Artist Radio to hear related waves.",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(tracks, key = { idx, track -> "art_tr_${idx}_${track.id}" }) { idx, track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    Box(modifier = Modifier.padding(horizontal = 10.dp)) {
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
}

@Composable
private fun MediaCollectionDetailView(
    badgeText: String,
    badgeColor: Color,
    title: String,
    subtitle: String,
    description: String?,
    artworkUrl: String?,
    colorStart: Long,
    colorEnd: Long,
    tracks: List<Track>,
    isLoading: Boolean,
    emptyText: String,
    primaryPlayLabel: String,
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    extraSecondaryAction: DetailPillAction?
) {
    val accent = LocalSonoraAccent.current
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(colorStart).copy(alpha = 0.28f),
                            Color(colorEnd).copy(alpha = 0.42f),
                            ObsidianBg
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 164.dp)
        ) {
            item(key = "media_top_bar") {
                DetailTopBar(
                    onBack = { actions.closeDetail() },
                    badgeText = badgeText,
                    badgeColor = badgeColor
                )
            }

            item(key = "media_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SonoraArtwork(
                        artworkUrl = artworkUrl,
                        title = title,
                        cornerRadius = 20.dp,
                        colorStart = colorStart,
                        colorEnd = colorEnd,
                        modifier = Modifier.size(168.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    if (!description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (tracks.isNotEmpty()) actions.playTrack(tracks.first(), tracks)
                            },
                            enabled = tracks.isNotEmpty(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(primaryPlayLabel, fontWeight = FontWeight.Bold)
                        }
                        if (extraSecondaryAction != null) {
                            DetailActionPill(
                                action = extraSecondaryAction,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                items(6) { ShimmerRowPlaceholder() }
            } else if (tracks.isEmpty()) {
                item(key = "media_empty") {
                    DetailEmptyCard(
                        title = "Nothing available",
                        subtitle = emptyText,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(tracks, key = { idx, track -> "med_tr_${idx}_${track.id}" }) { idx, track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    Box(modifier = Modifier.padding(horizontal = 10.dp)) {
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
}

@Composable
private fun SpecialCollectionDetailView(
    badgeText: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentStart: Color,
    accentEnd: Color,
    tracks: List<Track>,
    emptyTitle: String,
    emptySubtitle: String,
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    secondaryActions: List<DetailPillAction>
) {
    val accent = LocalSonoraAccent.current
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentStart.copy(alpha = 0.28f),
                            accentEnd.copy(alpha = 0.42f),
                            ObsidianBg
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 164.dp)
        ) {
            item(key = "spec_top_bar") {
                DetailTopBar(
                    onBack = { actions.closeDetail() },
                    badgeText = badgeText,
                    badgeColor = accentStart
                )
            }

            item(key = "spec_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Brush.linearGradient(listOf(accentStart, accentEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(62.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
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
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (tracks.isNotEmpty()) actions.playTrack(tracks.first(), tracks)
                            },
                            enabled = tracks.isNotEmpty(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All", fontWeight = FontWeight.Bold)
                        }
                        secondaryActions.forEach { action ->
                            DetailActionPill(action = action, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (tracks.isEmpty()) {
                item(key = "spec_empty") {
                    DetailEmptyCard(
                        title = emptyTitle,
                        subtitle = emptySubtitle,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(tracks, key = { idx, track -> "spec_tr_${idx}_${track.id}" }) { idx, track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    Box(modifier = Modifier.padding(horizontal = 10.dp)) {
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
    val accent = LocalSonoraAccent.current

    val jamPlaylist = remember(tracks) {
        Playlist(
            id = "jam_queue",
            name = "Sonora Jam Queue",
            description = "Live collaborative queue (${tracks.size} tracks)",
            tracks = tracks,
            coverColorStart = 0xFF8C6EFFL,
            coverColorEnd = 0xFF1B1540L,
            isUserCreated = false
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            VioletPulse.copy(alpha = 0.34f),
                            CyanGlow.copy(alpha = 0.16f),
                            ObsidianBg
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 164.dp)
        ) {
            item(key = "jam_top_bar") {
                DetailTopBar(
                    onBack = { actions.closeDetail() },
                    badgeText = "LIVE JAM",
                    badgeColor = VioletPulse,
                    trailingActions = {
                        IconButton(onClick = { onShareJamCode(jamPlaylist) }) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = "Share or Merge Jam Code",
                                tint = CyanGlow
                            )
                        }
                    }
                )
            }

            item(key = "jam_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(132.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Brush.linearGradient(listOf(VioletPulse, CyanGlow))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = ObsidianBg,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Jam & Party Queue",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${tracks.size} tracks queued • Share or merge codes with friends",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    val target = tracks.getOrNull(currentIndex) ?: tracks.first()
                                    actions.playTrack(target, tracks)
                                }
                            },
                            enabled = tracks.isNotEmpty(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play Queue", fontWeight = FontWeight.Bold)
                        }

                        DetailActionPill(
                            action = DetailPillAction("Share / Merge", Icons.Rounded.QrCode2, CyanGlow) {
                                onShareJamCode(jamPlaylist)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (tracks.size > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DetailActionPill(
                                action = DetailPillAction("Shuffle", Icons.Rounded.Shuffle, VioletPulse) {
                                    actions.toggleShuffle()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            DetailActionPill(
                                action = DetailPillAction("Clear Up Next", Icons.Rounded.DeleteOutline, CoralHeart) {
                                    actions.clearQueue()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (tracks.isEmpty()) {
                item(key = "jam_empty") {
                    DetailEmptyCard(
                        title = "Jam Queue is empty",
                        subtitle = "Play any song or tap Share / Merge above to import a friend's playlist code straight into the queue.",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(tracks, key = { idx, track -> "jam_${idx}_${track.id}" }) { idx, track ->
                    val isCurrent = idx == currentIndex
                    Box(modifier = Modifier.padding(horizontal = 10.dp)) {
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
                                            contentDescription = "Remove from queue",
                                            tint = CoralHeart.copy(alpha = 0.85f),
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
}

@Composable
private fun DetailActionPill(
    action: DetailPillAction,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = ObsidianCard,
        border = BorderStroke(1.dp, action.tint.copy(alpha = 0.50f)),
        modifier = modifier.sonoraPressable(shape = CircleShape, onClick = action.onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = action.tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        shape = RoundedCornerShape(20.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, ObsidianStrokeSoft),
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
