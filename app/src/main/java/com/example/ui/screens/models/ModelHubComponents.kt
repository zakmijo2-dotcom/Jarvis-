package com.example.ui.screens.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.hf.HfRepoDetail
import com.example.core.hf.HfRepoFile
import com.example.core.hf.HfRepoSummary
import com.example.core.model.CheckStatus
import com.example.core.model.CompatibilityReport
import com.example.core.model.CompatibilityVerdict
import com.example.core.model.DownloadQueueItem
import com.example.core.model.DownloadQueueStatus
import com.example.core.model.RuntimeFormatAdapter
import com.example.core.model.RuntimeFormatId
import com.example.data.local.db.ModelBenchmarkEntity
import com.example.ui.components.HudBadge
import com.example.ui.components.HudPanel
import com.example.ui.theme.NominalGreen
import com.example.ui.theme.SignalAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverSearchHeader(
    discover: DiscoverState,
    hasToken: Boolean,
    onQueryChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onManualRepoChange: (String) -> Unit,
    onSelectTagFilter: (String?) -> Unit,
    onSearch: () -> Unit,
    onOpenManualRepo: () -> Unit
) {
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .testTag("hf_discover_header")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HUGGING FACE MODEL HUB",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            HudBadge(
                text = if (hasToken) "TOKEN ENCRYPTED" else "PUBLIC ACCESS",
                color = if (hasToken) NominalGreen else MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = discover.searchQuery,
            onValueChange = onQueryChange,
            label = { Text("Search model name or keywords (e.g. gemma, qwen)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hf_search_query_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = discover.authorFilter,
                onValueChange = onAuthorChange,
                label = { Text("Author / Org (optional)") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("hf_author_input")
            )
            Button(
                onClick = onSearch,
                enabled = !discover.isLoading,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("hf_search_button")
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Search")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                "litert-lm" to "LiteRT-LM",
                null to "All Repos",
                "gguf" to "GGUF (Preview)",
                "text-generation" to "Text Gen"
            )
            presets.forEach { (tag, label) ->
                FilterChip(
                    selected = discover.selectedTagFilter == tag,
                    onClick = { onSelectTagFilter(tag) },
                    label = { Text(label) }
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )

        Text(
            text = "Open Repository ID Directly",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = discover.manualRepoId,
                onValueChange = onManualRepoChange,
                label = { Text("owner/repo (e.g. litert-community/Gemma3-1B-IT)") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("hf_manual_repo_input")
            )
            OutlinedButton(
                onClick = onOpenManualRepo,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("hf_open_manual_repo_button")
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Inspect")
            }
        }

        if (discover.isLoading) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        discover.errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HfRepoSummaryCard(
    repo: HfRepoSummary,
    onInspect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .clickable(onClick = onInspect)
            .testTag("hf_repo_card_${repo.repoId}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.repoId,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Author: ${repo.author.ifBlank { "Community" }} • ${formatCompactCount(repo.downloads)} downloads • ${repo.likes} likes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (repo.isGated) {
                    HudBadge(text = "GATED", color = SignalAmber)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repo.libraryName?.let {
                    HudBadge(text = it.uppercase(Locale.US), color = MaterialTheme.colorScheme.secondary)
                }
                repo.pipelineTag?.let {
                    HudBadge(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                repo.license?.let {
                    HudBadge(text = "License: $it", color = MaterialTheme.colorScheme.primary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onInspect,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Inspect Files & Compatibility")
                }
            }
        }
    }
}

@Composable
fun DownloadQueueCard(
    item: DownloadQueueItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val badgeColor = when (item.status) {
        DownloadQueueStatus.COMPLETED -> NominalGreen
        DownloadQueueStatus.DOWNLOADING, DownloadQueueStatus.VERIFYING -> MaterialTheme.colorScheme.primary
        DownloadQueueStatus.PAUSED, DownloadQueueStatus.QUEUED -> SignalAmber
        DownloadQueueStatus.FAILED, DownloadQueueStatus.CANCELLED -> MaterialTheme.colorScheme.error
    }

    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .testTag("download_queue_card_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.repoId} • ${item.runtimeFormat.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HudBadge(text = item.status.label.uppercase(Locale.US), color = badgeColor)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (item.status == DownloadQueueStatus.DOWNLOADING ||
            item.status == DownloadQueueStatus.PAUSED ||
            item.status == DownloadQueueStatus.VERIFYING
        ) {
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        val dlMb = item.downloadedBytes / (1024f * 1024f)
        val totalMb = item.totalBytes / (1024f * 1024f)
        val remMb = item.remainingBytes / (1024f * 1024f)
        val speedMb = item.speedBytesPerSec / (1024f * 1024f)

        val statsText = when (item.status) {
            DownloadQueueStatus.DOWNLOADING -> String.format(
                Locale.US,
                "%.1f / %.1f MB (%.0f%%) • %.2f MB/s • %.1f MB left%s",
                dlMb,
                totalMb,
                item.progress * 100f,
                speedMb,
                remMb,
                if (item.etaSeconds >= 0L) " • ETA ${formatDuration(item.etaSeconds)}" else ""
            )
            DownloadQueueStatus.PAUSED -> String.format(
                Locale.US,
                "Paused at %.1f / %.1f MB • %s",
                dlMb,
                totalMb,
                if (item.supportsResume) "HTTP Range resume supported" else "Will restart if resumed"
            )
            DownloadQueueStatus.VERIFYING -> "Verifying SHA-256 checksum…"
            DownloadQueueStatus.COMPLETED -> String.format(Locale.US, "Verified %.1f MB stored offline", dlMb)
            DownloadQueueStatus.FAILED -> item.errorMessage ?: "Download failed"
            DownloadQueueStatus.CANCELLED -> "Download cancelled"
            DownloadQueueStatus.QUEUED -> "Waiting to start…"
        }

        Text(
            text = statsText,
            style = MaterialTheme.typography.labelMedium,
            color = if (item.status == DownloadQueueStatus.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            if (item.status == DownloadQueueStatus.DOWNLOADING || item.status == DownloadQueueStatus.QUEUED) {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pause")
                }
            }
            if (item.status == DownloadQueueStatus.PAUSED || item.status == DownloadQueueStatus.FAILED) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (item.status == DownloadQueueStatus.PAUSED) "Resume" else "Retry")
                }
            }
            if (item.status != DownloadQueueStatus.COMPLETED && item.status != DownloadQueueStatus.CANCELLED) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun BenchmarkHistoryCard(
    benchmarks: List<ModelBenchmarkEntity>,
    formatAdapters: List<RuntimeFormatAdapter>,
    onClearBenchmarks: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HudPanel(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "MODEL BENCHMARK HISTORY",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (benchmarks.isNotEmpty()) {
                    TextButton(onClick = onClearBenchmarks) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (benchmarks.isEmpty()) {
                Text(
                    text = "No inference runs recorded yet. Send prompts using a loaded LiteRT-LM model to measure TTFT, decode tok/s, and native heap memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                benchmarks.forEach { entry ->
                    val timeLabel = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(entry.timestamp))
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = entry.modelName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = String.format(
                                Locale.US,
                                "TTFT %d ms • %.1f tok/s • %d out / %d prefill • %.1f MB heap • %s",
                                entry.ttftMs,
                                entry.tokensPerSecond,
                                entry.outputTokens,
                                entry.prefillTokens,
                                entry.nativeHeapMb,
                                entry.backendUsed
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }
        }

        HudPanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "RUNTIME FORMAT ADAPTERS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            formatAdapters.forEach { adapter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = adapter.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = adapter.runtimeNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HudBadge(
                        text = if (adapter.isExecutionSupported) "ACTIVE" else "PREPARED",
                        color = if (adapter.isExecutionSupported) NominalGreen else SignalAmber
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepoDetailDialog(
    state: RepoDetailState,
    onDismiss: () -> Unit,
    onDownloadFile: (HfRepoDetail, HfRepoFile, CompatibilityReport) -> Unit
) {
    var showReadme by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = state.detail?.repoId ?: "Repository Inspection",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                when {
                    state.isLoading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Fetching Hugging Face metadata & running compatibility inspection…",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    state.errorMessage != null -> {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    state.detail != null -> {
                        val detail = state.detail
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                HudBadge(text = "Arch: ${detail.architecture}", color = MaterialTheme.colorScheme.primary)
                                HudBadge(text = "Params: ${detail.parameterCount}", color = MaterialTheme.colorScheme.secondary)
                                HudBadge(text = "Quant: ${detail.quantizationSummary}", color = MaterialTheme.colorScheme.tertiary)
                                HudBadge(text = "License: ${detail.license}", color = NominalGreen)
                                if (detail.isGated) {
                                    HudBadge(text = "GATED (${detail.gatedMode ?: "manual"})", color = SignalAmber)
                                }
                            }

                            Text(
                                text = "Tasks: ${detail.supportedTasks.joinToString(", ")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            detail.gatedPrompt?.let { prompt ->
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = SignalAmber, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = prompt,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            if (!detail.modelCardMarkdown.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { showReadme = !showReadme },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (showReadme) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (showReadme) "Hide Model Card (README)" else "View Model Card (README)")
                                }
                                if (showReadme) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = detail.modelCardMarkdown,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "REPOSITORY FILES & COMPATIBILITY AUDIT",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )

                            state.filesWithCompatibility.forEach { (file, report) ->
                                FileCompatibilityRow(
                                    file = file,
                                    report = report,
                                    onDownload = { onDownloadFile(detail, file, report) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun FileCompatibilityRow(
    file: HfRepoFile,
    report: CompatibilityReport,
    onDownload: () -> Unit
) {
    var expandedChecks by remember { mutableStateOf(false) }
    val verdictColor = when (report.verdict) {
        CompatibilityVerdict.READY_TO_INSTALL -> NominalGreen
        CompatibilityVerdict.INSTALLABLE_WITH_WARNING, CompatibilityVerdict.PREPARED_RUNTIME_ONLY -> SignalAmber
        CompatibilityVerdict.BLOCKED -> MaterialTheme.colorScheme.error
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp), tint = verdictColor)
                    Column {
                        Text(
                            text = file.fileName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatFileSize(file.sizeBytes)} • ${report.formatId.label} • Quant: ${report.quantization}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { expandedChecks = !expandedChecks }) {
                    Icon(
                        imageVector = if (expandedChecks) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle compatibility details"
                    )
                }
            }

            HudBadge(text = report.verdict.label.uppercase(Locale.US), color = verdictColor)

            Text(
                text = report.summaryReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expandedChecks) {
                report.checks.forEach { check ->
                    val icon = when (check.status) {
                        CheckStatus.PASS -> Icons.Filled.CheckCircle
                        CheckStatus.WARN -> Icons.Filled.Warning
                        CheckStatus.BLOCK -> Icons.Filled.Block
                    }
                    val color = when (check.status) {
                        CheckStatus.PASS -> NominalGreen
                        CheckStatus.WARN -> SignalAmber
                        CheckStatus.BLOCK -> MaterialTheme.colorScheme.error
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${check.category}: ${check.detail}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (report.canDownloadToDevice) {
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("hf_download_file_${file.fileName}")
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (report.canRunInJarvisNow) "Download & Install"
                        else "Download (${report.formatId.label})"
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Size unknown"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return String.format(Locale.US, "%.2f GB", gb)
    val mb = bytes / (1024.0 * 1024.0)
    if (mb >= 1.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}

private fun formatCompactCount(count: Long): String = when {
    count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> "$count"
}

private fun formatDuration(seconds: Long): String {
    if (seconds < 60L) return "${seconds}s"
    val mins = seconds / 60L
    val secs = seconds % 60L
    return "${mins}m ${secs}s"
}
