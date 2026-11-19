package com.example.core.browser

import com.example.core.security.SecurityLogRedactor
import com.example.core.web.PublicHttpSafetyGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class BrowserLink(
    val index: Int,
    val text: String,
    val url: String
)

data class BrowserFormField(
    val name: String,
    val type: String,
    val placeholder: String = "",
    val defaultValue: String = ""
)

data class BrowserForm(
    val index: Int,
    val actionUrl: String,
    val method: String,
    val fields: List<BrowserFormField>
)

data class BrowserPageSnapshot(
    val url: String,
    val title: String,
    val textContent: String,
    val links: List<BrowserLink>,
    val forms: List<BrowserForm>,
    val structuredJsonLd: List<String> = emptyList(),
    val httpStatus: Int = 200,
    val fetchedAtMs: Long = System.currentTimeMillis()
)

data class BrowserTabState(
    val tabId: Int,
    val currentUrl: String,
    val title: String,
    val historyBackStack: List<String> = emptyList(),
    val lastSnapshot: BrowserPageSnapshot? = null
)

interface BrowserHttpFetcher {
    suspend fun get(url: String): Pair<Int, String>
    suspend fun postForm(url: String, fields: Map<String, String>): Pair<Int, String>
}

class OkHttpBrowserFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : BrowserHttpFetcher {

    override suspend fun get(url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        PublicHttpSafetyGuard.requireSafePublicUrl(url)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) JARVIS-Agent/1.1")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            PublicHttpSafetyGuard.requireSafePublicUrl(finalUrl)
            val body = resp.body?.string().orEmpty().take(MAX_HTML_BYTES)
            resp.code to body
        }
    }

    override suspend fun postForm(url: String, fields: Map<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
        PublicHttpSafetyGuard.requireSafePublicUrl(url)
        val formBuilder = FormBody.Builder()
        fields.forEach { (k, v) -> formBuilder.add(k, v) }
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) JARVIS-Agent/1.1")
            .post(formBuilder.build())
            .build()
        client.newCall(req).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            PublicHttpSafetyGuard.requireSafePublicUrl(finalUrl)
            val body = resp.body?.string().orEmpty().take(MAX_HTML_BYTES)
            resp.code to body
        }
    }

    companion object {
        private const val MAX_HTML_BYTES = 220_000
    }
}

/**
 * Modular stateful Browser Automation Engine (Stage 7).
 *
 * Capabilities:
 * - Multi-tab session tracking with backstack navigation
 * - Open URLs, read structured text/title/JSON-LD, list indexed links & forms
 * - Click links by index or text match
 * - Fill & submit GET/POST forms (with strict SSRF protection & secret redaction)
 * - Zero secrets, cookies, or password fields logged
 */
