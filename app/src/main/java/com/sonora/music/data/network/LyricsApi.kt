package com.sonora.music.data.network

import com.sonora.music.model.LyricLine
import com.sonora.music.model.LyricsResult
import com.sonora.music.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

class LyricsApi(private val client: OkHttpClient = HttpClientProvider.client) {

    suspend fun fetchLyrics(track: Track): LyricsResult = withContext(Dispatchers.IO) {
        val trackKey = track.id.ifBlank { "${track.title}|${track.artist}" }
        val rawTitle = track.title.trim()
        val artist = track.artist.trim()
        if (rawTitle.isEmpty()) return@withContext LyricsResult(trackKey = trackKey)

        try {
            if (artist.isNotEmpty()) {
                val getUrl = "$BASE_URL/api/get?track_name=${encodeUrlParam(rawTitle)}&artist_name=${encodeUrlParam(artist)}"
                val body = client.getText(getUrl)
                if (!body.isNullOrBlank()) {
                    parseLyricsObject(JSONObject(body), trackKey)?.let { return@withContext it }
                }
            }

            val cleanedTitle = cleanTitle(rawTitle).ifBlank { rawTitle }
            val primaryQuery = listOf(cleanedTitle, artist).filter { it.isNotBlank() }.joinToString(" ")
            searchLyrics(primaryQuery, trackKey, track.durationSec)?.let { return@withContext it }

            if (artist.isNotBlank() && cleanedTitle != rawTitle) {
                searchLyrics(cleanedTitle, trackKey, track.durationSec)?.let { return@withContext it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Return empty LyricsResult on network/parse failures
        }

        LyricsResult(trackKey = trackKey)
    }

    fun parseLrc(lrcText: String): List<LyricLine> {
        if (lrcText.isBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()

        for (rawLine in lrcText.lineSequence()) {
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty()) continue

            val matches = LRC_TIMESTAMP_REGEX.findAll(trimmedLine).toList()
            if (matches.isEmpty()) continue

            val text = LRC_TIMESTAMP_REGEX.replace(trimmedLine, "").trim()
            if (text.isEmpty()) continue

            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                val fracRaw = match.groupValues[3]
                val fracMs = when (fracRaw.length) {
                    0 -> 0L
                    1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                    else -> fracRaw.take(3).toLongOrNull() ?: 0L
                }
                val timeMs = minutes * 60_000L + seconds * 1_000L + fracMs
                lines.add(LyricLine(timeMs = timeMs, text = text))
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    private suspend fun searchLyrics(
        query: String,
        trackKey: String,
        targetDurationSec: Int
    ): LyricsResult? {
        if (query.isBlank()) return null
        val searchUrl = "$BASE_URL/api/search?q=${encodeUrlParam(query)}"
        val body = client.getText(searchUrl) ?: return null
        val array = JSONArray(body)
        if (array.length() == 0) return null

        val candidates = buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val result = parseLyricsObject(obj, trackKey) ?: continue
                val duration = obj.optInt("duration", 0)
                add(result to duration)
            }
        }
        if (candidates.isEmpty()) return null

        return candidates.maxByOrNull { (res, dur) ->
            var score = 0
            if (res.syncedLines.isNotEmpty()) score += 100
            if (!res.plainLyrics.isNullOrBlank()) score += 50
            if (res.isInstrumental) score += 10
            if (targetDurationSec > 0 && dur > 0) {
                val diff = kotlin.math.abs(targetDurationSec - dur)
                score += (20 - diff).coerceAtLeast(0)
            }
            score
        }?.first
    }

    private fun parseLyricsObject(obj: JSONObject, trackKey: String): LyricsResult? {
        val instrumental = obj.optBoolean("instrumental", false)
        val syncedRaw = obj.optCleanString("syncedLyrics")
        val plainRaw = obj.optCleanString("plainLyrics").ifBlank { null }
        val syncedLines = if (syncedRaw.isNotEmpty()) parseLrc(syncedRaw) else emptyList()

        if (!instrumental && syncedLines.isEmpty() && plainRaw == null) {
            return null
        }

        return LyricsResult(
            trackKey = trackKey,
            syncedLines = syncedLines,
            plainLyrics = plainRaw,
            isInstrumental = instrumental
        )
    }

    internal fun cleanTitle(title: String): String {
        return title
            .replace(BRACKET_NOISE_REGEX, " ")
            .replace(FEAT_SUFFIX_REGEX, "")
            .replace(DASH_NOISE_REGEX, "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .trim('-', '–', '—')
            .trim()
    }

    companion object {
        private const val BASE_URL = "https://lrclib.net"
        private val LRC_TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        private val BRACKET_NOISE_REGEX = Regex(
            """[\(\[][^)\]]*(?:remix|cover|official|feat\.?|ft\.?|prod\.?|audio|video|lyric|visualizer|edit|version|remaster|live|reverb|slowed|sped|acoustic|deluxe|explicit|clean)[^)\]]*[\)\]]""",
            RegexOption.IGNORE_CASE
        )
        private val FEAT_SUFFIX_REGEX = Regex(
            """\s*(?:feat\.?|ft\.?)\s+.*$""",
            RegexOption.IGNORE_CASE
        )
        private val DASH_NOISE_REGEX = Regex(
            """\s*[-–|]\s*[^-–|]*(?:remix|cover|official|audio|video|lyric|visualizer|edit|remaster|live|acoustic|reverb)[^-–|]*$""",
            RegexOption.IGNORE_CASE
        )
    }
}
