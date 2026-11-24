package com.sonora.music.data.repository

import com.sonora.music.data.network.AudiusApi
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

class RecommendationEngine(private val audiusApi: AudiusApi) {

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

        val undergroundDeferred = async { audiusApi.getUndergroundTrending(20) }
        val topGenreDeferred = async { audiusApi.getTrendingTracks(genre = topGenre, limit = 20) }
        val secondGenreDeferred = async {
            if (secondGenre != null) audiusApi.getTrendingTracks(genre = secondGenre, limit = 15)
            else audiusApi.getTrendingTracks(limit = 15)
        }
        val followedDeferred = followedArtistIds
            .filter { it.isNotBlank() }
            .take(3)
            .map { artistId -> async { audiusApi.getArtistTracks(artistId, limit = 10) } }
        val artistSearchDeferred = async {
            if (topArtist != null) audiusApi.searchTracks(topArtist, limit = 12) else emptyList()
        }

        val underground = undergroundDeferred.await()
        val topGenreTracks = topGenreDeferred.await()
        val secondGenreTracks = secondGenreDeferred.await()
        val followedTracks = followedDeferred.awaitAll().flatten()
        val artistTracks = artistSearchDeferred.await()

        val discoverCandidates = (underground + topGenreTracks).distinctBy { it.id }
        val freshDiscover = discoverCandidates.filter { it.id !in knownIds }
        val discoverTracks = (freshDiscover + discoverCandidates)
            .distinctBy { it.id }
            .take(20)

