package com.sonora.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.sonora.music.model.TrackSource
import com.sonora.music.ui.components.AnimatedPlayingBars
import com.sonora.music.ui.components.MusicVideoCard
import com.sonora.music.ui.components.ShimmerCardPlaceholder
import com.sonora.music.ui.components.ShimmerRowPlaceholder
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.components.SonoraBadge
import com.sonora.music.ui.components.SonoraBrandLogo
import com.sonora.music.ui.components.SonoraPillChip
import com.sonora.music.ui.components.SonoraSectionHeader
import com.sonora.music.ui.components.TrackRow
import com.sonora.music.ui.components.rememberSonoraShimmerBrush
import com.sonora.music.ui.components.sonoraPressable
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianSurface
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary

private val HomeTopFilters = listOf("All", "PH Hits", "OPM", "Global", "Videos", "PH Radio", "Podcasts")

private val HomeGenres = listOf(
    "OPM",
    "P-Pop",
    "Pinoy Hip-Hop",
    "Pop",
    "R&B",
    "K-Pop",
    "Rock",
    "Chill"
)

private val QuickAccessTileBg = Color(0xFF202024)
private val HeaderButtonBg = Color(0xFF202024)
private val HeaderButtonBorder = Color(0xFF2B2B30)
private val YouTubeAccentTint = Color(0xFFFF4E45)

private fun isPhMusicVideo(track: Track): Boolean {
    val haystack = "${track.genre} ${track.artist} ${track.album} ${track.title}".lowercase()
    return haystack.contains("opm") ||
        haystack.contains("p-pop") ||
        haystack.contains("pinoy") ||
        haystack.contains("wish 107") ||
        haystack.contains("esremborak") ||
        haystack.contains("mariah deborah") ||
        haystack.contains("cup of joe") ||
        haystack.contains("bini") ||
        haystack.contains("dionela") ||
        haystack.contains("maki") ||
        haystack.contains("earl agustin") ||
        haystack.contains("shanti dope")
}

