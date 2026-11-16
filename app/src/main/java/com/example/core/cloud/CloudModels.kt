package com.example.core.cloud

import com.example.core.ai.AiStreamEvent
import com.example.data.local.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class CloudProviderKind(
    val id: String,
    val label: String,
    val defaultEndpoint: String,
    val defaultModel: String,
    val availableModels: List<String>
) {
    OPENAI_COMPATIBLE(
        id = "openai_compatible",
        label = "OpenAI-Compatible",
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini",
        availableModels = listOf("gpt-4o-mini", "gpt-4o", "o4-mini")
    ),
    OPENROUTER(
        id = "openrouter",
        label = "OpenRouter",
        defaultEndpoint = "https://openrouter.ai/api/v1/chat/completions",
        defaultModel = "google/gemini-2.5-flash",
        availableModels = listOf(
            "google/gemini-2.5-flash",
            "anthropic/claude-3.7-sonnet",
            "meta-llama/llama-3.3-70b-instruct"
        )
    ),
    GEMINI_REST(
        id = "gemini_rest",
        label = "Google Gemini REST",
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.5-flash",
        availableModels = listOf("gemini-2.5-flash", "gemini-2.5-pro")
    ),
    ANTHROPIC(
        id = "anthropic_claude",
        label = "Anthropic Messages",
        defaultEndpoint = "https://api.anthropic.com/v1/messages",
        defaultModel = "claude-3-5-sonnet-latest",
        availableModels = listOf("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest")
    ),
    CUSTOM_ENDPOINT(
        id = "custom_openai",
        label = "Custom Endpoint",
        defaultEndpoint = "https://api.example.com/v1/chat/completions",
        defaultModel = "custom-model",
        availableModels = emptyList()
    );

    companion object {
        fun fromIdOrName(raw: String?): CloudProviderKind {
            if (raw.isNullOrBlank()) return OPENAI_COMPATIBLE
            return entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.id.equals(raw, ignoreCase = true)
            } ?: OPENAI_COMPATIBLE
        }
    }
}

data class CloudImageAttachment(
    val base64Data: String,
    val mimeType: String = "image/jpeg",
    val label: String = "screen"
)

data class CloudInferenceConfig(
    val providerKind: CloudProviderKind,
    val endpointUrl: String,
    val modelId: String,
    val apiKey: String,
    val timeoutSeconds: Int = 30,
    val maxOutputTokens: Int = 512,
    val temperature: Float = 0.7f,
    val toolsSchemaJson: String? = null,
    val attachments: List<CloudImageAttachment> = emptyList()
)

data class CloudUsageStats(
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val totalPromptTokensEst: Int = 0,
    val totalCompletionTokensEst: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastProviderUsed: String = "None",
    val lastErrorMessage: String? = null
)

interface CloudAiClient {
    val usageStats: StateFlow<CloudUsageStats>

    fun streamChat(
        config: CloudInferenceConfig,
        systemInstruction: String,
        prompt: String,
        history: List<MessageEntity>
    ): Flow<AiStreamEvent>

    fun cancelActiveRequest()
    fun resetUsageStats()
}
