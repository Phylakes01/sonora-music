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
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Unified Philippines (PH) & Global full-length music catalog.
 * Every returned song is a full-length Audius stream (`source = TrackSource.AUDIUS`,
 * `durationSec >= 90`, `isPreviewOnly = false`). Deezer and Apple/iTunes metadata
 * are used strictly for chart discovery and cover artwork — 30-second audio preview
 * URLs are never stored or returned.
 */
class MusicCatalogApi(private val client: OkHttpClient = HttpClientProvider.client) {

    private val fullAudioResolver = FullAudioResolver(client)
    val youTubeApi = YouTubeCatalogApi(client)
    private val deezerSemaphore = Semaphore(4)
    private val rateLimitedUntilMs = AtomicLong(0L)

    private val trackListCache = ConcurrentHashMap<String, CacheEntry<List<Track>>>()
    private val artistListCache = ConcurrentHashMap<String, CacheEntry<List<Artist>>>()
    private val playlistListCache = ConcurrentHashMap<String, CacheEntry<List<Playlist>>>()
    private val seedArtworkCache = ConcurrentHashMap<String, HydratedMeta>()
    private val trackMetaByKeyCache = ConcurrentHashMap<String, HydratedMeta>()

    suspend fun getPhTopTracks(limit: Int = 40): List<Track> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        cachedTracks("ph_top_$safeLimit") {
            coroutineScope {
                val primaryDef = async {
                    fetchDeezerChartCandidates(
                        "/playlist/$PL_TOP_HITS_PH_2026/tracks?limit=$safeLimit",
                        defaultGenre = "OPM"
                    )
                }
                val viralDef = async {
                    fetchDeezerChartCandidates(
                        "/playlist/$PL_OPM_VIRAL_HITS/tracks?limit=25",
                        defaultGenre = "OPM"
                    )
                }
                val appleRssDef = async { fetchApplePhChartEntries() }
                val audiusBatchDef = async { fetchAudiusSeedMetadata(PH_SEED_TRACKS) }

                val primaryCandidates = primaryDef.await()
                val viralCandidates = viralDef.await()
                val appleEntries = appleRssDef.await()
                val audiusBatchMeta = audiusBatchDef.await()

                val hydratedSeeds = hydrateSeeds(PH_SEED_TRACKS, audiusBatchMeta)

                val combinedChartCandidates = deduplicateTracks(primaryCandidates + viralCandidates)
                val nonSeedCandidates = combinedChartCandidates
                    .filter { matchCuratedSeed(it.title, it.artist) == null }
                    .take(8)

                val resolvedExtrasDef = async {
                    resolveCandidatesToFull(nonSeedCandidates, maxResolve = 8)
                }
                val appleExtrasDef = async {
                    val existingKeys = (hydratedSeeds + nonSeedCandidates)
                        .mapTo(HashSet()) { normalizedTrackKey(it.title, it.artist) }
                    val missingApple = appleEntries
                        .filter { normalizedTrackKey(it.title, it.artist) !in existingKeys }
                        .take(4)
                    resolveAppleEntriesToFull(missingApple, defaultGenre = "OPM")
                }

                val resolvedExtras = resolvedExtrasDef.await()
                val appleExtras = appleExtrasDef.await()

                val combined = interleaveTrackLists(hydratedSeeds, resolvedExtras + appleExtras)
                finalizeFullTrackList(combined, safeLimit)
            }
        }
    }

    suspend fun getOpmTrendingTracks(limit: Int = 35): List<Track> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        cachedTracks("opm_trending_$safeLimit") {
            coroutineScope {
                val playlistDef = async {
                    fetchDeezerChartCandidates(
                        "/playlist/$PL_OPM_VIRAL_HITS/tracks?limit=$safeLimit",
                        defaultGenre = "OPM"
                    )
                }
                val artistTrackDefs = OPM_SPOTLIGHT_ARTIST_IDS.map { artistId ->
                    async {
                        fetchDeezerChartCandidates(
                            "/artist/$artistId/top?limit=4",
                            defaultGenre = "OPM"
                        )
                    }
                }
                val audiusBatchDef = async { fetchAudiusSeedMetadata(PH_SEED_TRACKS) }

                val playlistCandidates = playlistDef.await()
                val spotlightLists = artistTrackDefs.awaitAll()
                val audiusBatchMeta = audiusBatchDef.await()

                val hydratedSeeds = hydrateSeeds(PH_SEED_TRACKS, audiusBatchMeta)

                val chartPool = deduplicateTracks(
                    roundRobinInterleave(spotlightLists) + playlistCandidates
                )
                val nonSeedCandidates = chartPool
                    .filter { matchCuratedSeed(it.title, it.artist) == null }
                    .take(10)
                val resolvedExtras = resolveCandidatesToFull(nonSeedCandidates, maxResolve = 10)

                val combined = hydratedSeeds + resolvedExtras
                finalizeFullTrackList(combined, safeLimit)
            }
        }
    }

    suspend fun getGlobalTopTracks(limit: Int = 40): List<Track> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        cachedTracks("global_top_$safeLimit") {
            coroutineScope {
                val worldwideDef = async {
                    fetchDeezerChartCandidates(
                        "/playlist/$PL_GLOBAL_TOP_50/tracks?limit=$safeLimit",
                        defaultGenre = "Pop"
                    )
                }
                val chartDef = async {
                    fetchDeezerChartCandidates(
                        "/chart/0/tracks?limit=$safeLimit",
                        defaultGenre = "Pop"
                    )
                }
                val audiusBatchDef = async { fetchAudiusSeedMetadata(GLOBAL_SEED_TRACKS) }

                val worldwide = worldwideDef.await()
                val chart = chartDef.await()
                val audiusBatchMeta = audiusBatchDef.await()

                val hydratedSeeds = hydrateSeeds(GLOBAL_SEED_TRACKS, audiusBatchMeta)
                val mergedChart = deduplicateTracks(interleaveTrackLists(worldwide, chart))
                val nonSeedCandidates = mergedChart
                    .filter { matchCuratedSeed(it.title, it.artist) == null }
                    .take(10)
                val resolvedExtras = resolveCandidatesToFull(nonSeedCandidates, maxResolve = 10)

                val combined = hydratedSeeds + resolvedExtras
                finalizeFullTrackList(combined, safeLimit)
            }
        }
    }

    suspend fun getPhTrendingArtists(): List<Artist> = withContext(Dispatchers.IO) {
        cachedArtists("ph_artists") {
            RegionalContentFilter.filterArtists(refreshCuratedArtists(CURATED_PH_ARTISTS))
        }
    }

    suspend fun getGlobalTrendingArtists(): List<Artist> = withContext(Dispatchers.IO) {
        cachedArtists("global_artists") {
            RegionalContentFilter.filterArtists(refreshCuratedArtists(CURATED_GLOBAL_ARTISTS))
        }
    }

    fun getInstantPhTracks(limit: Int = 40): List<Track> = finalizeFullTrackList(
        PH_SEED_TRACKS.map { seed ->
            buildSeedTrack(seed, seedArtworkCache[seed.audiusId] ?: lookupMetaByTitleArtist(seed.title, seed.artist))
        },
        limit
    )

    fun getInstantOpmTracks(limit: Int = 35): List<Track> = finalizeFullTrackList(
        PH_SEED_TRACKS.map { seed ->
            buildSeedTrack(seed, seedArtworkCache[seed.audiusId] ?: lookupMetaByTitleArtist(seed.title, seed.artist))
        },
        limit
    )

    fun getInstantGlobalTracks(limit: Int = 40): List<Track> = finalizeFullTrackList(
        GLOBAL_SEED_TRACKS.map { seed ->
            buildSeedTrack(seed, seedArtworkCache[seed.audiusId] ?: lookupMetaByTitleArtist(seed.title, seed.artist))
        },
        limit
    )

    fun getInstantPhArtists(): List<Artist> =
        RegionalContentFilter.filterArtists(CURATED_PH_ARTISTS.map { it.toArtist() })

    fun getInstantGlobalArtists(): List<Artist> =
        RegionalContentFilter.filterArtists(CURATED_GLOBAL_ARTISTS.map { it.toArtist() })

    fun getInstantMusicVideos(): List<Track> {
        val all = youTubeApi.getCuratedMusicVideos()
        val ph = all.filter { it.genre.contains("OPM", true) || it.genre.contains("P-Pop", true) }
        val global = all.filterNot { it in ph }
        return RegionalContentFilter.filterTracks(interleaveTrackLists(ph, global))
    }

    suspend fun getPhMusicVideos(): List<Track> = youTubeApi.getPhMusicVideos()

    suspend fun getGlobalMusicVideos(): List<Track> = youTubeApi.getGlobalMusicVideos()

    suspend fun getCombinedMusicVideos(): List<Track> = withContext(Dispatchers.IO) {
        coroutineScope {
            val phDef = async { getPhMusicVideos() }
            val globalDef = async { getGlobalMusicVideos() }
            val merged = interleaveTrackLists(phDef.await(), globalDef.await())
            val seenVideoIds = HashSet<String>(merged.size)
            val deduped = merged.filter { track ->
                val key = track.youtubeVideoId.ifBlank { track.id }
                seenVideoIds.add(key)
            }
            RegionalContentFilter.filterTracks(deduped)
        }
    }

    fun getYouTubePacks(): List<YouTubePack> = youTubeApi.getCuratedPacks()

    fun getYouTubePackTracks(packId: String): List<Track> = youTubeApi.getPackTracks(packId)

    suspend fun importFromYouTube(input: String): YouTubeImportResult =
        youTubeApi.importFromUrlOrQuery(input)

    suspend fun searchVideos(query: String): List<Track> = youTubeApi.searchVideos(query)

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

                key == "p-pop" || key == "ppop" -> coroutineScope {
                    val seeds = hydrateSeeds(
                        ALL_SEED_TRACKS.filter { it.genre.equals("P-Pop", ignoreCase = true) }
                    )
                    val chartDef = async {
                        fetchMultiArtistChartCandidates(listOf("320392011", "52961842", "122878032"), "P-Pop", perArtist = 6)
                    }
                    val biniDef = async { searchArtistMatchedAudius("BINI", "P-Pop", limit = 10) }
                    val sb19Def = async { searchArtistMatchedAudius("SB19", "P-Pop", limit = 10) }

                    val resolvedChart = resolveCandidatesToFull(chartDef.await(), maxResolve = 8)
                    val raw = (seeds + resolvedChart + biniDef.await() + sb19Def.await())
                        .map { it.copy(genre = "P-Pop") }
                    finalizeFullTrackList(raw, safeLimit)
                }

                key == "pinoy hip-hop" || key == "pinoy rap" -> coroutineScope {
                    val seeds = hydrateSeeds(
                        ALL_SEED_TRACKS.filter { it.genre.equals("Pinoy Hip-Hop", ignoreCase = true) }
                    )
                    val chartDef = async {
                        fetchMultiArtistChartCandidates(listOf("126789982", "11321714"), "Pinoy Hip-Hop", perArtist = 8)
                    }
                    val hevDef = async { searchArtistMatchedAudius("Hev Abi", "Pinoy Hip-Hop", limit = 10) }
                    val alDef = async { searchArtistMatchedAudius("Al James", "Pinoy Hip-Hop", limit = 8) }
                    val shantiDef = async { searchArtistMatchedAudius("Shanti Dope", "Pinoy Hip-Hop", limit = 8) }

                    val resolvedChart = resolveCandidatesToFull(chartDef.await(), maxResolve = 8)
                    val raw = (seeds + resolvedChart + hevDef.await() + alDef.await() + shantiDef.await())
                        .map { it.copy(genre = "Pinoy Hip-Hop") }
                    finalizeFullTrackList(raw, safeLimit)
                }

                key == "throwback opm" -> coroutineScope {
                    val chartDef = async {
                        fetchDeezerChartCandidates("/playlist/$PL_THROWBACK_OPM_2000S/tracks?limit=$safeLimit", "Throwback OPM")
                    }
                    val eheadsDef = async { searchArtistMatchedAudius("Eraserheads", "Throwback OPM", limit = 8) }
                    val parokyaDef = async { searchArtistMatchedAudius("Parokya ni Edgar", "Throwback OPM", limit = 8) }
                    val rivermayaDef = async { searchArtistMatchedAudius("Rivermaya", "Throwback OPM", limit = 8) }

                    val resolvedChart = resolveCandidatesToFull(chartDef.await(), maxResolve = 12)
                    val combined = (resolvedChart + eheadsDef.await() + parokyaDef.await() + rivermayaDef.await())
                        .ifEmpty { hydrateSeeds(PH_SEED_TRACKS) }
                        .map { it.copy(genre = "Throwback OPM") }
                    finalizeFullTrackList(combined, safeLimit)
                }

                key == "pop" || key == "global pop" || key == "dance" -> coroutineScope {
                    val seeds = hydrateSeeds(
                        ALL_SEED_TRACKS.filter { it.genre.equals("Pop", ignoreCase = true) }
                    )
                    val chartDef = async {
                        fetchMultiArtistChartCandidates(listOf("12246", "1562681", "1176900", "11152580"), "Pop", perArtist = 5)
                    }
                    val audiusDef = async { getAudiusTrendingFullTracks(genre = "Pop", limit = 15) }

                    val resolvedChart = resolveCandidatesToFull(chartDef.await(), maxResolve = 8)
                    val combined = (seeds + resolvedChart + audiusDef.await())
                        .map { it.copy(genre = "Pop") }
                    finalizeFullTrackList(combined, safeLimit)
                }

                key == "r&b" || key == "r&b/soul" || key == "soul" || key == "jazz" -> coroutineScope {
                    val seeds = hydrateSeeds(
                        ALL_SEED_TRACKS.filter {
                            it.genre.equals("R&B", ignoreCase = true) ||
                                it.artistId in setOf("69165172", "55640202", "4050205", "1193881")
                        }
                    )
                    val chartDef = async {
                        fetchMultiArtistChartCandidates(listOf("69165172", "55640202", "4050205"), "R&B", perArtist = 6)
                    }
                    val audiusDef = async { getAudiusTrendingFullTracks(genre = "R&B/Soul", limit = 15) }

                    val resolvedChart = resolveCandidatesToFull(chartDef.await(), maxResolve = 8)
                    val combined = (seeds + resolvedChart + audiusDef.await())
                        .map { it.copy(genre = "R&B") }
                    finalizeFullTrackList(combined, safeLimit)
                }

                key == "hip-hop" || key == "hip-hop/rap" || key == "rap" || key == "trap" -> coroutineScope {
                    val seeds = hydrateSeeds(
                        ALL_SEED_TRACKS.filter {
                            it.genre.equals("Hip-Hop", ignoreCase = true) ||
                                it.genre.equals("Pinoy Hip-Hop", ignoreCase = true)
                        }
                    )
                    val chartDef = async {
                        fetchMultiArtistChartCandidates(listOf("525046", "126789982"), "Hip-Hop", perArtist = 6)
                    }
                    val audiusDef = async { getAudiusTrendingFullTracks(genre = "Hip-Hop/Rap", limit = 15) }

                    val resolvedChart = resolveCandidatesToFull(chartDef.await(), maxResolve = 8)
                    val combined = (seeds + resolvedChart + audiusDef.await())
                        .map { it.copy(genre = "Hip-Hop") }
                    finalizeFullTrackList(combined, safeLimit)
                }

                key == "k-pop" || key == "kpop" -> coroutineScope {
                    val seeds = hydrateSeeds(
                        ALL_SEED_TRACKS.filter { it.genre.equals("K-Pop", ignoreCase = true) }
                    )
                    val chartDef = async {
                        fetchMultiArtistChartCandidates(listOf("1228100"), "K-Pop", perArtist = 8)
                    }
                    val jennieDef = async { searchArtistMatchedAudius("JENNIE", "K-Pop", limit = 8) }
                    val bpDef = async { searchArtistMatchedAudius("BLACKPINK", "K-Pop", limit = 8) }
                    val njDef = async { searchArtistMatchedAudius("NewJeans", "K-Pop", limit = 8) }

                    val resolvedChart = resolveCandidatesToFull(chartDef.await(), maxResolve = 8)
                    val combined = (seeds + resolvedChart + jennieDef.await() + bpDef.await() + njDef.await())
                        .map { it.copy(genre = "K-Pop") }
                    finalizeFullTrackList(combined, safeLimit)
                }

                key == "rock" || key == "indie" || key == "alternative" -> coroutineScope {
                    val seeds = hydrateSeeds(
                        ALL_SEED_TRACKS.filter { it.genre.equals("Rock", ignoreCase = true) }
                    )
                    val chartDef = async {
                        fetchMultiArtistChartCandidates(listOf("11152604", "12450156", "1407775"), "Rock", perArtist = 6)
                    }
                    val audiusDef = async { getAudiusTrendingFullTracks(genre = "Rock", limit = 15) }

                    val resolvedChart = resolveCandidatesToFull(chartDef.await(), maxResolve = 8)
                    val combined = (seeds + resolvedChart + audiusDef.await())
                        .map { it.copy(genre = "Rock") }
                    finalizeFullTrackList(combined, safeLimit)
                }

                key == "chill" || key == "lo-fi" || key == "lofi" || key == "ambient" || key == "acoustic" || key == "folk" -> coroutineScope {
                    val seeds = hydrateSeeds(
                        ALL_SEED_TRACKS.filter {
                            it.genre.equals("Chill", ignoreCase = true) ||
                                it.artistId in setOf("242188", "7189292", "6248262", "179158087")
                        }
                    )
                    val chartDef = async {
                        fetchMultiArtistChartCandidates(listOf("11072592", "242188", "11819305", "6248262"), "Chill", perArtist = 5)
                    }
                    val audiusDef = async { getAudiusTrendingFullTracks(genre = "Lo-Fi", limit = 15) }

                    val resolvedChart = resolveCandidatesToFull(chartDef.await(), maxResolve = 8)
                    val combined = (seeds + resolvedChart + audiusDef.await())
                        .map { it.copy(genre = "Chill") }
                    finalizeFullTrackList(combined, safeLimit)
                }

                key == "electronic" || key == "house" || key == "techno" || key == "synthwave" -> coroutineScope {
                    val audiusTrendDef = async { getAudiusTrendingFullTracks(genre = "Electronic", limit = safeLimit) }
                    val audiusSearchDef = async { searchAudiusFullTracks(cleanGenre, limit = safeLimit, defaultGenre = cleanGenre) }
                    val combined = (audiusTrendDef.await() + audiusSearchDef.await())
                        .map { it.copy(genre = cleanGenre) }
                    finalizeFullTrackList(combined, safeLimit)
                }

                else -> coroutineScope {
                    val searchDef = async { searchTracks(cleanGenre, limit = safeLimit) }
                    val trendDef = async { getAudiusTrendingFullTracks(genre = cleanGenre, limit = safeLimit) }
                    val combined = (searchDef.await() + trendDef.await())
                        .ifEmpty { getPhTopTracks(safeLimit) }
                    finalizeFullTrackList(combined, safeLimit)
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
                                parseDeezerPlaylist(root, fallback = seed) ?: seed.toPlaylist()
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
        val rawTrimmed = playlistId.trim()
        if (rawTrimmed.isEmpty()) return@withContext emptyList()

        if (rawTrimmed.startsWith(AUDIUS_PLAYLIST_ID_PREFIX)) {
            val audPlaylistId = rawTrimmed.removePrefix(AUDIUS_PLAYLIST_ID_PREFIX)
            return@withContext cachedTracks("pl_aud_$audPlaylistId") {
                val tracks = fetchAudiusPlaylistFullTracks(audPlaylistId)
                finalizeFullTrackList(tracks, 50)
            }
        }

        val cleanId = rawTrimmed.removePrefix(PLAYLIST_ID_PREFIX)
        if (cleanId.isEmpty()) return@withContext emptyList()

        cachedTracks("pl_tracks_$cleanId") {
            coroutineScope {
                val seedRosterForPlaylist = when (cleanId) {
                    PL_TOP_HITS_PH_2026, PL_OPM_VIRAL_HITS, PL_DEEZER_TOP_PH -> PH_SEED_TRACKS
                    PL_GLOBAL_TOP_50 -> GLOBAL_SEED_TRACKS
                    else -> emptyList()
                }
                val audiusSeedDef = async {
                    if (seedRosterForPlaylist.isNotEmpty()) {
                        fetchAudiusSeedMetadata(seedRosterForPlaylist)
                    } else {
                        emptyMap()
                    }
                }
                val deezerCandidatesDef = async {
                    fetchDeezerChartCandidates(
                        "/playlist/${encodePathSegment(cleanId)}/tracks?limit=50",
                        defaultGenre = playlistDefaultGenre(cleanId)
                    )
                }

                val audiusBatchMeta = audiusSeedDef.await()
                val chartCandidates = deezerCandidatesDef.await()
                val hydratedSeeds = if (seedRosterForPlaylist.isNotEmpty()) {
                    hydrateSeeds(seedRosterForPlaylist, audiusBatchMeta)
                } else {
                    emptyList()
                }

                val nonSeedCandidates = chartCandidates
                    .filter { matchCuratedSeed(it.title, it.artist) == null }
                    .take(15)
                val resolvedChartTracks = resolveCandidatesToFull(nonSeedCandidates, maxResolve = 15)

                val extraThrowback = if (cleanId == PL_THROWBACK_OPM_2000S && resolvedChartTracks.size < 10) {
                    searchAudiusFullTracks("Eraserheads Rivermaya Parokya ni Edgar", limit = 20, defaultGenre = "Throwback OPM")
                } else {
                    emptyList()
                }

                val combined = interleaveTrackLists(hydratedSeeds, resolvedChartTracks + extraThrowback)
                finalizeFullTrackList(combined, 50)
            }
        }
    }

    suspend fun getArtistTopTracks(
        artistId: String,
        artistName: String = "",
        limit: Int = 25
    ): List<Track> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 50)
        val cleanId = normalizeDeezerArtistId(artistId)
        val effectiveName = artistName.trim().ifBlank { CURATED_ARTIST_NAME_BY_ID[cleanId].orEmpty() }
        val cacheKey = "art_top_${cleanId.ifEmpty { effectiveName.lowercase(Locale.ROOT) }}_$safeLimit"

        cachedTracks(cacheKey) {
            coroutineScope {
                val matchingSeeds = ALL_SEED_TRACKS.filter { seed ->
                    (cleanId.isNotEmpty() && seed.artistId == cleanId) ||
                        (effectiveName.isNotEmpty() && matchesArtistName(seed.artist, seed.title, effectiveName))
                }

                val audiusSeedDef = async {
                    if (matchingSeeds.isNotEmpty()) fetchAudiusSeedMetadata(matchingSeeds) else emptyMap()
                }
                val deezerTopDef = async {
                    if (cleanId.isNotEmpty() && cleanId.all { it.isDigit() }) {
                        fetchDeezerChartCandidates(
                            "/artist/$cleanId/top?limit=$safeLimit",
                            defaultGenre = artistDefaultGenre(cleanId)
                        )
                    } else {
                        emptyList()
                    }
                }
                val audiusSearchDef = async {
                    if (effectiveName.isNotEmpty()) {
                        searchArtistMatchedAudius(
                            artistName = effectiveName,
                            defaultGenre = artistDefaultGenre(cleanId),
                            limit = safeLimit
                        )
                    } else {
                        emptyList()
                    }
                }

                val audiusSeedMeta = audiusSeedDef.await()
                val deezerCandidates = deezerTopDef.await()
                val audiusArtistTracks = audiusSearchDef.await()

                val hydratedSeeds = if (matchingSeeds.isNotEmpty()) {
                    hydrateSeeds(matchingSeeds, audiusSeedMeta)
                } else {
                    emptyList()
                }

                val nonSeedCandidates = deezerCandidates
                    .filter { matchCuratedSeed(it.title, it.artist) == null }
                    .take(10)
                val resolvedDeezer = resolveCandidatesToFull(nonSeedCandidates, maxResolve = 10)

                val combined = hydratedSeeds + resolvedDeezer + audiusArtistTracks
                finalizeFullTrackList(combined, safeLimit)
            }
        }
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
                val filtered = RegionalContentFilter.filterArtists(parsed)
                if (filtered.isNotEmpty()) return@withContext filtered
            }
        }
        val curatedPool = if (CURATED_GLOBAL_ARTISTS.any { it.id == cleanId }) {
            CURATED_GLOBAL_ARTISTS
        } else {
            CURATED_PH_ARTISTS
        }
        RegionalContentFilter.filterArtists(
            curatedPool.filterNot { it.id == cleanId }.take(10).map { it.toArtist() }
        )
    }

    suspend fun searchTracks(query: String, limit: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        if (RegionalContentFilter.isExcludedIndianContent(title = trimmed, artist = trimmed)) {
            return@withContext emptyList()
        }
        val safeLimit = limit.coerceIn(1, 50)
        val effectiveQuery = expandSearchAlias(trimmed)

        coroutineScope {
            val matchingSeeds = findMatchingCuratedSeeds(effectiveQuery)
            val seedMetaDef = async {
                if (matchingSeeds.isNotEmpty()) fetchAudiusSeedMetadata(matchingSeeds) else emptyMap()
            }
            val audiusSearchDef = async {
                searchAudiusFullTracks(effectiveQuery, limit = safeLimit.coerceAtLeast(25), defaultGenre = "Music")
            }

            val seedMeta = seedMetaDef.await()
            val hydratedSeeds = if (matchingSeeds.isNotEmpty()) {
                hydrateSeeds(matchingSeeds, seedMeta)
            } else {
                emptyList()
            }
            val audiusHits = audiusSearchDef.await()

            val curatedIds = hydratedSeeds.mapTo(HashSet()) { it.id }
            val curatedStreams = hydratedSeeds.mapTo(HashSet()) { it.streamUrl }
            val queryNorm = normalizeText(effectiveQuery)
            val queryTokens = tokenizeWords(effectiveQuery)
            val queryWantsNoise = NOISE_SEARCH_REGEXES.any { it.containsMatchIn(queryNorm) }

            val combined = deduplicateTracks(hydratedSeeds + audiusHits)
                .filter { track ->
                    queryWantsNoise || !isNoiseSearchResult(track.title)
                }
                .sortedWith(
                    compareByDescending<Track> { track ->
                        scoreSearchRelevance(
                            track = track,
                            queryNorm = queryNorm,
                            queryTokens = queryTokens,
                            isCuratedSeed = track.id in curatedIds || track.streamUrl in curatedStreams
                        )
                    }.thenByDescending { it.playCount }
                )

            finalizeFullTrackList(combined, safeLimit)
        }
    }

    suspend fun searchArtists(query: String, limit: Int = 15): List<Artist> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        if (RegionalContentFilter.isExcludedIndianContent(title = "", artist = trimmed)) {
            return@withContext emptyList()
        }
        val safeLimit = limit.coerceIn(1, 50)
        val fetchLimit = (safeLimit * 2).coerceIn(15, 50)
        val effectiveQuery = expandSearchAlias(trimmed)

        val root = fetchDeezerJson("/search/artist?q=${encodeUrlParam(effectiveQuery)}&limit=$fetchLimit")
        val data = root?.optJSONArray("data")
        if (data != null) {
            val parsed = buildList {
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    parseDeezerArtist(obj)?.let(::add)
                }
            }
            val filtered = RegionalContentFilter.filterArtists(parsed)
            if (filtered.isNotEmpty()) {
                return@withContext filtered
                    .sortedByDescending { it.followerCount }
                    .take(safeLimit)
            }
        }

        RegionalContentFilter.filterArtists(
            (CURATED_PH_ARTISTS + CURATED_GLOBAL_ARTISTS)
                .filter { it.name.contains(effectiveQuery, ignoreCase = true) }
                .sortedByDescending { it.followerCount }
                .take(safeLimit)
                .map { it.toArtist() }
        )
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

        val audiusPlaylists = searchAudiusPlaylists(trimmed, safeLimit)
        if (audiusPlaylists.isNotEmpty()) {
            return@withContext audiusPlaylists.take(safeLimit)
        }

        CURATED_PLAYLISTS
            .filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                    it.description.contains(trimmed, ignoreCase = true)
            }
            .take(safeLimit)
            .map { it.toPlaylist() }
    }

    suspend fun resolvePlayableTrack(track: Track): Track {
        if (RegionalContentFilter.isExcludedTrack(track)) {
            return track.copy(streamUrl = "", isPreviewOnly = false)
        }

        if (!track.localUri.isNullOrBlank() ||
            track.isLive ||
            track.source in ALWAYS_BYPASS_SOURCES
        ) {
            return if (FullAudioResolver.isPreviewAudioUrl(track.streamUrl) || track.isPreviewOnly) {
                track.copy(streamUrl = "", isPreviewOnly = false)
            } else {
                track
            }
        }

        if (track.source == TrackSource.AUDIUS &&
            track.streamUrl.startsWith("http", ignoreCase = true) &&
            !FullAudioResolver.isPreviewAudioUrl(track.streamUrl)
        ) {
            return track.copy(isPreviewOnly = false)
        }

        val resolved = fullAudioResolver.resolveFullTrackOrNull(track)
        if (resolved != null && isValidFullAudioTrack(resolved)) {
            return resolved
        }

        return if (FullAudioResolver.isPreviewAudioUrl(track.streamUrl)) {
            track.copy(streamUrl = "", isPreviewOnly = false)
        } else {
            track.copy(isPreviewOnly = false)
        }
    }

    suspend fun resolveFreshStreamUrl(track: Track): String {
        val resolved = resolvePlayableTrack(track)
        return if (FullAudioResolver.isPreviewAudioUrl(resolved.streamUrl)) "" else resolved.streamUrl
    }

    private suspend fun resolveCandidatesToFull(
        candidates: List<Track>,
        maxResolve: Int
    ): List<Track> = coroutineScope {
        if (candidates.isEmpty() || maxResolve <= 0) return@coroutineScope emptyList()

        val immediateSeeds = mutableListOf<Track>()
        val pending = mutableListOf<Track>()

        for (candidate in candidates) {
            val seed = matchCuratedSeed(candidate.title, candidate.artist)
            if (seed != null) {
                immediateSeeds.add(buildSeedTrack(seed, candidateMetadataToHydrated(candidate)))
            } else if (candidate.durationSec == 0 || candidate.durationSec >= MIN_FULL_DURATION_SEC) {
                pending.add(candidate)
            }
        }

        val resolvedDynamic = pending
            .take(maxResolve)
            .chunked(4)
            .flatMap { chunk ->
                chunk.map { candidate ->
                    async {
                        val resolved = fullAudioResolver.resolveFullTrackOrNull(candidate) ?: return@async null
                        if (!isValidFullAudioTrack(resolved)) return@async null
                        val ytId = resolved.youtubeVideoId.ifBlank {
                            youTubeApi.findMatchingVerifiedVideoId(resolved.title, resolved.artist).orEmpty()
                        }
                        resolved.copy(
                            artworkUrl = resolved.artworkUrl ?: candidate.artworkUrl,
                            artworkMirrors = resolved.artworkMirrors.ifEmpty { candidate.artworkMirrors },
                            album = resolved.album.ifBlank { candidate.album },
                            releaseDate = resolved.releaseDate.ifBlank { candidate.releaseDate },
                            playCount = maxOf(resolved.playCount, candidate.playCount),
                            isPreviewOnly = false,
                            youtubeVideoId = ytId,
                            hasMusicVideo = resolved.hasMusicVideo || ytId.isNotBlank()
                        )
                    }
                }.awaitAll().filterNotNull()
            }

        immediateSeeds + resolvedDynamic
    }

    private suspend fun resolveAppleEntriesToFull(
        entries: List<AppleChartEntry>,
        defaultGenre: String
    ): List<Track> = coroutineScope {
        if (entries.isEmpty()) return@coroutineScope emptyList()
        entries.chunked(2).flatMap { chunk ->
            chunk.map { entry ->
                async {
                    if (RegionalContentFilter.isExcludedIndianContent(entry.title, entry.artist, genre = defaultGenre)) {
                        return@async null
                    }
                    val matchedSeed = matchCuratedSeed(entry.title, entry.artist)
                    if (matchedSeed != null) {
                        return@async hydrateSeeds(listOf(matchedSeed)).firstOrNull()
                    }
                    val stub = Track(
                        id = "apple_${normalizedTrackKey(entry.title, entry.artist).hashCode().toUInt()}",
                        title = entry.title,
                        artist = entry.artist,
                        genre = defaultGenre,
                        durationSec = 0,
                        streamUrl = "",
                        source = TrackSource.DEEZER,
                        isPreviewOnly = false
                    )
                    val resolved = fullAudioResolver.resolveFullTrackOrNull(stub) ?: return@async null
                    if (!isValidFullAudioTrack(resolved)) return@async null
                    val artMeta = trackMetaByKeyCache[normalizedTrackKey(entry.title, entry.artist)]
                        ?: fetchArtworkMetadataFromItunes(entry.title, entry.artist)
                    val ytId = resolved.youtubeVideoId.ifBlank {
                        youTubeApi.findMatchingVerifiedVideoId(resolved.title, resolved.artist).orEmpty()
                    }
                    resolved.copy(
                        artworkUrl = resolved.artworkUrl ?: artMeta?.artworkUrl,
                        artworkMirrors = resolved.artworkMirrors.ifEmpty { artMeta?.artworkMirrors.orEmpty() },
                        album = resolved.album.ifBlank { artMeta?.album.orEmpty() },
                        isPreviewOnly = false,
                        youtubeVideoId = ytId,
                        hasMusicVideo = resolved.hasMusicVideo || ytId.isNotBlank()
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun hydrateSeeds(
        seeds: List<SeedTrack>,
        audiusBatchMeta: Map<String, HydratedMeta>? = null
    ): List<Track> = coroutineScope {
        if (seeds.isEmpty()) return@coroutineScope emptyList()

        val unresolvedSeeds = mutableListOf<SeedTrack>()
        for (seed in seeds) {
            val cached = seedArtworkCache[seed.audiusId]
            if (cached != null && !cached.artworkUrl.isNullOrBlank()) continue

            val chartMeta = lookupMetaByTitleArtist(seed.title, seed.artist)
            if (chartMeta != null && !chartMeta.artworkUrl.isNullOrBlank()) {
                seedArtworkCache[seed.audiusId] = chartMeta
                continue
            }
            unresolvedSeeds.add(seed)
        }

        if (unresolvedSeeds.isNotEmpty()) {
            val audiusMeta = audiusBatchMeta ?: fetchAudiusSeedMetadata(unresolvedSeeds)

            for (seed in unresolvedSeeds) {
                val audFallback = audiusMeta[seed.audiusId]
                if (audFallback != null && !audFallback.artworkUrl.isNullOrBlank()) {
                    seedArtworkCache[seed.audiusId] = audFallback
                }
            }

            val stillMissing = unresolvedSeeds.filter { seed ->
                seedArtworkCache[seed.audiusId] == null &&
                    ARTIST_PORTRAIT_BY_ID[seed.artistId].isNullOrBlank()
            }.take(10)

            if (stillMissing.isNotEmpty()) {
                withTimeoutOrNull(3_500L) {
                    stillMissing.map { seed ->
                        async {
                            val officialMeta = fetchArtworkForSeedFromSearch(seed)
                            if (officialMeta != null && !officialMeta.artworkUrl.isNullOrBlank()) {
                                seedArtworkCache[seed.audiusId] = officialMeta
                            }
                        }
                    }.awaitAll()
                }
            }
        }

        seeds.map { seed ->
            val meta = seedArtworkCache[seed.audiusId]
                ?: audiusBatchMeta?.get(seed.audiusId)
                ?: lookupMetaByTitleArtist(seed.title, seed.artist)
            buildSeedTrack(seed, meta)
        }
    }

    private fun buildSeedTrack(seed: SeedTrack, meta: HydratedMeta?): Track {
        val ytId = seed.youtubeVideoId
        val artworkUrl = meta?.artworkUrl?.takeIf { it.isNotBlank() }
            ?: ARTIST_PORTRAIT_BY_ID[seed.artistId]
            ?: ytId.takeIf { it.isNotBlank() }?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
        return Track(
            id = "$AUDIUS_TRACK_ID_PREFIX${seed.audiusId}",
            title = seed.title,
            artist = seed.artist,
            artistId = seed.artistId,
            album = meta?.album.orEmpty(),
            genre = seed.genre,
            mood = seed.mood,
            durationSec = maxOf(seed.durationSec, MIN_FULL_DURATION_SEC),
            artworkUrl = artworkUrl,
            artworkMirrors = meta?.artworkMirrors.orEmpty(),
            streamUrl = "$AUDIUS_PRIMARY_HOST/v1/tracks/${encodePathSegment(seed.audiusId)}/stream?app_name=$AUDIUS_APP_NAME",
            source = TrackSource.AUDIUS,
            isLive = false,
            isSmartPick = false,
            releaseDate = meta?.releaseDate.orEmpty(),
            playCount = meta?.playCount ?: 0L,
            externalUrl = "https://audius.co/tracks/${seed.audiusId}",
            isPreviewOnly = false,
            youtubeVideoId = ytId,
            hasMusicVideo = ytId.isNotBlank()
        )
    }

    private suspend fun fetchAudiusSeedMetadata(seeds: List<SeedTrack>): Map<String, HydratedMeta> {
        if (seeds.isEmpty()) return emptyMap()
        val combined = HashMap<String, HydratedMeta>(seeds.size)
        for (chunk in seeds.chunked(25)) {
            val idParams = chunk.joinToString("&") { "id=${encodeUrlParam(it.audiusId)}" }
            val pathAndQuery = "/v1/tracks?app_name=$AUDIUS_APP_NAME&$idParams"
            for (host in AUDIUS_HOSTS) {
                val body = client.getText("$host$pathAndQuery") ?: continue
                val data = try {
                    JSONObject(body).optJSONArray("data")
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                } ?: continue

                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val id = obj.optCleanString("id")
                    if (id.isEmpty()) continue
                    val (artworkUrl, mirrors) = extractAudiusArtwork(obj.optJSONObject("artwork"))
                    combined[id] = HydratedMeta(
                        artworkUrl = artworkUrl,
                        artworkMirrors = mirrors,
                        releaseDate = obj.optCleanString("release_date").take(10),
                        playCount = obj.optLong("play_count", 0L).coerceAtLeast(0L)
                    )
                }
                break
            }
        }
        return combined
    }

    private suspend fun fetchArtworkForSeedFromSearch(seed: SeedTrack): HydratedMeta? {
        val primaryArtist = extractPrimaryArtist(seed.artist)
        val query = listOf(seed.title, primaryArtist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null

        if (!isInRateLimitCooldown()) {
            val root = fetchDeezerJson(
                "/search/track?q=${encodeUrlParam(query)}&limit=1",
                allowRetryOnRateLimit = false
            )
            val firstObj = root?.optJSONArray("data")?.optJSONObject(0)
            if (firstObj != null) {
                val itemTitle = firstObj.optCleanString("title")
                    .ifBlank { firstObj.optCleanString("title_short") }
                if (titlesRoughlyMatch(seed.title, itemTitle)) {
                    val meta = extractDeezerMetadata(firstObj)
                    if (meta != null && !meta.artworkUrl.isNullOrBlank()) {
                        cacheMetaForKey(seed.title, seed.artist, meta)
                        return meta
                    }
                }
            }
        }

        return fetchArtworkMetadataFromItunes(seed.title, primaryArtist)
    }

    private suspend fun fetchArtworkMetadataFromItunes(title: String, artist: String): HydratedMeta? {
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        val url = "$ITUNES_PH_SEARCH_URL&term=${encodeUrlParam(query)}&limit=1"
        val body = client.getText(url) ?: return null
        return try {
            val item = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return null
            val itemTitle = item.optCleanString("trackName").ifBlank { item.optCleanString("trackCensoredName") }
            if (!titlesRoughlyMatch(title, itemTitle)) return null
            val rawArt100 = item.optCleanString("artworkUrl100").ifBlank { item.optCleanString("artworkUrl60") }
            val upgraded = upgradeItunesArtwork(rawArt100) ?: return null
            val meta = HydratedMeta(
                artworkUrl = upgraded,
                artworkMirrors = listOfNotNull(rawArt100.takeIf { it.isNotBlank() && it != upgraded }),
                album = item.optCleanString("collectionName"),
                releaseDate = item.optCleanString("releaseDate").take(10)
            )
            cacheMetaForKey(title, artist, meta)
            meta
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun searchAudiusFullTracks(
        query: String,
        limit: Int = 25,
        defaultGenre: String = "Music"
    ): List<Track> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val safeLimit = limit.coerceIn(1, 50)
        val pathAndQuery =
            "/v1/tracks/search?app_name=$AUDIUS_APP_NAME&limit=$safeLimit&query=${encodeUrlParam(trimmed)}"
        for (host in AUDIUS_HOSTS) {
            val body = client.getText("$host$pathAndQuery") ?: continue
            val data = try {
                JSONObject(body).optJSONArray("data")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            } ?: continue
            return parseAudiusFullTracks(data, defaultGenre)
        }
        return emptyList()
    }

    private suspend fun getAudiusTrendingFullTracks(
        genre: String? = null,
        limit: Int = 20
    ): List<Track> {
        val safeLimit = limit.coerceIn(1, 50)
        val genreParam = genre?.trim()?.takeIf {
            it.isNotEmpty() && !it.equals("All", ignoreCase = true)
        }?.let { "&genre=${encodeUrlParam(it)}" }.orEmpty()
        val pathAndQuery = "/v1/tracks/trending?app_name=$AUDIUS_APP_NAME&limit=$safeLimit$genreParam"
        for (host in AUDIUS_HOSTS) {
            val body = client.getText("$host$pathAndQuery") ?: continue
            val data = try {
                JSONObject(body).optJSONArray("data")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            } ?: continue
            return parseAudiusFullTracks(data, genre ?: "Music")
        }
        return emptyList()
    }

    private suspend fun searchArtistMatchedAudius(
        artistName: String,
        defaultGenre: String,
        limit: Int
    ): List<Track> {
        val cleanName = artistName.trim()
        if (cleanName.isEmpty()) return emptyList()
        val hits = searchAudiusFullTracks(cleanName, limit = limit.coerceAtLeast(20), defaultGenre = defaultGenre)
        return hits.filter { track ->
            matchesArtistName(track.artist, track.title, cleanName) &&
                !isNoiseSearchResult(track.title)
        }
    }

    private fun isNoiseSearchResult(title: String): Boolean {
        val norm = normalizeText(title)
        if (norm.isEmpty()) return false
        return NOISE_SEARCH_REGEXES.any { it.containsMatchIn(norm) }
    }

    private suspend fun fetchAudiusPlaylistFullTracks(playlistId: String): List<Track> {
        val pathAndQuery = "/v1/playlists/${encodePathSegment(playlistId)}/tracks?app_name=$AUDIUS_APP_NAME"
        for (host in AUDIUS_HOSTS) {
            val body = client.getText("$host$pathAndQuery") ?: continue
            val data = try {
                JSONObject(body).optJSONArray("data")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            } ?: continue
            return parseAudiusFullTracks(data, "Music")
        }
        return emptyList()
    }

    private suspend fun searchAudiusPlaylists(query: String, limit: Int): List<Playlist> {
        val pathAndQuery =
            "/v1/playlists/search?app_name=$AUDIUS_APP_NAME&limit=$limit&query=${encodeUrlParam(query)}"
        for (host in AUDIUS_HOSTS) {
            val body = client.getText("$host$pathAndQuery") ?: continue
            val data = try {
                JSONObject(body).optJSONArray("data")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            } ?: continue
            return buildList {
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val id = obj.optCleanString("id")
                    val name = obj.optCleanString("playlist_name").ifBlank { obj.optCleanString("name") }
                    if (id.isEmpty() || name.isEmpty()) continue
                    val (artworkUrl, _) = extractAudiusArtwork(obj.optJSONObject("artwork"))
                    val playlistId = "$AUDIUS_PLAYLIST_ID_PREFIX$id"
                    val (colorStart, colorEnd) = coverPaletteForKey(playlistId)
                    val author = obj.optJSONObject("user")?.optCleanString("name").orEmpty()
                        .ifBlank { "Audius Curator" }
                    add(
                        Playlist(
                            id = playlistId,
                            name = name,
                            description = obj.optCleanString("description"),
                            coverColorStart = colorStart,
                            coverColorEnd = colorEnd,
                            artworkUrl = artworkUrl,
                            isUserCreated = false,
                            isSystemMix = false,
                            authorName = author
                        )
                    )
                }
            }
        }
        return emptyList()
    }

    private fun parseAudiusFullTracks(array: JSONArray, defaultGenre: String): List<Track> = buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parseAudiusFullTrack(obj, defaultGenre)?.let(::add)
        }
    }

    private fun parseAudiusFullTrack(obj: JSONObject, defaultGenre: String): Track? {
        val id = obj.optCleanString("id")
        val title = obj.optCleanString("title")
        if (id.isEmpty() || title.isEmpty()) return null

        val accessStreamable = obj.optJSONObject("access")?.optBoolean("stream", true) ?: true
        val isStreamable = obj.optBoolean("is_streamable", true) && accessStreamable
        val durationSec = obj.optInt("duration", 0).coerceAtLeast(0)
        if (!isStreamable || durationSec < MIN_FULL_DURATION_SEC) {
            return null
        }

        val userObj = obj.optJSONObject("user")
        val artistName = userObj?.optCleanString("name")
            ?.ifBlank { userObj.optCleanString("handle") }
            .orEmpty()
        val artistId = userObj?.optCleanString("id").orEmpty()
        val genre = obj.optCleanString("genre").ifBlank { defaultGenre }.ifBlank { "Music" }
        val mood = obj.optCleanString("mood")
        val (rawArtworkUrl, mirrors) = extractAudiusArtwork(obj.optJSONObject("artwork"))
        val cachedMeta = lookupMetaByTitleArtist(title, artistName)
        if (RegionalContentFilter.isExcludedIndianContent(
                title = title,
                artist = artistName,
                album = cachedMeta?.album.orEmpty(),
                genre = genre,
                extra = mood
            )
        ) {
            return null
        }
        val artworkUrl = rawArtworkUrl ?: cachedMeta?.artworkUrl
        val ytId = youTubeApi.findMatchingVerifiedVideoId(title, artistName).orEmpty()

        return Track(
            id = "$AUDIUS_TRACK_ID_PREFIX$id",
            title = title,
            artist = artistName,
            artistId = artistId,
            album = cachedMeta?.album.orEmpty(),
            genre = genre,
            mood = mood,
            durationSec = durationSec,
            artworkUrl = artworkUrl,
            artworkMirrors = mirrors.ifEmpty { cachedMeta?.artworkMirrors.orEmpty() },
            streamUrl = "$AUDIUS_PRIMARY_HOST/v1/tracks/${encodePathSegment(id)}/stream?app_name=$AUDIUS_APP_NAME",
            source = TrackSource.AUDIUS,
            isLive = false,
            isSmartPick = false,
            releaseDate = obj.optCleanString("release_date").take(10).ifBlank { cachedMeta?.releaseDate.orEmpty() },
            playCount = obj.optLong("play_count", 0L).coerceAtLeast(0L),
            externalUrl = "https://audius.co/tracks/$id",
            isPreviewOnly = false,
            youtubeVideoId = ytId,
            hasMusicVideo = ytId.isNotBlank()
        )
    }

    private fun extractAudiusArtwork(artworkObj: JSONObject?): Pair<String?, List<String>> {
        if (artworkObj == null) return null to emptyList()
        val primary = artworkObj.optCleanString("480x480").ifBlank { null }
            ?: artworkObj.optCleanString("1000x1000").ifBlank { null }
            ?: artworkObj.optCleanString("150x150").ifBlank { null }

        val mirrorsArray = artworkObj.optJSONArray("mirrors")
        val mirrors = buildList {
            if (mirrorsArray != null) {
                for (i in 0 until mirrorsArray.length()) {
                    val value = mirrorsArray.optString(i, "").trim()
                    if (value.isNotEmpty() && value != "null") add(value)
                }
            }
        }
        return primary to mirrors
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

    private suspend fun fetchMultiArtistChartCandidates(
        artistIds: List<String>,
        genre: String,
        perArtist: Int
    ): List<Track> = coroutineScope {
        artistIds.chunked(3).flatMap { chunk ->
            chunk.map { id ->
                async { fetchDeezerChartCandidates("/artist/$id/top?limit=$perArtist", defaultGenre = genre) }
            }.awaitAll()
        }.flatten()
    }

    private suspend fun fetchDeezerChartCandidates(pathAndQuery: String, defaultGenre: String): List<Track> {
        val root = fetchDeezerJson(pathAndQuery) ?: return emptyList()
        val data = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                parseDeezerChartCandidate(item, defaultGenre)?.let(::add)
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
                    if (title.isNotEmpty() && artist.isNotEmpty() &&
                        !RegionalContentFilter.isExcludedIndianContent(title, artist)
                    ) {
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

    /**
     * Parses Deezer metadata ONLY (artwork, title, artist, album, duration, rank).
     * Never reads or stores Deezer's 30-second `preview` URL.
     */
    internal fun parseDeezerChartCandidate(obj: JSONObject, defaultGenre: String = "Music"): Track? {
        val rawId = obj.optCleanString("id")
        val title = obj.optCleanString("title").ifBlank { obj.optCleanString("title_short") }
        if (rawId.isEmpty() || rawId == "0" || title.isEmpty()) {
            return null
        }

        val artistObj = obj.optJSONObject("artist")
        val artistName = artistObj?.optCleanString("name").orEmpty()
        val artistId = artistObj?.optCleanString("id").orEmpty()
        val meta = extractDeezerMetadata(obj)
        if (RegionalContentFilter.isExcludedIndianContent(
                title = title,
                artist = artistName,
                album = meta?.album.orEmpty(),
                genre = defaultGenre
            )
        ) {
            return null
        }
        if (meta != null && artistName.isNotEmpty()) {
            cacheMetaForKey(title, artistName, meta)
        }

        val link = obj.optCleanString("link").ifBlank { "https://www.deezer.com/track/$rawId" }

        return Track(
            id = "$TRACK_ID_PREFIX$rawId",
            title = title,
            artist = artistName,
            artistId = artistId,
            album = meta?.album.orEmpty(),
            genre = defaultGenre.ifBlank { "Music" },
            durationSec = obj.optInt("duration", 0).coerceAtLeast(0),
            artworkUrl = meta?.artworkUrl,
            artworkMirrors = meta?.artworkMirrors.orEmpty(),
            streamUrl = "",
            source = TrackSource.DEEZER,
            isLive = false,
            isSmartPick = false,
            releaseDate = meta?.releaseDate.orEmpty(),
            playCount = meta?.playCount ?: 0L,
            externalUrl = link,
            isPreviewOnly = false
        )
    }

    private fun extractDeezerMetadata(obj: JSONObject): HydratedMeta? {
        val artistObj = obj.optJSONObject("artist")
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

        if (artworkUrl == null && albumTitle.isEmpty()) return null
        return HydratedMeta(
            artworkUrl = artworkUrl,
            artworkMirrors = mirrors,
            album = albumTitle,
            releaseDate = albumObj?.optCleanString("release_date").orEmpty(),
            playCount = obj.optLong("rank", 0L).coerceAtLeast(0L)
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
            ?: "Sonora Charts"

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

    private fun finalizeFullTrackList(tracks: List<Track>, limit: Int): List<Track> {
        val valid = RegionalContentFilter.filterTracks(tracks)
            .filter(::isValidFullAudioTrack)
            .map { if (it.isPreviewOnly) it.copy(isPreviewOnly = false) else it }
        return deduplicateTracks(valid).take(limit.coerceAtLeast(1))
    }

    private fun isValidFullAudioTrack(track: Track): Boolean {
        return !RegionalContentFilter.isExcludedTrack(track) &&
            track.source == TrackSource.AUDIUS &&
            !track.isPreviewOnly &&
            track.durationSec >= MIN_FULL_DURATION_SEC &&
            track.streamUrl.startsWith("http", ignoreCase = true) &&
            !FullAudioResolver.isPreviewAudioUrl(track.streamUrl)
    }

    private fun expandSearchAlias(query: String): String {
        val lower = query.lowercase(Locale.ROOT).trim()
        return when {
            lower == "maria deborah" || lower == "maria debora" || lower == "mariadeborah" ->
                "Mariah Deborah"
            lower.contains("maria deborah") ->
                lower.replace("maria deborah", "Mariah Deborah")
            else -> query
        }
    }

    private fun matchCuratedSeed(title: String, artist: String): SeedTrack? {
        val normTitle = normalizeTitleForMatch(title)
        val normArtist = normalizeText(artist)
        val primaryArtist = normalizeText(extractPrimaryArtist(artist))
        if (normTitle.isEmpty()) return null

        return ALL_SEED_TRACKS.firstOrNull { seed ->
            val seedTitle = normalizeTitleForMatch(seed.title)
            val seedPrimaryArtist = normalizeText(extractPrimaryArtist(seed.artist))
            val titleMatch = normTitle == seedTitle
            val artistMatch = normArtist.isEmpty() ||
                normArtist == normalizeText(seed.artist) ||
                (seedPrimaryArtist.length >= 2 && normArtist.contains(seedPrimaryArtist)) ||
                (primaryArtist.length >= 2 && seedPrimaryArtist == primaryArtist)
            titleMatch && artistMatch
        }
    }

    private fun titlesRoughlyMatch(expectedTitle: String, candidateTitle: String): Boolean {
        val a = normalizeTitleForMatch(expectedTitle)
        val b = normalizeTitleForMatch(candidateTitle)
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    private fun findMatchingCuratedSeeds(query: String): List<SeedTrack> {
        val effective = expandSearchAlias(query)
        val qNorm = normalizeText(effective)
        val qTokens = tokenizeWords(effective)
        if (qNorm.isEmpty()) return emptyList()

        return ALL_SEED_TRACKS.filter { seed ->
            val haystack = normalizeText("${seed.title} ${seed.artist} ${seed.genre}")
            val hayTokens = tokenizeWords(haystack)
            haystack.contains(qNorm) || (qTokens.isNotEmpty() && qTokens.all { token ->
                hayTokens.any { it == token || (token.length >= 3 && it.startsWith(token)) }
            })
        }
    }

    private fun scoreSearchRelevance(
        track: Track,
        queryNorm: String,
        queryTokens: List<String>,
        isCuratedSeed: Boolean
    ): Int {
        val titleNorm = normalizeText(track.title)
        val artistNorm = normalizeText(track.artist)
        val combinedNorm = "$titleNorm $artistNorm"
        var score = 0

        if (titleNorm == queryNorm) score += 120
        else if (artistNorm == queryNorm) score += 100
        else if (titleNorm.startsWith(queryNorm)) score += 70
        else if (artistNorm.startsWith(queryNorm)) score += 60
        else if (titleNorm.contains(queryNorm)) score += 45
        else if (combinedNorm.contains(queryNorm)) score += 35

        if (queryTokens.isNotEmpty()) {
            val combinedTokens = tokenizeWords(combinedNorm).toSet()
            val matched = queryTokens.count { q -> combinedTokens.any { it == q || it.startsWith(q) } }
            score += (matched * 20) / queryTokens.size
        }

        if (isCuratedSeed) score += 40
        val isVariant = VARIANT_KEYWORDS.any { titleNorm.contains(it) }
        if (!isVariant) score += 25
        return score
    }

    private fun matchesArtistName(trackArtist: String, trackTitle: String, targetArtistName: String): Boolean {
        val normTarget = normalizeText(targetArtistName)
        if (normTarget.isEmpty()) return false
        val targetTokens = tokenizeWords(targetArtistName)
        val artistNorm = normalizeText(trackArtist)
        val titleNorm = normalizeText(trackTitle)
        if (artistNorm.contains(normTarget) || titleNorm.contains(normTarget)) {
            return true
        }
        if (targetTokens.isEmpty()) return false
        val combinedTokens = (tokenizeWords(trackArtist) + tokenizeWords(trackTitle)).toSet()
        return targetTokens.all { it in combinedTokens }
    }

    private fun cacheMetaForKey(title: String, artist: String, meta: HydratedMeta) {
        val fullKey = normalizedTrackKey(title, artist)
        trackMetaByKeyCache.putIfAbsent(fullKey, meta)
        val primaryKey = normalizedTrackKey(title, extractPrimaryArtist(artist))
        trackMetaByKeyCache.putIfAbsent(primaryKey, meta)
    }

    private fun lookupMetaByTitleArtist(title: String, artist: String): HydratedMeta? {
        return trackMetaByKeyCache[normalizedTrackKey(title, artist)]
            ?: trackMetaByKeyCache[normalizedTrackKey(title, extractPrimaryArtist(artist))]
    }

    private fun candidateMetadataToHydrated(candidate: Track): HydratedMeta? {
        if (candidate.artworkUrl.isNullOrBlank() && candidate.album.isBlank()) return null
        return HydratedMeta(
            artworkUrl = candidate.artworkUrl,
            artworkMirrors = candidate.artworkMirrors,
            album = candidate.album,
            releaseDate = candidate.releaseDate,
            playCount = candidate.playCount
        )
    }

    private fun mergeHydratedMeta(primary: HydratedMeta?, secondary: HydratedMeta?): HydratedMeta? {
        if (primary == null) return secondary
        if (secondary == null) return primary
        return HydratedMeta(
            artworkUrl = primary.artworkUrl ?: secondary.artworkUrl,
            artworkMirrors = primary.artworkMirrors.ifEmpty { secondary.artworkMirrors },
            album = primary.album.ifBlank { secondary.album },
            releaseDate = primary.releaseDate.ifBlank { secondary.releaseDate },
            playCount = maxOf(primary.playCount, secondary.playCount)
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

    private fun extractPrimaryArtist(rawArtist: String): String {
        val cleaned = rawArtist.trim()
        if (cleaned.isEmpty()) return ""
        return cleaned.split(ARTIST_SPLIT_REGEX, limit = 2)
            .firstOrNull()
            ?.trim()
            .orEmpty()
    }

    private fun normalizeTitleForMatch(rawTitle: String): String {
        val stripped = rawTitle
            .replace(PAREN_REGEX, " ")
            .replace(BRACKET_REGEX, " ")
            .replace(FEAT_SUFFIX_REGEX, "")
        return normalizeText(stripped)
    }

    private fun normalizeText(raw: String): String {
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

    private fun tokenizeWords(raw: String): List<String> {
        val norm = normalizeText(raw)
        if (norm.isEmpty()) return emptyList()
        return norm.split(' ').filter { it.isNotEmpty() }
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

    private fun roundRobinInterleave(groups: List<List<Track>>): List<Track> {
        if (groups.isEmpty()) return emptyList()
        val maxLen = groups.maxOfOrNull { it.size } ?: 0
        val out = ArrayList<Track>(groups.sumOf { it.size })
        for (i in 0 until maxLen) {
            for (group in groups) {
                if (i < group.size) out.add(group[i])
            }
        }
        return out
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
        val seenStreamUrls = HashSet<String>(tracks.size)
        val seenTitleArtist = HashSet<String>(tracks.size)
        return buildList {
            for (t in tracks) {
                val key = normalizedTrackKey(t.title, t.artist)
                val streamOk = t.streamUrl.isBlank() || seenStreamUrls.add(t.streamUrl)
                if (seenIds.add(t.id) && streamOk && seenTitleArtist.add(key)) {
                    add(t)
                }
            }
        }
    }

    private fun normalizedTrackKey(title: String, artist: String): String =
        "${normalizeTitleForMatch(title)}|${normalizeText(artist)}"

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

    private data class HydratedMeta(
        val artworkUrl: String? = null,
        val artworkMirrors: List<String> = emptyList(),
        val album: String = "",
        val releaseDate: String = "",
        val playCount: Long = 0L
    )

    private data class SeedTrack(
        val title: String,
        val artist: String,
        val artistId: String,
        val audiusId: String,
        val durationSec: Int,
        val genre: String,
        val mood: String = "",
        val youtubeVideoId: String = ""
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
        private const val AUDIUS_PRIMARY_HOST = "https://api.audius.co"
        private const val AUDIUS_FALLBACK_HOST = "https://discoveryprovider.audius.co"
        private const val AUDIUS_APP_NAME = "Sonora"
        private val AUDIUS_HOSTS = listOf(AUDIUS_PRIMARY_HOST, AUDIUS_FALLBACK_HOST)

        private const val DEEZER_BASE = "https://api.deezer.com"
        private const val APPLE_PH_CHART_URL = "https://rss.marketingtools.apple.com/api/v2/ph/music/most-played/25/songs.json"
        private const val ITUNES_PH_SEARCH_URL = "https://itunes.apple.com/search?country=PH&media=music&entity=song"

        private const val AUDIUS_TRACK_ID_PREFIX = "aud_"
        private const val TRACK_ID_PREFIX = "dz_"
        private const val PLAYLIST_ID_PREFIX = "dz_pl_"
        private const val AUDIUS_PLAYLIST_ID_PREFIX = "aud_pl_"

        private const val PL_TOP_HITS_PH_2026 = "15507256221"
        private const val PL_OPM_VIRAL_HITS = "13461654563"
        private const val PL_DEEZER_TOP_PH = "1362518895"
        private const val PL_GLOBAL_TOP_50 = "3155776842"
        private const val PL_THROWBACK_OPM_2000S = "11970710661"

        private const val MIN_FULL_DURATION_SEC = 90
        private const val CACHE_TTL_MS = 60_000L
        private const val RATE_LIMIT_COOLDOWN_MS = 3_000L

        private const val ARTIST_CUP_OF_JOE = "60687062"
        private const val ARTIST_TJ_MONTERDE = "7189292"
        private const val ARTIST_SUNKISSED_LOLA = "179158087"
        private const val ARTIST_MAKI = "87317692"
        private const val ARTIST_DIONELA = "69165172"
        private const val ARTIST_BINI = "320392011"

        private val ALWAYS_BYPASS_SOURCES = setOf(
            TrackSource.LOCAL,
            TrackSource.PODCAST,
            TrackSource.AUDIOBOOK,
            TrackSource.RADIO,
            TrackSource.YOUTUBE
        )

        private val OPM_SPOTLIGHT_ARTIST_IDS = listOf(
            ARTIST_CUP_OF_JOE,
            ARTIST_TJ_MONTERDE,
            ARTIST_SUNKISSED_LOLA,
            ARTIST_MAKI,
            ARTIST_DIONELA,
            ARTIST_BINI
        )

        private val PAREN_REGEX = Regex("""\([^)]*\)""")
        private val BRACKET_REGEX = Regex("""\[[^\]]*]""")
        private val FEAT_SUFFIX_REGEX = Regex("""\s*\b(?:feat|ft)\.?\s+.*$""", RegexOption.IGNORE_CASE)
        private val ARTIST_SPLIT_REGEX = Regex(
            """\s*,\s*|\s+&\s+|\s*/\s*|\s*;\s*|\s+\b(?:feat\.?|ft\.?|with|x)\b\s+""",
            RegexOption.IGNORE_CASE
        )
        private val DIACRITICS_REGEX = Regex("""\p{InCombiningDiacriticalMarks}+""")
        private val NON_ALNUM_REGEX = Regex("""[^a-z0-9]""")
        private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")

        private val VARIANT_KEYWORDS = listOf(
            "remix", "sped up", "slowed", "nightcore", "8d", "reverb",
            "karaoke", "instrumental", "cover", "flip", "bootleg", "mashup"
        )

        private val NOISE_SEARCH_REGEXES = listOf(
            Regex("""\bkaraoke\b"""),
            Regex("""\binstrumental\b"""),
            Regex("""\btype\s*beat\b"""),
            Regex("""\bai\s*cover\b"""),
            Regex("""\bscrewed\b"""),
            Regex("""\bchopped\b"""),
            Regex("""\bscrewed\s*n\s*chopped\b"""),
            Regex("""\bdrill[\s-]*mix\b"""),
            Regex("""\bparody\b"""),
            Regex("""\bminus\s*\d+\b""")
        )

        private val PH_SEED_TRACKS = listOf(
            SeedTrack("Multo", "Cup of Joe", "60687062", "jz39y6M", 289, "OPM", "Emotional", "Rht8rS4cR1s"),
            SeedTrack("Tingin", "Cup of Joe, Janine Teñoso", "60687062", "VK6Zql6", 221, "OPM", "Romantic"),
            SeedTrack("Misteryoso", "Cup of Joe", "60687062", "agVv6Po", 223, "OPM", "Dreamy"),
            SeedTrack("Palagi", "TJ Monterde", "7189292", "gb4w8Mv", 240, "OPM", "Romantic"),
            SeedTrack("Dating Tayo", "TJ Monterde", "7189292", "zyM8wXp", 239, "OPM", "Nostalgic"),
            SeedTrack("Mahika", "Adie, Janine Berdin", "242188", "QKAWgZG", 204, "OPM", "Chill"),
            SeedTrack("Paraluman", "Adie", "242188", "Kd8vb0z", 310, "OPM", "Romantic"),
            SeedTrack("Dilaw", "Maki", "87317692", "92dRdP2", 192, "OPM", "Upbeat", "L215z9C4Zd8"),
            SeedTrack("Pasilyo", "SunKissed Lola", "179158087", "gvobVzq", 269, "OPM", "Romantic"),
            SeedTrack("Pagsamo", "Arthur Nery", "55640202", "7O2WQEP", 297, "OPM", "Soulful"),
            SeedTrack("Pelikula", "Janine Teñoso feat. Arthur Nery", "55640202", "80RvP5O", 206, "OPM", "Dreamy"),
            SeedTrack("Pantropiko", "BINI", "320392011", "xyGXRK9", 264, "P-Pop", "Sunny", "Zx31bB2vMns"),
            SeedTrack("Headshot", "Hev Abi", "126789982", "4kq0Zxr", 170, "Pinoy Hip-Hop", "Hype"),
            SeedTrack("Sining", "Dionela ft. Jay R", "69165172", "Y9jpAvW", 192, "R&B", "Smooth"),
            SeedTrack("Marilag", "Dionela", "69165172", "NvGQZpN", 199, "R&B", "Groovy", "bD9whtdq7dw"),
            SeedTrack("Raining in Manila", "Lola Amour", "12450156", "pRraNWG", 336, "Rock", "Feel Good"),
            SeedTrack("Malaya", "Moira Dela Torre", "6248262", "abPpq5w", 270, "Chill", "Emotional"),
            SeedTrack("Na Para Bang", "Mariah Deborah", "", "oWarYJg", 224, "OPM", "Heartfelt", "Y5z_4utl678"),
            SeedTrack("Tibok", "Earl Agustin", "", "XBRY4wb", 327, "OPM", "Romantic", "oiAtAzFLDfE"),
            SeedTrack("Sa Bawat Sandali", "Amiel Sol", "", "91Y6mNa", 304, "OPM", "Romantic"),
            SeedTrack("Uhaw (Tayong Lahat)", "Dilaw", "", "Y6ayEZp", 238, "OPM", "Rock"),
            SeedTrack("Ikot", "Over October", "", "5xOOMax", 254, "OPM", "Indie"),
            SeedTrack("Tulad Mo", "TJ Monterde", "7189292", "9mQdR90", 239, "OPM", "Acoustic"),
            SeedTrack("Hindi Tayo Pwede", "The Juans", "", "abjq0yQ", 295, "OPM", "Emotional"),
            SeedTrack("Ikaw Lang", "NOBITA", "", "4jMqqV9", 261, "OPM", "Romantic"),
            SeedTrack("711", "TONEEJAY", "", "732xGQA", 223, "OPM", "Upbeat"),
            SeedTrack("Miss Miss", "Rob Deniel", "109893432", "bKQyxay", 266, "OPM", "Retro"),
            SeedTrack("Kisame", "rhodessa", "", "3vd64ZB", 213, "OPM", "Dreamy"),
            SeedTrack("Tayo Na Lang Dalawa", "Mayonnaise", "", "lzPK7ww", 187, "OPM", "Upbeat"),
            SeedTrack("Estranghero", "Cup of Joe", "60687062", "bVEd8VJ", 184, "OPM", "Emotional"),
            SeedTrack("Kailan", "Maki", "87317692", "o4EWb97", 232, "OPM", "Chill"),
            SeedTrack("Saan?", "Maki", "87317692", "AKPMMVk", 225, "OPM", "Nostalgic"),
            SeedTrack("Umaasa", "CALEIN", "", "Rag1G1j", 338, "OPM", "Emotional"),
            SeedTrack("Magkabilang Mundo", "Jireh Lim", "", "320rRZJ", 238, "OPM", "Acoustic"),
            SeedTrack("Magbalik", "Callalily", "", "Nv0oBkx", 324, "Throwback OPM", "Nostalgic"),
            SeedTrack("Para Sa Streets", "Hev Abi", "126789982", "Alwy6ag", 190, "Pinoy Hip-Hop", "Hype"),
            SeedTrack("Kalimutan Ka", "Skusta Clee", "11321714", "02KPAK9", 321, "Pinoy Hip-Hop", "Emotional"),
            SeedTrack("Since Day One", "Skusta Clee ft. Flow G", "11321714", "JG2BR3k", 236, "Pinoy Hip-Hop", "Hype"),
            SeedTrack("Sandali", "mrld", "", "daMZzJ2", 223, "OPM", "Dreamy"),
            SeedTrack("Pag-Ibig ay Kanibalismo II", "fitterkarma", "", "7lbVX89", 207, "OPM", "Indie"),
            SeedTrack("Daleng Dale", "GAT", "", "91Km6Xo", 164, "P-Pop", "Upbeat"),
            SeedTrack("Randomantic", "James Reid", "", "vjVA2zK", 248, "OPM", "Romantic")
        )

        private val GLOBAL_SEED_TRACKS = listOf(
            SeedTrack("Die With A Smile", "Lady Gaga, Bruno Mars", "429675", "ZbZ4zZa", 249, "Pop", "Romantic", "kPa7bsKwL-c"),
            SeedTrack("Starboy", "The Weeknd, Daft Punk", "4050205", "ZPKqbAJ", 273, "R&B", "Hype", "34Na4j8AVgA"),
            SeedTrack("Birds of a Feather", "Billie Eilish", "9635624", "aW6rqjE", 230, "Pop", "Dreamy", "V9PVRfjEBTI"),
            SeedTrack("Cruel Summer", "Taylor Swift", "12246", "2lp1bg1", 179, "Pop", "Upbeat", "ic8j13piAhQ"),
            SeedTrack("Not Like Us", "Kendrick Lamar", "525046", "Oo1xMEQ", 273, "Hip-Hop", "Hype", "H58vbez_m4E"),
            SeedTrack("Drivers License", "Olivia Rodrigo", "11152580", "d0pMY5P", 247, "Pop", "Emotional", "ZmDBbnmKpqQ"),
            SeedTrack("Call Out My Name", "The Weeknd", "4050205", "KdmbB", 227, "R&B", "Emotional", "M4ZoCHID9GI"),
            SeedTrack("like JENNIE", "JENNIE", "1228100", "mw9mwr", 190, "K-Pop", "Hype", "JSFG-IE8n_c"),
            SeedTrack("lovely", "Billie Eilish, Khalid", "9635624", "NvQlZ", 200, "Pop", "Emotional", "V1Pl8CzNzCw"),
            SeedTrack("Blank Space", "Taylor Swift", "12246", "KVBWy", 272, "Pop", "Upbeat"),
            SeedTrack("The Hills", "The Weeknd", "4050205", "4aWNr", 265, "R&B", "Moody", "yzTuBuRdAyA"),
            SeedTrack("Fix You", "Coldplay", "892", "KbjZRxz", 296, "Rock", "Emotional", "k4V3Mo61fJM"),
            SeedTrack("Perfect", "Ed Sheeran", "384236", "8p9MqP", 260, "Pop", "Romantic", "2Vv-BfVoq4g"),
            SeedTrack("NASA", "Ariana Grande", "1562681", "ePO7X", 245, "Pop", "Upbeat"),
            SeedTrack("All The Stars", "Kendrick Lamar, SZA", "525046", "BZdx9dx", 235, "Hip-Hop", "Dramatic"),
            SeedTrack("LUNCH", "Billie Eilish", "9635624", "KddRRvB", 179, "Pop", "Upbeat"),
            SeedTrack("Often", "The Weeknd", "4050205", "Mp8PJwV", 250, "R&B", "Moody"),
            SeedTrack("On My Way", "Alan Walker, Sabrina Carpenter", "1176900", "WYrNGp9", 216, "Pop", "Upbeat"),
            SeedTrack("I Feel It Coming", "The Weeknd, Daft Punk", "4050205", "1VmZyaz", 265, "R&B", "Groovy", "qFLhGq0060w"),
            SeedTrack("HUMBLE.", "Kendrick Lamar", "525046", "qRmmZ0P", 185, "Hip-Hop", "Hype"),
            SeedTrack("Viva La Vida", "Coldplay", "892", "Z4bXj9v", 242, "Rock", "Upbeat", "dvgZkm1xWPE"),
            SeedTrack("Shake It Off", "Taylor Swift", "12246", "aW1JMYE", 221, "Pop", "Sunny"),
            SeedTrack("Wicked Games", "The Weeknd", "4050205", "kbmwoN", 331, "R&B", "Night Drive"),
            SeedTrack("goosebumps", "Travis Scott, Kendrick Lamar", "4495513", "zE37P", 250, "Hip-Hop", "Night Drive"),
            SeedTrack("Espresso", "Sabrina Carpenter", "1176900", "79kjd39", 160, "Pop", "Sunny", "eVli-tstM5E"),
            SeedTrack("We Can't Be Friends", "Ariana Grande", "1562681", "oE44v95", 297, "Pop", "Emotional", "KNtJGQkC-WI"),
            SeedTrack("Snooze", "SZA", "1193881", "1Ej98a5", 152, "R&B", "Chill"),
            SeedTrack("Kill Bill", "SZA", "1193881", "35M6A98", 149, "R&B", "Moody", "SQnc1QibapQ"),
            SeedTrack("Vampire", "Olivia Rodrigo", "11152580", "m7o6KJj", 208, "Pop", "Dramatic", "RlPNh_PBZb4"),
            SeedTrack("Golden", "HUNTR/X", "", "r9ywoPN", 192, "K-Pop", "Upbeat")
        )

        private val ALL_SEED_TRACKS = PH_SEED_TRACKS + GLOBAL_SEED_TRACKS

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
            CuratedArtistSeed("13817211", "Tate McRae", "https://cdn-images.dzcdn.net/images/artist/b12c2090606779c0ba39eac6e3d2708e/500x500-000000-80-0-0.jpg", 627797L, "Pop"),
            CuratedArtistSeed("892", "Coldplay", "https://cdn-images.dzcdn.net/images/artist/3087954bca22f306324912e5ac8375c3/500x500-000000-80-0-0.jpg", 18476242L, "Pop-Rock"),
            CuratedArtistSeed("384236", "Ed Sheeran", "https://cdn-images.dzcdn.net/images/artist/d6bb84390641d8ae9118228d9544e53d/500x500-000000-80-0-0.jpg", 20980362L, "Pop & Acoustic")
        )

        private val CURATED_ARTIST_NAME_BY_ID: Map<String, String> =
            (CURATED_PH_ARTISTS + CURATED_GLOBAL_ARTISTS).associate { it.id to it.name } +
                mapOf(
                    "1193881" to "SZA",
                    "4495513" to "Travis Scott"
                )

        private val ARTIST_PORTRAIT_BY_ID: Map<String, String> =
            (CURATED_PH_ARTISTS + CURATED_GLOBAL_ARTISTS).associate { it.id to it.avatarUrl } +
                mapOf(
                    "1193881" to "https://cdn-images.dzcdn.net/images/artist/88101c1614c17a7a42f01d7dc5e0d4aa/500x500-000000-80-0-0.jpg",
                    "4495513" to "https://cdn-images.dzcdn.net/images/artist/8d8316146026d7e6ce377e314536df62/500x500-000000-80-0-0.jpg"
                )

        private val CURATED_PLAYLISTS = listOf(
            CuratedPlaylistSeed(
                rawId = PL_TOP_HITS_PH_2026,
                name = "Top Hits Philippines 2026",
                description = "Hottest full-length tracks trending across the Philippines.",
                artworkUrl = "https://cdn-images.dzcdn.net/images/cover/f4c02f9d1ca510997e29370ee644eb96-ea05a09f71c650836e812c579134a34e-b755e304f6639c3a231f9f67832850a2-eb54a3a72b036ca5f45d3358eb40181b/500x500-000000-80-0-0.jpg",
                authorName = "Philippines Charts"
            ),
            CuratedPlaylistSeed(
                rawId = PL_OPM_VIRAL_HITS,
                name = "OPM Viral & Top Hits",
                description = "Essential full-length OPM, P-Pop, and viral Filipino favorites.",
                artworkUrl = "https://cdn-images.dzcdn.net/images/cover/a9ac1c2b00fc5cb5970892e2b460bcb8-eddde5230b09c97f5e222c2fa975d421-8e41af01b219dcbdd33a4244701ed2b3-579fb6c0f175d98e767ac94bce880348/500x500-000000-80-0-0.jpg",
                authorName = "OPM Select"
            ),
            CuratedPlaylistSeed(
                rawId = PL_DEEZER_TOP_PH,
                name = "Philippines Daily Top",
                description = "Daily top streamed full-length hits in the Philippines.",
                artworkUrl = "https://cdn-images.dzcdn.net/images/playlist/2d89a884d5c418fb217f461099f57095/500x500-000000-80-0-0.jpg",
                authorName = "PH Charts"
            ),
            CuratedPlaylistSeed(
                rawId = PL_GLOBAL_TOP_50,
                name = "Global Top 50",
                description = "Biggest international full-length tracks right now.",
                artworkUrl = "https://cdn-images.dzcdn.net/images/playlist/0b92c2f9e94a1a3650f0de769c4fc07c/500x500-000000-80-0-0.jpg",
                authorName = "Global Charts"
            ),
            CuratedPlaylistSeed(
                rawId = PL_THROWBACK_OPM_2000S,
                name = "Throwback OPM 2000s",
                description = "Classic Pinoy bands and nostalgic full-length OPM anthems.",
                artworkUrl = "https://cdn-images.dzcdn.net/images/cover/31dad1e3d13066d992376127114fb9d3-fe61fff7300ef02ce19d766c7e58ad26-c93c039515c9f48057d8472adc7b982f-db75d15f92b2a98153fa0205a2f99ec3/500x500-000000-80-0-0.jpg",
                authorName = "OPM Classics"
            )
        )
    }
}
