package com.example.ui.screens.chat

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ai.EngineLoadState
import com.example.core.model.ArtifactState
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.JarvisOrb
import com.example.ui.components.OrbMode
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
    onNavigateToModels: () -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val streamingText = uiState.streamingReply
    val totalItemCount = uiState.messages.size + if (streamingText != null) 1 else 0

    LaunchedEffect(totalItemCount, streamingText?.length) {
        if (totalItemCount > 0) {
            listState.animateScrollToItem(totalItemCount - 1)
        }
    }

    val (badgeText, badgeColor) = when (val engineState = uiState.engineLoadState) {
        is EngineLoadState.Ready -> "READY // ${engineState.backendUsed}" to MaterialTheme.colorScheme.primary
        is EngineLoadState.Loading -> "LOADING…" to MaterialTheme.colorScheme.secondary
        is EngineLoadState.Error -> "ENGINE ERROR" to MaterialTheme.colorScheme.error
        is EngineLoadState.Unloaded -> "UNLOADED" to MaterialTheme.colorScheme.tertiary
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
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.hud_brand_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
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
                    HudBadge(
                        text = badgeText,
                        color = badgeColor,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )

            if (uiState.engineLoadState is EngineLoadState.Loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainer
                )
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
                            size = 176.dp,
                            animationEnabled = uiState.settings.orbAnimationEnabled,
                            glowIntensity = uiState.settings.orbGlowIntensity
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = when (uiState.engineLoadState) {
                                is EngineLoadState.Ready -> "GEMMA 3 1B ONLINE"
                                is EngineLoadState.Loading -> "INITIALIZING LITERT-LM"
                                else -> stringResource(R.string.chat_empty_title)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val subtitle = when (val load = uiState.engineLoadState) {
                            is EngineLoadState.Ready ->
                                "Local LiteRT-LM session ready on ${load.backendUsed}. Native heap: ${String.format(Locale.US, "%.1f", uiState.nativeMemoryMb)} MB. Context: ${uiState.settings.contextLengthTokens} tok."
                            is EngineLoadState.Loading ->
                                "Loading ${load.modelName} weights on ${load.targetBackend} off the UI thread…"
                            is EngineLoadState.Error -> load.message
                            is EngineLoadState.Unloaded ->
                                if (uiState.activeModel?.artifactState == ArtifactState.READY) {
                                    "${uiState.activeModel.displayName} is verified and ready to load offline."
                                } else {
                                    "Offline LiteRT-LM runtime ready. Download or import Gemma 3 1B Int4 in Models to begin local generation."
                                }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("chat_message_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            ChatMessageBubble(message = message)
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

            // Quick Diagnostic & Model Control Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.primary,
                                leadingIconContentColor = MaterialTheme.colorScheme.primary
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
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.primary,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary
                        ),
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
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.secondary,
                            leadingIconContentColor = MaterialTheme.colorScheme.secondary
                        ),
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
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("chip_inspect_models")
                    )
                }
            }

            // Composer Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            tint = MaterialTheme.colorScheme.primary
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
                                style = MaterialTheme.typography.bodyMedium
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    )

                    if (uiState.isGenerating) {
                        FilledIconButton(
                            onClick = onCancelGeneration,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
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
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
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
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
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
                    Text(
                        text = "${modelLabel.uppercase(Locale.getDefault())} // STREAMING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$backendUsed • ~$estimatedTokens tok",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = if (text.isEmpty()) "Prefilling prompt…" else "$text▌",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: MessageEntity) {
    val isUser = message.role == MessageRole.USER
    val isSystemNotice = message.role == MessageRole.SYSTEM_NOTICE
    val isToolSnapshot = message.role == MessageRole.TOOL_SNAPSHOT

    val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

    val containerColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        isSystemNotice -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
        isToolSnapshot -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        isSystemNotice -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
        isToolSnapshot -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outline
    }
    val labelColor = when {
        isUser -> MaterialTheme.colorScheme.primary
        isSystemNotice -> MaterialTheme.colorScheme.tertiary
        isToolSnapshot -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(if (isUser) 0.84f else 0.94f),
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.modelLabel.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor
                    )
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = message.content,
                    style = if (isToolSnapshot) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (message.role == MessageRole.ASSISTANT &&
                    (message.ttftMs != null || message.tokensPerSecond != null)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                    val metricsText = buildString {
                        message.ttftMs?.let { append("TTFT ${it}ms") }
                        message.tokensPerSecond?.let {
                            if (isNotEmpty()) append(" • ")
                            append(String.format(Locale.US, "%.1f tok/s", it))
                        }
                        message.outputTokens?.let {
                            if (isNotEmpty()) append(" • ")
                            append("$it tok")
                        }
                        message.backendUsed?.let {
                            if (isNotEmpty()) append(" • ")
                            append(it)
                        }
                    }
                    Text(
                        text = metricsText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.testTag("message_benchmark_footer")
                    )
                }
            }
        }
    }
}
