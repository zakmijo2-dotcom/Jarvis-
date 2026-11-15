package com.example.core.web

import com.example.core.cloud.OkHttpCloudAiClient
import com.example.core.security.PromptSecuritySanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class WebSearchProviderKind(val label: String, val requiresApiKey: Boolean) {
    WIKIPEDIA_OPENSEARCH("Wikipedia Public API (No Key)", false),
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
    val maxResults: Int = 4
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
    private val baseClient: OkHttpClient = OkHttpClient()
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

            runCatching {
                when (config.provider) {
                    WebSearchProviderKind.WIKIPEDIA_OPENSEARCH -> searchWikipedia(client, cleanQuery, config.maxResults)
                    WebSearchProviderKind.TAVILY -> searchTavily(client, cleanQuery, config)
                    WebSearchProviderKind.SERPER -> searchSerper(client, cleanQuery, config)
                    WebSearchProviderKind.CUSTOM_JSON -> searchCustomEndpoint(client, cleanQuery, config)
                }
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
            val body = resp.body?.string().orEmpty()
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
            val json = JSONObject(resp.body?.string().orEmpty())
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
            val json = JSONObject(resp.body?.string().orEmpty())
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
            val json = JSONObject(resp.body?.string().orEmpty())
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
