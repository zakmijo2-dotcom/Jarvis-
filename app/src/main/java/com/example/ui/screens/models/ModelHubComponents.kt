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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.example.core.ai.EngineLoadState
import com.example.core.ai.GenerationBenchmark
import com.example.core.hf.HfRepoDetail
import com.example.core.hf.HfRepoFile
import com.example.core.hf.HfRepoSummary
import com.example.core.model.CheckStatus
import com.example.core.model.CompatibilityReport
import com.example.core.model.CompatibilityVerdict
import com.example.core.model.DownloadQueueItem
import com.example.core.model.DownloadQueueStatus
import com.example.core.model.ModelSlot
import com.example.core.model.RuntimeFormatAdapter
import com.example.data.local.db.ModelBenchmarkEntity
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.components.HudPanel
import com.example.ui.components.hairlineDp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import com.example.ui.theme.JarvisMonoMetricStyle
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisThemeTokens
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
    val palette = JarvisThemeTokens.palette
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
                text = "Hugging Face",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            HudBadge(
                text = if (hasToken) "Token saved" else "Public access",
                color = if (hasToken) palette.onHue else MaterialTheme.colorScheme.onSurfaceVariant,
                containerColor = if (hasToken) palette.sageContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = discover.searchQuery,
            onValueChange = onQueryChange,
            label = { Text("Search models (e.g. gemma, qwen)") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
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
                label = { Text("Author or org (optional)") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .weight(1f)
                    .testTag("hf_author_input")
            )
            Button(
                onClick = onSearch,
                enabled = !discover.isLoading,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.sageContainer,
                    contentColor = palette.onHue
                ),
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
                "litert-lm" to "LiteRT-LM (.litertlm)",
                null to "All repositories",
                "text-generation" to "Text generation"
            )
            presets.forEach { (tag, label) ->
                FilterChip(
                    selected = discover.selectedTagFilter == tag,
                    onClick = { onSelectTagFilter(tag) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.sageContainer,
                        selectedLabelColor = palette.onHue
                    )
                )
            }
        }

        EditorialHairlineDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = "Open repository directly",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
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
                shape = MaterialTheme.shapes.small,
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
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Inspect")
            }
        }

        if (discover.isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
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
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(hairlineDp(), MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${repo.author.ifBlank { "Community" }} · ${formatCompactCount(repo.downloads)} downloads · ${repo.likes} likes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (repo.isGated) {
                    HudBadge(text = "Gated", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repo.libraryName?.let {
                    HudBadge(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                repo.pipelineTag?.let {
                    HudBadge(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                repo.license?.let {
                    HudBadge(
                        text = "License: $it",
                        color = JarvisThemeTokens.palette.onHue,
                        containerColor = JarvisThemeTokens.palette.sageContainer
                    )
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
                    Text("Inspect files")
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
    val palette = JarvisThemeTokens.palette
    val badgeColor = when (item.status) {
        DownloadQueueStatus.COMPLETED, DownloadQueueStatus.DOWNLOADING, DownloadQueueStatus.VERIFYING ->
            palette.onHue
        DownloadQueueStatus.PAUSED, DownloadQueueStatus.QUEUED ->
            MaterialTheme.colorScheme.onSurfaceVariant
        DownloadQueueStatus.FAILED, DownloadQueueStatus.CANCELLED ->
            palette.onHue
    }
    val badgeContainer = when (item.status) {
        DownloadQueueStatus.COMPLETED, DownloadQueueStatus.DOWNLOADING, DownloadQueueStatus.VERIFYING ->
            palette.sageContainer
        DownloadQueueStatus.PAUSED, DownloadQueueStatus.QUEUED ->
            MaterialTheme.colorScheme.surfaceContainerHigh
        DownloadQueueStatus.FAILED, DownloadQueueStatus.CANCELLED ->
            palette.coralContainer
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
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.repoId} · ${item.runtimeFormat.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HudBadge(text = item.status.label, color = badgeColor, containerColor = badgeContainer)
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (item.status == DownloadQueueStatus.DOWNLOADING ||
            item.status == DownloadQueueStatus.PAUSED ||
            item.status == DownloadQueueStatus.VERIFYING
        ) {
            LinearProgressIndicator(
                progress = { item.progress },
                color = palette.sageAccent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val dlMb = item.downloadedBytes / (1024f * 1024f)
        val totalMb = item.totalBytes / (1024f * 1024f)
        val remMb = item.remainingBytes / (1024f * 1024f)
        val speedMb = item.speedBytesPerSec / (1024f * 1024f)

        val statsText = when (item.status) {
            DownloadQueueStatus.DOWNLOADING -> String.format(
                Locale.US,
                "%.1f / %.1f MB (%.0f%%) · %.2f MB/s · %.1f MB left%s",
                dlMb,
                totalMb,
                item.progress * 100f,
                speedMb,
                remMb,
                if (item.etaSeconds >= 0L) " · ETA ${formatDuration(item.etaSeconds)}" else ""
            )
            DownloadQueueStatus.PAUSED -> String.format(
                Locale.US,
                "Paused at %.1f / %.1f MB · %s",
                dlMb,
                totalMb,
                if (item.supportsResume) "Resumable" else "Restart on resume"
            )
            DownloadQueueStatus.VERIFYING -> "Verifying SHA-256 checksum…"
            DownloadQueueStatus.COMPLETED -> String.format(Locale.US, "Verified %.1f MB stored offline", dlMb)
            DownloadQueueStatus.FAILED -> item.errorMessage ?: "Download failed"
            DownloadQueueStatus.CANCELLED -> "Download cancelled"
            DownloadQueueStatus.QUEUED -> "Waiting to start…"
        }

        Text(
            text = statsText,
            style = JarvisMonoTelemetryStyle,
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
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.sageContainer,
                        contentColor = palette.onHue
                    ),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BenchmarkHistoryCard(
    benchmarks: List<ModelBenchmarkEntity>,
    formatAdapters: List<RuntimeFormatAdapter>,
    onClearBenchmarks: () -> Unit,
    activeModel: ModelSlot? = null,
    engineLoadState: EngineLoadState = EngineLoadState.Unloaded,
    nativeMemoryMb: Float = 0f,
    lastBenchmark: GenerationBenchmark? = null
) {
    val palette = JarvisThemeTokens.palette
    val avgTtftMs = remember(benchmarks) {
        if (benchmarks.isEmpty()) null else benchmarks.map { it.ttftMs }.average().toLong()
    }
    val avgTps = remember(benchmarks) {
        if (benchmarks.isEmpty()) null else benchmarks.map { it.tokensPerSecond.toDouble() }.average().toFloat()
    }
    val peakMemMb = remember(benchmarks, nativeMemoryMb, lastBenchmark) {
        val historyMax = benchmarks.maxOfOrNull { maxOf(it.peakMemoryMb, it.nativeHeapMb) } ?: 0f
        maxOf(historyMax, nativeMemoryMb, lastBenchmark?.peakMemoryMb ?: 0f)
    }
    val activeInitMs = (engineLoadState as? EngineLoadState.Ready)?.loadTimeMs
        ?: lastBenchmark?.initTimeMs
        ?: benchmarks.firstOrNull { it.initTimeMs > 0L }?.initTimeMs
        ?: 0L
    val activeFileSizeBytes = activeModel?.expectedSizeBytes?.takeIf { it > 0L }
        ?: lastBenchmark?.modelSizeBytes?.takeIf { it > 0L }
        ?: benchmarks.firstOrNull { it.modelSizeBytes > 0L }?.modelSizeBytes
        ?: 0L
    val latestThermal = lastBenchmark?.thermalStatus
        ?: benchmarks.firstOrNull()?.thermalStatus
        ?: "Nominal"
    val activeBackend = (engineLoadState as? EngineLoadState.Ready)?.backendUsed
        ?: lastBenchmark?.backendUsed
        ?: benchmarks.firstOrNull()?.backendUsed
        ?: "Idle"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HudPanel(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("benchmark_summary_panel")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = palette.sageAccent)
                    Text(
                        text = "Benchmark history",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (benchmarks.isNotEmpty()) {
                    TextButton(
                        onClick = onClearBenchmarks,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BenchmarkMetricColumn(
                    label = "Avg TTFT",
                    value = avgTtftMs?.let { "${it} ms" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                BenchmarkMetricColumn(
                    label = "Avg decode",
                    value = avgTps?.let { String.format(Locale.US, "%.1f tok/s", it) } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                BenchmarkMetricColumn(
                    label = "Peak memory",
                    value = String.format(Locale.US, "%.1f MB", peakMemMb),
                    modifier = Modifier.weight(1f)
                )
            }

            EditorialHairlineDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BenchmarkMetricColumn(
                    label = "Init time",
                    value = if (activeInitMs > 0L) "${activeInitMs} ms" else "—",
                    modifier = Modifier.weight(1f)
                )
                BenchmarkMetricColumn(
                    label = "Model size",
                    value = if (activeFileSizeBytes > 0L) formatFileSize(activeFileSizeBytes) else "Not installed",
                    modifier = Modifier.weight(1f)
                )
                BenchmarkMetricColumn(
                    label = "Thermal & backend",
                    value = "$latestThermal · ${activeBackend.substringBefore(" (")}",
                    modifier = Modifier.weight(1f)
                )
            }

            EditorialHairlineDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (benchmarks.isEmpty()) {
                Text(
                    text = "No inference runs recorded yet. Send a prompt with a loaded LiteRT-LM or cloud model to log latency, throughput, and memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                benchmarks.forEachIndexed { index, entry ->
                    val timeLabel = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(entry.timestamp))
                    val sizeLabel = if (entry.modelSizeBytes > 0L) formatFileSize(entry.modelSizeBytes) else "Cloud"
                    val effectivePeak = maxOf(entry.peakMemoryMb, entry.nativeHeapMb)
                    val batteryNote = if (entry.batteryPercent >= 0) {
                        "${entry.batteryPercent}% (${String.format(Locale.US, "%.1f", entry.batteryTempCelsius)}°C)"
                    } else {
                        "Battery unavailable"
                    }

                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.modelName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = timeLabel,
                                style = JarvisMonoTelemetryStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            HudBadge(
                                text = entry.backendUsed,
                                color = palette.onHue,
                                containerColor = palette.sageContainer
                            )
                            HudBadge(text = "Thermal: ${entry.thermalStatus}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HudBadge(text = batteryNote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = String.format(
                                Locale.US,
                                "Init %d ms · TTFT %d ms · %.1f tok/s · %d out / %d prefill · Peak %.1f MB · %s",
                                entry.initTimeMs,
                                entry.ttftMs,
                                entry.tokensPerSecond,
                                entry.outputTokens,
                                entry.prefillTokens,
                                effectivePeak,
                                sizeLabel
                            ),
                            style = JarvisMonoTelemetryStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index < benchmarks.lastIndex) {
                        EditorialHairlineDivider()
                    }
                }
            }
        }

        HudPanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Format adapters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            formatAdapters.forEachIndexed { index, adapter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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
                        text = if (adapter.isExecutionSupported) "Active" else "Prepared",
                        color = if (adapter.isExecutionSupported) palette.onHue else MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = if (adapter.isExecutionSupported) palette.sageContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
                if (index < formatAdapters.lastIndex) {
                    EditorialHairlineDivider()
                }
            }
        }
    }
}

@Composable
private fun BenchmarkMetricColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = JarvisMonoMetricStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
                text = state.detail?.repoId ?: "Repository details",
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
                                text = "Fetching repository metadata and checking compatibility…",
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
                                val palette = JarvisThemeTokens.palette
                                HudBadge(
                                    text = detail.architecture,
                                    color = palette.onHue,
                                    containerColor = palette.sageContainer
                                )
                                HudBadge(text = detail.parameterCount, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HudBadge(text = detail.quantizationSummary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HudBadge(text = detail.license, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (detail.isGated) {
                                    HudBadge(text = "Gated (${detail.gatedMode ?: "manual"})", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Text(
                                text = "Tasks: ${detail.supportedTasks.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            detail.gatedPrompt?.let { prompt ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = prompt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (!detail.modelCardMarkdown.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { showReadme = !showReadme },
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (showReadme) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (showReadme) "Hide README" else "View README")
                                }
                                if (showReadme) {
                                    Text(
                                        text = detail.modelCardMarkdown,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }

                            EditorialHairlineDivider()

                            Text(
                                text = "Repository files",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            state.filesWithCompatibility.forEachIndexed { index, (file, report) ->
                                FileCompatibilityRow(
                                    file = file,
                                    report = report,
                                    onDownload = { onDownloadFile(detail, file, report) }
                                )
                                if (index < state.filesWithCompatibility.lastIndex) {
                                    EditorialHairlineDivider()
                                }
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
    val palette = JarvisThemeTokens.palette
    var expandedChecks by remember { mutableStateOf(false) }
    val verdictColor = when (report.verdict) {
        CompatibilityVerdict.READY_TO_INSTALL -> palette.sageAccent
        CompatibilityVerdict.INSTALLABLE_WITH_WARNING -> palette.sandAccent
        CompatibilityVerdict.UNSUPPORTED_FORMAT, CompatibilityVerdict.BLOCKED -> palette.errorAccent
    }
    val verdictBadgeContainer = when (report.verdict) {
        CompatibilityVerdict.READY_TO_INSTALL -> palette.sageContainer
        CompatibilityVerdict.INSTALLABLE_WITH_WARNING -> palette.sandContainer
        CompatibilityVerdict.UNSUPPORTED_FORMAT, CompatibilityVerdict.BLOCKED -> palette.coralContainer
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp), tint = verdictColor)
                Column {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatFileSize(file.sizeBytes)} · ${report.formatId.label} · ${report.quantization}",
                        style = JarvisMonoTelemetryStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = { expandedChecks = !expandedChecks },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (expandedChecks) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Toggle compatibility details"
                )
            }
        }

        HudBadge(text = report.verdict.label, color = palette.onHue, containerColor = verdictBadgeContainer)

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
                    CheckStatus.PASS -> palette.sageAccent
                    CheckStatus.WARN -> palette.sandAccent
                    CheckStatus.BLOCK -> palette.errorAccent
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    Text(
                        text = "${check.category}: ${check.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (report.canDownloadToDevice && report.canRunInJarvisNow) {
            Button(
                onClick = onDownload,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.sageContainer,
                    contentColor = palette.onHue
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("hf_download_file_${file.fileName}")
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download and install (.litertlm)")
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