@Composable
fun HomeScreen(
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val home = state.home
    val activeFilter = home.selectedFilter.ifBlank { "All" }

    val isAll = activeFilter.equals("All", ignoreCase = true) ||
        activeFilter.equals("Music", ignoreCase = true)
    val isVideos = activeFilter.equals("Videos", ignoreCase = true)
    val isPhHits = activeFilter.equals("PH Hits", ignoreCase = true)
    val isOpm = activeFilter.equals("OPM", ignoreCase = true)
    val isGlobal = activeFilter.equals("Global", ignoreCase = true)
    val isRadio = activeFilter.equals("PH Radio", ignoreCase = true) ||
        activeFilter.equals("Radio", ignoreCase = true)
    val isPodcasts = activeFilter.equals("Podcasts", ignoreCase = true)

    val showSpotlightAndQuickGrid = isAll || isPhHits || isOpm || isGlobal
    val showMusicVideos = isAll || isVideos || isPhHits || isOpm || isGlobal
    val showPhArtists = isAll || isPhHits || isOpm
    val showPhTop = isAll || isPhHits
    val showOpm = isAll || isPhHits || isOpm
    val showGlobal = isAll || isGlobal
    val showMadeForYou = isAll || isPhHits || isGlobal
    val showGenresAndCharts = isAll || isPhHits || isOpm || isGlobal
    val showRadio = isAll || isRadio
    val showPodcasts = isAll || isPodcasts
    val showRecent = isAll || isPhHits || isOpm || isGlobal

    val phTopTracks = home.phTopTracks.ifEmpty { home.trendingTracks }

    val displayedMusicVideos = remember(activeFilter, home.musicVideos) {
        val unique = home.musicVideos.distinctBy { it.id }
        when {
            isPhHits || isOpm -> {
                val ph = unique.filter(::isPhMusicVideo)
                val other = unique.filterNot(::isPhMusicVideo)
                (ph + other)
            }
            isGlobal -> {
                val gl = unique.filterNot(::isPhMusicVideo)
                val ph = unique.filter(::isPhMusicVideo)
                (gl + ph)
            }
            else -> unique
        }
    }

    val spotlightQueue = remember(activeFilter, phTopTracks, home.opmTracks, home.globalTopTracks, home.quickPicks) {
        when {
            isOpm -> home.opmTracks.ifEmpty { phTopTracks }
            isGlobal -> home.globalTopTracks.ifEmpty { phTopTracks }
            isPhHits -> phTopTracks.ifEmpty { home.quickPicks }
            else -> phTopTracks.ifEmpty { home.opmTracks }.ifEmpty { home.quickPicks }.ifEmpty { home.globalTopTracks }
        }
    }
    val spotlightTrack = spotlightQueue.firstOrNull()

    val quickAccessTracks = remember(activeFilter, home.quickPicks, home.opmTracks, home.globalTopTracks, phTopTracks) {
        when {
            isOpm -> home.opmTracks.ifEmpty { home.quickPicks }
            isGlobal -> home.globalTopTracks.ifEmpty { home.quickPicks }
            isPhHits -> phTopTracks.ifEmpty { home.quickPicks }
            else -> home.quickPicks
        }.take(6)
    }

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
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Brand Header Row + Unclipped Horizontal Filter Pills Row
            item(key = "home_header_block") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SonoraBrandLogo(size = 30.dp)
                            Text(
                                text = "Sonora",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp
                                ),
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeaderIconCircleButton(
                                icon = Icons.Rounded.Tune,
                                contentDescription = "Audio Studio",
                                onClick = { actions.openAudioStudio() }
                            )
                            HeaderIconCircleButton(
                                icon = Icons.Rounded.BarChart,
                                contentDescription = "Listening Stats",
                                onClick = { actions.openWrapped() }
                            )
                            RefreshHeaderButton(
                                isRefreshing = home.isLoading,
                                onClick = { actions.refreshHome() }
                            )
                        }
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(HomeTopFilters, key = { it }) { filter ->
                            SonoraPillChip(
                                label = filter,
                                selected = activeFilter.equals(filter, ignoreCase = true) ||
                                    (filter == "All" && activeFilter.equals("Music", ignoreCase = true)) ||
                                    (filter == "PH Radio" && activeFilter.equals("Radio", ignoreCase = true)),
                                accentColor = EmeraldAccent,
                                onClick = { actions.selectHomeFilter(filter) }
                            )
                        }
                    }
                }
            }

            // Offline Banner
            if (home.isOffline) {
                item(key = "offline_banner") {
                    AnimatedVisibility(
                        visible = home.isOffline,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
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
            }

            // Non-offline Error Banner
            if (!home.errorMessage.isNullOrBlank() && !home.isOffline) {
                item(key = "error_banner") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
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
                    val shimmerBrush = rememberSonoraShimmerBrush()
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(176.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(shimmerBrush)
                        )
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

            // Editorial Spotlight Hero Card
            if (showSpotlightAndQuickGrid && spotlightTrack != null) {
                item(key = "editorial_spotlight_${activeFilter}") {
                    val isCurrent = state.playback.currentTrack?.id == spotlightTrack.id
                    EditorialSpotlightHeroCard(
                        track = spotlightTrack,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && state.playback.isPlaying,
                        onCardClick = { actions.playTrack(spotlightTrack, spotlightQueue) },
                        onPlayToggle = {
                            if (isCurrent) {
                                actions.togglePlayPause()
                            } else {
                                actions.playTrack(spotlightTrack, spotlightQueue)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 2-Column Quick Access Grid (6 items, 56.dp flush square art, rounded-6dp #202024 tiles)
            if (showSpotlightAndQuickGrid && quickAccessTracks.isNotEmpty()) {
                item(key = "quick_access") {
                    val pairs = remember(quickAccessTracks) {
                        quickAccessTracks.chunked(2)
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
                                        onClick = { actions.playTrack(track, quickAccessTracks) },
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

            // Official Music Videos (PH & Global) — Carousel or Full Feed when "Videos" filter is active
            if (showMusicVideos) {
                if (isVideos) {
                    item(key = "videos_tab_header") {
                        SonoraSectionHeader(
                            title = "Official Music Videos (PH & Global)",
                            subtitle = "${displayedMusicVideos.size} music videos ready to stream",
                            trailingContent = {
                                ImportYouTubeMiniChip(onClick = { actions.onOpenImportYouTube() })
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (displayedMusicVideos.isEmpty() && !home.isLoading) {
                        item(key = "videos_empty_card") {
                            ImportYouTubeBannerCard(
                                onClick = { actions.onOpenImportYouTube() },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        items(displayedMusicVideos, key = { "mv_full_${it.id}" }) { video ->
                            val isCurrent = state.playback.currentTrack?.id == video.id
                            MusicVideoCard(
                                track = video,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && state.playback.isPlaying,
                                isLiked = video.id in state.likedTrackIds,
                                onClick = { actions.onPlayMusicVideo(video, home.musicVideos) },
                                onLikeClick = { actions.toggleLike(video) },
                                onMoreClick = { onTrackOverflow(video) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else if (displayedMusicVideos.isNotEmpty() || home.isLoading) {
                    item(key = "official_music_videos") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SonoraSectionHeader(
                                title = "Official Music Videos (PH & Global)",
                                subtitle = "Esremborak, Mariah Deborah, The Weeknd, BINI & more",
                                trailingContent = {
                                    ImportYouTubeMiniChip(onClick = { actions.onOpenImportYouTube() })
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (displayedMusicVideos.isEmpty()) {
                                val shimmerBrush = rememberSonoraShimmerBrush()
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(3) {
                                        Column(
                                            modifier = Modifier.width(252.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(142.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(shimmerBrush)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.7f)
                                                    .height(13.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(shimmerBrush)
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(displayedMusicVideos, key = { "mv_card_${it.id}" }) { video ->
                                        val isCurrent = state.playback.currentTrack?.id == video.id
                                        MusicVideoCard(
                                            track = video,
                                            isCurrent = isCurrent,
                                            isPlaying = isCurrent && state.playback.isPlaying,
                                            isLiked = video.id in state.likedTrackIds,
                                            onClick = { actions.onPlayMusicVideo(video, home.musicVideos) },
                                            onLikeClick = { actions.toggleLike(video) },
                                            onMoreClick = { onTrackOverflow(video) },
                                            modifier = Modifier.width(252.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Trending in the Philippines (112.dp circular artist portraits with hairline border)
            if (showPhArtists && home.phArtists.isNotEmpty()) {
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

            // Top Hits — Philippines
            if (showPhTop && phTopTracks.isNotEmpty()) {
                item(key = "ph_top_tracks") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Top Hits — Philippines",
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

            // OPM Essentials
            if (showOpm && home.opmTracks.isNotEmpty()) {
                item(key = "opm_essentials") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "OPM Essentials",
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

            // Global Hits
            if (showGlobal && home.globalTopTracks.isNotEmpty()) {
                item(key = "global_top_tracks") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SonoraSectionHeader(
                            title = "Global Hits",
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
            if (showGlobal && home.globalArtists.isNotEmpty()) {
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
            if (showMadeForYou && home.madeForYou.isNotEmpty()) {
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
            if (showGenresAndCharts) {
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
            if (showGenresAndCharts && home.trendingPlaylists.isNotEmpty()) {
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
                if (isRadio) {
                    item(key = "live_radio_header") {
                        SonoraSectionHeader(
                            title = "Live PH Radio",
                            subtitle = "${home.liveRadio.size} live stations",
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

            // PH Podcasts
            if (showPodcasts && home.podcasts.isNotEmpty()) {
                if (isPodcasts) {
                    item(key = "podcasts_list_header") {
                        SonoraSectionHeader(
                            title = "PH Podcasts",
                            subtitle = "${home.podcasts.size} shows",
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
                                title = "PH Podcasts",
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
            if (showRecent && home.recentlyPlayed.isNotEmpty()) {
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
private fun HeaderIconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = HeaderButtonBg,
        border = BorderStroke(1.dp, HeaderButtonBorder),
        modifier = Modifier
            .size(36.dp)
            .sonoraPressable(shape = CircleShape, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RefreshHeaderButton(
    isRefreshing: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_angle"
    )
    Surface(
        shape = CircleShape,
        color = HeaderButtonBg,
        border = BorderStroke(1.dp, HeaderButtonBorder),
        modifier = Modifier
            .size(36.dp)
            .sonoraPressable(shape = CircleShape, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh Home",
                tint = TextPrimary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        rotationZ = if (isRefreshing) spinAngle else 0f
                    }
            )
        }
    }
}

@Composable
private fun EditorialSpotlightHeroCard(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onCardClick: () -> Unit,
    onPlayToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metaLine = remember(track.id, track.artist, track.durationSec) {
        if (track.durationSec > 0) {
            "${track.displayArtist} • ${track.formattedDuration}"
        } else {
            track.displayArtist
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = QuickAccessTileBg,
        border = BorderStroke(1.dp, HeaderButtonBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp)
            .sonoraPressable(shape = RoundedCornerShape(14.dp), pressedScale = 0.985f, onClick = onCardClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SonoraArtwork(
                artworkUrl = track.artworkUrl,
                title = track.title,
                cornerRadius = 14.dp,
                modifier = Modifier.fillMaxSize()
            )

            // Smooth left-to-right & bottom-to-top dark gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xE6121212),
                                Color(0xAE121212),
                                Color(0x66121212)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x28121212),
                                Color(0x66121212),
                                Color(0xE6121212)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 14.dp)
                ) {
                    Text(
                        text = "FEATURED FULL TRACK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        ),
                        color = EmeraldAccent,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = track.title.ifBlank { "Featured Track" },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 20.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFFD2D2D6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = EmeraldAccent,
                    modifier = Modifier
                        .size(48.dp)
                        .sonoraPressable(shape = CircleShape, pressedScale = 0.92f, onClick = onPlayToggle)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
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
        color = QuickAccessTileBg,
        modifier = modifier
            .height(56.dp)
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
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
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
                    modifier = Modifier.padding(end = 10.dp)
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
            .sonoraPressable(shape = RoundedCornerShape(12.dp), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SonoraArtwork(
            artworkUrl = artist.avatarUrl,
            title = artist.name,
            isCircle = true,
            modifier = Modifier
                .size(112.dp)
                .border(1.dp, HeaderButtonBorder, CircleShape)
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
            if (track.hasMusicVideo || track.source == TrackSource.YOUTUBE || track.youtubeVideoId.isNotBlank()) {
                SonoraBadge(
                    text = "MV",
                    tint = EmeraldAccent,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                )
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

@Composable
private fun YouTubeImportActionPill(
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color(0xFF23191B),
        border = BorderStroke(1.dp, YouTubeAccentTint.copy(alpha = 0.45f)),
        modifier = Modifier.sonoraPressable(shape = CircleShape, pressedScale = 0.96f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SmartDisplay,
                contentDescription = null,
                tint = YouTubeAccentTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "YouTube Import",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ImportYouTubeMiniChip(
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color(0xFF202026),
        border = BorderStroke(1.dp, HeaderButtonBorder),
        modifier = Modifier.sonoraPressable(shape = CircleShape, pressedScale = 0.96f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = EmeraldAccent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Import YouTube",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ImportYouTubeBannerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = QuickAccessTileBg,
        border = BorderStroke(1.dp, HeaderButtonBorder),
        modifier = modifier
            .fillMaxWidth()
            .sonoraPressable(shape = RoundedCornerShape(14.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(YouTubeAccentTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmartDisplay,
                    contentDescription = null,
                    tint = YouTubeAccentTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Import Official Music Videos",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Paste a YouTube link or tap to load Wish 107.5 & Global MV packs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
