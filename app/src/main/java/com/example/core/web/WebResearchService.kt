package com.example.core.web

import com.example.core.cloud.OkHttpCloudAiClient
import com.example.core.security.HttpSecuritySupport
import com.example.core.security.PromptSecuritySanitizer
import com.example.data.local.db.ResearchHistoryDao
import com.example.data.local.db.ResearchHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class WebSearchProviderKind(val label: String, val requiresApiKey: Boolean) {
    WIKIPEDIA_OPENSEARCH("Wikipedia Public API (No Key)", false),
    KEYLESS_FALLBACK("Keyless Web Fallback (DuckDuckGo)", false),
    BRAVE_SEARCH("Brave Search API", true),
    SERPAPI("SerpAPI Google Search", true),
    TAVILY("Tavily Search API", true),
    SERPER("Serper.dev Search API", true),
    CUSTOM_JSON("Custom Search JSON Endpoint", false);

    companion object {
        fun fromName(raw: String?): WebSearchProviderKind {
            if (raw.isNullOrBlank()) return WIKIPEDIA_OPENSEARCH
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WIKIPEDIA_OPENSEARCH
        }
    }
}

data class WebResearchConfig(
    val enabled: Boolean,
    val provider: WebSearchProviderKind,
    val apiKey: String = "",
    val customEndpointUrl: String = "",
    val timeoutSeconds: Int = 15,
    val maxResults: Int = 4,
    val braveApiKey: String = "",
    val serpApiKey: String = ""
)

data class WebSearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String
)

data class WebResearchReport(
    val query: String,
    val providerLabel: String,
    val results: List<WebSearchResult>,
    val fetchedAtMillis: Long = System.currentTimeMillis()
) {
    fun formatContextPrompt(): String {
        val rawBody = buildString {
            val safeQuery = PromptSecuritySanitizer.sanitizeExternalText(query, maxChars = 240)
            appendLine("LIVE WEB RESEARCH RESULTS ($providerLabel) for \"$safeQuery\":")
            if (results.isEmpty()) {
                appendLine("- No matching web results returned.")
            } else {
                results.forEachIndexed { index, item ->
                    val safeTitle = PromptSecuritySanitizer.sanitizeExternalText(item.title, maxChars = 160)
                    val safeSnippet = PromptSecuritySanitizer.sanitizeExternalText(item.snippet, maxChars = 400)
                    val safeUrl = PromptSecuritySanitizer.sanitizeExternalText(item.url, maxChars = 300)
                    appendLine("${index + 1}. $safeTitle")
                    if (safeSnippet.isNotBlank()) appendLine("   Summary: $safeSnippet")
                    if (safeUrl.isNotBlank()) appendLine("   Source: $safeUrl")
                }
            }
        }.trim()
        return PromptSecuritySanitizer.wrapUntrustedBlock(
            sourceLabel = "WebResearch:$providerLabel",
            rawContent = rawBody,
            maxChars = 3500
        ).ifBlank { rawBody }
    }
}

interface WebResearchService {
    suspend fun search(query: String, config: WebResearchConfig): Result<WebResearchReport>
}

