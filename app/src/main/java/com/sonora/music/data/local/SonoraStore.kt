package com.sonora.music.data.local

import android.content.Context
import com.sonora.music.data.network.RegionalContentFilter
import com.sonora.music.model.AudioSettings
import com.sonora.music.model.EqPreset
import com.sonora.music.model.ListeningEvent
import com.sonora.music.model.Playlist
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Locale
import java.util.UUID

class SonoraStore(private val rootDir: File) {

    constructor(context: Context) : this(File(context.filesDir, "sonora_db"))

    private val lock = Any()

    private val playlistsFile get() = File(rootDir, "playlists.json")
    private val likedSongsFile get() = File(rootDir, "liked_songs.json")
    private val followedArtistsFile get() = File(rootDir, "followed_artists.json")
    private val historyFile get() = File(rootDir, "listening_history.json")
    private val downloadedTracksFile get() = File(rootDir, "downloaded_tracks.json")
    private val importedYouTubeTracksFile get() = File(rootDir, "imported_youtube_tracks.json")
    private val recentSearchesFile get() = File(rootDir, "recent_searches.json")
    private val audioSettingsFile get() = File(rootDir, "audio_settings.json")

    fun loadPlaylists(): List<Playlist> = synchronized(lock) {
        val raw = readFileText(playlistsFile) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    playlistFromJson(obj)?.let(::add)
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun savePlaylists(playlists: List<Playlist>) = synchronized(lock) {
        val array = JSONArray()
        playlists.forEach { array.put(playlistToJson(it)) }
        writeAtomic(playlistsFile, array.toString())
    }

    fun loadLikedSongs(): List<Track> = synchronized(lock) {
        loadTrackList(likedSongsFile)
    }

    fun saveLikedSongs(tracks: List<Track>) = synchronized(lock) {
        saveTrackList(likedSongsFile, tracks)
    }

    fun loadFollowedArtistIds(): Set<String> = synchronized(lock) {
        val raw = readFileText(followedArtistsFile) ?: return emptySet()
        return try {
            val array = JSONArray(raw)
            buildSet {
                for (i in 0 until array.length()) {
                    val value = array.optString(i, "").trim()
                    if (value.isNotEmpty() && value != "null") add(value)
                }
            }
        } catch (_: Throwable) {
            emptySet()
        }
    }

    fun saveFollowedArtistIds(ids: Set<String>) = synchronized(lock) {
        val array = JSONArray()
        ids.filter { it.isNotBlank() }.forEach { array.put(it) }
        writeAtomic(followedArtistsFile, array.toString())
    }

    fun loadHistory(): List<ListeningEvent> = synchronized(lock) {
        val raw = readFileText(historyFile) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val trackId = obj.optClean("trackId")
                    val title = obj.optClean("title")
                    if (trackId.isEmpty() && title.isEmpty()) continue
                    add(
                        ListeningEvent(
                            trackId = trackId,
                            title = title,
                            artist = obj.optClean("artist"),
                            genre = obj.optClean("genre").ifBlank { "Music" },
                            artworkUrl = obj.optClean("artworkUrl").ifBlank { null },
                            listenedMs = obj.optLong("listenedMs", 0L).coerceAtLeast(0L),
                            timestampMs = obj.optLong("timestampMs", 0L).coerceAtLeast(0L)
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun appendHistory(event: ListeningEvent) = synchronized(lock) {
        val updated = (loadHistory() + event).takeLast(MAX_HISTORY_ENTRIES)
        val array = JSONArray()
        updated.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("trackId", item.trackId)
                    put("title", item.title)
                    put("artist", item.artist)
                    put("genre", item.genre)
                    put("artworkUrl", item.artworkUrl ?: JSONObject.NULL)
                    put("listenedMs", item.listenedMs)
                    put("timestampMs", item.timestampMs)
                }
            )
        }
        writeAtomic(historyFile, array.toString())
    }

    fun loadDownloadedTracks(): List<Track> = synchronized(lock) {
        loadTrackList(downloadedTracksFile)
    }

    fun saveDownloadedTracks(tracks: List<Track>) = synchronized(lock) {
        saveTrackList(downloadedTracksFile, tracks)
    }

    fun getImportedYouTubeTracks(): List<Track> = synchronized(lock) {
        RegionalContentFilter.filterTracks(loadTrackList(importedYouTubeTracksFile))
    }

    fun saveImportedYouTubeTracks(tracks: List<Track>) = synchronized(lock) {
        val cleaned = RegionalContentFilter.filterTracks(tracks)
            .distinctBy { it.youtubeVideoId.ifBlank { it.id } }
        saveTrackList(importedYouTubeTracksFile, cleaned)
    }

    fun loadRecentSearches(): List<String> = synchronized(lock) {
        val raw = readFileText(recentSearchesFile) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val q = array.optString(i, "").trim()
                    if (q.isNotEmpty() && q != "null" && q !in this) {
                        add(q)
                    }
                }
            }.take(MAX_RECENT_SEARCHES)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveRecentSearches(queries: List<String>) = synchronized(lock) {
        val cleaned = queries
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_RECENT_SEARCHES)
        val array = JSONArray()
        cleaned.forEach { array.put(it) }
        writeAtomic(recentSearchesFile, array.toString())
    }

    fun loadAudioSettings(): AudioSettings = synchronized(lock) {
        val raw = readFileText(audioSettingsFile) ?: return AudioSettings()
        return try {
            val obj = JSONObject(raw)
            val presetName = obj.optClean("eqPreset")
            val eqPreset = EqPreset.entries.firstOrNull { it.name == presetName } ?: EqPreset.FLAT
            val bandsJson = obj.optJSONArray("bandLevelsMb")
            val bands = if (bandsJson != null && bandsJson.length() > 0) {
                List(bandsJson.length()) { idx -> bandsJson.optInt(idx, 0) }
            } else {
                listOf(0, 0, 0, 0, 0)
            }
            AudioSettings(
                eqEnabled = obj.optBoolean("eqEnabled", true),
                eqPreset = eqPreset,
                bandLevelsMb = bands,
                bassBoostStrength = obj.optInt("bassBoostStrength", 0),
                virtualizerStrength = obj.optInt("virtualizerStrength", 0),
                crossfadeSeconds = obj.optInt("crossfadeSeconds", 3),
                playbackSpeed = obj.optDouble("playbackSpeed", 1.0).toFloat(),
                privateSession = obj.optBoolean("privateSession", false),
                streamingQualityLabel = obj.optClean("streamingQualityLabel")
                    .ifBlank { "High Quality" }
            )
        } catch (_: Throwable) {
            AudioSettings()
        }
    }

    fun saveAudioSettings(settings: AudioSettings) = synchronized(lock) {
        val obj = JSONObject().apply {
            put("eqEnabled", settings.eqEnabled)
            put("eqPreset", settings.eqPreset.name)
            put("bandLevelsMb", JSONArray().apply {
                settings.bandLevelsMb.forEach { put(it) }
            })
            put("bassBoostStrength", settings.bassBoostStrength)
            put("virtualizerStrength", settings.virtualizerStrength)
            put("crossfadeSeconds", settings.crossfadeSeconds)
            put("playbackSpeed", settings.playbackSpeed.toDouble())
            put("privateSession", settings.privateSession)
            put("streamingQualityLabel", settings.streamingQualityLabel)
        }
        writeAtomic(audioSettingsFile, obj.toString())
    }

    fun encodePlaylistCode(
        name: String,
        description: String,
        tracks: List<Track>,
        colorStart: Long,
        colorEnd: Long
    ): String {
        val payload = JSONObject().apply {
            put("v", 1)
            put("name", name)
            put("description", description)
            put("colorStart", colorStart)
            put("colorEnd", colorEnd)
            put("tracks", JSONArray().apply {
                tracks.forEach { put(trackToJson(it)) }
            })
        }.toString()
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        return "$PLAYLIST_CODE_PREFIX$encoded"
    }

    fun decodePlaylistCode(raw: String): Playlist? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val extracted = extractCodeFromDeepLink(trimmed) ?: trimmed
        val jsonText = when {
            extracted.startsWith(PLAYLIST_CODE_PREFIX, ignoreCase = true) -> {
                val base64Part = extracted.substring(PLAYLIST_CODE_PREFIX.length)
                    .replace("\\s+".toRegex(), "")
                decodeBase64Utf8(base64Part) ?: return null
            }
            extracted.startsWith("{") -> extracted
            else -> return null
        }

        return try {
            val obj = JSONObject(jsonText)
            val parsedTracks = parseTracksArray(obj.optJSONArray("tracks"))
            val parsedTrackIds = parseStringArray(obj.optJSONArray("trackIds"))
                .ifEmpty { parsedTracks.map { it.id } }
            val name = obj.optClean("name")
            if (name.isEmpty() && parsedTracks.isEmpty()) return null

            Playlist(
                id = "pl_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                name = name.ifBlank { "Shared Playlist" },
                description = obj.optClean("description"),
                trackIds = parsedTrackIds,
                tracks = parsedTracks,
                coverColorStart = obj.optLong("colorStart", 0xFF1ED760L),
                coverColorEnd = obj.optLong("colorEnd", 0xFF0A2E2AL),
                artworkUrl = obj.optClean("artworkUrl").ifBlank { null }
                    ?: parsedTracks.firstOrNull()?.artworkUrl,
                isUserCreated = true,
                isSystemMix = false,
                createdAt = System.currentTimeMillis(),
                authorName = obj.optClean("authorName").ifBlank { "Shared Mix" }
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractCodeFromDeepLink(raw: String): String? {
        if (!raw.startsWith("sonora://", ignoreCase = true)) return null
        return try {
            val uri = URI(raw)
            val query = uri.rawQuery ?: return null
            val codeParam = query.split('&')
                .mapNotNull { pair ->
                    val idx = pair.indexOf('=')
                    if (idx <= 0) null
                    else {
                        val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                        val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                        key to value
                    }
                }
                .firstOrNull { (k, _) -> k.equals("code", ignoreCase = true) }
                ?.second
                ?.trim()
            codeParam?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeBase64Utf8(encoded: String): String? {
        if (encoded.isEmpty()) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(encoded)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            try {
                val bytes = Base64.getDecoder().decode(encoded)
                String(bytes, StandardCharsets.UTF_8)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun loadTrackList(file: File): List<Track> {
        val raw = readFileText(file) ?: return emptyList()
        return try {
            parseTracksArray(JSONArray(raw))
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun saveTrackList(file: File, tracks: List<Track>) {
        val array = JSONArray()
        tracks.forEach { array.put(trackToJson(it)) }
        writeAtomic(file, array.toString())
    }

    private fun parseTracksArray(array: JSONArray?): List<Track> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                trackFromJson(obj)?.let(::add)
            }
        }
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optString(i, "").trim()
                if (item.isNotEmpty() && item != "null") add(item)
            }
        }
    }

    private fun trackToJson(track: Track): JSONObject = JSONObject().apply {
        val sanitizedStreamUrl = if (track.isPreviewOnly || isLegacyPreviewStreamUrl(track.streamUrl)) {
            ""
        } else {
            track.streamUrl
        }
        put("id", track.id)
        put("title", track.title)
        put("artist", track.artist)
        put("artistId", track.artistId)
        put("album", track.album)
        put("genre", track.genre)
        put("mood", track.mood)
        put("durationSec", track.durationSec)
        put("artworkUrl", track.artworkUrl ?: JSONObject.NULL)
        put("artworkMirrors", JSONArray().apply {
            track.artworkMirrors.forEach { put(it) }
        })
        put("streamUrl", sanitizedStreamUrl)
        put("source", track.source.name)
        put("isLive", track.isLive)
        put("isSmartPick", track.isSmartPick)
        put("releaseDate", track.releaseDate)
        put("playCount", track.playCount)
        put("localUri", track.localUri ?: JSONObject.NULL)
        put("externalUrl", track.externalUrl)
        put("isPreviewOnly", false)
        put("youtubeVideoId", track.youtubeVideoId)
        put("hasMusicVideo", track.hasMusicVideo)
    }

    private fun trackFromJson(obj: JSONObject): Track? {
        val id = obj.optClean("id")
        val title = obj.optClean("title")
        if (id.isEmpty() || title.isEmpty()) return null
        val sourceName = obj.optClean("source")
        val source = TrackSource.entries.firstOrNull { it.name == sourceName } ?: TrackSource.DEEZER
        val rawStreamUrl = obj.optClean("streamUrl")
        val wasPreviewOnly = obj.optBoolean("isPreviewOnly", false)
        val isLegacyPreview = wasPreviewOnly || isLegacyPreviewStreamUrl(rawStreamUrl)
        val sanitizedStreamUrl = if (isLegacyPreview) "" else rawStreamUrl
        val youtubeVideoId = obj.optClean("youtubeVideoId")
        val hasMusicVideo = obj.optBoolean("hasMusicVideo", youtubeVideoId.isNotEmpty())

        return Track(
            id = id,
            title = title,
            artist = obj.optClean("artist"),
            artistId = obj.optClean("artistId"),
            album = obj.optClean("album"),
            genre = obj.optClean("genre").ifBlank { "Music" },
            mood = obj.optClean("mood"),
            durationSec = obj.optInt("durationSec", 0).coerceAtLeast(0),
            artworkUrl = obj.optClean("artworkUrl").ifBlank { null },
            artworkMirrors = parseStringArray(obj.optJSONArray("artworkMirrors")),
            streamUrl = sanitizedStreamUrl,
            source = source,
            isLive = obj.optBoolean("isLive", false),
            isSmartPick = obj.optBoolean("isSmartPick", false),
            releaseDate = obj.optClean("releaseDate"),
            playCount = obj.optLong("playCount", 0L).coerceAtLeast(0L),
            localUri = obj.optClean("localUri").ifBlank { null },
            externalUrl = obj.optClean("externalUrl"),
            isPreviewOnly = false,
            youtubeVideoId = youtubeVideoId,
            hasMusicVideo = hasMusicVideo
        )
    }

    private fun isLegacyPreviewStreamUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("dzcdn.net") ||
            lower.contains("cdns-preview") ||
            lower.contains("cdnt-preview") ||
            lower.contains("audio-ssl.itunes.apple.com") ||
            lower.contains("itunes.apple.com")
    }

    private fun playlistToJson(playlist: Playlist): JSONObject = JSONObject().apply {
        put("id", playlist.id)
        put("name", playlist.name)
        put("description", playlist.description)
        put("trackIds", JSONArray().apply {
            playlist.trackIds.forEach { put(it) }
        })
        put("tracks", JSONArray().apply {
            playlist.tracks.forEach { put(trackToJson(it)) }
        })
        put("coverColorStart", playlist.coverColorStart)
        put("coverColorEnd", playlist.coverColorEnd)
        put("artworkUrl", playlist.artworkUrl ?: JSONObject.NULL)
        put("isUserCreated", playlist.isUserCreated)
        put("isSystemMix", playlist.isSystemMix)
        put("createdAt", playlist.createdAt)
        put("authorName", playlist.authorName)
    }

    private fun playlistFromJson(obj: JSONObject): Playlist? {
        val id = obj.optClean("id")
        val name = obj.optClean("name")
        if (id.isEmpty() || name.isEmpty()) return null
        val tracks = parseTracksArray(obj.optJSONArray("tracks"))
        val trackIds = parseStringArray(obj.optJSONArray("trackIds"))
            .ifEmpty { tracks.map { it.id } }

        return Playlist(
            id = id,
            name = name,
            description = obj.optClean("description"),
            trackIds = trackIds,
            tracks = tracks,
            coverColorStart = obj.optLong("coverColorStart", 0xFF1ED760L),
            coverColorEnd = obj.optLong("coverColorEnd", 0xFF0A2E2AL),
            artworkUrl = obj.optClean("artworkUrl").ifBlank { null },
            isUserCreated = obj.optBoolean("isUserCreated", true),
            isSystemMix = obj.optBoolean("isSystemMix", false),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            authorName = obj.optClean("authorName").ifBlank { "You" }
        )
    }

    private fun readFileText(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return try {
            file.readText(StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeAtomic(file: File, content: String) {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        val tmpFile = File(rootDir, "${file.name}.tmp")
        try {
            tmpFile.writeText(content, StandardCharsets.UTF_8)
            try {
                Files.move(
                    tmpFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tmpFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (_: Throwable) {
            tmpFile.delete()
        }
    }

    private fun JSONObject.optClean(key: String): String {
        if (isNull(key)) return ""
        val value = optString(key, "")
        return if (value == "null") "" else value.trim()
    }

    companion object {
        private const val MAX_HISTORY_ENTRIES = 1500
        private const val MAX_RECENT_SEARCHES = 12
        private const val PLAYLIST_CODE_PREFIX = "SONORA-PL-V1:"
    }
}
