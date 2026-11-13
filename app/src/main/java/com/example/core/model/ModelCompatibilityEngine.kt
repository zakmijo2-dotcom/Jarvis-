package com.example.core.model

import android.os.Build
import com.example.core.hf.HfRepoDetail
import com.example.core.hf.HfRepoFile
import com.example.core.security.SecureCredentialStore
import com.example.core.tools.DeviceTelemetry
import java.util.Locale

enum class CheckStatus {
    PASS,
    WARN,
    BLOCK
}

enum class CompatibilityVerdict(val label: String) {
    READY_TO_INSTALL("Compatible (LiteRT-LM)"),
    INSTALLABLE_WITH_WARNING("Compatible with Warnings"),
    PREPARED_RUNTIME_ONLY("Runtime Adapter Pending"),
    BLOCKED("Incompatible / Blocked")
}

data class CompatibilityCheckItem(
    val category: String,
    val detail: String,
    val status: CheckStatus
)

data class CompatibilityReport(
    val fileName: String,
    val formatId: RuntimeFormatId,
    val architecture: String,
    val quantization: String,
    val parameterCount: String,
    val requiredRuntime: String,
    val estimatedMemoryBytes: Long,
    val requiredStorageBytes: Long,
    val license: String,
    val verdict: CompatibilityVerdict,
    val canDownloadToDevice: Boolean,
    val canRunInJarvisNow: Boolean,
    val summaryReason: String,
    val checks: List<CompatibilityCheckItem>
)

