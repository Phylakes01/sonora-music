package com.sonora.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.model.Audiobook
import com.sonora.music.model.DetailRoute
import com.sonora.music.model.Playlist
import com.sonora.music.model.PodcastShow
import com.sonora.music.model.SonoraActions
import com.sonora.music.model.SonoraUiState
import com.sonora.music.model.Track
import com.sonora.music.ui.components.AnimatedPlayingBars
import com.sonora.music.ui.components.ShimmerCardPlaceholder
import com.sonora.music.ui.components.ShimmerRowPlaceholder
import com.sonora.music.ui.components.SonoraAmbientBackdrop
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.components.SonoraBadge
import com.sonora.music.ui.components.SonoraPillChip
import com.sonora.music.ui.components.SonoraSectionHeader
import com.sonora.music.ui.components.TrackRow
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
import java.util.Calendar

private val HomeGenres = listOf(
    "All",
    "Electronic",
    "Hip-Hop/Rap",
    "Lo-Fi",
    "Pop",
    "Rock",
    "R&B/Soul",
    "Ambient",
    "Indie",
    "Jazz"
)

@Composable
fun HomeScreen(
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val home = state.home
    val accent = LocalSonoraAccent.current
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late night waves"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        SonoraAmbientBackdrop(
            accentColor = accent,
            secondaryColor = VioletPulse
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 164.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Greeting & Top Control Pills
            item(key = "home_header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.displaySmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldAccent)
                                )
                                Text(
                                    text = "All Features Unlocked • Free Forever",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = ObsidianCard,
                            border = BorderStroke(1.dp, ObsidianStrokeSoft),
                            modifier = Modifier.sonoraPressable(shape = CircleShape) {
                                actions.refreshHome()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh feed",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isPrivate = state.audioSettings.privateSession
                        SonoraPillChip(
                            label = if (isPrivate) "Private On" else "Private Session",
                            selected = isPrivate,
                            accentColor = VioletPulse,
                            leadingIcon = if (isPrivate) Icons.Rounded.VisibilityOff else Icons.Rounded.Lock,
                            onClick = {
                                actions.updateAudioSettings { it.copy(privateSession = !it.privateSession) }
                            }
                        )

                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                Brush.linearGradient(listOf(EmeraldAccent, CyanGlow, VioletPulse))
                            ),
                            modifier = Modifier.sonoraPressable(shape = CircleShape) {
                                actions.openWrapped()
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                EmeraldAccent.copy(alpha = 0.14f),
                                                VioletPulse.copy(alpha = 0.14f)
                                            )
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = CyanGlow,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Wrapped",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                            }
                        }

                        val studioLabel = buildString {
                            append("Audio Studio")
                            if (state.audioSettings.eqEnabled && state.audioSettings.eqPreset.label != "Flat") {
                                append(" • ${state.audioSettings.eqPreset.label}")
                            }
                        }
                        SonoraPillChip(
                            label = studioLabel,
                            selected = state.playback.sleepTimer.active || state.audioSettings.eqEnabled,
                            accentColor = accent,
                            leadingIcon = Icons.Rounded.Tune,
                            onClick = { actions.openAudioStudio() }
                        )

                        SonoraPillChip(
                            label = "AI Mix",
                            selected = false,
                            accentColor = AmberGold,
                            leadingIcon = Icons.Rounded.GraphicEq,
                            onClick = { actions.openAiGenerator("") }
                        )
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
                        shape = RoundedCornerShape(16.dp),
                        color = AmberGold.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.42f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Offline Mode Active",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Play your downloaded tracks & local audio files seamlessly.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            TextButton(onClick = { actions.openDetail(DetailRoute.Downloads) }) {
                                Text("Downloads", color = AmberGold, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Non-offline Error Banner
            if (!home.errorMessage.isNullOrBlank() && !home.isOffline) {
                item(key = "error_banner") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CoralHeart.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, CoralHeart.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = home.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { actions.refreshHome() }) {
                                Text("Retry", color = CoralHeart, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Initial Shimmer Skeleton
            if (home.isLoading && home.quickPicks.isEmpty() && home.trendingTracks.isEmpty()) {
                item(key = "home_shimmer") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repeat(4) { ShimmerRowPlaceholder() }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(4) { ShimmerCardPlaceholder() }
                        }
                    }
                }
            }

            // 2-Column Quick Picks Grid
            if (home.quickPicks.isNotEmpty()) {
                item(key = "quick_picks") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Quick Picks",
                            subtitle = "Instant jump-back-in grooves",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val pairs = remember(home.quickPicks) {
                            home.quickPicks.take(8).chunked(2)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pairs.forEach { rowTracks ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowTracks.forEach { track ->
                                        val isCurrent = state.playback.currentTrack?.id == track.id
                                        QuickPickTile(
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
            }

            // Made For You
            if (home.madeForYou.isNotEmpty()) {
                item(key = "made_for_you") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Made For You",
                            subtitle = "Discover Weekly, Release Radar & Daily Mix",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(home.madeForYou, key = { it.id }) { playlist ->
                                MadeForYouCard(
                                    playlist = playlist,
                                    onOpen = { actions.openDetail(DetailRoute.PlaylistDetail(playlist)) },
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
                }
            }

            // Mood & Genre Filter Chips + Filtered Genre Spotlight
            item(key = "genre_filter_row") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SonoraSectionHeader(
                        title = "Explore by Vibe & Genre",
                        subtitle = if (home.selectedGenre == "All") {
                            "Tap any genre to refresh live picks"
                        } else {
                            "Curated ${home.selectedGenre} wave"
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(HomeGenres, key = { it }) { genre ->
                            SonoraPillChip(
                                label = genre,
                                selected = home.selectedGenre.equals(genre, ignoreCase = true),
                                onClick = { actions.loadMoreGenre(genre) }
                            )
                        }
                    }

                    val activeGenreTracks = if (!home.selectedGenre.equals("All", ignoreCase = true)) {
                        home.genreTracks
                    } else {
                        emptyList()
                    }
                    if (!home.selectedGenre.equals("All", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(14.dp))
                        if (home.isLoading && activeGenreTracks.isEmpty()) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                                repeat(3) { ShimmerRowPlaceholder() }
                            }
                        } else if (activeGenreTracks.isNotEmpty()) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                activeGenreTracks.take(6).forEachIndexed { idx, track ->
                                    val isCurrent = state.playback.currentTrack?.id == track.id
                                    TrackRow(
                                        track = track,
                                        index = idx + 1,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && state.playback.isPlaying,
                                        isLiked = track.id in state.likedTrackIds,
                                        downloadState = state.downloads[track.id],
                                        onClick = { actions.playTrack(track, activeGenreTracks) },
                                        onLikeClick = { actions.toggleLike(track) },
                                        onDownloadClick = {
                                            if (state.downloads[track.id]?.status == com.sonora.music.model.DownloadStatus.DOWNLOADED) {
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

            // Trending Worldwide (Cards + Ranked Track Rows)
            if (home.trendingTracks.isNotEmpty()) {
                item(key = "trending_cards") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Trending Worldwide",
                            subtitle = "Independent hits surging right now",
                            actionLabel = "Play All",
                            onActionClick = {
                                actions.playTrack(home.trendingTracks.first(), home.trendingTracks)
                            },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(home.trendingTracks.take(10), key = { "card_${it.id}" }) { track ->
                                val isCurrent = state.playback.currentTrack?.id == track.id
                                FeaturedTrackCard(
                                    track = track,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && state.playback.isPlaying,
                                    onClick = { actions.playTrack(track, home.trendingTracks) }
                                )
                            }
                        }
                    }
                }

                item(key = "trending_rows_header") {
                    SonoraSectionHeader(
                        title = "Top Charts",
                        subtitle = "Tap any track to stream or save offline",
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                itemsIndexed(
                    items = home.trendingTracks.take(8),
                    key = { _, track -> "trend_row_${track.id}" }
                ) { idx, track ->
                    val isCurrent = state.playback.currentTrack?.id == track.id
                    Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                        TrackRow(
                            track = track,
                            index = idx + 1,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && state.playback.isPlaying,
                            isLiked = track.id in state.likedTrackIds,
                            downloadState = state.downloads[track.id],
                            onClick = { actions.playTrack(track, home.trendingTracks) },
                            onLikeClick = { actions.toggleLike(track) },
                            onDownloadClick = {
                                if (state.downloads[track.id]?.status == com.sonora.music.model.DownloadStatus.DOWNLOADED) {
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

            // Trending Playlists
            if (home.trendingPlaylists.isNotEmpty()) {
                item(key = "trending_playlists") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Trending Playlists",
                            subtitle = "Community-curated selections",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(home.trendingPlaylists, key = { it.id }) { playlist ->
                                PlaylistPosterCard(
                                    playlist = playlist,
                                    onClick = { actions.openDetail(DetailRoute.PlaylistDetail(playlist)) }
                                )
                            }
                        }
                    }
                }
            }

            // Popular Podcasts
            if (home.podcasts.isNotEmpty()) {
                item(key = "popular_podcasts") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Popular Podcasts",
                            subtitle = "Zero-paywall RSS episodes & shows",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(home.podcasts, key = { it.id }) { show ->
                                PodcastPosterCard(
                                    show = show,
                                    onClick = { actions.openDetail(DetailRoute.PodcastDetail(show)) }
                                )
                            }
                        }
                    }
                }
            }

            // Free Classic Audiobooks
            if (home.audiobooks.isNotEmpty()) {
                item(key = "classic_audiobooks") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Free Classic Audiobooks",
                            subtitle = "Full LibriVox public-domain editions",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(home.audiobooks, key = { it.id }) { book ->
                                AudiobookPosterCard(
                                    book = book,
                                    onClick = { actions.openDetail(DetailRoute.AudiobookDetail(book)) }
                                )
                            }
                        }
                    }
                }
            }

            // Live Global Radio
            if (home.liveRadio.isNotEmpty()) {
                item(key = "live_radio") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Live Global Radio",
                            subtitle = "24/7 stations streaming worldwide",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(home.liveRadio, key = { it.id }) { station ->
                                val isCurrent = state.playback.currentTrack?.id == station.id
                                RadioStationCard(
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

            // Recently Played
            if (home.recentlyPlayed.isNotEmpty()) {
                item(key = "recently_played") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Recently Played",
                            subtitle = "Your recent listening trail",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(home.recentlyPlayed.take(14), key = { "recent_${it.id}" }) { track ->
                                val isCurrent = state.playback.currentTrack?.id == track.id
                                FeaturedTrackCard(
                                    track = track,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && state.playback.isPlaying,
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
private fun QuickPickTile(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalSonoraAccent.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrent) accent.copy(alpha = 0.16f) else ObsidianCard,
        border = BorderStroke(
            1.dp,
            if (isCurrent) accent.copy(alpha = 0.52f) else ObsidianStrokeSoft
        ),
        modifier = modifier.sonoraPressable(shape = RoundedCornerShape(12.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SonoraArtwork(
                artworkUrl = track.artworkUrl,
                title = track.title,
                cornerRadius = 10.dp,
                modifier = Modifier.size(58.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isCurrent) accent else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.displayArtist,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isCurrent) {
                AnimatedPlayingBars(
                    isPlaying = isPlaying,
                    color = accent,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun MadeForYouCard(
    playlist: Playlist,
    onOpen: () -> Unit,
    onQuickPlay: () -> Unit
) {
    val startColor = Color(playlist.coverColorStart)
    val endColor = Color(playlist.coverColorEnd)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, startColor.copy(alpha = 0.40f)),
        modifier = Modifier
            .width(172.dp)
            .sonoraPressable(shape = RoundedCornerShape(20.dp), onClick = onOpen)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            startColor.copy(alpha = 0.22f),
                            endColor.copy(alpha = 0.35f),
                            ObsidianCard
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                SonoraArtwork(
                    artworkUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
                    title = playlist.name,
                    cornerRadius = 14.dp,
                    colorStart = playlist.coverColorStart,
                    colorEnd = playlist.coverColorEnd,
                    modifier = Modifier.size(148.dp)
                )
                Surface(
                    shape = CircleShape,
                    color = startColor,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .sonoraPressable(shape = CircleShape, pressedScale = 0.90f, onClick = onQuickPlay)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play mix",
                            tint = Color(0xFF04140A),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "MADE FOR YOU",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = startColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playlist.description.ifBlank { "${playlist.tracks.size} curated tracks" },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FeaturedTrackCard(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalSonoraAccent.current
    Column(
        modifier = Modifier
            .width(150.dp)
            .sonoraPressable(shape = RoundedCornerShape(16.dp), onClick = onClick)
    ) {
        Box(modifier = Modifier.size(150.dp)) {
            SonoraArtwork(
                artworkUrl = track.artworkUrl,
                title = track.title,
                cornerRadius = 16.dp,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg.copy(alpha = 0.86f))
                        .padding(7.dp)
                ) {
                    AnimatedPlayingBars(isPlaying = isPlaying, color = accent)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isCurrent) accent else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.displayArtist,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistPosterCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .sonoraPressable(shape = RoundedCornerShape(16.dp), onClick = onClick)
    ) {
        SonoraArtwork(
            artworkUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
            title = playlist.name,
            cornerRadius = 16.dp,
            colorStart = playlist.coverColorStart,
            colorEnd = playlist.coverColorEnd,
            modifier = Modifier.size(150.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.description.ifBlank { "by ${playlist.authorName}" },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PodcastPosterCard(
    show: PodcastShow,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, ObsidianStrokeSoft),
        modifier = Modifier
            .width(154.dp)
            .sonoraPressable(shape = RoundedCornerShape(18.dp), onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box {
                SonoraArtwork(
                    artworkUrl = show.artworkUrl,
                    title = show.title,
                    cornerRadius = 12.dp,
                    colorStart = 0xFF00E5FFL,
                    colorEnd = 0xFF0F2744L,
                    modifier = Modifier.size(134.dp)
                )
                SonoraBadge(
                    text = "PODCAST",
                    tint = CyanGlow,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(ObsidianBg.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Podcasts,
                    contentDescription = null,
                    tint = CyanGlow,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = show.genre.ifBlank { "Show" },
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanGlow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = show.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = show.author,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AudiobookPosterCard(
    book: Audiobook,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.25f)),
        modifier = Modifier
            .width(154.dp)
            .sonoraPressable(shape = RoundedCornerShape(18.dp), onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box {
                SonoraArtwork(
                    artworkUrl = book.artworkUrl,
                    title = book.title,
                    cornerRadius = 12.dp,
                    colorStart = 0xFFFFB74DL,
                    colorEnd = 0xFF3F230CL,
                    modifier = Modifier.size(134.dp)
                )
                SonoraBadge(
                    text = "FREE BOOK",
                    tint = AmberGold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(ObsidianBg.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = AmberGold,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "LibriVox",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberGold
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.author.ifBlank { "Classic Literature" },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RadioStationCard(
    station: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_dot"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrent) CoralHeart.copy(alpha = 0.14f) else ObsidianCardElevated,
        border = BorderStroke(
            1.dp,
            if (isCurrent) CoralHeart.copy(alpha = 0.55f) else ObsidianStrokeSoft
        ),
        modifier = Modifier
            .width(184.dp)
            .sonoraPressable(shape = RoundedCornerShape(18.dp), onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CoralHeart.copy(alpha = pulseAlpha))
                    )
                    Text(
                        text = "LIVE RADIO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = CoralHeart
                    )
                }
                if (isCurrent) {
                    AnimatedPlayingBars(isPlaying = isPlaying, color = CoralHeart)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Radio,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SonoraArtwork(
                    artworkUrl = station.artworkUrl,
                    title = station.title,
                    cornerRadius = 10.dp,
                    colorStart = 0xFFFF4D6DL,
                    colorEnd = 0xFF3E1025L,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = station.genre.ifBlank { station.displayArtist },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
