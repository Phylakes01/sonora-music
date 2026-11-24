package com.sonora.music.data.network

import com.sonora.music.model.Track
import com.sonora.music.model.TrackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Resolves full-length playable audio streams via Audius for catalog tracks,
 * strictly validating title/artist/duration and probing stream headers before
 * upgrading. Never falls back to 30-second Deezer or iTunes preview URLs.
 */
class FullAudioResolver(private val client: OkHttpClient = HttpClientProvider.client) {

    private val resolvedCache = ConcurrentHashMap<String, Track>()

    private val probeClient: OkHttpClient by lazy {
        client.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun resolveFullTrackOrNull(track: Track): Track? = withContext(Dispatchers.IO) {
        if (!track.localUri.isNullOrBlank() ||
            track.isLive ||
            track.source in ALWAYS_BYPASS_SOURCES
        ) {
            return@withContext track
        }

        if (track.source == TrackSource.AUDIUS &&
            track.streamUrl.startsWith("http", ignoreCase = true) &&
            !isPreviewAudioUrl(track.streamUrl)
        ) {
            return@withContext track.copy(isPreviewOnly = false)
        }

        val cacheKey = track.id.trim()
        if (cacheKey.isNotEmpty()) {
            resolvedCache[cacheKey]?.let { return@withContext it }
        }

        val cleanTitle = cleanSearchText(track.title).ifBlank { track.title.trim() }
        val cleanArtist = cleanArtistText(track.artist)
        val firstArtist = extractFirstArtist(cleanArtist)

        val combined = LinkedHashMap<String, AudiusCandidate>()
        var ranked: List<Pair<AudiusCandidate, Int>> = emptyList()

        if (cleanTitle.isNotBlank()) {
            val primaryQuery = listOf(cleanTitle, cleanArtist)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            mergeCandidates(combined, searchAudiusCandidates(primaryQuery))
            ranked = rankCandidates(track, combined.values.toList())

            if (ranked.none { it.second >= CLEAN_ORIGINAL_THRESHOLD } &&
                firstArtist.isNotBlank() &&
                !firstArtist.equals(cleanArtist, ignoreCase = true)
            ) {
                mergeCandidates(combined, searchAudiusCandidates("$cleanTitle $firstArtist"))
                ranked = rankCandidates(track, combined.values.toList())
            }

            val artistNoArticle = stripLeadingArticle(firstArtist.ifBlank { cleanArtist })
            if (ranked.none { it.second >= CLEAN_ORIGINAL_THRESHOLD } &&
                artistNoArticle.isNotBlank() &&
                !artistNoArticle.equals(firstArtist, ignoreCase = true) &&
                !artistNoArticle.equals(cleanArtist, ignoreCase = true)
            ) {
                mergeCandidates(combined, searchAudiusCandidates("$cleanTitle $artistNoArticle"))
                ranked = rankCandidates(track, combined.values.toList())
            }

            if (ranked.isEmpty() && cleanTitle.length >= 6) {
                mergeCandidates(combined, searchAudiusCandidates(cleanTitle))
                ranked = rankCandidates(track, combined.values.toList())
            }
        }

        for ((candidate, _) in ranked) {
            val audiusStreamUrl =
                "$PRIMARY_HOST/v1/tracks/${encodePathSegment(candidate.id)}/stream?app_name=$APP_NAME"
            if (probeAudioStream(audiusStreamUrl)) {
                val resolved = track.copy(
                    streamUrl = audiusStreamUrl,
                    durationSec = candidate.durationSec.coerceAtLeast(track.durationSec),
                    source = TrackSource.AUDIUS,
                    isPreviewOnly = false
                )
                if (cacheKey.isNotEmpty()) {
                    resolvedCache[cacheKey] = resolved
                }
                return@withContext resolved
            }
        }

        null
    }

    suspend fun resolveFullTrack(
        track: Track,
        @Suppress("UNUSED_PARAMETER") fallbackPreviewUrl: suspend () -> String = { "" }
    ): Track = resolveFullTrackOrNull(track) ?: track.copy(
        streamUrl = "",
        isPreviewOnly = false
    )

    private fun mergeCandidates(
        target: LinkedHashMap<String, AudiusCandidate>,
        incoming: List<AudiusCandidate>
    ) {
        for (candidate in incoming) {
            if (!target.containsKey(candidate.id)) {
                target[candidate.id] = candidate
            }
        }
    }

    private fun rankCandidates(
        track: Track,
        candidates: List<AudiusCandidate>
    ): List<Pair<AudiusCandidate, Int>> =
        candidates
            .map { candidate ->
                val score = scoreCandidate(
                    targetTitle = track.title,
                    targetArtist = track.artist,
                    targetDurationSec = track.durationSec,
                    candidateTitle = candidate.title,
                    candidateUploader = candidate.uploader,
                    candidateDurationSec = candidate.durationSec,
                    isStreamable = candidate.isStreamable,
                    candidateDescription = candidate.description
                )
                candidate to score
            }
            .filter { (_, score) -> score >= ACCEPT_THRESHOLD }
            .sortedWith(
                compareByDescending<Pair<AudiusCandidate, Int>> { it.second }
                    .thenBy { (candidate, _) ->
                        if (track.durationSec > 0) abs(candidate.durationSec - track.durationSec)
                        else Int.MAX_VALUE
                    }
            )
            .take(MAX_PROBE_CANDIDATES)

    fun scoreCandidate(
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Int,
        candidateTitle: String,
        candidateUploader: String,
        candidateDurationSec: Int,
        isStreamable: Boolean,
        candidateDescription: String = ""
    ): Int {
        if (!isStreamable || candidateDurationSec < MIN_FULL_DURATION_SEC) {
            return -1000
        }

        val targetTitleTokens = tokenize(stripParensAndFeatures(targetTitle)).distinct()
        val candidateTitleTokens = tokenize(stripParensAndFeatures(candidateTitle))
        if (targetTitleTokens.isEmpty() || candidateTitleTokens.isEmpty()) {
            return -500
        }

        val candidateTitleSet = candidateTitleTokens.toSet()
        val matchedTitleCount = targetTitleTokens.count { it in candidateTitleSet }
        val titleOverlap = matchedTitleCount.toDouble() / targetTitleTokens.size.toDouble()

        if (targetTitleTokens.size == 1) {
            if (matchedTitleCount == 0) return -500
        } else {
            if (titleOverlap < MIN_MULTI_TOKEN_OVERLAP) return -500
        }

        val primaryArtistRaw = extractFirstArtist(targetArtist)
        val primaryArtistTokens = tokenize(stripParensAndFeatures(primaryArtistRaw))
            .ifEmpty { tokenize(stripParensAndFeatures(targetArtist)) }
        val allArtistTokens = tokenize(stripParensAndFeatures(targetArtist))
        val candidateArtistSearchTokens = tokenize(stripParensOnly(candidateTitle))
        val candidateUploaderTokens = tokenize(stripParensAndFeatures(candidateUploader))
        val candidateCombinedSet = candidateTitleSet + candidateArtistSearchTokens + candidateUploaderTokens

        val joinedPrimaryArtist = primaryArtistTokens.joinToString("")
        val candidateTitleNoSpaces = candidateArtistSearchTokens.joinToString("")
        val candidateUploaderNoSpaces = candidateUploaderTokens.joinToString("")
        val joinedArtistMatch = joinedPrimaryArtist.length >= 4 && (
            candidateTitleNoSpaces.contains(joinedPrimaryArtist) ||
                candidateUploaderNoSpaces.contains(joinedPrimaryArtist)
            )

        if (allArtistTokens.isNotEmpty()) {
            val matchedPrimary = primaryArtistTokens.count { it in candidateCombinedSet }
            val matchedAny = allArtistTokens.count { it in candidateCombinedSet }
            if (matchedPrimary == 0 && matchedAny == 0 && !joinedArtistMatch) {
                return -500
            }
        }

        val normFullTarget = normalizeText(targetTitle)
        val normFullCandidate = normalizeText(candidateTitle)
        val normDesc = normalizeText(candidateDescription)
        val isUnexpectedHardNoise = HARD_REJECT_NOISE_REGEXES.any { regex ->
            regex.containsMatchIn(normFullCandidate) && !regex.containsMatchIn(normFullTarget)
        } || DESC_HARD_REJECT_REGEXES.any { regex ->
            regex.containsMatchIn(normDesc) && !regex.containsMatchIn(normFullTarget)
        }
        if (isUnexpectedHardNoise) {
            return -500
        }
        val isUnexpectedVariant = VARIANT_REGEXES.any { regex ->
            regex.containsMatchIn(normFullCandidate) && !regex.containsMatchIn(normFullTarget)
        } || (DESC_COVER_BY_REGEX.containsMatchIn(normDesc) && !DESC_COVER_BY_REGEX.containsMatchIn(normFullTarget))
        val cleanPreferenceScore = if (isUnexpectedVariant) 20 else 60

        val titleBonus = when {
            candidateTitleTokens == targetTitleTokens -> 20
            matchedTitleCount == targetTitleTokens.size -> 15
            else -> (titleOverlap * 10.0).toInt()
        }

        val uploaderMatched = primaryArtistTokens.any { it in candidateUploaderTokens } ||
            (joinedPrimaryArtist.length >= 4 && candidateUploaderNoSpaces.contains(joinedPrimaryArtist))
        val allPrimaryMatched = primaryArtistTokens.isNotEmpty() &&
            (primaryArtistTokens.all { it in candidateCombinedSet } || joinedArtistMatch)
        val artistBonus = (if (uploaderMatched) 10 else 5) + (if (allPrimaryMatched) 5 else 0)

        val durationBonus = if (targetDurationSec > 0) {
            val diff = abs(candidateDurationSec - targetDurationSec)
            when {
                diff <= 35 -> 25
                diff <= 75 -> 10
                else -> 0
            }
        } else {
            0
        }

        return titleBonus + artistBonus + cleanPreferenceScore + durationBonus
    }

    private suspend fun searchAudiusCandidates(query: String): List<AudiusCandidate> {
        if (query.isBlank()) return emptyList()
        val pathAndQuery =
            "/v1/tracks/search?app_name=$APP_NAME&limit=$SEARCH_LIMIT&query=${encodeUrlParam(query)}"
        for (host in HOSTS) {
            val body = client.getText("$host$pathAndQuery") ?: continue
            val data = try {
                JSONObject(body).optJSONArray("data")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            if (data != null) {
                return parseCandidates(data)
            }
        }
        return emptyList()
    }

    private fun parseCandidates(data: JSONArray): List<AudiusCandidate> = buildList {
        for (i in 0 until data.length()) {
            val obj = data.optJSONObject(i) ?: continue
            val id = obj.optCleanString("id")
            val title = obj.optCleanString("title")
            if (id.isEmpty() || title.isEmpty()) continue

            val userObj = obj.optJSONObject("user")
            val userName = userObj?.optCleanString("name").orEmpty()
            val userHandle = userObj?.optCleanString("handle").orEmpty()
            val uploader = when {
                userName.isNotBlank() && userHandle.isNotBlank() &&
                    !userName.equals(userHandle, ignoreCase = true) -> "$userName $userHandle"
                userName.isNotBlank() -> userName
                else -> userHandle
            }

            val accessStreamable = obj.optJSONObject("access")?.optBoolean("stream", true) ?: true
            val isStreamable = obj.optBoolean("is_streamable", true) && accessStreamable
            val durationSec = obj.optInt("duration", 0).coerceAtLeast(0)
            val description = obj.optCleanString("description")

            add(
                AudiusCandidate(
                    id = id,
                    title = title,
                    uploader = uploader,
                    durationSec = durationSec,
                    isStreamable = isStreamable,
                    description = description
                )
            )
        }
    }

    private fun probeAudioStream(streamUrl: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(streamUrl)
                .get()
                .header("Range", "bytes=0-63")
                .header("Accept", "audio/*,*/*")
                .build()
            probeClient.newCall(request).execute().use { response ->
                val codeOk = response.code == 200 || response.code == 206
                val rawContentType = response.header("Content-Type")
                    ?: response.body?.contentType()?.toString()
                val contentType = rawContentType
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()
                val typeOk = contentType.startsWith("audio/") ||
                    contentType.startsWith("application/octet-stream")
                codeOk && typeOk
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }

    private fun cleanSearchText(raw: String): String =
        raw.replace(PAREN_REGEX, " ")
            .replace(BRACKET_REGEX, " ")
            .replace(FEAT_SUFFIX_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

    private fun cleanArtistText(raw: String): String =
        raw.replace(PAREN_REGEX, " ")
            .replace(BRACKET_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

    private fun extractFirstArtist(rawArtist: String): String {
        val cleaned = cleanArtistText(rawArtist)
        if (cleaned.isEmpty()) return ""
        return cleaned.split(ARTIST_SPLIT_REGEX, limit = 2)
            .firstOrNull()
            ?.trim()
            .orEmpty()
    }

    private fun stripLeadingArticle(raw: String): String =
        raw.replace(LEADING_ARTICLE_REGEX, "").trim()

    private fun stripParensOnly(raw: String): String =
        raw.replace(PAREN_REGEX, " ")
            .replace(BRACKET_REGEX, " ")

    private fun stripParensAndFeatures(raw: String): String =
        stripParensOnly(raw).replace(FEAT_SUFFIX_REGEX, "")

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

    private fun tokenize(raw: String): List<String> {
        val normalized = normalizeText(raw)
        if (normalized.isEmpty()) return emptyList()
        val rawTokens = normalized.split(' ').filter { it.isNotEmpty() }
        val filtered = rawTokens.filterNot { it in FILLER_WORDS }
        return filtered.ifEmpty { rawTokens }
    }

    private data class AudiusCandidate(
        val id: String,
        val title: String,
        val uploader: String,
        val durationSec: Int,
        val isStreamable: Boolean,
        val description: String = ""
    )

    companion object {
        private const val PRIMARY_HOST = "https://api.audius.co"
        private const val FALLBACK_HOST = "https://discoveryprovider.audius.co"
        private const val APP_NAME = "Sonora"
        private const val SEARCH_LIMIT = 25
        private const val MAX_PROBE_CANDIDATES = 3
        private const val MIN_FULL_DURATION_SEC = 90
        private const val MIN_MULTI_TOKEN_OVERLAP = 0.55
        private const val ACCEPT_THRESHOLD = 35
        private const val CLEAN_ORIGINAL_THRESHOLD = 75

        private val HOSTS = listOf(PRIMARY_HOST, FALLBACK_HOST)

        private val ALWAYS_BYPASS_SOURCES = setOf(
            TrackSource.LOCAL,
            TrackSource.PODCAST,
            TrackSource.AUDIOBOOK,
            TrackSource.RADIO
        )

        private val FILLER_WORDS = setOf(
            "the", "a", "an", "of", "and", "ft", "feat", "with", "x"
        )

        private val PAREN_REGEX = Regex("""\([^)]*\)""")
        private val BRACKET_REGEX = Regex("""\[[^\]]*]""")
        private val FEAT_SUFFIX_REGEX = Regex(
            """\s*\b(?:feat|ft)\.?\s+.*$""",
            RegexOption.IGNORE_CASE
        )
        private val ARTIST_SPLIT_REGEX = Regex(
            """\s*,\s*|\s+&\s+|\s*/\s*|\s*;\s*|\s+\b(?:feat\.?|ft\.?|with|x)\b\s+""",
            RegexOption.IGNORE_CASE
        )
        private val LEADING_ARTICLE_REGEX = Regex(
            """^(?:the|a|an)\s+""",
            RegexOption.IGNORE_CASE
        )
        private val DIACRITICS_REGEX = Regex("""\p{InCombiningDiacriticalMarks}+""")
        private val NON_ALNUM_REGEX = Regex("""[^a-z0-9]""")
        private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")

        private val HARD_REJECT_NOISE_REGEXES = listOf(
            Regex("""\bkaraoke\b"""),
            Regex("""\binstrumental\b"""),
            Regex("""\btype\s*beat\b"""),
            Regex("""\bai\s*cover\b"""),
            Regex("""\bscrewed\b"""),
            Regex("""\bchopped\b"""),
            Regex("""\bscrewed\s*n\s*chopped\b"""),
            Regex("""\bparody\b"""),
            Regex("""\bminus\s*\d+\b""")
        )

        private val DESC_HARD_REJECT_REGEXES = listOf(
            Regex("""\bpandastudio\b"""),
            Regex("""\bkaraoke\b"""),
            Regex("""\binstrumental\b"""),
            Regex("""\bshit\s*microphone\b""")
        )

        private val DESC_COVER_BY_REGEX = Regex("""\bcover\s+by\b""")

        private val VARIANT_REGEXES = listOf(
            Regex("""\bremix(?:ed|es)?\b"""),
            Regex("""\brmx\b"""),
            Regex("""\bedit\b"""),
            Regex("""\bcover\b"""),
            Regex("""\bflip\b"""),
            Regex("""\blive\b"""),
            Regex("""\bsped\s*up\b"""),
            Regex("""\bslowed\b"""),
            Regex("""\bnightcore\b"""),
            Regex("""\b8d\b"""),
            Regex("""\breverb(?:ed)?\b"""),
            Regex("""\brework\b"""),
            Regex("""\brefix\b"""),
            Regex("""\breblend\b"""),
            Regex("""\bdrill(?:[\s-]*mix)?\b"""),
            Regex("""\bviolin\b"""),
            Regex("""\bpiano\b"""),
            Regex("""\bmashup\b"""),
            Regex("""\bmash[\s-]*up\b"""),
            Regex("""\bclub\s*mix\b"""),
            Regex("""\bvelocity\s*mix\b"""),
            Regex("""\bbootleg\b"""),
            Regex("""\bboot\b"""),
            Regex("""\bfreestyle\b"""),
            Regex("""\blofi\b"""),
            Regex("""\blo[\s-]*fi\b"""),
            Regex("""\bacoustic\b"""),
            Regex("""\bextended\b"""),
            Regex("""\bvs\b"""),
            Regex("""\bbossa\b""")
        )

        internal fun isPreviewAudioUrl(url: String): Boolean {
            if (url.isBlank()) return false
            val lower = url.lowercase(Locale.ROOT)
            return lower.contains("dzcdn.net") ||
                lower.contains("audio-ssl.itunes.apple.com") ||
                lower.contains("itunes.apple.com")
        }
    }
}