class OkHttpWebResearchService(
    private val baseClient: OkHttpClient = OkHttpClient(),
    private val researchHistoryDao: ResearchHistoryDao? = null,
    private val braveBaseUrl: String = "https://api.search.brave.com/res/v1/web/search",
    private val serpApiBaseUrl: String = "https://serpapi.com/search.json",
    private val duckDuckGoHtmlUrl: String = "https://html.duckduckgo.com/html/"
) : WebResearchService {

    override suspend fun search(query: String, config: WebResearchConfig): Result<WebResearchReport> =
        withContext(Dispatchers.IO) {
            if (!config.enabled) {
                return@withContext Result.failure(
                    IllegalStateException("Web research is disabled in Privacy/Settings. Local models do not have internet access.")
                )
            }

            val cleanQuery = query.trim()
            if (cleanQuery.length < 2) {
                return@withContext Result.failure(IllegalArgumentException("Web research query is too short."))
            }

            val timeout = config.timeoutSeconds.coerceIn(5, 60).toLong()
            val client = baseClient.newBuilder()
                .connectTimeout(timeout.coerceAtMost(15L), TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .callTimeout(timeout + 5L, TimeUnit.SECONDS)
                .build()

            val result = runCatching {
                when (config.provider) {
                    WebSearchProviderKind.WIKIPEDIA_OPENSEARCH -> searchWikipedia(client, cleanQuery, config.maxResults)
                    WebSearchProviderKind.KEYLESS_FALLBACK -> searchKeylessFallback(client, cleanQuery, config.maxResults)
                    WebSearchProviderKind.BRAVE_SEARCH -> searchBrave(client, cleanQuery, config)
                    WebSearchProviderKind.SERPAPI -> searchSerpApi(client, cleanQuery, config)
                    WebSearchProviderKind.TAVILY -> searchTavily(client, cleanQuery, config)
                    WebSearchProviderKind.SERPER -> searchSerper(client, cleanQuery, config)
                    WebSearchProviderKind.CUSTOM_JSON -> searchCustomEndpoint(client, cleanQuery, config)
                }
            }

            result.onSuccess { report ->
                runCatching {
                    researchHistoryDao?.insertRecord(
                        ResearchHistoryEntity(
                            id = UUID.randomUUID().toString(),
                            query = cleanQuery.take(240),
                            provider = report.providerLabel,
                            urlsJson = JSONArray(report.results.map { it.url }).toString(),
                            summarySnippet = report.results.firstOrNull()?.snippet?.take(240).orEmpty(),
                            timestamp = report.fetchedAtMillis
                        )
                    )
                }
            }

            result
        }

    private fun searchBrave(
        client: OkHttpClient,
        query: String,
        config: WebResearchConfig
    ): WebResearchReport {
        val token = config.braveApiKey.trim().ifEmpty { config.apiKey.trim() }
        require(token.isNotEmpty()) { "Brave Search API key is not configured in Settings." }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val sep = if ('?' in braveBaseUrl) '&' else '?'
        val url = "$braveBaseUrl${sep}q=$encoded&count=${config.maxResults.coerceIn(1, 8)}"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Subscription-Token", token)
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Brave Search HTTP ${resp.code}: ${resp.message}")
            val json = JSONObject(HttpSecuritySupport.readBoundedUtf8Body(resp.body))
            val arr = json.optJSONObject("web")?.optJSONArray("results") ?: JSONArray()
            val results = (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val resultUrl = obj.optString("url")
                val title = obj.optString("title")
                if (title.isBlank() || resultUrl.isBlank()) return@mapNotNull null
                WebSearchResult(
                    title = title,
                    snippet = obj.optString("description").take(300),
                    url = resultUrl,
                    source = "Brave"
                )
            }
            return WebResearchReport(query, WebSearchProviderKind.BRAVE_SEARCH.label, results)
        }
    }

    private fun searchSerpApi(
        client: OkHttpClient,
        query: String,
        config: WebResearchConfig
    ): WebResearchReport {
        val apiKey = config.serpApiKey.trim().ifEmpty { config.apiKey.trim() }
        require(apiKey.isNotEmpty()) { "SerpAPI key is not configured in Settings." }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val sep = if ('?' in serpApiBaseUrl) '&' else '?'
        val url = "$serpApiBaseUrl${sep}engine=google&q=$encoded&num=${config.maxResults.coerceIn(1, 8)}&api_key=${URLEncoder.encode(apiKey, "UTF-8")}"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("SerpAPI HTTP ${resp.code}: ${resp.message}")
            val json = JSONObject(HttpSecuritySupport.readBoundedUtf8Body(resp.body))
            val arr = json.optJSONArray("organic_results") ?: JSONArray()
            val results = (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val link = obj.optString("link")
                val title = obj.optString("title")
                if (title.isBlank() || link.isBlank()) return@mapNotNull null
                WebSearchResult(
                    title = title,
                    snippet = obj.optString("snippet").take(300),
                    url = link,
                    source = "SerpAPI"
                )
            }
            return WebResearchReport(query, WebSearchProviderKind.SERPAPI.label, results)
        }
    }

    private fun searchKeylessFallback(
        client: OkHttpClient,
        query: String,
        maxResults: Int
    ): WebResearchReport {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$duckDuckGoHtmlUrl?q=$encoded"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .get()
            .build()

        val ddgResults = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("DDG HTTP ${resp.code}")
                val html = HttpSecuritySupport.readBoundedUtf8Body(resp.body, maxBytes = 768L * 1024L)
                parseDuckDuckGoHtml(html, maxResults)
            }
        }.getOrDefault(emptyList())

        if (ddgResults.isNotEmpty()) {
            return WebResearchReport(query, WebSearchProviderKind.KEYLESS_FALLBACK.label, ddgResults)
        }
        return searchWikipedia(client, query, maxResults)
    }

    internal fun parseDuckDuckGoHtml(html: String, maxResults: Int): List<WebSearchResult> {
        val linkRegex = Regex(
            """<a[^>]*class=["'][^"']*result__a[^"']*["'][^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val snippetRegex = Regex(
            """class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</(?:a|div|span)>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val links = linkRegex.findAll(html).take(maxResults.coerceIn(1, 8)).toList()
        val snippets = snippetRegex.findAll(html).map {
            it.groupValues[1].replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        }.toList()

        return links.mapIndexedNotNull { idx, m ->
            val rawUrl = m.groupValues[1]
            val cleanUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
            val title = m.groupValues[2].replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
            if (title.isBlank()) return@mapIndexedNotNull null
            WebSearchResult(
                title = title,
                snippet = snippets.getOrElse(idx) { "" },
                url = cleanUrl,
                source = "DuckDuckGo"
            )
        }
    }

    private fun searchWikipedia(
        client: OkHttpClient,
        query: String,
        maxResults: Int
    ): WebResearchReport {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srlimit=${maxResults.coerceIn(1, 6)}&format=json&utf8=1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JARVIS-Android-Agent/1.0")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Wikipedia API HTTP ${resp.code}: ${resp.message}")
            }
            val body = HttpSecuritySupport.readBoundedUtf8Body(resp.body)
            val root = JSONObject(body)
            val hits = root.optJSONObject("query")?.optJSONArray("search") ?: JSONArray()
            val results = buildList {
                for (i in 0 until hits.length()) {
                    val item = hits.optJSONObject(i) ?: continue
                    val title = item.optString("title")
                    val snippetRaw = item.optString("snippet")
                        .replace(Regex("<[^>]+>"), " ")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    val slug = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
                    add(
                        WebSearchResult(
                            title = title,
                            snippet = snippetRaw,
                            url = "https://en.wikipedia.org/wiki/$slug",
                            source = "Wikipedia"
                        )
                    )
                }
            }
            return WebResearchReport(query, configLabel(WebSearchProviderKind.WIKIPEDIA_OPENSEARCH), results)
        }
    }

    private fun searchTavily(client: OkHttpClient, query: String, config: WebResearchConfig): WebResearchReport {
        val apiKey = config.apiKey.trim()
        require(apiKey.isNotEmpty()) { "Tavily API key is not configured in Settings." }
        val payload = JSONObject().apply {
            put("api_key", apiKey)
            put("query", query)
            put("max_results", config.maxResults.coerceIn(1, 6))
        }
        val req = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Tavily HTTP ${resp.code}: ${resp.message}")
            val json = JSONObject(HttpSecuritySupport.readBoundedUtf8Body(resp.body))
            val arr = json.optJSONArray("results") ?: JSONArray()
            val results = (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                WebSearchResult(
                    title = obj.optString("title"),
                    snippet = obj.optString("content").take(260),
                    url = obj.optString("url"),
                    source = "Tavily"
                )
            }
            return WebResearchReport(query, configLabel(WebSearchProviderKind.TAVILY), results)
        }
    }

    private fun searchSerper(client: OkHttpClient, query: String, config: WebResearchConfig): WebResearchReport {
        val apiKey = config.apiKey.trim()
        require(apiKey.isNotEmpty()) { "Serper API key is not configured in Settings." }
        val payload = JSONObject().apply {
            put("q", query)
            put("num", config.maxResults.coerceIn(1, 6))
        }
        val req = Request.Builder()
            .url("https://google.serper.dev/search")
            .header("X-API-KEY", apiKey)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Serper HTTP ${resp.code}: ${resp.message}")
            val json = JSONObject(HttpSecuritySupport.readBoundedUtf8Body(resp.body))
            val arr = json.optJSONArray("organic") ?: JSONArray()
            val results = (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                WebSearchResult(
                    title = obj.optString("title"),
                    snippet = obj.optString("snippet").take(260),
                    url = obj.optString("link"),
                    source = "Serper"
                )
            }
            return WebResearchReport(query, configLabel(WebSearchProviderKind.SERPER), results)
        }
    }

    private fun searchCustomEndpoint(client: OkHttpClient, query: String, config: WebResearchConfig): WebResearchReport {
        val endpoint = config.customEndpointUrl.trim()
        require(OkHttpCloudAiClient.isSafeEndpointUrl(endpoint)) {
            "Custom Web Research URL must be valid HTTPS (or localhost for local gateway)."
        }
        val separator = if ('?' in endpoint) '&' else '?'
        val url = "${endpoint}${separator}q=${URLEncoder.encode(query, "UTF-8")}"
        val builder = Request.Builder().url(url).get()
        if (config.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.apiKey.trim()}")
        }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Custom Search HTTP ${resp.code}: ${resp.message}")
            val json = JSONObject(HttpSecuritySupport.readBoundedUtf8Body(resp.body))
            val arr = json.optJSONArray("results") ?: JSONArray()
            val results = (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                WebSearchResult(
                    title = obj.optString("title"),
                    snippet = obj.optString("snippet", obj.optString("content")),
                    url = obj.optString("url"),
                    source = "Custom"
                )
            }
            return WebResearchReport(query, configLabel(WebSearchProviderKind.CUSTOM_JSON), results)
        }
    }

    private fun configLabel(kind: WebSearchProviderKind): String = kind.label

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
