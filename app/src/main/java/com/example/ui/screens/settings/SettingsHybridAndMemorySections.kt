package com.example.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.agent.AgentRoutingMode
import com.example.core.cloud.CloudProviderKind
import com.example.core.memory.MemoryFact
import com.example.core.memory.MemoryKind
import com.example.core.web.WebSearchProviderKind
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.theme.JarvisMonoCodeStyle
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
    onSetWebApiKey: (String) -> Unit,
    onSetBraveApiKey: (String) -> Unit = {},
    onSetSerpApiKey: (String) -> Unit = {}
) {
    val settings = uiState.settings
    var customUrlDraft by remember(settings.webSearchCustomUrl) { mutableStateOf(settings.webSearchCustomUrl) }
    var webKeyDraft by remember(settings.webSearchApiKey) { mutableStateOf(settings.webSearchApiKey) }
    var braveKeyDraft by remember(settings.braveSearchApiKey) { mutableStateOf(settings.braveSearchApiKey) }
    var serpKeyDraft by remember(settings.serpApiKey) { mutableStateOf(settings.serpApiKey) }

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
                    "Local models are offline by default. Enable this to run live web search and public webpage extraction.",
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
        when (settings.webSearchProvider) {
            WebSearchProviderKind.BRAVE_SEARCH -> {
                OutlinedTextField(
                    value = braveKeyDraft,
                    onValueChange = { braveKeyDraft = it },
                    label = { Text("Brave Search API key (encrypted)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("brave_api_key_input")
                )
                TextButton(
                    onClick = { onSetBraveApiKey(braveKeyDraft) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("save_brave_key_button")
                ) { Text("Save Brave key") }
            }
            WebSearchProviderKind.SERPAPI -> {
                OutlinedTextField(
                    value = serpKeyDraft,
                    onValueChange = { serpKeyDraft = it },
                    label = { Text("SerpAPI key (encrypted)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("serp_api_key_input")
                )
                TextButton(
                    onClick = { onSetSerpApiKey(serpKeyDraft) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("save_serp_key_button")
                ) { Text("Save SerpAPI key") }
            }
            else -> if (settings.webSearchProvider.requiresApiKey || settings.webSearchProvider == WebSearchProviderKind.CUSTOM_JSON) {
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
    onDeleteSkill: (String) -> Unit,
    onExportSkillsJson: () -> Unit = {},
    onImportSkillsJson: (String) -> Unit = {},
    onClearExportedSkillsJson: () -> Unit = {}
) {
    val context = LocalContext.current
    val palette = JarvisThemeTokens.palette
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importDraft by rememberSaveable { mutableStateOf("") }

    val filteredSkills = remember(uiState.skills, searchQuery) {
        val needle = searchQuery.trim().lowercase(Locale.US)
        if (needle.isEmpty()) {
            uiState.skills
        } else {
            uiState.skills.filter { skill ->
                skill.name.lowercase(Locale.US).contains(needle) ||
                    skill.description.lowercase(Locale.US).contains(needle) ||
                    skill.triggerPattern.lowercase(Locale.US).contains(needle) ||
                    skill.targetPackage.lowercase(Locale.US).contains(needle) ||
                    skill.steps.any { it.toolName.lowercase(Locale.US).contains(needle) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_skills_library_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Filter skills by name, trigger, or tool") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("skills_search_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onExportSkillsJson,
                enabled = uiState.skills.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("btn_export_skills_json")
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export JSON")
            }
            OutlinedButton(
                onClick = { showImportDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("btn_import_skills_json")
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import JSON")
            }
        }

        if (filteredSkills.isEmpty()) {
            Text(
                text = if (uiState.skills.isEmpty()) {
                    "No saved skills yet. Safe multi-step workflows (excluding confirmation-gated actions) can be saved via /tool save_skill or distilled after verified runs."
                } else {
                    "No skills match \"$searchQuery\"."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val skills = filteredSkills.take(16)
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                                tint = palette.errorAccent
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

    val exportedJson = uiState.exportedSkillsJson
    if (exportedJson != null) {
        AlertDialog(
            onDismissRequest = onClearExportedSkillsJson,
            title = { Text("Exported skills JSON") },
            text = {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.surfaceHigh,
                    border = BorderStroke(1.dp, palette.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    Text(
                        text = exportedJson,
                        style = JarvisMonoCodeStyle,
                        color = palette.text,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("JARVIS Skills JSON", exportedJson))
                        onClearExportedSkillsJson()
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.lavenderContainer,
                        contentColor = palette.onHue
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_copy_exported_skills")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy JSON")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onClearExportedSkillsJson,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) { Text("Close") }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import skills JSON") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste an exported JARVIS skill JSON payload. Confirmation-gated or unsafe skills are rejected automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importDraft,
                        onValueChange = { importDraft = it },
                        label = { Text("Skills JSON") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("skills_import_json_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportSkillsJson(importDraft)
                        importDraft = ""
                        showImportDialog = false
                    },
                    enabled = importDraft.isNotBlank(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.lavenderContainer,
                        contentColor = palette.onHue
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_confirm_import_skills")
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImportDialog = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AppKnowledgeBaseCard(
    uiState: SettingsUiState,
    onDeleteKnowledge: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val palette = JarvisThemeTokens.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_app_knowledge_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.appKnowledge.isEmpty()) {
            Text(
                text = "No learned app screens yet. JARVIS automatically records verified non-sensitive UI element patterns and workflow hints as it completes Android automation tasks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.appKnowledge.size} verified screen profiles stored locally",
                    style = JarvisMonoTelemetryStyle,
                    color = palette.textSecondary
                )
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_clear_app_knowledge")
                ) {
                    Text("Clear all", color = palette.errorAccent)
                }
            }

            val entries = uiState.appKnowledge.take(15)
            Column(modifier = Modifier.fillMaxWidth()) {
                entries.forEachIndexed { idx, record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
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
                                    text = record.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                HudBadge(
                                    text = record.screenType,
                                    color = palette.onHue,
                                    containerColor = palette.lavenderContainer
                                )
                                HudBadge(
                                    text = "${record.successCount} ok",
                                    color = palette.onHue,
                                    containerColor = palette.successContainer
                                )
                            }
                            Text(
                                text = record.packageName,
                                style = JarvisMonoTelemetryStyle,
                                color = palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (record.knownElements.isNotEmpty()) {
                                Text(
                                    text = "Elements: ${record.knownElements.take(6).joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (record.verifiedWorkflowHints.isNotBlank()) {
                                Text(
                                    text = "Hints: ${record.verifiedWorkflowHints}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDeleteKnowledge(record.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_app_knowledge_${record.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete learned app screen",
                                tint = palette.errorAccent
                            )
                        }
                    }
                    if (idx < entries.lastIndex) {
                        EditorialHairlineDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun ExecutionHistoryCard(uiState: SettingsUiState) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
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
