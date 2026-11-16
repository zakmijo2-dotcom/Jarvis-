package com.example.core.web

import android.os.SystemClock
import com.example.core.security.HttpSecuritySupport
import com.example.core.security.PromptSecuritySanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class WebPageExtract(
    val url: String,
    val title: String,
    val readableText: String,
    val jsonLdSummary: String = "",
    val contentType: String = "text/html",
    val durationMs: Long = 0L
) {
    fun toSanitizedContextBlock(): String {
        val raw = buildString {
            appendLine("URL: $url")
            if (title.isNotBlank()) appendLine("Title: $title")
            if (jsonLdSummary.isNotBlank()) appendLine("Structured Data: $jsonLdSummary")
            appendLine("Content:")
            append(readableText)
        }
        return PromptSecuritySanitizer.wrapUntrustedBlock(
            sourceLabel = "WebPage:$url",
            rawContent = raw,
            maxChars = 4200
        )
    }
}

/**
 * Safe webpage fetcher & clean HTML/JSON-LD readable text extractor.
 * Enforces PublicHttpSafetyGuard SSRF checks (including redirects), bounded reads, and per-host cooldown.
 */
class WebPageExtractor(
    private val baseClient: OkHttpClient = OkHttpClient(),
    private val allowLoopbackForTests: Boolean = false
) {
    private val lastHostFetchMs = ConcurrentHashMap<String, Long>()

    suspend fun fetchReadablePage(
        url: String,
        timeoutSeconds: Int = 15,
        maxChars: Int = 4000
    ): Result<WebPageExtract> = withContext(Dispatchers.IO) {
        val validation = PublicHttpSafetyGuard.validateUrl(url, allowLoopback = allowLoopbackForTests)
        if (validation is PublicHttpValidationResult.Blocked) {
            return@withContext Result.failure(SecurityException(validation.reason))
        }
        val safeUrl = (validation as PublicHttpValidationResult.Safe).normalizedUrl
        val host = validation.host

        // Polite per-host cooldown
        val now = SystemClock.elapsedRealtime()
        val prev = lastHostFetchMs[host] ?: 0L
        if (!allowLoopbackForTests && now - prev < 600L) {
            kotlinx.coroutines.delay(600L - (now - prev))
        }
        lastHostFetchMs[host] = SystemClock.elapsedRealtime()

        val client = baseClient.newBuilder()
            .connectTimeout(timeoutSeconds.coerceIn(5, 30).toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.coerceIn(5, 30).toLong(), TimeUnit.SECONDS)
            .followRedirects(false)
            .build()

        val startMs = SystemClock.elapsedRealtime()
        runCatching {
            var currentUrl = safeUrl
            var hops = 0
            while (hops < 4) {
                val req = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "JARVIS-Android-Research/1.0")
                    .header("Accept", "text/html,application/xhtml+xml,application/json,text/plain")
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.code in 300..399) {
                        val location = resp.header("Location")
                            ?: throw IllegalStateException("Redirect ${resp.code} missing Location header.")
                        val resolved = resp.request.url.resolve(location)?.toString()
                            ?: throw IllegalStateException("Invalid redirect target URL.")
                        val hopCheck = PublicHttpSafetyGuard.validateUrl(resolved, allowLoopback = allowLoopbackForTests)
                        if (hopCheck is PublicHttpValidationResult.Blocked) {
                            throw SecurityException("Blocked redirect target: ${hopCheck.reason}")
                        }
                        currentUrl = (hopCheck as PublicHttpValidationResult.Safe).normalizedUrl
                        hops++
                        return@use
                    }
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Webpage HTTP ${resp.code}: ${resp.message}")
                    }
                    val contentType = resp.header("Content-Type").orEmpty().lowercase()
                    val bodyText = HttpSecuritySupport.readBoundedUtf8Body(resp.body, maxBytes = 1024L * 1024L)
                    val durationMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)

                    return@withContext Result.success(
                        parseReadableContent(
                            url = currentUrl,
                            rawBody = bodyText,
                            contentType = contentType,
                            maxChars = maxChars,
                            durationMs = durationMs
                        )
                    )
                }
            }
            throw IllegalStateException("Too many HTTP redirects while fetching webpage.")
        }
    }

    internal fun parseReadableContent(
        url: String,
        rawBody: String,
        contentType: String,
        maxChars: Int = 4000,
        durationMs: Long = 0L
    ): WebPageExtract {
        if (contentType.contains("application/json") || rawBody.trimStart().startsWith("{")) {
            val sanitized = PromptSecuritySanitizer.sanitizeExternalText(rawBody, maxChars = maxChars)
            return WebPageExtract(
                url = url,
                title = "JSON Document",
                readableText = sanitized,
                contentType = contentType.ifBlank { "application/json" },
                durationMs = durationMs
            )
        }

        val title = TITLE_REGEX.find(rawBody)?.groupValues?.getOrNull(1)
            ?.let(::decodeHtmlEntities)
            ?.trim()
            .orEmpty()

        val jsonLdSummary = extractJsonLdSummary(rawBody)

        val cleanedHtml = SCRIPT_STYLE_REGEX.replace(rawBody, " ")
        val withoutNav = CHROME_TAGS_REGEX.replace(cleanedHtml, " ")
        val withNewlines = BLOCK_TAGS_REGEX.replace(withoutNav, "\n")
        val plainText = ALL_TAGS_REGEX.replace(withNewlines, " ")
            .let(::decodeHtmlEntities)
            .lines()
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.length >= 2 }
            .joinToString("\n")
            .take(maxChars)

        return WebPageExtract(
            url = url,
            title = PromptSecuritySanitizer.sanitizeExternalText(title, maxChars = 160),
            readableText = PromptSecuritySanitizer.sanitizeExternalText(plainText, maxChars = maxChars),
            jsonLdSummary = PromptSecuritySanitizer.sanitizeExternalText(jsonLdSummary, maxChars = 500),
            contentType = contentType.ifBlank { "text/html" },
            durationMs = durationMs
        )
    }

    private fun extractJsonLdSummary(html: String): String {
        val parts = mutableListOf<String>()
        JSON_LD_REGEX.findAll(html).take(2).forEach { match ->
            val raw = match.groupValues[1].trim()
            runCatching {
                val obj = if (raw.startsWith("[")) JSONArray(raw).optJSONObject(0) else JSONObject(raw)
                if (obj != null) {
                    val headline = obj.optString("headline").ifBlank { obj.optString("name") }
                    val desc = obj.optString("description")
                    val piece = listOf(headline, desc).filter { it.isNotBlank() }.joinToString(" — ")
                    if (piece.isNotBlank()) parts.add(piece)
                }
            }
        }
        return parts.joinToString(" | ")
    }

    private fun decodeHtmlEntities(input: String): String =
        input.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")

    companion object {
        private val TITLE_REGEX = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val JSON_LD_REGEX = Regex("""<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val SCRIPT_STYLE_REGEX = Regex("""<(script|style|noscript|svg)[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val CHROME_TAGS_REGEX = Regex("""<(nav|footer|header|aside)[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val BLOCK_TAGS_REGEX = Regex("""</?(p|div|li|h[1-6]|tr|br|article|section)[^>]*>""", RegexOption.IGNORE_CASE)
        private val ALL_TAGS_REGEX = Regex("""<[^>]+>""")
    }
}
