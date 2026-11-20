package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.agent.AgentExecutionPhase
import com.example.core.agent.AgentTimelineEntry
import com.example.core.agent.ObservationPreviewSnapshot
import com.example.core.agent.StopSummary
import com.example.ui.theme.JarvisRadii
import com.example.ui.theme.JarvisThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgentPhaseBadge(
    phase: AgentExecutionPhase,
    modifier: Modifier = Modifier
) {
    val palette = JarvisThemeTokens.palette
    val (accent, container, pulsing) = when (phase) {
        AgentExecutionPhase.THINKING,
        AgentExecutionPhase.OBSERVING,
        AgentExecutionPhase.EXECUTING,
        AgentExecutionPhase.VERIFYING -> Triple(palette.skyAccent, palette.cyanBadgeBg, true)
        AgentExecutionPhase.WAITING_PERMISSION,
        AgentExecutionPhase.WAITING_CONFIRMATION,
        AgentExecutionPhase.PAUSED,
        AgentExecutionPhase.RECOVERING -> Triple(palette.warningAccent, palette.amberBadgeBg, false)
        AgentExecutionPhase.COMPLETED -> Triple(palette.successAccent, palette.tealBadgeBg, false)
        AgentExecutionPhase.FAILED,
        AgentExecutionPhase.CANCELLED -> Triple(palette.errorAccent, palette.crimsonBadgeBg, false)
        AgentExecutionPhase.IDLE -> Triple(palette.textSecondary, palette.surfaceHigh, false)
    }
    JarvisStatusIndicator(
        label = phase.label,
        accentColor = accent,
        containerColor = container,
        pulsing = pulsing,
        modifier = modifier.testTag("agent_phase_badge")
    )
}

@Composable
fun ScreenObservationPreviewCard(
    preview: ObservationPreviewSnapshot,
    modifier: Modifier = Modifier
) {
    val palette = JarvisThemeTokens.palette
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("screen_observation_preview_card"),
        shape = RoundedCornerShape(JarvisRadii.sm),
        color = palette.surfaceHigh,
        border = BorderStroke(hairlineDp(), palette.hairline)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ScreenshotMonitor,
                        contentDescription = null,
                        tint = palette.skyAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = preview.packageName ?: "Active screen",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${preview.screenTypeLabel} · ${preview.clickableCount}/${preview.elementCount} nodes",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = palette.textSecondary
                )
            }
            if (preview.summary.isNotBlank()) {
                Text(
                    text = preview.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AgentLiveTimelineSection(
    entries: List<AgentTimelineEntry>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = JarvisThemeTokens.palette
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("agent_live_timeline_section"),
        shape = RoundedCornerShape(JarvisRadii.sm),
        color = palette.surfaceHigh,
        border = BorderStroke(hairlineDp(), palette.hairline)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = palette.skyAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Live Execution Timeline (${entries.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.text
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse timeline" else "Expand timeline",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (expanded) {
                entries.takeLast(8).forEach { entry ->
                    val ts = remember(entry.timestampMs) { timeFormat.format(Date(entry.timestampMs)) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = ts,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = palette.textSecondary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.detail.isNotBlank()) {
                                Text(
                                    text = entry.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentStopSummaryBanner(
    summary: StopSummary,
    onResumeCheckpoint: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = JarvisThemeTokens.palette
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("agent_stop_summary_banner"),
        shape = RoundedCornerShape(JarvisRadii.md),
        color = palette.surfaceHigh,
        border = BorderStroke(hairlineDp(), palette.warningAccent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        imageVector = Icons.Outlined.StopCircle,
                        contentDescription = null,
                        tint = palette.warningAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Task Stopped (${summary.completedSteps}/${summary.totalSteps} steps)",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.text
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Dismiss stop summary",
                        tint = palette.textSecondary
                    )
                }
            }
            Text(
                text = summary.reason,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            if (summary.canResumeFromCheckpoint) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onResumeCheckpoint(summary.planId) }) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(text = "Resume Checkpoint")
                    }
                }
            }
        }
    }
}
