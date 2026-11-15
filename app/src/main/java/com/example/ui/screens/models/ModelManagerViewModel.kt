package com.example.ui.screens.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ai.AiEngine
import com.example.core.ai.BatteryThermalSnapshot
import com.example.core.ai.EngineLoadState
import com.example.core.ai.GenerationBenchmark
import com.example.core.ai.ModelHardwareRecommendation
import com.example.core.ai.ModelRouter
import com.example.core.cloud.CloudProviderDescriptor
import com.example.core.cloud.CloudProviderRegistry
import com.example.core.hf.HuggingFaceApiClient
import com.example.core.hf.HfRepoDetail
import com.example.core.hf.HfRepoFile
import com.example.core.hf.HfRepoSummary
import com.example.core.model.ArtifactState
import com.example.core.model.ArtifactTransferState
import com.example.core.model.CompatibilityReport
import com.example.core.model.DownloadQueueItem
import com.example.core.model.DownloadQueueManager
import com.example.core.model.EnqueueDownloadRequest
import com.example.core.model.EnqueueResult
import com.example.core.model.ModelArtifactManager
import com.example.core.model.ModelCompatibilityEngine
import com.example.core.model.ModelFormatRegistry
import com.example.core.model.ModelManager
import com.example.core.model.ModelProviderType
import com.example.core.model.ModelSlot
import com.example.core.model.RuntimeFormatId
import com.example.core.security.SecureCredentialStore
import com.example.core.tools.AndroidTools
import com.example.data.local.db.ModelBenchmarkDao
import com.example.data.local.db.ModelBenchmarkEntity
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ModelFilter(val label: String) {
    ALL("All"),
    LOCAL("On-Device"),
    CLOUD("Cloud")
}

enum class ModelHubTab(val label: String) {
    INSTALLED("Installed"),
    DISCOVER("Discover"),
    QUEUE("Queue"),
    BENCHMARKS("Benchmarks")
}

data class DiscoverState(
    val searchQuery: String = "",
    val authorFilter: String = "",
    val manualRepoId: String = "",
    val selectedTagFilter: String? = "litert-lm",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val repos: List<HfRepoSummary> = emptyList(),
    val hasSearchedOnce: Boolean = false
)

data class RepoDetailState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val detail: HfRepoDetail? = null,
    val filesWithCompatibility: List<Pair<HfRepoFile, CompatibilityReport>> = emptyList()
)

data class ModelManagerUiState(
    val selectedTab: ModelHubTab = ModelHubTab.INSTALLED,
    val models: List<ModelSlot> = emptyList(),
    val activeModel: ModelSlot? = null,
    val selectedFilter: ModelFilter = ModelFilter.ALL,
    val cloudProviders: List<CloudProviderDescriptor> = emptyList(),
    val engineLoadState: EngineLoadState = EngineLoadState.Unloaded,
    val transferState: ArtifactTransferState = ArtifactTransferState.Idle,
    val lastBenchmark: GenerationBenchmark? = null,
    val nativeMemoryMb: Float = 0f,
    val availableStorageBytes: Long = 0L,
    val installedStorageBytes: Long = 0L,
    val hardwareRecommendations: Map<String, ModelHardwareRecommendation> = emptyMap(),
    val settings: JarvisSettings = JarvisSettings(),
    val discover: DiscoverState = DiscoverState(),
    val repoDetail: RepoDetailState? = null,
    val downloadQueue: List<DownloadQueueItem> = emptyList(),
    val benchmarks: List<ModelBenchmarkEntity> = emptyList(),
    val hubNotice: String? = null,
    val formatAdapters: List<com.example.core.model.RuntimeFormatAdapter> = emptyList()
)

