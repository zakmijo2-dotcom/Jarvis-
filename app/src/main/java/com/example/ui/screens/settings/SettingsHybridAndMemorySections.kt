package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.agent.AgentRoutingMode
import com.example.core.cloud.CloudProviderKind
import com.example.core.memory.MemoryFact
import com.example.core.memory.MemoryKind
import com.example.core.web.WebSearchProviderKind
import com.example.ui.components.HudBadge
import com.example.ui.components.HudPanel
import com.example.ui.theme.NominalGreen
import com.example.ui.theme.SignalAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HybridRoutingAndCloudCard(
    uiState: SettingsUiState,
    onSetRoutingMode: (AgentRoutingMode) -> Unit,
    onSetCloudUploadConsent: (Boolean) -> Unit,
    onSetCloudProvider: (CloudProviderKind) -> Unit,
    onSetCloudEndpoint: (String) -> Unit,
    onSetCloudModelId: (String) -> Unit,
    onSetCloudApiKey: (String) -> Unit,
    onSetCloudTimeoutSeconds: (Int) -> Unit,
    onResetUsageStats: () -> Unit
) {
    val settings = uiState.settings
    var endpointDraft by remember(settings.cloudEndpointUrl, settings.cloudProviderKind) {
        mutableStateOf(settings.cloudEndpointUrl.ifBlank { settings.cloudProviderKind.defaultEndpoint })
    }
    var modelDraft by remember(settings.cloudModelId, settings.cloudProviderKind) {
        mutableStateOf(settings.cloudModelId.ifBlank { settings.cloudProviderKind.defaultModel })
    }
    var keyDraft by remember(settings.cloudApiKey) { mutableStateOf(settings.cloudApiKey) }

    SectionCard(
        title = "HYBRID AGENT ROUTING & CLOUD AI",
        icon = Icons.Filled.Cloud,
        tag = "settings_hybrid_routing_card"
    ) {
        Text("Execution Routing Policy", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AgentRoutingMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.routingMode == mode,
                    onClick = { onSetRoutingMode(mode) },
                    label = { Text(mode.label) },
                    modifier = Modifier.testTag("routing_chip_${mode.name.lowercase()}")
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Allow Cloud API Transmission", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Explicit user consent required before prompts or transcripts leave the device. Never uploads audio or chat when OFF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.cloudUploadConsentGranted,
                onCheckedChange = onSetCloudUploadConsent,
                modifier = Modifier.testTag("switch_cloud_consent")
            )
        }

        Spacer(Modifier.height(10.dp))
        Text("Configurable Cloud Provider", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CloudProviderKind.entries.forEach { kind ->
                FilterChip(
                    selected = settings.cloudProviderKind == kind,
                    onClick = { onSetCloudProvider(kind) },
                    label = { Text(kind.label) },
                    modifier = Modifier.testTag("cloud_provider_chip_${kind.id}")
                )
            }
        }

        if (settings.cloudProviderKind.availableModels.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                settings.cloudProviderKind.availableModels.forEach { preset ->
                    FilterChip(
                        selected = modelDraft == preset,
                        onClick = {
                            modelDraft = preset
                            onSetCloudModelId(preset)
                        },
                        label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = endpointDraft,
            onValueChange = { endpointDraft = it },
            label = { Text("Provider Endpoint URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("cloud_endpoint_input")
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = modelDraft,
            onValueChange = { modelDraft = it },
            label = { Text("Cloud Model ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("cloud_model_input")
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            label = { Text("Personal Cloud API Key (Keystore Encrypted)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("cloud_api_key_input")
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onSetCloudEndpoint(endpointDraft)
                    onSetCloudModelId(modelDraft)
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save Endpoint/Model") }
            Button(
                onClick = { onSetCloudApiKey(keyDraft) },
                modifier = Modifier.weight(1f).testTag("save_cloud_key_button")
            ) { Text("Save API Key") }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Request Timeout: ${settings.cloudTimeoutSeconds}s",
            style = MaterialTheme.typography.labelLarge
        )
        Slider(
            value = settings.cloudTimeoutSeconds.toFloat(),
            onValueChange = { onSetCloudTimeoutSeconds((it / 5f).roundToInt() * 5) },
            valueRange = 5f..120f
        )

        val stats = uiState.cloudUsageStats
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CLOUD USAGE MONITOR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = onResetUsageStats) { Text("Reset") }
                }
                Text(
                    "Requests: ${stats.totalRequests} (OK ${stats.successfulRequests} / Fail ${stats.failedRequests}) • Est. Tokens: ${stats.totalPromptTokensEst + stats.totalCompletionTokensEst}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Last: ${stats.lastProviderUsed} (${stats.lastLatencyMs} ms)${stats.lastErrorMessage?.let { " • Err: $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebResearchSettingsCard(
    uiState: SettingsUiState,
    onSetWebResearchEnabled: (Boolean) -> Unit,
    onSetWebSearchProvider: (WebSearchProviderKind) -> Unit,
    onSetWebCustomUrl: (String) -> Unit,
    onSetWebApiKey: (String) -> Unit
) {
    val settings = uiState.settings
    var customUrlDraft by remember(settings.webSearchCustomUrl) { mutableStateOf(settings.webSearchCustomUrl) }
    var webKeyDraft by remember(settings.webSearchApiKey) { mutableStateOf(settings.webSearchApiKey) }

    SectionCard(
        title = "OPTIONAL WEB RESEARCH SERVICE",
        icon = Icons.Filled.Public,
        tag = "settings_web_research_card"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Enable Internet Research Tool", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Local AI models have NO built-in internet access. Enabling this allows explicit live search queries via the selected provider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.webResearchEnabled,
                onCheckedChange = onSetWebResearchEnabled,
                modifier = Modifier.testTag("switch_web_research")
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WebSearchProviderKind.entries.forEach { provider ->
                FilterChip(
                    selected = settings.webSearchProvider == provider,
                    onClick = { onSetWebSearchProvider(provider) },
                    label = { Text(provider.label) },
                    modifier = Modifier.testTag("web_provider_${provider.name.lowercase()}")
                )
            }
        }
        if (settings.webSearchProvider == WebSearchProviderKind.CUSTOM_JSON) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = customUrlDraft,
                onValueChange = { customUrlDraft = it },
                label = { Text("Custom Search JSON URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { onSetWebCustomUrl(customUrlDraft) }) { Text("Save Custom Search URL") }
        }
        if (settings.webSearchProvider.requiresApiKey || settings.webSearchProvider == WebSearchProviderKind.CUSTOM_JSON) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = webKeyDraft,
                onValueChange = { webKeyDraft = it },
                label = { Text("Web Search API Key (Encrypted)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { onSetWebApiKey(webKeyDraft) }) { Text("Save Web Search Key") }
        }
    }
}

@Composable
fun LocalMemoryManagerCard(
    uiState: SettingsUiState,
    onSearchQueryChange: (String) -> Unit,
    onAddMemory: (MemoryKind, String, String, Boolean) -> Unit,
    onEditMemory: (String, String, String, Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingFact by remember { mutableStateOf<MemoryFact?>(null) }

    SectionCard(
        title = "SEARCHABLE LOCAL MEMORY (${uiState.memoryFactsCount})",
        icon = Icons.Filled.Psychology,
        tag = "settings_memory_manager_card"
    ) {
        OutlinedTextField(
            value = uiState.memorySearchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search preferences, summaries, facts…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("memory_search_input")
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.testTag("add_memory_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(" Add Approved Memory")
        }
        Spacer(Modifier.height(8.dp))
        if (uiState.memories.isEmpty()) {
            Text(
                "No stored memory records match your filter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.memories.take(12).forEach { fact ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${fact.kind.label.uppercase(Locale.getDefault())} • ${fact.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(fact.content, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(
                                onClick = { editingFact = fact },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_memory_${fact.id}")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit memory")
                            }
                            IconButton(
                                onClick = { onDeleteMemory(fact.id) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_memory_${fact.id}")
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete memory", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MemoryEditorDialog(
            title = "Add User-Approved Memory",
            initialKind = MemoryKind.PREFERENCE,
            initialCategory = "User Preference",
            initialContent = "",
            showKindSelector = true,
            onDismiss = { showAddDialog = false },
            onSave = { kind, cat, content, consent ->
                onAddMemory(kind, cat, content, consent)
                showAddDialog = false
            }
        )
    }

    val toEdit = editingFact
    if (toEdit != null) {
        MemoryEditorDialog(
            title = "Edit Local Memory",
            initialKind = toEdit.kind,
            initialCategory = toEdit.category,
            initialContent = toEdit.content,
            showKindSelector = false,
            onDismiss = { editingFact = null },
            onSave = { _, cat, content, consent ->
                onEditMemory(toEdit.id, cat, content, consent)
                editingFact = null
            }
        )
    }
}

@Composable
fun SkillsLibraryCard(
    uiState: SettingsUiState,
    onDeleteSkill: (String) -> Unit
) {
    SectionCard(
        title = "REUSABLE SKILLS LIBRARY (${uiState.skills.size})",
        icon = Icons.Filled.AutoFixHigh,
        tag = "settings_skills_library_card"
    ) {
        if (uiState.skills.isEmpty()) {
            Text(
                text = "No procedural skills saved yet. Safe multi-step workflows (excluding dangerous/confirmed actions) can be saved via /tool save_skill or auto-distilled after verified runs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.skills.take(12).forEach { skill ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${skill.name} v${skill.version}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    HudBadge(
                                        text = "${skill.usageCount} RUNS",
                                        color = NominalGreen
                                    )
                                }
                                Text(
                                    text = skill.description,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Steps: ${skill.steps.joinToString(" → ") { it.toolName }} • ${skill.provenance}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeleteSkill(skill.id) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("delete_skill_${skill.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete skill",
                                    tint = MaterialTheme.colorScheme.error
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
fun ExecutionHistoryCard(uiState: SettingsUiState) {
    SectionCard(
        title = "AGENT EXECUTION HISTORY (${uiState.executionHistory.size})",
        icon = Icons.Filled.History,
        tag = "settings_execution_history_card"
    ) {
        if (uiState.executionHistory.isEmpty()) {
            Text(
                "No autonomous agent runs logged yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.executionHistory.take(8).forEach { rec ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "${rec.routeTaken} • ${fmt.format(Date(rec.timestamp))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                HudBadge(
                                    text = "${rec.status} (${rec.durationMs}ms)",
                                    color = if (rec.status == "COMPLETED" || rec.status == "SUCCESS") NominalGreen else SignalAmber
                                )
                            }
                            Text(rec.userPrompt, style = MaterialTheme.typography.bodySmall)
                            if (rec.stepsSummary.isNotBlank()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                Text(
                                    rec.stepsSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryEditorDialog(
    title: String,
    initialKind: MemoryKind,
    initialCategory: String,
    initialContent: String,
    showKindSelector: Boolean,
    onDismiss: () -> Unit,
    onSave: (MemoryKind, String, String, Boolean) -> Unit
) {
    var kind by remember { mutableStateOf(initialKind) }
    var category by remember { mutableStateOf(initialCategory) }
    var content by remember { mutableStateOf(initialContent) }
    var consentSensitive by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showKindSelector) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MemoryKind.entries.forEach { k ->
                            FilterChip(
                                selected = kind == k,
                                onClick = { kind = k },
                                label = { Text(k.label) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("memory_editor_content_input")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consentSensitive, onCheckedChange = { consentSensitive = it })
                    Text(
                        "Explicitly consent if this note contains sensitive info",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(kind, category, content, consentSensitive) },
                enabled = content.isNotBlank(),
                modifier = Modifier.testTag("memory_editor_save_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    tag: String,
    content: @Composable () -> Unit
) {
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .testTag(tag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}