class ModelCompatibilityEngine(
    private val formatRegistry: ModelFormatRegistry = ModelFormatRegistry()
) {
    fun inspect(
        repoDetail: HfRepoDetail,
        file: HfRepoFile,
        telemetry: DeviceTelemetry,
        availableStorageBytes: Long,
        hasUserToken: Boolean
    ): CompatibilityReport {
        val checks = mutableListOf<CompatibilityCheckItem>()

        if (!SecureCredentialStore.isSafeModelFilename(file.fileName)) {
            checks += CompatibilityCheckItem(
                category = "Security",
                detail = "Blocked potentially executable or unsafe file (${file.fileName}).",
                status = CheckStatus.BLOCK
            )
            return CompatibilityReport(
                fileName = file.fileName,
                formatId = RuntimeFormatId.UNKNOWN,
                architecture = repoDetail.architecture,
                quantization = "N/A",
                parameterCount = repoDetail.parameterCount,
                requiredRuntime = "Blocked",
                estimatedMemoryBytes = 0L,
                requiredStorageBytes = 0L,
                license = repoDetail.license,
                verdict = CompatibilityVerdict.BLOCKED,
                canDownloadToDevice = false,
                canRunInJarvisNow = false,
                summaryReason = "Executable scripts and native libraries from repositories are strictly blocked.",
                checks = checks
            )
        }

        val adapter = formatRegistry.resolveAdapter(file.fileName)
        val formatId = adapter?.formatId ?: RuntimeFormatId.UNKNOWN
        val quantization = adapter?.extractQuantization(file.fileName, repoDetail.tags)
            ?: repoDetail.quantizationSummary

        // 1. File Format & Runtime Check
        when {
            formatId == RuntimeFormatId.SAFETENSORS_RAW -> {
                checks += CompatibilityCheckItem(
                    category = "Format & Runtime",
                    detail = adapter!!.runtimeNotes,
                    status = CheckStatus.BLOCK
                )
            }
            adapter == null -> {
                val ext = file.fileName.substringAfterLast('.', "none")
                val isConfigOrDoc = ext.lowercase(Locale.US) in setOf("json", "md", "txt", "model", "tokenizer")
                checks += CompatibilityCheckItem(
                    category = "File Format",
                    detail = if (isConfigOrDoc) {
                        "Metadata/config file (.$ext), not a standalone quantized mobile runtime bundle."
                    } else {
                        "Unrecognized model format (.$ext) with no registered runtime adapter."
                    },
                    status = CheckStatus.BLOCK
                )
            }
            adapter.isExecutionSupported -> {
                checks += CompatibilityCheckItem(
                    category = "Runtime",
                    detail = "${adapter.displayName} — supported offline on this build.",
                    status = CheckStatus.PASS
                )
            }
            else -> {
                checks += CompatibilityCheckItem(
                    category = "Runtime Adapter",
                    detail = adapter.runtimeNotes,
                    status = CheckStatus.WARN
                )
            }
        }

        // 2. Architecture & Quantization Inspection
        val archOk = !repoDetail.architecture.startsWith("Transformer (Inspect")
        checks += CompatibilityCheckItem(
            category = "Architecture",
            detail = "${repoDetail.architecture} • Params: ${repoDetail.parameterCount} • Quant: $quantization",
            status = if (archOk) CheckStatus.PASS else CheckStatus.WARN
        )

        // 3. Memory Estimation Check
        val estMemBytes = adapter?.estimateMemoryBytes(file.sizeBytes) ?: file.sizeBytes
        val totalRam = telemetry.totalRamBytes
        val availRam = telemetry.availRamBytes
        val estMemGb = estMemBytes / (1024f * 1024f * 1024f)
        val totalRamGb = totalRam / (1024f * 1024f * 1024f)
        if (estMemBytes > 0L && totalRam > 0L) {
            when {
                estMemBytes > (totalRam * 0.85).toLong() -> {
                    checks += CompatibilityCheckItem(
                        category = "Memory",
                        detail = String.format(
                            Locale.US,
                            "Estimated %.2f GB RAM exceeds safe limit for %.1f GB device RAM.",
                            estMemGb,
                            totalRamGb
                        ),
                        status = CheckStatus.BLOCK
                    )
                }
                availRam > 0L && estMemBytes > availRam -> {
                    checks += CompatibilityCheckItem(
                        category = "Memory",
                        detail = String.format(
                            Locale.US,
                            "Needs ~%.2f GB RAM; %.2f GB currently free (close background apps before loading).",
                            estMemGb,
                            availRam / (1024f * 1024f * 1024f)
                        ),
                        status = CheckStatus.WARN
                    )
                }
                else -> {
                    checks += CompatibilityCheckItem(
                        category = "Memory",
                        detail = String.format(
                            Locale.US,
                            "Estimated ~%.2f GB fits within %.1f GB RAM.",
                            estMemGb,
                            totalRamGb
                        ),
                        status = CheckStatus.PASS
                    )
                }
            }
        }

        // 4. Storage Check (file size + 128 MB headroom)
        val requiredStorage = if (file.sizeBytes > 0L) {
            file.sizeBytes + STORAGE_HEADROOM_BYTES
        } else {
            MIN_UNKNOWN_FILE_STORAGE_BYTES
        }
        val reqStorageMb = requiredStorage / (1024f * 1024f)
        val freeStorageGb = availableStorageBytes / (1024f * 1024f * 1024f)
        if (availableStorageBytes < requiredStorage) {
            checks += CompatibilityCheckItem(
                category = "Storage",
                detail = String.format(
                    Locale.US,
                    "Insufficient storage: needs %.0f MB (incl. headroom), %.2f GB free.",
                    reqStorageMb,
                    freeStorageGb
                ),
                status = CheckStatus.BLOCK
            )
        } else {
            checks += CompatibilityCheckItem(
                category = "Storage",
                detail = String.format(
                    Locale.US,
                    "Storage OK: needs %.0f MB, %.2f GB free.",
                    reqStorageMb,
                    freeStorageGb
                ),
                status = CheckStatus.PASS
            )
        }

        // 5. Device ABI & SDK Compatibility
        val abis = telemetry.supportedAbis.ifBlank { Build.SUPPORTED_ABIS.joinToString(", ") }
        val hasArm64 = abis.contains("arm64-v8a", ignoreCase = true)
        checks += CompatibilityCheckItem(
            category = "Device ABI",
            detail = if (hasArm64) {
                "ARM64 ($abis) • Android ${telemetry.androidRelease} (API ${telemetry.sdkInt})"
            } else {
                "Host/non-ARM64 ABI ($abis): LiteRT-LM native acceleration targets arm64-v8a."
            },
            status = if (hasArm64) CheckStatus.PASS else CheckStatus.WARN
        )

        // 6. License & Gated Restrictions
        if (repoDetail.isGated) {
            checks += CompatibilityCheckItem(
                category = "License & Gating",
                detail = if (hasUserToken) {
                    "Gated (${repoDetail.license}) — HF Read token configured; ensure license accepted on huggingface.co."
                } else {
                    "Gated (${repoDetail.license}) — requires accepting terms on HF and saving a Read token."
                },
                status = if (hasUserToken) CheckStatus.WARN else CheckStatus.BLOCK
            )
        } else {
            checks += CompatibilityCheckItem(
                category = "License",
                detail = repoDetail.license,
                status = CheckStatus.PASS
            )
        }

        val hasHardBlock = checks.any { it.status == CheckStatus.BLOCK }
        val hasWarn = checks.any { it.status == CheckStatus.WARN }
        val canRunNow = adapter?.isExecutionSupported == true && !hasHardBlock

        val verdict = when {
            hasHardBlock -> CompatibilityVerdict.BLOCKED
            adapter != null && !adapter.isExecutionSupported -> CompatibilityVerdict.PREPARED_RUNTIME_ONLY
            hasWarn -> CompatibilityVerdict.INSTALLABLE_WITH_WARNING
            else -> CompatibilityVerdict.READY_TO_INSTALL
        }

        val summaryReason = when (verdict) {
            CompatibilityVerdict.READY_TO_INSTALL ->
                "Verified compatible for immediate offline LiteRT-LM execution."
            CompatibilityVerdict.INSTALLABLE_WITH_WARNING ->
                checks.firstOrNull { it.status == CheckStatus.WARN }?.detail
                    ?: "Compatible with device warnings."
            CompatibilityVerdict.PREPARED_RUNTIME_ONLY ->
                adapter?.runtimeNotes ?: "Runtime adapter prepared for future execution."
            CompatibilityVerdict.BLOCKED ->
                checks.firstOrNull { it.status == CheckStatus.BLOCK }?.detail
                    ?: "File cannot be installed on this device."
        }

        return CompatibilityReport(
            fileName = file.fileName,
            formatId = formatId,
            architecture = repoDetail.architecture,
            quantization = quantization,
            parameterCount = repoDetail.parameterCount,
            requiredRuntime = adapter?.displayName ?: "Unsupported Format",
            estimatedMemoryBytes = estMemBytes,
            requiredStorageBytes = requiredStorage,
            license = repoDetail.license,
            verdict = verdict,
            canDownloadToDevice = !hasHardBlock && adapter != null,
            canRunInJarvisNow = canRunNow,
            summaryReason = summaryReason,
            checks = checks
        )
    }

    companion object {
        private const val STORAGE_HEADROOM_BYTES = 128L * 1024L * 1024L
        private const val MIN_UNKNOWN_FILE_STORAGE_BYTES = 256L * 1024L * 1024L
    }
}
