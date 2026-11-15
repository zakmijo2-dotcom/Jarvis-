package com.example.ui.screens.models

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.ai.EngineLoadState
import com.example.core.ai.ModelRecommendationBadge
import com.example.core.hf.HfRepoDetail
import com.example.core.hf.HfRepoFile
import com.example.core.model.ArtifactState
import com.example.core.model.ArtifactTransferState
import com.example.core.model.CompatibilityReport
import com.example.core.model.DownloadQueueStatus
import com.example.core.model.ModelProviderType
import com.example.core.model.ModelSlot
import com.example.core.model.ModelStatus
import com.example.core.model.RuntimeFormatId
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.HudPanel
import com.example.ui.components.hairlineDp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JarvisThemeTokens
import java.util.Locale

@Composable
fun ModelManagerScreen(
    uiState: ModelManagerUiState,
    onSelectTab: (ModelHubTab) -> Unit,
    onSelectFilter: (ModelFilter) -> Unit,
    onSelectActiveModel: (String) -> Unit,
    onStartDownload: (ModelSlot, String, Boolean) -> Unit,
    onImportLocalFile: (ModelSlot, android.net.Uri) -> Unit,
    onVerifyArtifact: (ModelSlot) -> Unit,
    onDeleteArtifact: (ModelSlot) -> Unit,
    onCancelTransfer: () -> Unit,
    onClearTransferStatus: () -> Unit,
    onLoadModel: (ModelSlot) -> Unit,
    onReloadActiveModel: () -> Unit,
    onUnloadModel: () -> Unit,
    onRegisterCustomSlot: (String, ModelProviderType, String, String, Int) -> Unit,
    onDeleteCustomSlot: (String) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onUpdateAuthorFilter: (String) -> Unit,
    onUpdateManualRepoId: (String) -> Unit,
    onSelectDiscoverTagFilter: (String?) -> Unit,
    onSearchHuggingFace: () -> Unit,
    onOpenManualRepo: () -> Unit,
    onOpenRepoDetail: (String) -> Unit,
    onDismissRepoDetail: () -> Unit,
    onEnqueueHubDownload: (HfRepoDetail, HfRepoFile, CompatibilityReport) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onClearFinishedDownloads: () -> Unit,
    onClearBenchmarks: () -> Unit,
    onDismissHubNotice: () -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var downloadDialogSlot by remember { mutableStateOf<ModelSlot?>(null) }
    var pendingImportSlot by remember { mutableStateOf<ModelSlot?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val slot = pendingImportSlot
        pendingImportSlot = null
        if (uri != null && slot != null) {
            onImportLocalFile(slot, uri)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val activeQueueCount = uiState.downloadQueue.count {
        it.status == DownloadQueueStatus.DOWNLOADING ||
            it.status == DownloadQueueStatus.QUEUED ||
            it.status == DownloadQueueStatus.PAUSED
    }

    HudBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag("model_manager_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 840.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val headerPalette = JarvisThemeTokens.palette
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = headerPalette.sageContainer
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Memory,
                                    contentDescription = null,
                                    tint = headerPalette.onHue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.models_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = headerPalette.text
                        )
                    }
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        shape = CircleShape,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_model_slot_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.models_add_slot))
                    }
                }
            }

            item {
                val tabPalette = JarvisThemeTokens.palette
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 840.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ModelHubTab.entries) { tab ->
                        val label = if (tab == ModelHubTab.QUEUE && activeQueueCount > 0) {
                            "${tab.label} ($activeQueueCount)"
                        } else {
                            tab.label
                        }
                        FilterChip(
                            selected = uiState.selectedTab == tab,
                            onClick = { onSelectTab(tab) },
                            label = {
                                Text(text = label, style = MaterialTheme.typography.labelLarge)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tabPalette.sageContainer,
                                selectedLabelColor = tabPalette.onHue
                            ),
                            modifier = Modifier.testTag("hub_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(visible = uiState.hubNotice != null) {
                    HudPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 840.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.hubNotice.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onDismissHubNotice,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }

            item {
                EngineRuntimeHudCard(
                    uiState = uiState,
                    onReload = onReloadActiveModel,
                    onUnload = onUnloadModel
                )
            }

            val transfer = uiState.transferState
            if (transfer !is ArtifactTransferState.Idle) {
                item {
                    ArtifactTransferBanner(
                        transfer = transfer,
                        onCancel = onCancelTransfer,
                        onDismiss = onClearTransferStatus
                    )
                }
            }

            when (uiState.selectedTab) {
                ModelHubTab.INSTALLED -> {
                    uiState.activeModel?.let { active ->
                        item {
                            ActiveModelSummaryCard(
                                slot = active,
                                isLoaded = (uiState.engineLoadState as? EngineLoadState.Ready)?.modelId == active.id,
                                onLoad = { onLoadModel(active) },
                                onUnload = onUnloadModel
                            )
                        }
                    }

                    item {
                        val recPalette = JarvisThemeTokens.palette
                        HudPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 840.dp)
                                .testTag("model_storage_and_fit_card")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Storage & hardware fit",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = recPalette.text
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", uiState.installedStorageBytes / (1024.0 * 1024.0 * 1024.0))} GB installed",
                                    style = JarvisMonoTelemetryStyle,
                                    color = recPalette.skyAccent
                                )
                            }
                            if (uiState.hardwareRecommendations.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                val topRecs = uiState.hardwareRecommendations.values
                                    .filter { it.badge != com.example.core.ai.ModelRecommendationBadge.UNSUPPORTED }
                                    .sortedByDescending { it.score }
                                    .take(2)
                                if (topRecs.isEmpty()) {
                                    Text(
                                        text = "No supported LiteRT-LM models match current constraints yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = recPalette.textSecondary
                                    )
                                } else {
                                    topRecs.forEachIndexed { idx, rec ->
                                        if (idx > 0) EditorialHairlineDivider(modifier = Modifier.padding(vertical = 6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = rec.displayName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = recPalette.text
                                                )
                                                Text(
                                                    text = rec.rationale,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = recPalette.textSecondary
                                                )
                                            }
                                            HudBadge(
                                                text = rec.badge.label,
                                                color = recPalette.onHue,
                                                containerColor = when (rec.badge) {
                                                    ModelRecommendationBadge.RECOMMENDED,
                                                    ModelRecommendationBadge.SUITABLE -> recPalette.sageContainer
                                                    ModelRecommendationBadge.TIGHT_RAM,
                                                    ModelRecommendationBadge.THERMAL_THROTTLED -> recPalette.sandContainer
                                                    else -> recPalette.surfaceHigh
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 840.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val filterPalette = JarvisThemeTokens.palette
                            ModelFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = uiState.selectedFilter == filter,
                                    onClick = { onSelectFilter(filter) },
                                    label = {
                                        Text(
                                            text = filter.label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = filterPalette.sageContainer,
                                        selectedLabelColor = filterPalette.onHue
                                    ),
                                    modifier = Modifier.testTag("model_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }

                    items(uiState.models, key = { it.id }) { slot ->
                        val isLoaded = (uiState.engineLoadState as? EngineLoadState.Ready)?.modelId == slot.id
                        val isTransferring = (transfer as? ArtifactTransferState.Downloading)?.modelId == slot.id ||
                            (transfer as? ArtifactTransferState.Importing)?.modelId == slot.id ||
                            (transfer as? ArtifactTransferState.Verifying)?.modelId == slot.id

                        ModelSlotCard(
                            slot = slot,
                            isLoaded = isLoaded,
                            isTransferring = isTransferring,
                            onActivate = { onSelectActiveModel(slot.id) },
                            onDownload = { downloadDialogSlot = slot },
                            onImportFile = {
                                pendingImportSlot = slot
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            onLoadEngine = { onLoadModel(slot) },
                            onUnloadEngine = onUnloadModel,
                            onVerifyArtifact = { onVerifyArtifact(slot) },
                            onDeleteArtifact = { onDeleteArtifact(slot) },
                            onDeleteCustom = { onDeleteCustomSlot(slot.id) },
                            onInspectRepo = slot.sourceRepoId?.let { repoId -> { onOpenRepoDetail(repoId) } }
                        )
                    }

                    item {
                        HudPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 840.dp)
                        ) {
                            Text(
                                text = "Cloud providers",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.cloudProviders.forEachIndexed { index, provider ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            text = provider.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${provider.protocol} · ${provider.authRequirement}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    HudBadge(
                                        text = provider.phaseStatus,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (index < uiState.cloudProviders.lastIndex) {
                                    EditorialHairlineDivider()
                                }
                            }
                        }
                    }
                }

                ModelHubTab.DISCOVER -> {
                    item {
                        DiscoverSearchHeader(
                            discover = uiState.discover,
                            hasToken = uiState.settings.huggingFaceToken.isNotBlank(),
                            onQueryChange = onUpdateSearchQuery,
                            onAuthorChange = onUpdateAuthorFilter,
                            onManualRepoChange = onUpdateManualRepoId,
                            onSelectTagFilter = onSelectDiscoverTagFilter,
                            onSearch = onSearchHuggingFace,
                            onOpenManualRepo = onOpenManualRepo
                        )
                    }

                    items(uiState.discover.repos, key = { it.repoId }) { repo ->
                        HfRepoSummaryCard(
                            repo = repo,
                            onInspect = { onOpenRepoDetail(repo.repoId) }
                        )
                    }
                }

                ModelHubTab.QUEUE -> {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 840.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Download queue",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (uiState.downloadQueue.isNotEmpty()) {
                                TextButton(
                                    onClick = onClearFinishedDownloads,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear finished")
                                }
                            }
                        }
                    }

                    if (uiState.downloadQueue.isEmpty()) {
                        item {
                            HudPanel(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 840.dp)
                            ) {
                                Text(
                                    text = "No queued or active downloads. Open Discover to browse Hugging Face repositories and queue .litertlm files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(uiState.downloadQueue, key = { it.id }) { item ->
                            DownloadQueueCard(
                                item = item,
                                onPause = { onPauseDownload(item.id) },
                                onResume = {
                                    ensureNotificationPermissionIfNeeded()
                                    onResumeDownload(item.id)
                                },
                                onCancel = { onCancelDownload(item.id) }
                            )
                        }
                    }
                }

                ModelHubTab.BENCHMARKS -> {
                    item {
                        BenchmarkHistoryCard(
                            benchmarks = uiState.benchmarks,
                            formatAdapters = uiState.formatAdapters,
                            onClearBenchmarks = onClearBenchmarks,
                            activeModel = uiState.activeModel,
                            engineLoadState = uiState.engineLoadState,
                            nativeMemoryMb = uiState.nativeMemoryMb,
                            lastBenchmark = uiState.lastBenchmark
                        )
                    }
                }
            }
        }
    }

    val repoDetail = uiState.repoDetail
    if (repoDetail != null) {
        RepoDetailDialog(
            state = repoDetail,
            onDismiss = onDismissRepoDetail,
            onDownloadFile = { detail, file, report ->
                ensureNotificationPermissionIfNeeded()
                onEnqueueHubDownload(detail, file, report)
            }
        )
    }

    val targetDownload = downloadDialogSlot
    if (targetDownload != null) {
        HuggingFaceDownloadDialog(
            slot = targetDownload,
            savedToken = uiState.settings.huggingFaceToken,
            onDismiss = { downloadDialogSlot = null },
            onStartDownload = { token, persist ->
                onStartDownload(targetDownload, token, persist)
                downloadDialogSlot = null
            }
        )
    }

    if (showAddDialog) {
        RegisterModelSlotDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, engine, hint, ctx ->
                onRegisterCustomSlot(name, type, engine, hint, ctx)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ActiveModelSummaryCard(
    slot: ModelSlot,
    isLoaded: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit
) {
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .testTag("active_model_banner"),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val summaryPalette = JarvisThemeTokens.palette
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Selected model",
                    style = MaterialTheme.typography.labelMedium,
                    color = summaryPalette.sageAccent
                )
                Text(
                    text = slot.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOfNotNull(
                        slot.runtimeFormat.label,
                        slot.architecture,
                        slot.parameterCount,
                        slot.quantization
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (slot.artifactState == ArtifactState.READY && slot.runtimeFormat == RuntimeFormatId.LITERT_LM) {
                if (isLoaded) {
                    OutlinedButton(
                        onClick = onUnload,
                        shape = CircleShape,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) { Text("Unload") }
                } else {
                    Button(
                        onClick = onLoad,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = summaryPalette.sageContainer,
                            contentColor = summaryPalette.onHue
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) { Text("Load") }
                }
            } else {
                HudBadge(text = slot.artifactState.label)
            }
        }
    }
}

@Composable
private fun EngineRuntimeHudCard(
    uiState: ModelManagerUiState,
    onReload: () -> Unit,
    onUnload: () -> Unit
) {
    val palette = JarvisThemeTokens.palette
    val freeGb = uiState.availableStorageBytes / (1024f * 1024f * 1024f)
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .testTag("engine_runtime_hud_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LiteRT-LM runtime",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val statusColor = when (uiState.engineLoadState) {
                is EngineLoadState.Ready -> palette.successAccent
                is EngineLoadState.Error -> palette.errorAccent
                is EngineLoadState.Loading, is EngineLoadState.Unloaded -> palette.textSecondary
            }
            val statusLabel = when (val s = uiState.engineLoadState) {
                is EngineLoadState.Ready -> s.backendUsed
                is EngineLoadState.Loading -> "Loading"
                is EngineLoadState.Error -> "Error"
                is EngineLoadState.Unloaded -> "Unloaded"
            }
            HudBadge(text = statusLabel, color = statusColor)
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (val state = uiState.engineLoadState) {
            is EngineLoadState.Ready -> {
                Text(
                    text = "Loaded ${state.modelName} (${state.loadTimeMs} ms init)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            is EngineLoadState.Loading -> {
                Text(
                    text = "Initializing ${state.modelName} on ${state.targetBackend}…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is EngineLoadState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            is EngineLoadState.Unloaded -> {
                Text(
                    text = "No model loaded in memory. Context limit ${uiState.settings.contextLengthTokens}, max output ${uiState.settings.maxOutputTokens}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format(Locale.US, "Heap %.1f MB", uiState.nativeMemoryMb),
                style = JarvisMonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format(Locale.US, "Free %.2f GB", freeGb),
                style = JarvisMonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        uiState.lastBenchmark?.let { bench ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = String.format(
                    Locale.US,
                    "TTFT %d ms · %.1f tok/s · %d out / %d prefill (%s)",
                    bench.ttftMs,
                    bench.tokensPerSecond,
                    bench.outputTokens,
                    bench.prefillTokens,
                    bench.backendUsed
                ),
                style = JarvisMonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("runtime_hud_benchmark")
            )
        }

        if (uiState.engineLoadState is EngineLoadState.Ready) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReload,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("hud_reload_engine_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reload")
                }
                OutlinedButton(
                    onClick = onUnload,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("hud_unload_engine_button")
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Unload")
                }
            }
        }
    }
}

@Composable
private fun ArtifactTransferBanner(
    transfer: ArtifactTransferState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val isError = transfer is ArtifactTransferState.Error
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .testTag("artifact_transfer_banner"),
        containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    ) {
        when (transfer) {
            is ArtifactTransferState.Downloading -> {
                val dlMb = transfer.bytesDownloaded / (1024f * 1024f)
                val totMb = transfer.totalBytes / (1024f * 1024f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Downloading %.1f / %.1f MB (%.0f%%)",
                            dlMb,
                            totMb,
                            transfer.progress * 100f
                        ),
                        style = JarvisMonoTelemetryStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
                LinearProgressIndicator(
                    progress = { transfer.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is ArtifactTransferState.Importing -> {
                val copiedMb = transfer.bytesCopied / (1024f * 1024f)
                Text(
                    text = String.format(Locale.US, "Importing .litertlm: %.1f MB", copiedMb),
                    style = JarvisMonoTelemetryStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is ArtifactTransferState.Verifying -> {
                Text(
                    text = "Verifying SHA-256 checksum…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is ArtifactTransferState.Completed -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Model file verified and stored privately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Text("OK")
                    }
                }
            }
            is ArtifactTransferState.Error -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transfer.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Text("Dismiss")
                    }
                }
            }
            ArtifactTransferState.Idle -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelSlotCard(
    slot: ModelSlot,
    isLoaded: Boolean,
    isTransferring: Boolean,
    onActivate: () -> Unit,
    onDownload: () -> Unit,
    onImportFile: () -> Unit,
    onLoadEngine: () -> Unit,
    onUnloadEngine: () -> Unit,
    onVerifyArtifact: () -> Unit,
    onDeleteArtifact: () -> Unit,
    onDeleteCustom: () -> Unit,
    onInspectRepo: (() -> Unit)?
) {
    val palette = JarvisThemeTokens.palette
    val isLocal = slot.providerType == ModelProviderType.LOCAL_ON_DEVICE
    val statusBadgeColor = when {
        isLoaded || slot.artifactState == ArtifactState.READY -> palette.successAccent
        slot.artifactState == ArtifactState.CORRUPTED -> palette.errorAccent
        else -> palette.textSecondary
    }
    val statusBadgeText = when {
        isLoaded -> "Loaded"
        isLocal -> slot.artifactState.label
        else -> slot.status.label
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .testTag("model_card_${slot.id}"),
        shape = RoundedCornerShape(22.dp),
        color = palette.surface,
        border = BorderStroke(
            width = hairlineDp(),
            color = if (slot.isActive) palette.sageAccent else palette.hairline
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isLocal) Icons.Filled.Memory else Icons.Filled.CloudQueue,
                        contentDescription = null,
                        tint = if (slot.isActive) palette.sageAccent else palette.textSecondary
                    )
                    Column {
                        Text(
                            text = slot.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.text
                        )
                        Text(
                            text = slot.engineFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                }
                HudBadge(
                    text = statusBadgeText,
                    color = statusBadgeColor
                )
            }

            val metaParts = listOfNotNull(
                slot.providerType.label,
                slot.architecture,
                slot.parameterHint,
                "${slot.contextWindow / 1024}K ctx",
                slot.license?.let { "License: $it" },
                slot.expectedSha256?.let { "SHA256 ${it.take(10)}…" }
            )
            Text(
                text = metaParts.joinToString(" · "),
                style = JarvisMonoTelemetryStyle,
                color = palette.textSecondary
            )

            if (isLocal) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (slot.downloadUrl != null && slot.artifactState != ArtifactState.READY) {
                        Button(
                            onClick = onDownload,
                            enabled = !isTransferring,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.sageContainer,
                                contentColor = palette.onHue
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("download_model_${slot.id}")
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download")
                        }
                    }

                    OutlinedButton(
                        onClick = onImportFile,
                        enabled = !isTransferring,
                        shape = CircleShape,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("import_model_${slot.id}")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import .litertlm")
                    }

                    if (slot.artifactState == ArtifactState.READY) {
                        if (slot.runtimeFormat == RuntimeFormatId.LITERT_LM) {
                            if (isLoaded) {
                                OutlinedButton(
                                    onClick = onUnloadEngine,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("unload_model_${slot.id}")
                                ) {
                                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Unload")
                                }
                            } else {
                                Button(
                                    onClick = onLoadEngine,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.sageContainer,
                                        contentColor = palette.onHue
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("load_model_${slot.id}")
                                ) {
                                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Load")
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onVerifyArtifact,
                            shape = CircleShape,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("verify_model_${slot.id}")
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Verify")
                        }

                        IconButton(
                            onClick = onDeleteArtifact,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete local model file",
                                tint = palette.errorAccent
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onInspectRepo != null) {
                    TextButton(
                        onClick = onInspectRepo,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Model card")
                    }
                }
                if (slot.isCustom) {
                    IconButton(
                        onClick = onDeleteCustom,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete custom model slot",
                            tint = palette.errorAccent
                        )
                    }
                }
                if (slot.isActive) {
                    HudBadge(
                        text = "Selected",
                        color = palette.onHue,
                        containerColor = palette.sageContainer
                    )
                } else {
                    OutlinedButton(
                        onClick = onActivate,
                        shape = CircleShape,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("activate_model_${slot.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select")
                    }
                }
            }
        }
    }
}

@Composable
private fun HuggingFaceDownloadDialog(
    slot: ModelSlot,
    savedToken: String,
    onDismiss: () -> Unit,
    onStartDownload: (String, Boolean) -> Unit
) {
    var tokenInput by remember { mutableStateOf(savedToken) }
    var persistToken by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Download ${slot.displayName}",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Downloads the official .litertlm file (${slot.parameterHint}) into private app storage and verifies its SHA-256 checksum. Gemma 3 1B on Hugging Face requires accepting the Gemma license and entering a Read token, or you can import a local .litertlm file instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Hugging Face read token (hf_…)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = persistToken,
                        onCheckedChange = { persistToken = it }
                    )
                    Text(
                        text = "Encrypt and save token locally",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStartDownload(tokenInput, persistToken) }) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RegisterModelSlotDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, ModelProviderType, String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var engineFamily by remember { mutableStateOf("LiteRT-LM (.litertlm)") }
    var paramHint by remember { mutableStateOf("1B INT4") }
    var contextText by remember { mutableStateOf("4096") }
    var selectedType by remember { mutableStateOf(ModelProviderType.LOCAL_ON_DEVICE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add model slot",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelProviderType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = engineFamily,
                    onValueChange = { engineFamily = it },
                    label = { Text("Runtime") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = paramHint,
                    onValueChange = { paramHint = it },
                    label = { Text("Quantization or tier") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contextText,
                    onValueChange = { value -> contextText = value.filter { it.isDigit() } },
                    label = { Text("Context window tokens") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ctx = contextText.toIntOrNull() ?: 4096
                    onConfirm(name, selectedType, engineFamily, paramHint, ctx)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(name = "Models Light", showBackground = true)
@Preview(name = "Models Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModelManagerScreenPreview() {
    val sampleSlot = ModelSlot(
        id = "slot_gemma3_1b_int4",
        displayName = "Gemma 3 1B Instruct (Int4)",
        providerType = ModelProviderType.LOCAL_ON_DEVICE,
        engineFamily = "LiteRT-LM (.litertlm)",
        parameterHint = "1B INT4",
        contextWindow = 4096,
        status = ModelStatus.READY,
        isActive = true,
        isCustom = false,
        artifactState = ArtifactState.NOT_INSTALLED
    )
    JarvisTheme {
        ModelManagerScreen(
            uiState = ModelManagerUiState(
                models = listOf(sampleSlot),
                activeModel = sampleSlot,
                availableStorageBytes = 48L * 1024L * 1024L * 1024L
            ),
            onSelectTab = {},
            onSelectFilter = {},
            onSelectActiveModel = {},
            onStartDownload = { _, _, _ -> },
            onImportLocalFile = { _, _ -> },
            onVerifyArtifact = {},
            onDeleteArtifact = {},
            onCancelTransfer = {},
            onClearTransferStatus = {},
            onLoadModel = {},
            onReloadActiveModel = {},
            onUnloadModel = {},
            onRegisterCustomSlot = { _, _, _, _, _ -> },
            onDeleteCustomSlot = {},
            onUpdateSearchQuery = {},
            onUpdateAuthorFilter = {},
            onUpdateManualRepoId = {},
            onSelectDiscoverTagFilter = {},
            onSearchHuggingFace = {},
            onOpenManualRepo = {},
            onOpenRepoDetail = {},
            onDismissRepoDetail = {},
            onEnqueueHubDownload = { _, _, _ -> },
            onPauseDownload = {},
            onResumeDownload = {},
            onCancelDownload = {},
            onClearFinishedDownloads = {},
            onClearBenchmarks = {},
            onDismissHubNotice = {}
        )
    }
}
