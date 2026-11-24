package com.sonora.music

import com.sonora.music.data.local.SonoraStore
import com.sonora.music.data.network.AudiobookApi
import com.sonora.music.data.network.FullAudioResolver
import com.sonora.music.data.network.HttpClientProvider
import com.sonora.music.data.network.LyricsApi
import com.sonora.music.data.network.MusicCatalogApi
import com.sonora.music.data.network.PodcastApi
import com.sonora.music.data.network.RadioApi
import com.sonora.music.data.repository.RecommendationEngine
import com.sonora.music.model.Audiobook
import com.sonora.music.model.AudioSettings
import com.sonora.music.model.EqPreset
import com.sonora.music.model.ListeningEvent
import com.sonora.music.model.Playlist
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class DataLayerTest {

    private lateinit var tempDir: File
    private lateinit var store: SonoraStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("sonora_store_test").toFile()
        store = SonoraStore(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun parseLrc_handlesCentisecondsMillisecondsMultiTagsAndSorting() {
        val api = LyricsApi()
        val lrc = """
            [ar:Midnight Drive]
            [ti:Neon Echoes]
            [01:05.250]Second verse line
            [00:12.50]First line here
            [00:30.00][01:30.10]Chorus repeated
            [00:05]Intro line
            [02:00.00]
        """.trimIndent()

        val parsed = api.parseLrc(lrc)
        assertEquals(5, parsed.size)
        assertEquals(5_000L, parsed[0].timeMs)
        assertEquals("Intro line", parsed[0].text)
        assertEquals(12_500L, parsed[1].timeMs)
        assertEquals("First line here", parsed[1].text)
        assertEquals(30_000L, parsed[2].timeMs)
        assertEquals("Chorus repeated", parsed[2].text)
        assertEquals(65_250L, parsed[3].timeMs)
        assertEquals("Second verse line", parsed[3].text)
        assertEquals(90_100L, parsed[4].timeMs)
        assertEquals("Chorus repeated", parsed[4].text)
    }

    @Test
    fun parseDurationToSeconds_handlesHhMmSsMmSsAndIntegerSeconds() {
        val api = PodcastApi()
        assertEquals(3723, api.parseDurationToSeconds("1:02:03"))
        assertEquals(754, api.parseDurationToSeconds("12:34"))
        assertEquals(420, api.parseDurationToSeconds("420"))
        assertEquals(95, api.parseDurationToSeconds("95.8"))
        assertEquals(0, api.parseDurationToSeconds(null))
        assertEquals(0, api.parseDurationToSeconds(""))
        assertEquals(0, api.parseDurationToSeconds("-10"))
        assertEquals(0, api.parseDurationToSeconds("invalid"))
    }

    @Test
    fun sonoraStore_persistsPlaylistsLikedFollowedSettingsHistoryAndSearches() {
        val sampleTrack = Track(
            id = "dz_101",
            title = "Multo",
            artist = "Cup of Joe",
            artistId = "60687062",
            album = "Silakbo",
            genre = "OPM",
            mood = "Emotional",
            durationSec = 238,
            artworkUrl = "https://cdn-images.dzcdn.net/images/cover/multo/500x500.jpg",
            artworkMirrors = listOf("https://cdn-images.dzcdn.net/images/cover/multo/1000x1000.jpg"),
            streamUrl = "https://cdnt-preview.dzcdn.net/api/1/1/multo.mp3",
            source = TrackSource.DEEZER,
            playCount = 950_000L,
            externalUrl = "https://www.deezer.com/track/101",
            isPreviewOnly = true
        )
        val playlist = Playlist(
            id = "pl_custom_1",
            name = "Manila Night Drive",
            description = "OPM & P-Pop essentials",
            trackIds = listOf(sampleTrack.id),
            tracks = listOf(sampleTrack),
            coverColorStart = 0xFF1DB954L,
            coverColorEnd = 0xFF121212L,
            artworkUrl = sampleTrack.artworkUrl,
            isUserCreated = true,
            isSystemMix = false,
            createdAt = 1_700_000_000_000L,
            authorName = "You"
        )

        store.savePlaylists(listOf(playlist))
        val loadedPlaylists = store.loadPlaylists()
        assertEquals(1, loadedPlaylists.size)
        assertEquals(playlist, loadedPlaylists.first())

        store.saveLikedSongs(listOf(sampleTrack))
        val loadedLiked = store.loadLikedSongs()
        assertEquals(listOf(sampleTrack), loadedLiked)
        assertEquals("https://www.deezer.com/track/101", loadedLiked.first().externalUrl)
        assertTrue(loadedLiked.first().isPreviewOnly)

        store.saveFollowedArtistIds(setOf("60687062", "320392011"))
        assertEquals(setOf("60687062", "320392011"), store.loadFollowedArtistIds())

        store.saveDownloadedTracks(listOf(sampleTrack.copy(localUri = "file:///tmp/dz_101.mp3")))
        val loadedDownload = store.loadDownloadedTracks().first()
        assertEquals("file:///tmp/dz_101.mp3", loadedDownload.localUri)
        assertEquals("https://www.deezer.com/track/101", loadedDownload.externalUrl)
        assertTrue(loadedDownload.isPreviewOnly)

        val settings = AudioSettings(
            eqEnabled = false,
            eqPreset = EqPreset.ELECTRONIC,
            bandLevelsMb = listOf(500, 150, -50, 350, 500),
            bassBoostStrength = 600,
            virtualizerStrength = 400,
            crossfadeSeconds = 6,
            playbackSpeed = 1.25f,
            privateSession = true,
            streamingQualityLabel = "High Quality"
        )
        store.saveAudioSettings(settings)
        assertEquals(settings, store.loadAudioSettings())

        // Recent searches capped at 12 and deduplicated
        val searches = (1..15).map { "query $it" } + listOf("query 1", "   ")
        store.saveRecentSearches(searches)
        val loadedSearches = store.loadRecentSearches()
        assertEquals(12, loadedSearches.size)
        assertEquals("query 1", loadedSearches.first())
        assertEquals("query 12", loadedSearches.last())

        // History capped at 1500
        for (i in 1..1505) {
            store.appendHistory(
                ListeningEvent(
                    trackId = "t_$i",
                    title = "Track $i",
                    artist = "Artist",
                    genre = "OPM",
                    listenedMs = 60_000L,
                    timestampMs = i.toLong()
                )
            )
        }
        val history = store.loadHistory()
        assertEquals(1500, history.size)
        assertEquals("t_6", history.first().trackId)
        assertEquals("t_1505", history.last().trackId)
    }

    @Test
    fun sonoraStore_playlistCodeRoundTrip_supportsPrefixDeepLinkAndRawJson() {
        val tracks = listOf(
            Track(
                id = "dz_201",
                title = "Pantropiko",
                artist = "BINI",
                durationSec = 225,
                streamUrl = "https://cdnt-preview.dzcdn.net/api/1/1/pantropiko.mp3",
                source = TrackSource.DEEZER,
                externalUrl = "https://www.deezer.com/track/201",
                isPreviewOnly = true
            ),
            Track(
                id = "it_202",
                title = "sining",
                artist = "Dionela",
                durationSec = 210,
                streamUrl = "https://audio-ssl.itunes.apple.com/sining.m4a",
                source = TrackSource.ITUNES,
                externalUrl = "https://music.apple.com/ph/album/sining",
                isPreviewOnly = true
            )
        )

        val code = store.encodePlaylistCode(
            name = "PH Top Mix",
            description = "OPM & P-Pop rotation",
            tracks = tracks,
            colorStart = 0xFF1DB954L,
            colorEnd = 0xFF121212L
        )
        assertTrue(code.startsWith("SONORA-PL-V1:"))

        val decodedDirect = store.decodePlaylistCode(code)
        assertNotNull(decodedDirect)
        assertEquals("PH Top Mix", decodedDirect!!.name)
        assertEquals("OPM & P-Pop rotation", decodedDirect.description)
        assertEquals(0xFF1DB954L, decodedDirect.coverColorStart)
        assertEquals(0xFF121212L, decodedDirect.coverColorEnd)
        assertEquals(tracks, decodedDirect.tracks)
        assertEquals(listOf("dz_201", "it_202"), decodedDirect.trackIds)

        val encodedQuery = URLEncoder.encode(code, StandardCharsets.UTF_8.name())
        val decodedDeepLink = store.decodePlaylistCode("sonora://playlist?code=$encodedQuery")
        assertNotNull(decodedDeepLink)
        assertEquals("PH Top Mix", decodedDeepLink!!.name)
        assertEquals(tracks, decodedDeepLink.tracks)

        val rawJson = """{"name":"Raw Mix","description":"Direct JSON","tracks":[{"id":"x1","title":"Echo","artist":"Zen"}]}"""
        val decodedRaw = store.decodePlaylistCode(rawJson)
        assertNotNull(decodedRaw)
        assertEquals("Raw Mix", decodedRaw!!.name)
        assertEquals(1, decodedRaw.tracks.size)
        assertEquals("Echo", decodedRaw.tracks.first().title)
        assertEquals(TrackSource.DEEZER, decodedRaw.tracks.first().source)

        assertNull(store.decodePlaylistCode("not-a-valid-code"))
        assertNull(store.decodePlaylistCode(""))
    }

    @Test
    fun recommendationEngine_parsePromptKeywords_extractsMoodGenresAndTerms() {
        val engine = RecommendationEngine(MusicCatalogApi())

        val (studyGenres, studyTerms) = engine.parsePromptKeywords("late night chill lofi study beats")
        assertTrue(studyGenres.contains("Lo-Fi"))
        assertTrue(studyGenres.contains("Ambient"))
        assertTrue(studyTerms.contains("study"))
        assertTrue(studyTerms.contains("lofi"))

        val (gymGenres, gymTerms) = engine.parsePromptKeywords("intense gym workout pump")
        assertTrue(gymGenres.contains("Electronic"))
        assertTrue(gymGenres.contains("Hip-Hop/Rap"))
        assertTrue(gymTerms.contains("workout"))

        val (opmGenres, opmTerms) = engine.parsePromptKeywords("late night opm hugot")
        assertTrue(opmGenres.contains("OPM"))
        assertTrue(opmTerms.contains("Cup of Joe"))

        val (defaultGenres, defaultTerms) = engine.parsePromptKeywords("")
        assertFalse(defaultGenres.isEmpty())
        assertFalse(defaultTerms.isEmpty())
    }

    @Test
    fun recommendationEngine_computeWrapped_aggregatesTopItemsAndPersona() {
        val engine = RecommendationEngine(MusicCatalogApi())

        val emptyStats = engine.computeWrapped(emptyList(), emptyList())
        assertEquals(0, emptyStats.totalMinutes)
        assertEquals(0, emptyStats.totalPlays)
        assertEquals("OPM & Global Listener", emptyStats.personaTitle)

        val history = listOf(
            ListeningEvent("t1", "Neon Sky", "Ayla", "Electronic", "https://art/1.jpg", 180_000L, 100L),
            ListeningEvent("t1", "Neon Sky", "Ayla", "Electronic", "https://art/1.jpg", 180_000L, 200L),
            ListeningEvent("t1", "Neon Sky", "Ayla", "Electronic", "https://art/1.jpg", 120_000L, 300L),
            ListeningEvent("t2", "Glass Wave", "Ayla", "Electronic", null, 240_000L, 400L),
            ListeningEvent("t3", "Warm Rain", "Koda", "Lo-Fi", "https://art/3.jpg", 180_000L, 500L)
        )
        val liked = listOf(
            Track(id = "t2", title = "Glass Wave", artist = "Ayla", genre = "Electronic", artworkUrl = "https://art/2.jpg")
        )

        val stats = engine.computeWrapped(history, liked)
        assertEquals(15, stats.totalMinutes)
        assertEquals(5, stats.totalPlays)
        assertEquals(3, stats.uniqueTracks)
        assertEquals(2, stats.uniqueArtists)

        assertEquals("Neon Sky", stats.topTracks.first().name)
        assertEquals(3, stats.topTracks.first().playCount)
        assertEquals(8, stats.topTracks.first().totalMinutes)

        assertEquals("Ayla", stats.topArtists.first().name)
        assertEquals(4, stats.topArtists.first().playCount)

        assertEquals("Electronic", stats.topGenres.first().name)
        assertEquals(4, stats.topGenres.first().playCount)
        assertEquals("Neon Architect", stats.personaTitle)

        val opmStats = engine.computeWrapped(
            listOf(ListeningEvent("dz_1", "Multo", "Cup of Joe", "OPM", null, 240_000L, 10L)),
            emptyList()
        )
        assertEquals("OPM Trailblazer", opmStats.personaTitle)
    }

    @Test
    fun musicCatalogApi_parsesDeezerItunesEnrichmentAndHandlesRateLimitsSafely() = runTest {
        val mockClient = createCatalogMockClient()
        val api = MusicCatalogApi(mockClient)

        // 1. Playlist parsing strips dz_pl_ prefix, filters empty preview, sets externalUrl & starts with isPreviewOnly=false
        val playlistTracks = api.getPlaylistTracks("dz_pl_15507256221")
        assertEquals(2, playlistTracks.size)
        val first = playlistTracks.first()
        assertEquals("dz_101", first.id)
        assertEquals("Multo", first.title)
        assertEquals("Cup of Joe", first.artist)
        assertEquals("60687062", first.artistId)
        assertEquals(TrackSource.DEEZER, first.source)
        assertFalse(first.isPreviewOnly)
        assertEquals("https://www.deezer.com/track/101", first.externalUrl)
        assertEquals("https://cdn.dzcdn.net/cover/101/500x500.jpg", first.artworkUrl)
        assertTrue(playlistTracks.none { it.id == "dz_999" })

        // 2. getPhTopTracks merges Deezer PH/OPM hits + Apple PH chart enriched via iTunes PH (600x600 artwork upgrade)
        val phTop = api.getPhTopTracks(limit = 10)
        assertEquals(4, phTop.size)
        assertTrue(phTop.any { it.id == "dz_103" })
        val enrichedItunes = phTop.firstOrNull { it.source == TrackSource.ITUNES }
        assertNotNull(enrichedItunes)
        assertEquals("it_777", enrichedItunes!!.id)
        assertFalse(enrichedItunes.isPreviewOnly)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/art/600x600bb.jpg", enrichedItunes.artworkUrl)

        // 3. searchTracks falls back to iTunes PH when Deezer returns quota error object
        val searchFallback = api.searchTracks("rate_limited_query", limit = 10)
        assertEquals(1, searchFallback.size)
        assertEquals(TrackSource.ITUNES, searchFallback.first().source)
        assertEquals("it_777", searchFallback.first().id)

        // 4. searchArtists sorts by highest nb_fan descending
        val artists = api.searchArtists("bini", limit = 10)
        assertEquals(2, artists.size)
        assertEquals("BINI", artists[0].name)
        assertEquals(25000L, artists[0].followerCount)
        assertEquals("Other Artist", artists[1].name)

        // 5. Rate-limited /artist/{id} never throws or returns empty curated artist shelves
        val phArtists = api.getPhTrendingArtists()
        assertEquals(18, phArtists.size)
        assertTrue(phArtists.any { it.name == "Cup of Joe" && !it.avatarUrl.isNullOrBlank() })
        assertTrue(phArtists.any { it.name == "BINI" && !it.avatarUrl.isNullOrBlank() })

        val globalArtists = api.getGlobalTrendingArtists()
        assertEquals(11, globalArtists.size)
        assertTrue(globalArtists.any { it.name == "Taylor Swift" && !it.avatarUrl.isNullOrBlank() })

        // 6. resolveFreshStreamUrl calls /track/{id} for fresh preview URL
        val refreshedUrl = api.resolveFreshStreamUrl(
            first.copy(streamUrl = "https://stale-preview.example.com/old.mp3")
        )
        assertEquals("https://cdnt-preview.dzcdn.net/fresh/101.mp3", refreshedUrl)
    }

    @Test
    fun recommendationEngine_buildsMadeForYouSmartShuffleRadioAndMoodMix() = runTest {
        val api = MusicCatalogApi(createCatalogMockClient())
        val engine = RecommendationEngine(api)

        val madeForYou = engine.buildMadeForYou(
            history = listOf(ListeningEvent("dz_101", "Multo", "Cup of Joe", "OPM", null, 180_000L)),
            likedSongs = emptyList(),
            followedArtistIds = setOf("60687062")
        )
        assertEquals(3, madeForYou.size)
        assertEquals("Discover Weekly", madeForYou[0].name)
        assertEquals("Release Radar", madeForYou[1].name)
        assertEquals("Daily Mix", madeForYou[2].name)
        assertTrue(madeForYou.all { it.tracks.isNotEmpty() })
        assertTrue(madeForYou.flatMap { it.tracks }.none { it.source == TrackSource.AUDIUS })

        val seed = Track(
            id = "dz_101",
            title = "Multo",
            artist = "Cup of Joe",
            artistId = "60687062",
            genre = "OPM",
            streamUrl = "https://cdnt-preview.dzcdn.net/101.mp3",
            source = TrackSource.DEEZER,
            isPreviewOnly = true
        )
        val picks = engine.getSmartShufflePicks(listOf(seed), excludeIds = setOf("dz_102"), count = 3)
        assertTrue(picks.isNotEmpty())
        assertTrue(picks.all { it.isSmartPick && it.id != "dz_101" && it.id != "dz_102" })

        val radio = engine.buildTrackRadio(seed)
        assertTrue(radio.size >= 2)
        assertEquals("dz_101", radio.first().id)

        val moodMix = engine.generateAiPlaylist("late night opm")
        assertTrue(moodMix.tracks.isNotEmpty())
    }

    @Test
    fun networkApis_lyricsPodcastAudiobookAndPhRadioParseAndFilterSafely() = runTest {
        val mockClient = createAuxApisMockClient()

        // LyricsApi fallback from /api/get 404 to /api/search with cleaned title
        val lyricsApi = LyricsApi(mockClient)
        val lyrics = lyricsApi.fetchLyrics(
            Track(
                id = "dz_101",
                title = "Multo (Official Audio) [Remix] feat. Guest",
                artist = "Cup of Joe",
                durationSec = 238
            )
        )
        assertEquals("dz_101", lyrics.trackKey)
        assertEquals(2, lyrics.syncedLines.size)
        assertEquals("Pure light", lyrics.syncedLines.first().text)

        // PodcastApi supports country=PH parameter cleanly
        val podcastApi = PodcastApi(mockClient)
        val shows = podcastApi.searchPodcasts("philippines", limit = 10, country = "PH")
        assertEquals(1, shows.size)
        assertEquals("Barangay Love Stories", shows.first().title)

        // AudiobookApi prefers 128kb / non-64kb and sorts chapters numerically
        val audiobookApi = AudiobookApi(mockClient)
        val chapters = audiobookApi.getChapters(Audiobook(id = "book_1", title = "Test Book", author = "Author"))
        assertEquals(2, chapters.size)
        assertEquals("Chapter One", chapters[0].title)
        assertEquals("https://archive.org/download/book_1/ch01.mp3", chapters[0].streamUrl)
        assertEquals("Chapter Two", chapters[1].title)
        assertEquals("https://archive.org/download/book_1/ch02_128kb.mp3", chapters[1].streamUrl)
        assertEquals(TrackSource.AUDIOBOOK, chapters[0].source)

        // RadioApi.getPhStations queries PH stations and filters out broken / blank / .pls streams
        val radioApi = RadioApi(mockClient)
        val phStations = radioApi.getPhStations(20)
        assertEquals(1, phStations.size)
        assertTrue(phStations.first().isLive)
        assertEquals(TrackSource.RADIO, phStations.first().source)
        assertEquals("96.3 Easy Rock - DWRK", phStations.first().title)

        // HttpClientProvider interceptor verification
        assertEquals(15_000, HttpClientProvider.client.connectTimeoutMillis)
        assertEquals(15_000, HttpClientProvider.client.readTimeoutMillis)
        assertTrue(HttpClientProvider.client.followRedirects)
        assertTrue(HttpClientProvider.client.followSslRedirects)
    }

    @Test
    fun fullAudioResolver_scoreCandidate_validatesMatchingCleanAndNoiseRules() {
        val resolver = FullAudioResolver()

        val cleanScore = resolver.scoreCandidate(
            targetTitle = "Multo",
            targetArtist = "Cup of Joe",
            targetDurationSec = 237,
            candidateTitle = "Multo - Cup of Joe (Official Audio)",
            candidateUploader = "SUTSU x YGG",
            candidateDurationSec = 240,
            isStreamable = true
        )
        assertTrue("Clean full song should pass threshold: $cleanScore", cleanScore >= 45)

        val remixScore = resolver.scoreCandidate(
            targetTitle = "Multo",
            targetArtist = "Cup of Joe",
            targetDurationSec = 237,
            candidateTitle = "Multo (Sped Up Remix)",
            candidateUploader = "Cup of Joe",
            candidateDurationSec = 237,
            isStreamable = true
        )
        assertTrue("Clean song ($cleanScore) must outscore remix ($remixScore)", cleanScore > remixScore)
        assertTrue("Unexpected remix must fall below threshold: $remixScore", remixScore < 45)

        assertEquals(
            -1000,
            resolver.scoreCandidate("Multo", "Cup of Joe", 237, "Multo", "Cup of Joe", 237, false)
        )
        assertEquals(
            -1000,
            resolver.scoreCandidate("Multo", "Cup of Joe", 237, "Multo", "Cup of Joe", 89, true)
        )
        assertEquals(
            -500,
            resolver.scoreCandidate("ERE", "juan karlos", 285, "Amanecer", "juan karlos", 144, true)
        )
        assertEquals(
            -500,
            resolver.scoreCandidate("ERE", "juan karlos", 285, "There", "juan karlos", 280, true)
        )

        val coverScore = resolver.scoreCandidate(
            targetTitle = "Pasilyo",
            targetArtist = "SunKissed Lola",
            targetDurationSec = 269,
            candidateTitle = "Pasilyo - SunKissed Lola (Jenzen Guino Cover)_256k",
            candidateUploader = "OPM Music Cover",
            candidateDurationSec = 261,
            isStreamable = true
        )
        assertTrue("Cover must be rejected: $coverScore", coverScore < 45)

        val accentedScore = resolver.scoreCandidate(
            targetTitle = "Tingin",
            targetArtist = "Cup of Joe",
            targetDurationSec = 221,
            candidateTitle = "Cup of Joe, Janine Teñoso - Tingin",
            candidateUploader = "SUTSU x YGG",
            candidateDurationSec = 221,
            isStreamable = true
        )
        assertTrue("Accented match should pass threshold: $accentedScore", accentedScore >= 45)

        val multiTokenGood = resolver.scoreCandidate(
            targetTitle = "Cruel Summer",
            targetArtist = "Taylor Swift",
            targetDurationSec = 178,
            candidateTitle = "Cruel Summer (Taylor's Version)",
            candidateUploader = "Taylor Swift",
            candidateDurationSec = 178,
            isStreamable = true
        )
        assertTrue(multiTokenGood >= 45)

        val multiTokenLowOverlap = resolver.scoreCandidate(
            targetTitle = "Cruel Summer",
            targetArtist = "Taylor Swift",
            targetDurationSec = 178,
            candidateTitle = "Cruel World",
            candidateUploader = "Taylor Swift",
            candidateDurationSec = 178,
            isStreamable = true
        )
        assertEquals(-500, multiTokenLowOverlap)

        val explicitRemixTarget = resolver.scoreCandidate(
            targetTitle = "Dilaw (Remix)",
            targetArtist = "Maki",
            targetDurationSec = 192,
            candidateTitle = "Dilaw (Remix)",
            candidateUploader = "Maki",
            candidateDurationSec = 192,
            isStreamable = true
        )
        assertTrue(explicitRemixTarget >= 45)
    }

    @Test
    fun fullAudioResolver_resolveFullTrack_probesHttp206AndFallsBackOn404() = runTest {
        val searchRequests = AtomicInteger(0)
        val probeRequests = AtomicInteger(0)
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.startsWith("https://api.audius.co/v1/tracks/search") &&
                        url.contains("Multo") -> {
                        searchRequests.incrementAndGet()
                        val body = """
                            {
                              "data": [
                                {
                                  "id": "aud_remix",
                                  "title": "Multo (Sped Up)",
                                  "duration": 237,
                                  "is_streamable": true,
                                  "user": { "name": "Cup of Joe", "handle": "cupofjoe" }
                                },
                                {
                                  "id": "aud_clean",
                                  "title": "Multo - Cup of Joe",
                                  "duration": 240,
                                  "is_streamable": true,
                                  "user": { "name": "SUTSU x YGG", "handle": "sutsu" }
                                }
                              ]
                            }
                        """.trimIndent()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url == "https://api.audius.co/v1/tracks/aud_clean/stream?app_name=Sonora" -> {
                        probeRequests.incrementAndGet()
                        assertEquals("bytes=0-63", req.header("Range"))
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(206)
                            .message("Partial Content")
                            .header("Content-Type", "audio/mpeg")
                            .body(ByteArray(64).toResponseBody("audio/mpeg".toMediaType()))
                            .build()
                    }
                    url.startsWith("https://api.audius.co/v1/tracks/search") &&
                        url.contains("ERE") -> {
                        val body = """
                            {
                              "data": [
                                {
                                  "id": "aud_amanecer",
                                  "title": "Amanecer",
                                  "duration": 144,
                                  "is_streamable": true,
                                  "user": { "name": "L.A", "handle": "la" }
                                }
                              ]
                            }
                        """.trimIndent()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url.startsWith("https://api.audius.co/v1/tracks/search") &&
                        url.contains("Dilaw") -> {
                        val body = """
                            {
                              "data": [
                                {
                                  "id": "aud_404",
                                  "title": "Maki - Dilaw (Lyrics)",
                                  "duration": 192,
                                  "is_streamable": true,
                                  "user": { "name": "YOWPAR", "handle": "yowpar" }
                                }
                              ]
                            }
                        """.trimIndent()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url == "https://api.audius.co/v1/tracks/aud_404/stream?app_name=Sonora" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("{}".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url.startsWith("https://api.audius.co/v1/tracks/search") &&
                        url.contains("Sining+Dionela%2C") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"data":[]}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url.startsWith("https://api.audius.co/v1/tracks/search") &&
                        url.contains("Sining+Dionela") -> {
                        val body = """
                            {
                              "data": [
                                {
                                  "id": "aud_sining",
                                  "title": "Sining - Dionela",
                                  "duration": 210,
                                  "is_streamable": true,
                                  "user": { "name": "Dionela", "handle": "dionela" }
                                }
                              ]
                            }
                        """.trimIndent()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url == "https://api.audius.co/v1/tracks/aud_sining/stream?app_name=Sonora" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ByteArray(64).toResponseBody("application/octet-stream".toMediaType()))
                            .build()
                    }
                    else -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("{}".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                }
            })
            .build()

        val resolver = FullAudioResolver(mockClient)

        // 1. HTTP 206 audio/mpeg upgrades preview track to full Audius stream and caches result
        val multo = Track(
            id = "dz_101",
            title = "Multo",
            artist = "Cup of Joe",
            durationSec = 237,
            streamUrl = "https://cdnt-preview.dzcdn.net/101.mp3",
            source = TrackSource.DEEZER
        )
        var fallbackCalls = 0
        val resolvedMulto = resolver.resolveFullTrack(multo) {
            fallbackCalls++
            "https://cdnt-preview.dzcdn.net/fresh/101.mp3"
        }
        assertEquals("https://api.audius.co/v1/tracks/aud_clean/stream?app_name=Sonora", resolvedMulto.streamUrl)
        assertEquals(240, resolvedMulto.durationSec)
        assertFalse(resolvedMulto.isPreviewOnly)
        assertEquals(0, fallbackCalls)
        assertEquals(1, searchRequests.get())
        assertEquals(1, probeRequests.get())

        val cachedMulto = resolver.resolveFullTrack(multo) {
            fallbackCalls++
            "https://cdnt-preview.dzcdn.net/fresh/101.mp3"
        }
        assertEquals(resolvedMulto, cachedMulto)
        assertEquals(1, searchRequests.get())
        assertEquals(1, probeRequests.get())

        // 2. Rejected candidate ("Amanecer" for "ERE - juan karlos") triggers preview fallback
        val ere = Track(
            id = "dz_202",
            title = "ERE",
            artist = "juan karlos",
            durationSec = 285,
            streamUrl = "https://cdnt-preview.dzcdn.net/202.mp3",
            source = TrackSource.DEEZER
        )
        val resolvedEre = resolver.resolveFullTrack(ere) {
            "https://cdnt-preview.dzcdn.net/fresh/ere.mp3"
        }
        assertEquals("https://cdnt-preview.dzcdn.net/fresh/ere.mp3", resolvedEre.streamUrl)
        assertTrue(resolvedEre.isPreviewOnly)

        // 3. HTTP 404 on stream probe triggers preview fallback
        val dilaw = Track(
            id = "it_303",
            title = "Dilaw",
            artist = "Maki",
            durationSec = 192,
            streamUrl = "https://audio-ssl.itunes.apple.com/dilaw.m4a",
            source = TrackSource.ITUNES
        )
        val resolvedDilaw = resolver.resolveFullTrack(dilaw) {
            "https://cdnt-preview.dzcdn.net/fresh/dilaw.mp3"
        }
        assertEquals("https://cdnt-preview.dzcdn.net/fresh/dilaw.mp3", resolvedDilaw.streamUrl)
        assertTrue(resolvedDilaw.isPreviewOnly)

        // 4. Secondary query (clean title + first artist) succeeds when multi-artist query returns empty
        val sining = Track(
            id = "dz_404",
            title = "Sining (Official Video)",
            artist = "Dionela, Jay R",
            durationSec = 210,
            streamUrl = "https://cdnt-preview.dzcdn.net/404.mp3",
            source = TrackSource.DEEZER
        )
        val resolvedSining = resolver.resolveFullTrack(sining) {
            "https://cdnt-preview.dzcdn.net/fresh/404.mp3"
        }
        assertEquals("https://api.audius.co/v1/tracks/aud_sining/stream?app_name=Sonora", resolvedSining.streamUrl)
        assertFalse(resolvedSining.isPreviewOnly)

        // 5. Bypass sources return track unchanged
        listOf(
            TrackSource.LOCAL,
            TrackSource.PODCAST,
            TrackSource.AUDIOBOOK,
            TrackSource.RADIO,
            TrackSource.AUDIUS
        ).forEach { src ->
            val t = Track(
                id = "bypass_${src.name}",
                title = "Multo",
                artist = "Cup of Joe",
                streamUrl = "https://example.com/${src.name}.mp3",
                source = src
            )
            var called = false
            val out = resolver.resolveFullTrack(t) {
                called = true
                "https://unexpected.example.com"
            }
            assertEquals(t, out)
            assertFalse(called)
        }
    }

    @Test
    fun musicCatalogApi_resolvePlayableTrack_upgradesAndFallsBackCleanly() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.startsWith("https://api.audius.co/v1/tracks/search") &&
                        url.contains("Multo") -> {
                        val body = """
                            {
                              "data": [
                                {
                                  "id": "aud_101",
                                  "title": "Multo - Cup of Joe",
                                  "duration": 240,
                                  "is_streamable": true,
                                  "user": { "name": "SUTSU x YGG", "handle": "sutsu" }
                                }
                              ]
                            }
                        """.trimIndent()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url == "https://api.audius.co/v1/tracks/aud_101/stream?app_name=Sonora" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(206)
                            .message("Partial Content")
                            .body(ByteArray(64).toResponseBody("audio/mpeg".toMediaType()))
                            .build()
                    }
                    url == "https://api.deezer.com/track/103" -> {
                        val body = """
                            {
                              "id": 103,
                              "title": "sining",
                              "preview": "https://cdnt-preview.dzcdn.net/fresh/103.mp3"
                            }
                        """.trimIndent()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    else -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("{}".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                }
            })
            .build()

        val api = MusicCatalogApi(mockClient)

        val resolvedMulto = api.resolvePlayableTrack(
            Track(
                id = "dz_101",
                title = "Multo",
                artist = "Cup of Joe",
                durationSec = 238,
                streamUrl = "https://cdnt-preview.dzcdn.net/101.mp3",
                source = TrackSource.DEEZER,
                isPreviewOnly = false
            )
        )
        assertEquals("https://api.audius.co/v1/tracks/aud_101/stream?app_name=Sonora", resolvedMulto.streamUrl)
        assertFalse(resolvedMulto.isPreviewOnly)

        val resolvedSining = api.resolvePlayableTrack(
            Track(
                id = "dz_103",
                title = "sining",
                artist = "Dionela",
                durationSec = 210,
                streamUrl = "https://cdnt-preview.dzcdn.net/103.mp3",
                source = TrackSource.DEEZER,
                isPreviewOnly = false
            )
        )
        assertEquals("https://cdnt-preview.dzcdn.net/fresh/103.mp3", resolvedSining.streamUrl)
        assertTrue(resolvedSining.isPreviewOnly)
    }

    private fun createCatalogMockClient(): OkHttpClient {
        val sampleTrack101 = """
            {
              "id": 101,
              "title": "Multo",
              "duration": 238,
              "rank": 900000,
              "link": "https://www.deezer.com/track/101",
              "preview": "https://cdnt-preview.dzcdn.net/101.mp3",
              "artist": { "id": 60687062, "name": "Cup of Joe" },
              "album": {
                "id": 5001,
                "title": "Silakbo",
                "cover_big": "https://cdn.dzcdn.net/cover/101/500x500.jpg",
                "cover_xl": "https://cdn.dzcdn.net/cover/101/1000x1000.jpg",
                "release_date": "2024-09-14"
              }
            }
        """.trimIndent()

        val sampleTrack102 = """
            {
              "id": 102,
              "title": "Pantropiko",
              "duration": 225,
              "rank": 850000,
              "link": "https://www.deezer.com/track/102",
              "preview": "https://cdnt-preview.dzcdn.net/102.mp3",
              "artist": { "id": 320392011, "name": "BINI" },
              "album": {
                "id": 5002,
                "title": "Pantropiko",
                "cover_big": "https://cdn.dzcdn.net/cover/102/500x500.jpg",
                "release_date": "2023-11-17"
              }
            }
        """.trimIndent()

        val sampleTrack103 = """
            {
              "id": 103,
              "title": "sining",
              "duration": 210,
              "rank": 800000,
              "link": "https://www.deezer.com/track/103",
              "preview": "https://cdnt-preview.dzcdn.net/103.mp3",
              "artist": { "id": 69165172, "name": "Dionela" },
              "album": {
                "id": 5003,
                "title": "sining",
                "cover_big": "https://cdn.dzcdn.net/cover/103/500x500.jpg",
                "release_date": "2024-06-17"
              }
            }
        """.trimIndent()

        val emptyPreviewTrack = """
            {
              "id": 999,
              "title": "Unplayable Song",
              "duration": 200,
              "link": "https://www.deezer.com/track/999",
              "preview": "",
              "artist": { "id": 1, "name": "Someone" },
              "album": { "id": 2, "title": "Album" }
            }
        """.trimIndent()

        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val (code, body) = when {
                    url.startsWith("https://api.deezer.com/playlist/15507256221/tracks") -> 200 to """
                        { "data": [ $sampleTrack101, $emptyPreviewTrack, $sampleTrack102 ] }
                    """.trimIndent()

                    url.startsWith("https://api.deezer.com/playlist/13461654563/tracks") ||
                        url.startsWith("https://api.deezer.com/playlist/3155776842/tracks") ||
                        url.startsWith("https://api.deezer.com/chart/0/tracks") -> 200 to """
                        { "data": [ $sampleTrack101, $sampleTrack102, $sampleTrack103 ] }
                    """.trimIndent()

                    url.startsWith("https://api.deezer.com/artist/") && url.contains("/top") -> 200 to """
                        { "data": [ $sampleTrack101, $sampleTrack103 ] }
                    """.trimIndent()

                    url.startsWith("https://api.deezer.com/artist/") && url.contains("/related") -> 200 to """
                        {
                          "data": [
                            { "id": 320392011, "name": "BINI", "nb_fan": 25000, "picture_big": "https://cdn.dzcdn.net/bini.jpg" }
                          ]
                        }
                    """.trimIndent()

                    url.startsWith("https://api.deezer.com/artist/") -> 429 to """
                        { "error": { "type": "Exception", "message": "Quota limit exceeded", "code": 4 } }
                    """.trimIndent()

                    url.startsWith("https://api.deezer.com/search/track") && url.contains("rate_limited_query") -> 200 to """
                        { "error": { "type": "Exception", "message": "Quota limit exceeded", "code": 4 } }
                    """.trimIndent()

                    url.startsWith("https://api.deezer.com/search/track") -> 200 to """
                        { "data": [ $sampleTrack102, $sampleTrack103 ] }
                    """.trimIndent()

                    url.startsWith("https://api.deezer.com/search/artist") -> 200 to """
                        {
                          "data": [
                            { "id": 111, "name": "Other Artist", "nb_fan": 500, "picture_big": "https://cdn.dzcdn.net/other.jpg" },
                            { "id": 320392011, "name": "BINI", "nb_fan": 25000, "picture_big": "https://cdn.dzcdn.net/bini.jpg" }
                          ]
                        }
                    """.trimIndent()

                    url == "https://api.deezer.com/track/101" -> 200 to """
                        {
                          "id": 101,
                          "title": "Multo",
                          "preview": "https://cdnt-preview.dzcdn.net/fresh/101.mp3"
                        }
                    """.trimIndent()

                    url.startsWith("https://rss.marketingtools.apple.com/api/v2/ph/music/most-played") -> 200 to """
                        {
                          "feed": {
                            "results": [
                              { "name": "Dilaw", "artistName": "Maki" }
                            ]
                          }
                        }
                    """.trimIndent()

                    url.startsWith("https://itunes.apple.com/search") -> 200 to """
                        {
                          "resultCount": 1,
                          "results": [
                            {
                              "trackId": 777,
                              "trackName": "Dilaw",
                              "artistName": "Maki",
                              "artistId": 999888,
                              "collectionName": "Dilaw - Single",
                              "primaryGenreName": "Pop",
                              "trackTimeMillis": 212000,
                              "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/art/100x100bb.jpg",
                              "previewUrl": "https://audio-ssl.itunes.apple.com/dilaw.m4a",
                              "trackViewUrl": "https://music.apple.com/ph/album/dilaw"
                            }
                          ]
                        }
                    """.trimIndent()

                    else -> 404 to "{}"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Error")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
    }

    private fun createAuxApisMockClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val (code, body) = when {
                    url.startsWith("https://lrclib.net/api/get") -> 404 to """{"message":"Not found"}"""
                    url.startsWith("https://lrclib.net/api/search") -> 200 to """
                        [
                          {
                            "id": 1,
                            "duration": 238,
                            "instrumental": false,
                            "plainLyrics": "Pure light\nShining bright",
                            "syncedLyrics": "[00:05.00]Pure light\n[00:10.00]Shining bright"
                          }
                        ]
                    """.trimIndent()
                    url.startsWith("https://itunes.apple.com/search") && url.contains("media=podcast") && url.contains("country=PH") -> 200 to """
                        {
                          "results": [
                            {
                              "collectionId": 55501,
                              "collectionName": "Barangay Love Stories",
                              "artistName": "Papa Dudut",
                              "feedUrl": "https://example.com/bls.rss",
                              "artworkUrl600": "https://example.com/bls600.jpg",
                              "primaryGenreName": "Society & Culture"
                            }
                          ]
                        }
                    """.trimIndent()
                    url.startsWith("https://archive.org/metadata/book_1/files") -> 200 to """
                        {
                          "result": [
                            {"name": "ch02_64kb.mp3", "format": "64Kbps MP3", "track": "2", "length": "05:00"},
                            {"name": "ch02_128kb.mp3", "format": "128Kbps MP3", "track": "2", "length": "05:00", "title": "Chapter Two"},
                            {"name": "ch01_64kb.mp3", "format": "64Kbps MP3", "track": "1", "length": "240"},
                            {"name": "ch01.mp3", "format": "VBR MP3", "track": "1", "length": "240", "title": "Chapter One"},
                            {"name": "cover.jpg", "format": "JPEG"}
                          ]
                        }
                    """.trimIndent()
                    url.startsWith("https://de1.api.radio-browser.info/json/stations/bycountrycodeexact/PH") -> 200 to """
                        [
                          {
                            "stationuuid": "ph-ok",
                            "name": "96.3 Easy Rock - DWRK",
                            "url_resolved": "https://azura.easyrock.com.ph/listen/easy_rock_manila/radio.mp3",
                            "favicon": "https://easyrock.com.ph/logo.png",
                            "tags": "easy listening,pop",
                            "country": "The Philippines",
                            "bitrate": 128,
                            "clickcount": 1200,
                            "lastcheckok": 1
                          },
                          {
                            "stationuuid": "ph-broken",
                            "name": "Dead Station",
                            "url_resolved": "https://dead.example.com/stream",
                            "lastcheckok": 0
                          },
                          {
                            "stationuuid": "ph-pls",
                            "name": "PLS Playlist Station",
                            "url_resolved": "http://example.com/listen.pls",
                            "lastcheckok": 1
                          },
                          {
                            "stationuuid": "ph-blank",
                            "name": "Blank URL Station",
                            "url_resolved": "",
                            "lastcheckok": 1
                          }
                        ]
                    """.trimIndent()
                    else -> 404 to "{}"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Error")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
    }
}
