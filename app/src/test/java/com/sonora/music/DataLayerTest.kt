package com.sonora.music

import com.sonora.music.data.local.SonoraStore
import com.sonora.music.data.network.AudiobookApi
import com.sonora.music.data.network.AudiusApi
import com.sonora.music.data.network.HttpClientProvider
import com.sonora.music.data.network.LyricsApi
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
            id = "tr_1",
            title = "Emerald Horizon",
            artist = "Nova Pulse",
            artistId = "art_1",
            album = "Afterglow",
            genre = "Electronic",
            mood = "Energetic",
            durationSec = 215,
            artworkUrl = "https://example.com/art.jpg",
            artworkMirrors = listOf("https://mirror1.example.com"),
            streamUrl = "https://api.audius.co/v1/tracks/tr_1/stream?app_name=Sonora",
            source = TrackSource.AUDIUS,
            playCount = 1234L
        )
        val playlist = Playlist(
            id = "pl_custom_1",
            name = "Late Night Synths",
            description = "Cruising past midnight",
            trackIds = listOf(sampleTrack.id),
            tracks = listOf(sampleTrack),
            coverColorStart = 0xFF00E5FFL,
            coverColorEnd = 0xFF0F2744L,
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
        assertEquals(listOf(sampleTrack), store.loadLikedSongs())

        store.saveFollowedArtistIds(setOf("art_1", "art_2"))
        assertEquals(setOf("art_1", "art_2"), store.loadFollowedArtistIds())

        store.saveDownloadedTracks(listOf(sampleTrack.copy(localUri = "file:///tmp/tr_1.mp3")))
        assertEquals("file:///tmp/tr_1.mp3", store.loadDownloadedTracks().first().localUri)

        val settings = AudioSettings(
            eqEnabled = false,
            eqPreset = EqPreset.ELECTRONIC,
            bandLevelsMb = listOf(500, 150, -50, 350, 500),
            bassBoostStrength = 600,
            virtualizerStrength = 400,
            crossfadeSeconds = 6,
            playbackSpeed = 1.25f,
            privateSession = true,
            streamingQualityLabel = "High Stream"
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
                    genre = "Electronic",
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
                id = "a1",
                title = "Solaris",
                artist = "Kairo",
                durationSec = 190,
                streamUrl = "https://api.audius.co/v1/tracks/a1/stream?app_name=Sonora",
                source = TrackSource.AUDIUS
            ),
            Track(
                id = "a2",
                title = "Velocity",
                artist = "Mina",
                durationSec = 240,
                streamUrl = "https://api.audius.co/v1/tracks/a2/stream?app_name=Sonora",
                source = TrackSource.AUDIUS
            )
        )

        val code = store.encodePlaylistCode(
            name = "Cyberpunk Run",
            description = "Neon cardio fuel",
            tracks = tracks,
            colorStart = 0xFF1ED760L,
            colorEnd = 0xFF0A2E2AL
        )
        assertTrue(code.startsWith("SONORA-PL-V1:"))

        val decodedDirect = store.decodePlaylistCode(code)
        assertNotNull(decodedDirect)
        assertEquals("Cyberpunk Run", decodedDirect!!.name)
        assertEquals("Neon cardio fuel", decodedDirect.description)
        assertEquals(0xFF1ED760L, decodedDirect.coverColorStart)
        assertEquals(0xFF0A2E2AL, decodedDirect.coverColorEnd)
        assertEquals(tracks, decodedDirect.tracks)
        assertEquals(listOf("a1", "a2"), decodedDirect.trackIds)

        val encodedQuery = URLEncoder.encode(code, StandardCharsets.UTF_8.name())
        val decodedDeepLink = store.decodePlaylistCode("sonora://playlist?code=$encodedQuery")
        assertNotNull(decodedDeepLink)
        assertEquals("Cyberpunk Run", decodedDeepLink!!.name)
        assertEquals(tracks, decodedDeepLink.tracks)

        val rawJson = """{"name":"Raw Mix","description":"Direct JSON","tracks":[{"id":"x1","title":"Echo","artist":"Zen"}]}"""
        val decodedRaw = store.decodePlaylistCode(rawJson)
        assertNotNull(decodedRaw)
        assertEquals("Raw Mix", decodedRaw!!.name)
        assertEquals(1, decodedRaw.tracks.size)
        assertEquals("Echo", decodedRaw.tracks.first().title)

        assertNull(store.decodePlaylistCode("not-a-valid-code"))
        assertNull(store.decodePlaylistCode(""))
    }

    @Test
    fun recommendationEngine_parsePromptKeywords_extractsMoodGenresAndTerms() {
        val engine = RecommendationEngine(AudiusApi())

        val (studyGenres, studyTerms) = engine.parsePromptKeywords("late night chill lofi study beats")
        assertTrue(studyGenres.contains("Lo-Fi"))
        assertTrue(studyGenres.contains("Ambient"))
        assertTrue(studyTerms.contains("study"))
        assertTrue(studyTerms.contains("lofi"))

        val (gymGenres, gymTerms) = engine.parsePromptKeywords("intense gym workout pump")
        assertTrue(gymGenres.contains("Electronic"))
        assertTrue(gymGenres.contains("Hip-Hop/Rap"))
        assertTrue(gymTerms.contains("workout"))

        val (defaultGenres, defaultTerms) = engine.parsePromptKeywords("")
        assertFalse(defaultGenres.isEmpty())
        assertFalse(defaultTerms.isEmpty())
    }

    @Test
    fun recommendationEngine_computeWrapped_aggregatesTopItemsAndPersona() {
        val engine = RecommendationEngine(AudiusApi())

        val emptyStats = engine.computeWrapped(emptyList(), emptyList())
        assertEquals(0, emptyStats.totalMinutes)
        assertEquals(0, emptyStats.totalPlays)
        assertEquals("Sonic Explorer", emptyStats.personaTitle)

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
    }

    @Test
    fun networkApis_parseMockPayloadsAndFallbackSafely() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val (code, body) = when {
                    url.startsWith("https://api.audius.co/v1/tracks/trending") -> 503 to """{"error":"down"}"""
                    url.startsWith("https://discoveryprovider.audius.co/v1/tracks/trending") -> 200 to """
                        {
                          "data": [
                            {
                              "id": "aud_42",
                              "title": "Fallback Beat",
                              "duration": 205,
                              "genre": "Electronic",
                              "mood": "Euphoric",
                              "play_count": 9001,
                              "artwork": {
                                "150x150": "https://img/150.jpg",
                                "1000x1000": "https://img/1000.jpg",
                                "mirrors": ["https://mirror.audius.co"]
                              },
                              "user": { "id": "u_7", "name": "SynthLord", "handle": "synthlord" }
                            }
                          ]
                        }
                    """.trimIndent()
                    url.startsWith("https://lrclib.net/api/get") -> 404 to """{"message":"Not found"}"""
                    url.startsWith("https://lrclib.net/api/search") -> 200 to """
                        [
                          {
                            "id": 1,
                            "duration": 205,
                            "instrumental": false,
                            "plainLyrics": "Pure light\nShining bright",
                            "syncedLyrics": "[00:05.00]Pure light\n[00:10.00]Shining bright"
                          }
                        ]
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
                    url.startsWith("https://de1.api.radio-browser.info/json/stations/topclick") -> 200 to """
                        [
                          {
                            "stationuuid": "rad-1",
                            "name": "SomaFM Groove Salad",
                            "url_resolved": "https://ice1.somafm.com/groovesalad-128-mp3",
                            "favicon": "https://somafm.com/img/groovesalad.png",
                            "tags": "ambient,chillout",
                            "country": "United States",
                            "bitrate": 128,
                            "clickcount": 5000
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

        // AudiusApi fallback host + 1000x1000 artwork fallback + mirrors + stream URL
        val audius = AudiusApi(mockClient)
        val trending = audius.getTrendingTracks()
        assertEquals(1, trending.size)
        val track = trending.first()
        assertEquals("aud_42", track.id)
        assertEquals("https://img/1000.jpg", track.artworkUrl)
        assertEquals(listOf("https://mirror.audius.co"), track.artworkMirrors)
        assertEquals("https://api.audius.co/v1/tracks/aud_42/stream?app_name=Sonora", track.streamUrl)

        // LyricsApi fallback from /api/get 404 to /api/search with cleaned title
        val lyricsApi = LyricsApi(mockClient)
        val lyrics = lyricsApi.fetchLyrics(
            Track(
                id = "aud_42",
                title = "Fallback Beat (Official Audio) [Remix] feat. Guest",
                artist = "SynthLord",
                durationSec = 205
            )
        )
        assertEquals("aud_42", lyrics.trackKey)
        assertEquals(2, lyrics.syncedLines.size)
        assertEquals("Pure light", lyrics.syncedLines.first().text)

        // AudiobookApi prefers 128kb / non-64kb and sorts chapters numerically
        val audiobookApi = AudiobookApi(mockClient)
        val chapters = audiobookApi.getChapters(Audiobook(id = "book_1", title = "Test Book", author = "Author"))
        assertEquals(2, chapters.size)
        assertEquals("Chapter One", chapters[0].title)
        assertEquals("https://archive.org/download/book_1/ch01.mp3", chapters[0].streamUrl)
        assertEquals("Chapter Two", chapters[1].title)
        assertEquals("https://archive.org/download/book_1/ch02_128kb.mp3", chapters[1].streamUrl)
        assertEquals(TrackSource.AUDIOBOOK, chapters[0].source)

        // RadioApi maps live stations
        val radioApi = RadioApi(mockClient)
        val stations = radioApi.getTopStations()
        assertEquals(1, stations.size)
        assertTrue(stations.first().isLive)
        assertEquals(TrackSource.RADIO, stations.first().source)
        assertEquals("SomaFM Groove Salad", stations.first().title)

        // HttpClientProvider interceptor verification
        assertEquals(15_000, HttpClientProvider.client.connectTimeoutMillis)
        assertEquals(15_000, HttpClientProvider.client.readTimeoutMillis)
        assertTrue(HttpClientProvider.client.followRedirects)
        assertTrue(HttpClientProvider.client.followSslRedirects)
    }
}
