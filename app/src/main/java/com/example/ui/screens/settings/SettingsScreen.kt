package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.agent.AgentRoutingMode
import com.example.core.cloud.CloudProviderKind
import com.example.core.memory.MemoryKind
import com.example.core.web.WebSearchProviderKind
import com.example.data.repository.InferenceBackendPreference
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.HudPanel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSetOrbAnimation: (Boolean) -> Unit,
    onSetOrbGlow: (Float) -> Unit,
    onSetTtsAutoSpeak: (Boolean) -> Unit,
    onSetTtsSpeechRate: (Float) -> Unit,
    onSetWakeWordStandby: (Boolean) -> Unit,
    onSetTelemetryInterval: (Int) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetInferenceBackend: (InferenceBackendPreference) -> Unit,
    onSetContextLengthTokens: (Int) -> Unit,
    onSetMaxOutputTokens: (Int) -> Unit,
    onSetSamplerTemperature: (Float) -> Unit,
    onSetSamplerTopK: (Int) -> Unit,
    onSetSamplerTopP: (Float) -> Unit,
    onSetHuggingFaceToken: (String) -> Unit,
    onPurgeHistory: () -> Unit,
    onDismissBanner: () -> Unit,
    onSetRoutingMode: (AgentRoutingMode) -> Unit = {},
    onSetCloudUploadConsent: (Boolean) -> Unit = {},
    onSetCloudProvider: (CloudProviderKind) -> Unit = {},
    onSetCloudEndpoint: (String) -> Unit = {},
    onSetCloudModelId: (String) -> Unit = {},
    onSetCloudApiKey: (String) -> Unit = {},
    onSetCloudTimeoutSeconds: (Int) -> Unit = {},
    onResetCloudUsageStats: () -> Unit = {},
    onSetWebResearchEnabled: (Boolean) -> Unit = {},
    onSetWebSearchProvider: (WebSearchProviderKind) -> Unit = {},
    onSetWebCustomUrl: (String) -> Unit = {},
    onSetWebApiKey: (String) -> Unit = {},
    onMemorySearchQueryChange: (String) -> Unit = {},
    onAddMemory: (MemoryKind, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onEditMemory: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onDeleteMemory: (String) -> Unit = {},
    onDeleteSkill: (String) -> Unit = {}
) {
    var showPurgeDialog by remember { mutableStateOf(false) }
    val settings = uiState.settings
    var hfTokenDraft by remember(settings.huggingFaceToken) { mutableStateOf(settings.huggingFaceToken) }

    HudBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag("settings_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HudBadge(text = "DATASTORE PERSISTED")
                }
            }

            item {
                AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                    HudPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 840.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.statusBannerMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissBanner) {
                                Text("OK")
                            }
                        }
                    }
                }
            }

            item {
                SettingsGroupCard(
                    title = "LOCAL AI INFERENCE (LITERT-LM)",
                    icon = Icons.Filled.Memory
                ) {
                    Text(
                        text = "Acceleration Backend",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InferenceBackendPreference.entries.forEach { pref ->
                            FilterChip(
                                selected = settings.inferenceBackend == pref,
                                onClick = { onSetInferenceBackend(pref) },
                                label = { Text(pref.label) },
                                modifier = Modifier.testTag("backend_chip_${pref.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Context Length Ceiling: ${settings.contextLengthTokens} tokens",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1024, 2048, 4096, 8192).forEach { ctx ->
                            FilterChip(
                                selected = settings.contextLengthTokens == ctx,
                                onClick = { onSetContextLengthTokens(ctx) },
                                label = { Text("$ctx") },
                                modifier = Modifier.testTag("context_chip_$ctx")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Max Output Tokens: ${settings.maxOutputTokens}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = settings.maxOutputTokens.toFloat(),
                        onValueChange = { onSetMaxOutputTokens((it / 64f).roundToInt() * 64) },
                        valueRange = 64f..2048f,
                        modifier = Modifier.testTag("slider_max_output_tokens")
                    )

                    Text(
                        text = String.format(Locale.US, "Temperature: %.2f", settings.samplerTemperature),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = settings.samplerTemperature,
                        onValueChange = onSetSamplerTemperature,
                        valueRange = 0.0f..1.5f,
                        modifier = Modifier.testTag("slider_temperature")
                    )

                    Text(
                        text = "Top-K: ${settings.samplerTopK}  •  Top-P: ${String.format(Locale.US, "%.2f", settings.samplerTopP)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = settings.samplerTopK.toFloat(),
                        onValueChange = { onSetSamplerTopK(it.roundToInt()) },
                        valueRange = 1f..100f
                    )
                    Slider(
                        value = settings.samplerTopP,
                        onValueChange = onSetSamplerTopP,
                        valueRange = 0.1f..1.0f
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hfTokenDraft,
                        onValueChange = { hfTokenDraft = it },
                        label = { Text("Hugging Face Read Token (optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hf_token_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reload model in Models after changing context/backend.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onSetHuggingFaceToken(hfTokenDraft) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("save_hf_token_button")
                        ) {
                            Text("Save Key")
                        }
                    }
                }
            }

            item {
                HybridRoutingAndCloudCard(
                    uiState = uiState,
                    onSetRoutingMode = onSetRoutingMode,
                    onSetCloudUploadConsent = onSetCloudUploadConsent,
                    onSetCloudProvider = onSetCloudProvider,
                    onSetCloudEndpoint = onSetCloudEndpoint,
                    onSetCloudModelId = onSetCloudModelId,
                    onSetCloudApiKey = onSetCloudApiKey,
                    onSetCloudTimeoutSeconds = onSetCloudTimeoutSeconds,
                    onResetUsageStats = onResetCloudUsageStats
                )
            }

            item {
                WebResearchSettingsCard(
                    uiState = uiState,
                    onSetWebResearchEnabled = onSetWebResearchEnabled,
                    onSetWebSearchProvider = onSetWebSearchProvider,
                    onSetWebCustomUrl = onSetWebCustomUrl,
                    onSetWebApiKey = onSetWebApiKey
                )
            }

            item {
                LocalMemoryManagerCard(
                    uiState = uiState,
                    onSearchQueryChange = onMemorySearchQueryChange,
                    onAddMemory = onAddMemory,
                    onEditMemory = onEditMemory,
                    onDeleteMemory = onDeleteMemory
                )
            }

            item {
                SkillsLibraryCard(
                    uiState = uiState,
                    onDeleteSkill = onDeleteSkill
                )
            }

            item {
                ExecutionHistoryCard(uiState = uiState)
            }

            item {
                SettingsGroupCard(
                    title = "ORB & VISUAL ENGINE",
                    icon = Icons.Filled.Tune
                ) {
                    SettingsSwitchRow(
                        label = "Animated Telemetry Orb",
                        description = "Run continuous counter-rotating orbital arcs",
                        checked = settings.orbAnimationEnabled,
                        onCheckedChange = onSetOrbAnimation,
                        testTag = "switch_orb_animation"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.US, "Orb Glow Intensity: %.0f%%", settings.orbGlowIntensity * 100f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = settings.orbGlowIntensity,
                        onValueChange = onSetOrbGlow,
                        valueRange = 0.2f..1.0f,
                        modifier = Modifier.testTag("slider_orb_glow")
                    )
                    SettingsSwitchRow(
                        label = "HUD Haptic Feedback",
                        description = "Tactile response on primary navigation actions",
                        checked = settings.hapticsEnabled,
                        onCheckedChange = onSetHaptics,
                        testTag = "switch_haptics"
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = "VOICE & SPEECH SYNTHESIS",
                    icon = Icons.Filled.GraphicEq
                ) {
                    SettingsSwitchRow(
                        label = "Auto-Speak System Notices",
                        description = "Read voice console diagnostics via Android TTS",
                        checked = settings.ttsAutoSpeak,
                        onCheckedChange = onSetTtsAutoSpeak,
                        testTag = "switch_tts_autospeak"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.US, "TTS Speech Rate: %.2fx", settings.ttsSpeechRate),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = settings.ttsSpeechRate,
                        onValueChange = onSetTtsSpeechRate,
                        valueRange = 0.6f..1.6f,
                        modifier = Modifier.testTag("slider_tts_rate")
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = "WAKE WORD & TELEMETRY",
                    icon = Icons.Filled.Radar
                ) {
                    SettingsSwitchRow(
                        label = "Wake Word Standby (\"${uiState.wakeWordKeyword}\")",
                        description = "Interface status: ${uiState.wakeWordState.label} (KWS requires Porcupine key / ONNX mic test)",
                        checked = settings.wakeWordStandbyEnabled,
                        onCheckedChange = onSetWakeWordStandby,
                        testTag = "switch_wake_word"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Telemetry Polling Interval",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 5, 10, 15).forEach { sec ->
                            FilterChip(
                                selected = settings.telemetryPollingSeconds == sec,
                                onClick = { onSetTelemetryInterval(sec) },
                                label = { Text("${sec}s") },
                                modifier = Modifier.testTag("polling_chip_${sec}s")
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroupCard(
                    title = "LOCAL MEMORY & ARCHIVE",
                    icon = Icons.Filled.Storage
                ) {
                    Text(
                        text = "Stored Local Memory Facts: ${uiState.memoryFactsCount}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showPurgeDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("purge_archive_button")
                    ) {
                        Icon(imageVector = Icons.Filled.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.widthIn(min = 8.dp))
                        Text(stringResource(R.string.settings_clear_history))
                    }
                }
            }
        }
    }

    if (showPurgeDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeDialog = false },
            title = { Text("Purge Conversation Archive?") },
            text = {
                Text("This permanently deletes all local Room conversations, messages, and memory facts on this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPurgeHistory()
                        showPurgeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Purge All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
