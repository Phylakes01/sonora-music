package com.sonora.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.music.model.Artist
import com.sonora.music.model.Audiobook
import com.sonora.music.model.DetailRoute
import com.sonora.music.model.DownloadStatus
import com.sonora.music.model.Playlist
import com.sonora.music.model.PodcastShow
import com.sonora.music.model.SearchCategory
import com.sonora.music.model.SonoraActions
import com.sonora.music.model.SonoraUiState
import com.sonora.music.model.Track
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
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianStrokeSoft
import com.sonora.music.ui.theme.ObsidianSurface
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import com.sonora.music.ui.theme.VioletPulse

private data class VibeCardSpec(
    val title: String,
    val subtitle: String,
    val query: String,
    val category: SearchCategory,
    val colorStart: Color,
    val colorEnd: Color,
    val icon: ImageVector
)

private val BrowseVibes = listOf(
    VibeCardSpec("Electronic & Synth", "Club, House & Bass", "electronic synthwave", SearchCategory.SONGS, CyanGlow, Color(0xFF0A2444), Icons.Rounded.Bolt),
    VibeCardSpec("Lo-Fi & Study", "Warm beats for deep focus", "lofi chill beats", SearchCategory.SONGS, EmeraldAccent, Color(0xFF0A2E2A), Icons.Rounded.LocalCafe),
    VibeCardSpec("Night Drive", "Atmospheric midnight runs", "night drive", SearchCategory.PLAYLISTS, VioletPulse, Color(0xFF1A123B), Icons.Rounded.DirectionsCar),
    VibeCardSpec("Gym & Phonk", "High-BPM adrenaline", "phonk workout", SearchCategory.SONGS, CoralHeart, Color(0xFF3E1025), Icons.Rounded.FitnessCenter),
    VibeCardSpec("Ambient & Sleep", "Calm soundscapes", "ambient sleep", SearchCategory.SONGS, Color(0xFF38BDF8), Color(0xFF132145), Icons.Rounded.Spa),
    VibeCardSpec("Indie & Rock", "Guitars & alt anthems", "indie rock", SearchCategory.SONGS, AmberGold, Color(0xFF3A220A), Icons.Rounded.MusicNote),
    VibeCardSpec("Podcasts", "Tech, culture & stories", "technology music", SearchCategory.PODCASTS, CyanGlow, Color(0xFF0D283A), Icons.Rounded.Podcasts),
    VibeCardSpec("Classic Books", "Free LibriVox editions", "adventure mystery", SearchCategory.AUDIOBOOKS, AmberGold, Color(0xFF3B1F0B), Icons.AutoMirrored.Rounded.MenuBook),
    VibeCardSpec("Global Radio", "Live 24/7 world stations", "electronic jazz", SearchCategory.RADIO, CoralHeart, Color(0xFF310D22), Icons.Rounded.Radio),
    VibeCardSpec("After Hours", "R&B, Soul & slow grooves", "r&b soul", SearchCategory.SONGS, VioletPulse, Color(0xFF281242), Icons.Rounded.Nightlight)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: SonoraUiState,
    actions: SonoraActions,
    onTrackOverflow: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val search = state.search
    val accent = LocalSonoraAccent.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        SonoraAmbientBackdrop(
            accentColor = CyanGlow,
            secondaryColor = accent
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Sticky Search Header
            Surface(
                color = ObsidianBg.copy(alpha = 0.88f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Search",
                            style = MaterialTheme.typography.displaySmall,
                            color = TextPrimary
                        )
                        SonoraBadge(
                            text = "UNLIMITED CATALOG",
                            tint = accent
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search Field
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianCard,
                        border = BorderStroke(
                            1.2.dp,
                            if (search.query.isNotBlank()) accent.copy(alpha = 0.65f) else ObsidianStroke
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = if (search.query.isNotBlank()) accent else TextSecondary,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (search.query.isEmpty()) {
                                    Text(
                                        text = "Songs, artists, podcasts, audiobooks, radio...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = search.query,
                                    onValueChange = actions::updateSearchQuery,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                                    cursorBrush = SolidColor(accent),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (search.query.isNotEmpty()) {
                                IconButton(
                                    onClick = { actions.updateSearchQuery("") },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear search",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Filter Pills
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SearchCategory.entries, key = { it.name }) { category ->
                            SonoraPillChip(
                                label = category.label,
                                selected = search.category == category,
                                onClick = { actions.selectSearchCategory(category) }
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 164.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Glowing AI Mood Playlist Studio Hero CTA
                item(key = "ai_studio_cta") {
                    AiStudioHeroCard(
                        queryHint = search.query,
                        onClick = { actions.openAiGenerator(search.query) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                if (search.query.isBlank()) {
                    // Recent Search Chips
                    if (search.recentSearches.isNotEmpty()) {
                        item(key = "recent_searches") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            ) {
                                SonoraSectionHeader(
                                    title = "Recent Searches",
                                    subtitle = "Jump back into previous queries"
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    search.recentSearches.forEach { term ->
                                        Surface(
                                            shape = CircleShape,
                                            color = ObsidianCard,
                                            border = BorderStroke(1.dp, ObsidianStrokeSoft),
                                            modifier = Modifier.sonoraPressable(shape = CircleShape) {
                                                actions.applyRecentSearch(term)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    start = 12.dp,
                                                    end = 6.dp,
                                                    top = 6.dp,
                                                    bottom = 6.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.History,
                                                    contentDescription = null,
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = term,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = TextPrimary,
                                                    maxLines = 1
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .clickable { actions.removeRecentSearch(term) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "Remove",
                                                        tint = TextMuted,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2-Column Genre & Vibe Cards
                    item(key = "browse_vibes") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            SonoraSectionHeader(
                                title = "Browse Genres & Vibes",
                                subtitle = "Tap any tile to dive into live audio"
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                BrowseVibes.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        pair.forEach { vibe ->
                                            VibeTileCard(
                                                spec = vibe,
                                                onClick = {
                                                    actions.selectSearchCategory(vibe.category)
                                                    actions.applyRecentSearch(vibe.query)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (pair.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Live Multi-Category Search Results
                    val hasAnyForAll = search.songs.isNotEmpty() ||
                        search.artists.isNotEmpty() ||
                        search.playlists.isNotEmpty() ||
                        search.podcasts.isNotEmpty() ||
                        search.audiobooks.isNotEmpty() ||
                        search.radioStations.isNotEmpty()

                    val hasResultsForActive = when (search.category) {
                        SearchCategory.ALL -> hasAnyForAll
                        SearchCategory.SONGS -> search.songs.isNotEmpty()
                        SearchCategory.ARTISTS -> search.artists.isNotEmpty()
                        SearchCategory.PLAYLISTS -> search.playlists.isNotEmpty()
                        SearchCategory.PODCASTS -> search.podcasts.isNotEmpty()
                        SearchCategory.AUDIOBOOKS -> search.audiobooks.isNotEmpty()
                        SearchCategory.RADIO -> search.radioStations.isNotEmpty()
                    }

                    if (search.isSearching && !hasResultsForActive) {
                        items(6) { ShimmerRowPlaceholder() }
                    } else if (!search.isSearching && !hasResultsForActive) {
                        item(key = "empty_search") {
                            EmptySearchCard(
                                query = search.query,
                                category = search.category,
                                onResetCategory = { actions.selectSearchCategory(SearchCategory.ALL) },
                                onOpenAi = { actions.openAiGenerator(search.query) },
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    } else {
                        // Songs
                        if ((search.category == SearchCategory.ALL || search.category == SearchCategory.SONGS) &&
                            search.songs.isNotEmpty()
                        ) {
                            item(key = "songs_header") {
                                SonoraSectionHeader(
                                    title = "Songs",
                                    actionLabel = if (search.category == SearchCategory.ALL && search.songs.size > 5) "See all" else null,
                                    onActionClick = { actions.selectSearchCategory(SearchCategory.SONGS) },
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                            val shownSongs = if (search.category == SearchCategory.ALL) {
                                search.songs.take(6)
                            } else {
                                search.songs
                            }
                            items(shownSongs, key = { "search_song_${it.id}" }) { track ->
                                val isCurrent = state.playback.currentTrack?.id == track.id
                                Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                                    TrackRow(
                                        track = track,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && state.playback.isPlaying,
                                        isLiked = track.id in state.likedTrackIds,
                                        downloadState = state.downloads[track.id],
                                        onClick = { actions.playTrack(track, search.songs) },
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

                        // Artists
                        if ((search.category == SearchCategory.ALL || search.category == SearchCategory.ARTISTS) &&
                            search.artists.isNotEmpty()
                        ) {
                            item(key = "artists_section") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    SonoraSectionHeader(
                                        title = "Artists",
                                        actionLabel = if (search.category == SearchCategory.ALL && search.artists.size > 5) "See all" else null,
                                        onActionClick = { actions.selectSearchCategory(SearchCategory.ARTISTS) },
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (search.category == SearchCategory.ALL) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            items(search.artists, key = { it.id }) { artist ->
                                                ArtistCircleCard(
                                                    artist = artist,
                                                    onClick = { actions.openDetail(DetailRoute.ArtistDetail(artist)) }
                                                )
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 20.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            search.artists.forEach { artist ->
                                                ArtistRowCard(
                                                    artist = artist,
                                                    onClick = { actions.openDetail(DetailRoute.ArtistDetail(artist)) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Playlists
                        if ((search.category == SearchCategory.ALL || search.category == SearchCategory.PLAYLISTS) &&
                            search.playlists.isNotEmpty()
                        ) {
                            item(key = "playlists_section") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    SonoraSectionHeader(
                                        title = "Playlists",
                                        actionLabel = if (search.category == SearchCategory.ALL && search.playlists.size > 5) "See all" else null,
                                        onActionClick = { actions.selectSearchCategory(SearchCategory.PLAYLISTS) },
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (search.category == SearchCategory.ALL) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            items(search.playlists, key = { it.id }) { playlist ->
                                                SearchPlaylistCard(
                                                    playlist = playlist,
                                                    onClick = { actions.openDetail(DetailRoute.PlaylistDetail(playlist)) }
                                                )
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 20.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            search.playlists.forEach { playlist ->
                                                SearchPlaylistRow(
                                                    playlist = playlist,
                                                    onClick = { actions.openDetail(DetailRoute.PlaylistDetail(playlist)) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Podcasts
                        if ((search.category == SearchCategory.ALL || search.category == SearchCategory.PODCASTS) &&
                            search.podcasts.isNotEmpty()
                        ) {
                            item(key = "podcasts_section") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    SonoraSectionHeader(
                                        title = "Podcasts",
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Column(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        search.podcasts.forEach { show ->
                                            SearchPodcastRow(
                                                show = show,
                                                onClick = { actions.openDetail(DetailRoute.PodcastDetail(show)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Audiobooks
                        if ((search.category == SearchCategory.ALL || search.category == SearchCategory.AUDIOBOOKS) &&
                            search.audiobooks.isNotEmpty()
                        ) {
                            item(key = "audiobooks_section") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    SonoraSectionHeader(
                                        title = "Audiobooks",
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Column(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        search.audiobooks.forEach { book ->
                                            SearchAudiobookRow(
                                                book = book,
                                                onClick = { actions.openDetail(DetailRoute.AudiobookDetail(book)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Radio Stations
                        if ((search.category == SearchCategory.ALL || search.category == SearchCategory.RADIO) &&
                            search.radioStations.isNotEmpty()
                        ) {
                            item(key = "radio_header") {
                                SonoraSectionHeader(
                                    title = "Live Radio Stations",
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                            items(search.radioStations, key = { "search_radio_${it.id}" }) { station ->
                                val isCurrent = state.playback.currentTrack?.id == station.id
                                Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                                    TrackRow(
                                        track = station,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && state.playback.isPlaying,
                                        isLiked = station.id in state.likedTrackIds,
                                        downloadState = null,
                                        onClick = { actions.playTrack(station, search.radioStations) },
                                        onLikeClick = { actions.toggleLike(station) },
                                        onDownloadClick = {},
                                        onMoreClick = { onTrackOverflow(station) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiStudioHeroCard(
    queryHint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(
            1.2.dp,
            Brush.linearGradient(listOf(EmeraldAccent, CyanGlow, VioletPulse))
        ),
        modifier = modifier
            .fillMaxWidth()
            .sonoraPressable(shape = RoundedCornerShape(20.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            EmeraldAccent.copy(alpha = 0.16f),
                            VioletPulse.copy(alpha = 0.18f),
                            ObsidianCard
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(listOf(EmeraldAccent, CyanGlow))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = ObsidianBg,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI Mood Playlist Studio",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (queryHint.isBlank()) {
                        "Describe any vibe, activity or mood — craft a custom mix"
                    } else {
                        "Generate an instant AI mix from \"$queryHint\""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = EmeraldAccent
            ) {
                Text(
                    text = "Create",
                    style = MaterialTheme.typography.labelLarge,
                    color = ObsidianBg,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun VibeTileCard(
    spec: VibeCardSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = spec.colorEnd,
        border = BorderStroke(1.dp, spec.colorStart.copy(alpha = 0.42f)),
        modifier = modifier
            .height(92.dp)
            .sonoraPressable(shape = RoundedCornerShape(18.dp), onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            spec.colorStart.copy(alpha = 0.28f),
                            spec.colorEnd.copy(alpha = 0.92f)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = spec.colorStart.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
            )
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = spec.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistCircleCard(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(116.dp)
            .sonoraPressable(shape = RoundedCornerShape(16.dp), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SonoraArtwork(
            artworkUrl = artist.avatarUrl,
            title = artist.name,
            isCircle = true,
            colorStart = 0xFF8C6EFFL,
            colorEnd = 0xFF1B1540L,
            modifier = Modifier.size(104.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (artist.followerCount > 0) "${formatCompactCount(artist.followerCount)} fans" else "Artist",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun ArtistRowCard(
    artist: Artist,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, ObsidianStrokeSoft),
        modifier = Modifier
            .fillMaxWidth()
            .sonoraPressable(shape = RoundedCornerShape(14.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SonoraArtwork(
                artworkUrl = artist.avatarUrl,
                title = artist.name,
                isCircle = true,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        if (artist.handle.isNotBlank()) append("@${artist.handle} • ")
                        append("${formatCompactCount(artist.followerCount)} followers")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(142.dp)
            .sonoraPressable(shape = RoundedCornerShape(16.dp), onClick = onClick)
    ) {
        SonoraArtwork(
            artworkUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
            title = playlist.name,
            cornerRadius = 14.dp,
            colorStart = playlist.coverColorStart,
            colorEnd = playlist.coverColorEnd,
            modifier = Modifier.size(142.dp)
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
            text = "by ${playlist.authorName}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchPlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, ObsidianStrokeSoft),
        modifier = Modifier
            .fillMaxWidth()
            .sonoraPressable(shape = RoundedCornerShape(14.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SonoraArtwork(
                artworkUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
                title = playlist.name,
                cornerRadius = 10.dp,
                colorStart = playlist.coverColorStart,
                colorEnd = playlist.coverColorEnd,
                modifier = Modifier.size(52.dp)
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
                Text(
                    text = "Playlist • ${playlist.authorName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchPodcastRow(
    show: PodcastShow,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, ObsidianStrokeSoft),
        modifier = Modifier
            .fillMaxWidth()
            .sonoraPressable(shape = RoundedCornerShape(14.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SonoraArtwork(
                artworkUrl = show.artworkUrl,
                title = show.title,
                cornerRadius = 10.dp,
                colorStart = 0xFF00E5FFL,
                colorEnd = 0xFF0F2744L,
                modifier = Modifier.size(52.dp)
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
                Text(
                    text = "${show.author} • ${show.genre}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SonoraBadge(text = "PODCAST", tint = CyanGlow)
        }
    }
}

@Composable
private fun SearchAudiobookRow(
    book: Audiobook,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, ObsidianStrokeSoft),
        modifier = Modifier
            .fillMaxWidth()
            .sonoraPressable(shape = RoundedCornerShape(14.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SonoraArtwork(
                artworkUrl = book.artworkUrl,
                title = book.title,
                cornerRadius = 10.dp,
                colorStart = 0xFFFFB74DL,
                colorEnd = 0xFF3F230CL,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author.ifBlank { "LibriVox Classic" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SonoraBadge(text = "BOOK", tint = AmberGold)
        }
    }
}

@Composable
private fun EmptySearchCard(
    query: String,
    category: SearchCategory,
    onResetCategory: () -> Unit,
    onOpenAi: () -> Unit,
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
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No ${category.label.lowercase()} matches for \"$query\"",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Try searching across All categories or spin up an AI Mood Playlist.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (category != SearchCategory.ALL) {
                    SonoraPillChip(
                        label = "Search All",
                        selected = true,
                        onClick = onResetCategory
                    )
                }
                SonoraPillChip(
                    label = "Ask AI Studio",
                    selected = true,
                    accentColor = VioletPulse,
                    leadingIcon = Icons.Rounded.AutoAwesome,
                    onClick = onOpenAi
                )
            }
        }
    }
}

private fun formatCompactCount(count: Long): String {
    return when {
        count >= 1_000_000L -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000L -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}
