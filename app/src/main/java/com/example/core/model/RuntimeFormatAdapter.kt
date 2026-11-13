package com.example.core.model

import java.util.Locale

enum class RuntimeFormatId(val label: String) {
    LITERT_LM("LiteRT-LM"),
    GGUF_LLAMA_CPP("GGUF / llama.cpp"),
    LITERT_TFLITE("LiteRT / TFLite"),
    MEDIAPIPE_TASK("MediaPipe Task"),
    SAFETENSORS_RAW("Safetensors (Unconverted)"),
    UNKNOWN("Unsupported / Non-Model")
}

interface RuntimeFormatAdapter {
    val formatId: RuntimeFormatId
    val displayName: String
    val supportedExtensions: Set<String>
    val isExecutionSupported: Boolean
    val runtimeNotes: String

    fun matchesFilename(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in supportedExtensions
    }

    fun extractQuantization(fileName: String, tags: List<String> = emptyList()): String {
        val upper = (listOf(fileName) + tags).joinToString(" ").uppercase(Locale.US)
        val candidates = listOf(
            "INT4", "Q4_K_M", "Q4_K_S", "Q4_0", "Q4_1", "IQ4_XS", "IQ4_NL",
            "Q5_K_M", "Q5_K_S", "Q6_K", "INT8", "Q8_0", "FP16", "BF16", "F32"
        )
        return candidates.firstOrNull { upper.contains(it) } ?: "Unspecified"
    }

    fun estimateMemoryBytes(fileSizeBytes: Long): Long {
        if (fileSizeBytes <= 0L) return 0L
        return (fileSizeBytes * 1.30).toLong() + KV_CACHE_OVERHEAD_BYTES
    }

    companion object {
        private const val KV_CACHE_OVERHEAD_BYTES = 384L * 1024L * 1024L
    }
}

class LiteRtLmFormatAdapter : RuntimeFormatAdapter {
    override val formatId = RuntimeFormatId.LITERT_LM
    override val displayName = "LiteRT-LM Engine (.litertlm)"
    override val supportedExtensions = setOf("litertlm")
    override val isExecutionSupported = true
    override val runtimeNotes =
        "Directly executable offline in JARVIS via LiteRT-LM with GPU (OpenCL) and CPU (XNNPACK) fallback."
}

class GgufFormatAdapter : RuntimeFormatAdapter {
    override val formatId = RuntimeFormatId.GGUF_LLAMA_CPP
    override val displayName = "GGUF / llama.cpp"
    override val supportedExtensions = setOf("gguf")
    override val isExecutionSupported = false
    override val runtimeNotes =
        "Adapter boundary prepared. llama.cpp native runtime integration is scheduled for a future phase; downloading stores the weights but cannot execute them yet."
}

class LiteRtTfliteFormatAdapter : RuntimeFormatAdapter {
    override val formatId = RuntimeFormatId.LITERT_TFLITE
    override val displayName = "LiteRT / TFLite"
    override val supportedExtensions = setOf("tflite")
    override val isExecutionSupported = false
    override val runtimeNotes =
        "Adapter boundary prepared for standalone .tflite flatbuffers (requires external tokenizer & session graph metadata)."
}

class MediaPipeTaskFormatAdapter : RuntimeFormatAdapter {
    override val formatId = RuntimeFormatId.MEDIAPIPE_TASK
    override val displayName = "MediaPipe LLM Task (.task)"
    override val supportedExtensions = setOf("task")
    override val isExecutionSupported = false
    override val runtimeNotes =
        "Adapter boundary prepared for MediaPipe GenAI .task bundles. Use .litertlm bundles for active LiteRT-LM execution."
}

class SafetensorsFormatAdapter : RuntimeFormatAdapter {
    override val formatId = RuntimeFormatId.SAFETENSORS_RAW
    override val displayName = "Raw Safetensors (.safetensors)"
    override val supportedExtensions = setOf("safetensors")
    override val isExecutionSupported = false
    override val runtimeNotes =
        "Raw .safetensors checkpoints are desktop/training weights and are NOT directly executable on Android. Convert to .litertlm or GGUF first."

    override fun estimateMemoryBytes(fileSizeBytes: Long): Long =
        (fileSizeBytes * 1.50).toLong() + 512L * 1024L * 1024L
}

class ModelFormatRegistry(
    val adapters: List<RuntimeFormatAdapter> = listOf(
        LiteRtLmFormatAdapter(),
        GgufFormatAdapter(),
        LiteRtTfliteFormatAdapter(),
        MediaPipeTaskFormatAdapter(),
        SafetensorsFormatAdapter()
    )
) {
    fun resolveAdapter(fileName: String): RuntimeFormatAdapter? =
        adapters.firstOrNull { it.matchesFilename(fileName) }

    fun resolveFormatId(fileName: String): RuntimeFormatId =
        resolveAdapter(fileName)?.formatId ?: RuntimeFormatId.UNKNOWN

    fun inferRepoSupportedFormats(fileNames: List<String>, tags: List<String>): List<RuntimeFormatId> {
        val detected = linkedSetOf<RuntimeFormatId>()
        fileNames.forEach { file ->
            resolveAdapter(file)?.let { detected.add(it.formatId) }
        }
        val lowerTags = tags.map { it.lowercase(Locale.US) }
        if (lowerTags.any { it.contains("litert-lm") || it.contains("litertlm") }) {
            detected.add(RuntimeFormatId.LITERT_LM)
        }
        if (lowerTags.any { it == "gguf" }) {
            detected.add(RuntimeFormatId.GGUF_LLAMA_CPP)
        }
        if (lowerTags.any { it == "safetensors" }) {
            detected.add(RuntimeFormatId.SAFETENSORS_RAW)
        }
        return detected.toList()
    }
}
