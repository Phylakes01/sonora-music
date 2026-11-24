package com.sonora.music.data.network

import com.sonora.music.model.Artist
import com.sonora.music.model.Playlist
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Unified Philippines (PH) & Global music catalog backed by Deezer public API,
 * Apple Music PH Chart RSS, and iTunes PH Search.
 */
class MusicCatalogApi(private val client: OkHttpClient = HttpClientProvider.client) {

    private val deezerSemaphore = Semaphore(4)
    private val rateLimitedUntilMs = AtomicLong(0L)

    private val trackListCache = ConcurrentHashMap<String, CacheEntry<List<Track>>>()
    private val artistListCache = ConcurrentHashMap<String, CacheEntry<List<Artist>>>()
    private val playlistListCache = ConcurrentHashMap<String, CacheEntry<List<Playlist>>>()

    suspend fun getPhTopTracks(limit: Int = 40): List<Track> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        cachedTracks("ph_top_$safeLimit") {
            coroutineScope {
                val primaryDef = async {
                    fetchDeezerTracks(
                        "/playlist/$PL_TOP_HITS_PH_2026/tracks?limit=$safeLimit",
                        defaultGenre = "OPM"
                    )
                }
                val appleRssDef = async { fetchApplePhChartEntries() }

                val primaryTracks = primaryDef.await()
                val appleEntries = appleRssDef.await()

                val combined = mutableListOf<Track>()
                combined.addAll(primaryTracks)

                if (combined.size < safeLimit && appleEntries.isNotEmpty()) {
                    val seenKeys = combined.mapTo(HashSet()) { normalizedTrackKey(it.title, it.artist) }
                    val missingEntries = appleEntries.filter { entry ->
                        normalizedTrackKey(entry.title, entry.artist) !in seenKeys
                    }
                    val toEnrichCount = if (combined.isEmpty()) {
                        safeLimit.coerceAtMost(15)
                    } else {
                        (safeLimit - combined.size).coerceAtMost(6)
                    }
                    val enriched = missingEntries
                        .take(toEnrichCount)
                        .chunked(3)
                        .flatMap { chunk ->
                            chunk.map { entry ->
                                async {
                                    searchItunesTracks("${entry.title} ${entry.artist}", limit = 1, defaultGenre = "OPM")
                                        .firstOrNull()
                                }
                            }.awaitAll().filterNotNull()
                        }
                    combined.addAll(enriched)
                }

                if (combined.size < 10) {
                    val dzPhChart = fetchDeezerTracks(
                        "/playlist/$PL_DEEZER_TOP_PH/tracks?limit=$safeLimit",
                        defaultGenre = "OPM"
                    )
                    combined.addAll(dzPhChart)
                }

                if (combined.isEmpty()) {
                    combined.addAll(searchItunesTracks("OPM hits Philippines", limit = safeLimit.coerceAtMost(25), defaultGenre = "OPM"))
                }

                deduplicateTracks(combined).take(safeLimit)
            }
        }
    }

    suspend fun getOpmTrendingTracks(limit: Int = 35): List<Track> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        cachedTracks("opm_trending_$safeLimit") {
            coroutineScope {
                val playlistDef = async {
                    fetchDeezerTracks(
                        "/playlist/$PL_OPM_VIRAL_HITS/tracks?limit=$safeLimit",
                        defaultGenre = "OPM"
                    )
                }
                val artistTrackDefs = OPM_SPOTLIGHT_ARTIST_IDS.map { artistId ->
                    async {
                        fetchDeezerTracks(
                            "/artist/$artistId/top?limit=6",
                            defaultGenre = "OPM"
                        )
                    }
                }

                val playlistTracks = playlistDef.await()
                val spotlightTracks = artistTrackDefs.awaitAll().flatten()

                val merged = interleaveTrackLists(spotlightTracks, playlistTracks)
                val finalTracks = if (merged.isNotEmpty()) {
                    merged
                } else {
                    searchItunesTracks("Cup of Joe BINI Dionela Maki", limit = safeLimit.coerceAtMost(25), defaultGenre = "OPM")
                }

                deduplicateTracks(finalTracks).take(safeLimit)
            }
        }
    }

    suspend fun getGlobalTopTracks(limit: Int = 40): List<Track> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        cachedTracks("global_top_$safeLimit") {
            coroutineScope {
                val worldwideDef = async {
                    fetchDeezerTracks(
                        "/playlist/$PL_GLOBAL_TOP_50/tracks?limit=$safeLimit",
                        defaultGenre = "Pop"
                    )
                }
                val chartDef = async {
                    fetchDeezerTracks(
                        "/chart/0/tracks?limit=$safeLimit",
                        defaultGenre = "Pop"
                    )
                }

                val worldwide = worldwideDef.await()
                val chart = chartDef.await()
                val merged = interleaveTrackLists(worldwide, chart)

                val finalTracks = if (merged.isNotEmpty()) {
                    merged
                } else {
                    searchItunesTracks("Taylor Swift Bruno Mars Ariana Grande", limit = safeLimit.coerceAtMost(25), defaultGenre = "Pop")
                }

                deduplicateTracks(finalTracks).take(safeLimit)
            }
        }
    }

    suspend fun getPhTrendingArtists(): List<Artist> = withContext(Dispatchers.IO) {
        cachedArtists("ph_artists") {
            refreshCuratedArtists(CURATED_PH_ARTISTS)
        }
    }

    suspend fun getGlobalTrendingArtists(): List<Artist> = withContext(Dispatchers.IO) {
        cachedArtists("global_artists") {
            refreshCuratedArtists(CURATED_GLOBAL_ARTISTS)
        }
    }

    suspend fun getGenreTracks(genre: String, limit: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        val cleanGenre = genre.trim()
        val safeLimit = limit.coerceIn(1, 100)
        if (cleanGenre.isEmpty() || cleanGenre.equals("All", ignoreCase = true)) {
            return@withContext getPhTopTracks(safeLimit)
        }

        val key = cleanGenre.lowercase(Locale.ROOT)
        cachedTracks("genre_${key}_$safeLimit") {
            when {
                key == "opm" -> getOpmTrendingTracks(safeLimit)
                key == "p-pop" || key == "ppop" -> {
                    val tracks = fetchMultiArtistTopTracks(listOf("320392011", "52961842", "122878032"), "P-Pop", perArtist = 8) +
                        searchTracks("BINI SB19 P-Pop", limit = 15)
                    deduplicateTracks(tracks).map { it.copy(genre = "P-Pop") }.take(safeLimit)
                }
                key == "pinoy hip-hop" || key == "pinoy rap" -> {
                    val tracks = fetchMultiArtistTopTracks(listOf("126789982", "11321714"), "Pinoy Hip-Hop", perArtist = 10) +
                        searchTracks("Hev Abi Skusta Clee Al James Shanti Dope", limit = 15)
                    deduplicateTracks(tracks).map { it.copy(genre = "Pinoy Hip-Hop") }.take(safeLimit)
                }
                key == "throwback opm" -> {
                    val tracks = fetchDeezerTracks("/playlist/$PL_THROWBACK_OPM_2000S/tracks?limit=$safeLimit", defaultGenre = "Throwback OPM")
                        .ifEmpty { searchTracks("Eraserheads Rivermaya Parokya ni Edgar", limit = safeLimit) }
                    deduplicateTracks(tracks).map { it.copy(genre = "Throwback OPM") }.take(safeLimit)
                }
                key == "pop" || key == "global pop" || key == "dance" -> {
                    val tracks = fetchMultiArtistTopTracks(listOf("12246", "1562681", "1176900", "11152580"), "Pop", perArtist = 6) +
                        getGlobalTopTracks(safeLimit)
                    deduplicateTracks(tracks).map { it.copy(genre = "Pop") }.take(safeLimit)
                }
                key == "r&b" || key == "r&b/soul" || key == "soul" || key == "jazz" -> {
                    val tracks = fetchMultiArtistTopTracks(listOf("69165172", "55640202", "4050205"), "R&B", perArtist = 8) +
                        searchTracks("Dionela Arthur Nery SZA R&B", limit = 12)
                    deduplicateTracks(tracks).map { it.copy(genre = "R&B") }.take(safeLimit)
                }
                key == "hip-hop" || key == "hip-hop/rap" || key == "rap" || key == "trap" -> {
                    val tracks = fetchMultiArtistTopTracks(listOf("525046", "126789982"), "Hip-Hop", perArtist = 10) +
                        searchTracks("Kendrick Lamar Drake Travis Scott", limit = 15)
                    deduplicateTracks(tracks).map { it.copy(genre = "Hip-Hop") }.take(safeLimit)
                }
                key == "k-pop" || key == "kpop" -> {
                    val tracks = fetchMultiArtistTopTracks(listOf("1228100"), "K-Pop", perArtist = 10) +
                        searchTracks("BLACKPINK NewJeans aespa Jungkook", limit = 20)
                    deduplicateTracks(tracks).map { it.copy(genre = "K-Pop") }.take(safeLimit)
                }
                key == "rock" || key == "indie" || key == "alternative" -> {
                    val tracks = fetchMultiArtistTopTracks(listOf("11152604", "12450156", "1407775"), "Rock", perArtist = 8) +
                        searchTracks("IV Of Spades Lola Amour Eraserheads", limit = 15)
                    deduplicateTracks(tracks).map { it.copy(genre = "Rock") }.take(safeLimit)
                }
                key == "chill" || key == "lo-fi" || key == "lofi" || key == "ambient" || key == "acoustic" || key == "folk" -> {
                    val tracks = fetchMultiArtistTopTracks(listOf("11072592", "242188", "11819305", "6248262"), "Chill", perArtist = 6) +
                        searchTracks("chill acoustic OPM lo-fi", limit = 12)
                    deduplicateTracks(tracks).map { it.copy(genre = "Chill") }.take(safeLimit)
                }
                key == "electronic" || key == "house" || key == "techno" || key == "synthwave" -> {
                    searchTracks("Calvin Harris Fred again electronic dance", limit = safeLimit)
                        .ifEmpty { getGlobalTopTracks(safeLimit) }
                        .map { it.copy(genre = cleanGenre) }
                }
                else -> {
                    searchTracks(cleanGenre, limit = safeLimit)
                        .ifEmpty { getPhTopTracks(safeLimit) }
                }
            }
        }
    }

    suspend fun getFeaturedPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        cachedPlaylists("featured_playlists") {
            coroutineScope {
                CURATED_PLAYLISTS.chunked(3).flatMap { chunk ->
                    chunk.map { seed ->
                        async {
                            val root = fetchDeezerJson("/playlist/${seed.rawId}?limit=1")
                            if (root != null) {
                                val parsed = parseDeezerPlaylist(root, fallback = seed)
                                parsed ?: seed.toPlaylist()
                            } else {
                                seed.toPlaylist()
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Track> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.trim().removePrefix(PLAYLIST_ID_PREFIX)
        if (cleanId.isEmpty()) return@withContext emptyList()
        cachedTracks("pl_tracks_$cleanId") {
            val tracks = fetchDeezerTracks(
                "/playlist/${encodePathSegment(cleanId)}/tracks?limit=50",
                defaultGenre = playlistDefaultGenre(cleanId)
            )
            deduplicateTracks(tracks)
        }
    }

    suspend fun getArtistTopTracks(
        artistId: String,
        artistName: String = "",
        limit: Int = 25
    ): List<Track> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 50)
        val cleanId = normalizeDeezerArtistId(artistId)
        val cleanName = artistName.trim()

        if (cleanId.isNotEmpty() && cleanId.all { it.isDigit() }) {
            val cached = cachedTracks("art_top_${cleanId}_$safeLimit") {
                fetchDeezerTracks("/artist/$cleanId/top?limit=$safeLimit", defaultGenre = artistDefaultGenre(cleanId))
            }
            if (cached.isNotEmpty()) return@withContext cached
        }

        if (cleanName.isNotEmpty()) {
            return@withContext searchTracks(cleanName, limit = safeLimit)
        }
        emptyList()
    }

    suspend fun getRelatedArtists(artistId: String): List<Artist> = withContext(Dispatchers.IO) {
        val cleanId = normalizeDeezerArtistId(artistId)
        if (cleanId.isNotEmpty() && cleanId.all { it.isDigit() }) {
            val root = fetchDeezerJson("/artist/$cleanId/related?limit=15")
            val array = root?.optJSONArray("data")
            if (array != null) {
                val parsed = buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        parseDeezerArtist(obj)?.let(::add)
                    }
                }
                if (parsed.isNotEmpty()) return@withContext parsed
            }
        }
        val curatedPool = if (CURATED_GLOBAL_ARTISTS.any { it.id == cleanId }) {
            CURATED_GLOBAL_ARTISTS
        } else {
            CURATED_PH_ARTISTS
        }
        curatedPool.filterNot { it.id == cleanId }.take(10).map { it.toArtist() }
    }

    suspend fun searchTracks(query: String, limit: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, 50)

        val deezerTracks = fetchDeezerTracks(
            "/search/track?q=${encodeUrlParam(trimmed)}&order=RANKING&limit=$safeLimit",
            defaultGenre = "Music"
        )
        if (deezerTracks.isNotEmpty()) {
            return@withContext deduplicateTracks(deezerTracks).take(safeLimit)
        }

        deduplicateTracks(searchItunesTracks(trimmed, limit = safeLimit, defaultGenre = "Music")).take(safeLimit)
    }

    suspend fun searchArtists(query: String, limit: Int = 15): List<Artist> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, 50)
        val fetchLimit = (safeLimit * 2).coerceIn(15, 50)

        val root = fetchDeezerJson("/search/artist?q=${encodeUrlParam(trimmed)}&limit=$fetchLimit")
        val data = root?.optJSONArray("data")
        if (data != null) {
            val parsed = buildList {
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    parseDeezerArtist(obj)?.let(::add)
                }
            }
            if (parsed.isNotEmpty()) {
                return@withContext parsed
                    .sortedByDescending { it.followerCount }
                    .take(safeLimit)
            }
        }

        (CURATED_PH_ARTISTS + CURATED_GLOBAL_ARTISTS)
            .filter { it.name.contains(trimmed, ignoreCase = true) }
            .sortedByDescending { it.followerCount }
            .take(safeLimit)
            .map { it.toArtist() }
    }

    suspend fun searchPlaylists(query: String, limit: Int = 15): List<Playlist> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, 50)

        val root = fetchDeezerJson("/search/playlist?q=${encodeUrlParam(trimmed)}&limit=$safeLimit")
        val data = root?.optJSONArray("data")
        if (data != null) {
            val parsed = buildList {
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    parseDeezerPlaylist(obj)?.let(::add)
                }
            }
            if (parsed.isNotEmpty()) return@withContext parsed.take(safeLimit)
        }

        CURATED_PLAYLISTS
            .filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                    it.description.contains(trimmed, ignoreCase = true)
            }
            .take(safeLimit)
            .map { it.toPlaylist() }
    }

    suspend fun resolveFreshStreamUrl(track: Track): String = withContext(Dispatchers.IO) {
        if (track.localUri != null && track.localUri.isNotBlank()) {
            return@withContext track.localUri
        }
        if (track.isLive ||
            track.source == TrackSource.LOCAL ||
            track.source == TrackSource.RADIO ||
            track.source == TrackSource.PODCAST ||
            track.source == TrackSource.AUDIOBOOK
        ) {
            return@withContext track.streamUrl
        }

        if (track.id.startsWith(TRACK_ID_PREFIX) || track.source == TrackSource.DEEZER) {
            val rawId = track.id.removePrefix(TRACK_ID_PREFIX)
            if (rawId.isNotEmpty() && rawId.all { it.isDigit() }) {
                val root = fetchDeezerJson("/track/$rawId")
                val freshPreview = root?.optCleanString("preview").orEmpty()
                if (freshPreview.startsWith("http")) {
                    return@withContext freshPreview
                }
            }
        }

        val query = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isNotBlank()) {
            val itunesFallback = searchItunesTracks(query, limit = 3, defaultGenre = track.genre)
                .firstOrNull { it.streamUrl.startsWith("http") }
            if (itunesFallback != null) {
                return@withContext itunesFallback.streamUrl
            }
        }

        track.streamUrl
    }

    private suspend fun refreshCuratedArtists(seeds: List<CuratedArtistSeed>): List<Artist> = coroutineScope {
        seeds.chunked(4).flatMap { chunk ->
            chunk.map { seed ->
                async {
                    val root = fetchDeezerJson("/artist/${seed.id}")
                    if (root != null) {
                        parseDeezerArtist(root, fallback = seed) ?: seed.toArtist()
                    } else {
                        seed.toArtist()
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun fetchMultiArtistTopTracks(
        artistIds: List<String>,
        genre: String,
        perArtist: Int
    ): List<Track> = coroutineScope {
        artistIds.chunked(3).flatMap { chunk ->
            chunk.map { id ->
                async { fetchDeezerTracks("/artist/$id/top?limit=$perArtist", defaultGenre = genre) }
            }.awaitAll()
        }.flatten()
    }

    private suspend fun fetchDeezerTracks(pathAndQuery: String, defaultGenre: String): List<Track> {
        val root = fetchDeezerJson(pathAndQuery) ?: return emptyList()
        val data = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                parseDeezerTrack(item, defaultGenre)?.let(::add)
            }
        }
    }

    private suspend fun fetchApplePhChartEntries(): List<AppleChartEntry> {
        val body = client.getText(APPLE_PH_CHART_URL) ?: return emptyList()
        return try {
            val results = JSONObject(body)
                .optJSONObject("feed")
                ?.optJSONArray("results")
                ?: return emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val title = item.optCleanString("name")
                    val artist = item.optCleanString("artistName")
                    if (title.isNotEmpty() && artist.isNotEmpty()) {
                        add(AppleChartEntry(title = title, artist = artist))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun searchItunesTracks(
        query: String,
        limit: Int = 20,
        defaultGenre: String = "Music"
    ): List<Track> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val safeLimit = limit.coerceIn(1, 50)
        val url = "$ITUNES_PH_SEARCH_URL&term=${encodeUrlParam(trimmed)}&limit=$safeLimit"
        val body = client.getText(url) ?: return emptyList()
        return try {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    parseItunesTrack(item, defaultGenre)?.let(::add)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun fetchDeezerJson(
        pathAndQuery: String,
        allowRetryOnRateLimit: Boolean = true
    ): JSONObject? {
        val url = if (pathAndQuery.startsWith("http")) pathAndQuery else "$DEEZER_BASE$pathAndQuery"
        return deezerSemaphore.withPermit {
            for (attempt in 0..1) {
                val (code, body) = client.getResponse(url)
                if (code == 429) {
                    if (attempt == 0 && allowRetryOnRateLimit && !isInRateLimitCooldown()) {
                        rateLimitedUntilMs.set(System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS)
                        delay(300)
                        continue
                    }
                    return@withPermit null
                }
                if (code !in 200..299 || body.isNullOrBlank()) {
                    return@withPermit null
                }
                val root = try {
                    JSONObject(body)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    return@withPermit null
                }
                val errObj = root.optJSONObject("error")
                if (errObj != null) {
                    val errCode = errObj.optInt("code", -1)
                    val errMsg = errObj.optCleanString("message")
                    val isQuota = errCode == 4 ||
                        errMsg.contains("quota", ignoreCase = true) ||
                        errMsg.contains("limit", ignoreCase = true)
                    if (isQuota && attempt == 0 && allowRetryOnRateLimit && !isInRateLimitCooldown()) {
                        rateLimitedUntilMs.set(System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS)
                        delay(300)
                        continue
                    }
                    return@withPermit null
                }
                return@withPermit root
            }
            null
        }
    }

    private fun isInRateLimitCooldown(): Boolean = System.currentTimeMillis() < rateLimitedUntilMs.get()

    internal fun parseDeezerTrack(obj: JSONObject, defaultGenre: String = "Music"): Track? {
        val rawId = obj.optCleanString("id")
        val title = obj.optCleanString("title").ifBlank { obj.optCleanString("title_short") }
        val preview = obj.optCleanString("preview")
        if (rawId.isEmpty() || rawId == "0" || title.isEmpty() || !preview.startsWith("http")) {
            return null
        }

        val artistObj = obj.optJSONObject("artist")
        val artistName = artistObj?.optCleanString("name").orEmpty()
        val artistId = artistObj?.optCleanString("id").orEmpty()
        val artistPic = artistObj?.optCleanString("picture_big")?.ifBlank { null }
            ?: artistObj?.optCleanString("picture_medium")?.ifBlank { null }

        val albumObj = obj.optJSONObject("album")
        val albumTitle = albumObj?.optCleanString("title").orEmpty()
        val coverBig = albumObj?.optCleanString("cover_big")?.ifBlank { null }
        val coverXl = albumObj?.optCleanString("cover_xl")?.ifBlank { null }
        val coverMed = albumObj?.optCleanString("cover_medium")?.ifBlank { null }
        val artworkUrl = coverBig ?: coverXl ?: coverMed ?: artistPic
        val mirrors = listOfNotNull(coverXl, coverBig, coverMed, artistPic)
            .distinct()
            .filter { it != artworkUrl }

        val link = obj.optCleanString("link").ifBlank { "https://www.deezer.com/track/$rawId" }

        return Track(
            id = "$TRACK_ID_PREFIX$rawId",
            title = title,
            artist = artistName,
            artistId = artistId,
            album = albumTitle,
            genre = defaultGenre.ifBlank { "Music" },
            durationSec = obj.optInt("duration", 0).coerceAtLeast(0),
            artworkUrl = artworkUrl,
            artworkMirrors = mirrors,
            streamUrl = preview,
            source = TrackSource.DEEZER,
            isLive = false,
            isSmartPick = false,
            releaseDate = albumObj?.optCleanString("release_date").orEmpty(),
            playCount = obj.optLong("rank", 0L).coerceAtLeast(0L),
            externalUrl = link,
            isPreviewOnly = true
        )
    }

    internal fun parseItunesTrack(obj: JSONObject, defaultGenre: String = "Music"): Track? {
        val rawId = obj.optCleanString("trackId")
        val title = obj.optCleanString("trackName").ifBlank { obj.optCleanString("trackCensoredName") }
        val preview = obj.optCleanString("previewUrl")
        if (rawId.isEmpty() || rawId == "0" || title.isEmpty() || !preview.startsWith("http")) {
            return null
        }

        val rawArt100 = obj.optCleanString("artworkUrl100")
            .ifBlank { obj.optCleanString("artworkUrl60") }
        val upgradedArt = upgradeItunesArtwork(rawArt100)
        val mirrors = listOfNotNull(rawArt100.takeIf { it.isNotBlank() && it != upgradedArt })
        val rawArtistId = obj.optCleanString("artistId")
        val artistId = if (rawArtistId.isNotEmpty() && rawArtistId != "0") "it_art_$rawArtistId" else ""
        val genre = obj.optCleanString("primaryGenreName").ifBlank { defaultGenre }.ifBlank { "Music" }
        val durationSec = (obj.optLong("trackTimeMillis", 0L) / 1000L).toInt().coerceAtLeast(0)
        val externalUrl = obj.optCleanString("trackViewUrl")
            .ifBlank { obj.optCleanString("collectionViewUrl") }

        return Track(
            id = "$ITUNES_ID_PREFIX$rawId",
            title = title,
            artist = obj.optCleanString("artistName"),
            artistId = artistId,
            album = obj.optCleanString("collectionName"),
            genre = genre,
            durationSec = durationSec,
            artworkUrl = upgradedArt,
            artworkMirrors = mirrors,
            streamUrl = preview,
            source = TrackSource.ITUNES,
            isLive = false,
            isSmartPick = false,
            releaseDate = obj.optCleanString("releaseDate").take(10),
            externalUrl = externalUrl,
            isPreviewOnly = true
        )
    }

    private fun parseDeezerArtist(obj: JSONObject, fallback: CuratedArtistSeed? = null): Artist? {
        val id = obj.optCleanString("id")
        val name = obj.optCleanString("name").ifBlank { fallback?.name.orEmpty() }
        if (id.isEmpty() || id == "0" || name.isEmpty()) return null

        val avatarUrl = obj.optCleanString("picture_big").ifBlank { null }
            ?: obj.optCleanString("picture_xl").ifBlank { null }
            ?: obj.optCleanString("picture_medium").ifBlank { null }
            ?: fallback?.avatarUrl

        val fans = obj.optLong("nb_fan", 0L).coerceAtLeast(0L)
            .let { if (it > 0L) it else fallback?.followerCount ?: 0L }

        return Artist(
            id = id,
            name = fallback?.name ?: name,
            handle = fallback?.handle ?: "",
            avatarUrl = avatarUrl,
            followerCount = fans,
            trackCount = obj.optInt("nb_album", 0).coerceAtLeast(0),
            bio = fallback?.bio.orEmpty()
        )
    }

    private fun parseDeezerPlaylist(obj: JSONObject, fallback: CuratedPlaylistSeed? = null): Playlist? {
        val rawId = obj.optCleanString("id")
        if (rawId.isEmpty() || rawId == "0") return null
        val name = fallback?.name ?: obj.optCleanString("title").ifBlank { obj.optCleanString("name") }
        if (name.isEmpty()) return null

        val artworkUrl = obj.optCleanString("picture_big").ifBlank { null }
            ?: obj.optCleanString("picture_xl").ifBlank { null }
            ?: obj.optCleanString("picture_medium").ifBlank { null }
            ?: fallback?.artworkUrl

        val creatorName = obj.optJSONObject("creator")?.optCleanString("name")?.ifBlank { null }
            ?: obj.optJSONObject("user")?.optCleanString("name")?.ifBlank { null }
            ?: fallback?.authorName
            ?: "Deezer"

        val description = fallback?.description
            ?: obj.optCleanString("description")

        val playlistId = if (rawId.startsWith(PLAYLIST_ID_PREFIX)) rawId else "$PLAYLIST_ID_PREFIX$rawId"
        val (colorStart, colorEnd) = coverPaletteForKey(playlistId)

        return Playlist(
            id = playlistId,
            name = name,
            description = description,
            trackIds = emptyList(),
            tracks = emptyList(),
            coverColorStart = colorStart,
            coverColorEnd = colorEnd,
            artworkUrl = artworkUrl,
            isUserCreated = false,
            isSystemMix = false,
            authorName = creatorName
        )
    }

    private fun upgradeItunesArtwork(rawUrl: String): String? {
        if (rawUrl.isBlank()) return null
        return rawUrl
            .replace("100x100bb", "600x600bb")
            .replace("60x60bb", "600x600bb")
            .replace("30x30bb", "600x600bb")
    }

    private fun normalizeDeezerArtistId(rawId: String): String {
        val trimmed = rawId.trim()
        return when {
            trimmed.startsWith("dz_art_") -> trimmed.removePrefix("dz_art_")
            trimmed.startsWith("dz_") -> trimmed.removePrefix("dz_")
            else -> trimmed
        }
    }

    private fun playlistDefaultGenre(rawPlaylistId: String): String = when (rawPlaylistId) {
        PL_TOP_HITS_PH_2026, PL_OPM_VIRAL_HITS, PL_DEEZER_TOP_PH -> "OPM"
        PL_THROWBACK_OPM_2000S -> "Throwback OPM"
        PL_GLOBAL_TOP_50 -> "Pop"
        else -> "Music"
    }

    private fun artistDefaultGenre(rawArtistId: String): String = when {
        rawArtistId in setOf("320392011", "52961842") -> "P-Pop"
        rawArtistId in setOf("126789982", "11321714") -> "Pinoy Hip-Hop"
        CURATED_PH_ARTISTS.any { it.id == rawArtistId } -> "OPM"
        rawArtistId == "1228100" -> "K-Pop"
        rawArtistId == "525046" -> "Hip-Hop"
        else -> "Pop"
    }

    private fun interleaveTrackLists(first: List<Track>, second: List<Track>): List<Track> {
        val out = ArrayList<Track>(first.size + second.size)
        val maxLen = maxOf(first.size, second.size)
        for (i in 0 until maxLen) {
            if (i < first.size) out.add(first[i])
            if (i < second.size) out.add(second[i])
        }
        return out
    }

    private fun deduplicateTracks(tracks: List<Track>): List<Track> {
        val seenIds = HashSet<String>(tracks.size)
        val seenTitleArtist = HashSet<String>(tracks.size)
        return buildList {
            for (t in tracks) {
                val key = normalizedTrackKey(t.title, t.artist)
                if (seenIds.add(t.id) && seenTitleArtist.add(key)) {
                    add(t)
                }
            }
        }
    }

    private fun normalizedTrackKey(title: String, artist: String): String =
        "${title.lowercase(Locale.ROOT).trim()}|${artist.lowercase(Locale.ROOT).trim()}"

    private suspend fun cachedTracks(key: String, loader: suspend () -> List<Track>): List<Track> {
        val now = System.currentTimeMillis()
        val cached = trackListCache[key]
        if (cached != null && now - cached.timestampMs < CACHE_TTL_MS && cached.value.isNotEmpty()) {
            return cached.value
        }
        val loaded = loader()
        if (loaded.isNotEmpty()) {
            trackListCache[key] = CacheEntry(loaded, now)
        }
        return loaded
    }

    private suspend fun cachedArtists(key: String, loader: suspend () -> List<Artist>): List<Artist> {
        val now = System.currentTimeMillis()
        val cached = artistListCache[key]
        if (cached != null && now - cached.timestampMs < CACHE_TTL_MS && cached.value.isNotEmpty()) {
            return cached.value
        }
        val loaded = loader()
        if (loaded.isNotEmpty()) {
            artistListCache[key] = CacheEntry(loaded, now)
        }
        return loaded
    }

    private suspend fun cachedPlaylists(key: String, loader: suspend () -> List<Playlist>): List<Playlist> {
        val now = System.currentTimeMillis()
        val cached = playlistListCache[key]
        if (cached != null && now - cached.timestampMs < CACHE_TTL_MS && cached.value.isNotEmpty()) {
            return cached.value
        }
        val loaded = loader()
        if (loaded.isNotEmpty()) {
            playlistListCache[key] = CacheEntry(loaded, now)
        }
        return loaded
    }

    private data class CacheEntry<T>(
        val value: T,
        val timestampMs: Long
    )

    private data class AppleChartEntry(
        val title: String,
        val artist: String
    )

    private data class CuratedArtistSeed(
        val id: String,
        val name: String,
        val avatarUrl: String,
        val followerCount: Long,
        val bio: String = "",
        val handle: String = ""
    ) {
        fun toArtist(): Artist = Artist(
            id = id,
            name = name,
            handle = handle,
            avatarUrl = avatarUrl,
            followerCount = followerCount,
            bio = bio
        )
    }

    private data class CuratedPlaylistSeed(
        val rawId: String,
        val name: String,
        val description: String,
        val artworkUrl: String,
        val authorName: String
    ) {
        fun toPlaylist(): Playlist {
            val playlistId = "$PLAYLIST_ID_PREFIX$rawId"
            val (colorStart, colorEnd) = coverPaletteForKey(playlistId)
            return Playlist(
                id = playlistId,
                name = name,
                description = description,
                trackIds = emptyList(),
                tracks = emptyList(),
                coverColorStart = colorStart,
                coverColorEnd = colorEnd,
                artworkUrl = artworkUrl,
                isUserCreated = false,
                isSystemMix = false,
                authorName = authorName
            )
        }
    }

    companion object {
        private const val DEEZER_BASE = "https://api.deezer.com"
        private const val APPLE_PH_CHART_URL = "https://rss.marketingtools.apple.com/api/v2/ph/music/most-played/25/songs.json"
        private const val ITUNES_PH_SEARCH_URL = "https://itunes.apple.com/search?country=PH&media=music&entity=song"

        private const val TRACK_ID_PREFIX = "dz_"
        private const val ITUNES_ID_PREFIX = "it_"
        private const val PLAYLIST_ID_PREFIX = "dz_pl_"

        private const val PL_TOP_HITS_PH_2026 = "15507256221"
        private const val PL_OPM_VIRAL_HITS = "13461654563"
        private const val PL_DEEZER_TOP_PH = "1362518895"
        private const val PL_GLOBAL_TOP_50 = "3155776842"
        private const val PL_THROWBACK_OPM_2000S = "11970710661"

        private const val CACHE_TTL_MS = 60_000L
        private const val RATE_LIMIT_COOLDOWN_MS = 3_000L

        private val OPM_SPOTLIGHT_ARTIST_IDS = listOf("60687062", "320392011", "69165172", "87317692")

        private val CURATED_PH_ARTISTS = listOf(
            CuratedArtistSeed("60687062", "Cup of Joe", "https://cdn-images.dzcdn.net/images/artist/72899d9b602142ed374df10a44e446c9/500x500-000000-80-0-0.jpg", 5537L, "OPM • Baguio"),
            CuratedArtistSeed("320392011", "BINI", "https://cdn-images.dzcdn.net/images/artist/351a56934b2d4fcc027f5367062f2834/500x500-000000-80-0-0.jpg", 1174L, "P-Pop • Philippines"),
            CuratedArtistSeed("69165172", "Dionela", "https://cdn-images.dzcdn.net/images/artist/3202f777283e87f7060dc1f0b971d0aa/500x500-000000-80-0-0.jpg", 3072L, "OPM R&B"),
            CuratedArtistSeed("7189292", "TJ Monterde", "https://cdn-images.dzcdn.net/images/artist/e7fb3f8f6746e76a3ba2b23d12485314/500x500-000000-80-0-0.jpg", 24342L, "OPM Acoustic"),
            CuratedArtistSeed("55640202", "Arthur Nery", "https://cdn-images.dzcdn.net/images/artist/516f55a94d77195f3d682afd954354b0/500x500-000000-80-0-0.jpg", 15928L, "OPM Soul & R&B"),
            CuratedArtistSeed("87317692", "Maki", "https://cdn-images.dzcdn.net/images/artist/0f1bcf1668638106bdf3444ad7567975/500x500-000000-80-0-0.jpg", 2996L, "OPM Pop"),
            CuratedArtistSeed("52961842", "SB19", "https://cdn-images.dzcdn.net/images/artist/270ce2686651cf5ea786d9407c200658/500x500-000000-80-0-0.jpg", 11686L, "P-Pop"),
            CuratedArtistSeed("11819305", "Ben&Ben", "https://cdn-images.dzcdn.net/images/artist/ed13e17bb0f432cf5c1d233f6ccb1801/500x500-000000-80-0-0.jpg", 79039L, "OPM Folk-Pop"),
            CuratedArtistSeed("11152604", "IV Of Spades", "https://cdn-images.dzcdn.net/images/artist/2add086f7c58984e3630eb70f84b145d/500x500-000000-80-0-0.jpg", 33657L, "OPM Funk-Rock"),
            CuratedArtistSeed("126789982", "Hev Abi", "https://cdn-images.dzcdn.net/images/artist/851012576628485608c3875389e7061b/500x500-000000-80-0-0.jpg", 3103L, "Pinoy Hip-Hop"),
            CuratedArtistSeed("12450156", "Lola Amour", "https://cdn-images.dzcdn.net/images/artist/f6aa915219964fd4dc899f5484e63995/500x500-000000-80-0-0.jpg", 5115L, "OPM Funk-Rock"),
            CuratedArtistSeed("6248262", "Moira Dela Torre", "https://cdn-images.dzcdn.net/images/artist/9fe17bc9f381b4e16b29ae8f787fbba9/500x500-000000-80-0-0.jpg", 48910L, "OPM Ballad"),
            CuratedArtistSeed("11072592", "Zack Tabudlo", "https://cdn-images.dzcdn.net/images/artist/640956e797b8fb66a4b3c715a44901f5/500x500-000000-80-0-0.jpg", 16060L, "OPM Pop & R&B"),
            CuratedArtistSeed("1407775", "juan karlos", "https://cdn-images.dzcdn.net/images/artist/835d9492299771f3b4ecde16524e3dfa/500x500-000000-80-0-0.jpg", 13925L, "OPM Rock"),
            CuratedArtistSeed("11321714", "Skusta Clee", "https://cdn-images.dzcdn.net/images/artist/133fcacfdfa752c1be0b00a5e10864db/500x500-000000-80-0-0.jpg", 19740L, "Pinoy Hip-Hop"),
            CuratedArtistSeed("109893432", "Rob Deniel", "https://cdn-images.dzcdn.net/images/artist/4f29962d383628efb4cdc545e81bf63b/500x500-000000-80-0-0.jpg", 1537L, "OPM Retro Pop"),
            CuratedArtistSeed("179158087", "SunKissed Lola", "https://cdn-images.dzcdn.net/images/artist/1e626c72de5dca3bf15cbeff4100eb8e/500x500-000000-80-0-0.jpg", 2139L, "OPM Indie"),
            CuratedArtistSeed("242188", "Adie", "https://cdn-images.dzcdn.net/images/artist/7ed601f9448d2df48f59f923eb450c0f/500x500-000000-80-0-0.jpg", 8279L, "OPM Acoustic")
        )

        private val CURATED_GLOBAL_ARTISTS = listOf(
            CuratedArtistSeed("12246", "Taylor Swift", "https://cdn-images.dzcdn.net/images/artist/cc2495870fe1a792ad0cdb05501ad5ec/500x500-000000-80-0-0.jpg", 12795217L, "Global Pop"),
            CuratedArtistSeed("429675", "Bruno Mars", "https://cdn-images.dzcdn.net/images/artist/90f0b5b11df4f87ee878f38569b5995b/500x500-000000-80-0-0.jpg", 12882907L, "Pop & R&B"),
            CuratedArtistSeed("1562681", "Ariana Grande", "https://cdn-images.dzcdn.net/images/artist/721d8fab84b315502de422b8d0901509/500x500-000000-80-0-0.jpg", 13480590L, "Pop & R&B"),
            CuratedArtistSeed("11152580", "Olivia Rodrigo", "https://cdn-images.dzcdn.net/images/artist/2c9e480317183c037eaebcd7ba96daf4/500x500-000000-80-0-0.jpg", 1865113L, "Pop-Rock"),
            CuratedArtistSeed("4050205", "The Weeknd", "https://cdn-images.dzcdn.net/images/artist/581693b4724a7fcfa754455101e13a44/500x500-000000-80-0-0.jpg", 14822774L, "R&B & Synth-Pop"),
            CuratedArtistSeed("1176900", "Sabrina Carpenter", "https://cdn-images.dzcdn.net/images/artist/4a9cdc7737e2a0e59b4917b47884b859/500x500-000000-80-0-0.jpg", 1847205L, "Pop"),
            CuratedArtistSeed("9635624", "Billie Eilish", "https://cdn-images.dzcdn.net/images/artist/8eab1a9a644889aabaca1e193e05f984/500x500-000000-80-0-0.jpg", 9319236L, "Alt-Pop"),
            CuratedArtistSeed("75491", "Lady Gaga", "https://cdn-images.dzcdn.net/images/artist/7565262f7661b0d762621a8d69ba6f49/500x500-000000-80-0-0.jpg", 9373994L, "Pop"),
            CuratedArtistSeed("1228100", "JENNIE", "https://cdn-images.dzcdn.net/images/artist/944fde7e413c745dcb3534c9d057bcf1/500x500-000000-80-0-0.jpg", 656558L, "K-Pop"),
            CuratedArtistSeed("525046", "Kendrick Lamar", "https://cdn-images.dzcdn.net/images/artist/be0a7c550567f4af0ed202d7235b74d6/500x500-000000-80-0-0.jpg", 6211279L, "Hip-Hop"),
            CuratedArtistSeed("13817211", "Tate McRae", "https://cdn-images.dzcdn.net/images/artist/b12c2090606779c0ba39eac6e3d2708e/500x500-000000-80-0-0.jpg", 627797L, "Pop")
        )

        private val CURATED_PLAYLISTS = listOf(
            CuratedPlaylistSeed(
                rawId = PL_TOP_HITS_PH_2026,
                name = "Top Hits Philippines 2026",
                description = "Hottest tracks trending across the Philippines.",
                artworkUrl = "https://cdn-images.dzcdn.net/images/cover/f4c02f9d1ca510997e29370ee644eb96-ea05a09f71c650836e812c579134a34e-b755e304f6639c3a231f9f67832850a2-eb54a3a72b036ca5f45d3358eb40181b/500x500-000000-80-0-0.jpg",
                authorName = "Philippines Charts"
            ),
            CuratedPlaylistSeed(
                rawId = PL_OPM_VIRAL_HITS,
                name = "OPM Viral & Top Hits",
                description = "Essential OPM, P-Pop, and viral Filipino favorites.",
                artworkUrl = "https://cdn-images.dzcdn.net/images/cover/a9ac1c2b00fc5cb5970892e2b460bcb8-eddde5230b09c97f5e222c2fa975d421-8e41af01b219dcbdd33a4244701ed2b3-579fb6c0f175d98e767ac94bce880348/500x500-000000-80-0-0.jpg",
                authorName = "OPM Select"
            ),
            CuratedPlaylistSeed(
                rawId = PL_DEEZER_TOP_PH,
                name = "Deezer Top Philippines",
                description = "Daily top streamed chart in the Philippines.",
                artworkUrl = "https://cdn-images.dzcdn.net/images/playlist/2d89a884d5c418fb217f461099f57095/500x500-000000-80-0-0.jpg",
                authorName = "Deezer Charts"
            ),
            CuratedPlaylistSeed(
                rawId = PL_GLOBAL_TOP_50,
                name = "Global Top 50",
                description = "Biggest international tracks right now.",
                artworkUrl = "https://cdn-images.dzcdn.net/images/playlist/0b92c2f9e94a1a3650f0de769c4fc07c/500x500-000000-80-0-0.jpg",
                authorName = "Deezer Charts"
            ),
            CuratedPlaylistSeed(
                rawId = PL_THROWBACK_OPM_2000S,
                name = "Throwback OPM 2000s",
                description = "Classic Pinoy bands and nostalgic OPM anthems.",
                artworkUrl = "https://cdn-images.dzcdn.net/images/cover/31dad1e3d13066d992376127114fb9d3-fe61fff7300ef02ce19d766c7e58ad26-c93c039515c9f48057d8472adc7b982f-db75d15f92b2a98153fa0205a2f99ec3/500x500-000000-80-0-0.jpg",
                authorName = "OPM Classics"
            )
        )
    }
}
