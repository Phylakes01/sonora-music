package com.sonora.music.data.network

import com.sonora.music.model.Artist
import com.sonora.music.model.Track
import java.text.Normalizer
import java.util.Locale

/**
 * Strictly excludes Indian, Hindi, Bollywood, Punjabi, Tamil, Telugu, Malayalam,
 * Kannada, Marathi, Bengali, Bhojpuri, Gujarati, and Desi tracks, artists, and labels
 * across all catalog, search, YouTube RSS/oEmbed, and recommendation flows.
 */
object RegionalContentFilter {

    private val INDIC_UNICODE_REGEX = Regex(
        """[\u0900-\u097F\u0980-\u09FF\u0A00-\u0A7F\u0A80-\u0AFF\u0B00-\u0B7F\u0B80-\u0BFF\u0C00-\u0C7F\u0C80-\u0CFF\u0D00-\u0D7F]"""
    )

    private val DIACRITICS_REGEX = Regex("""\p{InCombiningDiacriticalMarks}+""")
    private val PUNCT_TO_SPACE_REGEX = Regex("""[^\p{L}\p{N}\s]""")
    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")

    private val EXCLUDED_TERM_REGEXES: List<Regex> = listOf(
        // Regions, languages, and Indian music subgenres (word-bounded so indie/indiana/pinoy/opm never match)
        Regex("""\bhindi\b"""),
        Regex("""\bbollywood\b"""),
        Regex("""\bpunjabi\b"""),
        Regex("""\bbhangra\b"""),
        Regex("""\btamil\b"""),
        Regex("""\btelugu\b"""),
        Regex("""\bmalayalam\b"""),
        Regex("""\bkannada\b"""),
        Regex("""\bmarathi\b"""),
        Regex("""\bbengali\b"""),
        Regex("""\bbhojpuri\b"""),
        Regex("""\bgujarati\b"""),
        Regex("""\bharyanvi\b"""),
        Regex("""\brajasthani\b"""),
        Regex("""\bdesi\b"""),
        Regex("""\btollywood\b"""),
        Regex("""\bkollywood\b"""),
        Regex("""\bmollywood\b"""),
        Regex("""\bsandalwood\b"""),
        Regex("""\bqawwali\b"""),
        Regex("""\bbhajan\b"""),
        Regex("""\bghazal\b"""),
        Regex("""\bgarba\b"""),
        Regex("""\bdandiya\b"""),
        Regex("""\bindian\b"""),
        Regex("""\bbharat\b"""),
        Regex("""\bhindustani\b"""),
        Regex("""\bcarnatic\b"""),
        Regex("""\bfilmi\b"""),

        // Known Indian record labels & music channels
        Regex("""\bt\s*series\b"""),
        Regex("""\btseries\b"""),
        Regex("""\bzee\s*music\b"""),
        Regex("""\bzeemusic\b"""),
        Regex("""\bsony\s+music\s+india\b"""),
        Regex("""\btips\s+official\b"""),
        Regex("""\btips\s+music\b"""),
        Regex("""\bsaregama\b"""),
        Regex("""\byrf\b"""),
        Regex("""\byash\s+raj\b"""),
        Regex("""\bspeed\s+records\b"""),
        Regex("""\bdesi\s+melodies\b"""),
        Regex("""\bwhite\s+hill\s+music\b"""),

        // Prominent Indian / Bollywood / Punjabi artists & iconic tracks
        Regex("""\barijit\s+singh\b"""),
        Regex("""\bshreya\s+ghoshal\b"""),
        Regex("""\bneha\s+kakkar\b"""),
        Regex("""\bbadshah\b"""),
        Regex("""\bdiljit\s+dosanjh\b"""),
        Regex("""\bsidhu\s+moose\s+wala\b"""),
        Regex("""\bap\s+dhillon\b"""),
        Regex("""\byo\s+yo\s+honey\s+singh\b"""),
        Regex("""\batif\s+aslam\b"""),
        Regex("""\bsonu\s+nigam\b"""),
        Regex("""\banirudh\b"""),
        Regex("""\ba\s*r\s+rahman\b"""),
        Regex("""\bar\s+rahman\b"""),
        Regex("""\bpritam\b"""),
        Regex("""\bjubin\s+nautiyal\b"""),
        Regex("""\bdarshan\s+raval\b"""),
        Regex("""\barman\s+malik\b"""),
        Regex("""\bking\s+rocco\b"""),
        Regex("""\braftaar\b"""),
        Regex("""\bkrsna\b"""),
        Regex("""\bemiway\b"""),
        Regex("""\bmc\s+stan\b"""),
        Regex("""\bhanumankind\b"""),
        Regex("""\bkaran\s+aujla\b"""),
        Regex("""\bshubh\b"""),
        Regex("""\btalwiinder\b"""),
        Regex("""\btanishk\s+bagchi\b"""),
        Regex("""\bvishal\s+shekhar\b"""),
        Regex("""\bshankar\s+mahadevan\b"""),
        Regex("""\budit\s+narayan\b"""),
        Regex("""\bkumar\s+sanu\b"""),
        Regex("""\balka\s+yagnik\b"""),
        Regex("""\blata\s+mangeshkar\b"""),
        Regex("""\bkishore\s+kumar\b"""),
        Regex("""\bmohammed\s+rafi\b"""),
        Regex("""\bkailash\s+kher\b"""),
        Regex("""\bb\s+praak\b"""),
        Regex("""\bguru\s+randhawa\b"""),
        Regex("""\bharrdy\s+sandhu\b"""),
        Regex("""\bjass\s+manak\b"""),
        Regex("""\bparmish\s+verma\b"""),
        Regex("""\bsunidhi\s+chauhan\b"""),
        Regex("""\bmonali\s+thakur\b"""),
        Regex("""\bjonita\s+gandhi\b"""),
        Regex("""\bjasleen\s+royal\b"""),
        Regex("""\bprateek\s+kuhad\b"""),
        Regex("""\banuv\s+jain\b"""),
        Regex("""\britviz\b"""),
        Regex("""\bnucleya\b"""),
        Regex("""\bshah\s+rukh\b"""),
        Regex("""\btu\s+mil\s+jaaye\b"""),
        Regex("""\baashiqui\b"""),
        Regex("""\btum\s+hi\s+ho\b"""),
        Regex("""\bkesariya\b"""),
        Regex("""\bchaleya\b"""),
        Regex("""\bapna\s+bana\s+le\b"""),
        Regex("""\btauba\s+tauba\b"""),
        Regex("""\bpasoori\b"""),
        Regex("""\bbrown\s+munde\b""")
    )

