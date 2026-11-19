package com.example.core.cloud

import com.example.core.ai.AiStreamEvent
import com.example.core.ai.GenerationBenchmark
import com.example.core.ai.PromptCompressor
import com.example.core.security.HttpSecuritySupport
import com.example.core.security.SecurityLogRedactor
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OkHttpCloudAiClient(
    private val baseClient: OkHttpClient = OkHttpClient()
) : CloudAiClient {

    private val _usageStats = MutableStateFlow(CloudUsageStats())
    override val usageStats: StateFlow<CloudUsageStats> = _usageStats.asStateFlow()

    @Volatile
    private var activeCall: Call? = null
    private val cancelRequested = AtomicBoolean(false)

    override fun cancelActiveRequest() {
        cancelRequested.set(true)
        activeCall?.cancel()
    }

    override fun resetUsageStats() {
        _usageStats.value = CloudUsageStats()
    }

    override fun streamChat(
        config: CloudInferenceConfig,
        systemInstruction: String,
        prompt: String,
        history: List<MessageEntity>
    ): Flow<AiStreamEvent> = flow {
        cancelRequested.set(false)
        val trimmedKey = config.apiKey.trim()
        if (trimmedKey.isEmpty()) {
            val msg = "No API key configured for ${config.providerKind.label}. Add your personal API key in Settings."
            recordFailure(config.providerKind.label, msg, 0L)
            emit(AiStreamEvent.Error(msg))
            return@flow
        }

        val resolvedEndpoint = config.endpointUrl.trim().ifBlank { config.providerKind.defaultEndpoint }
        val resolvedModel = config.modelId.trim().ifBlank { config.providerKind.defaultModel }
        if (!isSafeEndpointUrl(resolvedEndpoint)) {
            val msg = "Invalid or insecure endpoint URL '$resolvedEndpoint'. HTTPS required (localhost permitted for local test gateways)."
            recordFailure(config.providerKind.label, msg, 0L)
            emit(AiStreamEvent.Error(msg))
            return@flow
        }

        val timeoutSec = config.timeoutSeconds.coerceIn(5, 120).toLong()
        val client = baseClient.newBuilder()
            .connectTimeout(timeoutSec.coerceAtMost(20L), TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .writeTimeout(timeoutSec, TimeUnit.SECONDS)
            .callTimeout(timeoutSec + 10L, TimeUnit.SECONDS)
            .build()

        val request = buildHttpRequest(
            config = config.copy(endpointUrl = resolvedEndpoint, modelId = resolvedModel, apiKey = trimmedKey),
            systemInstruction = systemInstruction,
            prompt = prompt,
            history = history
        )

        val backendLabel = "Cloud (${config.providerKind.label}: $resolvedModel)"
        val prefillEst = estimateTokens(systemInstruction) + history.takeLast(12).sumOf { estimateTokens(it.content) } + estimateTokens(prompt)
        _usageStats.value = _usageStats.value.copy(
            totalRequests = _usageStats.value.totalRequests + 1,
            totalPromptTokensEst = _usageStats.value.totalPromptTokensEst + prefillEst,
            lastProviderUsed = backendLabel
        )

        emit(AiStreamEvent.Started(backendLabel))

        val startNs = System.nanoTime()
        var firstTokenNs: Long? = null
        val accumulated = StringBuilder()

        val maxAttempts = (config.maxRetries.coerceIn(0, 3)) + 1
        var lastErrorMsg: String? = null
        var succeeded = false

        for (attempt in 1..maxAttempts) {
            if (cancelRequested.get()) {
                emit(AiStreamEvent.Cancelled(accumulated.toString(), null))
                return@flow
            }
            val call = client.newCall(request)
            activeCall = call
            var shouldRetry = false

            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = runCatching {
                            HttpSecuritySupport.readBoundedUtf8Body(response.body, 256L * 1024L)
                        }.getOrDefault("")
                        val cleanErr = SecurityLogRedactor.redact(extractApiError(response.code, response.message, errorBody))
                        lastErrorMsg = cleanErr
                        if (isRetryableHttpCode(response.code) && attempt < maxAttempts && accumulated.isEmpty()) {
                            shouldRetry = true
                        } else {
                            val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                            recordFailure(backendLabel, cleanErr, latencyMs)
                        }
                        return@use
                    }

                    val body = response.body
                    if (body == null) {
                        lastErrorMsg = "Cloud provider returned an empty response body."
                        val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                        recordFailure(backendLabel, lastErrorMsg!!, latencyMs)
                        return@use
                    }

                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    if (contentType.contains("application/json") && !contentType.contains("event-stream")) {
                        val rawJson = HttpSecuritySupport.readBoundedUtf8Body(body)
                        val fullText = parseNonStreamingJson(config.providerKind, rawJson)
                        firstTokenNs = System.nanoTime()
                        accumulated.append(fullText)
                        val outTok = estimateTokens(fullText)
                        emit(AiStreamEvent.Delta(fullText, fullText, outTok))
                    } else {
                        val toolCallNames = linkedMapOf<Int, String>()
                        val toolCallArgs = linkedMapOf<Int, StringBuilder>()
                        BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                            while (true) {
                                if (cancelRequested.get()) break
                                val line = reader.readLine() ?: break
                                if (!line.startsWith("data:")) continue
                                val data = line.removePrefix("data:").trim()
                                if (data.isEmpty() || data == "[DONE]") {
                                    if (data == "[DONE]") break
                                    continue
                                }
                                accumulateStreamingToolCalls(
                                    provider = config.providerKind,
                                    jsonData = data,
                                    namesByIndex = toolCallNames,
                                    argsByIndex = toolCallArgs
                                )
                                val delta = extractSseDelta(config.providerKind, data, includeOpenAiToolCalls = false)
                                if (!delta.isNullOrEmpty()) {
                                    if (firstTokenNs == null) {
                                        firstTokenNs = System.nanoTime()
                                    }
                                    accumulated.append(delta)
                                    val currentText = accumulated.toString()
                                    emit(
                                        AiStreamEvent.Delta(
                                            accumulatedText = currentText,
                                            deltaText = delta,
                                            estimatedTokens = estimateTokens(currentText)
                                        )
                                    )
                                }
                            }
                        }
                        if (toolCallNames.isNotEmpty() && !accumulated.contains("[TOOL_CALL]")) {
                            for ((idx, fnName) in toolCallNames) {
                                if (fnName.isBlank()) continue
                                val rawArgs = toolCallArgs[idx]?.toString().orEmpty().trim().ifEmpty { "{}" }
                                val tag = formatCanonicalToolCallTag(fnName, rawArgs)
                                if (firstTokenNs == null) {
                                    firstTokenNs = System.nanoTime()
                                }
                                if (accumulated.isNotEmpty() && !accumulated.endsWith("\n")) {
                                    accumulated.append("\n")
                                }
                                accumulated.append(tag)
                                val currentText = accumulated.toString()
                                emit(
                                    AiStreamEvent.Delta(
                                        accumulatedText = currentText,
                                        deltaText = tag,
                                        estimatedTokens = estimateTokens(currentText)
                                    )
                                )
                            }
                        }
                    }
                    succeeded = true
                }
            } catch (ce: CancellationException) {
                emit(AiStreamEvent.Cancelled(accumulated.toString(), null))
                throw ce
            } catch (te: SocketTimeoutException) {
                lastErrorMsg = SecurityLogRedactor.redact("Cloud request timed out after ${timeoutSec}s (${te.localizedMessage ?: "timeout"}).")
                if (attempt < maxAttempts && accumulated.isEmpty()) {
                    shouldRetry = true
                } else {
                    val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                    recordFailure(backendLabel, lastErrorMsg!!, latencyMs)
                }
            } catch (io: IOException) {
                if (cancelRequested.get()) {
                    emit(AiStreamEvent.Cancelled(accumulated.toString(), null))
                    return@flow
                }
                lastErrorMsg = SecurityLogRedactor.redact("Cloud network failure: ${io.localizedMessage ?: io.javaClass.simpleName}")
                if (attempt < maxAttempts && accumulated.isEmpty()) {
                    shouldRetry = true
                } else {
                    val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                    recordFailure(backendLabel, lastErrorMsg!!, latencyMs)
                }
            } catch (t: Throwable) {
                val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                lastErrorMsg = SecurityLogRedactor.redact("Cloud inference error: ${t.localizedMessage ?: t.javaClass.simpleName}")
                recordFailure(backendLabel, lastErrorMsg!!, latencyMs)
            } finally {
                if (activeCall === call) {
                    activeCall = null
                }
            }

            if (succeeded) break
            if (shouldRetry) {
                val backoffMs = (config.retryBaseDelayMs.coerceIn(50L, 2_000L)) * (1L shl (attempt - 1))
                delay(backoffMs)
            } else {
                break
            }
        }

        if (!succeeded) {
            val fallback = config.fallbackConfig
            if (fallback != null && !cancelRequested.get()) {
                streamChat(
                    config = fallback.copy(fallbackConfig = null),
                    systemInstruction = systemInstruction,
                    prompt = prompt,
                    history = history
                ).collect { ev -> emit(ev) }
                return@flow
            }
            emit(AiStreamEvent.Error(lastErrorMsg ?: "Cloud provider request failed."))
            return@flow
        }

        val endNs = System.nanoTime()
        val totalLatencyMs = ((endNs - startNs) / 1_000_000L).coerceAtLeast(1L)
        val ttftMs = (((firstTokenNs ?: endNs) - startNs) / 1_000_000L).coerceAtLeast(1L)
        val finalText = accumulated.toString()
        val outTokens = estimateTokens(finalText)
        val decodeSec = ((endNs - (firstTokenNs ?: startNs)).coerceAtLeast(1_000_000L)) / 1_000_000_000f
        val tps = (outTokens / decodeSec).coerceAtLeast(0.1f)

        val benchmark = GenerationBenchmark(
            ttftMs = ttftMs,
            tokensPerSecond = tps,
            outputTokens = outTokens,
            prefillTokens = prefillEst,
            backendUsed = backendLabel,
            nativeHeapMb = 0f
        )

        if (cancelRequested.get()) {
            emit(AiStreamEvent.Cancelled(finalText, benchmark))
        } else {
            _usageStats.value = _usageStats.value.copy(
                successfulRequests = _usageStats.value.successfulRequests + 1,
                totalCompletionTokensEst = _usageStats.value.totalCompletionTokensEst + outTokens,
                lastLatencyMs = totalLatencyMs,
                lastProviderUsed = backendLabel,
                lastErrorMessage = null
            )
            emit(AiStreamEvent.Completed(finalText, benchmark))
        }
    }.flowOn(Dispatchers.IO)

    internal fun isRetryableHttpCode(code: Int): Boolean =
        code == 429 || code == 500 || code == 502 || code == 503 || code == 504

    private fun recordFailure(provider: String, message: String, latencyMs: Long) {
        _usageStats.value = _usageStats.value.copy(
            failedRequests = _usageStats.value.failedRequests + 1,
            lastLatencyMs = latencyMs,
            lastProviderUsed = provider,
            lastErrorMessage = message
        )
    }

    internal fun buildHttpRequest(
        config: CloudInferenceConfig,
        systemInstruction: String,
        prompt: String,
        history: List<MessageEntity>
    ): Request {
        val recentHistory = PromptCompressor.compressHistory(
            history = history,
            contextBudgetTokens = 3072
        ).turns

        return when (config.providerKind) {
            CloudProviderKind.GEMINI_REST -> {
                val base = config.endpointUrl.trimEnd('/')
                val url = if (base.contains(":streamGenerateContent")) {
                    if (base.contains("alt=sse")) base else if ('?' in base) "$base&alt=sse" else "$base?alt=sse"
                } else {
                    "$base/models/${config.modelId}:streamGenerateContent?alt=sse"
                }
                val contents = JSONArray()
                for (msg in recentHistory) {
                    val role = if (msg.isUser) "user" else "model"
                    contents.put(
                        JSONObject().apply {
                            put("role", role)
                            put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                        }
                    )
                }
                val userParts = JSONArray().apply {
                    put(JSONObject().put("text", prompt))
                    config.attachments.take(3).forEach { att ->
                        if (att.base64Data.isNotBlank()) {
                            put(
                                JSONObject().put(
                                    "inlineData",
                                    JSONObject().apply {
                                        put("mimeType", att.mimeType)
                                        put("data", att.base64Data)
                                    }
                                )
                            )
                        }
                    }
                }
                contents.put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", userParts)
                    }
                )
                val payload = JSONObject().apply {
                    if (systemInstruction.isNotBlank()) {
                        put(
                            "systemInstruction",
                            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                        )
                    }
                    put("contents", contents)
                    if (!config.toolsSchemaJson.isNullOrBlank()) {
                        runCatching { JSONArray(config.toolsSchemaJson) }.getOrNull()?.let { toolsArr ->
                            if (toolsArr.length() > 0) {
                                put("tools", toolsArr)
                            }
                        }
                    }
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("temperature", config.temperature.toDouble())
                            put("maxOutputTokens", config.maxOutputTokens)
                        }
                    )
                }
                Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("x-goog-api-key", config.apiKey)
                    .header("Accept", "text/event-stream")
                    .build()
            }

            CloudProviderKind.ANTHROPIC -> {
                val messages = JSONArray()
                for (msg in recentHistory) {
                    messages.put(
                        JSONObject().apply {
                            put("role", if (msg.isUser) "user" else "assistant")
                            put("content", msg.content)
                        }
                    )
                }
                val validAtts = config.attachments.filter { it.base64Data.isNotBlank() }.take(3)
                if (validAtts.isEmpty()) {
                    messages.put(JSONObject().put("role", "user").put("content", prompt))
                } else {
                    val contentArr = JSONArray().apply {
                        validAtts.forEach { att ->
                            put(
                                JSONObject().apply {
                                    put("type", "image")
                                    put(
                                        "source",
                                        JSONObject().apply {
                                            put("type", "base64")
                                            put("media_type", att.mimeType)
                                            put("data", att.base64Data)
                                        }
                                    )
                                }
                            )
                        }
                        put(JSONObject().put("type", "text").put("text", prompt))
                    }
                    messages.put(JSONObject().put("role", "user").put("content", contentArr))
                }
                val payload = JSONObject().apply {
                    put("model", config.modelId)
                    put("max_tokens", config.maxOutputTokens)
                    put("temperature", config.temperature.toDouble())
                    put("stream", true)
                    if (systemInstruction.isNotBlank()) {
                        put("system", systemInstruction)
                    }
                    put("messages", messages)
                    if (!config.toolsSchemaJson.isNullOrBlank()) {
                        runCatching { JSONArray(config.toolsSchemaJson) }.getOrNull()?.let { toolsArr ->
                            if (toolsArr.length() > 0) {
                                put("tools", toolsArr)
                                put("tool_choice", JSONObject().put("type", "auto"))
                            }
                        }
                    }
                }
                Request.Builder()
                    .url(config.endpointUrl)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("x-api-key", config.apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Accept", "text/event-stream")
                    .build()
            }

            CloudProviderKind.OPENAI_COMPATIBLE,
            CloudProviderKind.OPENROUTER,
            CloudProviderKind.CUSTOM_ENDPOINT -> {
                val messages = JSONArray()
                if (systemInstruction.isNotBlank()) {
                    messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
                }
                for (msg in recentHistory) {
                    messages.put(
                        JSONObject().apply {
                            put("role", if (msg.isUser) "user" else "assistant")
                            put("content", msg.content)
                        }
                    )
                }
                val validAtts = config.attachments.filter { it.base64Data.isNotBlank() }.take(3)
                if (validAtts.isEmpty()) {
                    messages.put(JSONObject().put("role", "user").put("content", prompt))
                } else {
                    val contentArr = JSONArray().apply {
                        put(JSONObject().put("type", "text").put("text", prompt))
                        validAtts.forEach { att ->
                            put(
                                JSONObject().apply {
                                    put("type", "image_url")
                                    put(
                                        "image_url",
                                        JSONObject().put("url", "data:${att.mimeType};base64,${att.base64Data}")
                                    )
                                }
                            )
                        }
                    }
                    messages.put(JSONObject().put("role", "user").put("content", contentArr))
                }
                val payload = JSONObject().apply {
                    put("model", config.modelId)
                    put("messages", messages)
                    put("stream", true)
                    put("temperature", config.temperature.toDouble())
                    put("max_tokens", config.maxOutputTokens)
                    if (!config.toolsSchemaJson.isNullOrBlank()) {
                        runCatching { JSONArray(config.toolsSchemaJson) }.getOrNull()?.let { toolsArr ->
                            if (toolsArr.length() > 0) {
                                put("tools", toolsArr)
                                put("tool_choice", "auto")
                            }
                        }
                    }
                }
                Request.Builder()
                    .url(config.endpointUrl)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("Authorization", "Bearer ${config.apiKey}")
                    .header("Accept", "text/event-stream")
                    .build()
            }
        }
    }

    internal fun extractSseDelta(
        provider: CloudProviderKind,
        jsonData: String,
        includeOpenAiToolCalls: Boolean = true
    ): String? {
        val obj = runCatching { JSONObject(jsonData) }.getOrNull() ?: return null
        return when (provider) {
            CloudProviderKind.GEMINI_REST -> {
                val candidates = obj.optJSONArray("candidates") ?: return null
                val first = candidates.optJSONObject(0) ?: return null
                val parts = first.optJSONObject("content")?.optJSONArray("parts") ?: return null
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val text = part.optString("text")
                    if (text.isNotEmpty()) sb.append(text)
                    val fnCall = part.optJSONObject("functionCall")
                    if (fnCall != null) {
                        val fnName = fnCall.optString("name").trim()
                        if (fnName.isNotEmpty()) {
                            val argsObj = fnCall.optJSONObject("args") ?: JSONObject()
                            sb.append(formatCanonicalToolCallTag(fnName, argsObj.toString()))
                        }
                    }
                }
                sb.toString().takeIf { it.isNotEmpty() }
            }

            CloudProviderKind.ANTHROPIC -> {
                if (obj.optString("type") == "content_block_delta") {
                    obj.optJSONObject("delta")?.optString("text")?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }

            else -> {
                val choices = obj.optJSONArray("choices") ?: return null
                val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return null
                val content = delta.optString("content").takeIf { it.isNotEmpty() && it != "null" }
                if (!includeOpenAiToolCalls) {
                    return content
                }
                val toolCalls = delta.optJSONArray("tool_calls")
                val fnTag = if (toolCalls != null && toolCalls.length() > 0) {
                    val fn = toolCalls.optJSONObject(0)?.optJSONObject("function")
                    val fnName = fn?.optString("name").orEmpty().trim()
                    val fnArgs = fn?.optString("arguments").orEmpty().trim()
                    if (fnName.isNotEmpty() && (fnArgs.isEmpty() || fnArgs.startsWith("{"))) {
                        formatCanonicalToolCallTag(fnName, fnArgs.ifEmpty { "{}" })
                    } else null
                } else null
                when {
                    content != null && fnTag != null -> "$content\n$fnTag"
                    content != null -> content
                    else -> fnTag
                }
            }
        }
    }

    private fun accumulateStreamingToolCalls(
        provider: CloudProviderKind,
        jsonData: String,
        namesByIndex: MutableMap<Int, String>,
        argsByIndex: MutableMap<Int, StringBuilder>
    ) {
        if (provider == CloudProviderKind.GEMINI_REST) return
        val obj = runCatching { JSONObject(jsonData) }.getOrNull() ?: return

        if (provider == CloudProviderKind.ANTHROPIC) {
            val type = obj.optString("type")
            val idx = obj.optInt("index", 0)
            if (type == "content_block_start") {
                val block = obj.optJSONObject("content_block") ?: return
                if (block.optString("type") == "tool_use") {
                    val fnName = block.optString("name").trim()
                    if (fnName.isNotEmpty()) {
                        namesByIndex[idx] = fnName
                    }
                    val initialInput = block.optJSONObject("input")
                    if (initialInput != null && initialInput.length() > 0) {
                        argsByIndex.getOrPut(idx) { StringBuilder() }.append(initialInput.toString())
                    }
                }
            } else if (type == "content_block_delta") {
                val delta = obj.optJSONObject("delta") ?: return
                if (delta.optString("type") == "input_json_delta") {
                    val partial = delta.optString("partial_json")
                    if (partial.isNotEmpty()) {
                        argsByIndex.getOrPut(idx) { StringBuilder() }.append(partial)
                    }
                }
            }
            return
        }

        val delta = obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: return
        val toolCalls = delta.optJSONArray("tool_calls")
        if (toolCalls != null) {
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.optJSONObject(i) ?: continue
                val idx = tc.optInt("index", i)
                val fn = tc.optJSONObject("function") ?: continue
                val namePart = fn.optString("name")
                if (namePart.isNotBlank() && namePart != "null") {
                    namesByIndex[idx] = (namesByIndex[idx].orEmpty() + namePart).trim()
                }
                val argPart = fn.optString("arguments")
                if (argPart.isNotEmpty() && argPart != "null") {
                    argsByIndex.getOrPut(idx) { StringBuilder() }.append(argPart)
                }
            }
        }
        val legacyFn = delta.optJSONObject("function_call")
        if (legacyFn != null) {
            val namePart = legacyFn.optString("name")
            if (namePart.isNotBlank() && namePart != "null") {
                namesByIndex[0] = (namesByIndex[0].orEmpty() + namePart).trim()
            }
            val argPart = legacyFn.optString("arguments")
            if (argPart.isNotEmpty() && argPart != "null") {
                argsByIndex.getOrPut(0) { StringBuilder() }.append(argPart)
            }
        }
    }

    internal fun parseNonStreamingJson(provider: CloudProviderKind, rawJson: String): String {
        val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return rawJson
        return when (provider) {
            CloudProviderKind.GEMINI_REST -> {
                val parts = obj.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                if (parts == null) return rawJson
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val text = part.optString("text")
                    if (text.isNotEmpty()) sb.append(text)
                    val fnCall = part.optJSONObject("functionCall")
                    if (fnCall != null) {
                        val fnName = fnCall.optString("name").trim()
                        if (fnName.isNotEmpty()) {
                            val argsObj = fnCall.optJSONObject("args") ?: JSONObject()
                            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                            sb.append(formatCanonicalToolCallTag(fnName, argsObj.toString()))
                        }
                    }
                }
                sb.toString()
            }

            CloudProviderKind.ANTHROPIC -> {
                val contentArr = obj.optJSONArray("content") ?: return ""
                val sb = StringBuilder()
                for (i in 0 until contentArr.length()) {
                    val block = contentArr.optJSONObject(i) ?: continue
                    when (block.optString("type")) {
                        "text" -> {
                            val txt = block.optString("text")
                            if (txt.isNotEmpty()) sb.append(txt)
                        }
                        "tool_use" -> {
                            val fnName = block.optString("name").trim()
                            if (fnName.isNotEmpty()) {
                                val inputObj = block.optJSONObject("input") ?: JSONObject()
                                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                                sb.append(formatCanonicalToolCallTag(fnName, inputObj.toString()))
                            }
                        }
                    }
                }
                sb.toString()
            }

            else -> {
                val msg = obj.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?: return rawJson
                val sb = StringBuilder()
                val content = msg.optString("content")
                if (content.isNotEmpty() && content != "null") {
                    sb.append(content)
                }
                val toolCalls = msg.optJSONArray("tool_calls")
                if (toolCalls != null) {
                    for (i in 0 until toolCalls.length()) {
                        val fn = toolCalls.optJSONObject(i)?.optJSONObject("function") ?: continue
                        val fnName = fn.optString("name").trim()
                        val fnArgs = fn.optString("arguments").trim().ifEmpty { "{}" }
                        if (fnName.isNotEmpty()) {
                            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                            sb.append(formatCanonicalToolCallTag(fnName, fnArgs))
                        }
                    }
                }
                val legacyFn = msg.optJSONObject("function_call")
                if (legacyFn != null && toolCalls == null) {
                    val fnName = legacyFn.optString("name").trim()
                    val fnArgs = legacyFn.optString("arguments").trim().ifEmpty { "{}" }
                    if (fnName.isNotEmpty()) {
                        if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                        sb.append(formatCanonicalToolCallTag(fnName, fnArgs))
                    }
                }
                sb.toString()
            }
        }.ifBlank { rawJson }
    }

    private fun formatCanonicalToolCallTag(toolName: String, rawArgsJson: String): String {
        val parsedArgs = runCatching { JSONObject(rawArgsJson) }.getOrDefault(JSONObject())
        val wrapper = JSONObject().apply {
            put("tool", toolName.trim().lowercase())
            put("args", parsedArgs)
        }
        return "[TOOL_CALL]${wrapper}[/TOOL_CALL]"
    }

    private fun extractApiError(code: Int, statusMessage: String, body: String): String {
        val parsed = runCatching {
            val obj = JSONObject(body)
            obj.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        return if (parsed != null) {
            "Cloud HTTP $code: $parsed"
        } else {
            "Cloud HTTP $code ($statusMessage)"
        }
    }

    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return ((text.length + 3) / 4).coerceAtLeast(1)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun isSafeEndpointUrl(url: String): Boolean {
            val parsed = runCatching { URI(url.trim()) }.getOrNull() ?: return false
            if (!parsed.userInfo.isNullOrEmpty()) return false
            val scheme = parsed.scheme?.lowercase() ?: return false
            val host = parsed.host?.lowercase()?.trim('[', ']') ?: return false
            if (host.isBlank()) return false
            if (host == "169.254.169.254" ||
                host == "metadata.google.internal" ||
                host.startsWith("169.254.") ||
                host == "0.0.0.0" ||
                host == "::"
            ) {
                return false
            }
            if (scheme == "https") return true
            return scheme == "http" && (host == "127.0.0.1" || host == "localhost" || host == "::1")
        }
    }
}
