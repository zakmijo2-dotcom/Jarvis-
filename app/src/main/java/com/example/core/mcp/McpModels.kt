package com.example.core.mcp

import org.json.JSONObject

data class McpToolSpec(
    val serverId: String,
    val serverName: String,
    val name: String,
    val description: String,
    val inputSchemaJson: String = "{}",
    val isPotentiallyMutating: Boolean = false
)

data class McpServerConfig(
    val id: String,
    val name: String,
    val endpointUrl: String,
    val enabled: Boolean,
    val requireWriteConfirmation: Boolean,
    val sessionId: String? = null,
    val tools: List<McpToolSpec> = emptyList(),
    val lastStatus: String = "Configured",
    val lastSyncedAt: Long = 0L
)

data class McpInitResult(
    val protocolVersion: String,
    val serverName: String,
    val sessionId: String?
)

data class McpToolCallOutput(
    val toolName: String,
    val textContent: String,
    val isError: Boolean,
    val rawJson: JSONObject? = null
)
