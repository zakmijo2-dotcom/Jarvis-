package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.agent.AgentRoutingMode
import com.example.core.cloud.CloudProviderKind
import com.example.core.memory.MemoryFact
import com.example.core.memory.MemoryKind
import com.example.core.web.WebSearchProviderKind
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisThemeTokens
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

    val palette = JarvisThemeTokens.palette
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = palette.lavenderContainer,
        selectedLabelColor = palette.onHue
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_hybrid_routing_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Execution routing", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AgentRoutingMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.routingMode == mode,
                    onClick = { onSetRoutingMode(mode) },
                    label = { Text(mode.label) },
                    colors = chipColors,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("routing_chip_${mode.name.lowercase()}")
                )
            }
        }

        EditorialHairlineDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Allow cloud transmission", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Requires explicit consent before prompts or transcripts leave the device. Nothing is uploaded when off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.cloudUploadConsentGranted,
                onCheckedChange = onSetCloudUploadConsent,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("switch_cloud_consent")
            )
        }

        EditorialHairlineDivider()

        Text("Cloud provider", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CloudProviderKind.entries.forEach { kind ->
                FilterChip(
                    selected = settings.cloudProviderKind == kind,
                    onClick = { onSetCloudProvider(kind) },
                    label = { Text(kind.label) },
                    colors = chipColors,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("cloud_provider_chip_${kind.id}")
                )
            }
        }

        if (settings.cloudProviderKind.availableModels.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                settings.cloudProviderKind.availableModels.forEach { preset ->
                    FilterChip(
                        selected = modelDraft == preset,
                        onClick = {
                            modelDraft = preset
                            onSetCloudModelId(preset)
                        },
                        label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                        colors = chipColors,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
            }
        }

        OutlinedTextField(
            value = endpointDraft,
            onValueChange = { endpointDraft = it },
            label = { Text("Provider endpoint URL") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cloud_endpoint_input")
        )
        OutlinedTextField(
            value = modelDraft,
            onValueChange = { modelDraft = it },
            label = { Text("Cloud model ID") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cloud_model_input")
        )
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            label = { Text("Personal cloud API key (encrypted)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cloud_api_key_input")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onSetCloudEndpoint(endpointDraft)
                    onSetCloudModelId(modelDraft)
                },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) { Text("Save endpoint") }
            Button(
                onClick = { onSetCloudApiKey(keyDraft) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.lavenderContainer,
                    contentColor = palette.onHue
                ),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("save_cloud_key_button")
            ) { Text("Save API key") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Request timeout",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${settings.cloudTimeoutSeconds}s",
                style = JarvisMonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = settings.cloudTimeoutSeconds.toFloat(),
            onValueChange = { onSetCloudTimeoutSeconds((it / 5f).roundToInt() * 5) },
            valueRange = 5f..120f
        )

        val stats = uiState.cloudUsageStats
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cloud usage",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onResetUsageStats,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) { Text("Reset") }
            }
            Text(
                text = "${stats.totalRequests} requests (${stats.successfulRequests} ok / ${stats.failedRequests} failed) · ${stats.totalPromptTokensEst + stats.totalCompletionTokensEst} est. tokens",
                style = JarvisMonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Last: ${stats.lastProviderUsed} (${stats.lastLatencyMs} ms)${stats.lastErrorMessage?.let { " · $it" } ?: ""}",
                style = JarvisMonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_web_research_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Web search tool", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Local models are offline by default. Enable this to run live web search queries via the chosen provider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.webResearchEnabled,
                onCheckedChange = onSetWebResearchEnabled,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("switch_web_research")
            )
        }
        val palette = JarvisThemeTokens.palette
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WebSearchProviderKind.entries.forEach { provider ->
                FilterChip(
                    selected = settings.webSearchProvider == provider,
                    onClick = { onSetWebSearchProvider(provider) },
                    label = { Text(provider.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.lavenderContainer,
                        selectedLabelColor = palette.onHue
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("web_provider_${provider.name.lowercase()}")
                )
            }
        }
        if (settings.webSearchProvider == WebSearchProviderKind.CUSTOM_JSON) {
            OutlinedTextField(
                value = customUrlDraft,
                onValueChange = { customUrlDraft = it },
                label = { Text("Custom search JSON URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = { onSetWebCustomUrl(customUrlDraft) },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) { Text("Save custom URL") }
        }
        if (settings.webSearchProvider.requiresApiKey || settings.webSearchProvider == WebSearchProviderKind.CUSTOM_JSON) {
            OutlinedTextField(
                value = webKeyDraft,
                onValueChange = { webKeyDraft = it },
                label = { Text("Web search API key (encrypted)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = { onSetWebApiKey(webKeyDraft) },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) { Text("Save web key") }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_memory_manager_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = uiState.memorySearchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search preferences, summaries, facts…") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("memory_search_input")
        )
        val palette = JarvisThemeTokens.palette
        Button(
            onClick = { showAddDialog = true },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.lavenderContainer,
                contentColor = palette.onHue
            ),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("add_memory_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add memory")
        }
        if (uiState.memories.isEmpty()) {
            Text(
                "No stored memory records match your filter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val items = uiState.memories.take(12)
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { idx, fact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
                                text = "${fact.kind.label} · ${fact.category}",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.lavenderAccent
                            )
                            Text(
                                text = fact.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { editingFact = fact },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("edit_memory_${fact.id}")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit memory")
                        }
                        IconButton(
                            onClick = { onDeleteMemory(fact.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_memory_${fact.id}")
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete memory",
                                tint = palette.errorAccent
                            )
                        }
                    }
                    if (idx < items.lastIndex) {
                        EditorialHairlineDivider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MemoryEditorDialog(
            title = "Add memory",
            initialKind = MemoryKind.PREFERENCE,
            initialCategory = "User preference",
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
            title = "Edit memory",
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_skills_library_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (uiState.skills.isEmpty()) {
            Text(
                text = "No saved skills yet. Safe multi-step workflows (excluding confirmation-gated actions) can be saved via /tool save_skill or distilled after verified runs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val skills = uiState.skills.take(12)
            Column(modifier = Modifier.fillMaxWidth()) {
                skills.forEachIndexed { idx, skill ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${skill.name} v${skill.version}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val palette = JarvisThemeTokens.palette
                                HudBadge(
                                    text = "${skill.usageCount} runs",
                                    color = palette.onHue,
                                    containerColor = palette.lavenderContainer
                                )
                            }
                            Text(
                                text = skill.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Steps: ${skill.steps.joinToString(" -> ") { it.toolName }} · ${skill.provenance}",
                                style = JarvisMonoTelemetryStyle,
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
                                tint = JarvisThemeTokens.palette.errorAccent
                            )
                        }
                    }
                    if (idx < skills.lastIndex) {
                        EditorialHairlineDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun ExecutionHistoryCard(uiState: SettingsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_execution_history_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (uiState.executionHistory.isEmpty()) {
            Text(
                "No autonomous agent runs logged yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            val records = uiState.executionHistory.take(8)
            Column(modifier = Modifier.fillMaxWidth()) {
                records.forEachIndexed { idx, rec ->
                    val ok = rec.status == "COMPLETED" || rec.status == "SUCCESS"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${rec.routeTaken} · ${fmt.format(Date(rec.timestamp))}",
                                style = JarvisMonoTelemetryStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val palette = JarvisThemeTokens.palette
                            HudBadge(
                                text = "${rec.status.lowercase()} (${rec.durationMs} ms)",
                                color = if (ok) {
                                    palette.onHue
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                containerColor = if (ok) {
                                    palette.lavenderContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                        }
                        Text(
                            text = rec.userPrompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (rec.stepsSummary.isNotBlank()) {
                            Text(
                                text = rec.stepsSummary,
                                style = JarvisMonoTelemetryStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (idx < records.lastIndex) {
                        EditorialHairlineDivider()
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

    val palette = JarvisThemeTokens.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showKindSelector) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemoryKind.entries.forEach { k ->
                            FilterChip(
                                selected = kind == k,
                                onClick = { kind = k },
                                label = { Text(k.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.lavenderContainer,
                                    selectedLabelColor = palette.onHue
                                ),
                                modifier = Modifier.minimumInteractiveComponentSize()
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_editor_content_input")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = consentSensitive,
                        onCheckedChange = { consentSensitive = it },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
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
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.lavenderContainer,
                    contentColor = palette.onHue
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("memory_editor_save_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) { Text("Cancel") }
        }
    )
}
