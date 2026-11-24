package com.sonora.music.data.network

import com.sonora.music.model.Artist
import com.sonora.music.model.Playlist
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

class AudiusApi(private val client: OkHttpClient = HttpClientProvider.client) {

    private val hosts = listOf(PRIMARY_HOST, FALLBACK_HOST)

    suspend fun getTrendingTracks(genre: String? = null, limit: Int = 25): List<Track> =
        withContext(Dispatchers.IO) {
            val safeLimit = limit.coerceIn(1, 100)
            val cleanGenre = genre?.trim()?.takeIf {
                it.isNotEmpty() && !it.equals("All", ignoreCase = true)
            }
            val genreParam = cleanGenre?.let { "&genre=${encodeUrlParam(it)}" }.orEmpty()
            val data = fetchDataArray("/v1/tracks/trending?app_name=$APP_NAME&limit=$safeLimit$genreParam")
                ?: return@withContext emptyList()
            parseTrackArray(data)
        }

    suspend fun getUndergroundTrending(limit: Int = 20): List<Track> =
        withContext(Dispatchers.IO) {
            val safeLimit = limit.coerceIn(1, 100)
            val data = fetchDataArray("/v1/tracks/trending/underground?app_name=$APP_NAME&limit=$safeLimit")
                ?: return@withContext emptyList()
            parseTrackArray(data)
        }

