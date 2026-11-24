package com.sonora.music.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.sonora.music.ui.components.SonoraAmbientBackdrop
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.components.SonoraBadge
import com.sonora.music.ui.components.SonoraPillChip
import com.sonora.music.ui.components.SonoraSectionHeader
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
import com.sonora.music.ui.theme.ObsidianCardElevated
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianStrokeSoft
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import com.sonora.music.ui.theme.VioletPulse

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
    val accent = LocalSonoraAccent.current
    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        SonoraAmbientBackdrop(
            accentColor = VioletPulse,
            secondaryColor = accent
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 164.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header & Actions
            item(key = "library_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Your Library",
                                style = MaterialTheme.typography.displaySmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "All Features Unlocked • Free Forever",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenCreatePlaylist,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color(0xFF04140A)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "New Playlist",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenImportDialog,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ObsidianStroke),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = null,
                                tint = CyanGlow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Import Code",
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Animated Sonora Wrapped Promo Card
            item(key = "wrapped_promo") {
                WrappedPromoBanner(
                    totalMinutes = state.wrappedStats.totalMinutes,
                    totalPlays = state.wrappedStats.totalPlays,
                    personaTitle = state.wrappedStats.personaTitle,
                    onClick = { actions.openWrapped() },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Filter Chips
            item(key = "library_filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(LibraryFilter.entries, key = { it.name }) { filter ->
                        SonoraPillChip(
                            label = filter.label,
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter }
                        )
                    }
                }
            }

            // Pinned Collections: Liked Songs & Offline Downloads
            if (selectedFilter == LibraryFilter.ALL ||
                selectedFilter == LibraryFilter.LIKED ||
                selectedFilter == LibraryFilter.DOWNLOADS
            ) {
                item(key = "pinned_collections") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SonoraSectionHeader(
                            title = "Pinned Collections",
                            subtitle = "Instant offline & favorites access"
                        )

                        if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.LIKED) {
                            PinnedCollectionCard(
                                title = "Liked Songs",
                                subtitle = "${library.likedSongs.size} favorite tracks",
                                icon = Icons.Rounded.Favorite,
                                gradientStart = CoralHeart,
                                gradientEnd = Color(0xFF471127),
                                badge = "PINNED",
                                onClick = { actions.openDetail(DetailRoute.LikedSongs) },
                                onQuickPlay = if (library.likedSongs.isNotEmpty()) {
                                    { actions.playTrack(library.likedSongs.first(), library.likedSongs) }
                                } else null
                            )
                        }

                        if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.DOWNLOADS) {
                            val mbUsed = formatBytesMb(library.totalDownloadedBytes)
                            PinnedCollectionCard(
                                title = "Offline Downloads",
                                subtitle = "${library.downloadedTracks.size} tracks • $mbUsed stored",
                                icon = Icons.Rounded.DownloadDone,
                                gradientStart = EmeraldAccent,
                                gradientEnd = Color(0xFF0A2E2A),
                                badge = mbUsed,
                                onClick = { actions.openDetail(DetailRoute.Downloads) },
                                onQuickPlay = if (library.downloadedTracks.isNotEmpty()) {
                                    { actions.playTrack(library.downloadedTracks.first(), library.downloadedTracks) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Jam / Party Queue Card
            if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.JAM) {
                item(key = "jam_queue_card") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SonoraSectionHeader(
                            title = "Jam & Live Queue",
                            subtitle = "Collaborative shareable playback queue"
                        )
                        PinnedCollectionCard(
                            title = "Jam / Party Queue",
                            subtitle = if (state.playback.queue.isEmpty()) {
                                "Queue is ready — merge a friend's code or start playing"
                            } else {
                                "${state.playback.queue.size} tracks in queue • Tap to manage & share"
                            },
                            icon = Icons.Rounded.Groups,
                            gradientStart = VioletPulse,
                            gradientEnd = Color(0xFF1B1540),
                            badge = if (state.playback.queue.isEmpty()) "READY" else "${state.playback.queue.size} UP",
                            onClick = { actions.openDetail(DetailRoute.JamQueue) },
                            onQuickPlay = if (state.playback.queue.isNotEmpty()) {
                                { actions.openQueue() }
                            } else null
                        )
                    }
                }
            }

            // User Playlists Section
            if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.PLAYLISTS) {
                item(key = "playlists_header") {
                    SonoraSectionHeader(
                        title = "Your Playlists (${library.userPlaylists.size})",
                        subtitle = "Reorder, export codes & listen offline",
                        actionLabel = "+ New",
                        onActionClick = onOpenCreatePlaylist,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                if (library.userPlaylists.isEmpty()) {
                    item(key = "playlists_empty") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = ObsidianCard,
                            border = BorderStroke(1.dp, ObsidianStrokeSoft),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .sonoraPressable(shape = RoundedCornerShape(18.dp), onClick = onOpenCreatePlaylist)
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(accent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Create your first playlist",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Pick a cover gradient or import a SONORA-PL-V1 code",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
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
                            },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }

            // Liked Quick Preview when specifically filtered to Liked
            if (selectedFilter == LibraryFilter.LIKED && library.likedSongs.isNotEmpty()) {
                items(library.likedSongs.take(12), key = { "lib_liked_${it.id}" }) { track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    Box(modifier = Modifier.padding(horizontal = 10.dp)) {
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
            }

            // Downloads Quick Preview when specifically filtered to Downloads
            if (selectedFilter == LibraryFilter.DOWNLOADS && library.downloadedTracks.isNotEmpty()) {
                items(library.downloadedTracks.take(12), key = { "lib_dl_${it.id}" }) { track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    Box(modifier = Modifier.padding(horizontal = 10.dp)) {
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
            }

            // Local Files Section
            if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.LOCAL) {
                item(key = "local_files_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SonoraSectionHeader(
                            title = "Local Device Audio",
                            subtitle = "Play MP3, FLAC, WAV & M4A stored on your phone",
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
                    val previewCount = if (selectedFilter == LibraryFilter.LOCAL) 15 else 4
                    items(library.localTracks.take(previewCount), key = { "lib_local_${it.id}" }) { track ->
                        val isCurrent = state.playback.currentTrack?.id == track.id
                        Box(modifier = Modifier.padding(horizontal = 10.dp)) {
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
}

@Composable
private fun WrappedPromoBanner(
    totalMinutes: Int,
    totalPlays: Int,
    personaTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "wrapped_glow")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wrapped_alpha"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ObsidianCard,
        border = BorderStroke(
            1.3.dp,
            Brush.linearGradient(
                listOf(
                    EmeraldAccent.copy(alpha = glowAlpha),
                    CyanGlow.copy(alpha = glowAlpha),
                    VioletPulse.copy(alpha = glowAlpha)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .sonoraPressable(shape = RoundedCornerShape(22.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            EmeraldAccent.copy(alpha = 0.20f),
                            CyanGlow.copy(alpha = 0.14f),
                            VioletPulse.copy(alpha = 0.22f)
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(listOf(EmeraldAccent, CyanGlow, VioletPulse))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = ObsidianBg,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sonora Wrapped",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    SonoraBadge(text = "5 SLIDES", tint = AmberGold)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "$totalMinutes min • $totalPlays plays • $personaTitle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Open Wrapped",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PinnedCollectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientStart: Color,
    gradientEnd: Color,
    badge: String,
    onClick: () -> Unit,
    onQuickPlay: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, gradientStart.copy(alpha = 0.34f)),
        modifier = Modifier
            .fillMaxWidth()
            .sonoraPressable(shape = RoundedCornerShape(18.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            gradientStart.copy(alpha = 0.14f),
                            ObsidianCard
                        )
                    )
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(gradientStart, gradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SonoraBadge(text = badge, tint = gradientStart)
                }
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
                    color = gradientStart.copy(alpha = 0.22f),
                    modifier = Modifier
                        .size(38.dp)
                        .sonoraPressable(shape = CircleShape, onClick = onQuickPlay)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Quick play",
                            tint = gradientStart,
                            modifier = Modifier.size(22.dp)
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
}

@Composable
private fun UserPlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onQuickPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val count = maxOf(playlist.tracks.size, playlist.trackIds.size)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, ObsidianStrokeSoft),
        modifier = modifier
            .fillMaxWidth()
            .sonoraPressable(shape = RoundedCornerShape(16.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SonoraArtwork(
                artworkUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
                title = playlist.name,
                cornerRadius = 12.dp,
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
                    text = buildString {
                        append("$count tracks")
                        if (playlist.description.isNotBlank()) {
                            append(" • ")
                            append(playlist.description)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = CircleShape,
                color = ObsidianCardElevated,
                modifier = Modifier
                    .size(36.dp)
                    .sonoraPressable(shape = CircleShape, onClick = onQuickPlay)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play playlist",
                        tint = Color(playlist.coverColorStart),
                        modifier = Modifier.size(20.dp)
                    )
                }
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
        shape = RoundedCornerShape(18.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .sonoraPressable(
                shape = RoundedCornerShape(18.dp),
                onClick = if (hasPermission) onOpenAll else onRequestPermission
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyanGlow.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasPermission) Icons.Rounded.LibraryMusic else Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = CyanGlow,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasPermission) "Device Audio ($trackCount files)" else "Enable Local Audio Playback",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (hasPermission) {
                            "Scan storage to discover newly added songs"
                        } else {
                            "Grant media audio permission to play local MP3/FLAC files"
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanGlow,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Audio Access", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onScanDevice,
                        enabled = !isScanning,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.55f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = CyanGlow,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = CyanGlow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isScanning) "Scanning..." else "Scan Device Audio",
                            color = TextPrimary
                        )
                    }

                    if (trackCount > 0) {
                        Button(
                            onClick = onOpenAll,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanGlow,
                                contentColor = ObsidianBg
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
