package com.example.core.ai

import com.example.core.cloud.CloudProviderKind
import java.util.Locale

enum class ModelTier(val label: String) {
    FAST_SMALL("Fast / Lightweight"),
    BALANCED("Balanced General"),
    REASONING_STRONG("Strong Reasoning"),
    VISION("Multimodal Vision")
}

data class DetectedModelCapabilities(
    val modelId: String,
    val supportsTools: Boolean,
    val supportsVision: Boolean,
    val supportsStructuredOutput: Boolean,
    val tier: ModelTier,
    val recommendedForComplexityMin: Int,
    val rationale: String
)

/**
 * Capability detector & tiered model selector across Gemini, OpenAI-compatible, OpenRouter,
 * Anthropic Claude, open-source models, and local LiteRT-LM.
 *
 * Never assumes every model supports tools, vision, or structured JSON output.
 */
object ModelCapabilityDetector {

    fun detectCloudModel(
        provider: CloudProviderKind,
        modelId: String
    ): DetectedModelCapabilities {
        val id = modelId.trim().ifBlank { provider.defaultModel }
        val lower = id.lowercase(Locale.ROOT)

        val supportsVision = when {
            lower.contains("gpt-4o") || lower.contains("gpt-4.1") || lower.contains("gpt-5") -> true
            lower.contains("gemini-2") || lower.contains("gemini-1.5") || lower.contains("gemini-3") -> true
            lower.contains("claude-3") || lower.contains("claude-4") || lower.contains("sonnet") || lower.contains("opus") || lower.contains("haiku") -> true
            lower.contains("vl") || lower.contains("vision") || lower.contains("llava") || lower.contains("pixtral") || lower.contains("qwen2.5-vl") -> true
            else -> false
        }

        val supportsTools = when {
            // Known legacy / pure completion / tiny open-source models without reliable tool calling
            lower.contains("instruct-base") || lower.contains("tinyllama") || lower.contains("phi-2") || lower.contains("gemma-2b") -> false
            provider == CloudProviderKind.GEMINI_REST -> true
            provider == CloudProviderKind.ANTHROPIC -> true
            provider == CloudProviderKind.OPENAI_COMPATIBLE -> !lower.contains("davinci") && !lower.contains("babbage")
            provider == CloudProviderKind.OPENROUTER -> !lower.contains(":free-text-only") && !lower.contains("mythomax")
            else -> lower.contains("gpt") || lower.contains("claude") || lower.contains("gemini") || lower.contains("qwen") || lower.contains("llama-3") || lower.contains("mistral") || lower.contains("deepseek")
        }

        val supportsStructured = supportsTools || lower.contains("json") || lower.contains("gemini") || lower.contains("gpt-4") || lower.contains("claude-3") || lower.contains("claude-4")

        val isSmallFast = lower.contains("mini") || lower.contains("flash") || lower.contains("haiku") ||
            lower.contains("nano") || lower.contains("1b") || lower.contains("2b") || lower.contains("3b") || lower.contains("8b")
        val isStrongReasoning = lower.contains("pro") || lower.contains("opus") || lower.contains("sonnet") ||
            lower.contains("o1") || lower.contains("o3") || lower.contains("o4") || lower.contains("r1") ||
            lower.contains("70b") || lower.contains("405b") || (lower.contains("gpt-4o") && !lower.contains("mini")) ||
            lower.contains("gpt-4.1") && !lower.contains("mini")

        val tier = when {
            isStrongReasoning -> ModelTier.REASONING_STRONG
            isSmallFast -> ModelTier.FAST_SMALL
            supportsVision -> ModelTier.VISION
            else -> ModelTier.BALANCED
        }

        return DetectedModelCapabilities(
            modelId = id,
            supportsTools = supportsTools,
            supportsVision = supportsVision,
            supportsStructuredOutput = supportsStructured,
            tier = tier,
            recommendedForComplexityMin = if (tier == ModelTier.REASONING_STRONG) 6 else 1,
            rationale = "${provider.label}/$id • tier=${tier.label} • tools=${if (supportsTools) "yes" else "no"} • vision=${if (supportsVision) "yes" else "no"}"
        )
    }

    fun detectLocalModel(modelName: String): DetectedModelCapabilities {
        val lower = modelName.lowercase(Locale.ROOT)
        val isVision = lower.contains("vision") || lower.contains("vlm") || lower.contains("e2b") || lower.contains("e4b")
        val isSmall = lower.contains("0.5b") || lower.contains("1b") || lower.contains("1.5b") || lower.contains("2b")
        return DetectedModelCapabilities(
            modelId = modelName,
            supportsTools = true, // Via JARVIS prompt-based [TOOL_CALL] protocol
            supportsVision = isVision,
            supportsStructuredOutput = !isSmall,
            tier = if (isSmall) ModelTier.FAST_SMALL else ModelTier.BALANCED,
            recommendedForComplexityMin = 1,
            rationale = "Local LiteRT-LM ($modelName) • offline • prompt-protocol tools"
        )
    }

    /**
     * Selects a tier-appropriate model from the provider's configured catalog when no explicit
     * `@model` override is set by the user:
     * - Low complexity text tasks (<= 3, no vision) -> fast/small model if available
     * - Visual tasks -> vision-capable model
     * - High complexity planning/reasoning (>= 6) -> strong reasoning model
     */
    fun selectTieredCloudModel(
        provider: CloudProviderKind,
        configuredModelId: String,
        complexityScore: Int,
        needsVision: Boolean
    ): Pair<String, ModelTier> {
        val configured = configuredModelId.trim().ifBlank { provider.defaultModel }
        val configuredCaps = detectCloudModel(provider, configured)

        // If the user explicitly configured a custom model outside defaults, respect it unless it lacks required vision
        val isProviderDefault = configured.equals(provider.defaultModel, ignoreCase = true)
        if (!isProviderDefault && (!needsVision || configuredCaps.supportsVision)) {
            return configured to configuredCaps.tier
        }

        val candidates = provider.availableModels.map { detectCloudModel(provider, it) }
        if (candidates.isEmpty()) return configured to configuredCaps.tier

        if (needsVision) {
            val visionPick = candidates
                .filter { it.supportsVision }
                .sortedByDescending { if (complexityScore >= 6 && it.tier == ModelTier.REASONING_STRONG) 2 else 1 }
                .firstOrNull()
            if (visionPick != null) return visionPick.modelId to ModelTier.VISION
        }

        if (complexityScore >= 6) {
            val strong = candidates.firstOrNull { it.tier == ModelTier.REASONING_STRONG }
            if (strong != null) return strong.modelId to ModelTier.REASONING_STRONG
        }

        if (complexityScore <= 3 && !needsVision) {
            val fast = candidates.firstOrNull { it.tier == ModelTier.FAST_SMALL }
            if (fast != null) return fast.modelId to ModelTier.FAST_SMALL
        }

        return configured to configuredCaps.tier
    }
}
