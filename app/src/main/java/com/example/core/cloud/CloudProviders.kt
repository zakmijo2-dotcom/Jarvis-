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
            id = CloudProviderKind.OPENAI_COMPATIBLE.id,
            name = CloudProviderKind.OPENAI_COMPATIBLE.label,
            protocol = "HTTPS / SSE Streaming",
            authRequirement = "Encrypted User API Key",
            phaseStatus = "Active (Phase 6)"
        ),
        CloudProviderDescriptor(
            id = CloudProviderKind.OPENROUTER.id,
            name = CloudProviderKind.OPENROUTER.label,
            protocol = "HTTPS / SSE Streaming",
            authRequirement = "Encrypted User API Key",
            phaseStatus = "Active (Phase 6)"
        ),
        CloudProviderDescriptor(
            id = CloudProviderKind.GEMINI_REST.id,
            name = CloudProviderKind.GEMINI_REST.label,
            protocol = "HTTPS / SSE Streaming",
            authRequirement = "Encrypted User API Key",
            phaseStatus = "Active (Phase 6)"
        ),
        CloudProviderDescriptor(
            id = CloudProviderKind.ANTHROPIC.id,
            name = CloudProviderKind.ANTHROPIC.label,
            protocol = "HTTPS / SSE Streaming",
            authRequirement = "Encrypted User API Key",
            phaseStatus = "Active (Phase 6)"
        ),
        CloudProviderDescriptor(
            id = CloudProviderKind.CUSTOM_ENDPOINT.id,
            name = CloudProviderKind.CUSTOM_ENDPOINT.label,
            protocol = "Configurable OpenAI SSE",
            authRequirement = "Encrypted User API Key",
            phaseStatus = "Active (Phase 6)"
        )
    )
}