    fun isExcludedIndianContent(
        title: String,
        artist: String = "",
        album: String = "",
        genre: String = "",
        extra: String = ""
    ): Boolean {
        if (artist.trim().equals("divine", ignoreCase = true)) {
            return true
        }

        val combinedRaw = buildString {
            append(title).append(' ')
            append(artist).append(' ')
            append(album).append(' ')
            append(genre).append(' ')
            append(extra)
        }
        if (combinedRaw.isBlank()) return false

        if (INDIC_UNICODE_REGEX.containsMatchIn(combinedRaw)) {
            return true
        }

        val normalized = normalizeForMatch(combinedRaw)
        if (normalized.isEmpty()) return false

        return EXCLUDED_TERM_REGEXES.any { it.containsMatchIn(normalized) }
    }

    fun isExcludedTrack(track: Track): Boolean = isExcludedIndianContent(
        title = track.title,
        artist = track.artist,
        album = track.album,
        genre = track.genre,
        extra = track.mood
    )

    fun filterTracks(tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        return tracks.filterNot(::isExcludedTrack)
    }

    fun filterArtists(artists: List<Artist>): List<Artist> {
        if (artists.isEmpty()) return emptyList()
        return artists.filterNot { artist ->
            isExcludedIndianContent(
                title = "",
                artist = artist.name,
                genre = artist.bio,
                extra = artist.handle
            )
        }
    }

    private fun normalizeForMatch(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .replace("'", "")
            .replace("\u2019", "")
            .replace(PUNCT_TO_SPACE_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }
}
