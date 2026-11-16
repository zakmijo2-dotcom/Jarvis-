package com.example.core.mcp

import android.content.Context
import com.example.core.security.SecureCredentialStore
import com.example.core.security.SecurityLogRedactor
import com.example.data.local.db.McpServerDao
import com.example.data.local.db.McpServerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Coordinates registered MCP servers in Room + encrypted Keystore bearer tokens
 * and gates tool discovery/invocation safely.
 */
class McpManager(
    private val context: Context,
    private val mcpServerDao: McpServerDao,
    private val mcpClient: McpClient,
    private val credentialStore: SecureCredentialStore
) {

    fun observeServers(): Flow<List<McpServerConfig>> =
        mcpServerDao.observeServers().map { rows -> rows.map(::toModel) }

    suspend fun listServersOnce(): List<McpServerConfig> =
        mcpServerDao.getAllOnce().map(::toModel)

    suspend fun addOrUpdateServer(
        name: String,
        endpointUrl: String,
        bearerToken: String = "",
        enabled: Boolean = false,
        requireWriteConfirmation: Boolean = true
    ): Result<McpServerConfig> {
        val cleanName = name.trim()
        val cleanUrl = endpointUrl.trim()
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("MCP server name must be at least 2 characters."))
        }
        val existing = mcpServerDao.getByName(cleanName)
        val id = existing?.id ?: UUID.randomUUID().toString()
        if (bearerToken.isNotBlank()) {
            saveEncryptedToken(id, bearerToken.trim())
        }
        val entity = McpServerEntity(
            id = id,
            name = cleanName,
            endpointUrl = cleanUrl,
            enabled = enabled,
            requireWriteConfirmation = requireWriteConfirmation,
            sessionId = existing?.sessionId,
            cachedToolsJson = existing?.cachedToolsJson ?: "[]",
            lastStatus = existing?.lastStatus ?: "Configured",
            lastSyncedAt = existing?.lastSyncedAt ?: 0L,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        mcpServerDao.upsertServer(entity)
        return Result.success(toModel(entity))
    }

    suspend fun setServerEnabled(id: String, enabled: Boolean) {
        mcpServerDao.setEnabled(id, enabled)
    }

    suspend fun deleteServer(id: String) {
        clearEncryptedToken(id)
        mcpServerDao.deleteById(id)
    }

    suspend fun syncServerTools(id: String): Result<McpServerConfig> {
        val entity = mcpServerDao.getById(id)
            ?: return Result.failure(IllegalArgumentException("MCP server not found."))
        val token = readEncryptedToken(entity.id)

        val initRes = mcpClient.initialize(entity.endpointUrl, token)
        if (initRes.isFailure) {
            val errMsg = SecurityLogRedactor.redact(initRes.exceptionOrNull()?.localizedMessage ?: "Init failed")
            mcpServerDao.upsertServer(entity.copy(lastStatus = "Error: $errMsg"))
            return Result.failure(initRes.exceptionOrNull()!!)
        }
        val init = initRes.getOrThrow()

        val toolsRes = mcpClient.listTools(
            serverId = entity.id,
            serverName = entity.name,
            endpointUrl = entity.endpointUrl,
            bearerToken = token,
            sessionId = init.sessionId
        )
        if (toolsRes.isFailure) {
            val errMsg = SecurityLogRedactor.redact(toolsRes.exceptionOrNull()?.localizedMessage ?: "tools/list failed")
            mcpServerDao.upsertServer(entity.copy(sessionId = init.sessionId, lastStatus = "Error: $errMsg"))
            return Result.failure(toolsRes.exceptionOrNull()!!)
        }

        val (tools, finalSession) = toolsRes.getOrThrow()
        val toolsJson = encodeTools(tools)
        val updated = entity.copy(
            sessionId = finalSession,
            cachedToolsJson = toolsJson,
            lastStatus = "Connected (${init.serverName}) · ${tools.size} tools",
            lastSyncedAt = System.currentTimeMillis()
        )
        mcpServerDao.upsertServer(updated)
        return Result.success(toModel(updated))
    }

    suspend fun listEnabledTools(): List<McpToolSpec> =
        mcpServerDao.getAllOnce()
            .filter { it.enabled }
            .flatMap { parseTools(it.id, it.name, it.cachedToolsJson) }

    suspend fun invokeTool(
        serverNameOrId: String?,
        toolName: String,
        argumentsJson: String,
        userConfirmed: Boolean
    ): Result<McpToolCallOutput> {
        val servers = mcpServerDao.getAllOnce().filter { it.enabled }
        if (servers.isEmpty()) {
            return Result.failure(IllegalStateException("No enabled MCP servers configured. Add and enable an MCP server in Settings -> MCP."))
        }
        val candidates = if (!serverNameOrId.isNullOrBlank()) {
            servers.filter {
                it.id == serverNameOrId || it.name.equals(serverNameOrId.trim(), ignoreCase = true)
            }
        } else {
            servers
        }
        val targetServer = candidates.firstOrNull { srv ->
            parseTools(srv.id, srv.name, srv.cachedToolsJson).any { it.name.equals(toolName.trim(), ignoreCase = true) }
        } ?: candidates.firstOrNull()
            ?: return Result.failure(IllegalArgumentException("No enabled MCP server matches '$serverNameOrId'."))

        val spec = parseTools(targetServer.id, targetServer.name, targetServer.cachedToolsJson)
            .firstOrNull { it.name.equals(toolName.trim(), ignoreCase = true) }

        if (targetServer.requireWriteConfirmation && spec?.isPotentiallyMutating == true && !userConfirmed) {
            return Result.failure(
                SecurityException("CONFIRM_REQUIRED: MCP tool '${toolName}' on '${targetServer.name}' appears to modify external state.")
            )
        }

        val argsObj = runCatching {
            if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
        }.getOrElse {
            return Result.failure(IllegalArgumentException("Invalid JSON arguments for MCP tool '$toolName'."))
        }

        val token = readEncryptedToken(targetServer.id)
        return mcpClient.callTool(
            endpointUrl = targetServer.endpointUrl,
            toolName = toolName.trim(),
            arguments = argsObj,
            bearerToken = token,
            sessionId = targetServer.sessionId
        )
    }

    private fun tokenPrefs() =
        context.getSharedPreferences("jarvis_mcp_secrets", Context.MODE_PRIVATE)

    private fun saveEncryptedToken(serverId: String, plaintext: String) {
        val encrypted = credentialStore.encryptResult(plaintext).getOrNull().orEmpty()
        if (encrypted.isNotBlank()) {
            tokenPrefs().edit().putString(tokenAlias(serverId), encrypted).apply()
        }
    }

    private fun readEncryptedToken(serverId: String): String {
        val stored = tokenPrefs().getString(tokenAlias(serverId), "").orEmpty()
        return if (stored.isBlank()) "" else credentialStore.decrypt(stored)
    }

    private fun clearEncryptedToken(serverId: String) {
        tokenPrefs().edit().remove(tokenAlias(serverId)).apply()
    }

    private fun tokenAlias(serverId: String): String = "mcp_token_$serverId"

    private fun toModel(entity: McpServerEntity): McpServerConfig =
        McpServerConfig(
            id = entity.id,
            name = entity.name,
            endpointUrl = entity.endpointUrl,
            enabled = entity.enabled,
            requireWriteConfirmation = entity.requireWriteConfirmation,
            sessionId = entity.sessionId,
            tools = parseTools(entity.id, entity.name, entity.cachedToolsJson),
            lastStatus = entity.lastStatus,
            lastSyncedAt = entity.lastSyncedAt
        )

    private fun encodeTools(tools: List<McpToolSpec>): String {
        val arr = JSONArray()
        tools.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("name", t.name)
                    put("description", t.description)
                    put("inputSchemaJson", t.inputSchemaJson)
                    put("isPotentiallyMutating", t.isPotentiallyMutating)
                }
            )
        }
        return arr.toString()
    }

    private fun parseTools(serverId: String, serverName: String, raw: String): List<McpToolSpec> =
        runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val name = obj.optString("name")
                if (name.isBlank()) return@mapNotNull null
                McpToolSpec(
                    serverId = serverId,
                    serverName = serverName,
                    name = name,
                    description = obj.optString("description"),
                    inputSchemaJson = obj.optString("inputSchemaJson", "{}"),
                    isPotentiallyMutating = obj.optBoolean("isPotentiallyMutating", false)
                )
            }
        }.getOrDefault(emptyList())
}
