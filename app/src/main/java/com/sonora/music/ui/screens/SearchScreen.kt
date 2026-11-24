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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.components.SonoraPillChip
import com.sonora.music.ui.components.SonoraSectionHeader
import com.sonora.music.ui.components.TrackRow
import com.sonora.music.ui.components.sonoraPressable
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary

private data class BrowseTileSpec(
    val title: String,
    val query: String,
    val category: SearchCategory,
    val color: Color
)

private val BrowseTiles = listOf(
    BrowseTileSpec("Top PH", "OPM hits", SearchCategory.SONGS, Color(0xFF1E6B45)),
    BrowseTileSpec("OPM", "Cup of Joe BINI Dionela", SearchCategory.SONGS, Color(0xFF9E4234)),
    BrowseTileSpec("P-Pop", "BINI SB19", SearchCategory.SONGS, Color(0xFF2D5982)),
    BrowseTileSpec("Pinoy Hip-Hop", "Hev Abi Al James", SearchCategory.SONGS, Color(0xFF693B62)),
    BrowseTileSpec("Global Pop", "Taylor Swift Sabrina Carpenter", SearchCategory.SONGS, Color(0xFF28636B)),
    BrowseTileSpec("K-Pop", "JENNIE NewJeans aespa", SearchCategory.SONGS, Color(0xFF7A2F46)),
    BrowseTileSpec("R&B", "The Weeknd SZA", SearchCategory.SONGS, Color(0xFF424075)),
    BrowseTileSpec("Rock", "IV Of Spades Eraserheads", SearchCategory.SONGS, Color(0xFF823333)),
    BrowseTileSpec("Chill", "Ben&Ben Arthur Nery", SearchCategory.SONGS, Color(0xFF3A6351)),
    BrowseTileSpec("Throwback OPM", "Parokya ni Edgar Silent Sanctuary", SearchCategory.SONGS, Color(0xFF7A5D2C)),
    BrowseTileSpec("PH Podcasts", "philippines", SearchCategory.PODCASTS, Color(0xFF23565E)),
    BrowseTileSpec("PH Radio", "manila", SearchCategory.RADIO, Color(0xFF523E5C))
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
    ) {
        // Header + Search Input + Category Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianBg)
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )

                Surface(
                    shape = CircleShape,
                    color = ObsidianCard,
                    modifier = Modifier.sonoraPressable(shape = CircleShape) {
                        actions.openAiGenerator(search.query)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Mood Mix",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Solid White Rounded-8.dp Search Field
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = ObsidianBg,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (search.query.isEmpty()) {
                            Text(
                                text = "What do you want to listen to?",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFF535353),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = search.query,
                            onValueChange = actions::updateSearchQuery,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = ObsidianBg,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(ObsidianBg),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (search.query.isNotEmpty()) {
                        IconButton(
                            onClick = { actions.updateSearchQuery("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = ObsidianBg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchCategory.entries, key = { it.name }) { category ->
                    SonoraPillChip(
                        label = category.label,
                        selected = search.category == category,
                        accentColor = EmeraldAccent,
                        onClick = { actions.selectSearchCategory(category) }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 148.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (search.query.isBlank()) {
                // Recent Searches
                if (search.recentSearches.isNotEmpty()) {
                    item(key = "recent_searches") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            SonoraSectionHeader(title = "Recent searches")
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                search.recentSearches.forEach { term ->
                                    Surface(
                                        shape = CircleShape,
                                        color = ObsidianCard,
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

                // 2-Column Flat Color-Blocked Browse Tiles
                item(key = "browse_all") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        SonoraSectionHeader(title = "Browse all")
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            BrowseTiles.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    pair.forEach { tile ->
                                        BrowseCategoryTile(
                                            spec = tile,
                                            onClick = {
                                                actions.selectSearchCategory(tile.category)
                                                actions.applyRecentSearch(tile.query)
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
                            onOpenMoodMix = { actions.openAiGenerator(search.query) },
                            modifier = Modifier.padding(horizontal = 16.dp)
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
                                actionLabel = if (search.category == SearchCategory.ALL && search.songs.size > 6) "See all" else null,
                                onActionClick = { actions.selectSearchCategory(SearchCategory.SONGS) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        val shownSongs = if (search.category == SearchCategory.ALL) {
                            search.songs.take(6)
                        } else {
                            search.songs
                        }
                        items(shownSongs, key = { "search_song_${it.id}" }) { track ->
                            val isCurrent = state.playback.currentTrack?.id == track.id
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
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                if (search.category == SearchCategory.ALL) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        items(search.artists, key = { it.id }) { artist ->
                                            SearchArtistCircleCard(
                                                artist = artist,
                                                onClick = { actions.openDetail(DetailRoute.ArtistDetail(artist)) }
                                            )
                                        }
                                    }
                                } else {
                                    Column {
                                        search.artists.forEach { artist ->
                                            SearchArtistRow(
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
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                if (search.category == SearchCategory.ALL) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(search.playlists, key = { it.id }) { playlist ->
                                            SearchPlaylistCard(
                                                playlist = playlist,
                                                onClick = { actions.openDetail(DetailRoute.PlaylistDetail(playlist)) }
                                            )
                                        }
                                    }
                                } else {
                                    Column {
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
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Column {
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
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Column {
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
                                title = "Live Radio",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(search.radioStations, key = { "search_radio_${it.id}" }) { station ->
                            val isCurrent = state.playback.currentTrack?.id == station.id
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

@Composable
private fun BrowseCategoryTile(
    spec: BrowseTileSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = spec.color,
        modifier = modifier
            .height(74.dp)
            .sonoraPressable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
private fun SearchArtistCircleCard(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .sonoraPressable(shape = RoundedCornerShape(8.dp), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SonoraArtwork(
            artworkUrl = artist.avatarUrl,
            title = artist.name,
            isCircle = true,
            modifier = Modifier.size(96.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchArtistRow(
    artist: Artist,
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
                text = if (artist.followerCount > 0) {
                    "Artist • ${formatCompactCount(artist.followerCount)} fans"
                } else {
                    "Artist"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            .width(140.dp)
            .sonoraPressable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) {
        SonoraArtwork(
            artworkUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
            title = playlist.name,
            cornerRadius = 8.dp,
            colorStart = playlist.coverColorStart,
            colorEnd = playlist.coverColorEnd,
            modifier = Modifier.size(140.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.authorName,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SonoraArtwork(
            artworkUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
            title = playlist.name,
            cornerRadius = 4.dp,
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

@Composable
private fun SearchPodcastRow(
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
            cornerRadius = 4.dp,
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
                text = "Podcast • ${show.author}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchAudiobookRow(
    book: Audiobook,
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
            artworkUrl = book.artworkUrl,
            title = book.title,
            cornerRadius = 4.dp,
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
                text = "Audiobook • ${book.author.ifBlank { "LibriVox" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptySearchCard(
    query: String,
    category: SearchCategory,
    onResetCategory: () -> Unit,
    onOpenMoodMix: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ObsidianCard,
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
                text = "No results found for \"$query\"",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Try checking your spelling or searching across All.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (category != SearchCategory.ALL) {
                    SonoraPillChip(
                        label = "Search All",
                        selected = true,
                        accentColor = EmeraldAccent,
                        onClick = onResetCategory
                    )
                }
                SonoraPillChip(
                    label = "Mood Mix",
                    selected = false,
                    onClick = onOpenMoodMix
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
