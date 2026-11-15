package com.example.core.model

import com.example.data.local.db.ModelDao
import com.example.data.local.db.ModelEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

enum class ModelProviderType(val label: String) {
    LOCAL_ON_DEVICE("On-Device"),
    CLOUD_API("Cloud API")
}

enum class ModelStatus(val label: String) {
    PHASE_2_PENDING("Unsupported / Legacy"),
    PHASE_3_PENDING("Unconfigured"),
    UNSUPPORTED("Unsupported Format"),
    UNCONFIGURED("Unconfigured"),
    READY("Ready")
}

enum class ArtifactState(val label: String) {
    NOT_INSTALLED("Not Installed"),
    DOWNLOADING("Downloading"),
    VERIFYING("Verifying SHA-256"),
    READY("Verified Ready"),
    CORRUPTED("Corrupted")
}

data class ModelSlot(
    val id: String,
    val displayName: String,
    val providerType: ModelProviderType,
    val engineFamily: String,
    val parameterHint: String,
    val contextWindow: Int,
    val status: ModelStatus,
    val isActive: Boolean,
    val isCustom: Boolean,
    val localFilePath: String? = null,
    val downloadUrl: String? = null,
    val expectedSha256: String? = null,
    val expectedSizeBytes: Long = 0L,
    val artifactState: ArtifactState = ArtifactState.NOT_INSTALLED,
    val sourceRepoId: String? = null,
    val license: String? = null,
    val architecture: String? = null,
    val quantization: String? = null,
    val parameterCount: String? = null,
    val runtimeFormat: RuntimeFormatId = RuntimeFormatId.LITERT_LM
) {
    fun estimatedMemoryBytes(contextTokens: Int = contextWindow): Long {
        if (providerType == ModelProviderType.CLOUD_API) return 0L
        val diskBytes = localFilePath?.let { path ->
            runCatching { java.io.File(path).length() }.getOrDefault(0L)
        }?.takeIf { it > 0L } ?: expectedSizeBytes
        if (diskBytes <= 0L) return 0L
        return ModelFormatRegistry().resolveAdapter("model.${runtimeFormat.defaultExtension()}")
            ?.estimateMemoryBytes(diskBytes, contextTokens)
            ?: (diskBytes + 384L * 1024L * 1024L)
    }

    private fun RuntimeFormatId.defaultExtension(): String = when (this) {
        RuntimeFormatId.LITERT_LM -> "litertlm"
        RuntimeFormatId.GGUF_LLAMA_CPP -> "gguf"
        RuntimeFormatId.LITERT_TFLITE -> "tflite"
        RuntimeFormatId.MEDIAPIPE_TASK -> "task"
        RuntimeFormatId.SAFETENSORS_RAW -> "safetensors"
        RuntimeFormatId.UNKNOWN -> "bin"
    }
}

interface ModelManager {
    fun observeModels(): Flow<List<ModelSlot>>
    fun observeActiveModel(): Flow<ModelSlot?>
    suspend fun ensureSeeded()
    suspend fun getActiveModelOnce(): ModelSlot?
    suspend fun getModelById(id: String): ModelSlot?
    suspend fun setActiveModel(id: String)
    suspend fun updateArtifactState(
        id: String,
        localFilePath: String?,
        artifactState: ArtifactState,
        status: ModelStatus
    )
    suspend fun upsertHubModelSlot(slot: ModelSlot)
    suspend fun registerCustomSlot(
        displayName: String,
        providerType: ModelProviderType,
        engineFamily: String,
        parameterHint: String,
        contextWindow: Int
    )
    suspend fun deleteCustomSlot(id: String)
    suspend fun deleteModelSlot(id: String)
}

