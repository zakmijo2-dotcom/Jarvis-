package com.example.core.ai

import android.content.Context
import com.example.core.agent.AgentRoutingMode
import com.example.core.agent.ExecutionRoute
import com.example.core.agent.IntentRouter
import com.example.core.agent.RoutingDecision
import com.example.core.cloud.CloudProviderKind
import com.example.core.model.ArtifactState
import com.example.core.model.HardwareFitTier
import com.example.core.model.ModelFormatRegistry
import com.example.core.model.ModelProviderType
import com.example.core.model.ModelSlot
import com.example.core.model.RuntimeFormatId
import com.example.core.tools.DeviceTelemetry
import com.example.data.local.db.ModelBenchmarkEntity
import com.example.data.repository.JarvisSettings
import java.io.File
import java.util.Locale

enum class ModelRecommendationBadge(val label: String) {
    RECOMMENDED("Recommended"),
    SUITABLE("Suitable"),
    TIGHT_RAM("Tight RAM"),
    INSUFFICIENT_RAM("Insufficient RAM"),
    THERMAL_THROTTLED("Thermal/Battery Guard"),
    UNSUPPORTED("Unsupported Format"),
    NEEDS_SETUP("Setup Required")
}

data class MeasuredModelMetrics(
    val sampleCount: Int,
    val avgTtftMs: Long,
    val avgTokensPerSecond: Float,
    val lastPeakMemoryMb: Float
)

data class ModelHardwareRecommendation(
    val slotId: String,
    val displayName: String,
    val providerType: ModelProviderType,
    val runtimeFormat: RuntimeFormatId,
    val badge: ModelRecommendationBadge,
    val fitTier: HardwareFitTier,
    val score: Int,
    val estimatedMemoryBytes: Long,
    val supportsToolCalling: Boolean,
    val measuredMetrics: MeasuredModelMetrics?,
    val rationale: String
)

data class LoadBudgetVerdict(
    val allowedToLoad: Boolean,
    val mustUnloadExistingFirst: Boolean,
    val allowConcurrentMultiModel: Boolean,
    val estimatedIncomingRamBytes: Long,
    val availableRamBytes: Long,
    val reason: String
)

data class ModelRoutePlan(
    val decision: RoutingDecision,
    val preferredSlot: ModelSlot?,
    val recommendedContextTokens: Int,
    val memoryBudgetWarning: String?,
    val thermalBatteryNote: String?,
    val recommendations: List<ModelHardwareRecommendation>
)

/**
 * Hardware-, benchmark-, thermal-, and task-aware router for local LiteRT-LM and modular Cloud models.
 */
