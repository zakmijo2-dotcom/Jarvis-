package com.example.ui.screens.settings

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.agent.AgentRoutingMode
import com.example.core.cloud.CloudProviderKind
import com.example.core.memory.MemoryKind
import com.example.core.web.WebSearchProviderKind
import com.example.data.repository.InferenceBackendPreference
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBackground
import com.example.ui.components.HudPanel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JarvisThemeTokens
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
    onDeleteSkill: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    var showPurgeDialog by remember { mutableStateOf(false) }
    val settings = uiState.settings
    var hfTokenDraft by remember(settings.huggingFaceToken) { mutableStateOf(settings.huggingFaceToken) }

    var localExpanded by rememberSaveable { mutableStateOf(true) }
    var cloudExpanded by rememberSaveable { mutableStateOf(false) }
    var webExpanded by rememberSaveable { mutableStateOf(false) }
    var memoryExpanded by rememberSaveable { mutableStateOf(false) }
    var skillsExpanded by rememberSaveable { mutableStateOf(false) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var voiceExpanded by rememberSaveable { mutableStateOf(false) }
    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var dataExpanded by rememberSaveable { mutableStateOf(false) }

    HudBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag("settings_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val headerPalette = JarvisThemeTokens.palette
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 840.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = headerPalette.text
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = headerPalette.lavenderContainer
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                tint = headerPalette.onHue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = headerPalette.text
                    )
                }
            }

            if (uiState.statusBannerMessage != null) {
                item {
                    val bannerPalette = JarvisThemeTokens.palette
                    HudPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 840.dp),
                        containerColor = bannerPalette.lavenderContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.statusBannerMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = bannerPalette.onHue,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onDismissBanner,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("OK", color = bannerPalette.onHue)
                            }
                        }
                    }
                }
            }

            // 1. Local inference
            item {
                CollapsibleSettingsSection(
                    title = "Local inference",
                    summary = "${settings.inferenceBackend.label} · ${settings.contextLengthTokens} ctx",
                    icon = Icons.Filled.Memory,
                    expanded = localExpanded,
                    onToggle = { localExpanded = !localExpanded },
                    headerTestTag = "settings_section_local"
                ) {
                    Text(
                        text = "Acceleration backend",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val localPalette = JarvisThemeTokens.palette
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InferenceBackendPreference.entries.forEach { pref ->
                            FilterChip(
                                selected = settings.inferenceBackend == pref,
                                onClick = { onSetInferenceBackend(pref) },
                                label = { Text(pref.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = localPalette.lavenderContainer,
                                    selectedLabelColor = localPalette.onHue
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("backend_chip_${pref.name.lowercase()}")
                            )
                        }
                    }

                    EditorialHairlineDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Context length",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${settings.contextLengthTokens} tokens",
                            style = JarvisMonoTelemetryStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1024, 2048, 4096, 8192).forEach { ctx ->
                            FilterChip(
                                selected = settings.contextLengthTokens == ctx,
                                onClick = { onSetContextLengthTokens(ctx) },
                                label = { Text("$ctx") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = localPalette.lavenderContainer,
                                    selectedLabelColor = localPalette.onHue
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("context_chip_$ctx")
                            )
                        }
                    }

                    EditorialHairlineDivider()

                    SliderLabelRow(
                        label = "Max output tokens",
                        valueText = "${settings.maxOutputTokens}"
                    )
                    Slider(
                        value = settings.maxOutputTokens.toFloat(),
                        onValueChange = { onSetMaxOutputTokens((it / 64f).roundToInt() * 64) },
                        valueRange = 64f..2048f,
                        modifier = Modifier.testTag("slider_max_output_tokens")
                    )

                    SliderLabelRow(
                        label = "Temperature",
                        valueText = String.format(Locale.US, "%.2f", settings.samplerTemperature)
                    )
                    Slider(
                        value = settings.samplerTemperature,
                        onValueChange = onSetSamplerTemperature,
                        valueRange = 0.0f..1.5f,
                        modifier = Modifier.testTag("slider_temperature")
                    )

                    SliderLabelRow(
                        label = "Top-K and Top-P",
                        valueText = "${settings.samplerTopK} · ${String.format(Locale.US, "%.2f", settings.samplerTopP)}"
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

                    EditorialHairlineDivider()

                    OutlinedTextField(
                        value = hfTokenDraft,
                        onValueChange = { hfTokenDraft = it },
                        label = { Text("Hugging Face read token (optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hf_token_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reload the active model after changing context or backend.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        val keyPalette = JarvisThemeTokens.palette
                        Button(
                            onClick = { onSetHuggingFaceToken(hfTokenDraft) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = keyPalette.lavenderContainer,
                                contentColor = keyPalette.onHue
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("save_hf_token_button")
                        ) {
                            Text("Save key")
                        }
                    }
                }
            }

            // 2. Routing and cloud
            item {
                CollapsibleSettingsSection(
                    title = "Routing and cloud",
                    summary = "${settings.routingMode.label} · ${if (settings.cloudUploadConsentGranted) settings.cloudProviderKind.label else "Cloud off"}",
                    icon = Icons.Filled.Cloud,
                    expanded = cloudExpanded,
                    onToggle = { cloudExpanded = !cloudExpanded },
                    headerTestTag = "settings_section_cloud"
                ) {
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
            }

            // 3. Web search
            item {
                CollapsibleSettingsSection(
                    title = "Web search",
                    summary = if (settings.webResearchEnabled) settings.webSearchProvider.label else "Disabled",
                    icon = Icons.Filled.Public,
                    expanded = webExpanded,
                    onToggle = { webExpanded = !webExpanded },
                    headerTestTag = "settings_section_web"
                ) {
                    WebResearchSettingsCard(
                        uiState = uiState,
                        onSetWebResearchEnabled = onSetWebResearchEnabled,
                        onSetWebSearchProvider = onSetWebSearchProvider,
                        onSetWebCustomUrl = onSetWebCustomUrl,
                        onSetWebApiKey = onSetWebApiKey
                    )
                }
            }

            // 4. Memory
            item {
                CollapsibleSettingsSection(
                    title = "Memory",
                    summary = "${uiState.memoryFactsCount} saved",
                    icon = Icons.Filled.Psychology,
                    expanded = memoryExpanded,
                    onToggle = { memoryExpanded = !memoryExpanded },
                    headerTestTag = "settings_section_memory"
                ) {
                    LocalMemoryManagerCard(
                        uiState = uiState,
                        onSearchQueryChange = onMemorySearchQueryChange,
                        onAddMemory = onAddMemory,
                        onEditMemory = onEditMemory,
                        onDeleteMemory = onDeleteMemory
                    )
                }
            }

            // 5. Saved skills
            item {
                CollapsibleSettingsSection(
                    title = "Saved skills",
                    summary = "${uiState.skills.size} saved",
                    icon = Icons.Filled.AutoFixHigh,
                    expanded = skillsExpanded,
                    onToggle = { skillsExpanded = !skillsExpanded },
                    headerTestTag = "settings_section_skills"
                ) {
                    SkillsLibraryCard(
                        uiState = uiState,
                        onDeleteSkill = onDeleteSkill
                    )
                }
            }

            // 6. Agent history
            item {
                CollapsibleSettingsSection(
                    title = "Agent history",
                    summary = "${uiState.executionHistory.size} runs",
                    icon = Icons.Filled.History,
                    expanded = historyExpanded,
                    onToggle = { historyExpanded = !historyExpanded },
                    headerTestTag = "settings_section_history"
                ) {
                    ExecutionHistoryCard(uiState = uiState)
                }
            }

            // 7. Voice and wake word
            item {
                CollapsibleSettingsSection(
                    title = "Voice and wake word",
                    summary = "${if (settings.wakeWordStandbyEnabled) "Standby on" else "Standby off"} · ${String.format(Locale.US, "%.1fx", settings.ttsSpeechRate)}",
                    icon = Icons.Filled.GraphicEq,
                    expanded = voiceExpanded,
                    onToggle = { voiceExpanded = !voiceExpanded },
                    headerTestTag = "settings_section_voice"
                ) {
                    SettingsSwitchRow(
                        label = "Auto-speak responses",
                        description = "Read voice console replies and notices aloud via Android TTS",
                        checked = settings.ttsAutoSpeak,
                        onCheckedChange = onSetTtsAutoSpeak,
                        testTag = "switch_tts_autospeak"
                    )
                    EditorialHairlineDivider()
                    SliderLabelRow(
                        label = "Speech rate",
                        valueText = String.format(Locale.US, "%.2fx", settings.ttsSpeechRate)
                    )
                    Slider(
                        value = settings.ttsSpeechRate,
                        onValueChange = onSetTtsSpeechRate,
                        valueRange = 0.6f..1.6f,
                        modifier = Modifier.testTag("slider_tts_rate")
                    )
                    EditorialHairlineDivider()
                    SettingsSwitchRow(
                        label = "Wake word standby (\"${uiState.wakeWordKeyword}\")",
                        description = "Status: ${uiState.wakeWordState.label} (KWS uses Porcupine key or audio gate)",
                        checked = settings.wakeWordStandbyEnabled,
                        onCheckedChange = onSetWakeWordStandby,
                        testTag = "switch_wake_word"
                    )
                }
            }

            // 8. Appearance and telemetry
            item {
                CollapsibleSettingsSection(
                    title = "Appearance and telemetry",
                    summary = "Polling ${settings.telemetryPollingSeconds}s",
                    icon = Icons.Filled.Tune,
                    expanded = appearanceExpanded,
                    onToggle = { appearanceExpanded = !appearanceExpanded },
                    headerTestTag = "settings_section_appearance"
                ) {
                    SettingsSwitchRow(
                        label = "Animate voice sphere",
                        description = "Slow breathing, listening reactivity, and gentle rotation (paused off-screen)",
                        checked = settings.orbAnimationEnabled,
                        onCheckedChange = onSetOrbAnimation,
                        testTag = "switch_orb_animation"
                    )
                    SliderLabelRow(
                        label = "Sphere shading response",
                        valueText = String.format(Locale.US, "%.0f%%", settings.orbGlowIntensity * 100f)
                    )
                    Slider(
                        value = settings.orbGlowIntensity,
                        onValueChange = onSetOrbGlow,
                        valueRange = 0.2f..1.0f,
                        modifier = Modifier.testTag("slider_orb_glow")
                    )
                    EditorialHairlineDivider()
                    SettingsSwitchRow(
                        label = "Haptic feedback",
                        description = "Subtle tactile pulse on primary navigation actions",
                        checked = settings.hapticsEnabled,
                        onCheckedChange = onSetHaptics,
                        testTag = "switch_haptics"
                    )
                    EditorialHairlineDivider()
                    Text(
                        text = "Telemetry polling interval",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val pollPalette = JarvisThemeTokens.palette
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 5, 10, 15).forEach { sec ->
                            FilterChip(
                                selected = settings.telemetryPollingSeconds == sec,
                                onClick = { onSetTelemetryInterval(sec) },
                                label = { Text("${sec}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = pollPalette.lavenderContainer,
                                    selectedLabelColor = pollPalette.onHue
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("polling_chip_${sec}s")
                            )
                        }
                    }
                }
            }

            // 9. Data and archive
            item {
                CollapsibleSettingsSection(
                    title = "Data and archive",
                    summary = "${uiState.memoryFactsCount} stored facts",
                    icon = Icons.Filled.Storage,
                    expanded = dataExpanded,
                    onToggle = { dataExpanded = !dataExpanded },
                    headerTestTag = "settings_section_data"
                ) {
                    val dataPalette = JarvisThemeTokens.palette
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stored local memory facts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = dataPalette.textSecondary
                        )
                        Text(
                            text = "${uiState.memoryFactsCount}",
                            style = JarvisMonoTelemetryStyle,
                            color = dataPalette.text
                        )
                    }
                    OutlinedButton(
                        onClick = { showPurgeDialog = true },
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = dataPalette.errorAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("purge_archive_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_clear_history))
                    }
                }
            }
        }
    }

    if (showPurgeDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeDialog = false },
            title = { Text("Clear conversation archive?") },
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
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPurgeDialog = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CollapsibleSettingsSection(
    title: String,
    summary: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    headerTestTag: String,
    content: @Composable () -> Unit
) {
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag(headerTestTag)
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sectionPalette = JarvisThemeTokens.palette
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = sectionPalette.lavenderAccent,
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!expanded) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(animationSpec = tween(150))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditorialHairlineDivider()
                content()
            }
        }
    }
}

@Composable
private fun SliderLabelRow(
    label: String,
    valueText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = valueText,
            style = JarvisMonoTelemetryStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
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
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        )
    }
}

@Preview(name = "Settings Light", showBackground = true)
@Preview(name = "Settings Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    JarvisTheme {
        SettingsScreen(
            uiState = SettingsUiState(memoryFactsCount = 4),
            onSetOrbAnimation = {},
            onSetOrbGlow = {},
            onSetTtsAutoSpeak = {},
            onSetTtsSpeechRate = {},
            onSetWakeWordStandby = {},
            onSetTelemetryInterval = {},
            onSetHaptics = {},
            onSetInferenceBackend = {},
            onSetContextLengthTokens = {},
            onSetMaxOutputTokens = {},
            onSetSamplerTemperature = {},
            onSetSamplerTopK = {},
            onSetSamplerTopP = {},
            onSetHuggingFaceToken = {},
            onPurgeHistory = {},
            onDismissBanner = {}
        )
    }
}
