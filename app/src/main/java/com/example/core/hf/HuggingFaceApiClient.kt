package com.example.core.hf

import com.example.core.security.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class HuggingFaceApiClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = HF_BASE_URL
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun searchModels(
        query: String = "",
        author: String = "",
        filterTag: String? = null,
        token: String = "",
        limit: Int = 25
    ): Result<List<HfRepoSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = "${baseUrl.trimEnd('/')}/api/models".toHttpUrl().newBuilder()
                .addQueryParameter("sort", "downloads")
                .addQueryParameter("direction", "-1")
                .addQueryParameter("limit", limit.coerceIn(1, 50).toString())
                .addQueryParameter("full", "true")

            if (query.isNotBlank()) {
                urlBuilder.addQueryParameter("search", query.trim())
            }
            if (author.isNotBlank()) {
                urlBuilder.addQueryParameter("author", author.trim())
            }
            if (!filterTag.isNullOrBlank()) {
                urlBuilder.addQueryParameter("filter", filterTag.trim())
            }

            val req = buildRequest(urlBuilder.build().toString(), token)
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    throw IOException(mapHttpError(res.code, "Search failed"))
                }
                val body = res.body?.string() ?: "[]"
                val dtos = json.decodeFromString<List<HfModelSummaryDto>>(body)
                dtos.map { dto -> dto.toSummary() }
            }
        }
    }

    suspend fun getModelDetail(
        rawRepoId: String,
        token: String = ""
    ): Result<HfRepoDetail> = withContext(Dispatchers.IO) {
        val repoId = SecureCredentialStore.normalizeAndValidateRepoId(rawRepoId)
            .getOrElse { return@withContext Result.failure(it) }

        runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/models/$repoId?blobs=true"
            val req = buildRequest(url, token)
            val detail = httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    throw IOException(mapHttpError(res.code, "Repository lookup failed"))
                }
                val body = res.body?.string() ?: throw IOException("Empty response from Hugging Face.")
                val dto = json.decodeFromString<HfModelDetailDto>(body)
                dto.toDetail(baseUrl.trimEnd('/'))
            }
            val readme = fetchModelCardReadme(repoId, token).getOrNull()
            detail.copy(modelCardMarkdown = readme)
        }
    }

    suspend fun fetchModelCardReadme(
        rawRepoId: String,
        token: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val repoId = SecureCredentialStore.normalizeAndValidateRepoId(rawRepoId)
            .getOrElse { return@withContext Result.failure(it) }

        runCatching {
            val url = "${baseUrl.trimEnd('/')}/$repoId/resolve/main/README.md"
            val req = buildRequest(url, token)
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    throw IOException(mapHttpError(res.code, "Model card unavailable"))
                }
                val rawMd = res.body?.string().orEmpty()
                HfMetadataParser.stripYamlFrontmatter(rawMd).take(8000)
            }
        }
    }

    private fun buildRequest(url: String, token: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "JarvisAndroidModelHub/1.0")
            .get()
        // Never log or expose Authorization token; attach only for HTTPS HF hosts (or local mock test URL)
        val trimmedToken = token.trim()
        if (trimmedToken.isNotEmpty() && isTrustedAuthHost(url)) {
            builder.header("Authorization", "Bearer $trimmedToken")
        }
        return builder.build()
    }

    private fun isTrustedAuthHost(url: String): Boolean {
        val parsed = runCatching { url.toHttpUrl() }.getOrNull() ?: return false
        val host = parsed.host.lowercase()
        val isTrustedHfDomain = parsed.isHttps && (
            host == "huggingface.co" ||
                host.endsWith(".huggingface.co") ||
                host.endsWith(".hf.co")
            )
        val isLoopback = host == "localhost" || host == "127.0.0.1"
        return isTrustedHfDomain || isLoopback
    }

    private fun mapHttpError(code: Int, prefix: String): String = when (code) {
        401, 403 -> "$prefix (HTTP $code): Access restricted or gated model. Accept the license on Hugging Face and configure a valid Read token."
        404 -> "$prefix (HTTP 404): Repository or file not found."
        429 -> "$prefix (HTTP 429): Hugging Face rate limit reached. Try again shortly or add a Read token."
        else -> "$prefix (HTTP $code)."
    }

    internal fun HfModelSummaryDto.toSummary(): HfRepoSummary {
        val (gatedBool, gatedMode) = HfMetadataParser.parseGated(gated)
        val parts = id.split('/')
        val resolvedAuthor = author ?: parts.firstOrNull().orEmpty()
        val resolvedName = parts.getOrNull(1) ?: id
        return HfRepoSummary(
            repoId = id,
            author = resolvedAuthor,
            modelName = resolvedName,
            downloads = downloads,
            likes = likes,
            isPrivate = private,
            isGated = gatedBool,
            gatedMode = gatedMode,
            pipelineTag = pipelineTag,
            libraryName = libraryName,
            license = HfMetadataParser.extractLicense(null, tags),
            tags = tags,
            siblingFilenames = siblings.map { it.rfilename },
            lastModified = lastModified
        )
    }

    internal fun HfModelDetailDto.toDetail(resolvedBaseUrl: String): HfRepoDetail {
        val (gatedBool, gatedMode) = HfMetadataParser.parseGated(gated)
        val parts = id.split('/')
        val resolvedAuthor = author ?: parts.firstOrNull().orEmpty()
        val resolvedName = parts.getOrNull(1) ?: id
        val fileNames = siblings.map { it.rfilename }

        val mappedFiles = siblings
            .filter { SecureCredentialStore.isSafeModelFilename(it.rfilename) }
            .map { sib ->
                val size = sib.lfs?.size ?: sib.size ?: 0L
                val encodedPath = sib.rfilename.split('/').joinToString("/") { seg ->
                    java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
                }
                HfRepoFile(
                    fileName = sib.rfilename,
                    sizeBytes = size,
                    sha256 = sib.lfs?.sha256,
                    downloadUrl = "$resolvedBaseUrl/$id/resolve/main/$encodedPath"
                )
            }

        return HfRepoDetail(
            repoId = id,
            author = resolvedAuthor,
            modelName = resolvedName,
            downloads = downloads,
            likes = likes,
            isPrivate = private,
            isGated = gatedBool,
            gatedMode = gatedMode,
            gatedPrompt = cardData?.extraGatedPrompt,
            license = HfMetadataParser.extractLicense(cardData?.license, tags),
            architecture = HfMetadataParser.extractArchitecture(config, gguf, tags, id),
            parameterCount = HfMetadataParser.extractParameterCount(safetensors, gguf, id, fileNames),
            quantizationSummary = HfMetadataParser.extractQuantizationSummary(fileNames, tags),
            contextLengthHint = gguf?.contextLength,
            supportedTasks = HfMetadataParser.extractSupportedTasks(
                pipelineTag ?: cardData?.pipelineTag,
                tags + (cardData?.tags ?: emptyList())
            ),
            libraryName = libraryName ?: cardData?.libraryName,
            baseModel = HfMetadataParser.parseJsonStringOrList(cardData?.baseModel),
            tags = tags,
            files = mappedFiles
        )
    }

    companion object {
        const val HF_BASE_URL = "https://huggingface.co"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
