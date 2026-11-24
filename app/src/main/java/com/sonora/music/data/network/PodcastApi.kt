package com.sonora.music.data.network

import com.sonora.music.model.PodcastShow
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class PodcastApi(private val client: OkHttpClient = HttpClientProvider.client) {

    suspend fun searchPodcasts(query: String, limit: Int = 20): List<PodcastShow> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val safeLimit = limit.coerceIn(1, 50)
            val url = "$ITUNES_SEARCH_URL?media=podcast&term=${encodeUrlParam(trimmed)}&limit=$safeLimit"
            val body = client.getText(url) ?: return@withContext emptyList()

            try {
                val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val feedUrl = item.optCleanString("feedUrl")
                        val title = item.optCleanString("collectionName")
                            .ifBlank { item.optCleanString("trackName") }
                        if (feedUrl.isEmpty() || title.isEmpty()) continue

                        val id = item.optLong("collectionId", item.optLong("trackId", 0L))
                            .takeIf { it > 0L }?.toString()
                            ?: "pod_${feedUrl.hashCode().toUInt()}"
                        val artworkUrl = item.optCleanString("artworkUrl600").ifBlank { null }
                            ?: item.optCleanString("artworkUrl100").ifBlank { null }
                            ?: item.optCleanString("artworkUrl60").ifBlank { null }
                            ?: item.optCleanString("artworkUrl30").ifBlank { null }

                        add(
                            PodcastShow(
                                id = id,
                                title = title,
                                author = item.optCleanString("artistName").ifBlank { "Podcast Creator" },
                                artworkUrl = artworkUrl,
                                feedUrl = feedUrl,
                                genre = item.optCleanString("primaryGenreName").ifBlank { "Podcast" }
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

    suspend fun getEpisodes(show: PodcastShow, limit: Int = 40): List<Track> =
        withContext(Dispatchers.IO) {
            val feedUrl = show.feedUrl.trim()
            if (feedUrl.isEmpty()) return@withContext emptyList()
            val safeLimit = limit.coerceIn(1, 200)
            val xml = client.getText(
                url = feedUrl,
                acceptHeader = "application/rss+xml, application/xml, text/xml, */*"
            ) ?: return@withContext emptyList()

            try {
                parseRssFeed(xml, show, safeLimit)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                emptyList()
            }
        }

    fun parseDurationToSeconds(raw: String?): Int = parseFlexibleDurationToSeconds(raw)

    private fun parseRssFeed(xml: String, show: PodcastShow, limit: Int): List<Track> {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val episodes = mutableListOf<Track>()
        var channelArtwork: String? = show.artworkUrl
        var inItem = false
        var inChannelImage = false

        var itemTitle = ""
        var itemAuthor = ""
        var itemGuid = ""
        var itemPubDate = ""
        var itemDurationRaw = ""
        var itemEnclosureUrl = ""
        var itemArtwork: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name?.lowercase().orEmpty()
                    if (!inItem) {
                        when {
                            tag == "item" -> {
                                inItem = true
                                itemTitle = ""
                                itemAuthor = ""
                                itemGuid = ""
                                itemPubDate = ""
                                itemDurationRaw = ""
                                itemEnclosureUrl = ""
                                itemArtwork = null
                            }
                            tag == "image" -> inChannelImage = true
                            tag == "url" && inChannelImage -> {
                                val url = readTagText(parser)
                                if (url.isNotBlank() && channelArtwork.isNullOrBlank()) {
                                    channelArtwork = url
                                }
                            }
                            tag == "itunes:image" || tag.endsWith(":image") -> {
                                val href = parser.getAttributeValue(null, "href")?.trim()
                                if (!href.isNullOrBlank() && channelArtwork.isNullOrBlank()) {
                                    channelArtwork = href
                                }
                            }
                        }
                    } else {
                        when {
                            tag == "title" -> itemTitle = readTagText(parser)
                            tag == "guid" -> itemGuid = readTagText(parser)
                            tag == "pubdate" -> itemPubDate = readTagText(parser)
                            tag == "itunes:duration" || tag == "duration" || tag.endsWith(":duration") -> {
                                itemDurationRaw = readTagText(parser)
                            }
                            tag == "itunes:author" || tag == "author" || tag == "dc:creator" -> {
                                if (itemAuthor.isBlank()) {
                                    itemAuthor = readTagText(parser)
                                }
                            }
                            tag == "itunes:image" || tag.endsWith(":image") -> {
                                val href = parser.getAttributeValue(null, "href")?.trim()
                                if (!href.isNullOrBlank()) itemArtwork = href
                            }
                            tag == "media:thumbnail" -> {
                                val url = parser.getAttributeValue(null, "url")?.trim()
                                if (!url.isNullOrBlank() && itemArtwork == null) itemArtwork = url
                            }
                            tag == "enclosure" -> {
                                val url = parser.getAttributeValue(null, "url")?.trim().orEmpty()
                                val type = parser.getAttributeValue(null, "type")?.trim().orEmpty()
                                if (url.isNotEmpty() && !type.startsWith("image/", ignoreCase = true) && itemEnclosureUrl.isEmpty()) {
                                    itemEnclosureUrl = url
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name?.lowercase().orEmpty()
                    if (!inItem && tag == "image") {
                        inChannelImage = false
                    } else if (inItem && tag == "item") {
                        inItem = false
                        if (itemEnclosureUrl.isNotBlank()) {
                            val stableKey = itemGuid.ifBlank { itemEnclosureUrl }.hashCode().toUInt()
                            episodes.add(
                                Track(
                                    id = "pod_${show.id}_$stableKey",
                                    title = itemTitle.ifBlank { "Episode ${episodes.size + 1}" },
                                    artist = itemAuthor.ifBlank { show.author },
                                    artistId = show.id,
                                    album = show.title,
                                    genre = show.genre.ifBlank { "Podcast" },
                                    durationSec = parseDurationToSeconds(itemDurationRaw),
                                    artworkUrl = itemArtwork ?: show.artworkUrl ?: channelArtwork,
                                    streamUrl = itemEnclosureUrl,
                                    source = TrackSource.PODCAST,
                                    isLive = false,
                                    releaseDate = itemPubDate
                                )
                            )
                            if (episodes.size >= limit) break
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return episodes
    }

    private fun readTagText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text.orEmpty())
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return stripHtmlTags(sb.toString())
    }

    companion object {
        private const val ITUNES_SEARCH_URL = "https://itunes.apple.com/search"
    }
}