class ModelRouter(
    private val intentRouter: IntentRouter = IntentRouter(),
    private val formatRegistry: ModelFormatRegistry = ModelFormatRegistry()
) {

    fun routeTask(
        prompt: String,
        historyChars: Int,
        settings: JarvisSettings,
        models: List<ModelSlot>,
        loadState: EngineLoadState,
        telemetry: DeviceTelemetry,
        batterySnapshot: BatteryThermalSnapshot,
        benchmarks: List<ModelBenchmarkEntity>,
        matchedSkillName: String? = null
    ): ModelRoutePlan {
        val recommendations = evaluateModels(
            models = models,
            settings = settings,
            telemetry = telemetry,
            batterySnapshot = batterySnapshot,
            benchmarks = benchmarks,
            requiredContextTokens = estimateRequiredContext(prompt.length + historyChars, settings.contextLengthTokens),
            needsToolCalling = intentRouter.detectNaturalLanguageTools(prompt).isNotEmpty()
        )

        val readyLocalModels = models.filter {
            it.providerType == ModelProviderType.LOCAL_ON_DEVICE &&
                it.runtimeFormat == RuntimeFormatId.LITERT_LM &&
                it.artifactState == ArtifactState.READY &&
                !it.localFilePath.isNullOrBlank()
        }
        val activeLocalReady = (loadState as? EngineLoadState.Ready) != null || readyLocalModels.isNotEmpty()

        val activeCapabilities = ModelCapabilityProfile(
            supportsToolCalling = true,
            supportsVision = false,
            supportsStreaming = true,
            maxContextTokens = adaptContextWindow(settings.contextLengthTokens, telemetry, batterySnapshot),
            estimatedLatencyTier = if (telemetry.availRamBytes in 1L..(1024L * 1024L * 1024L)) "TIGHT_RAM" else "FAST_LOCAL",
            offlineCapable = true
        )

        val baseDecision = intentRouter.routePrompt(
            prompt = prompt,
            settings = settings,
            isLocalModelReady = activeLocalReady,
            isNetworkConnected = telemetry.isNetworkConnected,
            activeCapabilities = activeCapabilities,
            matchedSkillName = matchedSkillName
        )

        val bestLocalSlot = recommendations
            .filter { rec ->
                rec.providerType == ModelProviderType.LOCAL_ON_DEVICE &&
                    rec.fitTier != HardwareFitTier.UNSUPPORTED_FORMAT &&
                    rec.fitTier != HardwareFitTier.INSUFFICIENT_RAM &&
                    models.firstOrNull { it.id == rec.slotId }?.artifactState == ArtifactState.READY
            }
            .maxByOrNull { it.score }
            ?.let { best -> models.firstOrNull { it.id == best.slotId } }

        val activeCloudSlot = models.firstOrNull { it.isActive && it.providerType == ModelProviderType.CLOUD_API }
            ?: models.firstOrNull { it.providerType == ModelProviderType.CLOUD_API }

        // Adapt toward lighter cloud inference during severe thermal/battery constraints in AUTO_HYBRID if consented
        val thermalOrBatteryConstrained =
            batterySnapshot.isPowerSaveMode ||
                batterySnapshot.thermalStatusCode >= 3 ||
                (!batterySnapshot.isCharging && batterySnapshot.batteryPercent in 1..15)

        val canOffloadCloud = settings.routingMode == AgentRoutingMode.AUTO_HYBRID &&
            settings.cloudUploadConsentGranted &&
            settings.cloudApiKey.isNotBlank() &&
            telemetry.isNetworkConnected

        val adaptedDecision = if (
            thermalOrBatteryConstrained &&
            !baseDecision.useCloudModel &&
            baseDecision.route == ExecutionRoute.LOCAL_LITERT_LM &&
            canOffloadCloud
        ) {
            baseDecision.copy(
                route = if (baseDecision.needsWebResearch) ExecutionRoute.HYBRID_WEB_AND_AI else ExecutionRoute.CLOUD_API,
                useCloudModel = true,
                reason = "Thermal/battery guard active (${batterySnapshot.thermalStatusLabel}, ${batterySnapshot.batteryPercent}%): offloaded synthesis to Cloud AI."
            )
        } else {
            baseDecision
        }

        val preferredSlot = if (adaptedDecision.useCloudModel) {
            activeCloudSlot
        } else {
            models.firstOrNull { it.isActive && it.providerType == ModelProviderType.LOCAL_ON_DEVICE && it.artifactState == ArtifactState.READY && it.runtimeFormat == RuntimeFormatId.LITERT_LM }
                ?: bestLocalSlot
                ?: models.firstOrNull { it.isActive }
        }

        val recommendedContext = adaptContextWindow(settings.contextLengthTokens, telemetry, batterySnapshot)

        val selectedRec = preferredSlot?.let { slot -> recommendations.firstOrNull { it.slotId == slot.id } }
        val memWarning = when {
            preferredSlot == null -> null
            selectedRec?.fitTier == HardwareFitTier.INSUFFICIENT_RAM ->
                "${preferredSlot.displayName} exceeds safe RAM limits for this device."
            selectedRec?.fitTier == HardwareFitTier.TIGHT ->
                "Tight RAM headroom (~${selectedRec.estimatedMemoryBytes / (1024L * 1024L)} MB estimated). Context capped to $recommendedContext tokens."
            else -> null
        }

        val thermalNote = if (thermalOrBatteryConstrained) {
            buildString {
                append("Device constraint active: Thermal=${batterySnapshot.thermalStatusLabel}")
                if (batterySnapshot.batteryPercent >= 0) {
                    append(", Battery=${batterySnapshot.batteryPercent}%")
                    if (batterySnapshot.batteryTempCelsius > 0f) {
                        append(String.format(Locale.US, " (%.1f°C)", batterySnapshot.batteryTempCelsius))
                    }
                }
                if (batterySnapshot.isPowerSaveMode) append(", Power Saver ON")
                append(".")
            }
        } else null

        return ModelRoutePlan(
            decision = adaptedDecision,
            preferredSlot = preferredSlot,
            recommendedContextTokens = recommendedContext,
            memoryBudgetWarning = memWarning,
            thermalBatteryNote = thermalNote,
            recommendations = recommendations
        )
    }

    fun evaluateModels(
        models: List<ModelSlot>,
        settings: JarvisSettings,
        telemetry: DeviceTelemetry,
        batterySnapshot: BatteryThermalSnapshot,
        benchmarks: List<ModelBenchmarkEntity>,
        requiredContextTokens: Int = settings.contextLengthTokens,
        needsToolCalling: Boolean = false
    ): List<ModelHardwareRecommendation> {
        val totalRam = telemetry.totalRamBytes.coerceAtLeast(0L)
        val availRam = telemetry.availRamBytes.coerceAtLeast(0L)
        val thermalConstrained =
            batterySnapshot.thermalStatusCode >= 3 ||
                (!batterySnapshot.isCharging && batterySnapshot.batteryPercent in 1..15) ||
                batterySnapshot.isPowerSaveMode

        val rawList = models.map { slot ->
            val measured = aggregateBenchmarksForSlot(slot, benchmarks)
            if (slot.providerType == ModelProviderType.CLOUD_API) {
                evaluateCloudSlot(slot, settings, telemetry, thermalConstrained, measured)
            } else {
                evaluateLocalSlot(
                    slot = slot,
                    totalRam = totalRam,
                    availRam = availRam,
                    requiredContextTokens = requiredContextTokens,
                    thermalConstrained = thermalConstrained,
                    needsToolCalling = needsToolCalling,
                    measured = measured
                )
            }
        }

        val topExecutableId = rawList
            .filter {
                it.fitTier == HardwareFitTier.OPTIMAL ||
                    it.fitTier == HardwareFitTier.TIGHT
            }
            .filter {
                val slot = models.firstOrNull { m -> m.id == it.slotId }
                if (it.providerType == ModelProviderType.CLOUD_API) {
                    settings.cloudUploadConsentGranted && settings.cloudApiKey.isNotBlank() && telemetry.isNetworkConnected
                } else {
                    slot?.runtimeFormat == RuntimeFormatId.LITERT_LM &&
                        (slot.artifactState == ArtifactState.READY || !slot.downloadUrl.isNullOrBlank())
                }
            }
            .maxByOrNull { it.score }
            ?.slotId

        return rawList.map { rec ->
            if (rec.slotId == topExecutableId &&
                rec.badge != ModelRecommendationBadge.INSUFFICIENT_RAM &&
                rec.badge != ModelRecommendationBadge.UNSUPPORTED &&
                rec.badge != ModelRecommendationBadge.NEEDS_SETUP
            ) {
                rec.copy(badge = ModelRecommendationBadge.RECOMMENDED)
            } else {
                rec
            }
        }.sortedByDescending { it.score }
    }

    private fun evaluateLocalSlot(
        slot: ModelSlot,
        totalRam: Long,
        availRam: Long,
        requiredContextTokens: Int,
        thermalConstrained: Boolean,
        needsToolCalling: Boolean,
        measured: MeasuredModelMetrics?
    ): ModelHardwareRecommendation {
        val diskBytes = slot.localFilePath?.let { path ->
            runCatching { File(path).length() }.getOrDefault(0L)
        }?.takeIf { it > 0L } ?: slot.expectedSizeBytes

        val estMem = formatRegistry
            .resolveAdapter("model.${slot.runtimeFormat.name.lowercase(Locale.US)}")
            ?.estimateMemoryBytes(diskBytes, requiredContextTokens)
            ?: slot.estimatedMemoryBytes(requiredContextTokens)

        if (slot.runtimeFormat != RuntimeFormatId.LITERT_LM) {
            return ModelHardwareRecommendation(
                slotId = slot.id,
                displayName = slot.displayName,
                providerType = slot.providerType,
                runtimeFormat = slot.runtimeFormat,
                badge = ModelRecommendationBadge.UNSUPPORTED,
                fitTier = HardwareFitTier.UNSUPPORTED_FORMAT,
                score = -100,
                estimatedMemoryBytes = estMem,
                supportsToolCalling = false,
                measuredMetrics = measured,
                rationale = "Format ${slot.runtimeFormat.label} has no bundled runtime engine in this APK."
            )
        }

        val ramFit = when {
            estMem <= 0L -> HardwareFitTier.OPTIMAL
            totalRam > 0L && estMem > (totalRam * 0.85).toLong() -> HardwareFitTier.INSUFFICIENT_RAM
            availRam > 0L && estMem > availRam -> HardwareFitTier.TIGHT
            else -> HardwareFitTier.OPTIMAL
        }

        var score = 55
        if (slot.artifactState == ArtifactState.READY) score += 25
        if (slot.isActive) score += 8
        when (ramFit) {
            HardwareFitTier.OPTIMAL -> score += 18
            HardwareFitTier.TIGHT -> score -= 12
            HardwareFitTier.INSUFFICIENT_RAM -> score -= 60
            HardwareFitTier.UNSUPPORTED_FORMAT -> score -= 100
        }
        if (slot.contextWindow >= requiredContextTokens) score += 6 else score -= 8
        if (needsToolCalling) score += 5
        if (thermalConstrained && estMem > 1024L * 1024L * 1024L) score -= 18
        if (measured != null) {
            score += when {
                measured.avgTokensPerSecond >= 18f -> 12
                measured.avgTokensPerSecond >= 8f -> 6
                else -> 0
            }
            if (measured.avgTtftMs in 1L..800L) score += 5
        }

        val badge = when {
            ramFit == HardwareFitTier.INSUFFICIENT_RAM -> ModelRecommendationBadge.INSUFFICIENT_RAM
            slot.artifactState != ArtifactState.READY && slot.downloadUrl.isNullOrBlank() -> ModelRecommendationBadge.NEEDS_SETUP
            thermalConstrained && estMem > 1024L * 1024L * 1024L -> ModelRecommendationBadge.THERMAL_THROTTLED
            ramFit == HardwareFitTier.TIGHT -> ModelRecommendationBadge.TIGHT_RAM
            else -> ModelRecommendationBadge.SUITABLE
        }

        val estMb = estMem / (1024L * 1024L)
        val availMb = availRam / (1024L * 1024L)
        val rationale = buildString {
            append(if (estMb > 0L) "~${estMb} MB RAM • " else "")
            append("Free ${availMb} MB • ")
            append("Ctx ${slot.contextWindow} • ")
            if (measured != null) {
                append(String.format(Locale.US, "Measured %.1f tok/s (TTFT %d ms)", measured.avgTokensPerSecond, measured.avgTtftMs))
            } else {
                append("No local runs recorded yet")
            }
        }

        return ModelHardwareRecommendation(
            slotId = slot.id,
            displayName = slot.displayName,
            providerType = slot.providerType,
            runtimeFormat = slot.runtimeFormat,
            badge = badge,
            fitTier = ramFit,
            score = score,
            estimatedMemoryBytes = estMem,
            supportsToolCalling = true,
            measuredMetrics = measured,
            rationale = rationale
        )
    }

    private fun evaluateCloudSlot(
        slot: ModelSlot,
        settings: JarvisSettings,
        telemetry: DeviceTelemetry,
        thermalConstrained: Boolean,
        measured: MeasuredModelMetrics?
    ): ModelHardwareRecommendation {
        val readyCloud = settings.cloudUploadConsentGranted && settings.cloudApiKey.isNotBlank()
        val detected = ModelCapabilityDetector.detectCloudModel(
            provider = settings.cloudProviderKind,
            modelId = settings.cloudModelId.ifBlank { settings.cloudProviderKind.defaultModel }
        )
        val supportsTools = detected.supportsTools

        var score = 40
        if (!readyCloud) score -= 35
        if (!telemetry.isNetworkConnected) score -= 30
        if (readyCloud && telemetry.isNetworkConnected && thermalConstrained) score += 35
        if (supportsTools) score += 5
        if (detected.supportsVision) score += 4
        if (measured != null && measured.avgTtftMs in 1L..1500L) score += 4

        val badge = when {
            !readyCloud -> ModelRecommendationBadge.NEEDS_SETUP
            !telemetry.isNetworkConnected -> ModelRecommendationBadge.NEEDS_SETUP
            else -> ModelRecommendationBadge.SUITABLE
        }

        val rationale = when {
            !settings.cloudUploadConsentGranted -> "Cloud transmission consent is OFF in Security/Privacy."
            settings.cloudApiKey.isBlank() -> "API key not configured for ${settings.cloudProviderKind.label}."
            !telemetry.isNetworkConnected -> "Device offline — cloud model unreachable."
            else -> buildString {
                append("${settings.cloudProviderKind.label} (${detected.tier.label}) • 0 MB local RAM • ")
                append(if (supportsTools) "Tools" else "Text")
                if (detected.supportsVision) append(" + Vision")
                if (measured != null) {
                    append(String.format(Locale.US, " • %.1f tok/s", measured.avgTokensPerSecond))
                }
            }
        }

        return ModelHardwareRecommendation(
            slotId = slot.id,
            displayName = slot.displayName,
            providerType = slot.providerType,
            runtimeFormat = slot.runtimeFormat,
            badge = badge,
            fitTier = HardwareFitTier.OPTIMAL,
            score = score,
            estimatedMemoryBytes = 0L,
            supportsToolCalling = supportsTools,
            measuredMetrics = measured,
            rationale = rationale
        )
    }

    fun evaluateLoadMemoryBudget(
        context: Context,
        incomingSlot: ModelSlot,
        currentlyLoadedSizesBytes: Map<RuntimeFormatId, Long>,
        contextTokens: Int = incomingSlot.contextWindow
    ): LoadBudgetVerdict {
        val diskBytes = incomingSlot.localFilePath?.let { path ->
            runCatching { File(path).length() }.getOrDefault(0L)
        }?.takeIf { it > 0L } ?: incomingSlot.expectedSizeBytes
        val estIncoming = incomingSlot.estimatedMemoryBytes(contextTokens).coerceAtLeast(diskBytes)

        val headroom = ThermalAndMemoryPolicy.assessMemoryHeadroom(context, estIncoming)
        val existingTotal = currentlyLoadedSizesBytes
            .filterKeys { it != incomingSlot.runtimeFormat }
            .values
            .sum()

        if (headroom.totalRamBytes > 0L && estIncoming > (headroom.totalRamBytes * 0.85).toLong()) {
            return LoadBudgetVerdict(
                allowedToLoad = false,
                mustUnloadExistingFirst = existingTotal > 0L,
                allowConcurrentMultiModel = false,
                estimatedIncomingRamBytes = estIncoming,
                availableRamBytes = headroom.availableRamBytes,
                reason = String.format(
                    Locale.US,
                    "Blocked: %s needs ~%.2f GB RAM which exceeds 85%% of device total %.1f GB RAM.",
                    incomingSlot.displayName,
                    estIncoming / (1024f * 1024f * 1024f),
                    headroom.totalRamBytes / (1024f * 1024f * 1024f)
                )
            )
        }

        val combinedNeeded = existingTotal + estIncoming + CONCURRENT_SAFETY_RESERVE_BYTES
        val allowConcurrent = existingTotal == 0L || (
            !headroom.isLowMemory &&
                headroom.availableRamBytes >= combinedNeeded
            )

        val mustUnload = existingTotal > 0L && !allowConcurrent
        val canFitAfterUnload = !headroom.isLowMemory || existingTotal > 0L

        return LoadBudgetVerdict(
            allowedToLoad = canFitAfterUnload,
            mustUnloadExistingFirst = mustUnload,
            allowConcurrentMultiModel = existingTotal > 0L && allowConcurrent,
            estimatedIncomingRamBytes = estIncoming,
            availableRamBytes = headroom.availableRamBytes,
            reason = when {
                mustUnload -> "Insufficient free RAM for concurrent models; unloading previous runtime before loading ${incomingSlot.displayName}."
                !canFitAfterUnload -> "System is under critical low-memory pressure (${headroom.availableRamBytes / (1024L * 1024L)} MB free)."
                else -> "Verified memory budget OK (${headroom.availableRamBytes / (1024L * 1024L)} MB free)."
            }
        )
    }

    private fun aggregateBenchmarksForSlot(
        slot: ModelSlot,
        benchmarks: List<ModelBenchmarkEntity>
    ): MeasuredModelMetrics? {
        val matching = benchmarks.filter {
            it.modelId == slot.id || it.modelName.equals(slot.displayName, ignoreCase = true)
        }
        if (matching.isEmpty()) return null
        val avgTtft = matching.map { it.ttftMs }.average().toLong()
        val avgTps = matching.map { it.tokensPerSecond.toDouble() }.average().toFloat()
        val lastPeak = matching.first().peakMemoryMb
        return MeasuredModelMetrics(
            sampleCount = matching.size,
            avgTtftMs = avgTtft,
            avgTokensPerSecond = avgTps,
            lastPeakMemoryMb = lastPeak
        )
    }

    private fun estimateRequiredContext(totalChars: Int, configuredMax: Int): Int {
        val estPromptTokens = (totalChars / 4) + 512
        return estPromptTokens.coerceIn(1024, configuredMax.coerceIn(1024, 32768))
    }

    private fun adaptContextWindow(
        requested: Int,
        telemetry: DeviceTelemetry,
        batterySnapshot: BatteryThermalSnapshot
    ): Int {
        var ctx = requested.coerceIn(1024, 8192)
        if (telemetry.availRamBytes in 1L..(1536L * 1024L * 1024L)) {
            ctx = ctx.coerceAtMost(2048)
        }
        if (batterySnapshot.isPowerSaveMode ||
            batterySnapshot.thermalStatusCode >= 3 ||
            (!batterySnapshot.isCharging && batterySnapshot.batteryPercent in 1..15)
        ) {
            ctx = ctx.coerceAtMost(2048)
        }
        return ctx
    }

    companion object {
        private const val CONCURRENT_SAFETY_RESERVE_BYTES = 2048L * 1024L * 1024L
    }
}
