package com.sonora.music.data.network

import com.sonora.music.model.Audiobook
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

class AudiobookApi(private val client: OkHttpClient = HttpClientProvider.client) {

    suspend fun searchAudiobooks(query: String = "", limit: Int = 20): List<Audiobook> =
        withContext(Dispatchers.IO) {
            val safeLimit = limit.coerceIn(1, 50)
            val trimmed = query.trim()
            val qExpr = if (trimmed.isEmpty()) {
                "collection:librivoxaudio"
            } else {
                "collection:librivoxaudio AND ($trimmed)"
            }
            val url = "$ARCHIVE_BASE/advancedsearch.php" +
                "?q=${encodeUrlParam(qExpr)}" +
                "&fl[]=identifier,title,creator,description,downloads" +
                "&sort[]=downloads+desc" +
                "&rows=$safeLimit" +
                "&output=json"

            val body = client.getText(url) ?: return@withContext emptyList()
            try {
                val docs = JSONObject(body)
                    .optJSONObject("response")
                    ?.optJSONArray("docs")
                    ?: return@withContext emptyList()

                buildList {
                    for (i in 0 until docs.length()) {
                        val doc = docs.optJSONObject(i) ?: continue
                        val id = extractField(doc, "identifier")
                        val title = extractField(doc, "title")
                        if (id.isEmpty() || title.isEmpty()) continue

                        val creator = extractField(doc, "creator").ifBlank { "LibriVox" }
                        val description = stripHtmlTags(extractField(doc, "description"))
                        val downloads = doc.optLong("downloads", 0L).coerceAtLeast(0L)

                        add(
                            Audiobook(
                                id = id,
                                title = title,
                                author = creator,
                                artworkUrl = "$ARCHIVE_BASE/services/img/${encodePathSegment(id)}",
                                description = description,
                                downloads = downloads
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                emptyList()
            }
        }

    suspend fun getChapters(book: Audiobook): List<Track> =
        withContext(Dispatchers.IO) {
            val bookId = book.id.trim()
            if (bookId.isEmpty()) return@withContext emptyList()

            val url = "$ARCHIVE_BASE/metadata/${encodePathSegment(bookId)}/files"
            val body = client.getText(url) ?: return@withContext emptyList()

            try {
                val files = JSONObject(body).optJSONArray("result") ?: return@withContext emptyList()
                val candidates = mutableListOf<ChapterFile>()

                for (i in 0 until files.length()) {
                    val file = files.optJSONObject(i) ?: continue
                    val name = file.optCleanString("name")
                    if (!name.endsWith(".mp3", ignoreCase = true)) continue

                    val format = file.optCleanString("format")
                    val title = file.optCleanString("title")
                    val trackRaw = file.optCleanString("track")
                    val lengthRaw = file.optCleanString("length")
                    val source = file.optCleanString("source")

                    val is64kb = name.contains("_64kb", ignoreCase = true) ||
                        format.contains("64Kbps", ignoreCase = true)
                    val is128kb = name.contains("_128kb", ignoreCase = true) ||
                        format.contains("128Kbps", ignoreCase = true)

                    val priority = when {
                        is128kb -> 3
                        !is64kb && source.equals("original", ignoreCase = true) -> 3
                        !is64kb -> 2
                        else -> 1
                    }

                    val stem = normalizeStem(name)
                    candidates.add(
                        ChapterFile(
                            name = name,
                            stem = stem,
                            title = title,
                            trackNumber = parseTrackOrder(trackRaw, name),
                            durationSec = parseFlexibleDurationToSeconds(lengthRaw),
                            priority = priority
                        )
                    )
                }

                if (candidates.isEmpty()) return@withContext emptyList()

                val hasNon64kb = candidates.any { it.priority >= 2 }
                val filtered = candidates
                    .filter { !hasNon64kb || it.priority >= 2 }
                    .groupBy { it.stem }
                    .values
                    .mapNotNull { group -> group.maxByOrNull { it.priority } }
                    .sortedWith(
                        compareBy<ChapterFile> { it.trackNumber ?: Int.MAX_VALUE }
                            .thenBy { it.name.lowercase() }
                    )

                filtered.mapIndexed { index, chapter ->
                    val encodedFile = chapter.name
                        .split('/')
                        .joinToString("/") { segment -> encodePathSegment(segment) }
                    val cleanTitle = chapter.title.ifBlank { formatFilenameAsTitle(chapter.name, index + 1) }

                    Track(
                        id = "book_${bookId}_${chapter.name.hashCode().toUInt()}",
                        title = cleanTitle,
                        artist = book.author.ifBlank { "LibriVox" },
                        artistId = bookId,
                        album = book.title,
                        genre = "Audiobook",
                        durationSec = chapter.durationSec,
                        artworkUrl = book.artworkUrl ?: "$ARCHIVE_BASE/services/img/${encodePathSegment(bookId)}",
                        streamUrl = "$ARCHIVE_BASE/download/${encodePathSegment(bookId)}/$encodedFile",
                        source = TrackSource.AUDIOBOOK,
                        isLive = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                emptyList()
            }
        }

    private fun extractField(doc: JSONObject, key: String): String {
        if (doc.isNull(key)) return ""
        return when (val value = doc.opt(key)) {
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    val str = value.optString(i, "").trim()
                    if (str.isNotEmpty() && str != "null") add(str)
                }
            }.joinToString(", ")
            is String -> if (value == "null") "" else value.trim()
            else -> value?.toString()?.trim().orEmpty().takeUnless { it == "null" }.orEmpty()
        }
    }

    private fun normalizeStem(fileName: String): String {
        return fileName
            .substringAfterLast('/')
            .replace(STEM_SUFFIX_REGEX, "")
            .lowercase()
    }

    private fun parseTrackOrder(trackRaw: String, fileName: String): Int? {
        if (trackRaw.isNotBlank()) {
            val lead = trackRaw.substringBefore('/').trim().toIntOrNull()
            if (lead != null) return lead
        }
        val base = fileName.substringAfterLast('/')
            .replace(STEM_SUFFIX_REGEX, "")
        return FIRST_NUMBER_REGEX.find(base)?.value?.toIntOrNull()
    }

    private fun formatFilenameAsTitle(fileName: String, fallbackIndex: Int): String {
        val base = fileName
            .substringAfterLast('/')
            .replace(STEM_SUFFIX_REGEX, "")
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return base.ifBlank { "Chapter $fallbackIndex" }
    }

    private data class ChapterFile(
        val name: String,
        val stem: String,
        val title: String,
        val trackNumber: Int?,
        val durationSec: Int,
        val priority: Int
    )

    companion object {
        private const val ARCHIVE_BASE = "https://archive.org"
        private val STEM_SUFFIX_REGEX = Regex("""(_128kb|_64kb|_vbr)?\.mp3$""", RegexOption.IGNORE_CASE)
        private val FIRST_NUMBER_REGEX = Regex("""\d+""")
    }
}