        val releaseTracks = (followedTracks + secondGenreTracks)
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Track> { it.releaseDate.isNotBlank() }
                    .thenByDescending { it.releaseDate }
            )
            .take(20)

        val likedAudio = likedSongs.filter { it.source == TrackSource.AUDIUS }.take(8)
        val dailyTracks = (likedAudio + artistTracks + topGenreTracks + underground)
            .distinctBy { it.id }
            .take(20)

        listOf(
            Playlist(
                id = "mix_discover_weekly",
                name = "Discover Weekly",
                description = topGenre?.let {
                    "Fresh underground & $it cuts curated for your rotation."
                } ?: "Fresh underground discoveries tailored to your vibe.",
                trackIds = discoverTracks.map { it.id },
                tracks = discoverTracks,
                coverColorStart = 0xFF1ED760L,
                coverColorEnd = 0xFF0B2F24L,
                artworkUrl = discoverTracks.firstOrNull()?.artworkUrl,
                isUserCreated = false,
                isSystemMix = true,
                authorName = "Made for You"
            ),
            Playlist(
                id = "mix_release_radar",
                name = "Release Radar",
                description = if (followedArtistIds.isNotEmpty()) {
                    "Latest drops from artists you follow and rising scenes."
                } else {
                    "New & trending releases across your favorite waves."
                },
                trackIds = releaseTracks.map { it.id },
                tracks = releaseTracks,
                coverColorStart = 0xFF00E5FFL,
                coverColorEnd = 0xFF0F2744L,
                artworkUrl = releaseTracks.firstOrNull()?.artworkUrl,
                isUserCreated = false,
                isSystemMix = true,
                authorName = "Made for You"
            ),
            Playlist(
                id = "mix_daily_mix",
                name = "Daily Mix",
                description = if (rankedArtists.isNotEmpty()) {
                    "Built around ${rankedArtists.take(2).joinToString(" & ")} and ${topGenre ?: "your favorites"}."
                } else {
                    "Your everyday blend of high-rotation favorites."
                },
                trackIds = dailyTracks.map { it.id },
                tracks = dailyTracks,
                coverColorStart = 0xFF8C6EFFL,
                coverColorEnd = 0xFF1B1540L,
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
        val dominantArtist = seedTracks
            .map { it.artist.trim() }
            .firstOrNull { it.isNotEmpty() }

        val genreDeferred = async { audiusApi.getTrendingTracks(genre = dominantGenre, limit = 20) }
        val artistDeferred = async {
            if (dominantArtist != null) audiusApi.searchTracks(dominantArtist, limit = 12)
            else emptyList()
        }
        val undergroundDeferred = async { audiusApi.getUndergroundTrending(15) }

        val candidates = (artistDeferred.await() + genreDeferred.await() + undergroundDeferred.await())
            .distinctBy { it.id }
            .filter { it.id !in excluded && it.streamUrl.isNotBlank() }

        candidates.take(count).map { it.copy(isSmartPick = true) }
    }

    suspend fun buildTrackRadio(seedTrack: Track): List<Track> = coroutineScope {
        val genre = seedTrack.genre.trim().takeIf { isMusicGenre(it) }
        val mood = seedTrack.mood.trim()

        val artistDeferred = async {
            when {
                seedTrack.artistId.isNotBlank() -> audiusApi.getArtistTracks(seedTrack.artistId, limit = 12)
                seedTrack.artist.isNotBlank() -> audiusApi.searchTracks(seedTrack.artist, limit = 12)
                else -> emptyList()
            }
        }
        val genreDeferred = async { audiusApi.getTrendingTracks(genre = genre, limit = 20) }
        val moodDeferred = async {
            if (mood.isNotEmpty()) audiusApi.searchTracks(mood, limit = 12) else emptyList()
        }

        val related = (moodDeferred.await() + artistDeferred.await() + genreDeferred.await())
            .filter { it.id != seedTrack.id }
            .distinctBy { it.id }
            .take(24)

        listOf(seedTrack) + related
    }

    suspend fun buildArtistRadio(artist: Artist): List<Track> = coroutineScope {
        val artistTracksDeferred = async { audiusApi.getArtistTracks(artist.id, limit = 20) }
        val searchDeferred = async { audiusApi.searchTracks(artist.name, limit = 15) }

        val artistTracks = artistTracksDeferred.await()
        val searchTracks = searchDeferred.await()
        val topGenre = (artistTracks + searchTracks)
            .map { it.genre.trim() }
            .firstOrNull { isMusicGenre(it) }

        val genreTracks = audiusApi.getTrendingTracks(genre = topGenre, limit = 20)

        (artistTracks + searchTracks + genreTracks)
            .distinctBy { it.id }
            .take(25)
    }

    suspend fun generateAiPlaylist(prompt: String): Playlist = coroutineScope {
        val trimmedPrompt = prompt.trim()
        val (genres, searchTerms) = parsePromptKeywords(trimmedPrompt)

        val genreDeferreds = genres.take(2).map { genre ->
            async { audiusApi.getTrendingTracks(genre = genre, limit = 15) }
        }
        val searchDeferreds = searchTerms.take(2).map { term ->
            async { audiusApi.searchTracks(term, limit = 15) }
        }

        val fetched = (genreDeferreds.awaitAll().flatten() + searchDeferreds.awaitAll().flatten())
            .distinctBy { it.id }
            .toMutableList()

        if (fetched.size < 10) {
            val fallback = audiusApi.getTrendingTracks(limit = 20)
            fetched.addAll(fallback)
        }

        val tracks = fetched.distinctBy { it.id }.take(25)
        val (colorStart, colorEnd) = selectPaletteForPrompt(trimmedPrompt, genres)
        val displayTitle = formatPromptTitle(trimmedPrompt)

        Playlist(
            id = "ai_${System.currentTimeMillis()}",
            name = displayTitle,
            description = if (trimmedPrompt.isNotBlank()) {
                "AI Mix generated for \"$trimmedPrompt\""
            } else {
                "AI Mix curated by Sonora"
            },
            trackIds = tracks.map { it.id },
            tracks = tracks,
            coverColorStart = colorStart,
            coverColorEnd = colorEnd,
            artworkUrl = tracks.firstOrNull()?.artworkUrl,
            isUserCreated = false,
            isSystemMix = true,
            authorName = "Sonora AI"
        )
    }

    fun parsePromptKeywords(prompt: String): Pair<List<String>, List<String>> {
        val normalized = prompt.lowercase(Locale.ROOT).trim()
        if (normalized.isEmpty()) {
            return listOf("Electronic", "Pop") to listOf("chill", "vibes")
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
            genres.add("Electronic")
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
                    subtitle = sample.artist.ifBlank { "Independent Artist" },
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
            g.contains("electronic") || g.contains("house") || g.contains("techno") || g.contains("synth") -> Triple(
                "Neon Architect",
                "Thriving on electric pulses, late-night synths, and high-voltage grooves.",
                listOf(0xFF00E5FFL, 0xFF8C6EFFL, 0xFF1ED760L)
            )
            g.contains("hip-hop") || g.contains("hip hop") || g.contains("rap") || g.contains("trap") -> Triple(
                "Rhythm Maverick",
                "Locked into heavy 808s, crisp cadences, and forward-pushing beats.",
                listOf(0xFFFF4D6DL, 0xFFFFB74DL, 0xFF8C6EFFL)
            )
            g.contains("lo-fi") || g.contains("lofi") || g.contains("ambient") || g.contains("chill") -> Triple(
                "Midnight Voyager",
                "Drifting through warm textures, tranquil loops, and late-night calm.",
                listOf(0xFF38BDF8L, 0xFF8C6EFFL, 0xFF0F7A38L)
            )
            g.contains("rock") || g.contains("indie") || g.contains("alternative") -> Triple(
                "Velvet Rebel",
                "Chasing raw guitars, analog grit, and anthems that stay after midnight.",
                listOf(0xFFFF4D6DL, 0xFF8C6EFFL, 0xFFFFB74DL)
            )
            g.contains("jazz") || g.contains("soul") || g.contains("r&b") || g.contains("acoustic") -> Triple(
                "Soul Alchemist",
                "Drawn to rich harmonies, warm grooves, and timeless vocal expression.",
                listOf(0xFFFFB74DL, 0xFF1ED760L, 0xFF00E5FFL)
            )
            g.contains("pop") || g.contains("dance") -> Triple(
                "Prism Headliner",
                "Living for radiant hooks, kinetic choruses, and nonstop momentum.",
                listOf(0xFF1ED760L, 0xFFFF4D6DL, 0xFF00E5FFL)
            )
            uniqueArtists >= 10 && uniqueGenreCount >= 4 -> Triple(
                "Genre Chameleon",
                "Fearlessly crossing soundscapes with a taste for endless discovery.",
                listOf(0xFF1ED760L, 0xFF00E5FFL, 0xFFFFB74DL)
            )
            else -> Triple(
                "Sonic Explorer",
                "Always chasing the next great wave across genres.",
                listOf(0xFF1ED760L, 0xFF00E5FFL, 0xFF7B61FFL)
            )
        }
    }

    private fun selectPaletteForPrompt(prompt: String, genres: List<String>): Pair<Long, Long> {
        val text = (prompt + " " + genres.joinToString(" ")).lowercase(Locale.ROOT)
        return when {
            text.contains("workout") || text.contains("gym") || text.contains("rock") || text.contains("hip-hop") ->
                0xFFFF4D6DL to 0xFF3E1025L
            text.contains("chill") || text.contains("night") || text.contains("ambient") || text.contains("sleep") ->
                0xFF00E5FFL to 0xFF0F2744L
            text.contains("focus") || text.contains("study") || text.contains("lofi") || text.contains("lo-fi") ->
                0xFF8C6EFFL to 0xFF1B1540L
            text.contains("jazz") || text.contains("soul") || text.contains("summer") || text.contains("coffee") ->
                0xFFFFB74DL to 0xFF3F230CL
            else -> 0xFF1ED760L to 0xFF0B2F24L
        }
    }

    private fun formatPromptTitle(prompt: String): String {
        if (prompt.isBlank()) return "Sonora AI Mix"
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
                genres = listOf("Indie", "R&B/Soul", "Alternative", "Acoustic"),
                searchTerms = listOf("sad", "acoustic", "soul")
            ),
            MoodRule(
                triggers = listOf("happy", "sunny", "summer", "upbeat", "morning", "feel good", "joy", "bright"),
                genres = listOf("Pop", "Electronic", "Dance", "R&B/Soul"),
                searchTerms = listOf("summer", "sunshine", "groove")
            ),
            MoodRule(
                triggers = listOf("drive", "driving", "road trip", "highway", "cruising", "sunset", "neon", "synthwave", "cyberpunk"),
                genres = listOf("Synthwave", "Electronic", "Rock", "Indie"),
                searchTerms = listOf("synthwave", "drive", "night", "neon")
            ),
            MoodRule(
                triggers = listOf("hip hop", "hip-hop", "rap", "trap", "bars", "808"),
                genres = listOf("Hip-Hop/Rap", "Trap"),
                searchTerms = listOf("hip hop", "rap", "trap")
            ),
            MoodRule(
                triggers = listOf("rock", "metal", "punk", "guitar", "indie", "alt"),
                genres = listOf("Rock", "Alternative", "Indie"),
                searchTerms = listOf("rock", "guitar", "indie")
            ),
            MoodRule(
                triggers = listOf("jazz", "soul", "funk", "groove", "coffee", "cafe"),
                genres = listOf("Jazz", "R&B/Soul"),
                searchTerms = listOf("jazz", "soul", "groove")
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
                genres = listOf("Acoustic", "Folk", "Indie"),
                searchTerms = listOf("acoustic", "folk")
            )
        )
    }
}
