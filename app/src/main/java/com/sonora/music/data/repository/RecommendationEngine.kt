package com.sonora.music.data.repository

import com.sonora.music.data.network.MusicCatalogApi
import com.sonora.music.model.Artist
import com.sonora.music.model.ListeningEvent
import com.sonora.music.model.Playlist
import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import com.sonora.music.model.WrappedStats
import com.sonora.music.model.WrappedTopItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale

class RecommendationEngine(private val catalogApi: MusicCatalogApi) {

    suspend fun buildMadeForYou(
        history: List<ListeningEvent>,
        likedSongs: List<Track>,
        followedArtistIds: Set<String>
    ): List<Playlist> = coroutineScope {
        val genreScores = linkedMapOf<String, Int>()
        val artistScores = linkedMapOf<String, Int>()

        history.forEach { event ->
            val genre = event.genre.trim()
            if (isMusicGenre(genre)) {
                genreScores[genre] = (genreScores[genre] ?: 0) + 1
            }
            val artist = event.artist.trim()
            if (artist.isNotEmpty()) {
                artistScores[artist] = (artistScores[artist] ?: 0) + 1
            }
        }
        likedSongs.forEach { track ->
            val genre = track.genre.trim()
            if (isMusicGenre(genre)) {
                genreScores[genre] = (genreScores[genre] ?: 0) + 2
            }
            val artist = track.artist.trim()
            if (artist.isNotEmpty()) {
                artistScores[artist] = (artistScores[artist] ?: 0) + 2
            }
        }

        val rankedGenres = genreScores.entries.sortedByDescending { it.value }.map { it.key }
        val rankedArtists = artistScores.entries.sortedByDescending { it.value }.map { it.key }

        val topGenre = rankedGenres.firstOrNull()
        val secondGenre = rankedGenres.getOrNull(1)
        val topArtist = rankedArtists.firstOrNull()
        val knownIds = history.mapTo(HashSet()) { it.trackId } + likedSongs.mapTo(HashSet()) { it.id }

        val phTopDeferred = async { catalogApi.getPhTopTracks(25) }
        val opmDeferred = async { catalogApi.getOpmTrendingTracks(25) }
        val globalDeferred = async { catalogApi.getGlobalTopTracks(25) }
        val topGenreDeferred = async {
            if (topGenre != null) catalogApi.getGenreTracks(topGenre, limit = 20) else emptyList()
        }
        val secondGenreDeferred = async {
            if (secondGenre != null) catalogApi.getGenreTracks(secondGenre, limit = 15) else emptyList()
        }
        val followedDeferred = followedArtistIds
            .filter { it.isNotBlank() }
            .take(3)
            .map { artistId -> async { catalogApi.getArtistTopTracks(artistId, limit = 10) } }
        val artistSearchDeferred = async {
            if (topArtist != null) catalogApi.searchTracks(topArtist, limit = 12) else emptyList()
        }

        val phTop = phTopDeferred.await()
        val opmTrending = opmDeferred.await()
        val globalTop = globalDeferred.await()
        val topGenreTracks = topGenreDeferred.await()
        val secondGenreTracks = secondGenreDeferred.await()
        val followedTracks = followedDeferred.awaitAll().flatten()
        val artistTracks = artistSearchDeferred.await()

        val discoverCandidates = (opmTrending + topGenreTracks + globalTop + phTop).distinctBy { it.id }
        val freshDiscover = discoverCandidates.filter { it.id !in knownIds }
        val discoverTracks = (freshDiscover + discoverCandidates)
            .distinctBy { it.id }
            .take(20)

        val releaseTracks = (followedTracks + phTop + secondGenreTracks + globalTop)
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Track> { it.releaseDate.isNotBlank() }
                    .thenByDescending { it.releaseDate }
            )
            .take(20)

        val likedMusic = likedSongs.filter {
            it.source == TrackSource.AUDIUS &&
                !it.isPreviewOnly &&
                it.durationSec >= 90 &&
                it.streamUrl.startsWith("http", ignoreCase = true)
        }.take(8)
        val dailyTracks = (likedMusic + artistTracks + topGenreTracks + opmTrending + phTop + globalTop)
            .distinctBy { it.id }
            .take(20)

