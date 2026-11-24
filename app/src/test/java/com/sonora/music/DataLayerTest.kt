package com.sonora.music

import com.sonora.music.data.local.SonoraStore
import com.sonora.music.data.network.AudiobookApi
import com.sonora.music.data.network.FullAudioResolver
import com.sonora.music.data.network.HttpClientProvider
import com.sonora.music.data.network.LyricsApi
import com.sonora.music.data.network.MusicCatalogApi
import com.sonora.music.data.network.PodcastApi
import com.sonora.music.data.network.RadioApi
import com.sonora.music.data.network.RegionalContentFilter
import com.sonora.music.data.network.YouTubeCatalogApi
import com.sonora.music.data.repository.RecommendationEngine
import com.sonora.music.model.Artist
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
            id = "aud_jz39y6M",
            title = "Multo",
            artist = "Cup of Joe",
            artistId = "60687062",
            album = "Silakbo",
            genre = "OPM",
            mood = "Emotional",
            durationSec = 289,
            artworkUrl = "https://cdn-images.dzcdn.net/images/cover/multo/500x500.jpg",
            artworkMirrors = listOf("https://cdn-images.dzcdn.net/images/cover/multo/1000x1000.jpg"),
            streamUrl = "https://api.audius.co/v1/tracks/jz39y6M/stream?app_name=Sonora",
            source = TrackSource.AUDIUS,
            playCount = 950_000L,
            externalUrl = "https://audius.co/tracks/jz39y6M",
            isPreviewOnly = false
        )
        val legacyDeezerTrack = Track(
            id = "dz_101",
            title = "Tingin",
            artist = "Cup of Joe",
            durationSec = 221,
            artworkUrl = "https://cdn-images.dzcdn.net/images/cover/tingin/500x500.jpg",
            streamUrl = "https://cdnt-preview.dzcdn.net/api/1/1/tingin.mp3",
            source = TrackSource.DEEZER,
            isPreviewOnly = true
        )
        val legacyItunesTrack = Track(
            id = "it_202",
            title = "Dilaw",
            artist = "Maki",
            durationSec = 192,
            streamUrl = "https://audio-ssl.itunes.apple.com/itunes-assets/dilaw.m4a",
            source = TrackSource.ITUNES,
            isPreviewOnly = true
        )
        val playlist = Playlist(
            id = "pl_custom_1",
            name = "Manila Night Drive",
            description = "OPM & P-Pop essentials",
            trackIds = listOf(sampleTrack.id, legacyDeezerTrack.id),
            tracks = listOf(sampleTrack, legacyDeezerTrack),
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
        val loadedTracks = loadedPlaylists.first().tracks
        assertEquals(sampleTrack, loadedTracks[0])
        // Legacy 30s preview URLs are sanitized so they re-resolve to full length
        assertEquals("", loadedTracks[1].streamUrl)
        assertFalse(loadedTracks[1].isPreviewOnly)

        store.saveLikedSongs(listOf(sampleTrack, legacyDeezerTrack, legacyItunesTrack))
        val loadedLiked = store.loadLikedSongs()
        assertEquals(3, loadedLiked.size)
        assertEquals(sampleTrack, loadedLiked[0])
        assertEquals("", loadedLiked[1].streamUrl)
        assertFalse(loadedLiked[1].isPreviewOnly)
        assertEquals("", loadedLiked[2].streamUrl)
        assertFalse(loadedLiked[2].isPreviewOnly)

        store.saveFollowedArtistIds(setOf("60687062", "320392011"))
        assertEquals(setOf("60687062", "320392011"), store.loadFollowedArtistIds())

        store.saveDownloadedTracks(
            listOf(
                sampleTrack.copy(localUri = "file:///tmp/aud_jz39y6M.mp3"),
                legacyDeezerTrack.copy(localUri = "file:///tmp/dz_101.mp3")
            )
        )
        val loadedDownloads = store.loadDownloadedTracks()
        assertEquals("file:///tmp/aud_jz39y6M.mp3", loadedDownloads[0].localUri)
        assertEquals("https://api.audius.co/v1/tracks/jz39y6M/stream?app_name=Sonora", loadedDownloads[0].streamUrl)
        assertFalse(loadedDownloads[0].isPreviewOnly)
        assertEquals("file:///tmp/dz_101.mp3", loadedDownloads[1].localUri)
        assertEquals("", loadedDownloads[1].streamUrl)
        assertFalse(loadedDownloads[1].isPreviewOnly)

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

        // YouTube MV fields + imported YouTube tracks persistence & regional/preview sanitization
        val hybridTrack = sampleTrack.copy(
            youtubeVideoId = "Rht8rS4cR1s",
            hasMusicVideo = true
        )
        val videoOnlyTrack = Track(
            id = "yt_S7BKWs2Tirg",
            title = "Mahal Magmahal",
            artist = "Esremborak",
            genre = "OPM",
            durationSec = 248,
            artworkUrl = "https://i.ytimg.com/vi/S7BKWs2Tirg/hqdefault.jpg",
            streamUrl = "",
            source = TrackSource.YOUTUBE,
            externalUrl = "https://www.youtube.com/watch?v=S7BKWs2Tirg",
            isPreviewOnly = false,
            youtubeVideoId = "S7BKWs2Tirg",
            hasMusicVideo = true
        )
        assertFalse(hybridTrack.isVideoOnly)
        assertTrue(videoOnlyTrack.isVideoOnly)
        assertEquals("VIDEO", TrackSource.YOUTUBE.badgeLabel)
        assertEquals("https://www.youtube.com/watch?v=S7BKWs2Tirg", videoOnlyTrack.effectiveYoutubeUrl)

        val excludedIndianTrack = Track(
            id = "yt_ind123",
            title = "Kesariya",
            artist = "Arijit Singh",
            genre = "Bollywood",
            durationSec = 210,
            source = TrackSource.YOUTUBE,
            youtubeVideoId = "BddP6PYo2gs",
            hasMusicVideo = true
        )
        store.saveImportedYouTubeTracks(listOf(videoOnlyTrack, hybridTrack, excludedIndianTrack))
        val loadedImportedVideos = store.getImportedYouTubeTracks()
        assertEquals(2, loadedImportedVideos.size)
        assertEquals("S7BKWs2Tirg", loadedImportedVideos[0].youtubeVideoId)
        assertTrue(loadedImportedVideos[0].hasMusicVideo)
        assertEquals("Rht8rS4cR1s", loadedImportedVideos[1].youtubeVideoId)
        assertTrue(loadedImportedVideos[1].hasMusicVideo)
    }

    @Test
    fun sonoraStore_playlistCodeRoundTrip_supportsPrefixDeepLinkAndRawJson() {
        val fullTracks = listOf(
            Track(
                id = "aud_xyGXRK9",
                title = "Pantropiko",
                artist = "BINI",
                durationSec = 264,
                streamUrl = "https://api.audius.co/v1/tracks/xyGXRK9/stream?app_name=Sonora",
                source = TrackSource.AUDIUS,
                externalUrl = "https://audius.co/tracks/xyGXRK9",
                isPreviewOnly = false
            ),
            Track(
                id = "aud_Y9jpAvW",
                title = "Sining",
                artist = "Dionela ft. Jay R",
                durationSec = 192,
                streamUrl = "https://api.audius.co/v1/tracks/Y9jpAvW/stream?app_name=Sonora",
                source = TrackSource.AUDIUS,
                externalUrl = "https://audius.co/tracks/Y9jpAvW",
                isPreviewOnly = false
            )
        )

        val code = store.encodePlaylistCode(
            name = "PH Top Mix",
            description = "OPM & P-Pop rotation",
            tracks = fullTracks,
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
        assertEquals(fullTracks, decodedDirect.tracks)
        assertEquals(listOf("aud_xyGXRK9", "aud_Y9jpAvW"), decodedDirect.trackIds)

        val encodedQuery = URLEncoder.encode(code, StandardCharsets.UTF_8.name())
        val decodedDeepLink = store.decodePlaylistCode("sonora://playlist?code=$encodedQuery")
        assertNotNull(decodedDeepLink)
        assertEquals("PH Top Mix", decodedDeepLink!!.name)
        assertEquals(fullTracks, decodedDeepLink.tracks)

        // Legacy playlist codes carrying 30s preview URLs get sanitized on import
        val legacyCode = store.encodePlaylistCode(
            name = "Legacy Mix",
            description = "Old links",
            tracks = listOf(
                Track(
                    id = "dz_201",
                    title = "Pantropiko",
                    artist = "BINI",
                    durationSec = 225,
                    streamUrl = "https://cdnt-preview.dzcdn.net/api/1/1/pantropiko.mp3",
                    source = TrackSource.DEEZER,
                    isPreviewOnly = true
                ),
                Track(
                    id = "it_202",
                    title = "Sining",
                    artist = "Dionela",
                    durationSec = 210,
                    streamUrl = "https://audio-ssl.itunes.apple.com/sining.m4a",
                    source = TrackSource.ITUNES,
                    isPreviewOnly = true
                )
            ),
            colorStart = 0xFF1DB954L,
            colorEnd = 0xFF121212L
        )
        val decodedLegacy = store.decodePlaylistCode(legacyCode)
        assertNotNull(decodedLegacy)
        assertTrue(decodedLegacy!!.tracks.all { it.streamUrl.isEmpty() && !it.isPreviewOnly })

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
    fun musicCatalogApi_returnsOnlyFullLengthAudiusTracksAndZero30sPreviews() = runTest {
        val mockClient = createCatalogMockClient()
        val api = MusicCatalogApi(mockClient)

        fun assertAllFullLength(tracks: List<Track>, label: String) {
            assertTrue("$label should not be empty", tracks.isNotEmpty())
            tracks.forEach { t ->
                assertEquals("$label -> ${t.title} must be AUDIUS", TrackSource.AUDIUS, t.source)
                assertFalse("$label -> ${t.title} must not be preview-only", t.isPreviewOnly)
                assertTrue("$label -> ${t.title} durationSec=${t.durationSec} must be >= 90", t.durationSec >= 90)
                assertTrue(
                    "$label -> ${t.title} must have Audius streamUrl: ${t.streamUrl}",
                    t.streamUrl.startsWith("https://api.audius.co/v1/tracks/") &&
                        t.streamUrl.endsWith("/stream?app_name=Sonora")
                )
                assertFalse(
                    "$label -> ${t.title} must never use dzcdn.net audio",
                    t.streamUrl.contains("dzcdn.net", ignoreCase = true)
                )
                assertFalse(
                    "$label -> ${t.title} must never use itunes.apple.com audio",
                    t.streamUrl.contains("itunes.apple.com", ignoreCase = true)
                )
            }
        }

        // 1. Curated PH playlist returns full-length seed + resolved live tracks, dropping unresolved songs
        val playlistTracks = api.getPlaylistTracks("dz_pl_15507256221")
        assertAllFullLength(playlistTracks, "getPlaylistTracks")
        assertTrue(playlistTracks.any { it.title == "Multo" && it.durationSec == 289 })
        assertTrue(playlistTracks.any { it.title == "Pantropiko" && it.durationSec == 264 })
        assertTrue(playlistTracks.none { it.title == "Unplayable Song" })
        val multoFromChart = playlistTracks.first { it.title == "Multo" }
        assertEquals("https://cdn.dzcdn.net/cover/101/500x500.jpg", multoFromChart.artworkUrl)

        // 2. getPhTopTracks, getOpmTrendingTracks, getGlobalTopTracks all return 100% full-length tracks
        val phTop = api.getPhTopTracks(limit = 40)
        assertAllFullLength(phTop, "getPhTopTracks")
        assertTrue(phTop.size >= 17)
        assertTrue(phTop.any { it.title == "Raining in Manila" && it.durationSec == 336 })
        assertTrue(phTop.any { it.title == "Malaya" && it.durationSec == 270 })
        assertTrue(phTop.any { it.title == "Na Para Bang" && it.artist == "Mariah Deborah" && it.durationSec == 224 && it.youtubeVideoId == "Y5z_4utl678" && it.hasMusicVideo })
        assertTrue(phTop.any { it.title == "Tibok" && it.artist == "Earl Agustin" && it.durationSec == 327 && it.youtubeVideoId == "oiAtAzFLDfE" && it.hasMusicVideo })
        assertTrue(phTop.first { it.title == "Multo" }.hasMusicVideo)
        // Extra resolved live non-seed hit
        assertTrue(phTop.any { it.title == "Bulong" && it.durationSec == 275 })

        val opmTrending = api.getOpmTrendingTracks(limit = 35)
        assertAllFullLength(opmTrending, "getOpmTrendingTracks")
        assertTrue(opmTrending.any { it.title == "Palagi" && it.durationSec == 240 })

        val globalTop = api.getGlobalTopTracks(limit = 40)
        assertAllFullLength(globalTop, "getGlobalTopTracks")
        assertTrue(globalTop.size >= 30)
        assertTrue(globalTop.any { it.title == "Die With A Smile" && it.durationSec == 249 && it.youtubeVideoId == "kPa7bsKwL-c" })
        assertTrue(globalTop.any { it.title == "Starboy" && it.durationSec == 273 && it.youtubeVideoId == "34Na4j8AVgA" })
        assertTrue(globalTop.any { it.title == "Golden" && it.artist == "HUNTR/X" && it.durationSec == 192 })
        assertTrue(globalTop.any { it.title == "Birds of a Feather" && it.durationSec == 230 })
        assertTrue(globalTop.any { it.title == "Not Like Us" && it.durationSec == 273 })
        assertTrue(globalTop.any { it.title == "Call Out My Name" && it.durationSec == 227 })
        assertTrue(globalTop.any { it.title == "Drivers License" && it.durationSec == 247 })
        assertTrue(globalTop.any { it.title == "LUNCH" && it.durationSec == 179 })
        assertTrue(globalTop.any { it.title == "NASA" && it.durationSec == 245 })
        assertTrue(globalTop.any { it.title == "On My Way" && it.durationSec == 216 })

        // 3. Genre shelves & featured playlists return only full-length tracks
        listOf("OPM", "P-Pop", "Pinoy Hip-Hop", "Pop", "R&B", "Hip-Hop", "K-Pop", "Rock", "Chill").forEach { genre ->
            val genreTracks = api.getGenreTracks(genre, limit = 20)
            assertAllFullLength(genreTracks, "getGenreTracks($genre)")
        }
        val featuredPlaylists = api.getFeaturedPlaylists()
        assertEquals(5, featuredPlaylists.size)

        // 4. getArtistTopTracks returns verified artist seeds + artist-matched Audius full tracks
        val cupOfJoeTracks = api.getArtistTopTracks("60687062", "Cup of Joe", limit = 20)
        assertAllFullLength(cupOfJoeTracks, "getArtistTopTracks(Cup of Joe)")
        assertTrue(cupOfJoeTracks.any { it.title == "Multo" })
        assertTrue(cupOfJoeTracks.any { it.title == "Tingin" })
        assertTrue(cupOfJoeTracks.any { it.title == "Misteryoso" })

        val weekndTracks = api.getArtistTopTracks("4050205", "The Weeknd", limit = 25)
        assertAllFullLength(weekndTracks, "getArtistTopTracks(The Weeknd)")
        assertTrue(weekndTracks.size >= 6)
        listOf(
            "Starboy",
            "Call Out My Name",
            "The Hills",
            "Often",
            "I Feel It Coming",
            "Wicked Games"
        ).forEach { expectedTitle ->
            assertTrue("Expected Weeknd track $expectedTitle", weekndTracks.any { it.title == expectedTitle })
        }
        assertTrue(
            weekndTracks.all {
                it.source == TrackSource.AUDIUS &&
                    it.durationSec >= 180 &&
                    !it.streamUrl.contains("dzcdn.net", ignoreCase = true) &&
                    !it.streamUrl.contains("itunes.apple.com", ignoreCase = true)
            }
        )

        // 5. searchTracks queries Audius open search (filtering <90s, non-streamable, and noise) + matching seeds, exact first
        val searchMulto = api.searchTracks("Multo", limit = 15)
        assertAllFullLength(searchMulto, "searchTracks(Multo)")
        assertEquals("Multo", searchMulto.first().title)
        assertTrue(
            searchMulto.none {
                it.title.contains("Snippet", ignoreCase = true) ||
                    it.title.contains("Locked", ignoreCase = true) ||
                    it.title.contains("Type Beat", ignoreCase = true)
            }
        )
        val searchMultoTypeBeat = api.searchTracks("Multo Type Beat", limit = 15)
        assertTrue(searchMultoTypeBeat.any { it.title == "Multo (Type Beat)" })

        // Spelling alias "maria deborah" resolves "Mariah Deborah"
        val searchMaria = api.searchTracks("maria deborah", limit = 15)
        assertTrue(searchMaria.any { it.title == "Na Para Bang" && it.artist == "Mariah Deborah" && it.youtubeVideoId == "Y5z_4utl678" })

        // Excluded Indian search returns empty
        assertTrue(api.searchTracks("Arijit Singh", limit = 15).isEmpty())

        // 6. searchArtists sorts by highest nb_fan descending
        val artists = api.searchArtists("bini", limit = 10)
        assertEquals(2, artists.size)
        assertEquals("BINI", artists[0].name)
        assertEquals(25000L, artists[0].followerCount)
        assertEquals("Other Artist", artists[1].name)

        // 7. Rate-limited /artist/{id} never throws or returns empty curated artist shelves
        val phArtists = api.getPhTrendingArtists()
        assertEquals(18, phArtists.size)
        assertTrue(phArtists.any { it.name == "Cup of Joe" && !it.avatarUrl.isNullOrBlank() })
        assertTrue(phArtists.any { it.name == "BINI" && !it.avatarUrl.isNullOrBlank() })

        val globalArtists = api.getGlobalTrendingArtists()
        assertEquals(13, globalArtists.size)
        assertTrue(globalArtists.any { it.name == "Taylor Swift" && !it.avatarUrl.isNullOrBlank() })
        assertTrue(globalArtists.any { it.name == "Coldplay" && !it.avatarUrl.isNullOrBlank() })
        assertTrue(globalArtists.any { it.name == "Ed Sheeran" && !it.avatarUrl.isNullOrBlank() })
    }

    @Test
    fun recommendationEngine_buildsMadeForYouSmartShuffleRadioAndMoodMix() = runTest {
        val api = MusicCatalogApi(createCatalogMockClient())
        val engine = RecommendationEngine(api)

        val madeForYou = engine.buildMadeForYou(
            history = listOf(ListeningEvent("aud_jz39y6M", "Multo", "Cup of Joe", "OPM", null, 180_000L)),
            likedSongs = emptyList(),
            followedArtistIds = setOf("60687062")
        )
        assertEquals(3, madeForYou.size)
        assertEquals("Discover Weekly", madeForYou[0].name)
        assertEquals("Release Radar", madeForYou[1].name)
        assertEquals("Daily Mix", madeForYou[2].name)
        assertTrue(madeForYou.all { it.tracks.isNotEmpty() })
        val allMadeForYouTracks = madeForYou.flatMap { it.tracks }
        assertTrue(allMadeForYouTracks.all {
            it.source == TrackSource.AUDIUS &&
                !it.isPreviewOnly &&
                it.durationSec >= 90 &&
                !it.streamUrl.contains("dzcdn.net") &&
                !it.streamUrl.contains("itunes.apple.com")
        })

        val seed = Track(
            id = "aud_jz39y6M",
            title = "Multo",
            artist = "Cup of Joe",
            artistId = "60687062",
            genre = "OPM",
            durationSec = 289,
            streamUrl = "https://api.audius.co/v1/tracks/jz39y6M/stream?app_name=Sonora",
            source = TrackSource.AUDIUS,
            isPreviewOnly = false
        )
        val picks = engine.getSmartShufflePicks(listOf(seed), excludeIds = setOf("aud_xyGXRK9"), count = 3)
        assertTrue(picks.isNotEmpty())
        assertTrue(picks.all {
            it.isSmartPick &&
                it.id != "aud_jz39y6M" &&
                it.id != "aud_xyGXRK9" &&
                it.source == TrackSource.AUDIUS &&
                !it.isPreviewOnly &&
                it.durationSec >= 90
        })

        val radio = engine.buildTrackRadio(seed)
        assertTrue(radio.size >= 2)
        assertEquals("aud_jz39y6M", radio.first().id)
        assertTrue(radio.all { it.source == TrackSource.AUDIUS && !it.isPreviewOnly && it.durationSec >= 90 })

        val moodMix = engine.generateAiPlaylist("late night opm")
        assertTrue(moodMix.tracks.isNotEmpty())
        assertTrue(moodMix.tracks.all { it.source == TrackSource.AUDIUS && !it.isPreviewOnly && it.durationSec >= 90 })
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
        assertTrue("Matched full-length remix of exact song/artist should pass: $remixScore", remixScore >= 45)

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

        val karaokeScore = resolver.scoreCandidate(
            targetTitle = "Multo",
            targetArtist = "Cup of Joe",
            targetDurationSec = 237,
            candidateTitle = "Multo - Cup of Joe (Karaoke Version)",
            candidateUploader = "Karaoke Hub",
            candidateDurationSec = 237,
            isStreamable = true
        )
        assertTrue("Unexpected karaoke must be rejected (< 0): $karaokeScore", karaokeScore < 0)

        listOf(
            "Multo - Cup of Joe (Type Beat)",
            "Multo - Cup of Joe (AI Cover)",
            "Multo - Cup of Joe (ScrewedNChopped)",
            "Multo - Cup of Joe (Parody)",
            "Multo - Cup of Joe (Minus 1)"
        ).forEach { noiseTitle ->
            val noiseScore = resolver.scoreCandidate(
                targetTitle = "Multo",
                targetArtist = "Cup of Joe",
                targetDurationSec = 237,
                candidateTitle = noiseTitle,
                candidateUploader = "Cup of Joe",
                candidateDurationSec = 237,
                isStreamable = true
            )
            assertTrue("Unexpected hard noise '$noiseTitle' must score < 0: $noiseScore", noiseScore < 0)
        }

        val pandaDescScore = resolver.scoreCandidate(
            targetTitle = "Blinding Lights",
            targetArtist = "The Weeknd",
            targetDurationSec = 200,
            candidateTitle = "The Weeknd - Blinding Lights",
            candidateUploader = "Panda Studio Music",
            candidateDurationSec = 202,
            isStreamable = true,
            candidateDescription = "Instrumental: Pandastudio"
        )
        assertEquals(-500, pandaDescScore)

        val origWeekndScore = resolver.scoreCandidate(
            targetTitle = "Starboy",
            targetArtist = "The Weeknd",
            targetDurationSec = 230,
            candidateTitle = "The Weeknd - Starboy",
            candidateUploader = "Official Upload",
            candidateDurationSec = 230,
            isStreamable = true
        )
        val coverDescScore = resolver.scoreCandidate(
            targetTitle = "Starboy",
            targetArtist = "The Weeknd",
            targetDurationSec = 230,
            candidateTitle = "The Weeknd - Starboy",
            candidateUploader = "Someone",
            candidateDurationSec = 230,
            isStreamable = true,
            candidateDescription = "Cover by Someone"
        )
        assertTrue("Cover-by description ($coverDescScore) must score lower than clean original ($origWeekndScore)", coverDescScore < origWeekndScore)

        val coverScore = resolver.scoreCandidate(
            targetTitle = "Pasilyo",
            targetArtist = "SunKissed Lola",
            targetDurationSec = 269,
            candidateTitle = "Pasilyo - SunKissed Lola (Jenzen Guino Cover)_256k",
            candidateUploader = "OPM Music Cover",
            candidateDurationSec = 261,
            isStreamable = true
        )
        assertTrue("Matched cover of exact song/artist passes cleanly: $coverScore", coverScore >= 45)

        // All 12 matched full-length remixes/edits/covers/flips pass cleanly even with large duration diffs
        val matchedVariantCases = listOf(
            Triple("Sining", "Dionela ft. Jay R", "SINING - DIONELA ft JAY R (NICKY MONTES REMIX)") to (192 to "Nicky Montes"),
            Triple("Raining in Manila", "Lola Amour", "Lola Amour - Raining in Manila chillhouse remix") to (336 to "DJ Chill"),
            Triple("Malaya", "Moira Dela Torre", "Malaya - Moira Dela Torre (Joseph Vincent -Tagalog Cover) (128 kbps)") to (270 to "Joseph Vincent"),
            Triple("Birds of a Feather", "Billie Eilish", "Billie Eilish - BIRDS OF A FEATHER (Erick T Edit)") to (322 to "Erick T"),
            Triple("Espresso", "Sabrina Carpenter", "Sabrina Carpenter - Espresso (Michael Porter Remix)") to (160 to "Michael Porter"),
            Triple("Blinding Lights", "The Weeknd", "The Weeknd - Blinding Lights (DJ Luciano Velocity Mix)") to (241 to "DJ Luciano"),
            Triple("Starboy", "The Weeknd", "The Weeknd - Starboy (Ekay Remix)") to (195 to "Ekay"),
            Triple("Snooze", "SZA", "sza - snooze (perry. flip)") to (152 to "perry"),
            Triple("Kill Bill", "SZA", "sza - kill bill (flip)") to (149 to "flipper"),
            Triple("Drivers License", "Olivia Rodrigo", "Olivia Rodrigo - drivers license (MOZART Remix)") to (230 to "Mozart"),
            Triple("We Can't Be Friends", "Ariana Grande", "Ariana Grande - we can't be friends (Buuurgos Remix)") to (297 to "Buuurgos"),
            Triple("Not Like Us", "Kendrick Lamar", "Kendrick Lamar - Not Like Us (Fattybass Remix)") to (219 to "Fattybass")
        )
        matchedVariantCases.forEach { (titles, meta) ->
            val (targetTitle, targetArtist, candidateTitle) = titles
            val (dur, uploader) = meta
            val variantScore = resolver.scoreCandidate(
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                targetDurationSec = 0,
                candidateTitle = candidateTitle,
                candidateUploader = uploader,
                candidateDurationSec = dur,
                isStreamable = true
            )
            assertTrue("$targetTitle matched variant should score >= 45 (got $variantScore)", variantScore >= 45)
        }

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
    fun fullAudioResolver_resolveFullTrackAndOrNull_neverFallsBackToPreviewAndNeverCachesFailures() = runTest {
        val searchRequests = AtomicInteger(0)
        val probeRequests = AtomicInteger(0)
        val ereSearchRequests = AtomicInteger(0)
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
                        ereSearchRequests.incrementAndGet()
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
                    url.startsWith("https://api.audius.co/v1/tracks/search") &&
                        url.contains("Blinding+Lights+The+Weeknd") -> {
                        val body = """
                            {
                              "data": [
                                {
                                  "id": "aud_bl_remix",
                                  "title": "The Weeknd - Blinding Lights (DJ Remix)",
                                  "duration": 202,
                                  "is_streamable": true,
                                  "user": { "name": "DJ Remix", "handle": "djremix" }
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
                        url.contains("Blinding+Lights+Weeknd") -> {
                        val body = """
                            {
                              "data": [
                                {
                                  "id": "aud_bl_clean",
                                  "title": "Blinding Lights - Weeknd",
                                  "duration": 202,
                                  "is_streamable": true,
                                  "user": { "name": "Weeknd", "handle": "weeknd" }
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
                    url == "https://api.audius.co/v1/tracks/aud_bl_clean/stream?app_name=Sonora" -> {
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
                        url.contains("Closer+The+Chainsmokers") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"data":[]}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url.startsWith("https://api.audius.co/v1/tracks/search") &&
                        url.contains("Closer+Chainsmokers") -> {
                        val body = """
                            {
                              "data": [
                                {
                                  "id": "aud_closer_clean",
                                  "title": "The Chainsmokers - Closer",
                                  "duration": 244,
                                  "is_streamable": true,
                                  "user": { "name": "Chainsmokers", "handle": "chainsmokers" }
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
                    url == "https://api.audius.co/v1/tracks/aud_closer_clean/stream?app_name=Sonora" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(206)
                            .message("Partial Content")
                            .header("Content-Type", "audio/mpeg")
                            .body(ByteArray(64).toResponseBody("audio/mpeg".toMediaType()))
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

        // 1. HTTP 206 audio/mpeg upgrades legacy track to full Audius stream and caches result
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
        assertEquals(TrackSource.AUDIUS, resolvedMulto.source)
        assertFalse(resolvedMulto.isPreviewOnly)
        assertEquals(0, fallbackCalls)
        assertEquals(1, searchRequests.get())
        assertEquals(1, probeRequests.get())

        val cachedMulto = resolver.resolveFullTrackOrNull(multo)
        assertEquals(resolvedMulto, cachedMulto)
        assertEquals(1, searchRequests.get())
        assertEquals(1, probeRequests.get())

        // 2. Rejected candidate ("Amanecer" for "ERE - juan karlos") returns null, does NOT cache failure,
        //    and NEVER falls back to 30s preview URLs
        val ere = Track(
            id = "dz_202",
            title = "ERE",
            artist = "juan karlos",
            durationSec = 285,
            streamUrl = "https://cdnt-preview.dzcdn.net/202.mp3",
            source = TrackSource.DEEZER
        )
        assertNull(resolver.resolveFullTrackOrNull(ere))
        val firstEreCalls = ereSearchRequests.get()
        val resolvedEre = resolver.resolveFullTrack(ere) {
            fallbackCalls++
            "https://cdnt-preview.dzcdn.net/fresh/ere.mp3"
        }
        assertTrue("Failed resolution must NOT be cached", ereSearchRequests.get() > firstEreCalls)
        assertEquals("", resolvedEre.streamUrl)
        assertFalse(resolvedEre.isPreviewOnly)
        assertEquals(0, fallbackCalls)

        // 3. HTTP 404 on stream probe returns null and never returns iTunes/Deezer 30s preview URL
        val dilaw = Track(
            id = "it_303",
            title = "Dilaw",
            artist = "Maki",
            durationSec = 192,
            streamUrl = "https://audio-ssl.itunes.apple.com/dilaw.m4a",
            source = TrackSource.ITUNES
        )
        assertNull(resolver.resolveFullTrackOrNull(dilaw))
        val resolvedDilaw = resolver.resolveFullTrack(dilaw) {
            fallbackCalls++
            "https://cdnt-preview.dzcdn.net/fresh/dilaw.mp3"
        }
        assertEquals("", resolvedDilaw.streamUrl)
        assertFalse(resolvedDilaw.isPreviewOnly)
        assertEquals(0, fallbackCalls)

        // 4. Secondary query (clean title + first artist) succeeds when multi-artist query returns empty
        val sining = Track(
            id = "dz_404",
            title = "Sining (Official Video)",
            artist = "Dionela, Jay R",
            durationSec = 210,
            streamUrl = "https://cdnt-preview.dzcdn.net/404.mp3",
            source = TrackSource.DEEZER
        )
        val resolvedSining = resolver.resolveFullTrackOrNull(sining)
        assertNotNull(resolvedSining)
        assertEquals("https://api.audius.co/v1/tracks/aud_sining/stream?app_name=Sonora", resolvedSining!!.streamUrl)
        assertEquals(TrackSource.AUDIUS, resolvedSining.source)
        assertFalse(resolvedSining.isPreviewOnly)

        // 4b. Leading "The " stripping ("The Weeknd" -> "Weeknd") resolves clean Global track when primary query yields only remix or empty results
        val blindingLights = Track(
            id = "dz_505",
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationSec = 202,
            streamUrl = "https://cdnt-preview.dzcdn.net/505.mp3",
            source = TrackSource.DEEZER
        )
        val resolvedBlinding = resolver.resolveFullTrackOrNull(blindingLights)
        assertNotNull(resolvedBlinding)
        assertEquals("https://api.audius.co/v1/tracks/aud_bl_clean/stream?app_name=Sonora", resolvedBlinding!!.streamUrl)
        assertEquals(TrackSource.AUDIUS, resolvedBlinding.source)
        assertFalse(resolvedBlinding.isPreviewOnly)

        val closer = Track(
            id = "dz_506",
            title = "Closer",
            artist = "The Chainsmokers",
            durationSec = 244,
            streamUrl = "https://cdnt-preview.dzcdn.net/506.mp3",
            source = TrackSource.DEEZER
        )
        val resolvedCloser = resolver.resolveFullTrackOrNull(closer)
        assertNotNull(resolvedCloser)
        assertEquals("https://api.audius.co/v1/tracks/aud_closer_clean/stream?app_name=Sonora", resolvedCloser!!.streamUrl)
        assertEquals(TrackSource.AUDIUS, resolvedCloser.source)
        assertFalse(resolvedCloser.isPreviewOnly)

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
    fun musicCatalogApi_resolvePlayableTrack_upgradesAndNeverReturnsPreviewUrl() = runTest {
        val audiusCalls = AtomicInteger(0)
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.startsWith("https://api.audius.co/v1/tracks/search") &&
                        url.contains("Multo") -> {
                        audiusCalls.incrementAndGet()
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

        // Already-AUDIUS track returns immediately with zero search calls
        val nativeAudius = Track(
            id = "aud_jz39y6M",
            title = "Multo",
            artist = "Cup of Joe",
            durationSec = 289,
            streamUrl = "https://api.audius.co/v1/tracks/jz39y6M/stream?app_name=Sonora",
            source = TrackSource.AUDIUS,
            isPreviewOnly = false
        )
        assertEquals(nativeAudius, api.resolvePlayableTrack(nativeAudius))
        assertEquals(0, audiusCalls.get())

        // Legacy Deezer track upgrades to AUDIUS full length
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
        assertEquals(TrackSource.AUDIUS, resolvedMulto.source)
        assertFalse(resolvedMulto.isPreviewOnly)

        // Unresolved track strips legacy preview URL instead of returning 30s preview
        val unresolvedTrack = api.resolvePlayableTrack(
            Track(
                id = "dz_999",
                title = "Unknown Song",
                artist = "Unknown Artist",
                durationSec = 210,
                streamUrl = "https://cdnt-preview.dzcdn.net/fresh/999.mp3",
                source = TrackSource.DEEZER,
                isPreviewOnly = true
            )
        )
        assertEquals("", unresolvedTrack.streamUrl)
        assertFalse(unresolvedTrack.isPreviewOnly)
    }

    private fun createCatalogMockClient(): OkHttpClient {
        val sampleTrack101 = """
            {
              "id": 101,
              "title": "Multo",
              "duration": 289,
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
              "duration": 264,
              "rank": 850000,
              "link": "https://www.deezer.com/track/102",
              "preview": "https://cdnt-preview.dzcdn.net/102.mp3",
              "artist": { "id": 320392011, "name": "BINI" },
              "album": {
                "id": 5002,
                "title": "Talaarawan",
                "cover_big": "https://cdn.dzcdn.net/cover/102/500x500.jpg",
                "release_date": "2023-11-17"
              }
            }
        """.trimIndent()

        val sampleTrack104 = """
            {
              "id": 104,
              "title": "Bulong",
              "duration": 275,
              "rank": 780000,
              "link": "https://www.deezer.com/track/104",
              "preview": "https://cdnt-preview.dzcdn.net/104.mp3",
              "artist": { "id": 9001, "name": "December Avenue" },
              "album": {
                "id": 5004,
                "title": "Langit Mong Bughaw",
                "cover_big": "https://cdn.dzcdn.net/cover/104/500x500.jpg",
                "release_date": "2019-12-20"
              }
            }
        """.trimIndent()

        val unplayableTrack = """
            {
              "id": 999,
              "title": "Unplayable Song",
              "duration": 200,
              "link": "https://www.deezer.com/track/999",
              "preview": "https://cdnt-preview.dzcdn.net/999.mp3",
              "artist": { "id": 1, "name": "Someone" },
              "album": { "id": 2, "title": "Album" }
            }
        """.trimIndent()

        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.startsWith("https://api.audius.co/v1/tracks/search") -> {
                        val body = when {
                            url.contains("Bulong") -> """
                                {
                                  "data": [
                                    {
                                      "id": "aud_bulong",
                                      "title": "Bulong - December Avenue",
                                      "duration": 275,
                                      "is_streamable": true,
                                      "artwork": { "480x480": "https://audius.example.com/bulong.jpg" },
                                      "user": { "name": "December Avenue", "handle": "decave" }
                                    }
                                  ]
                                }
                            """.trimIndent()
                            url.contains("Multo") -> """
                                {
                                  "data": [
                                    {
                                      "id": "aud_short",
                                      "title": "Multo Snippet",
                                      "duration": 30,
                                      "is_streamable": true,
                                      "user": { "name": "Cup of Joe", "handle": "cupofjoe" }
                                    },
                                    {
                                      "id": "aud_locked",
                                      "title": "Multo Locked",
                                      "duration": 289,
                                      "is_streamable": false,
                                      "user": { "name": "Cup of Joe", "handle": "cupofjoe" }
                                    },
                                    {
                                      "id": "aud_multo_live",
                                      "title": "Multo (Live)",
                                      "duration": 295,
                                      "is_streamable": true,
                                      "artwork": { "480x480": "https://audius.example.com/multo_live.jpg" },
                                      "user": { "name": "Cup of Joe", "handle": "cupofjoe" }
                                    },
                                    {
                                      "id": "aud_multo_type_beat",
                                      "title": "Multo (Type Beat)",
                                      "duration": 240,
                                      "is_streamable": true,
                                      "user": { "name": "BeatMaker", "handle": "beatmaker" }
                                    }
                                  ]
                                }
                            """.trimIndent()
                            else -> """{"data":[]}"""
                        }
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }

                    url.startsWith("https://api.audius.co/v1/tracks/aud_bulong/stream") ->
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(206)
                            .message("Partial Content")
                            .header("Content-Type", "audio/mpeg")
                            .body(ByteArray(64).toResponseBody("audio/mpeg".toMediaType()))
                            .build()

                    url.startsWith("https://api.audius.co/v1/tracks?") -> {
                        val body = """
                            {
                              "data": [
                                {
                                  "id": "jz39y6M",
                                  "artwork": { "480x480": "https://audius.example.com/multo.jpg" },
                                  "play_count": 50000
                                },
                                {
                                  "id": "ZbZ4zZa",
                                  "artwork": { "480x480": "https://audius.example.com/dwas.jpg" },
                                  "play_count": 80000
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

                    url.startsWith("https://api.deezer.com/playlist/15507256221/tracks") ->
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{ "data": [ $sampleTrack101, $unplayableTrack, $sampleTrack102, $sampleTrack104 ] }""".toResponseBody("application/json".toMediaType()))
                            .build()

                    url.startsWith("https://api.deezer.com/playlist/13461654563/tracks") ||
                        url.startsWith("https://api.deezer.com/playlist/3155776842/tracks") ||
                        url.startsWith("https://api.deezer.com/chart/0/tracks") ->
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{ "data": [ $sampleTrack101, $sampleTrack102, $sampleTrack104 ] }""".toResponseBody("application/json".toMediaType()))
                            .build()

                    url.startsWith("https://api.deezer.com/artist/") && url.contains("/top") ->
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{ "data": [ $sampleTrack101 ] }""".toResponseBody("application/json".toMediaType()))
                            .build()

                    url.startsWith("https://api.deezer.com/artist/") && url.contains("/related") ->
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{ "data": [ { "id": 320392011, "name": "BINI", "nb_fan": 25000, "picture_big": "https://cdn.dzcdn.net/bini.jpg" } ] }""".toResponseBody("application/json".toMediaType()))
                            .build()

                    url.startsWith("https://api.deezer.com/artist/") ->
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(429)
                            .message("Rate Limited")
                            .body("""{ "error": { "type": "Exception", "message": "Quota limit exceeded", "code": 4 } }""".toResponseBody("application/json".toMediaType()))
                            .build()

                    url.startsWith("https://api.deezer.com/search/artist") ->
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                """
                                {
                                  "data": [
                                    { "id": 111, "name": "Other Artist", "nb_fan": 500, "picture_big": "https://cdn.dzcdn.net/other.jpg" },
                                    { "id": 320392011, "name": "BINI", "nb_fan": 25000, "picture_big": "https://cdn.dzcdn.net/bini.jpg" }
                                  ]
                                }
                                """.trimIndent().toResponseBody("application/json".toMediaType())
                            )
                            .build()

                    url.startsWith("https://rss.marketingtools.apple.com/api/v2/ph/music/most-played") ->
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{ "feed": { "results": [ { "name": "Dilaw", "artistName": "Maki" } ] } }""".toResponseBody("application/json".toMediaType()))
                            .build()

                    else ->
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("{}".toResponseBody("application/json".toMediaType()))
                            .build()
                }
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

    @Test
    fun regionalContentFilter_blocksIndianBollywoodWhileAllowingOpmAndGlobalIndie() {
        // Indic Unicode script blocks
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "तुम ही हो", artist = "Unknown"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "தமிழ் பாடல்", artist = "Unknown"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "ਪੰਜਾਬੀ ਗੀਤ", artist = "Unknown"))

        // Excluded genres, labels, artists & Bollywood/Ed-Sheeran collabs
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "Random Song", genre = "Bollywood"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "Random Song", genre = "Hindi Pop"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "Track", genre = "Punjabi"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "Track", genre = "Tamil"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "Song", artist = "T-Series"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "Kesariya", artist = "Arijit Singh"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "Tauba Tauba", artist = "Karan Aujla"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "Sapphire (feat. Shah Rukh Khan)", artist = "Ed Sheeran"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "Sapphire", artist = "Ed Sheeran, Arijit Singh"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "Mirchi", artist = "DIVINE"))
        assertTrue(RegionalContentFilter.isExcludedIndianContent(title = "Mirchi", artist = " divine "))

        // Allowed OPM, P-Pop, Global & Indie content (including "divine" in song titles)
        assertFalse(RegionalContentFilter.isExcludedIndianContent(title = "Na Para Bang", artist = "Mariah Deborah", genre = "OPM"))
        assertFalse(RegionalContentFilter.isExcludedIndianContent(title = "Mahal Magmahal", artist = "Esremborak", genre = "OPM"))
        assertFalse(RegionalContentFilter.isExcludedIndianContent(title = "Pantropiko", artist = "BINI", genre = "P-Pop"))
        assertFalse(RegionalContentFilter.isExcludedIndianContent(title = "Golden", artist = "HUNTR/X", genre = "Indie Pop"))
        assertFalse(RegionalContentFilter.isExcludedIndianContent(title = "Indiana", artist = "Indie Band", genre = "Indie"))
        assertFalse(RegionalContentFilter.isExcludedIndianContent(title = "Divine Mercy", artist = "Hillsong", genre = "Pop"))
        assertFalse(RegionalContentFilter.isExcludedIndianContent(title = "A Divine Feeling", artist = "The Weeknd", genre = "R&B"))

        val mixedTracks = listOf(
            Track(id = "ok_1", title = "Na Para Bang", artist = "Mariah Deborah", genre = "OPM"),
            Track(id = "bad_1", title = "Tum Hi Ho", artist = "Arijit Singh", genre = "Bollywood"),
            Track(id = "bad_2", title = "Sapphire", artist = "Ed Sheeran", mood = "Shah Rukh Khan"),
            Track(id = "ok_2", title = "Mahal Magmahal", artist = "Esremborak", genre = "OPM")
        )
        val filteredTracks = RegionalContentFilter.filterTracks(mixedTracks)
        assertEquals(listOf("ok_1", "ok_2"), filteredTracks.map { it.id })

        val mixedArtists = listOf(
            Artist(id = "a1", name = "Esremborak", bio = "OPM"),
            Artist(id = "a2", name = "Arijit Singh", bio = "Bollywood"),
            Artist(id = "a3", name = "Divine", bio = "Indian Rap"),
            Artist(id = "a4", name = "BINI", bio = "P-Pop")
        )
        val filteredArtists = RegionalContentFilter.filterArtists(mixedArtists)
        assertEquals(listOf("Esremborak", "BINI"), filteredArtists.map { it.name })
    }

    @Test
    fun youTubeCatalogApi_extractsUrlsAndParsesAtomRssAndCuratedMvs() = runTest {
        val api = YouTubeCatalogApi(createCatalogMockClient())

        // URL / ID / Handle extraction
        assertEquals("S7BKWs2Tirg", api.extractVideoId("https://www.youtube.com/watch?v=S7BKWs2Tirg"))
        assertEquals("Y5z_4utl678", api.extractVideoId("https://youtu.be/Y5z_4utl678?si=abc123"))
        assertEquals("Zx31bB2vMns", api.extractVideoId("https://www.youtube.com/shorts/Zx31bB2vMns"))
        assertEquals("4NRXx6U8ABQ", api.extractVideoId("https://www.youtube.com/live/4NRXx6U8ABQ"))
        assertEquals("ekr2nIex040", api.extractVideoId("https://www.youtube.com/embed/ekr2nIex040"))
        assertEquals("Rht8rS4cR1s", api.extractVideoId("https://music.youtube.com/watch?v=Rht8rS4cR1s&list=RD123"))
        assertEquals("S7BKWs2Tirg", api.extractVideoId("S7BKWs2Tirg"))
        assertEquals("S7BKWs2Tirg", api.extractVideoId("Listen to Esremborak! https://youtu.be/S7BKWs2Tirg?si=share"))
        assertNull(api.extractVideoId("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"))

        assertEquals(
            "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
            api.extractPlaylistId("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
        )
        assertEquals("UCdfq8-581QVUtVsa8ChVnDQ", api.extractChannelId("https://www.youtube.com/channel/UCdfq8-581QVUtVsa8ChVnDQ"))
        assertEquals("UCdfq8-581QVUtVsa8ChVnDQ", api.extractChannelId("@wish1075"))
        assertEquals("UCtOcDBKgyr-f50SgbMErFkQ", api.extractChannelId("https://www.youtube.com/@bini_ph/videos"))
        assertEquals("UC0WP5P-ufpRfjbNrmOWwLBQ", api.extractChannelId("@theweeknd"))

        // Curated MVs & Packs
        val curated = api.getCuratedMusicVideos()
        assertEquals(39, curated.size)
        assertTrue(curated.any { it.artist == "Esremborak" && it.title == "Mahal Magmahal" && it.youtubeVideoId == "S7BKWs2Tirg" })
        assertTrue(curated.any { it.artist == "Esremborak" && it.title == "Tenchu" && it.youtubeVideoId == "IKB_JIXfuDw" })
        assertTrue(curated.any { it.artist == "Mariah Deborah" && it.title == "Na Para Bang" && it.youtubeVideoId == "Y5z_4utl678" })
        assertTrue(curated.any { it.artist == "The Weeknd" && it.title == "Blinding Lights" && it.youtubeVideoId == "4NRXx6U8ABQ" })
        assertTrue(curated.all {
            it.source == TrackSource.YOUTUBE &&
                it.hasMusicVideo &&
                it.isVideoOnly &&
                !it.isPreviewOnly &&
                !RegionalContentFilter.isExcludedTrack(it)
        })

        val packs = api.getCuratedPacks()
        assertEquals(listOf("ph_viral", "wish_1075", "global_mvs"), packs.map { it.id })
        assertTrue(packs.all { it.tracks.isNotEmpty() })
        assertTrue(api.getPackTracks("ph_viral").any { it.artist == "Esremborak" })

        // Spelling alias search
        val mariaVideos = api.searchVideos("maria deborah")
        assertTrue(mariaVideos.any { it.artist == "Mariah Deborah" && it.title == "Na Para Bang" })

        // JVM-safe Atom RSS XML parsing + title cleaning + Indian filtering
        val sampleAtomXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
              <title>Wish 107.5</title>
              <entry>
                <id>yt:video:abcDEF12345</id>
                <yt:videoId>abcDEF12345</yt:videoId>
                <title>Sample Band performs "Harana" LIVE on Wish 107.5 Bus</title>
                <author><name>Wish 107.5</name></author>
                <published>2026-01-10T00:00:00+00:00</published>
                <media:group>
                  <media:thumbnail url="https://i.ytimg.com/vi/abcDEF12345/hqdefault.jpg"/>
                  <yt:duration seconds="255"/>
                </media:group>
              </entry>
              <entry>
                <id>yt:video:xyzXYZ98765</id>
                <yt:videoId>xyzXYZ98765</yt:videoId>
                <title>"Starlight" - Nova | Wish 107.5 Bus</title>
                <author><name>Wish 107.5</name></author>
                <published>2026-01-11T00:00:00+00:00</published>
              </entry>
              <entry>
                <id>yt:video:indIND11111</id>
                <yt:videoId>indIND11111</yt:videoId>
                <title>Arijit Singh - Kesariya (Official Video)</title>
                <author><name>T-Series</name></author>
              </entry>
            </feed>
        """.trimIndent()

        val parsedFeed = api.parseAtomFeedXml(sampleAtomXml, regionHint = "PH", fallbackLabel = "Wish 107.5")
        assertEquals(1, parsedFeed.excludedCount)
        assertEquals(2, parsedFeed.tracks.size)
        assertEquals("Harana", parsedFeed.tracks[0].title)
        assertEquals("Sample Band", parsedFeed.tracks[0].artist)
        assertEquals(255, parsedFeed.tracks[0].durationSec)
        assertEquals(255_000L, parsedFeed.tracks[0].durationMs)
        assertEquals(TrackSource.YOUTUBE, parsedFeed.tracks[0].source)
        assertTrue(parsedFeed.tracks[0].hasMusicVideo)
        assertTrue(parsedFeed.tracks[0].isVideoOnly)
        assertFalse(parsedFeed.tracks[0].isPreviewOnly)
        assertEquals("https://i.ytimg.com/vi/abcDEF12345/hqdefault.jpg", parsedFeed.tracks[0].artworkUrl)

        assertEquals("Starlight", parsedFeed.tracks[1].title)
        assertEquals("Nova", parsedFeed.tracks[1].artist)
        assertEquals(210, parsedFeed.tracks[1].durationSec)
        assertEquals(210_000L, parsedFeed.tracks[1].durationMs)

        // Mocked oEmbed + Playlist RSS import
        val ytMockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val (code, mime, body) = when {
                    url.startsWith("https://www.youtube.com/oembed") && url.contains("newVID12345") ->
                        Triple(
                            200,
                            "application/json",
                            """{"title":"Cup of Joe - Tingin (Official Music Video)","author_name":"Cup of Joe","thumbnail_url":"https://i.ytimg.com/vi/newVID12345/hqdefault.jpg"}"""
                        )
                    url.startsWith("https://www.youtube.com/oembed") && url.contains("bollyVID999") ->
                        Triple(
                            200,
                            "application/json",
                            """{"title":"Tum Hi Ho - Arijit Singh","author_name":"T-Series","thumbnail_url":"https://i.ytimg.com/vi/bollyVID999/hqdefault.jpg"}"""
                        )
                    url.startsWith("https://www.youtube.com/feeds/videos.xml?playlist_id=PLtest123456") ->
                        Triple(200, "application/atom+xml", sampleAtomXml)
                    else -> Triple(404, "application/json", "{}")
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Not Found")
                    .body(body.toResponseBody(mime.toMediaType()))
                    .build()
            })
            .build()

        val catalogApi = MusicCatalogApi(ytMockClient)
        val okImport = catalogApi.importFromYouTube("https://www.youtube.com/watch?v=newVID12345")
        assertEquals(1, okImport.tracks.size)
        assertEquals("Tingin", okImport.tracks.first().title)
        assertEquals("Cup of Joe", okImport.tracks.first().artist)
        assertEquals(0, okImport.excludedCount)

        val blockedImport = catalogApi.importFromYouTube("https://www.youtube.com/watch?v=bollyVID999")
        assertTrue(blockedImport.tracks.isEmpty())
        assertEquals(1, blockedImport.excludedCount)

        val playlistImport = catalogApi.importFromYouTube("https://www.youtube.com/playlist?list=PLtest123456")
        assertEquals(2, playlistImport.tracks.size)
        assertEquals(1, playlistImport.excludedCount)

        val combinedVideos = catalogApi.getCombinedMusicVideos()
        assertTrue(combinedVideos.any { it.artist == "Esremborak" })
        assertTrue(combinedVideos.any { it.artist == "Mariah Deborah" })
        assertTrue(combinedVideos.any { it.artist == "The Weeknd" })
        assertTrue(combinedVideos.none { RegionalContentFilter.isExcludedTrack(it) })
        assertTrue(combinedVideos.none { it.isPreviewOnly })
    }

    @Test
    fun fullAudioResolver_rejectsIndianBollywoodCandidatesAndTracks() = runTest {
        val resolver = FullAudioResolver(createCatalogMockClient())
        val bollywoodScore = resolver.scoreCandidate(
            targetTitle = "Kesariya",
            targetArtist = "Arijit Singh",
            targetDurationSec = 268,
            candidateTitle = "Kesariya - Arijit Singh",
            candidateUploader = "T-Series",
            candidateDurationSec = 268,
            isStreamable = true
        )
        assertTrue("Indian/Bollywood candidate must be rejected (< 0): $bollywoodScore", bollywoodScore < 0)

        val excludedTrack = Track(
            id = "dz_indian_1",
            title = "Tum Hi Ho",
            artist = "Arijit Singh",
            genre = "Bollywood",
            durationSec = 260
        )
        assertNull(resolver.resolveFullTrackOrNull(excludedTrack))
    }

    @Test
    fun directYouTubeSearch_findsHongKongByWeekendWithoutManualImport() = runTest {
        val api = MusicCatalogApi(createCatalogMockClient())
        val byBoth = api.searchVideos("Hong Kong by Weekend")
        val byTitle = api.searchVideos("hong kong")
        val byArtist = api.searchVideos("weekend")

        assertTrue("Search 'Hong Kong by Weekend' should include hX9S94XsySk", byBoth.any { it.youtubeVideoId == "hX9S94XsySk" })
        assertTrue("Search 'hong kong' should include The Weeknd MV", byTitle.any { it.youtubeVideoId == "hX9S94XsySk" && it.artist == "The Weeknd" })
        assertTrue("Search 'weekend' should include The Weeknd catalog MVs", byArtist.any { it.artist == "The Weeknd" && it.youtubeVideoId == "hX9S94XsySk" })
        assertTrue(byBoth.none { RegionalContentFilter.isExcludedTrack(it) })
        assertTrue(byBoth.none { it.isPreviewOnly })
    }

    @Test
    fun youTubeCatalogApi_parseYouTubeSearchJson_extractsLiveVideosSkipsShortsAndFiltersIndian() {
        val ytApi = YouTubeCatalogApi(createCatalogMockClient())
        val sampleJson = """
            {
              "contents": {
                "twoColumnSearchResultsRenderer": {
                  "primaryContents": {
                    "sectionListRenderer": {
                      "contents": [
                        {
                          "itemSectionRenderer": {
                            "contents": [
                              {
                                "videoRenderer": {
                                  "videoId": "hC4IW0MI8Hs",
                                  "title": { "runs": [ { "text": "December Avenue - Bulong (OFFICIAL MUSIC VIDEO)" } ] },
                                  "ownerText": { "runs": [ { "text": "TOWERofDOOM" } ] },
                                  "lengthText": { "simpleText": "4:31" },
                                  "viewCountText": { "simpleText": "12,345,678 views" }
                                }
                              },
                              {
                                "videoRenderer": {
                                  "videoId": "shrt1234567",
                                  "title": { "runs": [ { "text": "Bulong Short Preview" } ] },
                                  "ownerText": { "runs": [ { "text": "ShortsClip" } ] },
                                  "lengthText": { "simpleText": "0:28" },
                                  "thumbnailOverlays": [
                                    { "thumbnailOverlayTimeStatusRenderer": { "style": "SHORTS", "text": { "simpleText": "SHORTS" } } }
                                  ]
                                }
                              },
                              {
                                "videoRenderer": {
                                  "videoId": "ind12345678",
                                  "title": { "runs": [ { "text": "Arijit Singh - Tum Hi Ho" } ] },
                                  "ownerText": { "runs": [ { "text": "T-Series" } ] },
                                  "lengthText": { "simpleText": "4:22" },
                                  "viewCountText": { "simpleText": "500,000,000 views" }
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = ytApi.parseYouTubeSearchJson(sampleJson)
        assertEquals(1, parsed.size)
        val track = parsed.first()
        assertEquals("hC4IW0MI8Hs", track.youtubeVideoId)
        assertEquals("Bulong", track.title)
        assertEquals("December Avenue", track.artist)
        assertEquals(271, track.durationSec)
        assertEquals(12345678L, track.playCount)
        assertEquals(TrackSource.YOUTUBE, track.source)
        assertFalse(track.isPreviewOnly)

        val wrappedHtml = "<html><head><script>var ytInitialData = $sampleJson; var other = {};</script></head></html>"
        val extractedJson = ytApi.extractYtInitialDataJson(wrappedHtml)
        assertNotNull(extractedJson)
        val roundTrip = ytApi.parseYouTubeSearchJson(extractedJson!!)
        assertEquals(1, roundTrip.size)
        assertEquals("hC4IW0MI8Hs", roundTrip.first().youtubeVideoId)
    }
}