class BrowserAutomationEngine(
    private val fetcher: BrowserHttpFetcher = OkHttpBrowserFetcher()
) {
    private val tabsMap = linkedMapOf<Int, BrowserTabState>()
    private var nextTabId = 1

    private val _activeTabId = MutableStateFlow<Int?>(null)
    val activeTabId: StateFlow<Int?> = _activeTabId.asStateFlow()

    private val _tabs = MutableStateFlow<List<BrowserTabState>>(emptyList())
    val tabs: StateFlow<List<BrowserTabState>> = _tabs.asStateFlow()

    suspend fun openUrl(url: String, newTab: Boolean = false): Result<BrowserPageSnapshot> = runCatching {
        val cleanUrl = normalizeUrl(url)
        PublicHttpSafetyGuard.requireSafePublicUrl(cleanUrl)
        val (status, html) = fetcher.get(cleanUrl)
        val snapshot = parseHtml(cleanUrl, html, status)

        synchronized(tabsMap) {
            val targetId = if (newTab || _activeTabId.value == null || !tabsMap.containsKey(_activeTabId.value)) {
                val id = nextTabId++
                tabsMap[id] = BrowserTabState(
                    tabId = id,
                    currentUrl = cleanUrl,
                    title = snapshot.title,
                    historyBackStack = emptyList(),
                    lastSnapshot = snapshot
                )
                id
            } else {
                val id = _activeTabId.value!!
                val prev = tabsMap[id]!!
                val newStack = (prev.historyBackStack + prev.currentUrl).takeLast(MAX_BACKSTACK)
                tabsMap[id] = prev.copy(
                    currentUrl = cleanUrl,
                    title = snapshot.title,
                    historyBackStack = newStack,
                    lastSnapshot = snapshot
                )
                id
            }
            _activeTabId.value = targetId
            publishTabsLocked()
        }
        snapshot
    }

    fun getCurrentPage(): BrowserPageSnapshot? = synchronized(tabsMap) {
        val id = _activeTabId.value ?: return null
        tabsMap[id]?.lastSnapshot
    }

    fun switchTab(tabId: Int): BrowserTabState? = synchronized(tabsMap) {
        val tab = tabsMap[tabId] ?: return null
        _activeTabId.value = tabId
        publishTabsLocked()
        tab
    }

    fun closeTab(tabId: Int): Boolean = synchronized(tabsMap) {
        val removed = tabsMap.remove(tabId) != null
        if (removed && _activeTabId.value == tabId) {
            _activeTabId.value = tabsMap.keys.lastOrNull()
        }
        publishTabsLocked()
        removed
    }

    suspend fun navigateBack(): Result<BrowserPageSnapshot> {
        val prevUrl = synchronized(tabsMap) {
            val id = _activeTabId.value ?: return Result.failure(IllegalStateException("No active browser tab."))
            val tab = tabsMap[id] ?: return Result.failure(IllegalStateException("Active browser tab missing."))
            val last = tab.historyBackStack.lastOrNull()
                ?: return Result.failure(IllegalStateException("No previous page in browser back stack."))
            tabsMap[id] = tab.copy(historyBackStack = tab.historyBackStack.dropLast(1))
            last
        }
        return runCatching {
            PublicHttpSafetyGuard.requireSafePublicUrl(prevUrl)
            val (status, html) = fetcher.get(prevUrl)
            val snap = parseHtml(prevUrl, html, status)
            synchronized(tabsMap) {
                val id = _activeTabId.value!!
                val tab = tabsMap[id]!!
                tabsMap[id] = tab.copy(currentUrl = prevUrl, title = snap.title, lastSnapshot = snap)
                publishTabsLocked()
            }
            snap
        }
    }

    suspend fun clickLink(index: Int? = null, textQuery: String? = null): Result<BrowserPageSnapshot> {
        val current = getCurrentPage()
            ?: return Result.failure(IllegalStateException("No active page open. Call browser_open first."))
        val target = when {
            index != null -> current.links.firstOrNull { it.index == index }
            !textQuery.isNullOrBlank() -> current.links.firstOrNull {
                it.text.contains(textQuery.trim(), ignoreCase = true) ||
                    it.url.contains(textQuery.trim(), ignoreCase = true)
            }
            else -> null
        } ?: return Result.failure(IllegalArgumentException("Link not found on current page (links=${current.links.size})."))

        return openUrl(target.url, newTab = false)
    }

    suspend fun fillAndSubmitForm(
        formIndex: Int,
        fieldValues: Map<String, String>
    ): Result<BrowserPageSnapshot> = runCatching {
        val current = getCurrentPage()
            ?: error("No active page open. Call browser_open first.")
        val form = current.forms.firstOrNull { it.index == formIndex }
            ?: error("Form #$formIndex not found (available forms=${current.forms.size}).")

        val mergedFields = linkedMapOf<String, String>()
        form.fields.forEach { f ->
            if (f.name.isNotBlank() && f.defaultValue.isNotBlank()) {
                mergedFields[f.name] = f.defaultValue
            }
        }
        fieldValues.forEach { (k, v) ->
            if (k.isNotBlank()) mergedFields[k] = v
        }

        PublicHttpSafetyGuard.requireSafePublicUrl(form.actionUrl)
        val (status, html, finalUrl) = if (form.method.equals("POST", ignoreCase = true)) {
            val (code, body) = fetcher.postForm(form.actionUrl, mergedFields)
            Triple(code, body, form.actionUrl)
        } else {
            val queryStr = mergedFields.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            val sep = if (form.actionUrl.contains("?")) "&" else "?"
            val targetUrl = if (queryStr.isEmpty()) form.actionUrl else "${form.actionUrl}$sep$queryStr"
            PublicHttpSafetyGuard.requireSafePublicUrl(targetUrl)
            val (code, body) = fetcher.get(targetUrl)
            Triple(code, body, targetUrl)
        }

        val snap = parseHtml(finalUrl, html, status)
        synchronized(tabsMap) {
            val id = _activeTabId.value!!
            val prev = tabsMap[id]!!
            tabsMap[id] = prev.copy(
                currentUrl = finalUrl,
                title = snap.title,
                historyBackStack = (prev.historyBackStack + prev.currentUrl).takeLast(MAX_BACKSTACK),
                lastSnapshot = snap
            )
            publishTabsLocked()
        }
        snap
    }

    fun formatPageSummary(snapshot: BrowserPageSnapshot, maxTextChars: Int = 1800): String = buildString {
        appendLine("URL: ${SecurityLogRedactor.redact(snapshot.url)}")
        appendLine("Title: ${snapshot.title}")
        appendLine("HTTP: ${snapshot.httpStatus} • Links: ${snapshot.links.size} • Forms: ${snapshot.forms.size}")
        if (snapshot.structuredJsonLd.isNotEmpty()) {
            appendLine("Structured Metadata: ${snapshot.structuredJsonLd.first().take(240)}")
        }
        appendLine("--- CONTENT ---")
        appendLine(SecurityLogRedactor.redact(snapshot.textContent.take(maxTextChars)))
        if (snapshot.links.isNotEmpty()) {
            appendLine("--- TOP LINKS ---")
            snapshot.links.take(12).forEach { link ->
                appendLine("[${link.index}] ${link.text.take(60)} -> ${link.url.take(90)}")
            }
        }
        if (snapshot.forms.isNotEmpty()) {
            appendLine("--- FORMS ---")
            snapshot.forms.take(5).forEach { form ->
                val fNames = form.fields.joinToString(", ") { "${it.name}(${it.type})" }
                appendLine("Form #${form.index} [${form.method}] -> ${form.actionUrl.take(80)} | fields: $fNames")
            }
        }
    }.trim()

    internal fun parseHtml(baseUrl: String, html: String, status: Int): BrowserPageSnapshot {
        val title = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues?.getOrNull(1)
            ?.let(::decodeEntities)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
            .ifBlank { baseUrl }

        val jsonLd = Regex(
            """<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html).map { it.groupValues[1].trim().replace(Regex("\\s+"), " ").take(400) }.take(3).toList()

        val links = mutableListOf<BrowserLink>()
        val seenUrls = linkedSetOf<String>()
        val linkRegex = Regex(
            """<a\b[^>]*href=["']([^"'#][^"']*)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        var linkIdx = 1
        for (match in linkRegex.findAll(html)) {
            if (links.size >= 40) break
            val rawHref = decodeEntities(match.groupValues[1].trim())
            val resolved = resolveUrl(baseUrl, rawHref) ?: continue
            if (!resolved.startsWith("http://") && !resolved.startsWith("https://")) continue
            if (!seenUrls.add(resolved)) continue
            val label = stripTags(match.groupValues[2]).ifBlank { resolved }
            links.add(BrowserLink(index = linkIdx++, text = label.take(100), url = resolved))
        }

        val forms = mutableListOf<BrowserForm>()
        val formRegex = Regex(
            """<form\b([^>]*)>(.*?)</form>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        var formIdx = 1
        for (match in formRegex.findAll(html)) {
            if (forms.size >= 10) break
            val attrs = match.groupValues[1]
            val body = match.groupValues[2]
            val rawAction = extractAttr(attrs, "action").orEmpty()
            val actionUrl = if (rawAction.isBlank()) baseUrl else (resolveUrl(baseUrl, rawAction) ?: baseUrl)
            val method = extractAttr(attrs, "method")?.uppercase()?.takeIf { it == "POST" || it == "GET" } ?: "GET"

            val fields = mutableListOf<BrowserFormField>()
            val inputRegex = Regex("""<(?:input|textarea|select)\b([^>]*)>""", RegexOption.IGNORE_CASE)
            for (inp in inputRegex.findAll(body)) {
                val iAttrs = inp.groupValues[1]
                val name = extractAttr(iAttrs, "name").orEmpty()
                if (name.isBlank()) continue
                val type = extractAttr(iAttrs, "type")?.lowercase() ?: "text"
                val placeholder = extractAttr(iAttrs, "placeholder").orEmpty()
                // Never copy password values into snapshots
                val defaultVal = if (type == "password") "" else extractAttr(iAttrs, "value").orEmpty()
                fields.add(BrowserFormField(name = name, type = type, placeholder = placeholder, defaultValue = defaultVal))
            }
            if (fields.isNotEmpty()) {
                forms.add(BrowserForm(index = formIdx++, actionUrl = actionUrl, method = method, fields = fields))
            }
        }

        val cleanedText = html
            .replace(Regex("""<script\b[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<style\b[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<noscript\b[^>]*>.*?</noscript>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .let(::stripTags)
            .take(8_000)

        return BrowserPageSnapshot(
            url = baseUrl,
            title = title,
            textContent = cleanedText,
            links = links,
            forms = forms,
            structuredJsonLd = jsonLd,
            httpStatus = status
        )
    }

    private fun publishTabsLocked() {
        _tabs.value = tabsMap.values.toList()
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun resolveUrl(base: String, relative: String): String? = runCatching {
        URI(base).resolve(relative).toString()
    }.getOrNull()

    private fun extractAttr(attrs: String, name: String): String? {
        val rx = Regex("""\b${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
        val m = rx.find(attrs) ?: return null
        return decodeEntities(m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } }).trim()
    }

    private fun stripTags(htmlFragment: String): String =
        decodeEntities(htmlFragment.replace(Regex("<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun decodeEntities(raw: String): String = raw
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")

    companion object {
        private const val MAX_BACKSTACK = 15
    }
}
