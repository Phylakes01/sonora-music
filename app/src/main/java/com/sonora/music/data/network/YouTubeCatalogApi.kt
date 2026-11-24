package com.sonora.music.data.network

import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

data class YouTubePack(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Track>
)

data class YouTubeImportResult(
    val tracks: List<Track>,
    val sourceLabel: String,
    val excludedCount: Int = 0
)

/**
 * YouTube Music Video catalog & importer powered strictly by sanctioned public
 * YouTube oEmbed (`https://www.youtube.com/oembed`) and official Atom RSS feeds
 * (`https://www.youtube.com/feeds/videos.xml`). Never extracts raw video stream URLs.
 */
class YouTubeCatalogApi(private val client: OkHttpClient = HttpClientProvider.client) {

    private val oEmbedCache = ConcurrentHashMap<String, Track>()
    private val feedCache = ConcurrentHashMap<String, CacheEntry<List<Track>>>()
    private val rssIndexCache = ConcurrentHashMap<String, Track>()
    @Volatile
    private var lastRssIndexWarmMs: Long = 0L

    suspend fun warmRssSearchIndex(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && rssIndexCache.isNotEmpty() && now - lastRssIndexWarmMs < INDEX_CACHE_TTL_MS) {
            return@withContext
        }
        VERIFIED_CHANNELS.chunked(8).forEach { batch ->
            val fetched = withTimeoutOrNull(5_000L) {
                coroutineScope {
                    batch.map { ch ->
                        async {
                            withTimeoutOrNull(4_500L) {
                                fetchChannelRssVideos(ch.channelId, ch.regionHint)
                            }.orEmpty()
                        }
                    }.awaitAll().flatten()
                }
            }.orEmpty()
            fetched.forEach { track ->
                val key = track.youtubeVideoId.ifBlank { track.id }
                rssIndexCache[key] = track
            }
        }
        lastRssIndexWarmMs = System.currentTimeMillis()
    }

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (RAW_VIDEO_ID_REGEX.matches(trimmed)) {
            return trimmed
        }

        val candidates = URL_EXTRACT_REGEX.findAll(trimmed)
            .map { it.value.trimEnd('.', ',', ')', ']', ';', '"', '\'') }
            .toList()
            .ifEmpty { listOf(trimmed) }

        for (candidate in candidates) {
            if (!candidate.contains("youtu", ignoreCase = true)) continue
            if (PLAYLIST_PATH_REGEX.containsMatchIn(candidate) && !WATCH_V_REGEX.containsMatchIn(candidate)) {
                continue
            }
            WATCH_V_REGEX.find(candidate)?.groupValues?.getOrNull(1)?.let { return it }
            SHORT_OR_PATH_VIDEO_REGEX.find(candidate)?.groupValues?.getOrNull(1)?.let { return it }
            YOUTU_BE_REGEX.find(candidate)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (RAW_PLAYLIST_ID_REGEX.matches(trimmed)) {
            return trimmed
        }

        val candidates = URL_EXTRACT_REGEX.findAll(trimmed)
            .map { it.value.trimEnd('.', ',', ')', ']', ';', '"', '\'') }
            .toList()
            .ifEmpty { listOf(trimmed) }

        for (candidate in candidates) {
            if (!candidate.contains("youtu", ignoreCase = true) && !candidate.contains("list=", ignoreCase = true)) {
                continue
            }
            LIST_PARAM_REGEX.find(candidate)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    fun extractChannelId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (RAW_CHANNEL_ID_REGEX.matches(trimmed)) {
            return trimmed
        }

        val lower = trimmed.lowercase(Locale.ROOT)
        HANDLE_ALIASES[lower]?.let { return it }

        val candidates = URL_EXTRACT_REGEX.findAll(trimmed)
            .map { it.value.trimEnd('.', ',', ')', ']', ';', '"', '\'') }
            .toList()
            .ifEmpty { listOf(trimmed) }

        for (candidate in candidates) {
            CHANNEL_PATH_REGEX.find(candidate)?.groupValues?.getOrNull(1)?.let { return it }
            HANDLE_IN_URL_REGEX.find(candidate)?.groupValues?.getOrNull(1)?.let { handle ->
                HANDLE_ALIASES["@${handle.lowercase(Locale.ROOT)}"]?.let { return it }
            }
        }
        return null
    }

    fun getCuratedMusicVideos(): List<Track> =
        RegionalContentFilter.filterTracks((PH_MV_TRACKS + GLOBAL_MV_TRACKS).map { it.toTrack() })

    fun getCuratedPacks(): List<YouTubePack> = listOf(
        YouTubePack(
            id = PACK_PH_VIRAL,
            title = "PH & Wish 107.5 Viral MVs",
            subtitle = "Esremborak, Mariah Deborah, Cup of Joe, BINI, Dionela",
            tracks = getPackTracks(PACK_PH_VIRAL)
        ),
        YouTubePack(
            id = PACK_WISH_1075,
            title = "Wish 107.5 Live Sessions",
            subtitle = "Latest live OPM Bus performances & acoustic sessions",
            tracks = getPackTracks(PACK_WISH_1075)
        ),
        YouTubePack(
            id = PACK_GLOBAL_MVS,
            title = "Global Pop & R&B Music Videos",
            subtitle = "The Weeknd, ROSÉ & Bruno Mars, Billie Eilish, Lady Gaga",
            tracks = getPackTracks(PACK_GLOBAL_MVS)
        )
    )

    fun getPackTracks(packId: String): List<Track> {
        val raw = when (packId.trim().lowercase(Locale.ROOT)) {
            PACK_PH_VIRAL -> PH_MV_TRACKS.map { it.toTrack() }
            PACK_WISH_1075 -> PH_MV_TRACKS
                .filter { it.videoId in WISH_1075_PACK_VIDEO_IDS }
                .map { it.toTrack() }
            PACK_GLOBAL_MVS -> GLOBAL_MV_TRACKS.map { it.toTrack() }
            else -> emptyList()
        }
        return RegionalContentFilter.filterTracks(raw)
    }

    suspend fun getPhMusicVideos(): List<Track> = withContext(Dispatchers.IO) {
        val curated = PH_MV_TRACKS.map { it.toTrack() }
        val rssTracks = withTimeoutOrNull(4_500L) {
            coroutineScope {
                PH_CHANNEL_IDS.map { channelId ->
                    async {
                        withTimeoutOrNull(4_000L) {
                            fetchChannelRssVideos(channelId, regionHint = "PH")
                        }.orEmpty()
                    }
                }.awaitAll().flatten()
            }
        }.orEmpty()
        val merged = deduplicateByVideoId(curated + rssTracks)
        RegionalContentFilter.filterTracks(merged)
    }

    suspend fun getGlobalMusicVideos(): List<Track> = withContext(Dispatchers.IO) {
        val curated = GLOBAL_MV_TRACKS.map { it.toTrack() }
        val rssTracks = withTimeoutOrNull(4_500L) {
            coroutineScope {
                FEATURED_GLOBAL_RSS_CHANNEL_IDS.map { channelId ->
                    async {
                        withTimeoutOrNull(4_000L) {
                            fetchChannelRssVideos(channelId, regionHint = "Global")
                        }.orEmpty()
                    }
                }.awaitAll().flatten()
            }
        }.orEmpty()
        val merged = deduplicateByVideoId(curated + rssTracks)
        RegionalContentFilter.filterTracks(merged)
    }

    suspend fun searchVideos(query: String): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        extractVideoId(trimmed)?.let { videoId ->
            if (isUrlOrStrictId(trimmed)) {
                fetchOEmbedTrack(videoId)?.let { return@withContext listOf(it) }
            }
        }
        extractPlaylistId(trimmed)?.let { playlistId ->
            val playlistVideos = fetchPlaylistRssVideos(playlistId)
            if (playlistVideos.isNotEmpty()) return@withContext playlistVideos
        }
        extractChannelId(trimmed)?.let { channelId ->
            val channelVideos = fetchChannelRssVideos(
                channelId = channelId,
                regionHint = if (channelId in PH_CHANNEL_IDS) "PH" else "Global"
            )
            if (channelVideos.isNotEmpty()) return@withContext channelVideos
        }

        val expandedQuery = expandSearchAlias(trimmed)
        val qNorm = normalizeForMatch(expandedQuery)
        val qTokens = tokenize(expandedQuery)
        if (qNorm.isEmpty()) return@withContext emptyList()
        val effectiveTokens = qTokens.filterNot { it in SEARCH_CONNECTOR_WORDS }.ifEmpty { qTokens }

        val curatedMatches = (PH_MV_TRACKS + GLOBAL_MV_TRACKS)
            .map { it.toTrack() }
            .mapNotNull { track ->
                val score = scoreVideoMatch(track, qNorm, qTokens)
                if (score > 0) track to (score + 10) else null
            }

        val indexedRssMatches = rssIndexCache.values
            .mapNotNull { track ->
                val score = scoreVideoMatch(track, qNorm, qTokens)
                if (score > 0) track to score else null
            }

        val matchingChannels = VERIFIED_CHANNELS.filter { ch ->
            val chNorm = normalizeForMatch(ch.name)
            chNorm.contains(qNorm) || qNorm.contains(chNorm) ||
                effectiveTokens.any { token -> token.length >= 3 && chNorm.contains(token) }
        }.take(4)

        val channelRssTracks = if (matchingChannels.isNotEmpty()) {
            withTimeoutOrNull(4_500L) {
                coroutineScope {
                    matchingChannels.map { ch ->
                        async {
                            withTimeoutOrNull(4_000L) {
                                fetchChannelRssVideos(ch.channelId, ch.regionHint)
                            }.orEmpty()
                        }
                    }.awaitAll().flatten()
                }
            }.orEmpty()
        } else {
            emptyList()
        }
        channelRssTracks.forEach { track ->
            val key = track.youtubeVideoId.ifBlank { track.id }
            rssIndexCache[key] = track
        }

        val rssMatches = channelRssTracks.mapNotNull { track ->
            val score = scoreVideoMatch(track, qNorm, qTokens)
            if (score > 0) track to score else null
        }

        val sorted = (curatedMatches + indexedRssMatches + rssMatches)
            .sortedByDescending { it.second }
            .map { it.first }

        RegionalContentFilter.filterTracks(deduplicateByVideoId(sorted))
    }

    suspend fun importFromUrlOrQuery(input: String): YouTubeImportResult = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return@withContext YouTubeImportResult(emptyList(), "Empty input", 0)
        }

        val hasPlaylistPath = PLAYLIST_PATH_REGEX.containsMatchIn(trimmed)
        val videoId = if (!hasPlaylistPath) extractVideoId(trimmed) else null
        val playlistId = extractPlaylistId(trimmed)
        val channelId = extractChannelId(trimmed)

        if (videoId != null) {
            val rawResult = fetchOEmbedTrackWithFilterCheck(videoId)
            if (rawResult.excluded) {
                return@withContext YouTubeImportResult(
                    tracks = emptyList(),
                    sourceLabel = "YouTube Video",
                    excludedCount = 1
                )
            }
            if (rawResult.track != null) {
                return@withContext YouTubeImportResult(
                    tracks = listOf(rawResult.track),
                    sourceLabel = rawResult.track.artist.ifBlank { "YouTube Video" },
                    excludedCount = 0
                )
            }
            if (!isUrlOrStrictId(trimmed)) {
                val searchResults = searchVideos(trimmed)
                return@withContext YouTubeImportResult(
                    tracks = searchResults,
                    sourceLabel = "YouTube Search: $trimmed",
                    excludedCount = 0
                )
            }
            return@withContext YouTubeImportResult(emptyList(), "YouTube Video", 0)
        }

        if (playlistId != null) {
            val parsed = fetchPlaylistRssVideosRaw(playlistId)
            return@withContext YouTubeImportResult(
                tracks = parsed.tracks,
                sourceLabel = parsed.label.ifBlank { "YouTube Playlist" },
                excludedCount = parsed.excludedCount
            )
        }

        if (channelId != null) {
            val regionHint = if (channelId in PH_CHANNEL_IDS) "PH" else "Global"
            val parsed = fetchChannelRssVideosRaw(channelId, regionHint)
            return@withContext YouTubeImportResult(
                tracks = parsed.tracks,
                sourceLabel = parsed.label.ifBlank { "YouTube Channel" },
                excludedCount = parsed.excludedCount
            )
        }

        val matches = searchVideos(trimmed)
        YouTubeImportResult(
            tracks = matches,
            sourceLabel = "YouTube Search: $trimmed",
            excludedCount = 0
        )
    }

    suspend fun fetchOEmbedTrack(videoId: String): Track? = withContext(Dispatchers.IO) {
        fetchOEmbedTrackWithFilterCheck(videoId).track
    }

    private suspend fun fetchOEmbedTrackWithFilterCheck(videoId: String): OEmbedLookupResult {
        val cleanId = extractVideoId(videoId) ?: videoId.trim()
        if (!RAW_VIDEO_ID_REGEX.matches(cleanId)) return OEmbedLookupResult()

        oEmbedCache[cleanId]?.let { cached ->
            if (RegionalContentFilter.isExcludedTrack(cached)) {
                return OEmbedLookupResult(excluded = true)
            }
            return OEmbedLookupResult(track = cached)
        }

        val watchUrl = "https://www.youtube.com/watch?v=$cleanId"
        val oEmbedUrl = "$OEMBED_ENDPOINT?url=${encodeUrlParam(watchUrl)}&format=json"
        val body = client.getText(oEmbedUrl)
        if (!body.isNullOrBlank()) {
            try {
                val obj = JSONObject(body)
                val rawTitle = obj.optCleanString("title")
                val rawAuthor = obj.optCleanString("author_name")
                val thumbUrl = obj.optCleanString("thumbnail_url")
                if (rawTitle.isNotEmpty()) {
                    val curated = CURATED_BY_VIDEO_ID[cleanId]
                    val (cleanTitle, cleanArtist) = if (curated != null) {
                        curated.title to curated.artist
                    } else {
                        parseTitleAndArtist(rawTitle, rawAuthor)
                    }
                    val genre = curated?.genre ?: "Music Video"
                    if (RegionalContentFilter.isExcludedIndianContent(
                            title = cleanTitle,
                            artist = cleanArtist,
                            genre = genre,
                            extra = "$rawTitle $rawAuthor"
                        )
                    ) {
                        return OEmbedLookupResult(excluded = true)
                    }
                    val defaultThumb = "https://i.ytimg.com/vi/$cleanId/hqdefault.jpg"
                    val track = Track(
                        id = "yt_$cleanId",
                        title = cleanTitle,
                        artist = cleanArtist,
                        artistId = curated?.artistId ?: cleanArtist,
                        album = curated?.album ?: "YouTube Music Video",
                        genre = genre,
                        mood = curated?.mood.orEmpty(),
                        durationSec = curated?.durationSec ?: DEFAULT_VIDEO_DURATION_SEC,
                        artworkUrl = defaultThumb,
                        artworkMirrors = listOfNotNull(
                            thumbUrl.takeIf { it.isNotBlank() && it != defaultThumb },
                            "https://i.ytimg.com/vi/$cleanId/maxresdefault.jpg"
                        ),
                        streamUrl = "",
                        source = TrackSource.YOUTUBE,
                        isLive = false,
                        isSmartPick = false,
                        externalUrl = watchUrl,
                        isPreviewOnly = false,
                        youtubeVideoId = cleanId,
                        hasMusicVideo = true
                    )
                    oEmbedCache[cleanId] = track
                    return OEmbedLookupResult(track = track)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Fall back to curated metadata if available
            }
        }

        val curated = CURATED_BY_VIDEO_ID[cleanId]?.toTrack()
        if (curated != null) {
            if (RegionalContentFilter.isExcludedTrack(curated)) {
                return OEmbedLookupResult(excluded = true)
            }
            oEmbedCache[cleanId] = curated
            return OEmbedLookupResult(track = curated)
        }

        return OEmbedLookupResult()
    }

    suspend fun fetchChannelRssVideos(
        channelId: String,
        regionHint: String = "Global"
    ): List<Track> = withContext(Dispatchers.IO) {
        fetchChannelRssVideosRaw(channelId, regionHint).tracks
    }

    suspend fun fetchPlaylistRssVideos(playlistId: String): List<Track> = withContext(Dispatchers.IO) {
        fetchPlaylistRssVideosRaw(playlistId).tracks
    }

    private suspend fun fetchChannelRssVideosRaw(
        channelId: String,
        regionHint: String
    ): ParsedFeedResult {
        val cleanId = extractChannelId(channelId) ?: channelId.trim()
        if (cleanId.isEmpty()) return ParsedFeedResult(emptyList(), "", 0)

        val cacheKey = "ch_${cleanId}_$regionHint"
        val now = System.currentTimeMillis()
        feedCache[cacheKey]?.let { cached ->
            if (now - cached.timestampMs < FEED_CACHE_TTL_MS) {
                val filtered = RegionalContentFilter.filterTracks(cached.value)
                return ParsedFeedResult(filtered, VERIFIED_CHANNEL_BY_ID[cleanId]?.name ?: "YouTube Channel", 0)
            }
        }

        val url = "$RSS_ENDPOINT?channel_id=${encodeUrlParam(cleanId)}"
        val xml = client.getText(
            url = url,
            acceptHeader = "application/atom+xml, application/xml, text/xml, */*"
        ) ?: return ParsedFeedResult(emptyList(), "", 0)

        val parsed = parseAtomFeedXml(
            xml = xml,
            regionHint = regionHint,
            fallbackLabel = VERIFIED_CHANNEL_BY_ID[cleanId]?.name ?: "YouTube Channel"
        )
        if (parsed.tracks.isNotEmpty()) {
            feedCache[cacheKey] = CacheEntry(parsed.tracks, now)
        }
        return parsed
    }

    private suspend fun fetchPlaylistRssVideosRaw(playlistId: String): ParsedFeedResult {
        val cleanId = extractPlaylistId(playlistId) ?: playlistId.trim()
        if (cleanId.isEmpty()) return ParsedFeedResult(emptyList(), "", 0)

        val url = "$RSS_ENDPOINT?playlist_id=${encodeUrlParam(cleanId)}"
        val xml = client.getText(
            url = url,
            acceptHeader = "application/atom+xml, application/xml, text/xml, */*"
        ) ?: return ParsedFeedResult(emptyList(), "", 0)

        return parseAtomFeedXml(
            xml = xml,
            regionHint = "Global",
            fallbackLabel = "YouTube Playlist"
        )
    }

    internal fun parseAtomFeedXml(
        xml: String,
        regionHint: String = "Global",
        fallbackLabel: String = "YouTube Feed"
    ): ParsedFeedResult {
        if (xml.isBlank()) return ParsedFeedResult(emptyList(), fallbackLabel, 0)
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                try {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                } catch (_: Throwable) {
                }
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))
            doc.documentElement?.normalize()

            val rootElement = doc.documentElement
            val feedTitle = rootElement?.let { firstDirectChildText(it, "title") }.orEmpty()
                .ifBlank {
                    rootElement?.let { firstDirectChildElement(it, "author")?.let { a -> firstDirectChildText(a, "name") } }.orEmpty()
                }
                .ifBlank { fallbackLabel }

            val entryNodes = doc.getElementsByTagName("entry")
            val tracks = mutableListOf<Track>()
            var excludedCount = 0

            for (i in 0 until entryNodes.length) {
                val entry = entryNodes.item(i) as? Element ?: continue
                val videoId = extractVideoIdFromEntry(entry) ?: continue
                val rawTitle = firstChildTagText(entry, "title")
                    .ifBlank { firstDescendantTagText(entry, "media:title") }
                if (rawTitle.isBlank()) continue

                val entryAuthor = firstDirectChildElement(entry, "author")
                    ?.let { firstDirectChildText(it, "name") }
                    .orEmpty()
                    .ifBlank { feedTitle }
                val channelId = firstDescendantTagText(entry, "yt:channelId")
                val description = firstDescendantTagText(entry, "media:description")
                    .ifBlank { firstChildTagText(entry, "summary") }
                val published = firstChildTagText(entry, "published")
                    .ifBlank { firstChildTagText(entry, "updated") }
                    .take(10)

                val durationSec = extractDurationFromEntry(entry) ?: DEFAULT_VIDEO_DURATION_SEC
                val playCount = extractViewsFromEntry(entry)
                val xmlThumb = extractThumbnailFromEntry(entry)

                val curated = CURATED_BY_VIDEO_ID[videoId]
                val (cleanTitle, cleanArtist) = if (curated != null) {
                    curated.title to curated.artist
                } else {
                    parseTitleAndArtist(rawTitle, entryAuthor)
                }
                val genre = curated?.genre ?: if (regionHint.equals("PH", ignoreCase = true)) "OPM" else "Music Video"

                if (RegionalContentFilter.isExcludedIndianContent(
                        title = cleanTitle,
                        artist = cleanArtist,
                        genre = genre,
                        extra = "$rawTitle $entryAuthor $feedTitle $description"
                    )
                ) {
                    excludedCount++
                    continue
                }

                val defaultThumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                tracks.add(
                    Track(
                        id = "yt_$videoId",
                        title = cleanTitle,
                        artist = cleanArtist,
                        artistId = curated?.artistId ?: channelId.ifBlank { cleanArtist },
                        album = curated?.album ?: feedTitle,
                        genre = genre,
                        mood = curated?.mood.orEmpty(),
                        durationSec = durationSec,
                        artworkUrl = defaultThumb,
                        artworkMirrors = listOfNotNull(
                            xmlThumb.takeIf { it.isNotBlank() && it != defaultThumb },
                            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
                        ),
                        streamUrl = "",
                        source = TrackSource.YOUTUBE,
                        isLive = false,
                        isSmartPick = false,
                        releaseDate = published,
                        playCount = playCount,
                        externalUrl = "https://www.youtube.com/watch?v=$videoId",
                        isPreviewOnly = false,
                        youtubeVideoId = videoId,
                        hasMusicVideo = true
                    )
                )
            }

            ParsedFeedResult(
                tracks = deduplicateByVideoId(tracks),
                label = feedTitle,
                excludedCount = excludedCount
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            ParsedFeedResult(emptyList(), fallbackLabel, 0)
        }
    }

    internal fun parseTitleAndArtist(rawTitle: String, fallbackChannel: String): Pair<String, String> {
        val trimmedTitle = rawTitle.trim()
        val cleanChannel = cleanChannelName(fallbackChannel)

        PERFORMS_TITLE_REGEX.find(trimmedTitle)?.let { match ->
            val artist = match.groupValues[1].trim()
            val song = cleanSongTitle(match.groupValues[2])
            if (song.isNotEmpty() && artist.isNotEmpty()) {
                return song to artist
            }
        }

        QUOTED_SONG_DASH_ARTIST_REGEX.find(trimmedTitle)?.let { match ->
            val song = cleanSongTitle(match.groupValues[1])
            val artist = cleanArtistSuffix(match.groupValues[2])
            if (song.isNotEmpty() && artist.isNotEmpty()) {
                return song to artist
            }
        }

        val withoutTrailingPipe = trimmedTitle.replace(PIPE_CHANNEL_SUFFIX_REGEX, "").trim()
        val separatorMatch = ARTIST_SONG_SPLIT_REGEX.find(withoutTrailingPipe)
        if (separatorMatch != null) {
            val left = withoutTrailingPipe.substring(0, separatorMatch.range.first).trim()
            val right = withoutTrailingPipe.substring(separatorMatch.range.last + 1).trim()
            val cleanedRight = cleanSongTitle(right)
            val cleanedLeft = cleanArtistSuffix(left)
            if (cleanedLeft.isNotEmpty() && cleanedRight.isNotEmpty()) {
                return cleanedRight to cleanedLeft
            }
        }

        val fallbackTitle = cleanSongTitle(withoutTrailingPipe).ifBlank { trimmedTitle }
        val fallbackArtist = cleanChannel.ifBlank { "Official Artist" }
        return fallbackTitle to fallbackArtist
    }

    internal fun findMatchingVerifiedVideoId(title: String, artist: String): String? {
        val normTitle = normalizeTitleForLookup(title)
        if (normTitle.isEmpty()) return null
        val targetArtistTokens = significantTokens(artist)

        val candidate = (PH_MV_TRACKS + GLOBAL_MV_TRACKS).firstOrNull { mv ->
            val mvTitle = normalizeTitleForLookup(mv.title)
            val mvArtistTokens = significantTokens(mv.artist)
            val titleMatch = normTitle == mvTitle
            val artistMatch = if (targetArtistTokens.isEmpty() || mvArtistTokens.isEmpty()) {
                true
            } else {
                mvArtistTokens.any { it in targetArtistTokens } ||
                    targetArtistTokens.any { it in mvArtistTokens }
            }
            titleMatch && artistMatch
        }
        return candidate?.videoId
    }

    private fun significantTokens(raw: String): List<String> {
        val tokens = tokenize(raw)
        val filtered = tokens.filterNot { it in FILLER_WORDS }
        return filtered.ifEmpty { tokens }
    }

    private fun extractVideoIdFromEntry(entry: Element): String? {
        val ytVideoId = firstDescendantTagText(entry, "yt:videoId")
            .ifBlank { firstDescendantTagText(entry, "videoId") }
        if (RAW_VIDEO_ID_REGEX.matches(ytVideoId)) {
            return ytVideoId
        }
        val entryId = firstChildTagText(entry, "id")
        if (entryId.startsWith("yt:video:")) {
            val candidate = entryId.removePrefix("yt:video:").trim()
            if (RAW_VIDEO_ID_REGEX.matches(candidate)) return candidate
        }
        val links = entry.getElementsByTagName("link")
        for (j in 0 until links.length) {
            val linkEl = links.item(j) as? Element ?: continue
            val href = linkEl.getAttribute("href").trim()
            extractVideoId(href)?.let { return it }
        }
        return null
    }

    private fun extractDurationFromEntry(entry: Element): Int? {
        val ytDurations = entry.getElementsByTagName("yt:duration")
        if (ytDurations.length > 0) {
            val el = ytDurations.item(0) as? Element
            val sec = el?.getAttribute("seconds")?.trim()?.toIntOrNull()
            if (sec != null && sec > 0) return sec
        }
        val mediaContents = entry.getElementsByTagName("media:content")
        if (mediaContents.length > 0) {
            val el = mediaContents.item(0) as? Element
            val sec = el?.getAttribute("duration")?.trim()?.toIntOrNull()
            if (sec != null && sec > 0) return sec
        }
        val textDuration = firstDescendantTagText(entry, "duration")
        val parsed = parseFlexibleDurationToSeconds(textDuration)
        return parsed.takeIf { it > 0 }
    }

    private fun extractViewsFromEntry(entry: Element): Long {
        val stats = entry.getElementsByTagName("media:statistics")
        if (stats.length > 0) {
            val el = stats.item(0) as? Element
            val views = el?.getAttribute("views")?.trim()?.toLongOrNull()
            if (views != null && views >= 0L) return views
        }
        return 0L
    }

    private fun extractThumbnailFromEntry(entry: Element): String {
        val thumbs = entry.getElementsByTagName("media:thumbnail")
        if (thumbs.length > 0) {
            val el = thumbs.item(0) as? Element
            val url = el?.getAttribute("url")?.trim().orEmpty()
            if (url.isNotEmpty()) return url
        }
        return ""
    }

    private fun firstDirectChildElement(parent: Element, tagName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName.equals(tagName, ignoreCase = true)) {
                return node as Element
            }
        }
        return null
    }

    private fun firstDirectChildText(parent: Element, tagName: String): String =
        firstDirectChildElement(parent, tagName)?.textContent?.trim().orEmpty()

    private fun firstChildTagText(parent: Element, tagName: String): String =
        firstDirectChildText(parent, tagName).ifBlank { firstDescendantTagText(parent, tagName) }

    private fun firstDescendantTagText(parent: Element, tagName: String): String {
        val list = parent.getElementsByTagName(tagName)
        if (list.length == 0) return ""
        return list.item(0)?.textContent?.trim().orEmpty()
    }

    private fun cleanChannelName(raw: String): String {
        val cleaned = raw.trim()
            .replace(TOPIC_SUFFIX_REGEX, "")
            .replace(VEVO_SUFFIX_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        return if (cleaned.lowercase(Locale.ROOT) in GENERIC_HUB_CHANNELS) "" else cleaned
    }

    private fun cleanSongTitle(raw: String): String {
        var cleaned = raw.trim()
        cleaned = cleaned.replace(PIPE_CHANNEL_SUFFIX_REGEX, "").trim()
        cleaned = cleaned.replace(VIDEO_NOISE_PARENS_REGEX, " ")
        cleaned = cleaned.replace(VIDEO_NOISE_BRACKETS_REGEX, " ")
        cleaned = cleaned.replace(VIDEO_TRAILING_WORDS_REGEX, "")
        cleaned = cleaned.replace(MULTI_SPACE_REGEX, " ").trim()
        if (cleaned.length >= 2 &&
            ((cleaned.startsWith('"') && cleaned.endsWith('"')) ||
                (cleaned.startsWith('\'') && cleaned.endsWith('\'')))
        ) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        return cleaned
    }

    private fun cleanArtistSuffix(raw: String): String = raw
        .replace(PIPE_CHANNEL_SUFFIX_REGEX, "")
        .replace(VIDEO_NOISE_PARENS_REGEX, " ")
        .replace(VIDEO_NOISE_BRACKETS_REGEX, " ")
        .replace(MULTI_SPACE_REGEX, " ")
        .trim()

    private fun isUrlOrStrictId(input: String): Boolean =
        input.contains("youtu", ignoreCase = true) ||
            (RAW_VIDEO_ID_REGEX.matches(input) && input.any { it.isUpperCase() || it.isDigit() || it == '-' || it == '_' })

    private fun expandSearchAlias(query: String): String {
        var normalized = query.trim()
        normalized = WEEKEND_ALIAS_REGEX.replace(normalized, "The Weeknd")
        normalized = MARIA_DEBORAH_ALIAS_REGEX.replace(normalized, "Mariah Deborah")
        normalized = ESREMBORAK_ALIAS_REGEX.replace(normalized, "Esremborak")
        normalized = BEN_AND_BEN_ALIAS_REGEX.replace(normalized, "Ben&Ben")
        normalized = CUP_OF_JO_ALIAS_REGEX.replace(normalized, "Cup of Joe")
        return normalized.replace(MULTI_SPACE_REGEX, " ").trim()
    }

    private fun scoreVideoMatch(track: Track, qNorm: String, qTokens: List<String>): Int {
        val titleNorm = normalizeForMatch(track.title)
        val artistNorm = normalizeForMatch(track.artist)
        val genreNorm = normalizeForMatch(track.genre)
        val moodNorm = normalizeForMatch(track.mood)
        val combined = "$titleNorm $artistNorm $genreNorm $moodNorm"

        if (titleNorm == qNorm || artistNorm == qNorm) return 150
        if (titleNorm.startsWith(qNorm) || artistNorm.startsWith(qNorm)) return 120
        if (titleNorm.contains(qNorm) || artistNorm.contains(qNorm)) return 100
        if (combined.contains(qNorm)) return 80

        if (qTokens.isEmpty()) return 0
        val effectiveTokens = qTokens.filterNot { it in SEARCH_CONNECTOR_WORDS }
            .ifEmpty { qTokens }
        val combinedTokens = tokenize(combined).toSet()
        val matched = effectiveTokens.count { qTok ->
            combinedTokens.any { it == qTok || (qTok.length >= 3 && it.startsWith(qTok)) }
        }
        return when {
            matched == effectiveTokens.size -> 90 + matched * 10
            effectiveTokens.size >= 2 && matched >= effectiveTokens.size - 1 && matched > 0 -> 35 + matched * 5
            else -> 0
        }
    }

    private fun deduplicateByVideoId(tracks: List<Track>): List<Track> {
        val seenVideoIds = HashSet<String>(tracks.size)
        val seenIds = HashSet<String>(tracks.size)
        return buildList {
            for (t in tracks) {
                val key = t.youtubeVideoId.ifBlank { t.id }
                if (seenVideoIds.add(key) && seenIds.add(t.id)) {
                    add(t)
                }
            }
        }
    }

    private fun normalizeTitleForLookup(raw: String): String {
        val stripped = raw
            .replace(ANY_PARENS_REGEX, " ")
            .replace(ANY_BRACKETS_REGEX, " ")
            .replace(FEAT_SUFFIX_REGEX, "")
        return normalizeForMatch(stripped)
    }

    private fun normalizeForMatch(raw: String): String {
        if (raw.isBlank()) return ""
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .replace("'", "")
            .replace("\u2019", "")
            .replace(NON_ALNUM_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    private fun tokenize(raw: String): List<String> {
        val norm = normalizeForMatch(raw)
        if (norm.isEmpty()) return emptyList()
        return norm.split(' ').filter { it.isNotEmpty() }
    }

    internal data class ParsedFeedResult(
        val tracks: List<Track>,
        val label: String,
        val excludedCount: Int
    )

    private data class OEmbedLookupResult(
        val track: Track? = null,
        val excluded: Boolean = false
    )

    private data class CacheEntry<T>(
        val value: T,
        val timestampMs: Long
    )

    private data class VerifiedChannel(
        val channelId: String,
        val name: String,
        val regionHint: String
    )

    private data class CuratedMv(
        val videoId: String,
        val title: String,
        val artist: String,
        val genre: String,
        val durationSec: Int,
        val mood: String = "",
        val album: String = "Official Music Video",
        val artistId: String = ""
    ) {
        fun toTrack(): Track {
            val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            return Track(
                id = "yt_$videoId",
                title = title,
                artist = artist,
                artistId = artistId.ifBlank { artist },
                album = album,
                genre = genre,
                mood = mood,
                durationSec = durationSec,
                artworkUrl = thumb,
                artworkMirrors = listOf("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"),
                streamUrl = "",
                source = TrackSource.YOUTUBE,
                isLive = false,
                isSmartPick = false,
                externalUrl = "https://www.youtube.com/watch?v=$videoId",
                isPreviewOnly = false,
                youtubeVideoId = videoId,
                hasMusicVideo = true
            )
        }
    }

    companion object {
        const val PACK_PH_VIRAL = "ph_viral"
        const val PACK_WISH_1075 = "wish_1075"
        const val PACK_GLOBAL_MVS = "global_mvs"

        private const val OEMBED_ENDPOINT = "https://www.youtube.com/oembed"
        private const val RSS_ENDPOINT = "https://www.youtube.com/feeds/videos.xml"
        private const val DEFAULT_VIDEO_DURATION_SEC = 210
        private const val FEED_CACHE_TTL_MS = 120_000L
        private const val INDEX_CACHE_TTL_MS = 600_000L

        private val RAW_VIDEO_ID_REGEX = Regex("""^[A-Za-z0-9_-]{11}$""")
        private val RAW_PLAYLIST_ID_REGEX = Regex("""^(?:PL|UU|OLAK5uy_|LL|FL)[A-Za-z0-9_-]{8,}$""")
        private val RAW_CHANNEL_ID_REGEX = Regex("""^UC[A-Za-z0-9_-]{22}$""")

        private val URL_EXTRACT_REGEX = Regex("""https?://[^\s<>"]+""")
        private val PLAYLIST_PATH_REGEX = Regex("""youtube\.com/playlist\b""", RegexOption.IGNORE_CASE)
        private val WATCH_V_REGEX = Regex("""[?&]v=([A-Za-z0-9_-]{11})(?:[&#?/\s]|$)""")
        private val SHORT_OR_PATH_VIDEO_REGEX = Regex(
            """(?:youtube\.com|youtube-nocookie\.com)/(?:shorts|live|embed|v)/([A-Za-z0-9_-]{11})(?:[?&#/\s]|$)""",
            RegexOption.IGNORE_CASE
        )
        private val YOUTU_BE_REGEX = Regex("""youtu\.be/([A-Za-z0-9_-]{11})(?:[?&#/\s]|$)""", RegexOption.IGNORE_CASE)
        private val LIST_PARAM_REGEX = Regex("""[?&]list=([A-Za-z0-9_-]{10,})(?:[&#\s]|$)""")
        private val CHANNEL_PATH_REGEX = Regex("""youtube\.com/channel/(UC[A-Za-z0-9_-]{20,})(?:[?&#/\s]|$)""", RegexOption.IGNORE_CASE)
        private val HANDLE_IN_URL_REGEX = Regex("""youtube\.com/@([A-Za-z0-9._-]+)(?:[?&#/\s]|$)""", RegexOption.IGNORE_CASE)

        private val WEEKEND_ALIAS_REGEX = Regex("""\b(?:the\s+weekend|weekend|weeknd)\b""", RegexOption.IGNORE_CASE)
        private val MARIA_DEBORAH_ALIAS_REGEX = Regex("""\b(?:maria\s+deborah|maria\s+debora|mariadeborah)\b""", RegexOption.IGNORE_CASE)
        private val ESREMBORAK_ALIAS_REGEX = Regex("""\besremborac\b""", RegexOption.IGNORE_CASE)
        private val BEN_AND_BEN_ALIAS_REGEX = Regex("""\bben\s+and\s+ben\b""", RegexOption.IGNORE_CASE)
        private val CUP_OF_JO_ALIAS_REGEX = Regex("""\bcup\s+of\s+jo\b""", RegexOption.IGNORE_CASE)

        private val PERFORMS_TITLE_REGEX = Regex(
            """^(.+?)\s+performs\s+["\u201C'](.+?)["\u201D'](?:\s+LIVE.*|\s+on\s+.*|$)""",
            RegexOption.IGNORE_CASE
        )
        private val QUOTED_SONG_DASH_ARTIST_REGEX = Regex(
            """^["\u201C'](.+?)["\u201D']\s*[-–—]\s*(.+)$"""
        )
        private val PIPE_CHANNEL_SUFFIX_REGEX = Regex("""\s*\|\s*[^|]+$""")
        private val ARTIST_SONG_SPLIT_REGEX = Regex("""\s+[-–—]\s+""")
        private val VIDEO_NOISE_PARENS_REGEX = Regex(
            """\((?:official\s*(?:music\s*)?(?:video|audio|lyric\s*video|visualizer)|music\s*video|lyric\s*video|lyrics?|audio|performance\s*video|wishclusive|4k|hd)[^)]*\)""",
            RegexOption.IGNORE_CASE
        )
        private val VIDEO_NOISE_BRACKETS_REGEX = Regex(
            """\[(?:official\s*(?:music\s*)?(?:video|audio|lyric\s*video|visualizer)|music\s*video|lyrics?|4k|hd)[^\]]*]""",
            RegexOption.IGNORE_CASE
        )
        private val VIDEO_TRAILING_WORDS_REGEX = Regex(
            """\s+(?:official\s*(?:music\s*)?video|music\s*video|performance\s*video|wishclusive)\s*$""",
            RegexOption.IGNORE_CASE
        )
        private val TOPIC_SUFFIX_REGEX = Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE)
        private val VEVO_SUFFIX_REGEX = Regex("""(?<=\S)VEVO$""", RegexOption.IGNORE_CASE)
        private val ANY_PARENS_REGEX = Regex("""\([^)]*\)""")
        private val ANY_BRACKETS_REGEX = Regex("""\[[^\]]*]""")
        private val FEAT_SUFFIX_REGEX = Regex("""\s*\b(?:feat|ft)\.?\s+.*$""", RegexOption.IGNORE_CASE)
        private val DIACRITICS_REGEX = Regex("""\p{InCombiningDiacriticalMarks}+""")
        private val NON_ALNUM_REGEX = Regex("""[^a-z0-9]""")
        private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")

        private val GENERIC_HUB_CHANNELS = setOf(
            "wish 107.5", "wish 1075", "the cozy cove", "nine degrees north",
            "abs-cbn star music", "viva records", "tarsier records", "hybe labels",
            "jyp entertainment", "smtown", "tower of doom", "youtube"
        )
        private val FILLER_WORDS = setOf(
            "the", "a", "an", "and", "of", "ft", "feat", "with", "x"
        )
        private val SEARCH_CONNECTOR_WORDS = setOf(
            "by", "the", "official", "mv", "video", "music", "song", "ft", "feat"
        )

        private val HANDLE_ALIASES = mapOf(
            "@wish1075" to "UCdfq8-581QVUtVsa8ChVnDQ",
            "@wish1075official" to "UCdfq8-581QVUtVsa8ChVnDQ",
            "@bini_ph" to "UCtOcDBKgyr-f50SgbMErFkQ",
            "@biniph" to "UCtOcDBKgyr-f50SgbMErFkQ",
            "@biniofficial" to "UCtOcDBKgyr-f50SgbMErFkQ",
            "@starmusicph" to "UCIWZ1_W5BqsvW9fm1cYFPpQ",
            "@vivarecordsofficial" to "UCS09CkK8fjPJ_nHD6fcGMSw",
            "@tarsierrecords" to "UC9fACMkCC80Lz8aZV--rNwA",
            "@ninedegreesnorthrecords" to "UCNO66UYt0xSeEMw9d5v-37w",
            "@officialsb19" to "UCm4v7afBTnJKRm4SlfHJzyg",
            "@benandbenmusic" to "UCD2HT__OY6xzj-YbnAXJhfA",
            "@cupofjoemusic_" to "UCPq0kgKjpNzryoNbRp5eUyQ",
            "@theweeknd" to "UC0WP5P-ufpRfjbNrmOWwLBQ",
            "@brunomars" to "UCoUM-UJ7rirJYP8CQ0EIaHA",
            "@billieeilish" to "UCiGm_E4ZwYSHV3bcW1pnSeQ",
            "@taylorswift" to "UCqECaJ8Gagnn7YCbPEzWH6g",
            "@arianagrande" to "UC9CoOnJkIBMdeijd9qYoT_g",
            "@blackpink" to "UCOmHUn--16B90oW2L6FRR3A",
            "@blackpinkofficial" to "UCOmHUn--16B90oW2L6FRR3A",
            "@coldplay" to "UCDPM_n1atn2ijUwHd0NNRQw",
            "@edsheeran" to "UC0C-w0YjGpqDXGB8IHb662A",
            "@harrystyles" to "UCZFWPqqPkFlNwIxcpsLOwew",
            "@drake" to "UCByOQJjav0CUDwxCk-jVNRQ",
            "@champagnepapi" to "UCByOQJjav0CUDwxCk-jVNRQ",
            "@lanadelrey" to "UCqk3CdGN_j8IR9z4uBbVPSg",
            "@travisscott" to "UCtxdfwb9wfkoGocVUAJ-Bmg",
            "@sabrinacarpenter" to "UCPKWE1H6xhxwPlqUlKgHb_w",
            "@oliviarodrigo" to "UCy3zgWom-5AGypGX_FVTKpg",
            "@sza" to "UCO5IQ70V7l-XpHW40HwaGsw",
            "@kendricklamar" to "UC3lBXcrKFnFAFkfVk5WuKcQ",
            "@dualipa" to "UC-J-KZfRV8c13fOCkhXdLiQ",
            "@postmalone" to "UCeLHszkByNZtPKcaVXOCOQQ",
            "@justinbieber" to "UCIwFjwMjI0y7PDBVEO9-bkQ",
            "@ladygaga" to "UCNL1ZadSjHpjm4q9j2sVtOA",
            "@maroon5" to "UCBVjMGOIkavEAhyqpxJ73Dw",
            "@adele" to "UCsRM0YB_dabtEPGPTKo-gcw",
            "@rihanna" to "UCcgqSM4YEo5vVQpqwN-MaNw",
            "@shawnmendes" to "UCAvCL8hyXjSUHKEGuUPr1BA",
            "@charlieputh" to "UCwppdrjsBPAZg5_cUwQjfMQ",
            "@tatemcrae" to "UCQh6LB206jF3JxpCDD-fp5Q",
            "@jennierubyjane" to "UCNYi_zGmR519r5gYdOKLTjQ",
            "@roses_are_rosie" to "UCBo1hnzxV9rz3WVsv__Rn1g"
        )

        // 39 live-verified official YouTube channel RSS feeds (zero Indian channels)
        private val VERIFIED_CHANNELS = listOf(
            VerifiedChannel("UCdfq8-581QVUtVsa8ChVnDQ", "Wish 107.5", "PH"),
            VerifiedChannel("UCtOcDBKgyr-f50SgbMErFkQ", "BINI Official", "PH"),
            VerifiedChannel("UCIWZ1_W5BqsvW9fm1cYFPpQ", "ABS-CBN Star Music", "PH"),
            VerifiedChannel("UCS09CkK8fjPJ_nHD6fcGMSw", "Viva Records", "PH"),
            VerifiedChannel("UC9fACMkCC80Lz8aZV--rNwA", "Tarsier Records", "PH"),
            VerifiedChannel("UCNO66UYt0xSeEMw9d5v-37w", "Nine Degrees North", "PH"),
            VerifiedChannel("UCm4v7afBTnJKRm4SlfHJzyg", "SB19 Official", "PH"),
            VerifiedChannel("UCD2HT__OY6xzj-YbnAXJhfA", "Ben&Ben", "PH"),
            VerifiedChannel("UCPq0kgKjpNzryoNbRp5eUyQ", "Cup of Joe", "PH"),
            VerifiedChannel("UC0WP5P-ufpRfjbNrmOWwLBQ", "The Weeknd", "Global"),
            VerifiedChannel("UCoUM-UJ7rirJYP8CQ0EIaHA", "Bruno Mars", "Global"),
            VerifiedChannel("UCiGm_E4ZwYSHV3bcW1pnSeQ", "Billie Eilish", "Global"),
            VerifiedChannel("UCqECaJ8Gagnn7YCbPEzWH6g", "Taylor Swift", "Global"),
            VerifiedChannel("UC9CoOnJkIBMdeijd9qYoT_g", "Ariana Grande", "Global"),
            VerifiedChannel("UCOmHUn--16B90oW2L6FRR3A", "BLACKPINK", "Global"),
            VerifiedChannel("UCDPM_n1atn2ijUwHd0NNRQw", "Coldplay", "Global"),
            VerifiedChannel("UC0C-w0YjGpqDXGB8IHb662A", "Ed Sheeran", "Global"),
            VerifiedChannel("UCZFWPqqPkFlNwIxcpsLOwew", "Harry Styles", "Global"),
            VerifiedChannel("UCByOQJjav0CUDwxCk-jVNRQ", "Drake", "Global"),
            VerifiedChannel("UCqk3CdGN_j8IR9z4uBbVPSg", "Lana Del Rey", "Global"),
            VerifiedChannel("UCtxdfwb9wfkoGocVUAJ-Bmg", "Travis Scott", "Global"),
            VerifiedChannel("UCPKWE1H6xhxwPlqUlKgHb_w", "Sabrina Carpenter", "Global"),
            VerifiedChannel("UCy3zgWom-5AGypGX_FVTKpg", "Olivia Rodrigo", "Global"),
            VerifiedChannel("UCO5IQ70V7l-XpHW40HwaGsw", "SZA", "Global"),
            VerifiedChannel("UC3lBXcrKFnFAFkfVk5WuKcQ", "Kendrick Lamar", "Global"),
            VerifiedChannel("UC-J-KZfRV8c13fOCkhXdLiQ", "Dua Lipa", "Global"),
            VerifiedChannel("UCeLHszkByNZtPKcaVXOCOQQ", "Post Malone", "Global"),
            VerifiedChannel("UCIwFjwMjI0y7PDBVEO9-bkQ", "Justin Bieber", "Global"),
            VerifiedChannel("UCNL1ZadSjHpjm4q9j2sVtOA", "Lady Gaga", "Global"),
            VerifiedChannel("UCBVjMGOIkavEAhyqpxJ73Dw", "Maroon 5", "Global"),
            VerifiedChannel("UCsRM0YB_dabtEPGPTKo-gcw", "Adele", "Global"),
            VerifiedChannel("UCcgqSM4YEo5vVQpqwN-MaNw", "Rihanna", "Global"),
            VerifiedChannel("UCAvCL8hyXjSUHKEGuUPr1BA", "Shawn Mendes", "Global"),
            VerifiedChannel("UCwppdrjsBPAZg5_cUwQjfMQ", "Charlie Puth", "Global"),
            VerifiedChannel("UCQh6LB206jF3JxpCDD-fp5Q", "Tate McRae", "Global"),
            VerifiedChannel("UCNYi_zGmR519r5gYdOKLTjQ", "JENNIE", "Global"),
            VerifiedChannel("UCBo1hnzxV9rz3WVsv__Rn1g", "ROSÉ", "Global"),
            VerifiedChannel("UC3IZKseVpdzPSBaWxBxundA", "HYBE LABELS", "Global"),
            VerifiedChannel("UCaO6TYtlC8U5ttz62hTrZgg", "JYP Entertainment", "Global"),
            VerifiedChannel("UCEf_Bc-KVd7onSeifS3py9g", "SMTOWN", "Global")
        )

        private val VERIFIED_CHANNEL_BY_ID = VERIFIED_CHANNELS.associateBy { it.channelId }
        private val PH_CHANNEL_IDS = setOf(
            "UCdfq8-581QVUtVsa8ChVnDQ",
            "UCtOcDBKgyr-f50SgbMErFkQ",
            "UCIWZ1_W5BqsvW9fm1cYFPpQ",
            "UCS09CkK8fjPJ_nHD6fcGMSw",
            "UC9fACMkCC80Lz8aZV--rNwA",
            "UCNO66UYt0xSeEMw9d5v-37w",
            "UCm4v7afBTnJKRm4SlfHJzyg",
            "UCD2HT__OY6xzj-YbnAXJhfA",
            "UCPq0kgKjpNzryoNbRp5eUyQ"
        )
        private val FEATURED_GLOBAL_RSS_CHANNEL_IDS = listOf(
            "UC0WP5P-ufpRfjbNrmOWwLBQ",
            "UCoUM-UJ7rirJYP8CQ0EIaHA",
            "UCiGm_E4ZwYSHV3bcW1pnSeQ",
            "UCqECaJ8Gagnn7YCbPEzWH6g",
            "UCOmHUn--16B90oW2L6FRR3A",
            "UCPKWE1H6xhxwPlqUlKgHb_w",
            "UCy3zgWom-5AGypGX_FVTKpg"
        )

        private val WISH_1075_PACK_VIDEO_IDS = setOf(
            "S7BKWs2Tirg",
            "Y5z_4utl678",
            "IKB_JIXfuDw",
            "bD9whtdq7dw"
        )

        // Live-oEmbed-verified PH/OPM Official MVs (9)
        private val PH_MV_TRACKS = listOf(
            CuratedMv("S7BKWs2Tirg", "Mahal Magmahal", "Esremborak", "OPM", 248, "Wish 107.5 Bus", "Wish 107.5 Bus"),
            CuratedMv("Y5z_4utl678", "Na Para Bang", "Mariah Deborah", "OPM", 224, "Wish 107.5 Bus", "Wish 107.5 Bus"),
            CuratedMv("IKB_JIXfuDw", "Tenchu", "Esremborak", "OPM", 212, "Emotional", "Official Music Video"),
            CuratedMv("Rht8rS4cR1s", "Multo", "Cup of Joe", "OPM", 240, "Emotional", "Silakbo", "60687062"),
            CuratedMv("Zx31bB2vMns", "Pantropiko", "BINI", "P-Pop", 225, "Sunny", "Talaarawan", "320392011"),
            CuratedMv("bD9whtdq7dw", "Marilag (Live at The Cozy Cove)", "Dionela", "R&B", 218, "Groovy", "The Cozy Cove", "69165172"),
            CuratedMv("L215z9C4Zd8", "Dilaw", "Maki", "OPM", 205, "Upbeat", "Official Music Video", "87317692"),
            CuratedMv("oiAtAzFLDfE", "Tibok", "Earl Agustin", "OPM", 327, "Romantic", "Official Music Video"),
            CuratedMv("D1VaVDm_YP4", "Palagi", "Shanti Dope", "Pinoy Hip-Hop", 210, "Hype", "Official Music Video")
        )

        // Live-oEmbed-verified Global Official MVs (30, incl. Hong Kong by The Weeknd)
        private val GLOBAL_MV_TRACKS = listOf(
            CuratedMv("hX9S94XsySk", "Hong Kong (feat. 王菲季候風)", "The Weeknd", "R&B", 305, "Moody", "Official Music Video", "4050205"),
            CuratedMv("4NRXx6U8ABQ", "Blinding Lights", "The Weeknd", "R&B", 202, "Night Drive", "After Hours", "4050205"),
            CuratedMv("kPa7bsKwL-c", "Die With A Smile", "Lady Gaga & Bruno Mars", "Pop", 249, "Romantic", "Official Music Video", "429675"),
            CuratedMv("ekr2nIex040", "APT.", "ROSÉ & Bruno Mars", "Pop", 173, "Upbeat", "rosie", "429675"),
            CuratedMv("XXYlFuWEuKI", "Save Your Tears", "The Weeknd", "R&B", 216, "Synth-Pop", "After Hours", "4050205"),
            CuratedMv("V9PVRfjEBTI", "BIRDS OF A FEATHER", "Billie Eilish", "Pop", 230, "Dreamy", "HIT ME HARD AND SOFT", "9635624"),
            CuratedMv("34Na4j8AVgA", "Starboy", "The Weeknd", "R&B", 273, "Hype", "Starboy", "4050205"),
            CuratedMv("yzTuBuRdAyA", "The Hills", "The Weeknd", "R&B", 265, "Moody", "Beauty Behind the Madness", "4050205"),
            CuratedMv("M4ZoCHID9GI", "Call Out My Name", "The Weeknd", "R&B", 227, "Emotional", "My Dear Melancholy,", "4050205"),
            CuratedMv("qFLhGq0060w", "I Feel It Coming", "The Weeknd", "R&B", 265, "Groovy", "Starboy", "4050205"),
            CuratedMv("5GJWxDKyk3A", "Happier Than Ever", "Billie Eilish", "Pop", 315, "Emotional", "Happier Than Ever", "9635624"),
            CuratedMv("DyDfgMOUjCI", "bad guy", "Billie Eilish", "Pop", 206, "Hype", "WHEN WE ALL FALL ASLEEP, WHERE DO WE GO?", "9635624"),
            CuratedMv("V1Pl8CzNzCw", "lovely", "Billie Eilish & Khalid", "Pop", 200, "Emotional", "13 Reasons Why", "9635624"),
            CuratedMv("eVli-tstM5E", "Espresso", "Sabrina Carpenter", "Pop", 175, "Sunny", "Short n' Sweet", "1176900"),
            CuratedMv("cF1Na4AIecM", "Please Please Please", "Sabrina Carpenter", "Pop", 263, "Pop", "Short n' Sweet", "1176900"),
            CuratedMv("H58vbez_m4E", "Not Like Us", "Kendrick Lamar", "Hip-Hop", 273, "Hype", "Official Music Video", "525046"),
            CuratedMv("SQnc1QibapQ", "Kill Bill", "SZA", "R&B", 275, "Moody", "SOS", "1193881"),
            CuratedMv("b1kbLwvqugk", "Anti-Hero", "Taylor Swift", "Pop", 309, "Upbeat", "Midnights", "12246"),
            CuratedMv("ic8j13piAhQ", "Cruel Summer", "Taylor Swift", "Pop", 179, "Upbeat", "Lover", "12246"),
            CuratedMv("ZmDBbnmKpqQ", "drivers license", "Olivia Rodrigo", "Pop", 247, "Emotional", "SOUR", "11152580"),
            CuratedMv("RlPNh_PBZb4", "vampire", "Olivia Rodrigo", "Pop", 244, "Dramatic", "GUTS", "11152580"),
            CuratedMv("KNtJGQkC-WI", "we can't be friends", "Ariana Grande", "Pop", 297, "Emotional", "eternal sunshine", "1562681"),
            CuratedMv("JSFG-IE8n_c", "like JENNIE", "JENNIE", "K-Pop", 190, "Hype", "Ruby", "1228100"),
            CuratedMv("dvgZkm1xWPE", "Viva La Vida", "Coldplay", "Rock", 242, "Upbeat", "Viva la Vida", "892"),
            CuratedMv("k4V3Mo61fJM", "Fix You", "Coldplay", "Rock", 296, "Emotional", "X&Y", "892"),
            CuratedMv("u6wOyMUs74I", "Eyes Closed", "Ed Sheeran", "Pop", 215, "Emotional", "-", "384236"),
            CuratedMv("JGwWNGJdvx8", "Shape of You", "Ed Sheeran", "Pop", 263, "Upbeat", "÷", "384236"),
            CuratedMv("2Vv-BfVoq4g", "Perfect", "Ed Sheeran", "Pop", 279, "Romantic", "÷", "384236"),
            CuratedMv("TdrL3QxjyVw", "Summertime Sadness", "Lana Del Rey", "Pop", 265, "Dreamy", "Born to Die"),
            CuratedMv("SlPhMPnQ58k", "Memories", "Maroon 5", "Pop", 195, "Nostalgic", "JORDI")
        )

        private val CURATED_BY_VIDEO_ID = (PH_MV_TRACKS + GLOBAL_MV_TRACKS).associateBy { it.videoId }
    }
}
