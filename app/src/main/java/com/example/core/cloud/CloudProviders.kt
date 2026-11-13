package com.example.core.cloud

data class CloudProviderDescriptor(
    val id: String,
    val name: String,
    val protocol: String,
    val authRequirement: String,
    val phaseStatus: String
)

interface CloudProviderRegistry {
    fun listProviders(): List<CloudProviderDescriptor>
}

class Phase1CloudProviderRegistry : CloudProviderRegistry {
    override fun listProviders(): List<CloudProviderDescriptor> = listOf(
        CloudProviderDescriptor(
            id = "firebase_ai_gemini",
            name = "Google Gemini (Firebase AI Logic)",
            protocol = "Managed SDK / Streaming",
            authRequirement = "Firebase App Check",
            phaseStatus = "Boundary Ready (Phase 2)"
        ),
        CloudProviderDescriptor(
            id = "openai_compatible",
            name = "OpenAI-Compatible Gateway",
            protocol = "HTTPS / SSE",
            authRequirement = "Bearer Secret",
            phaseStatus = "Boundary Ready (Phase 2)"
        ),
        CloudProviderDescriptor(
            id = "anthropic_claude",
            name = "Anthropic Messages Gateway",
            protocol = "HTTPS / SSE",
            authRequirement = "API Key Secret",
            phaseStatus = "Boundary Ready (Phase 2)"
        )
    )
}
