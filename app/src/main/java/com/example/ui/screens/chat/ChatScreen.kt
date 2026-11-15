package com.example.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.agent.AgentRoutingMode
import com.example.core.agent.PlanStepStatus
import com.example.core.ai.EngineLoadState
import com.example.core.model.ArtifactState
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.JarvisOrb
import com.example.ui.components.OrbMode
import com.example.ui.components.hairlineDp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.JarvisMonoCodeStyle
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JarvisThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onOpenDrawer: () -> Unit,
    onNewSession: () -> Unit,
    onSendMessage: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onLoadActiveModel: () -> Unit,
    onRunSystemSnapshot: () -> Unit,
    onRunReadinessAudit: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToModels: () -> Unit,
    onConfirmPendingTool: () -> Unit = {},
    onCancelPendingTool: () -> Unit = {},
    onCancelActiveTool: () -> Unit = {},
    onDismissPermissionPrompt: () -> Unit = {},
    onRuntimePermissionResult: (String, Boolean) -> Unit = { _, _ -> },
    onOpenDeviceControls: () -> Unit = {},
    onSelectRoutingMode: (AgentRoutingMode) -> Unit = {},
    onSummarizeSession: () -> Unit = {},
    onRegenerateResponse: () -> Unit = {}
) {
    val context = LocalContext.current
    val palette = JarvisThemeTokens.palette
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val perm = uiState.pendingRuntimePermission
        if (perm != null) {
            onRuntimePermissionResult(perm, granted)
        }
    }

    val streamingText = uiState.streamingReply
    val totalItemCount = uiState.messages.size + if (streamingText != null) 1 else 0

    LaunchedEffect(totalItemCount) {
        if (totalItemCount > 3) {
            listState.animateScrollToItem((totalItemCount - 1).coerceAtLeast(0))
        }
    }

    val (badgeText, badgeColor) = when (val engineState = uiState.engineLoadState) {
        is EngineLoadState.Ready -> engineState.backendUsed to palette.skyAccent
        is EngineLoadState.Loading -> "Loading" to palette.textSecondary
        is EngineLoadState.Error -> "Error" to palette.errorAccent
        is EngineLoadState.Unloaded -> "Unloaded" to palette.textSecondary
    }

    HudBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_history_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.cd_open_history),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.hud_brand_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = uiState.activeConversationTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    val privacyTag = when {
                        uiState.settings.routingMode == AgentRoutingMode.LOCAL_ONLY -> "Local"
                        uiState.settings.cloudUploadConsentGranted -> "Hybrid"
                        else -> "Private"
                    }
                    HudBadge(
                        text = privacyTag,
                        color = if (uiState.settings.cloudUploadConsentGranted && uiState.settings.routingMode != AgentRoutingMode.LOCAL_ONLY) {
                            palette.skyAccent
                        } else {
                            palette.textSecondary
                        },
                        containerColor = palette.surfaceHigh,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("chat_privacy_badge")
                    )
                    HudBadge(
                        text = badgeText,
                        color = badgeColor,
                        containerColor = palette.surfaceHigh,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("chat_engine_badge")
                    )
                    IconButton(
                        onClick = onNewSession,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("topbar_new_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddComment,
                            contentDescription = stringResource(R.string.drawer_new_session),
                            tint = palette.skyAccent
                        )
                    }
                }
            )
            EditorialHairlineDivider()

            if (uiState.engineLoadState is EngineLoadState.Loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.skyAccent,
                    trackColor = palette.surfaceHigh
                )
            }

            if (uiState.messages.isEmpty() && streamingText == null) {
                Spacer(modifier = Modifier.height(8.dp))
                UnifiedAssistantStatusHeader(
                    uiState = uiState,
                    onNavigateToVoice = onNavigateToVoice,
                    onNavigateToModels = onNavigateToModels,
                    onOpenDeviceControls = onOpenDeviceControls
                )
            }

            if (uiState.activePlan != null) {
                val activePlan = uiState.activePlan
                val inProgress = activePlan.steps.any {
                    it.status == PlanStepStatus.RUNNING || it.status == PlanStepStatus.PENDING
                }
                var monitorExpanded by rememberSaveable(activePlan.planId) { mutableStateOf(false) }
                val completedCount = activePlan.steps.count {
                    it.status == PlanStepStatus.COMPLETED || it.status == PlanStepStatus.RECOVERED
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 840.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("agent_execution_monitor"),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(hairlineDp(), MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { monitorExpanded = !monitorExpanded }
                                .testTag("agent_monitor_toggle"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${activePlan.routeDecision.route.label} ($completedCount/${activePlan.steps.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val subMeta = buildString {
                                    activePlan.routeDecision.matchedSkillName?.let { append("Skill: $it · ") }
                                    if (activePlan.routeDecision.capabilitySummary.isNotBlank()) {
                                        append(activePlan.routeDecision.capabilitySummary)
                                    } else {
                                        append(activePlan.goal.take(56))
                                    }
                                }
                                Text(
                                    text = subMeta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (inProgress) {
                                    TextButton(
                                        onClick = onCancelActiveTool,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("cancel_plan_button")
                                    ) {
                                        Text("Stop")
                                    }
                                }
                                Icon(
                                    imageVector = if (monitorExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (monitorExpanded) "Collapse plan" else "Expand plan",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (monitorExpanded) {
                            EditorialHairlineDivider()
                            activePlan.steps.forEach { step ->
                                val statusColor = when (step.status) {
                                    PlanStepStatus.COMPLETED, PlanStepStatus.RECOVERED -> MaterialTheme.colorScheme.primary
                                    PlanStepStatus.RUNNING -> MaterialTheme.colorScheme.onSurface
                                    PlanStepStatus.FAILED, PlanStepStatus.CANCELLED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${step.index + 1}. ${step.title}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = step.status.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor
                                        )
                                    }
                                    val detailLine = step.resultSummary?.takeIf { it.isNotBlank() } ?: step.detail
                                    if (detailLine.isNotBlank()) {
                                        Text(
                                            text = detailLine,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            if (uiState.activeToolName != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 840.dp)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("active_tool_banner"),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(hairlineDp(), MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Running ${uiState.activeToolName}…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = onCancelActiveTool,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("cancel_active_tool_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
            ) {
                if (uiState.messages.isEmpty() && streamingText == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val orbMode = when (uiState.engineLoadState) {
                            is EngineLoadState.Loading -> OrbMode.PROCESSING
                            is EngineLoadState.Error -> OrbMode.WARNING
                            else -> OrbMode.IDLE
                        }
                        JarvisOrb(
                            mode = orbMode,
                            size = 160.dp,
                            animationEnabled = uiState.settings.orbAnimationEnabled,
                            glowIntensity = uiState.settings.orbGlowIntensity,
                            isModelGenerating = uiState.isGenerating
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = when (uiState.engineLoadState) {
                                is EngineLoadState.Ready -> "Gemma 3 1B ready"
                                is EngineLoadState.Loading -> "Loading LiteRT-LM"
                                else -> stringResource(R.string.chat_empty_title)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val subtitle = when (val load = uiState.engineLoadState) {
                            is EngineLoadState.Ready ->
                                "Session ready on ${load.backendUsed} (${String.format(Locale.US, "%.1f", uiState.nativeMemoryMb)} MB heap, ${uiState.settings.contextLengthTokens} context)."
                            is EngineLoadState.Loading ->
                                "Loading ${load.modelName} on ${load.targetBackend}…"
                            is EngineLoadState.Error -> load.message
                            is EngineLoadState.Unloaded ->
                                if (uiState.activeModel?.artifactState == ArtifactState.READY) {
                                    "${uiState.activeModel.displayName} is verified and ready to load offline."
                                } else {
                                    stringResource(R.string.chat_empty_subtitle)
                                }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 460.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("chat_message_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            ChatMessageBubble(
                                message = message,
                                canRegenerate = !uiState.isGenerating && message.role == MessageRole.ASSISTANT,
                                onRegenerate = onRegenerateResponse
                            )
                        }
                        if (streamingText != null) {
                            item(key = "streaming_bubble") {
                                StreamingAssistantBubble(
                                    modelLabel = uiState.activeModel?.displayName ?: "Gemma 3 1B",
                                    backendUsed = uiState.streamingBackend ?: "LiteRT-LM",
                                    text = streamingText,
                                    estimatedTokens = uiState.streamingEstimatedTokens
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.engineLoadState is EngineLoadState.Error) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 840.dp)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("chat_error_recovery_banner"),
                    shape = MaterialTheme.shapes.small,
                    color = palette.crimsonBadgeBg,
                    border = BorderStroke(hairlineDp(), palette.errorAccent.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                text = "Local engine error",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.errorAccent
                            )
                            Text(
                                text = uiState.engineLoadState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = onLoadActiveModel,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_retry_load_button")
                            ) {
                                Text("Retry", color = palette.skyAccent)
                            }
                            TextButton(
                                onClick = onNavigateToModels,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_switch_model_button")
                            ) {
                                Text("Models", color = palette.text)
                            }
                        }
                    }
                }
            }

            // Quick Actions Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val modes = AgentRoutingMode.entries
                    val nextMode = modes[(modes.indexOf(uiState.settings.routingMode) + 1) % modes.size]
                    AssistChip(
                        onClick = { onSelectRoutingMode(nextMode) },
                        label = {
                            Text(
                                text = uiState.settings.routingMode.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (uiState.settings.routingMode) {
                                    AgentRoutingMode.LOCAL_ONLY -> Icons.Filled.Lock
                                    AgentRoutingMode.CLOUD_ONLY -> Icons.Filled.Cloud
                                    AgentRoutingMode.AUTO_HYBRID -> Icons.Filled.AutoAwesome
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = palette.surface,
                            labelColor = palette.text,
                            leadingIconContentColor = palette.skyAccent
                        ),
                        modifier = Modifier.testTag("chip_routing_mode")
                    )
                }
                if (uiState.messages.isNotEmpty()) {
                    item {
                        AssistChip(
                            onClick = onSummarizeSession,
                            label = {
                                Text(
                                    text = "Save summary",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.BookmarkAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("chip_summarize_session")
                        )
                    }
                }
                if (uiState.engineLoadState !is EngineLoadState.Ready &&
                    uiState.activeModel?.artifactState == ArtifactState.READY
                ) {
                    item {
                        AssistChip(
                            onClick = onLoadActiveModel,
                            label = {
                                Text(
                                    text = "Load ${uiState.activeModel.displayName}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = palette.skyContainer,
                                labelColor = palette.onHue,
                                leadingIconContentColor = palette.onHue
                            ),
                            modifier = Modifier.testTag("chip_load_model")
                        )
                    }
                }
                item {
                    AssistChip(
                        onClick = onRunSystemSnapshot,
                        label = {
                            Text(
                                text = stringResource(R.string.chip_telemetry_snapshot),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.MonitorHeart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("chip_system_snapshot")
                    )
                }
                item {
                    AssistChip(
                        onClick = onRunReadinessAudit,
                        label = {
                            Text(
                                text = stringResource(R.string.chip_engine_status),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("chip_engine_audit")
                    )
                }
                item {
                    AssistChip(
                        onClick = onNavigateToModels,
                        label = {
                            Text(
                                text = stringResource(R.string.chip_open_models),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("chip_inspect_models")
                    )
                }
            }

            // Composer Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(24.dp),
                color = palette.surface,
                border = BorderStroke(hairlineDp(), palette.hairline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onNavigateToVoice,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_voice_handoff_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = stringResource(R.string.cd_voice_handoff),
                            tint = palette.peachAccent
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.chat_input_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
                            )
                        },
                        maxLines = 4,
                        enabled = !uiState.isGenerating,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() && !uiState.isGenerating) {
                                    onSendMessage(inputText)
                                    inputText = ""
                                }
                            }
                        ),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.skyAccent,
                            unfocusedBorderColor = palette.hairline
                        )
                    )

                    if (uiState.isGenerating) {
                        FilledIconButton(
                            onClick = onCancelGeneration,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = palette.coralContainer,
                                contentColor = palette.onHue
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Stop generation"
                            )
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    onSendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = palette.skyContainer,
                                contentColor = palette.onHue,
                                disabledContainerColor = palette.surfaceHigh,
                                disabledContentColor = palette.textSecondary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.cd_send_message)
                            )
                        }
                    }
                }
            }

            val palette = JarvisThemeTokens.palette
            val pendingConfirm = uiState.pendingToolConfirmation
            if (pendingConfirm != null) {
                AlertDialog(
                    onDismissRequest = onCancelPendingTool,
                    modifier = Modifier.testTag("tool_confirmation_dialog"),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = palette.skyAccent
                        )
                    },
                    title = {
                        Text("Confirm ${pendingConfirm.request.toolName}")
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = pendingConfirm.confirmationMessage,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = pendingConfirm.sensitivityWarning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = onConfirmPendingTool,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.skyContainer,
                                contentColor = palette.onHue
                            ),
                            modifier = Modifier.testTag("confirm_tool_button")
                        ) {
                            Text("Approve")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = onCancelPendingTool,
                            modifier = Modifier.testTag("cancel_tool_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            val pendingPerm = uiState.pendingRuntimePermission
            val pendingSpecial = uiState.pendingSpecialAccess
            if (pendingPerm != null || pendingSpecial != null) {
                AlertDialog(
                    onDismissRequest = onDismissPermissionPrompt,
                    modifier = Modifier.testTag("tool_permission_dialog"),
                    title = {
                        Text(
                            if (pendingPerm != null) "Permission required"
                            else "${pendingSpecial?.label} required"
                        )
                    },
                    text = {
                        Text(
                            text = uiState.permissionRationale
                                ?: "Explicit authorization is required before using this Android feature.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (pendingPerm != null) {
                                    permissionLauncher.launch(pendingPerm)
                                } else if (pendingSpecial != null) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(pendingSpecial.settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                    onDismissPermissionPrompt()
                                }
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.skyContainer,
                                contentColor = palette.onHue
                            ),
                            modifier = Modifier.testTag("grant_tool_permission_button")
                        ) {
                            Text(if (pendingPerm != null) "Allow" else "Open settings")
                        }
                    },
                    dismissButton = {
                        Row {
                            if (pendingSpecial != null) {
                                TextButton(onClick = {
                                    onDismissPermissionPrompt()
                                    onOpenDeviceControls()
                                }) {
                                    Text("Privacy controls")
                                }
                            }
                            TextButton(onClick = onDismissPermissionPrompt) {
                                Text("Dismiss")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    modelLabel: String,
    backendUsed: String,
    text: String,
    estimatedTokens: Int
) {
    val palette = JarvisThemeTokens.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_streaming_bubble"),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(0.94f),
            shape = RoundedCornerShape(22.dp),
            color = palette.surface,
            border = BorderStroke(hairlineDp(), palette.skyAccent.copy(alpha = 0.6f))
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
                    Text(
                        text = "$modelLabel · Streaming",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.skyAccent
                    )
                    Text(
                        text = "$backendUsed · ~$estimatedTokens tok",
                        style = JarvisMonoTelemetryStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val streamDirection = if (containsArabicScript(text)) {
                    LayoutDirection.Rtl
                } else {
                    LocalLayoutDirection.current
                }
                CompositionLocalProvider(LocalLayoutDirection provides streamDirection) {
                    Text(
                        text = if (text.isEmpty()) "Thinking…" else "$text▌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedAssistantStatusHeader(
    uiState: ChatUiState,
    onNavigateToVoice: () -> Unit,
    onNavigateToModels: () -> Unit,
    onOpenDeviceControls: () -> Unit
) {
    val orbMode = when {
        uiState.isGenerating -> OrbMode.PROCESSING
        uiState.engineLoadState is EngineLoadState.Loading -> OrbMode.PROCESSING
        uiState.engineLoadState is EngineLoadState.Error -> OrbMode.WARNING
        else -> OrbMode.IDLE
    }
    val stateLabel = when {
        uiState.isGenerating -> "Thinking"
        uiState.engineLoadState is EngineLoadState.Loading -> "Loading"
        uiState.engineLoadState is EngineLoadState.Ready -> "Ready"
        uiState.engineLoadState is EngineLoadState.Error -> "Alert"
        else -> "Standby"
    }
    val activeModelName = uiState.activeModel?.displayName ?: "Gemma 3 1B"
    val runtimeDetail = when (val load = uiState.engineLoadState) {
        is EngineLoadState.Ready ->
            "${load.backendUsed} · ${String.format(Locale.US, "%.0f", uiState.nativeMemoryMb)} MB"
        is EngineLoadState.Loading ->
            "Loading ${load.modelName}…"
        is EngineLoadState.Error ->
            load.message
        is EngineLoadState.Unloaded ->
            "${uiState.settings.routingMode.label} · ${uiState.settings.inferenceBackend.label}"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .padding(horizontal = 16.dp)
            .testTag("assistant_home_header"),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(hairlineDp(), MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onNavigateToVoice)
                    .testTag("header_orb_voice_trigger"),
                contentAlignment = Alignment.Center
            ) {
                JarvisOrb(
                    mode = orbMode,
                    size = 24.dp,
                    animationEnabled = uiState.settings.orbAnimationEnabled,
                    glowIntensity = uiState.settings.orbGlowIntensity,
                    isModelGenerating = uiState.isGenerating,
                    statusDescription = "Assistant status sphere"
                )
            }
            Text(
                text = "$stateLabel · $activeModelName ($runtimeDetail)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Models",
                style = MaterialTheme.typography.labelMedium,
                color = JarvisThemeTokens.palette.sageAccent,
                modifier = Modifier
                    .clickable(onClick = onNavigateToModels)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("header_models_shortcut")
            )
            Text(
                text = "Tools",
                style = MaterialTheme.typography.labelMedium,
                color = JarvisThemeTokens.palette.sandAccent,
                modifier = Modifier
                    .clickable(onClick = onOpenDeviceControls)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("header_device_shortcut")
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: MessageEntity,
    canRegenerate: Boolean = false,
    onRegenerate: () -> Unit = {}
) {
    val context = LocalContext.current
    val palette = JarvisThemeTokens.palette
    val isUser = message.role == MessageRole.USER
    val isSystemNotice = message.role == MessageRole.SYSTEM_NOTICE
    val isToolSnapshot = message.role == MessageRole.TOOL_SNAPSHOT

    val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

    val containerColor = when {
        isUser -> palette.skyContainer
        isSystemNotice -> MaterialTheme.colorScheme.surfaceContainerLow
        isToolSnapshot -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> palette.surface
    }
    val bubbleTextColor = if (isUser) palette.onHue else palette.text
    val bubbleSecondaryColor = if (isUser) palette.onHue.copy(alpha = 0.72f) else palette.textSecondary
    val bubbleAccentColor = if (isUser) palette.onHue else palette.skyAccent
    val borderStroke = if (isUser) {
        null
    } else {
        BorderStroke(hairlineDp(), palette.hairline)
    }

    val segments = parseMessageSegments(message.content)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(if (isUser) 0.84f else 0.94f),
            shape = RoundedCornerShape(22.dp),
            color = containerColor,
            border = borderStroke
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.modelLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = bubbleAccentColor,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (canRegenerate) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable(onClick = onRegenerate)
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .testTag("regenerate_response_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Retry response",
                                    modifier = Modifier.size(14.dp),
                                    tint = palette.skyAccent
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    text = "Retry",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.skyAccent
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { copyToClipboard(context, "Jarvis message", message.content) }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .testTag("copy_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy message",
                                modifier = Modifier.size(14.dp),
                                tint = bubbleSecondaryColor
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = "Copy",
                                style = MaterialTheme.typography.labelSmall,
                                color = bubbleSecondaryColor
                            )
                        }
                        Text(
                            text = timestamp,
                            style = JarvisMonoTelemetryStyle,
                            color = bubbleSecondaryColor
                        )
                    }
                }
                val messageDirection = if (containsArabicScript(message.content)) {
                    LayoutDirection.Rtl
                } else {
                    LocalLayoutDirection.current
                }
                CompositionLocalProvider(LocalLayoutDirection provides messageDirection) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        segments.forEach { seg ->
                            when (seg) {
                                is MessageSegment.Prose -> {
                                    Text(
                                        text = seg.text,
                                        style = if (isToolSnapshot) {
                                            JarvisMonoCodeStyle
                                        } else {
                                            MaterialTheme.typography.bodyMedium
                                        },
                                        color = bubbleTextColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is MessageSegment.CodeBlock -> {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(hairlineDp(), palette.hairline)
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
                                                Text(
                                                    text = seg.language.ifBlank { "code" },
                                                    style = JarvisMonoTelemetryStyle,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clickable { copyToClipboard(context, "Jarvis code", seg.code) }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        .testTag("copy_code_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.ContentCopy,
                                                        contentDescription = "Copy code",
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.size(4.dp))
                                                    Text(
                                                        text = "Copy code",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Text(
                                                text = seg.code,
                                                style = JarvisMonoCodeStyle,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (message.role == MessageRole.ASSISTANT &&
                    (message.ttftMs != null || message.tokensPerSecond != null)
                ) {
                    EditorialHairlineDivider(modifier = Modifier.padding(top = 4.dp))
                    val metricsText = buildString {
                        message.ttftMs?.let { append("TTFT ${it}ms") }
                        message.tokensPerSecond?.let {
                            if (isNotEmpty()) append(" · ")
                            append(String.format(Locale.US, "%.1f tok/s", it))
                        }
                        message.outputTokens?.let {
                            if (isNotEmpty()) append(" · ")
                            append("$it tok")
                        }
                        message.backendUsed?.let {
                            if (isNotEmpty()) append(" · ")
                            append(it)
                        }
                    }
                    Text(
                        text = metricsText,
                        style = JarvisMonoTelemetryStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("message_benchmark_footer")
                    )
                }
            }
        }
    }
}

private sealed interface MessageSegment {
    data class Prose(val text: String) : MessageSegment
    data class CodeBlock(val language: String, val code: String) : MessageSegment
}

private val CODE_FENCE_REGEX = Regex("```([a-zA-Z0-9_+-]*)\\n?([\\s\\S]*?)```")

private fun parseMessageSegments(raw: String): List<MessageSegment> {
    if (!raw.contains("```")) return listOf(MessageSegment.Prose(raw))
    val segments = mutableListOf<MessageSegment>()
    var lastIndex = 0
    for (match in CODE_FENCE_REGEX.findAll(raw)) {
        if (match.range.first > lastIndex) {
            val prose = raw.substring(lastIndex, match.range.first).trim()
            if (prose.isNotEmpty()) {
                segments.add(MessageSegment.Prose(prose))
            }
        }
        val lang = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        segments.add(MessageSegment.CodeBlock(language = lang, code = code))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < raw.length) {
        val trailing = raw.substring(lastIndex).trim()
        if (trailing.isNotEmpty()) {
            segments.add(MessageSegment.Prose(trailing))
        }
    }
    return if (segments.isEmpty()) listOf(MessageSegment.Prose(raw)) else segments
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

private fun containsArabicScript(text: String): Boolean =
    text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }

@Preview(name = "Chat Standby Light", showBackground = true)
@Preview(name = "Chat Standby Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenStandbyPreview() {
    JarvisTheme {
        ChatScreen(
            uiState = ChatUiState(activeConversationTitle = "Session 1"),
            onOpenDrawer = {},
            onNewSession = {},
            onSendMessage = {},
            onCancelGeneration = {},
            onLoadActiveModel = {},
            onRunSystemSnapshot = {},
            onRunReadinessAudit = {},
            onNavigateToVoice = {},
            onNavigateToModels = {}
        )
    }
}

@Preview(name = "Chat Active Light", showBackground = true)
@Preview(name = "Chat Active Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenConversationPreview() {
    JarvisTheme {
        ChatScreen(
            uiState = ChatUiState(
                activeConversationTitle = "Battery & Diagnostics",
                messages = listOf(
                    MessageEntity(
                        id = "m1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "Check my battery and show a Kotlin snippet.",
                        timestamp = 1700000000000L,
                        modelLabel = "You"
                    ),
                    MessageEntity(
                        id = "m2",
                        conversationId = "c1",
                        role = MessageRole.ASSISTANT,
                        content = "Battery is at 88%.\n```kotlin\nval level = batteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)\n```",
                        timestamp = 1700000010000L,
                        modelLabel = "Gemma 3 1B",
                        ttftMs = 210L,
                        tokensPerSecond = 19.4f,
                        outputTokens = 28,
                        backendUsed = "GPU"
                    )
                )
            ),
            onOpenDrawer = {},
            onNewSession = {},
            onSendMessage = {},
            onCancelGeneration = {},
            onLoadActiveModel = {},
            onRunSystemSnapshot = {},
            onRunReadinessAudit = {},
            onNavigateToVoice = {},
            onNavigateToModels = {}
        )
    }
}
