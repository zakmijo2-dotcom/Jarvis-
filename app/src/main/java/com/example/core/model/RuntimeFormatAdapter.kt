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
            "INT4", "Q4_K_M", "Q4_K_S", "Q4_K", "Q4_M", "Q4_0", "Q4_1", "IQ4_XS", "IQ4_NL",
            "Q5_K_M", "Q5_K_S", "Q6_K", "Q3_K_M", "Q3_K_S", "Q2_K", "IQ3_XS", "IQ2_XS",
            "INT8", "Q8_0", "FP16", "BF16", "F32"
        )
        return candidates.firstOrNull { upper.contains(it) } ?: "Unspecified"
    }

    fun estimateMemoryBytes(fileSizeBytes: Long, contextTokens: Int = 4096): Long {
        if (fileSizeBytes <= 0L) return 0L
        val clampedCtx = contextTokens.coerceIn(512, 32768)
        val kvBytes = BASE_KV_OVERHEAD_BYTES + ((clampedCtx / 1024L) * KV_PER_1K_CONTEXT_BYTES)
        return (fileSizeBytes * 1.25).toLong() + kvBytes
    }

    companion object {
        private const val BASE_KV_OVERHEAD_BYTES = 160L * 1024L * 1024L
        private const val KV_PER_1K_CONTEXT_BYTES = 56L * 1024L * 1024L
    }
}

class LiteRtLmFormatAdapter : RuntimeFormatAdapter {
    override val formatId = RuntimeFormatId.LITERT_LM
    override val displayName = "LiteRT-LM Engine (.litertlm)"
    override val supportedExtensions = setOf("litertlm")
    override val isExecutionSupported = true
    override val runtimeNotes =
        "Verified local execution via LiteRT-LM with OpenCL GPU and XNNPACK CPU fallback."
}

class GgufFormatAdapter : RuntimeFormatAdapter {
    override val formatId = RuntimeFormatId.GGUF_LLAMA_CPP
    override val displayName = "GGUF / llama.cpp"
    override val supportedExtensions = setOf("gguf")
    override val isExecutionSupported = false
    override val runtimeNotes =
        "Unsupported in this APK build: native llama.cpp engine is not bundled. Downloads and execution are disabled."
}

class LiteRtTfliteFormatAdapter : RuntimeFormatAdapter {
    override val formatId = RuntimeFormatId.LITERT_TFLITE
    override val displayName = "LiteRT / TFLite"
    override val supportedExtensions = setOf("tflite")
    override val isExecutionSupported = false
    override val runtimeNotes =
        "Unsupported in this APK build: standalone .tflite flatbuffers lack bundled LLM tokenizer/session runner."
}

class MediaPipeTaskFormatAdapter : RuntimeFormatAdapter {
    override val formatId = RuntimeFormatId.MEDIAPIPE_TASK
    override val displayName = "MediaPipe LLM Task (.task)"
    override val supportedExtensions = setOf("task")
    override val isExecutionSupported = false
    override val runtimeNotes =
        "Unsupported in this APK build: MediaPipe GenAI runtime is not bundled. Use .litertlm bundles instead."
}

class SafetensorsFormatAdapter : RuntimeFormatAdapter {
    override val formatId = RuntimeFormatId.SAFETENSORS_RAW
    override val displayName = "Raw Safetensors (.safetensors)"
    override val supportedExtensions = setOf("safetensors")
    override val isExecutionSupported = false
    override val runtimeNotes =
        "Unsupported format: raw .safetensors training weights cannot execute on Android."

    override fun estimateMemoryBytes(fileSizeBytes: Long, contextTokens: Int): Long =
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
