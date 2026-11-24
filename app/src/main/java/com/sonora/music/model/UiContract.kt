package com.sonora.music.model

import androidx.compose.ui.graphics.Color

enum class SearchCategory(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    VIDEOS("Videos"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    PODCASTS("Podcasts"),
    AUDIOBOOKS("Audiobooks"),
    RADIO("Radio")
}

data class HomeFeedState(
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val selectedFilter: String = "All",
    val selectedGenre: String = "OPM",
    val quickPicks: List<Track> = emptyList(),
    val madeForYou: List<Playlist> = emptyList(),
    val phTopTracks: List<Track> = emptyList(),
    val opmTracks: List<Track> = emptyList(),
    val phArtists: List<Artist> = emptyList(),
    val globalTopTracks: List<Track> = emptyList(),
    val globalArtists: List<Artist> = emptyList(),
    val trendingTracks: List<Track> = emptyList(),
    val genreTracks: List<Track> = emptyList(),
    val trendingPlaylists: List<Playlist> = emptyList(),
    val podcasts: List<PodcastShow> = emptyList(),
    val audiobooks: List<Audiobook> = emptyList(),
    val liveRadio: List<Track> = emptyList(),
    val recentlyPlayed: List<Track> = emptyList(),
    val errorMessage: String? = null,
    val musicVideos: List<Track> = emptyList()
)

data class SearchState(
    val query: String = "",
    val category: SearchCategory = SearchCategory.ALL,
    val isSearching: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val songs: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val podcasts: List<PodcastShow> = emptyList(),
    val audiobooks: List<Audiobook> = emptyList(),
    val radioStations: List<Track> = emptyList(),
    val aiPrompt: String = "",
    val isGeneratingAiPlaylist: Boolean = false,
    val generatedAiPlaylist: Playlist? = null,
    val musicVideos: List<Track> = emptyList()
)

data class LibraryState(
    val userPlaylists: List<Playlist> = emptyList(),
    val likedSongs: List<Track> = emptyList(),
    val downloadedTracks: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val hasLocalAudioPermission: Boolean = false,
    val isScanningLocal: Boolean = false,
    val followedArtistIds: Set<String> = emptySet(),
    val totalDownloadedBytes: Long = 0L
)

data class DetailState(
    val route: DetailRoute = DetailRoute.None,
    val isLoading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val artist: Artist? = null,
    val playlist: Playlist? = null,
    val podcast: PodcastShow? = null,
    val audiobook: Audiobook? = null
)

data class SonoraUiState(
    val currentTab: AppTab = AppTab.HOME,
    val home: HomeFeedState = HomeFeedState(),
    val search: SearchState = SearchState(),
    val library: LibraryState = LibraryState(),
    val detail: DetailState = DetailState(),
    val playback: PlaybackState = PlaybackState(),
    val audioSettings: AudioSettings = AudioSettings(),
    val downloads: Map<String, DownloadState> = emptyMap(),
    val currentLyrics: LyricsResult? = null,
    val isLoadingLyrics: Boolean = false,
    val outputDevices: List<AudioOutputDevice> = emptyList(),
    val wrappedStats: WrappedStats = WrappedStats(),
    val dominantAccent: Color = Color(0xFF1DB954),
    val showIntroOverlay: Boolean = false,
    val isFullPlayerOpen: Boolean = false,
    val isLyricsOpen: Boolean = false,
    val isQueueOpen: Boolean = false,
    val isAudioStudioOpen: Boolean = false,
    val isWrappedOpen: Boolean = false,
    val isAiGeneratorOpen: Boolean = false,
    val snackbarMessage: String? = null,
    val isVideoMode: Boolean = false,
    val isImportYouTubeOpen: Boolean = false,
    val isImportingYouTube: Boolean = false,
    val youtubeImportError: String? = null
) {
    val likedTrackIds: Set<String>
        get() = library.likedSongs.mapTo(HashSet()) { it.id }
}

interface SonoraActions {
    fun playTrack(track: Track, queue: List<Track> = listOf(track))
    fun togglePlayPause()
    fun skipNext()
    fun skipPrev()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun playNext(track: Track)
    fun addToQueue(track: Track)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun removeQueueItem(index: Int)
    fun clearQueue()
    fun startRadio(seedTrack: Track)
    fun startArtistRadio(artist: Artist)

    fun toggleLike(track: Track)
    fun toggleFollowArtist(artist: Artist)
    fun createPlaylist(name: String, description: String, colorStart: Long, colorEnd: Long, initialTrack: Track? = null)
    fun renamePlaylist(playlistId: String, newName: String, newDescription: String)
    fun deletePlaylist(playlistId: String)
    fun addTrackToPlaylist(playlistId: String, track: Track)
    fun removeTrackFromPlaylist(playlistId: String, trackId: String)
    fun reorderPlaylistTrack(playlistId: String, fromIndex: Int, toIndex: Int)
    fun exportPlaylistCode(playlist: Playlist): String
    fun exportQueueCode(): String
    fun importPlaylistCode(code: String, mergeIntoJamQueue: Boolean = false)

    fun downloadTrack(track: Track)
    fun downloadPlaylist(tracks: List<Track>)
    fun deleteDownload(trackId: String)
    fun deleteAllDownloads()
    fun scanLocalFiles()
    fun requestLocalAudioPermission()

    fun updateSearchQuery(query: String)
    fun selectSearchCategory(category: SearchCategory)
    fun applyRecentSearch(query: String)
    fun removeRecentSearch(query: String)
    fun openAiGenerator(initialPrompt: String = "")
    fun closeAiGenerator()
    fun generateAiPlaylist(prompt: String)
    fun saveGeneratedPlaylist(playlist: Playlist)

    fun openDetail(route: DetailRoute)
    fun closeDetail()
    fun selectTab(tab: AppTab)
    fun dismissIntroOverlay()
    fun openFullPlayer()
    fun closeFullPlayer()
    fun openLyrics()
    fun closeLyrics()
    fun openQueue()
    fun closeQueue()
    fun openAudioStudio()
    fun closeAudioStudio()
    fun openWrapped()
    fun closeWrapped()

    fun updateAudioSettings(transform: (AudioSettings) -> AudioSettings)
    fun applyEqPreset(preset: EqPreset)
    fun setSleepTimer(minutes: Int, endOfTrack: Boolean = false)
    fun cancelSleepTimer()
    fun openOutputPicker()
    fun refreshOutputDevices()

    fun shareTrack(track: Track)
    fun sharePlaylist(playlist: Playlist)
    fun shareWrapped()
    fun selectHomeFilter(filter: String)
    fun openFullTrackExternal(track: Track)
    fun loadMoreGenre(genre: String)
    fun refreshHome()
    fun dismissSnackbar()

    fun onSetVideoMode(enabled: Boolean) {}
    fun onPlayMusicVideo(track: Track, queue: List<Track> = listOf(track)) {}
    fun onOpenImportYouTube() {}
    fun onCloseImportYouTube() {}
    fun onImportYouTube(query: String) {}
    fun onImportYouTubePack(packId: String) {}
}