    suspend fun searchTracks(query: String, limit: Int = 30): List<Track> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val safeLimit = limit.coerceIn(1, 100)
            val data = fetchDataArray(
                "/v1/tracks/search?query=${encodeUrlParam(trimmed)}&app_name=$APP_NAME&limit=$safeLimit"
            ) ?: return@withContext emptyList()
            parseTrackArray(data)
        }

    suspend fun getTrendingPlaylists(limit: Int = 15): List<Playlist> =
        withContext(Dispatchers.IO) {
            val safeLimit = limit.coerceIn(1, 100)
            val data = fetchDataArray("/v1/playlists/trending?app_name=$APP_NAME&limit=$safeLimit")
                ?: return@withContext emptyList()
            parsePlaylistArray(data)
        }

    suspend fun searchPlaylists(query: String, limit: Int = 15): List<Playlist> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val safeLimit = limit.coerceIn(1, 100)
            val data = fetchDataArray(
                "/v1/playlists/search?query=${encodeUrlParam(trimmed)}&app_name=$APP_NAME&limit=$safeLimit"
            ) ?: return@withContext emptyList()
            parsePlaylistArray(data)
        }

    suspend fun getPlaylistTracks(playlistId: String): List<Track> =
        withContext(Dispatchers.IO) {
            val trimmed = playlistId.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val data = fetchDataArray(
                "/v1/playlists/${encodePathSegment(trimmed)}/tracks?app_name=$APP_NAME"
            ) ?: return@withContext emptyList()
            parseTrackArray(data)
        }

    suspend fun searchArtists(query: String, limit: Int = 15): List<Artist> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val safeLimit = limit.coerceIn(1, 100)
            val data = fetchDataArray(
                "/v1/users/search?query=${encodeUrlParam(trimmed)}&app_name=$APP_NAME&limit=$safeLimit"
            ) ?: return@withContext emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    parseArtist(item)?.let(::add)
                }
            }
        }

    suspend fun getArtistTracks(artistId: String, limit: Int = 30): List<Track> =
        withContext(Dispatchers.IO) {
            val trimmed = artistId.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val safeLimit = limit.coerceIn(1, 100)
            val data = fetchDataArray(
                "/v1/users/${encodePathSegment(trimmed)}/tracks?app_name=$APP_NAME&limit=$safeLimit"
            ) ?: return@withContext emptyList()
            parseTrackArray(data)
        }

    private suspend fun fetchDataArray(pathAndQuery: String): JSONArray? {
        for (host in hosts) {
            val body = client.getText("$host$pathAndQuery") ?: continue
            try {
                val root = JSONObject(body)
                val data = root.optJSONArray("data")
                if (data != null) return data
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Try fallback host when JSON parsing fails
            }
        }
        return null
    }

    private fun parseTrackArray(array: JSONArray): List<Track> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            parseTrack(item)?.let(::add)
        }
    }

    private fun parsePlaylistArray(array: JSONArray): List<Playlist> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            parsePlaylist(item)?.let(::add)
        }
    }

    private fun parseTrack(obj: JSONObject): Track? {
        val id = obj.optCleanString("id")
        val title = obj.optCleanString("title")
        if (id.isEmpty() || title.isEmpty()) return null

        val userObj = obj.optJSONObject("user")
        val artistName = userObj?.optCleanString("name")
            ?.ifBlank { userObj.optCleanString("handle") }
            .orEmpty()
        val artistId = userObj?.optCleanString("id").orEmpty()

        val (artworkUrl, mirrors) = extractArtwork(obj.optJSONObject("artwork"))

        return Track(
            id = id,
            title = title,
            artist = artistName,
            artistId = artistId,
            genre = obj.optCleanString("genre").ifBlank { "Music" },
            mood = obj.optCleanString("mood"),
            durationSec = obj.optInt("duration", 0).coerceAtLeast(0),
            artworkUrl = artworkUrl,
            artworkMirrors = mirrors,
            streamUrl = "$PRIMARY_HOST/v1/tracks/${encodePathSegment(id)}/stream?app_name=$APP_NAME",
            source = TrackSource.AUDIUS,
            isLive = false,
            isSmartPick = false,
            releaseDate = obj.optCleanString("release_date"),
            playCount = obj.optLong("play_count", 0L).coerceAtLeast(0L)
        )
    }

    private fun parsePlaylist(obj: JSONObject): Playlist? {
        val id = obj.optCleanString("id")
        val name = obj.optCleanString("playlist_name").ifBlank { obj.optCleanString("name") }
        if (id.isEmpty() || name.isEmpty()) return null

        val userObj = obj.optJSONObject("user")
        val authorName = userObj?.optCleanString("name")
            ?.ifBlank { userObj.optCleanString("handle") }
            .orEmpty()
            .ifBlank { "Audius Curator" }

        val (artworkUrl, _) = extractArtwork(obj.optJSONObject("artwork"))
        val tracks = obj.optJSONArray("tracks")?.let(::parseTrackArray).orEmpty()
        val trackIds = if (tracks.isNotEmpty()) {
            tracks.map { it.id }
        } else {
            val contents = obj.optJSONArray("playlist_contents")
            buildList {
                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        val entry = contents.optJSONObject(i) ?: continue
                        val trackId = entry.optCleanString("track_id")
                            .ifBlank { entry.optCleanString("id") }
                        if (trackId.isNotEmpty()) add(trackId)
                    }
                }
            }
        }

        val (colorStart, colorEnd) = coverPaletteForKey(id)
        return Playlist(
            id = id,
            name = name,
            description = obj.optCleanString("description"),
            trackIds = trackIds,
            tracks = tracks,
            coverColorStart = colorStart,
            coverColorEnd = colorEnd,
            artworkUrl = artworkUrl ?: tracks.firstOrNull()?.artworkUrl,
            isUserCreated = false,
            isSystemMix = false,
            authorName = authorName
        )
    }

    private fun parseArtist(obj: JSONObject): Artist? {
        val id = obj.optCleanString("id")
        val name = obj.optCleanString("name").ifBlank { obj.optCleanString("handle") }
        if (id.isEmpty() || name.isEmpty()) return null

        val (avatarUrl, _) = extractArtwork(obj.optJSONObject("profile_picture"))
        return Artist(
            id = id,
            name = name,
            handle = obj.optCleanString("handle"),
            avatarUrl = avatarUrl,
            followerCount = obj.optLong("follower_count", 0L).coerceAtLeast(0L),
            trackCount = obj.optInt("track_count", 0).coerceAtLeast(0),
            bio = obj.optCleanString("bio")
        )
    }

    private fun extractArtwork(artworkObj: JSONObject?): Pair<String?, List<String>> {
        if (artworkObj == null) return null to emptyList()
        val primary = artworkObj.optCleanString("480x480").ifBlank { null }
            ?: artworkObj.optCleanString("1000x1000").ifBlank { null }
            ?: artworkObj.optCleanString("150x150").ifBlank { null }

        val mirrorsArray = artworkObj.optJSONArray("mirrors")
        val mirrors = buildList {
            if (mirrorsArray != null) {
                for (i in 0 until mirrorsArray.length()) {
                    val value = mirrorsArray.optString(i, "").trim()
                    if (value.isNotEmpty() && value != "null") {
                        add(value)
                    }
                }
            }
        }
        return primary to mirrors
    }

    companion object {
        private const val PRIMARY_HOST = "https://api.audius.co"
        private const val FALLBACK_HOST = "https://discoveryprovider.audius.co"
        private const val APP_NAME = "Sonora"
    }
}