class RoomModelManager(
    private val modelDao: ModelDao
) : ModelManager {

    companion object {
        const val GEMMA_3_1B_ID = "slot_gemma3_1b_int4"
        const val GEMMA_3_1B_URL =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm"
        const val GEMMA_3_1B_SHA256 =
            "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be"
        const val GEMMA_3_1B_EXPECTED_BYTES = 612_368_384L // ~584 MiB
    }

    override fun observeModels(): Flow<List<ModelSlot>> =
        modelDao.observeAllModels().map { list -> list.map { it.toDomain() } }

    override fun observeActiveModel(): Flow<ModelSlot?> =
        modelDao.observeActiveModel().map { it?.toDomain() }

    override suspend fun ensureSeeded() {
        val existingGemma = modelDao.getModelById(GEMMA_3_1B_ID)
        if (existingGemma == null) {
            val hasAnyModels = modelDao.countModels() > 0
            val gemmaEntry = ModelEntryEntity(
                id = GEMMA_3_1B_ID,
                displayName = "Gemma 3 1B Instruct (Int4)",
                providerType = ModelProviderType.LOCAL_ON_DEVICE.name,
                engineFamily = "LiteRT-LM (OpenCL GPU / CPU)",
                parameterHint = "1B INT4 (~584 MB)",
                contextWindow = 4096,
                status = ModelStatus.UNCONFIGURED.name,
                isActive = true,
                isCustom = false,
                localFilePath = null,
                downloadUrl = GEMMA_3_1B_URL,
                expectedSha256 = GEMMA_3_1B_SHA256,
                expectedSizeBytes = GEMMA_3_1B_EXPECTED_BYTES,
                artifactState = ArtifactState.NOT_INSTALLED.name,
                sourceRepoId = "litert-community/Gemma3-1B-IT",
                license = "gemma",
                architecture = "Gemma 3",
                quantization = "INT4",
                parameterCount = "1B",
                runtimeFormat = RuntimeFormatId.LITERT_LM.name
            )
            if (!hasAnyModels) {
                modelDao.insertAll(
                    listOf(
                        gemmaEntry,
                        ModelEntryEntity(
                            id = "slot_gemini_cloud",
                            displayName = "Gemini Cloud Core",
                            providerType = ModelProviderType.CLOUD_API.name,
                            engineFamily = "Cloud Provider API",
                            parameterHint = "Cloud Multimodal",
                            contextWindow = 1048576,
                            status = ModelStatus.UNCONFIGURED.name,
                            isActive = false,
                            isCustom = false
                        ),
                        ModelEntryEntity(
                            id = "slot_custom_litertlm",
                            displayName = "Custom LiteRT-LM Bundle",
                            providerType = ModelProviderType.LOCAL_ON_DEVICE.name,
                            engineFamily = "LiteRT-LM (.litertlm)",
                            parameterHint = "User Imported",
                            contextWindow = 4096,
                            status = ModelStatus.UNCONFIGURED.name,
                            isActive = false,
                            isCustom = false,
                            artifactState = ArtifactState.NOT_INSTALLED.name,
                            runtimeFormat = RuntimeFormatId.LITERT_LM.name
                        )
                    )
                )
            } else {
                modelDao.upsertModel(gemmaEntry)
                modelDao.setActiveModel(GEMMA_3_1B_ID)
            }
        }
    }

    override suspend fun getActiveModelOnce(): ModelSlot? =
        modelDao.getActiveModelOnce()?.toDomain()

    override suspend fun getModelById(id: String): ModelSlot? =
        modelDao.getModelById(id)?.toDomain()

    override suspend fun setActiveModel(id: String) {
        modelDao.setActiveModel(id)
    }

    override suspend fun updateArtifactState(
        id: String,
        localFilePath: String?,
        artifactState: ArtifactState,
        status: ModelStatus
    ) {
        modelDao.updateArtifactState(
            id = id,
            localFilePath = localFilePath,
            artifactState = artifactState.name,
            status = status.name
        )
    }

    override suspend fun upsertHubModelSlot(slot: ModelSlot) {
        modelDao.upsertModel(slot.toEntity())
    }

    override suspend fun registerCustomSlot(
        displayName: String,
        providerType: ModelProviderType,
        engineFamily: String,
        parameterHint: String,
        contextWindow: Int
    ) {
        val sanitizedName = displayName.trim()
        if (sanitizedName.isEmpty()) return
        modelDao.upsertModel(
            ModelEntryEntity(
                id = "custom_${UUID.randomUUID()}",
                displayName = sanitizedName,
                providerType = providerType.name,
                engineFamily = engineFamily.trim().ifBlank { "LiteRT-LM (.litertlm)" },
                parameterHint = parameterHint.trim().ifBlank { "Custom" },
                contextWindow = contextWindow.coerceIn(1024, 2_000_000),
                status = ModelStatus.UNCONFIGURED.name,
                isActive = false,
                isCustom = true,
                artifactState = ArtifactState.NOT_INSTALLED.name,
                runtimeFormat = RuntimeFormatId.LITERT_LM.name
            )
        )
    }

    override suspend fun deleteCustomSlot(id: String) {
        modelDao.deleteCustomModel(id)
    }

    override suspend fun deleteModelSlot(id: String) {
        modelDao.deleteModelById(id)
    }

    private fun ModelSlot.toEntity(): ModelEntryEntity = ModelEntryEntity(
        id = id,
        displayName = displayName,
        providerType = providerType.name,
        engineFamily = engineFamily,
        parameterHint = parameterHint,
        contextWindow = contextWindow,
        status = status.name,
        isActive = isActive,
        isCustom = isCustom,
        localFilePath = localFilePath,
        downloadUrl = downloadUrl,
        expectedSha256 = expectedSha256,
        expectedSizeBytes = expectedSizeBytes,
        artifactState = artifactState.name,
        sourceRepoId = sourceRepoId,
        license = license,
        architecture = architecture,
        quantization = quantization,
        parameterCount = parameterCount,
        runtimeFormat = runtimeFormat.name
    )

    private fun ModelEntryEntity.toDomain(): ModelSlot = ModelSlot(
        id = id,
        displayName = displayName,
        providerType = runCatching { ModelProviderType.valueOf(providerType) }
            .getOrDefault(ModelProviderType.LOCAL_ON_DEVICE),
        engineFamily = engineFamily,
        parameterHint = parameterHint,
        contextWindow = contextWindow,
        status = runCatching { ModelStatus.valueOf(status) }
            .getOrDefault(ModelStatus.UNCONFIGURED),
        isActive = isActive,
        isCustom = isCustom,
        localFilePath = localFilePath,
        downloadUrl = downloadUrl,
        expectedSha256 = expectedSha256,
        expectedSizeBytes = expectedSizeBytes,
        artifactState = runCatching { ArtifactState.valueOf(artifactState) }
            .getOrDefault(ArtifactState.NOT_INSTALLED),
        sourceRepoId = sourceRepoId,
        license = license,
        architecture = architecture,
        quantization = quantization,
        parameterCount = parameterCount,
        runtimeFormat = runCatching { RuntimeFormatId.valueOf(runtimeFormat) }
            .getOrDefault(RuntimeFormatId.LITERT_LM)
    )
}
