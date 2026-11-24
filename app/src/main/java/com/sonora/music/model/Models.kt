package com.sonora.music.model

enum class TrackSource(val badgeLabel: String) {
    AUDIUS("STREAM"),
    DEEZER("CATALOG"),
    ITUNES("CATALOG"),
    PODCAST("PODCAST"),
    AUDIOBOOK("AUDIOBOOK"),
    RADIO("LIVE"),
    LOCAL("LOCAL")
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    val album: String = "",
    val genre: String = "Music",
    val mood: String = "",
    val durationSec: Int = 0,
    val artworkUrl: String? = null,
    val artworkMirrors: List<String> = emptyList(),
    val streamUrl: String = "",
    val source: TrackSource = TrackSource.DEEZER,
    val isLive: Boolean = false,
    val isSmartPick: Boolean = false,
    val releaseDate: String = "",
    val playCount: Long = 0L,
    val localUri: String? = null,
    val externalUrl: String = "",
    val isPreviewOnly: Boolean = false
) {
    val displayArtist: String
        get() = artist.ifBlank { "Featured Artist" }

    val formattedDuration: String
        get() {
            if (isLive) return "LIVE"
            if (durationSec <= 0) return "--:--"
            val hours = durationSec / 3600
            val minutes = (durationSec % 3600) / 60
            val seconds = durationSec % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }
}

data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val trackIds: List<String> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val coverColorStart: Long = 0xFF1DB954L,
    val coverColorEnd: Long = 0xFF181818L,
    val artworkUrl: String? = null,
    val isUserCreated: Boolean = true,
    val isSystemMix: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val authorName: String = "You"
)

data class Artist(
    val id: String,
    val name: String,
    val handle: String = "",
    val avatarUrl: String? = null,
    val followerCount: Long = 0L,
    val trackCount: Int = 0,
    val bio: String = ""
)

data class PodcastShow(
    val id: String,
    val title: String,
    val author: String,
    val artworkUrl: String? = null,
    val feedUrl: String,
    val genre: String = "Podcast"
)

data class Audiobook(
    val id: String,
    val title: String,
    val author: String,
    val artworkUrl: String? = null,
    val description: String = "",
    val downloads: Long = 0L
)

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsResult(
    val trackKey: String,
    val syncedLines: List<LyricLine> = emptyList(),
    val plainLyrics: String? = null,
    val isInstrumental: Boolean = false
)

data class ListeningEvent(
    val trackId: String,
    val title: String,
    val artist: String,
    val genre: String,
    val artworkUrl: String? = null,
    val listenedMs: Long,
    val timestampMs: Long = System.currentTimeMillis()
)

data class WrappedTopItem(
    val name: String,
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val playCount: Int = 0,
    val totalMinutes: Int = 0
)

data class WrappedStats(
    val totalMinutes: Int = 0,
    val totalPlays: Int = 0,
    val uniqueTracks: Int = 0,
    val uniqueArtists: Int = 0,
    val topTracks: List<WrappedTopItem> = emptyList(),
    val topArtists: List<WrappedTopItem> = emptyList(),
    val topGenres: List<WrappedTopItem> = emptyList(),
    val personaTitle: String = "OPM & Global Listener",
    val personaSubtitle: String = "Blending local Philippine hits with global charts.",
    val auraColors: List<Long> = listOf(0xFF1DB954L, 0xFF2E3C48L, 0xFF181818L)
)

enum class EqPreset(
    val label: String,
    val bandOffsetsMb: List<Int>
) {
    FLAT("Flat", listOf(0, 0, 0, 0, 0)),
    BASS_BOOST("Bass Boost", listOf(600, 450, 100, 50, 100)),
    VOCAL("Vocal Boost", listOf(-150, 100, 450, 400, 150)),
    POP("Pop", listOf(-100, 200, 400, 250, -50)),
    ROCK("Rock", listOf(450, 250, -100, 300, 450)),
    HIP_HOP("Hip-Hop", listOf(550, 350, 0, 150, 300)),
    LOFI("Lo-Fi Warm", listOf(300, 200, 50, -250, -400)),
    ELECTRONIC("Electronic", listOf(500, 150, -50, 350, 500)),
    CUSTOM("Custom", listOf(0, 0, 0, 0, 0))
}

data class AudioSettings(
    val eqEnabled: Boolean = true,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val bandLevelsMb: List<Int> = listOf(0, 0, 0, 0, 0),
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val crossfadeSeconds: Int = 3,
    val playbackSpeed: Float = 1.0f,
    val privateSession: Boolean = false,
    val streamingQualityLabel: String = "High Quality"
)

enum class DownloadStatus {
    NONE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

data class DownloadState(
    val status: DownloadStatus = DownloadStatus.NONE,
    val progress: Float = 0f,
    val fileSizeBytes: Long = 0L,
    val localPath: String? = null
)

enum class ShuffleMode {
    OFF,
    SHUFFLE,
    SMART_SHUFFLE
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class SleepTimerState(
    val active: Boolean = false,
    val remainingMs: Long = 0L,
    val endOfTrack: Boolean = false,
    val label: String = "Off"
)

data class AudioOutputDevice(
    val id: String,
    val name: String,
    val typeLabel: String,
    val isActive: Boolean = false
)

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val shuffleMode: ShuffleMode = ShuffleMode.OFF,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1.0f,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val errorMessage: String? = null
)

enum class AppTab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    LIBRARY("Library")
}

sealed interface DetailRoute {
    data object None : DetailRoute
    data class PlaylistDetail(val playlist: Playlist) : DetailRoute
    data class ArtistDetail(val artist: Artist) : DetailRoute
    data class PodcastDetail(val show: PodcastShow) : DetailRoute
    data class AudiobookDetail(val book: Audiobook) : DetailRoute
    data object LikedSongs : DetailRoute
    data object Downloads : DetailRoute
    data object LocalFiles : DetailRoute
    data object JamQueue : DetailRoute
}
