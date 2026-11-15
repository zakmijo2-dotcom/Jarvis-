package com.example.core.hf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

@Serializable
data class HfModelSummaryDto(
    val id: String,
    val author: String? = null,
    val downloads: Long = 0L,
    val likes: Long = 0L,
    val private: Boolean = false,
    val gated: JsonElement? = null,
    val disabled: Boolean = false,
    @SerialName("pipeline_tag") val pipelineTag: String? = null,
    @SerialName("library_name") val libraryName: String? = null,
    val tags: List<String> = emptyList(),
    val lastModified: String? = null,
    val siblings: List<HfSiblingDto> = emptyList()
)

@Serializable
data class HfModelDetailDto(
    val id: String,
    val author: String? = null,
    val downloads: Long = 0L,
    val likes: Long = 0L,
    val private: Boolean = false,
    val gated: JsonElement? = null,
    val disabled: Boolean = false,
    @SerialName("pipeline_tag") val pipelineTag: String? = null,
    @SerialName("library_name") val libraryName: String? = null,
    val tags: List<String> = emptyList(),
    val lastModified: String? = null,
    val cardData: HfCardDataDto? = null,
    val config: JsonObject? = null,
    val safetensors: HfSafetensorsDto? = null,
    val gguf: HfGgufDto? = null,
    val siblings: List<HfSiblingDto> = emptyList()
)

@Serializable
data class HfCardDataDto(
    val license: JsonElement? = null,
    @SerialName("base_model") val baseModel: JsonElement? = null,
    @SerialName("pipeline_tag") val pipelineTag: String? = null,
    @SerialName("library_name") val libraryName: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("extra_gated_prompt") val extraGatedPrompt: String? = null
)

@Serializable
data class HfSafetensorsDto(
    val total: Long? = null
)

@Serializable
data class HfGgufDto(
    val total: Long? = null,
    val architecture: String? = null,
    @SerialName("context_length") val contextLength: Int? = null
)

@Serializable
data class HfSiblingDto(
    val rfilename: String,
    val size: Long? = null,
    val lfs: HfLfsInfoDto? = null
)

@Serializable
data class HfLfsInfoDto(
    val sha256: String? = null,
    val size: Long? = null
)

data class HfRepoSummary(
    val repoId: String,
    val author: String,
    val modelName: String,
    val downloads: Long,
    val likes: Long,
    val isPrivate: Boolean,
    val isGated: Boolean,
    val gatedMode: String?,
    val pipelineTag: String?,
    val libraryName: String?,
    val license: String?,
    val tags: List<String>,
    val siblingFilenames: List<String>,
    val lastModified: String?
)

data class HfRepoFile(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String?,
    val downloadUrl: String
)

data class HfRepoDetail(
    val repoId: String,
    val author: String,
    val modelName: String,
    val downloads: Long,
    val likes: Long,
    val isPrivate: Boolean,
    val isGated: Boolean,
    val gatedMode: String?,
    val gatedPrompt: String?,
    val license: String,
    val architecture: String,
    val parameterCount: String,
    val quantizationSummary: String,
    val contextLengthHint: Int?,
    val supportedTasks: List<String>,
    val libraryName: String?,
    val baseModel: String?,
    val tags: List<String>,
    val files: List<HfRepoFile>,
    val modelCardMarkdown: String? = null
)

object HfMetadataParser {

    fun parseGated(gated: JsonElement?): Pair<Boolean, String?> {
        if (gated == null) return false to null
        if (gated is JsonPrimitive) {
            val boolVal = gated.booleanOrNull
            if (boolVal != null) {
                return boolVal to if (boolVal) "manual" else null
            }
            val str = gated.contentOrNull?.trim().orEmpty()
            if (str.isNotEmpty() && !str.equals("false", ignoreCase = true)) {
                return true to str
            }
        }
        return false to null
    }

