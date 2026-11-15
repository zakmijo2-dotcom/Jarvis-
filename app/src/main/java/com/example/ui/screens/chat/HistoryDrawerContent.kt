package com.example.ui.screens.chat

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.db.ConversationWithPreview
import com.example.ui.components.EditorialHairlineDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JarvisThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryDrawerContent(
    conversations: List<ConversationWithPreview>,
    activeConversationId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectConversation: (String) -> Unit,
    onCreateNewConversation: () -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onTogglePinned: (String, Boolean) -> Unit,
    onDeleteConversation: (String) -> Unit
) {
    var renamingTarget by remember { mutableStateOf<ConversationWithPreview?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    ModalDrawerSheet(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .testTag("history_drawer_sheet"),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerTonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.drawer_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${conversations.size} saved",
                        style = JarvisMonoTelemetryStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val palette = JarvisThemeTokens.palette
                FilledTonalButton(
                    onClick = onCreateNewConversation,
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = palette.skyContainer,
                        contentColor = palette.onHue
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("new_session_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.drawer_new_session),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input"),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                placeholder = {
                    Text(
                        text = stringResource(R.string.drawer_search_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search sessions"
                    )
                }
            )

            EditorialHairlineDivider()

            if (conversations.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.drawer_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(conversations, key = { _, item -> item.id }) { index, item ->
                        val isSelected = item.id == activeConversationId
                        ConversationDrawerItem(
                            item = item,
                            isSelected = isSelected,
                            onSelect = { onSelectConversation(item.id) },
                            onStartRename = {
                                renamingTarget = item
                                renameDraft = item.title
                            },
                            onTogglePin = { onTogglePinned(item.id, item.pinned) },
                            onDelete = { onDeleteConversation(item.id) }
                        )
                        if (index < conversations.lastIndex) {
                            EditorialHairlineDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }

    val target = renamingTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renamingTarget = null },
            title = { Text("Rename session", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameConversation(target.id, renameDraft)
                        renamingTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ConversationDrawerItem(
    item: ConversationWithPreview,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onStartRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val timeText = remember(item.updatedAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.updatedAt))
    }

    val palette = JarvisThemeTokens.palette
    val containerBg = if (isSelected) {
        palette.skyContainer
    } else {
        palette.surface
    }
    val itemTitleColor = if (isSelected) palette.onHue else palette.text
    val itemSubColor = if (isSelected) palette.onHue.copy(alpha = 0.78f) else palette.textSecondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(containerBg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("history_item_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = itemTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${item.messageCount}",
                style = JarvisMonoTelemetryStyle,
                color = itemSubColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.lastMessage?.takeIf { it.isNotBlank() } ?: "Empty session",
            style = MaterialTheme.typography.bodySmall,
            color = itemSubColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeText,
                style = JarvisMonoTelemetryStyle,
                color = itemSubColor
            )
            Row {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.cd_pin_session),
                        tint = if (item.pinned) (if (isSelected) palette.onHue else palette.skyAccent) else itemSubColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onStartRename,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.cd_rename_session),
                        tint = itemSubColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_session),
                        tint = if (isSelected) palette.onHue else palette.errorAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "Drawer Light", showBackground = true)
@Preview(name = "Drawer Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryDrawerPreview() {
    JarvisTheme {
        HistoryDrawerContent(
            conversations = listOf(
                ConversationWithPreview(
                    id = "1",
                    title = "Battery check",
                    createdAt = 1700000000000L,
                    updatedAt = 1700000100000L,
                    pinned = true,
                    lastMessage = "Battery is at 84% and charging via USB-C.",
                    messageCount = 4
                ),
                ConversationWithPreview(
                    id = "2",
                    title = "Local model benchmark",
                    createdAt = 1699990000000L,
                    updatedAt = 1699990500000L,
                    pinned = false,
                    lastMessage = "Gemma 3 1B averaged 18.4 tok/s on GPU.",
                    messageCount = 6
                )
            ),
            activeConversationId = "1",
            searchQuery = "",
            onSearchQueryChange = {},
            onSelectConversation = {},
            onCreateNewConversation = {},
            onRenameConversation = { _, _ -> },
            onTogglePinned = { _, _ -> },
            onDeleteConversation = {}
        )
    }
}
