package com.sonora.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.model.Artist
import com.sonora.music.model.Audiobook
import com.sonora.music.model.DetailRoute
import com.sonora.music.model.DownloadStatus
import com.sonora.music.model.Playlist
import com.sonora.music.model.PodcastShow
import com.sonora.music.model.SonoraActions
import com.sonora.music.model.SonoraUiState
import com.sonora.music.model.Track
import com.sonora.music.ui.components.AnimatedPlayingBars
import com.sonora.music.ui.components.ShimmerCardPlaceholder
import com.sonora.music.ui.components.ShimmerRowPlaceholder
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.components.SonoraPillChip
import com.sonora.music.ui.components.SonoraSectionHeader
import com.sonora.music.ui.components.TrackRow
import com.sonora.music.ui.components.sonoraPressable
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianSurface
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary

private val HomeTopFilters = listOf("All", "Music", "OPM", "Podcasts", "Radio")

private val HomeGenres = listOf(
    "OPM",
    "P-Pop",
    "Pinoy Hip-Hop",
    "Pop",
    "R&B",
    "K-Pop",
    "Rock",
    "Chill",
    "Throwback OPM"
)

@Composable
fun HomeScreen(
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val home = state.home
    val activeFilter = home.selectedFilter.ifBlank { "All" }
    val showMusic = activeFilter.equals("All", ignoreCase = true) ||
        activeFilter.equals("Music", ignoreCase = true)
    val showOpm = activeFilter.equals("All", ignoreCase = true) ||
        activeFilter.equals("OPM", ignoreCase = true)
    val showPodcasts = activeFilter.equals("All", ignoreCase = true) ||
        activeFilter.equals("Podcasts", ignoreCase = true)
    val showRadio = activeFilter.equals("All", ignoreCase = true) ||
        activeFilter.equals("Radio", ignoreCase = true)

    val phTopTracks = home.phTopTracks.ifEmpty { home.trendingTracks }

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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Compact Top Filter Row + Audio EQ & Listening Stats Icons
            item(key = "home_top_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeTopFilters.forEach { filter ->
                            SonoraPillChip(
                                label = filter,
                                selected = activeFilter.equals(filter, ignoreCase = true),
                                accentColor = EmeraldAccent,
                                onClick = { actions.selectHomeFilter(filter) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ObsidianCard,
                            modifier = Modifier
                                .size(36.dp)
                                .sonoraPressable(shape = CircleShape) {
                                    actions.openAudioStudio()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Audio Equalizer",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = ObsidianCard,
                            modifier = Modifier
                                .size(36.dp)
                                .sonoraPressable(shape = CircleShape) {
                                    actions.openWrapped()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.BarChart,
                                    contentDescription = "Listening Stats",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Offline Banner
            item(key = "offline_banner") {
                AnimatedVisibility(
                    visible = home.isOffline,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, ObsidianStroke),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Offline",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Play your downloads and local audio files.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            TextButton(onClick = { actions.openDetail(DetailRoute.Downloads) }) {
                                Text("Downloads", color = EmeraldAccent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Non-offline Error Banner
            if (!home.errorMessage.isNullOrBlank() && !home.isOffline) {
                item(key = "error_banner") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, ObsidianStroke),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = home.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { actions.refreshHome() }) {
                                Text("Retry", color = EmeraldAccent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Initial Shimmer Skeleton
            if (home.isLoading && home.quickPicks.isEmpty() && phTopTracks.isEmpty()) {
                item(key = "home_shimmer") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(4) { ShimmerRowPlaceholder() }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(4) { ShimmerCardPlaceholder() }
                        }
                    }
                }
            }

            // 2-Column Flat Quick Access Tiles (max 6 items)
            if ((showMusic || showOpm) && home.quickPicks.isNotEmpty()) {
                item(key = "quick_access") {
                    val pairs = remember(home.quickPicks) {
                        home.quickPicks.take(6).chunked(2)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pairs.forEach { rowTracks ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowTracks.forEach { track ->
                                    val isCurrent = state.playback.currentTrack?.id == track.id
                                    QuickAccessTile(
                                        track = track,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && state.playback.isPlaying,
                                        onClick = { actions.playTrack(track, home.quickPicks) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowTracks.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Trending in the Philippines (Circular 112.dp artist portraits)
            if ((showMusic || showOpm) && home.phArtists.isNotEmpty()) {
                item(key = "ph_artists") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Trending in the Philippines",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(home.phArtists, key = { "ph_art_${it.id}" }) { artist ->
                                HomeArtistCircle(
                                    artist = artist,
                                    onClick = { actions.openDetail(DetailRoute.ArtistDetail(artist)) }
                                )
                            }
                        }
                    }
                }
            }

            // Top 50 — Philippines
            if ((showMusic || showOpm) && phTopTracks.isNotEmpty()) {
                item(key = "ph_top_tracks") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Top 50 — Philippines",
                            actionLabel = "Play All",
                            onActionClick = { actions.playTrack(phTopTracks.first(), phTopTracks) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(phTopTracks.take(15), key = { "ph_top_${it.id}" }) { track ->
                                val isCurrent = state.playback.currentTrack?.id == track.id
                                BorderlessTrackShelfItem(
                                    track = track,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && state.playback.isPlaying,
                                    onClick = { actions.playTrack(track, phTopTracks) }
                                )
                            }
                        }
                    }
                }
            }

            // OPM Hits
            if (showOpm && home.opmTracks.isNotEmpty()) {
                item(key = "opm_hits") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "OPM Hits",
                            actionLabel = "Play All",
                            onActionClick = { actions.playTrack(home.opmTracks.first(), home.opmTracks) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(home.opmTracks.take(15), key = { "opm_${it.id}" }) { track ->
                                val isCurrent = state.playback.currentTrack?.id == track.id
                                BorderlessTrackShelfItem(
                                    track = track,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && state.playback.isPlaying,
                                    onClick = { actions.playTrack(track, home.opmTracks) }
                                )
                            }
                        }
                    }
                }
            }

            // Global Top 50
            if (showMusic && home.globalTopTracks.isNotEmpty()) {
                item(key = "global_top_tracks") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Global Top 50",
                            actionLabel = "Play All",
                            onActionClick = {
                                actions.playTrack(home.globalTopTracks.first(), home.globalTopTracks)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(home.globalTopTracks.take(15), key = { "global_${it.id}" }) { track ->
                                val isCurrent = state.playback.currentTrack?.id == track.id
                                BorderlessTrackShelfItem(
                                    track = track,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && state.playback.isPlaying,
                                    onClick = { actions.playTrack(track, home.globalTopTracks) }
                                )
                            }
                        }
                    }
                }
            }

            // Global Trending Artists
            if (showMusic && home.globalArtists.isNotEmpty()) {
                item(key = "global_artists") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Global Trending Artists",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(home.globalArtists, key = { "gl_art_${it.id}" }) { artist ->
                                HomeArtistCircle(
                                    artist = artist,
                                    onClick = { actions.openDetail(DetailRoute.ArtistDetail(artist)) }
                                )
                            }
                        }
                    }
                }
            }

            // Made For You
            if (showMusic && home.madeForYou.isNotEmpty()) {
                item(key = "made_for_you") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Made For You",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(home.madeForYou, key = { "mfy_${it.id}" }) { playlist ->
                                BorderlessPlaylistItem(
                                    playlist = playlist,
                                    onClick = { actions.openDetail(DetailRoute.PlaylistDetail(playlist)) }
                                )
                            }
                        }
                    }
                }
            }

            // Browse by Genre
            if (showMusic || showOpm) {
                item(key = "browse_by_genre") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Browse by Genre",
                            actionLabel = if (home.genreTracks.isNotEmpty()) "Play All" else null,
                            onActionClick = if (home.genreTracks.isNotEmpty()) {
                                { actions.playTrack(home.genreTracks.first(), home.genreTracks) }
                            } else null,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(HomeGenres, key = { it }) { genre ->
                                SonoraPillChip(
                                    label = genre,
                                    selected = home.selectedGenre.equals(genre, ignoreCase = true),
                                    accentColor = EmeraldAccent,
                                    onClick = { actions.loadMoreGenre(genre) }
                                )
                            }
                        }

                        if (home.isLoading && home.genreTracks.isEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column {
                                repeat(3) { ShimmerRowPlaceholder() }
                            }
                        } else if (home.genreTracks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column {
                                home.genreTracks.take(5).forEachIndexed { idx, track ->
                                    val isCurrent = state.playback.currentTrack?.id == track.id
                                    TrackRow(
                                        track = track,
                                        index = idx + 1,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && state.playback.isPlaying,
                                        isLiked = track.id in state.likedTrackIds,
                                        downloadState = state.downloads[track.id],
                                        onClick = { actions.playTrack(track, home.genreTracks) },
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

            // Featured Charts & Playlists
            if ((showMusic || showOpm) && home.trendingPlaylists.isNotEmpty()) {
                item(key = "featured_playlists") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Featured Charts & Playlists",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(home.trendingPlaylists, key = { "feat_pl_${it.id}" }) { playlist ->
                                BorderlessPlaylistItem(
                                    playlist = playlist,
                                    onClick = { actions.openDetail(DetailRoute.PlaylistDetail(playlist)) }
                                )
                            }
                        }
                    }
                }
            }

            // Live PH Radio
            if (showRadio && home.liveRadio.isNotEmpty()) {
                if (activeFilter.equals("Radio", ignoreCase = true)) {
                    item(key = "live_radio_header") {
                        SonoraSectionHeader(
                            title = "Live PH Radio",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    itemsIndexed(home.liveRadio, key = { _, st -> "radio_row_${st.id}" }) { _, station ->
                        val isCurrent = state.playback.currentTrack?.id == station.id
                        TrackRow(
                            track = station,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && state.playback.isPlaying,
                            isLiked = station.id in state.likedTrackIds,
                            downloadState = null,
                            onClick = { actions.playTrack(station, home.liveRadio) },
                            onLikeClick = { actions.toggleLike(station) },
                            onDownloadClick = {},
                            onMoreClick = { onTrackOverflow(station) }
                        )
                    }
                } else {
                    item(key = "live_radio_shelf") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SonoraSectionHeader(
                                title = "Live PH Radio",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(home.liveRadio, key = { "radio_card_${it.id}" }) { station ->
                                    val isCurrent = state.playback.currentTrack?.id == station.id
                                    BorderlessRadioShelfItem(
                                        station = station,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && state.playback.isPlaying,
                                        onClick = { actions.playTrack(station, home.liveRadio) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Podcasts in PH
            if (showPodcasts && home.podcasts.isNotEmpty()) {
                if (activeFilter.equals("Podcasts", ignoreCase = true)) {
                    item(key = "podcasts_list_header") {
                        SonoraSectionHeader(
                            title = "Podcasts in PH",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    items(home.podcasts, key = { "pod_row_${it.id}" }) { show ->
                        PodcastListRow(
                            show = show,
                            onClick = { actions.openDetail(DetailRoute.PodcastDetail(show)) }
                        )
                    }
                } else {
                    item(key = "podcasts_shelf") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SonoraSectionHeader(
                                title = "Podcasts in PH",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(home.podcasts, key = { "pod_${it.id}" }) { show ->
                                    BorderlessPodcastItem(
                                        show = show,
                                        onClick = { actions.openDetail(DetailRoute.PodcastDetail(show)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Free Audiobooks
            if (showPodcasts && home.audiobooks.isNotEmpty()) {
                item(key = "audiobooks_shelf") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Free Audiobooks",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(home.audiobooks, key = { "book_${it.id}" }) { book ->
                                BorderlessAudiobookItem(
                                    book = book,
                                    onClick = { actions.openDetail(DetailRoute.AudiobookDetail(book)) }
                                )
                            }
                        }
                    }
                }
            }

            // Recently Played
            if (showMusic && home.recentlyPlayed.isNotEmpty()) {
                item(key = "recently_played") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Recently Played",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(home.recentlyPlayed.take(14), key = { "recent_${it.id}" }) { track ->
                                val isCurrent = state.playback.currentTrack?.id == track.id
                                BorderlessTrackShelfItem(
                                    track = track,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && state.playback.isPlaying,
                                    coverSize = 128.dp,
                                    onClick = { actions.playTrack(track, home.recentlyPlayed) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAccessTile(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = ObsidianCard,
        modifier = modifier
            .height(52.dp)
            .sonoraPressable(shape = RoundedCornerShape(6.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SonoraArtwork(
                artworkUrl = track.artworkUrl,
                title = track.title,
                cornerRadius = 0.dp,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 15.sp
                ),
                color = if (isCurrent) EmeraldAccent else TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            if (isCurrent) {
                AnimatedPlayingBars(
                    isPlaying = isPlaying,
                    color = EmeraldAccent,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeArtistCircle(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .sonoraPressable(shape = RoundedCornerShape(8.dp), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SonoraArtwork(
            artworkUrl = artist.avatarUrl,
            title = artist.name,
            isCircle = true,
            modifier = Modifier.size(112.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BorderlessTrackShelfItem(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    coverSize: Dp = 144.dp
) {
    Column(
        modifier = Modifier
            .width(coverSize)
            .sonoraPressable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) {
        Box(modifier = Modifier.size(coverSize)) {
            SonoraArtwork(
                artworkUrl = track.artworkUrl,
                title = track.title,
                cornerRadius = 8.dp,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg.copy(alpha = 0.85f))
                        .padding(6.dp)
                ) {
                    AnimatedPlayingBars(isPlaying = isPlaying, color = EmeraldAccent)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = if (isCurrent) EmeraldAccent else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = track.displayArtist,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BorderlessPlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(144.dp)
            .sonoraPressable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) {
        SonoraArtwork(
            artworkUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
            title = playlist.name,
            cornerRadius = 8.dp,
            colorStart = playlist.coverColorStart,
            colorEnd = playlist.coverColorEnd,
            modifier = Modifier.size(144.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = playlist.description.ifBlank { playlist.authorName },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BorderlessRadioShelfItem(
    station: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .sonoraPressable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) {
        Box(modifier = Modifier.size(136.dp)) {
            SonoraArtwork(
                artworkUrl = station.artworkUrl,
                title = station.title,
                cornerRadius = 8.dp,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg.copy(alpha = 0.85f))
                        .padding(6.dp)
                ) {
                    AnimatedPlayingBars(isPlaying = isPlaying, color = EmeraldAccent)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = station.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = if (isCurrent) EmeraldAccent else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "LIVE • ${station.genre.ifBlank { station.displayArtist }}",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BorderlessPodcastItem(
    show: PodcastShow,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(144.dp)
            .sonoraPressable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) {
        SonoraArtwork(
            artworkUrl = show.artworkUrl,
            title = show.title,
            cornerRadius = 8.dp,
            modifier = Modifier.size(144.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = show.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = show.author,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PodcastListRow(
    show: PodcastShow,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SonoraArtwork(
            artworkUrl = show.artworkUrl,
            title = show.title,
            cornerRadius = 6.dp,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = show.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${show.author} • ${show.genre}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BorderlessAudiobookItem(
    book: Audiobook,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(144.dp)
            .sonoraPressable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) {
        SonoraArtwork(
            artworkUrl = book.artworkUrl,
            title = book.title,
            cornerRadius = 8.dp,
            modifier = Modifier.size(144.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = book.author.ifBlank { "LibriVox" },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
