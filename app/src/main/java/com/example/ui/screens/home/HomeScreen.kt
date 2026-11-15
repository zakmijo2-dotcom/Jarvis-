package com.example.ui.screens.home

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ai.EngineLoadState
import com.example.core.voice.MicStatus
import com.example.data.local.db.ConversationWithPreview
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBackground
import com.example.ui.components.JarvisOrb
import com.example.ui.components.OrbMode
import com.example.ui.components.hairlineDp
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JarvisThemeTokens

@Composable
fun HomeScreen(
    displayName: String,
    activeModelName: String,
    engineLoadState: EngineLoadState,
    micStatus: MicStatus,
    batteryPercent: Int,
    freeRamMb: Long,
    conversations: List<ConversationWithPreview>,
    isModelGenerating: Boolean,
    orbAnimationEnabled: Boolean,
    orbGlowIntensity: Float,
    onOpenVoice: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onSeeAllHistory: () -> Unit
) {
    val palette = JarvisThemeTokens.palette
    val effectiveName = displayName.ifBlank { stringResource(R.string.home_default_name) }
    val orbMode = when {
        micStatus == MicStatus.ACTIVE_STT -> OrbMode.LISTENING
        isModelGenerating -> OrbMode.PROCESSING
        else -> OrbMode.IDLE
    }

    HudBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: app title & settings menu/avatar pill
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = palette.surface,
                        border = BorderStroke(hairlineDp(), palette.hairline)
                    ) {
                        Text(
                            text = stringResource(R.string.hud_brand_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    val settingsCd = stringResource(R.string.cd_open_settings)
                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable(onClick = onOpenSettings)
                            .testTag("home_settings_button"),
                        shape = CircleShape,
                        color = palette.lavenderContainer,
                        border = BorderStroke(hairlineDp(), palette.hairline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = settingsCd,
                                tint = palette.onHue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.nav_settings),
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.onHue
                            )
                        }
                    }
                }
            }

            // Friendly Greeting + Headline
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_greeting_prefix, effectiveName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textSecondary,
                        modifier = Modifier.testTag("home_greeting_text")
                    )
                    Text(
                        text = stringResource(R.string.home_headline),
                        style = MaterialTheme.typography.displaySmall,
                        color = palette.text,
                        modifier = Modifier.testTag("home_headline_text")
                    )
                }
            }

            // Bento Grid
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Hero Voice Bento Tile (Peach)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(onClick = onOpenVoice)
                            .testTag("home_bento_voice"),
                        shape = RoundedCornerShape(24.dp),
                        color = palette.peachContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.GraphicEq,
                                        contentDescription = null,
                                        tint = palette.onHue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.nav_voice),
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = palette.onHue
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.home_tile_voice_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.onHue.copy(alpha = 0.84f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = if (palette.isDark) 0.34f else 0.56f)
                                ) {
                                    Text(
                                        text = micStatus.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.onHue,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            JarvisOrb(
                                mode = orbMode,
                                size = 84.dp,
                                animationEnabled = orbAnimationEnabled,
                                glowIntensity = orbGlowIntensity,
                                isModelGenerating = isModelGenerating,
                                statusDescription = stringResource(R.string.nav_voice)
                            )
                        }
                    }

                    // Middle Bento Pair: Chat (Sky) + Models (Sage)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BentoSectionTile(
                            title = stringResource(R.string.nav_chat),
                            subtitle = stringResource(R.string.home_tile_chat_subtitle),
                            meta = "${conversations.size}",
                            icon = Icons.Outlined.ChatBubbleOutline,
                            containerColor = palette.skyContainer,
                            inkColor = palette.onHue,
                            onClick = onOpenChat,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("home_bento_chat")
                        )
                        val engineStatus = when (engineLoadState) {
                            is EngineLoadState.Ready -> "Ready"
                            is EngineLoadState.Loading -> "Loading"
                            is EngineLoadState.Error -> "Error"
                            is EngineLoadState.Unloaded -> "Standby"
                        }
                        BentoSectionTile(
                            title = stringResource(R.string.nav_models),
                            subtitle = activeModelName.ifBlank { stringResource(R.string.home_tile_models_subtitle) },
                            meta = engineStatus,
                            icon = Icons.Outlined.Memory,
                            containerColor = palette.sageContainer,
                            inkColor = palette.onHue,
                            onClick = onOpenModels,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("home_bento_models")
                        )
                    }

                    // Device Bento Tile (Sand)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(onClick = onOpenDevice)
                            .testTag("home_bento_device"),
                        shape = RoundedCornerShape(22.dp),
                        color = palette.sandContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MonitorHeart,
                                contentDescription = null,
                                tint = palette.onHue,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.nav_device),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = palette.onHue
                                )
                                Text(
                                    text = stringResource(R.string.home_tile_device_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.onHue.copy(alpha = 0.80f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "$batteryPercent% · ${freeRamMb}MB",
                                style = JarvisMonoTelemetryStyle,
                                color = palette.onHue
                            )
                        }
                    }
                }
            }

            // Recent History Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.home_history_header),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.text
                        )
                        TextButton(
                            onClick = onSeeAllHistory,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_history_see_all")
                        ) {
                            Text(
                                text = stringResource(R.string.home_history_see_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.skyAccent
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = palette.surface,
                        border = BorderStroke(hairlineDp(), palette.hairline)
                    ) {
                        if (conversations.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.home_history_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
                            }
                        } else {
                            val recent = conversations.take(4)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                recent.forEachIndexed { index, convo ->
                                    if (index > 0) {
                                        EditorialHairlineDivider()
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .clickable { onSelectConversation(convo.id) }
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                            .testTag("home_history_item_${convo.id}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.History,
                                            contentDescription = null,
                                            tint = palette.skyAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = convo.title.ifBlank { "Conversation" },
                                                style = MaterialTheme.typography.titleSmall,
                                                color = palette.text,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val preview = convo.lastMessage?.trim().orEmpty()
                                            if (preview.isNotEmpty()) {
                                                Text(
                                                    text = preview,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = palette.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = palette.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BentoSectionTile(
    title: String,
    subtitle: String,
    meta: String,
    icon: ImageVector,
    containerColor: Color,
    inkColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = inkColor,
                    modifier = Modifier.size(22.dp)
                )
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.42f)
                ) {
                    Text(
                        text = meta,
                        style = JarvisMonoTelemetryStyle,
                        color = inkColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = inkColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = inkColor.copy(alpha = 0.80f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(name = "Home Light", showBackground = true)
@Preview(name = "Home Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenPreview() {
    JarvisTheme {
        HomeScreen(
            displayName = "Alex",
            activeModelName = "Gemma 3 1B",
            engineLoadState = EngineLoadState.Unloaded,
            micStatus = MicStatus.OFF,
            batteryPercent = 82,
            freeRamMb = 3120L,
            conversations = listOf(
                ConversationWithPreview(
                    id = "1",
                    title = "Morning briefing & schedule",
                    createdAt = 1000L,
                    updatedAt = 2000L,
                    pinned = true,
                    lastMessage = "Battery is at 82%, no conflicts before 10:30.",
                    messageCount = 4
                ),
                ConversationWithPreview(
                    id = "2",
                    title = "Summarize architecture notes",
                    createdAt = 1000L,
                    updatedAt = 1500L,
                    pinned = false,
                    lastMessage = "Key action items saved to memory.",
                    messageCount = 6
                )
            ),
            isModelGenerating = false,
            orbAnimationEnabled = false,
            orbGlowIntensity = 0.85f,
            onOpenVoice = {},
            onOpenChat = {},
            onOpenModels = {},
            onOpenDevice = {},
            onOpenSettings = {},
            onSelectConversation = {},
            onSeeAllHistory = {}
        )
    }
}
