package com.sonora.music.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object HttpClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("User-Agent", "Sonora/1.0 (Android)")
                if (original.header("Accept").isNullOrBlank()) {
                    builder.header("Accept", "application/json")
                }
                chain.proceed(builder.build())
            }
            .build()
    }
}

internal suspend fun OkHttpClient.getText(
    url: String,
    acceptHeader: String? = null
): String? = withContext(Dispatchers.IO) {
    try {
        val requestBuilder = Request.Builder().url(url).get()
        if (!acceptHeader.isNullOrBlank()) {
            requestBuilder.header("Accept", acceptHeader)
        }
        newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.string()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}

internal fun encodeUrlParam(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

internal fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

internal fun JSONObject.optCleanString(key: String, default: String = ""): String {
    if (isNull(key)) return default
    val raw = optString(key, default)
    return if (raw == "null") default else raw.trim()
}

internal fun parseFlexibleDurationToSeconds(raw: String?): Int {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return 0

    if (trimmed.contains(':')) {
        val parts = trimmed.split(':').map { it.trim() }
        if (parts.size !in 2..3) return 0
        val parsed = parts.mapIndexed { index, part ->
            if (index == parts.lastIndex) {
                part.toDoubleOrNull()?.toInt()
            } else {
                part.toIntOrNull()
            }
        }
        if (parsed.any { it == null || it < 0 }) return 0
        return when (parsed.size) {
            2 -> (parsed[0]!! * 60 + parsed[1]!!).coerceAtLeast(0)
            3 -> (parsed[0]!! * 3600 + parsed[1]!! * 60 + parsed[2]!!).coerceAtLeast(0)
            else -> 0
        }
    }

    return (trimmed.toIntOrNull() ?: trimmed.toDoubleOrNull()?.toInt() ?: 0).coerceAtLeast(0)
}

internal fun stripHtmlTags(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

internal val SonoraCoverPalettes: List<Pair<Long, Long>> = listOf(
    0xFF1ED760L to 0xFF0B2F24L,
    0xFF00E5FFL to 0xFF0F2744L,
    0xFFFF4D6DL to 0xFF3E1025L,
    0xFF8C6EFFL to 0xFF1B1540L,
    0xFFFFB74DL to 0xFF3F230CL,
    0xFF38BDF8L to 0xFF1E1B4BL
)

internal fun coverPaletteForKey(key: String): Pair<Long, Long> {
    val index = ((key.hashCode() and Int.MAX_VALUE) % SonoraCoverPalettes.size)
    return SonoraCoverPalettes[index]
}
