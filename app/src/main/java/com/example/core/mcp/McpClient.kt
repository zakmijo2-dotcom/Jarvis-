package com.example.core.mcp

import com.example.core.cloud.OkHttpCloudAiClient
import com.example.core.security.HttpSecuritySupport
import com.example.core.security.PromptSecuritySanitizer
import com.example.core.security.SecurityLogRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Clean-room MCP (Model Context Protocol) JSON-RPC 2.0 client over Streamable HTTP / SSE.
 * Inspired by Juyu McpClient and Zafiro McpServiceImpl.
 */
class McpClient(
    private val baseClient: OkHttpClient = OkHttpClient()
) {
    private val nextRequestId = AtomicInteger(1)

    suspend fun initialize(
        endpointUrl: String,
        bearerToken: String = ""
    ): Result<McpInitResult> = withContext(Dispatchers.IO) {
        if (!OkHttpCloudAiClient.isSafeEndpointUrl(endpointUrl)) {
            return@withContext Result.failure(
                SecurityException("MCP endpoint must be HTTPS (or localhost for local development).")
            )
        }
        runCatching {
            val initParams = JSONObject().apply {
                put("protocolVersion", "2025-03-26")
                put("capabilities", JSONObject())
                put(
                    "clientInfo",
                    JSONObject().apply {
                        put("name", "JARVIS-Android")
                        put("version", "1.0.1")
                    }
                )
            }
            val (json, headerSessionId) = postRpc(
                endpointUrl = endpointUrl,
                method = "initialize",
                params = initParams,
                bearerToken = bearerToken,
                sessionId = null
            )
            val resultObj = json.optJSONObject("result")
                ?: throw IllegalStateException("MCP initialize returned no result object.")
            val serverName = resultObj.optJSONObject("serverInfo")?.optString("name")
                ?.ifBlank { "MCP Server" } ?: "MCP Server"
            val protocol = resultObj.optString("protocolVersion").ifBlank { "2025-03-26" }

            // Fire notifications/initialized cleanly per MCP spec
            runCatching {
                postNotification(
                    endpointUrl = endpointUrl,
                    method = "notifications/initialized",
                    bearerToken = bearerToken,
                    sessionId = headerSessionId
                )
            }

            McpInitResult(
                protocolVersion = protocol,
                serverName = serverName,
                sessionId = headerSessionId
            )
        }
    }

    suspend fun listTools(
        serverId: String,
        serverName: String,
        endpointUrl: String,
        bearerToken: String = "",
        sessionId: String? = null
    ): Result<Pair<List<McpToolSpec>, String?>> = withContext(Dispatchers.IO) {
        if (!OkHttpCloudAiClient.isSafeEndpointUrl(endpointUrl)) {
            return@withContext Result.failure(SecurityException("Unsafe MCP endpoint URL."))
        }
        runCatching {
            val (json, nextSession) = postRpc(
                endpointUrl = endpointUrl,
                method = "tools/list",
                params = JSONObject(),
                bearerToken = bearerToken,
                sessionId = sessionId
            )
            val toolsArr = json.optJSONObject("result")?.optJSONArray("tools") ?: JSONArray()
            val list = (0 until toolsArr.length()).mapNotNull { i ->
                val obj = toolsArr.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                if (name.isEmpty()) return@mapNotNull null
                val desc = obj.optString("description").trim()
                val schema = obj.optJSONObject("inputSchema")?.toString() ?: "{}"
                McpToolSpec(
                    serverId = serverId,
                    serverName = serverName,
                    name = name,
                    description = desc,
                    inputSchemaJson = schema,
                    isPotentiallyMutating = isWriteLikeToolName(name, desc)
                )
            }
            list to (nextSession ?: sessionId)
        }
    }

    suspend fun callTool(
        endpointUrl: String,
        toolName: String,
        arguments: JSONObject,
        bearerToken: String = "",
        sessionId: String? = null
    ): Result<McpToolCallOutput> = withContext(Dispatchers.IO) {
        if (!OkHttpCloudAiClient.isSafeEndpointUrl(endpointUrl)) {
            return@withContext Result.failure(SecurityException("Unsafe MCP endpoint URL."))
        }
        runCatching {
            val params = JSONObject().apply {
                put("name", toolName)
                put("arguments", arguments)
            }
            val (json, _) = postRpc(
                endpointUrl = endpointUrl,
                method = "tools/call",
                params = params,
                bearerToken = bearerToken,
                sessionId = sessionId
            )
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                throw IllegalStateException("MCP RPC error ${errorObj.optInt("code")}: ${errorObj.optString("message")}")
            }
            val result = json.optJSONObject("result") ?: JSONObject()
            val isErr = result.optBoolean("isError", false)
            val contentArr = result.optJSONArray("content")
            val textParts = mutableListOf<String>()
            if (contentArr != null) {
                for (i in 0 until contentArr.length()) {
                    val item = contentArr.optJSONObject(i) ?: continue
                    val txt = item.optString("text")
                    if (txt.isNotBlank()) textParts.add(txt)
                }
            }
            val merged = textParts.joinToString("\n").ifBlank { result.toString() }
            McpToolCallOutput(
                toolName = toolName,
                textContent = PromptSecuritySanitizer.sanitizeExternalText(merged, maxChars = 3500),
                isError = isErr,
                rawJson = result
            )
        }
    }

    private fun postRpc(
        endpointUrl: String,
        method: String,
        params: JSONObject,
        bearerToken: String,
        sessionId: String?
    ): Pair<JSONObject, String?> {
        val rpcId = nextRequestId.getAndIncrement()
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", rpcId)
            put("method", method)
            put("params", params)
        }
        val client = baseClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
        if (bearerToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${bearerToken.trim()}")
        }
        if (!sessionId.isNullOrBlank()) {
            reqBuilder.header("Mcp-Session-Id", sessionId)
        }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("MCP HTTP ${resp.code}: ${SecurityLogRedactor.redact(resp.message)}")
            }
            val returnedSessionId = resp.header("Mcp-Session-Id")?.takeIf { it.isNotBlank() } ?: sessionId
            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            val rawBody = HttpSecuritySupport.readBoundedUtf8Body(resp.body)
            val parsedJson = if (contentType.contains("text/event-stream")) {
                extractLastJsonFromSse(rawBody, rpcId)
            } else {
                JSONObject(rawBody)
            }
            return parsedJson to returnedSessionId
        }
    }

    private fun postNotification(
        endpointUrl: String,
        method: String,
        bearerToken: String,
        sessionId: String?
    ) {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
        }
        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
        if (bearerToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${bearerToken.trim()}")
        }
        if (!sessionId.isNullOrBlank()) {
            reqBuilder.header("Mcp-Session-Id", sessionId)
        }
        baseClient.newCall(reqBuilder.build()).execute().close()
    }

    internal fun extractLastJsonFromSse(rawSse: String, expectedId: Int): JSONObject {
        var lastJson: JSONObject? = null
        rawSse.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("data:")) {
                val payload = trimmed.removePrefix("data:").trim()
                if (payload.startsWith("{")) {
                    val obj = runCatching { JSONObject(payload) }.getOrNull()
                    if (obj != null) {
                        if (obj.optInt("id", -1) == expectedId) {
                            return obj
                        }
                        lastJson = obj
                    }
                }
            }
        }
        return lastJson ?: throw IllegalStateException("MCP SSE stream returned no JSON-RPC payload.")
    }

    private fun isWriteLikeToolName(name: String, description: String): Boolean {
        val s = "$name $description".lowercase()
        return WRITE_KEYWORDS.any { kw -> s.contains(kw) }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val WRITE_KEYWORDS = listOf(
            "write", "delete", "remove", "update", "create", "post", "send",
            "execute", "run", "modify", "edit", "insert", "drop", "commit", "push"
        )
    }
}