class ModelManagerViewModel(
    private val modelManager: ModelManager,
    private val modelArtifactManager: ModelArtifactManager,
    private val downloadQueueManager: DownloadQueueManager,
    private val huggingFaceApiClient: HuggingFaceApiClient,
    private val compatibilityEngine: ModelCompatibilityEngine,
    private val formatRegistry: ModelFormatRegistry,
    private val androidTools: AndroidTools,
    private val modelBenchmarkDao: ModelBenchmarkDao,
    private val aiEngine: AiEngine,
    private val settingsRepository: SettingsRepository,
    private val cloudProviderRegistry: CloudProviderRegistry,
    private val modelRouter: ModelRouter = ModelRouter(formatRegistry = formatRegistry)
) : ViewModel() {

    private val selectedTab = MutableStateFlow(ModelHubTab.INSTALLED)
    private val selectedFilter = MutableStateFlow(ModelFilter.ALL)
    private val discoverState = MutableStateFlow(DiscoverState())
    private val repoDetailState = MutableStateFlow<RepoDetailState?>(null)
    private val hubNotice = MutableStateFlow<String?>(null)

    private val coreModelsFlow = combine(
        modelManager.observeModels(),
        selectedFilter,
        aiEngine.loadState,
        modelArtifactManager.transferState,
        aiEngine.lastBenchmark,
        aiEngine.nativeMemoryMb,
        settingsRepository.settingsFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allModels = values[0] as List<ModelSlot>
        val filter = values[1] as ModelFilter
        val loadState = values[2] as EngineLoadState
        val transfer = values[3] as ArtifactTransferState
        val benchmark = values[4] as GenerationBenchmark?
        val nativeMem = values[5] as Float
        val settings = values[6] as JarvisSettings
        CoreModelSnapshot(allModels, filter, loadState, transfer, benchmark, nativeMem, settings)
    }

    private val hubDataFlow = combine(
        selectedTab,
        discoverState,
        repoDetailState,
        downloadQueueManager.observeQueue(),
        modelBenchmarkDao.observeBenchmarks(),
        hubNotice
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        HubSnapshot(
            tab = values[0] as ModelHubTab,
            discover = values[1] as DiscoverState,
            repoDetail = values[2] as RepoDetailState?,
            queue = values[3] as List<DownloadQueueItem>,
            benchmarks = values[4] as List<ModelBenchmarkEntity>,
            notice = values[5] as String?
        )
    }

    val uiState: StateFlow<ModelManagerUiState> = combine(
        coreModelsFlow,
        hubDataFlow
    ) { core, hub ->
        val filtered = when (core.filter) {
            ModelFilter.ALL -> core.allModels
            ModelFilter.LOCAL -> core.allModels.filter { it.providerType == ModelProviderType.LOCAL_ON_DEVICE }
            ModelFilter.CLOUD -> core.allModels.filter { it.providerType == ModelProviderType.CLOUD_API }
        }
        val telemetry = runCatching { androidTools.readDeviceTelemetry() }.getOrNull()
        val recs = if (telemetry != null) {
            modelRouter.evaluateModels(
                models = core.allModels,
                settings = core.settings,
                telemetry = telemetry,
                batterySnapshot = BatteryThermalSnapshot(
                    batteryPercent = telemetry.batteryPercent,
                    batteryTempCelsius = telemetry.batteryTempCelsius,
                    isPowerSaveMode = false,
                    thermalStatusCode = telemetry.thermalCode,
                    thermalStatusLabel = telemetry.thermalStatusLabel
                ),
                benchmarks = hub.benchmarks
            ).associateBy { it.slotId }
        } else {
            emptyMap()
        }
        ModelManagerUiState(
            selectedTab = hub.tab,
            models = filtered,
            activeModel = core.allModels.firstOrNull { it.isActive },
            selectedFilter = core.filter,
            cloudProviders = cloudProviderRegistry.listProviders(),
            engineLoadState = core.loadState,
            transferState = core.transfer,
            lastBenchmark = core.benchmark,
            nativeMemoryMb = core.nativeMem,
            availableStorageBytes = modelArtifactManager.availableStorageBytes(),
            installedStorageBytes = modelArtifactManager.installedStorageFootprintBytes(),
            hardwareRecommendations = recs,
            settings = core.settings,
            discover = hub.discover,
            repoDetail = hub.repoDetail,
            downloadQueue = hub.queue,
            benchmarks = hub.benchmarks,
            hubNotice = hub.notice,
            formatAdapters = formatRegistry.adapters
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ModelManagerUiState(
            cloudProviders = cloudProviderRegistry.listProviders(),
            availableStorageBytes = modelArtifactManager.availableStorageBytes(),
            installedStorageBytes = modelArtifactManager.installedStorageFootprintBytes(),
            formatAdapters = formatRegistry.adapters
        )
    )

    init {
        viewModelScope.launch {
            modelManager.ensureSeeded()
            aiEngine.refreshMemoryUsage()
        }
    }

    fun selectTab(tab: ModelHubTab) {
        selectedTab.value = tab
        if (tab == ModelHubTab.DISCOVER && !discoverState.value.hasSearchedOnce && !discoverState.value.isLoading) {
            searchHuggingFace()
        }
    }

    fun setFilter(filter: ModelFilter) {
        selectedFilter.value = filter
    }

    fun selectActiveModel(id: String) {
        viewModelScope.launch {
            modelManager.setActiveModel(id)
            val target = modelManager.getModelById(id) ?: return@launch
            val currentLoaded = (aiEngine.loadState.value as? EngineLoadState.Ready)?.modelId
            if (target.artifactState == ArtifactState.READY &&
                target.runtimeFormat == RuntimeFormatId.LITERT_LM &&
                currentLoaded != null &&
                currentLoaded != target.id
            ) {
                aiEngine.loadModel(target, uiState.value.settings)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        discoverState.value = discoverState.value.copy(searchQuery = query)
    }

    fun updateAuthorFilter(author: String) {
        discoverState.value = discoverState.value.copy(authorFilter = author)
    }

    fun updateManualRepoId(repoId: String) {
        discoverState.value = discoverState.value.copy(manualRepoId = repoId)
    }

    fun selectDiscoverTagFilter(tag: String?) {
        discoverState.value = discoverState.value.copy(selectedTagFilter = tag)
        searchHuggingFace()
    }

    fun searchHuggingFace() {
        val current = discoverState.value
        discoverState.value = current.copy(isLoading = true, errorMessage = null, hasSearchedOnce = true)
        viewModelScope.launch {
            val token = uiState.value.settings.huggingFaceToken
            val result = huggingFaceApiClient.searchModels(
                query = current.searchQuery,
                author = current.authorFilter,
                filterTag = current.selectedTagFilter,
                token = token,
                limit = 25
            )
            result.onSuccess { repos ->
                discoverState.value = discoverState.value.copy(
                    isLoading = false,
                    repos = repos,
                    errorMessage = if (repos.isEmpty()) "No public Hugging Face repositories matched this filter." else null
                )
            }.onFailure { err ->
                discoverState.value = discoverState.value.copy(
                    isLoading = false,
                    errorMessage = err.localizedMessage ?: "Unable to reach Hugging Face Hub."
                )
            }
        }
    }

    fun openManualRepo() {
        val raw = discoverState.value.manualRepoId
        val validated = SecureCredentialStore.normalizeAndValidateRepoId(raw)
        if (validated.isFailure) {
            discoverState.value = discoverState.value.copy(
                errorMessage = validated.exceptionOrNull()?.message ?: "Invalid repository identifier."
            )
            return
        }
        openRepoDetail(validated.getOrThrow())
    }

    fun openRepoDetail(repoId: String) {
        repoDetailState.value = RepoDetailState(isLoading = true)
        viewModelScope.launch {
            val token = uiState.value.settings.huggingFaceToken
            val detailRes = huggingFaceApiClient.getModelDetail(repoId, token)
            if (detailRes.isFailure) {
                repoDetailState.value = RepoDetailState(
                    isLoading = false,
                    errorMessage = detailRes.exceptionOrNull()?.localizedMessage ?: "Failed to load repository details."
                )
                return@launch
            }
            val detail = detailRes.getOrThrow()
            val telemetry = androidTools.readDeviceTelemetry()
            val freeStorage = modelArtifactManager.availableStorageBytes()
            val hasToken = token.isNotBlank()

            val inspected = detail.files.map { file ->
                file to compatibilityEngine.inspect(
                    repoDetail = detail,
                    file = file,
                    telemetry = telemetry,
                    availableStorageBytes = freeStorage,
                    hasUserToken = hasToken
                )
            }.sortedByDescending { (_, report) ->
                when {
                    report.canRunInJarvisNow -> 3
                    report.canDownloadToDevice -> 2
                    report.formatId != RuntimeFormatId.UNKNOWN -> 1
                    else -> 0
                }
            }

            repoDetailState.value = RepoDetailState(
                isLoading = false,
                detail = detail,
                filesWithCompatibility = inspected
            )
        }
    }

    fun dismissRepoDetail() {
        repoDetailState.value = null
    }

    fun enqueueHubFileDownload(
        repo: HfRepoDetail,
        file: HfRepoFile,
        report: CompatibilityReport
    ) {
        if (!report.canDownloadToDevice) {
            hubNotice.value = report.summaryReason
            return
        }
        viewModelScope.launch {
            val token = uiState.value.settings.huggingFaceToken
            val req = EnqueueDownloadRequest(
                repoId = repo.repoId,
                fileName = file.fileName,
                displayName = "${repo.modelName} (${file.fileName.substringAfterLast('/')})",
                downloadUrl = file.downloadUrl,
                expectedSha256 = file.sha256,
                totalBytes = file.sizeBytes,
                runtimeFormat = report.formatId,
                architecture = repo.architecture,
                quantization = report.quantization,
                parameterCount = repo.parameterCount,
                license = repo.license,
                contextWindow = repo.contextLengthHint ?: 4096
            )
            when (val res = downloadQueueManager.enqueueAndStart(req, token)) {
                is EnqueueResult.Enqueued -> {
                    hubNotice.value = "Queued ${file.fileName} for background download."
                    repoDetailState.value = null
                    selectedTab.value = ModelHubTab.QUEUE
                }
                is EnqueueResult.Duplicate -> {
                    hubNotice.value = res.reason
                    repoDetailState.value = null
                    selectedTab.value = ModelHubTab.QUEUE
                }
                is EnqueueResult.Rejected -> {
                    hubNotice.value = res.reason
                }
            }
        }
    }

    fun pauseDownload(taskId: String) {
        downloadQueueManager.pauseDownload(taskId)
    }

    fun resumeDownload(taskId: String) {
        downloadQueueManager.startOrResumeTask(taskId, uiState.value.settings.huggingFaceToken)
    }

    fun cancelDownload(taskId: String) {
        downloadQueueManager.cancelDownload(taskId)
    }

    fun clearFinishedDownloads() {
        viewModelScope.launch {
            downloadQueueManager.clearFinished()
        }
    }

    fun clearBenchmarks() {
        viewModelScope.launch {
            modelBenchmarkDao.clearBenchmarks()
        }
    }

    fun dismissHubNotice() {
        hubNotice.value = null
    }

    fun startDownload(slot: ModelSlot, tokenInput: String, persistToken: Boolean) {
        viewModelScope.launch {
            val effectiveToken = tokenInput.trim().ifBlank { uiState.value.settings.huggingFaceToken }
            if (persistToken && tokenInput.isNotBlank()) {
                settingsRepository.setHuggingFaceToken(tokenInput)
            }
            modelArtifactManager.startDownload(slot, effectiveToken)
        }
    }

    fun importLocalModel(slot: ModelSlot, uri: Uri) {
        modelArtifactManager.startImportFromUri(slot, uri)
    }

    fun verifyArtifact(slot: ModelSlot) {
        viewModelScope.launch {
            modelArtifactManager.verifyInstalledArtifact(slot)
        }
    }

    fun deleteArtifact(slot: ModelSlot) {
        viewModelScope.launch {
            val loaded = aiEngine.loadState.value as? EngineLoadState.Ready
            if (loaded?.modelId == slot.id) {
                aiEngine.unloadModel()
            }
            modelArtifactManager.deleteArtifact(slot)
            if (slot.id.startsWith("hf_")) {
                modelManager.deleteModelSlot(slot.id)
            }
        }
    }

    fun cancelTransfer() {
        modelArtifactManager.cancelTransfer()
    }

    fun clearTransferStatus() {
        modelArtifactManager.clearTransferStatus()
    }

    fun loadModel(slot: ModelSlot) {
        if (slot.runtimeFormat != RuntimeFormatId.LITERT_LM) {
            hubNotice.value = "${slot.runtimeFormat.label} is unsupported in this APK build (no native runtime bundled)."
            return
        }
        viewModelScope.launch {
            modelManager.setActiveModel(slot.id)
            val result = aiEngine.loadModel(slot, uiState.value.settings)
            if (result.isFailure) {
                val state = aiEngine.loadState.value
                if (state is EngineLoadState.Error && state.isCorruptedFile) {
                    modelArtifactManager.markCorruptedAndClean(slot, state.message)
                }
            }
        }
    }

    fun reloadActiveModel() {
        viewModelScope.launch {
            val active = modelManager.getActiveModelOnce() ?: return@launch
            aiEngine.reloadModel(active, uiState.value.settings)
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            aiEngine.unloadModel()
        }
    }

    fun registerCustomSlot(
        displayName: String,
        providerType: ModelProviderType,
        engineFamily: String,
        parameterHint: String,
        contextWindow: Int
    ) {
        viewModelScope.launch {
            modelManager.registerCustomSlot(
                displayName = displayName,
                providerType = providerType,
                engineFamily = engineFamily,
                parameterHint = parameterHint,
                contextWindow = contextWindow
            )
        }
    }

    fun deleteCustomSlot(id: String) {
        viewModelScope.launch {
            val slot = modelManager.getModelById(id)
            if (slot != null) {
                val loaded = aiEngine.loadState.value as? EngineLoadState.Ready
                if (loaded?.modelId == slot.id) {
                    aiEngine.unloadModel()
                }
                modelArtifactManager.deleteArtifact(slot)
            }
            modelManager.deleteCustomSlot(id)
        }
    }

    private data class CoreModelSnapshot(
        val allModels: List<ModelSlot>,
        val filter: ModelFilter,
        val loadState: EngineLoadState,
        val transfer: ArtifactTransferState,
        val benchmark: GenerationBenchmark?,
        val nativeMem: Float,
        val settings: JarvisSettings
    )

    private data class HubSnapshot(
        val tab: ModelHubTab,
        val discover: DiscoverState,
        val repoDetail: RepoDetailState?,
        val queue: List<DownloadQueueItem>,
        val benchmarks: List<ModelBenchmarkEntity>,
        val notice: String?
    )

    companion object {
        fun factory(
            modelManager: ModelManager,
            modelArtifactManager: ModelArtifactManager,
            downloadQueueManager: DownloadQueueManager,
            huggingFaceApiClient: HuggingFaceApiClient,
            compatibilityEngine: ModelCompatibilityEngine,
            formatRegistry: ModelFormatRegistry,
            androidTools: AndroidTools,
            modelBenchmarkDao: ModelBenchmarkDao,
            aiEngine: AiEngine,
            settingsRepository: SettingsRepository,
            cloudProviderRegistry: CloudProviderRegistry,
            modelRouter: ModelRouter = ModelRouter(formatRegistry = formatRegistry)
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ModelManagerViewModel(
                    modelManager = modelManager,
                    modelArtifactManager = modelArtifactManager,
                    downloadQueueManager = downloadQueueManager,
                    huggingFaceApiClient = huggingFaceApiClient,
                    compatibilityEngine = compatibilityEngine,
                    formatRegistry = formatRegistry,
                    androidTools = androidTools,
                    modelBenchmarkDao = modelBenchmarkDao,
                    aiEngine = aiEngine,
                    settingsRepository = settingsRepository,
                    cloudProviderRegistry = cloudProviderRegistry,
                    modelRouter = modelRouter
                ) as T
            }
        }
    }
}