        listOf(
            Playlist(
                id = "mix_discover_weekly",
                name = "Discover Weekly",
                description = topGenre?.let {
                    "Fresh $it & trending PH and global cuts picked for you."
                } ?: "Fresh OPM and global tracks picked for your rotation.",
                trackIds = discoverTracks.map { it.id },
                tracks = discoverTracks,
                coverColorStart = 0xFF1DB954L,
                coverColorEnd = 0xFF121212L,
                artworkUrl = discoverTracks.firstOrNull()?.artworkUrl,
                isUserCreated = false,
                isSystemMix = true,
                authorName = "Made for You"
            ),
            Playlist(
                id = "mix_release_radar",
                name = "Release Radar",
                description = if (followedArtistIds.isNotEmpty()) {
                    "New drops from artists you follow and PH charts."
                } else {
                    "Latest PH and global releases across your rotation."
                },
                trackIds = releaseTracks.map { it.id },
                tracks = releaseTracks,
                coverColorStart = 0xFF2E4D45L,
                coverColorEnd = 0xFF121212L,
                artworkUrl = releaseTracks.firstOrNull()?.artworkUrl,
                isUserCreated = false,
                isSystemMix = true,
                authorName = "Made for You"
            ),
            Playlist(
                id = "mix_daily_mix",
                name = "Daily Mix",
                description = if (rankedArtists.isNotEmpty()) {
                    "${rankedArtists.take(2).joinToString(", ")} and ${topGenre ?: "OPM & Global"} favorites."
                } else {
                    "Your daily blend of OPM hits and global chart favorites."
                },
                trackIds = dailyTracks.map { it.id },
                tracks = dailyTracks,
                coverColorStart = 0xFF3E3A54L,
                coverColorEnd = 0xFF121212L,
                artworkUrl = dailyTracks.firstOrNull()?.artworkUrl,
                isUserCreated = false,
                isSystemMix = true,
                authorName = "Made for You"
            )
        )
    }

    suspend fun getSmartShufflePicks(
        seedTracks: List<Track>,
        excludeIds: Set<String>,
        count: Int = 5
    ): List<Track> = coroutineScope {
        if (count <= 0) return@coroutineScope emptyList()

        val excluded = excludeIds + seedTracks.map { it.id }
        val dominantGenre = seedTracks
            .map { it.genre.trim() }
            .filter { isMusicGenre(it) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val seedWithArtist = seedTracks.firstOrNull { it.artist.isNotBlank() }

        val genreDeferred = async {
            if (dominantGenre != null) catalogApi.getGenreTracks(dominantGenre, limit = 20)
            else catalogApi.getPhTopTracks(20)
        }
        val artistDeferred = async {
            when {
                seedWithArtist != null && seedWithArtist.artistId.isNotBlank() ->
                    catalogApi.getArtistTopTracks(seedWithArtist.artistId, seedWithArtist.artist, limit = 12)
                seedWithArtist != null ->
                    catalogApi.searchTracks(seedWithArtist.artist, limit = 12)
                else -> emptyList()
            }
        }
        val opmDeferred = async { catalogApi.getOpmTrendingTracks(15) }
        val globalDeferred = async { catalogApi.getGlobalTopTracks(15) }

        val candidates = (
            artistDeferred.await() +
                genreDeferred.await() +
                opmDeferred.await() +
                globalDeferred.await()
            )
            .distinctBy { it.id }
            .filter { it.id !in excluded && it.streamUrl.isNotBlank() }

        candidates.take(count).map { it.copy(isSmartPick = true) }
    }

    suspend fun buildTrackRadio(seedTrack: Track): List<Track> = coroutineScope {
        val genre = seedTrack.genre.trim().takeIf { isMusicGenre(it) }
        val mood = seedTrack.mood.trim()

        val artistDeferred = async {
            when {
                seedTrack.artistId.isNotBlank() || seedTrack.artist.isNotBlank() ->
                    catalogApi.getArtistTopTracks(seedTrack.artistId, seedTrack.artist, limit = 12)
                else -> emptyList()
            }
        }
        val relatedArtistsDeferred = async {
            if (seedTrack.artistId.isNotBlank()) catalogApi.getRelatedArtists(seedTrack.artistId)
            else emptyList()
        }
        val genreDeferred = async {
            if (genre != null) catalogApi.getGenreTracks(genre, limit = 20)
            else catalogApi.getPhTopTracks(20)
        }
        val moodDeferred = async {
            if (mood.isNotEmpty()) catalogApi.searchTracks(mood, limit = 12) else emptyList()
        }

        val relatedArtistTracks = relatedArtistsDeferred.await()
            .take(2)
            .map { rel -> async { catalogApi.getArtistTopTracks(rel.id, rel.name, limit = 6) } }
            .awaitAll()
            .flatten()

        val related = (
            moodDeferred.await() +
                artistDeferred.await() +
                relatedArtistTracks +
                genreDeferred.await()
            )
            .filter { it.id != seedTrack.id }
            .distinctBy { it.id }
            .take(24)

        listOf(seedTrack) + related
    }

    suspend fun buildArtistRadio(artist: Artist): List<Track> = coroutineScope {
        val artistTracksDeferred = async { catalogApi.getArtistTopTracks(artist.id, artist.name, limit = 15) }
        val relatedArtistsDeferred = async { catalogApi.getRelatedArtists(artist.id) }
        val searchDeferred = async {
            if (artist.name.isNotBlank()) catalogApi.searchTracks(artist.name, limit = 12) else emptyList()
        }

        val artistTracks = artistTracksDeferred.await()
        val relatedTracks = relatedArtistsDeferred.await()
            .take(3)
            .map { rel -> async { catalogApi.getArtistTopTracks(rel.id, rel.name, limit = 6) } }
            .awaitAll()
            .flatten()
        val searchTracks = searchDeferred.await()

        val topGenre = (artistTracks + searchTracks)
            .map { it.genre.trim() }
            .firstOrNull { isMusicGenre(it) }

        val fallbackTracks = if (topGenre != null) {
            catalogApi.getGenreTracks(topGenre, limit = 15)
        } else {
            catalogApi.getOpmTrendingTracks(15)
        }

        (artistTracks + relatedTracks + searchTracks + fallbackTracks)
            .distinctBy { it.id }
            .take(25)
    }

    suspend fun generateAiPlaylist(prompt: String): Playlist = coroutineScope {
        val trimmedPrompt = prompt.trim()
        val (genres, searchTerms) = parsePromptKeywords(trimmedPrompt)

        val genreDeferreds = genres.take(2).map { genre ->
            async { catalogApi.getGenreTracks(genre, limit = 15) }
        }
        val searchDeferreds = searchTerms.take(2).map { term ->
            async { catalogApi.searchTracks(term, limit = 15) }
        }

        val fetched = (genreDeferreds.awaitAll().flatten() + searchDeferreds.awaitAll().flatten())
            .distinctBy { it.id }
            .toMutableList()

        if (fetched.size < 10) {
            val phFallback = async { catalogApi.getPhTopTracks(20) }
            val globalFallback = async { catalogApi.getGlobalTopTracks(15) }
            fetched.addAll(phFallback.await())
            fetched.addAll(globalFallback.await())
        }

        val tracks = fetched.distinctBy { it.id }.take(25)
        val (colorStart, colorEnd) = selectPaletteForPrompt(trimmedPrompt, genres)
        val displayTitle = formatPromptTitle(trimmedPrompt)

        Playlist(
            id = "ai_${System.currentTimeMillis()}",
            name = displayTitle,
            description = if (trimmedPrompt.isNotBlank()) {
                "Mix generated for \"$trimmedPrompt\""
            } else {
                "Custom Mood Mix"
            },
            trackIds = tracks.map { it.id },
            tracks = tracks,
            coverColorStart = colorStart,
            coverColorEnd = colorEnd,
            artworkUrl = tracks.firstOrNull()?.artworkUrl,
            isUserCreated = false,
            isSystemMix = true,
            authorName = "Sonora Mix"
        )
    }

    fun parsePromptKeywords(prompt: String): Pair<List<String>, List<String>> {
        val normalized = prompt.lowercase(Locale.ROOT).trim()
        if (normalized.isEmpty()) {
            return listOf("OPM", "Pop") to listOf("Cup of Joe", "BINI")
        }

        val genres = linkedSetOf<String>()
        val terms = linkedSetOf<String>()

        for (rule in MOOD_RULES) {
            if (rule.triggers.any { trigger -> normalized.contains(trigger) }) {
                genres.addAll(rule.genres)
                terms.addAll(rule.searchTerms)
            }
        }

        val tokens = normalized
            .split(Regex("[^a-z0-9\\-]+"))
            .map { it.trim('-') }
            .filter { it.length >= 3 && it !in STOP_WORDS }

        terms.addAll(tokens)

        if (genres.isEmpty()) {
            genres.add("OPM")
            genres.add("Pop")
        }
        if (terms.isEmpty()) {
            terms.add(normalized)
        }

        return genres.toList() to terms.toList()
    }

    fun computeWrapped(
        history: List<ListeningEvent>,
        likedSongs: List<Track>
    ): WrappedStats {
        if (history.isEmpty() && likedSongs.isEmpty()) {
            return WrappedStats()
        }

        val totalMs = history.sumOf { it.listenedMs.coerceAtLeast(0L) }
        val totalMinutes = (totalMs / 60_000L).toInt()
        val totalPlays = history.size
        val uniqueTracks = history.map { it.trackId }.filter { it.isNotBlank() }.toSet().size
        val uniqueArtists = history.map { it.artist.trim() }.filter { it.isNotEmpty() }.toSet().size

        val likedArtByTrackId = likedSongs.associateBy({ it.id }, { it.artworkUrl })

        val topTracks = history
            .groupBy { it.trackId.ifBlank { "${it.title}|${it.artist}" } }
            .values
            .sortedWith(
                compareByDescending<List<ListeningEvent>> { it.size }
                    .thenByDescending { group -> group.sumOf { it.listenedMs } }
            )
            .take(5)
            .map { group ->
                val sample = group.first()
                val art = group.firstNotNullOfOrNull { it.artworkUrl }
                    ?: likedArtByTrackId[sample.trackId]
                WrappedTopItem(
                    name = sample.title.ifBlank { "Unknown Track" },
                    subtitle = sample.artist.ifBlank { "Featured Artist" },
                    artworkUrl = art,
                    playCount = group.size,
                    totalMinutes = (group.sumOf { it.listenedMs.coerceAtLeast(0L) } / 60_000L).toInt()
                )
            }

        val topArtists = history
            .filter { it.artist.isNotBlank() }
            .groupBy { it.artist.trim() }
            .values
            .sortedWith(
                compareByDescending<List<ListeningEvent>> { it.size }
                    .thenByDescending { group -> group.sumOf { it.listenedMs } }
            )
            .take(5)
            .map { group ->
                val artistName = group.first().artist.trim()
                val trackCount = group.map { it.trackId }.toSet().size
                val art = group.firstNotNullOfOrNull { it.artworkUrl }
                    ?: likedSongs.firstOrNull { it.artist.equals(artistName, ignoreCase = true) }?.artworkUrl
                WrappedTopItem(
                    name = artistName,
                    subtitle = "$trackCount ${if (trackCount == 1) "track" else "tracks"}",
                    artworkUrl = art,
                    playCount = group.size,
                    totalMinutes = (group.sumOf { it.listenedMs.coerceAtLeast(0L) } / 60_000L).toInt()
                )
            }

        val topGenres = history
            .groupBy { it.genre.trim().ifBlank { "Music" } }
            .values
            .sortedWith(
                compareByDescending<List<ListeningEvent>> { it.size }
                    .thenByDescending { group -> group.sumOf { it.listenedMs } }
            )
            .take(5)
            .map { group ->
                val genreName = group.first().genre.trim().ifBlank { "Music" }
                WrappedTopItem(
                    name = genreName,
                    subtitle = "${group.size} ${if (group.size == 1) "play" else "plays"}",
                    artworkUrl = group.firstNotNullOfOrNull { it.artworkUrl },
                    playCount = group.size,
                    totalMinutes = (group.sumOf { it.listenedMs.coerceAtLeast(0L) } / 60_000L).toInt()
                )
            }

        val topGenreName = topGenres.firstOrNull()?.name
            ?: likedSongs.map { it.genre.trim() }.firstOrNull { isMusicGenre(it) }
            ?: ""
        val (personaTitle, personaSubtitle, auraColors) = resolvePersona(
            topGenre = topGenreName,
            uniqueArtists = uniqueArtists,
            uniqueGenreCount = topGenres.size
        )

        return WrappedStats(
            totalMinutes = totalMinutes,
            totalPlays = totalPlays,
            uniqueTracks = uniqueTracks,
            uniqueArtists = uniqueArtists,
            topTracks = topTracks,
            topArtists = topArtists,
            topGenres = topGenres,
            personaTitle = personaTitle,
            personaSubtitle = personaSubtitle,
            auraColors = auraColors
        )
    }

    private fun resolvePersona(
        topGenre: String,
        uniqueArtists: Int,
        uniqueGenreCount: Int
    ): Triple<String, String, List<Long>> {
        val g = topGenre.lowercase(Locale.ROOT)
        return when {
            g.contains("opm") || g.contains("p-pop") || g.contains("pinoy") -> Triple(
                "OPM Trailblazer",
                "Keeping Pinoy charts, P-Pop hooks, and Manila soundscapes in heavy rotation.",
                listOf(0xFF1DB954L, 0xFF2E4D45L, 0xFF181818L)
            )
            g.contains("electronic") || g.contains("house") || g.contains("techno") || g.contains("synth") -> Triple(
                "Neon Architect",
                "Thriving on electric pulses, late-night synths, and high-voltage grooves.",
                listOf(0xFF1DB954L, 0xFF2E3C48L, 0xFF181818L)
            )
            g.contains("hip-hop") || g.contains("hip hop") || g.contains("rap") || g.contains("trap") -> Triple(
                "Rhythm Maverick",
                "Locked into heavy 808s, crisp cadences, and forward-pushing beats.",
                listOf(0xFF1DB954L, 0xFF42322AL, 0xFF181818L)
            )
            g.contains("lo-fi") || g.contains("lofi") || g.contains("ambient") || g.contains("chill") -> Triple(
                "Midnight Voyager",
                "Drifting through warm textures, tranquil loops, and late-night calm.",
                listOf(0xFF1DB954L, 0xFF283B48L, 0xFF181818L)
            )
            g.contains("rock") || g.contains("indie") || g.contains("alternative") -> Triple(
                "Velvet Rebel",
                "Chasing raw guitars, analog grit, and anthems that stay after midnight.",
                listOf(0xFF1DB954L, 0xFF3E2D38L, 0xFF181818L)
            )
            g.contains("jazz") || g.contains("soul") || g.contains("r&b") || g.contains("acoustic") -> Triple(
                "Soul Alchemist",
                "Drawn to rich harmonies, warm grooves, and timeless vocal expression.",
                listOf(0xFF1DB954L, 0xFF3A3528L, 0xFF181818L)
            )
            g.contains("pop") || g.contains("k-pop") || g.contains("dance") -> Triple(
                "Prism Headliner",
                "Living for radiant hooks, kinetic choruses, and nonstop momentum.",
                listOf(0xFF1DB954L, 0xFF332D48L, 0xFF181818L)
            )
            uniqueArtists >= 10 && uniqueGenreCount >= 4 -> Triple(
                "Genre Chameleon",
                "Fearlessly crossing soundscapes with a taste for endless discovery.",
                listOf(0xFF1DB954L, 0xFF2B3E44L, 0xFF181818L)
            )
            else -> Triple(
                "Sonic Explorer",
                "Always chasing the next great track across OPM and global charts.",
                listOf(0xFF1DB954L, 0xFF2E3C48L, 0xFF181818L)
            )
        }
    }

    private fun selectPaletteForPrompt(prompt: String, genres: List<String>): Pair<Long, Long> {
        val text = (prompt + " " + genres.joinToString(" ")).lowercase(Locale.ROOT)
        return when {
            text.contains("workout") || text.contains("gym") || text.contains("rock") || text.contains("hip-hop") ->
                0xFF5A2E38L to 0xFF181818L
            text.contains("chill") || text.contains("night") || text.contains("ambient") || text.contains("sleep") ->
                0xFF23394DL to 0xFF181818L
            text.contains("focus") || text.contains("study") || text.contains("lofi") || text.contains("lo-fi") ->
                0xFF3A3252L to 0xFF181818L
            text.contains("opm") || text.contains("pinoy") || text.contains("hugot") || text.contains("p-pop") ->
                0xFF1F4E37L to 0xFF181818L
            else -> 0xFF1DB954L to 0xFF181818L
        }
    }

    private fun formatPromptTitle(prompt: String): String {
        if (prompt.isBlank()) return "Mood Mix"
        val words = prompt.split(Regex("\\s+")).filter { it.isNotBlank() }.take(5)
        val formatted = words.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        return if (formatted.length > 36) "${formatted.take(33)}..." else formatted
    }

    private fun isMusicGenre(genre: String): Boolean {
        if (genre.isBlank()) return false
        return genre.lowercase(Locale.ROOT) !in NON_MUSIC_GENRES
    }

    private data class MoodRule(
        val triggers: List<String>,
        val genres: List<String>,
        val searchTerms: List<String>
    )

    companion object {
        private val NON_MUSIC_GENRES = setOf("music", "podcast", "audiobook", "live radio", "unknown")

        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "from", "into", "that", "this", "music",
            "song", "songs", "track", "tracks", "playlist", "mix", "vibe", "vibes",
            "make", "create", "build", "give", "some", "best", "good", "mood"
        )

        private val MOOD_RULES = listOf(
            MoodRule(
                triggers = listOf("opm", "pinoy", "tagalog", "hugot", "manila", "kilig", "senti", "original pilipino"),
                genres = listOf("OPM", "Chill"),
                searchTerms = listOf("Cup of Joe", "Dionela", "Arthur Nery")
            ),
            MoodRule(
                triggers = listOf("p-pop", "ppop", "bini", "sb19", "alamat"),
                genres = listOf("P-Pop", "Pop"),
                searchTerms = listOf("BINI", "SB19")
            ),
            MoodRule(
                triggers = listOf("throwback", "2000s", "90s", "classic opm", "banda"),
                genres = listOf("Throwback OPM", "Rock"),
                searchTerms = listOf("Eraserheads", "Rivermaya")
            ),
            MoodRule(
                triggers = listOf("gym", "workout", "run", "running", "cardio", "fitness", "pump", "hype", "energy", "energetic", "intense"),
                genres = listOf("Electronic", "Hip-Hop/Rap", "House"),
                searchTerms = listOf("workout", "energy", "bass", "hype")
            ),
            MoodRule(
                triggers = listOf("chill", "relax", "calm", "sleep", "night", "midnight", "rain", "ambient", "peaceful", "soft"),
                genres = listOf("Ambient", "Lo-Fi", "Chill", "R&B/Soul"),
                searchTerms = listOf("chill", "ambient", "night", "dream")
            ),
            MoodRule(
                triggers = listOf("focus", "study", "coding", "code", "work", "reading", "concentration", "deep"),
                genres = listOf("Lo-Fi", "Ambient", "Classical", "Electronic"),
                searchTerms = listOf("lofi", "study", "instrumental", "focus")
            ),
            MoodRule(
                triggers = listOf("party", "club", "dance", "festival", "rave", "weekend", "friday", "dj"),
                genres = listOf("Dance", "House", "Electronic", "Pop"),
                searchTerms = listOf("dance", "club", "party")
            ),
            MoodRule(
                triggers = listOf("sad", "heartbreak", "melancholy", "cry", "lonely", "blue", "emotional"),
                genres = listOf("OPM", "R&B", "Chill"),
                searchTerms = listOf("Moira Dela Torre", "Arthur Nery", "acoustic")
            ),
            MoodRule(
                triggers = listOf("happy", "sunny", "summer", "upbeat", "morning", "feel good", "joy", "bright"),
                genres = listOf("Pop", "P-Pop", "R&B"),
                searchTerms = listOf("BINI", "Sabrina Carpenter", "Bruno Mars")
            ),
            MoodRule(
                triggers = listOf("drive", "driving", "road trip", "highway", "cruising", "sunset", "neon", "synthwave", "cyberpunk"),
                genres = listOf("Pop", "R&B", "Rock"),
                searchTerms = listOf("The Weeknd", "Lola Amour", "Cup of Joe")
            ),
            MoodRule(
                triggers = listOf("hip hop", "hip-hop", "rap", "trap", "bars", "808", "pinoy rap"),
                genres = listOf("Pinoy Hip-Hop", "Hip-Hop"),
                searchTerms = listOf("Hev Abi", "Kendrick Lamar")
            ),
            MoodRule(
                triggers = listOf("rock", "metal", "punk", "guitar", "indie", "alt"),
                genres = listOf("Rock", "Throwback OPM"),
                searchTerms = listOf("IV Of Spades", "Eraserheads")
            ),
            MoodRule(
                triggers = listOf("jazz", "soul", "funk", "groove", "coffee", "cafe", "r&b"),
                genres = listOf("R&B", "Chill"),
                searchTerms = listOf("Dionela", "Arthur Nery", "SZA")
            ),
            MoodRule(
                triggers = listOf("k-pop", "kpop", "jennie", "blackpink", "bts"),
                genres = listOf("K-Pop", "Pop"),
                searchTerms = listOf("JENNIE", "BLACKPINK")
            ),
            MoodRule(
                triggers = listOf("lofi", "lo-fi", "beats"),
                genres = listOf("Lo-Fi", "Chill"),
                searchTerms = listOf("lofi", "beats")
            ),
            MoodRule(
                triggers = listOf("techno", "house", "edm", "synth"),
                genres = listOf("Techno", "House", "Electronic"),
                searchTerms = listOf("techno", "house", "synth")
            ),
            MoodRule(
                triggers = listOf("acoustic", "folk", "unplugged"),
                genres = listOf("Chill", "OPM"),
                searchTerms = listOf("Ben&Ben", "TJ Monterde")
            )
        )
    }
}
