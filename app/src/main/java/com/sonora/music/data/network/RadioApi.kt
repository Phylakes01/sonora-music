package com.sonora.music.data.network

import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray

class RadioApi(private val client: OkHttpClient = HttpClientProvider.client) {

    private val hosts = listOf(PRIMARY_HOST, FALLBACK_HOST)

    suspend fun getTopStations(limit: Int = 25): List<Track> =
        withContext(Dispatchers.IO) {
            val safeLimit = limit.coerceIn(1, 100)
            fetchStations("/json/stations/topclick/$safeLimit?hidebroken=true")
        }

    suspend fun searchStations(query: String, limit: Int = 25): List<Track> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val safeLimit = limit.coerceIn(1, 100)
            fetchStations(
                "/json/stations/search?name=${encodeUrlParam(trimmed)}" +
                    "&limit=$safeLimit&hidebroken=true&order=clickcount&reverse=true"
            )
        }

    private suspend fun fetchStations(pathAndQuery: String): List<Track> {
        for (host in hosts) {
            val body = client.getText("$host$pathAndQuery") ?: continue
            try {
                val array = JSONArray(body)
                return parseStations(array)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Try fallback Radio Browser mirror
            }
        }
        return emptyList()
    }

    private fun parseStations(array: JSONArray): List<Track> = buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optCleanString("name")
            val streamUrl = obj.optCleanString("url_resolved")
                .ifBlank { obj.optCleanString("url") }
            if (name.isEmpty() || streamUrl.isEmpty()) continue

            val uuid = obj.optCleanString("stationuuid")
                .ifBlank { obj.optCleanString("changeuuid") }
                .ifBlank { streamUrl.hashCode().toUInt().toString() }
            val country = obj.optCleanString("country")
            val language = obj.optCleanString("language")
            val tagsRaw = obj.optCleanString("tags")
            val primaryTag = tagsRaw
                .split(',')
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.replaceFirstChar { it.uppercase() }
                .orEmpty()
            val bitrate = obj.optInt("bitrate", 0)
            val favicon = obj.optCleanString("favicon")
                .takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }

            val artistLabel = listOfNotNull(
                country.takeIf { it.isNotEmpty() },
                language.takeIf { it.isNotEmpty() }
            ).joinToString(" • ").ifBlank { "Live Radio" }

            add(
                Track(
                    id = "radio_$uuid",
                    title = name,
                    artist = artistLabel,
                    album = if (bitrate > 0) "${bitrate} kbps Live Stream" else "Live Radio",
                    genre = primaryTag.ifBlank { "Live Radio" },
                    durationSec = 0,
                    artworkUrl = favicon,
                    streamUrl = streamUrl,
                    source = TrackSource.RADIO,
                    isLive = true,
                    playCount = obj.optLong("clickcount", 0L).coerceAtLeast(0L)
                )
            )
        }
    }

    companion object {
        private const val PRIMARY_HOST = "https://de1.api.radio-browser.info"
        private const val FALLBACK_HOST = "https://all.api.radio-browser.info"
    }
}