    fun parseJsonStringOrList(element: JsonElement?): String? {
        if (element == null) return null
        return when (element) {
            is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(", ")
                .takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    fun extractLicense(cardLicense: JsonElement?, tags: List<String>): String {
        val fromCard = parseJsonStringOrList(cardLicense)
        if (!fromCard.isNullOrBlank()) return fromCard
        val fromTag = tags.firstOrNull { it.startsWith("license:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
        return fromTag?.takeIf { it.isNotEmpty() } ?: "Unspecified (verify on HF)"
    }

    fun extractArchitecture(
        config: JsonObject?,
        gguf: HfGgufDto?,
        tags: List<String>,
        repoId: String
    ): String {
        val configArch = (config?.get("architectures") as? JsonArray)
            ?.firstOrNull()
            ?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (!configArch.isNullOrBlank()) return configArch

        val modelType = (config?.get("model_type") as? JsonPrimitive)?.contentOrNull
        if (!modelType.isNullOrBlank()) return modelType.replaceFirstChar { it.uppercase(Locale.US) }

        val ggufArch = gguf?.architecture?.takeIf { it.isNotBlank() }
        if (ggufArch != null) return ggufArch

        val lowerRepo = repoId.lowercase(Locale.US)
        val archCandidates = listOf(
            "gemma-4" to "Gemma 4",
            "gemma4" to "Gemma 4",
            "gemma-3" to "Gemma 3",
            "gemma3" to "Gemma 3",
            "gemma-2" to "Gemma 2",
            "gemma2" to "Gemma 2",
            "gemma" to "Gemma",
            "qwen2.5" to "Qwen 2.5",
            "qwen3" to "Qwen 3",
            "qwen2" to "Qwen 2",
            "qwen" to "Qwen",
            "llama-3" to "Llama 3",
            "llama3" to "Llama 3",
            "llama" to "Llama",
            "phi-4" to "Phi-4",
            "phi-3" to "Phi-3",
            "phi3" to "Phi-3",
            "smollm" to "SmolLM",
            "mistral" to "Mistral",
            "deepseek" to "DeepSeek"
        )
        for ((needle, label) in archCandidates) {
            if (lowerRepo.contains(needle) || tags.any { it.lowercase(Locale.US).contains(needle) }) {
                return label
            }
        }
        return "Transformer (Inspect Card)"
    }

    fun extractParameterCount(
        safetensors: HfSafetensorsDto?,
        gguf: HfGgufDto?,
        repoId: String,
        fileNames: List<String>
    ): String {
        val totalParams = safetensors?.total ?: gguf?.total
        if (totalParams != null && totalParams > 0L) {
            return when {
                totalParams >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", totalParams / 1_000_000_000.0)
                totalParams >= 1_000_000L -> String.format(Locale.US, "%.0fM", totalParams / 1_000_000.0)
                else -> "$totalParams"
            }
        }
        val regex = Regex("""(?i)\b(\d+(?:\.\d+)?[BM])\b""")
        val repoMatch = regex.find(repoId.replace('-', ' ').replace('_', ' '))?.groupValues?.get(1)
        if (repoMatch != null) return repoMatch.uppercase(Locale.US)
        for (name in fileNames) {
            val fileMatch = regex.find(name.replace('-', ' ').replace('_', ' '))?.groupValues?.get(1)
            if (fileMatch != null) return fileMatch.uppercase(Locale.US)
        }
        return "Unknown"
    }

    fun extractQuantizationSummary(fileNames: List<String>, tags: List<String>): String {
        val joined = (fileNames + tags).joinToString(" ").uppercase(Locale.US)
        val found = linkedSetOf<String>()
        val tokens = listOf(
            "INT4", "Q4_K_M", "Q4_K_S", "Q4_0", "Q4", "INT8", "Q8_0",
            "Q5_K_M", "Q6_K", "IQ4_XS", "FP16", "BF16", "F32"
        )
        for (tok in tokens) {
            if (joined.contains(tok)) found.add(tok)
        }
        return if (found.isEmpty()) "See model files" else found.take(4).joinToString(", ")
    }

    fun extractSupportedTasks(pipelineTag: String?, tags: List<String>): List<String> {
        val tasks = linkedSetOf<String>()
        if (!pipelineTag.isNullOrBlank()) tasks.add(pipelineTag)
        val knownTasks = setOf(
            "text-generation", "conversational", "chat", "text2text-generation",
            "image-text-to-text", "automatic-speech-recognition", "feature-extraction",
            "question-answering", "summarization"
        )
        tags.forEach { tag ->
            if (tag.lowercase(Locale.US) in knownTasks) tasks.add(tag)
        }
        return if (tasks.isEmpty()) listOf("text-generation") else tasks.toList()
    }

    fun stripYamlFrontmatter(markdown: String): String {
        val trimmed = markdown.trimStart()
        if (!trimmed.startsWith("---")) return markdown.trim()
        val endIdx = trimmed.indexOf("\n---", startIndex = 3)
        return if (endIdx != -1) {
            trimmed.substring(endIdx + 4).trim()
        } else {
            markdown.trim()
        }
    }
}
