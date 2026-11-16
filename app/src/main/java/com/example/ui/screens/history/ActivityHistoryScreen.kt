package com.example.ui.screens.history

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.agent.AgentExecutionRecord
import com.example.core.device.DeviceCommandResult
import com.example.core.device.DeviceControlBackend
import com.example.core.device.PrivilegedActionId
import com.example.data.local.db.AgentCheckpointEntity
import com.example.data.local.db.ModelBenchmarkEntity
import com.example.data.local.db.ResearchHistoryEntity
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.HudPanel
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JarvisThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ActivityHistoryTab(val label: String) {
    EXECUTIONS("Executions"),
    CHECKPOINTS("Checkpoints"),
    WEB_HISTORY("Web History"),
    COMMANDS("Device Commands"),
    BENCHMARKS("AI Benchmarks")
}

@Composable
fun ActivityHistoryScreen(
    executions: List<AgentExecutionRecord>,
    commandResults: List<DeviceCommandResult>,
    benchmarks: List<ModelBenchmarkEntity>,
    onNavigateBack: () -> Unit,
    checkpoints: List<AgentCheckpointEntity> = emptyList(),
    researchHistory: List<ResearchHistoryEntity> = emptyList(),
    onResumeCheckpoint: (String) -> Unit = {},
    onDeleteCheckpoint: (String) -> Unit = {}
) {
    val palette = JarvisThemeTokens.palette
    var selectedTab by rememberSaveable { mutableStateOf(ActivityHistoryTab.EXECUTIONS) }

    HudBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag("activity_history_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 860.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("activity_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = palette.text
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.indigoBadgeBg
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = palette.lavenderAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.activity_history_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = palette.text
                        )
                        Text(
                            text = "${executions.size} agent · ${commandResults.size} device · ${benchmarks.size} measured runs",
                            style = JarvisMonoTelemetryStyle,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            item {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.skyContainer,
                    selectedLabelColor = palette.onHue
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 860.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == ActivityHistoryTab.EXECUTIONS,
                        onClick = { selectedTab = ActivityHistoryTab.EXECUTIONS },
                        label = { Text("${ActivityHistoryTab.EXECUTIONS.label} (${executions.size})") },
                        colors = chipColors,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_tab_executions")
                    )
                    FilterChip(
                        selected = selectedTab == ActivityHistoryTab.CHECKPOINTS,
                        onClick = { selectedTab = ActivityHistoryTab.CHECKPOINTS },
                        label = { Text("${ActivityHistoryTab.CHECKPOINTS.label} (${checkpoints.size})") },
                        colors = chipColors,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_tab_checkpoints")
                    )
                    FilterChip(
                        selected = selectedTab == ActivityHistoryTab.WEB_HISTORY,
                        onClick = { selectedTab = ActivityHistoryTab.WEB_HISTORY },
                        label = { Text("${ActivityHistoryTab.WEB_HISTORY.label} (${researchHistory.size})") },
                        colors = chipColors,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_tab_web")
                    )
                    FilterChip(
                        selected = selectedTab == ActivityHistoryTab.COMMANDS,
                        onClick = { selectedTab = ActivityHistoryTab.COMMANDS },
                        label = { Text("${ActivityHistoryTab.COMMANDS.label} (${commandResults.size})") },
                        colors = chipColors,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_tab_commands")
                    )
                    FilterChip(
                        selected = selectedTab == ActivityHistoryTab.BENCHMARKS,
                        onClick = { selectedTab = ActivityHistoryTab.BENCHMARKS },
                        label = { Text("${ActivityHistoryTab.BENCHMARKS.label} (${benchmarks.size})") },
                        colors = chipColors,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_tab_benchmarks")
                    )
                }
            }

            when (selectedTab) {
                ActivityHistoryTab.EXECUTIONS -> {
                    if (executions.isEmpty()) {
                        item {
                            EmptyHistoryPanel("No recorded agent executions yet. Prompts and tool dispatches will appear here transparently.")
                        }
                    } else {
                        items(executions, key = { it.id }) { record ->
                            ExecutionRecordCard(record = record)
                        }
                    }
                }

                ActivityHistoryTab.CHECKPOINTS -> {
                    if (checkpoints.isEmpty()) {
                        item {
                            EmptyHistoryPanel("No saved agent checkpoints yet. Multi-step autonomous runs save recoverable checkpoints here.")
                        }
                    } else {
                        items(checkpoints, key = { it.id }) { cp ->
                            CheckpointCard(
                                checkpoint = cp,
                                onResume = { onResumeCheckpoint(cp.id) },
                                onDelete = { onDeleteCheckpoint(cp.id) }
                            )
                        }
                    }
                }

                ActivityHistoryTab.WEB_HISTORY -> {
                    if (researchHistory.isEmpty()) {
                        item {
                            EmptyHistoryPanel("No web research logs yet. Enable Web Search in Settings to run grounded queries.")
                        }
                    } else {
                        items(researchHistory, key = { it.id }) { rec ->
                            ResearchHistoryCard(record = rec)
                        }
                    }
                }

                ActivityHistoryTab.COMMANDS -> {
                    if (commandResults.isEmpty()) {
                        item {
                            EmptyHistoryPanel("No device command executions yet. Actions routed through Shizuku, Wireless ADB, Native APIs, or Accessibility appear here with verification evidence.")
                        }
                    } else {
                        items(
                            items = commandResults,
                            key = { "${it.actionId.wireId}_${it.timestampMs}" }
                        ) { cmd ->
                            DeviceCommandHistoryCard(cmd = cmd)
                        }
                    }
                }

                ActivityHistoryTab.BENCHMARKS -> {
                    if (benchmarks.isEmpty()) {
                        item {
                            EmptyHistoryPanel("No measured AI benchmarks yet. Send a message with a local LiteRT-LM model loaded to capture real TTFT, tokens/sec, peak RAM, battery, and thermal metrics.")
                        }
                    } else {
                        items(benchmarks, key = { it.id }) { bm ->
                            MeasuredBenchmarkHistoryCard(bm = bm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryPanel(message: String) {
    val palette = JarvisThemeTokens.palette
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 860.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun ExecutionRecordCard(record: AgentExecutionRecord) {
    val palette = JarvisThemeTokens.palette
    val time = SimpleDateFormat("MMM d · HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 860.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = time,
                style = JarvisMonoTelemetryStyle,
                color = palette.textSecondary
            )
            HudBadge(
                text = "${record.routeTaken} · ${record.status}",
                color = palette.onHue,
                containerColor = if (record.status.equals("SUCCESS", ignoreCase = true) ||
                    record.status.equals("COMPLETED", ignoreCase = true)
                ) {
                    palette.sageContainer
                } else {
                    palette.skyContainer
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = record.userPrompt,
            style = MaterialTheme.typography.titleSmall,
            color = palette.text
        )
        if (record.stepsSummary.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = record.stepsSummary,
                style = JarvisMonoTelemetryStyle,
                color = palette.skyAccent
            )
        }
        if (record.resultSummary.isNotBlank()) {
            EditorialHairlineDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = record.resultSummary,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${record.durationMs} ms · ${record.routeReason}",
            style = JarvisMonoTelemetryStyle,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun DeviceCommandHistoryCard(cmd: DeviceCommandResult) {
    val palette = JarvisThemeTokens.palette
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(cmd.timestampMs))
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 860.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    tint = palette.skyAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = cmd.actionId.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.text
                )
            }
            HudBadge(
                text = if (cmd.verified) "${cmd.backendUsed.label} Verified" else cmd.backendUsed.label,
                color = palette.onHue,
                containerColor = if (cmd.success) palette.sageContainer else palette.coralContainer
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = cmd.summary,
            style = MaterialTheme.typography.bodySmall,
            color = palette.text
        )
        if (cmd.verificationEvidence.isNotBlank()) {
            EditorialHairlineDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = "$time · ${cmd.verificationEvidence}",
                style = JarvisMonoTelemetryStyle,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun MeasuredBenchmarkHistoryCard(bm: ModelBenchmarkEntity) {
    val palette = JarvisThemeTokens.palette
    val time = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(bm.timestamp))
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 860.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    tint = palette.sageAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = bm.modelName,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.text
                )
            }
            HudBadge(
                text = bm.backendUsed,
                color = palette.onHue,
                containerColor = palette.sageContainer
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = String.format(
                Locale.US,
                "TTFT %d ms · %.1f tok/s · Load %d ms · Peak RAM %.0f MB · %s · Batt %d%%",
                bm.ttftMs,
                bm.tokensPerSecond,
                bm.initTimeMs,
                bm.peakMemoryMb,
                bm.thermalStatus,
                bm.batteryPercent
            ),
            style = JarvisMonoTelemetryStyle,
            color = palette.skyAccent
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$time · ${bm.outputTokens} out / ${bm.prefillTokens} prefill tokens",
            style = JarvisMonoTelemetryStyle,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun CheckpointCard(
    checkpoint: AgentCheckpointEntity,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = JarvisThemeTokens.palette
    val time = SimpleDateFormat("MMM d · HH:mm:ss", Locale.getDefault()).format(Date(checkpoint.updatedAt))
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 860.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Step ${checkpoint.currentStep}/${checkpoint.maxSteps} · $time",
                style = JarvisMonoTelemetryStyle,
                color = palette.skyAccent
            )
            HudBadge(
                text = checkpoint.status,
                color = palette.onHue,
                containerColor = palette.lavenderContainer
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = checkpoint.userGoal,
            style = MaterialTheme.typography.titleSmall,
            color = palette.text
        )
        if (checkpoint.lastObservationSummary.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = checkpoint.lastObservationSummary,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(
                onClick = onResume,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("checkpoint_resume_${checkpoint.id}")
            ) {
                Text("Resume in Chat")
            }
            androidx.compose.material3.TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("checkpoint_delete_${checkpoint.id}")
            ) {
                Text("Delete", color = palette.errorAccent)
            }
        }
    }
}

@Composable
private fun ResearchHistoryCard(record: ResearchHistoryEntity) {
    val palette = JarvisThemeTokens.palette
    val time = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 860.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = time,
                style = JarvisMonoTelemetryStyle,
                color = palette.textSecondary
            )
            HudBadge(
                text = record.provider,
                color = palette.onHue,
                containerColor = palette.skyContainer
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = record.query,
            style = MaterialTheme.typography.titleSmall,
            color = palette.text
        )
        if (record.summarySnippet.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = record.summarySnippet,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
    }
}

@Preview(name = "Activity Light", showBackground = true)
@Preview(name = "Activity Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActivityHistoryPreview() {
    JarvisTheme {
        ActivityHistoryScreen(
            executions = emptyList(),
            commandResults = listOf(
                DeviceCommandResult(
                    actionId = PrivilegedActionId.PING_RUNTIME,
                    success = true,
                    backendUsed = DeviceControlBackend.SHIZUKU,
                    verified = true,
                    verificationEvidence = "UID 2000 verified",
                    summary = "Executed via Shizuku (ADB Shell): uid=2000(shell)"
                )
            ),
            benchmarks = emptyList(),
            onNavigateBack = {}
        )
    }
}
