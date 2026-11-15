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
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ai.EngineLoadState
import com.example.core.device.DeviceCapabilitySnapshot
import com.example.core.device.ShizukuAvailabilityState
import com.example.core.device.WirelessAdbConnectionState
import com.example.core.voice.MicStatus
import com.example.data.local.db.ConversationWithPreview
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.HudPanel
import com.example.ui.components.JarvisOrb
import com.example.ui.components.OrbMode
import com.example.ui.components.TelemetryProgressBar
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
    totalRamMb: Long = 0L,
    thermalLabel: String = "Nominal",
    startupDurationMs: Long? = null,
    capabilities: DeviceCapabilitySnapshot = DeviceCapabilitySnapshot(),
    conversations: List<ConversationWithPreview>,
    isModelGenerating: Boolean,
    orbAnimationEnabled: Boolean,
    orbGlowIntensity: Float,
    onOpenVoice: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenBridgeSetup: () -> Unit = {},
    onOpenActivityHistory: () -> Unit = {},
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
    val engineReady = engineLoadState is EngineLoadState.Ready
    val shizukuReady = capabilities.shizuku.availability == ShizukuAvailabilityState.READY
    val adbConnected = capabilities.wirelessAdb.connectionState == WirelessAdbConnectionState.CONNECTED
    val jarvisReadinessText = when {
        isModelGenerating -> "Active · Generating"
        engineReady -> "Ready · Local AI online"
        shizukuReady || adbConnected -> "Standby · Bridge online"
        else -> "Standby · Model unloaded"
    }

    HudBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: JARVIS console badge + Readiness + Settings
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = palette.surface,
                            border = BorderStroke(hairlineDp(), palette.hairline)
                        ) {
                            Text(
                                text = stringResource(R.string.hud_brand_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.skyAccent,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        if (startupDurationMs != null) {
                            HudBadge(
                                text = "${startupDurationMs}ms boot",
                                color = palette.textSecondary,
                                containerColor = palette.surfaceHigh
                            )
                        }
                    }

                    val settingsCd = stringResource(R.string.cd_open_settings)
                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable(onClick = onOpenSettings)
                            .testTag("home_settings_button"),
                        shape = CircleShape,
                        color = palette.surface,
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
                                tint = palette.skyAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.nav_settings),
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.text
                            )
                        }
                    }
                }
            }

            // Greeting + Headline
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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

            // Primary Voice Hero Tile with interactive JARVIS Orb (immediately visible above the fold)
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenVoice)
                        .testTag("home_bento_voice"),
                    shape = RoundedCornerShape(22.dp),
                    color = palette.surface,
                    border = BorderStroke(hairlineDp(), palette.skyAccent.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.cyanBadgeBg
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.GraphicEq,
                                        contentDescription = null,
                                        tint = palette.skyAccent,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(18.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.nav_voice),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = palette.text
                                )
                            }
                            Text(
                                text = stringResource(R.string.home_tile_voice_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            HudBadge(
                                text = micStatus.label,
                                color = palette.skyAccent,
                                containerColor = palette.cyanBadgeBg
                            )
                        }

                        JarvisOrb(
                            mode = orbMode,
                            size = 80.dp,
                            animationEnabled = orbAnimationEnabled,
                            glowIntensity = orbGlowIntensity,
                            isModelGenerating = isModelGenerating,
                            statusDescription = stringResource(R.string.nav_voice)
                        )
                    }
                }
            }

            // JARVIS System Status Telemetry Card (Model, Shizuku, Wireless ADB, Memory, Thermal, Battery)
            item {
                HudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .testTag("home_status_telemetry_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "SYSTEM STATUS",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.skyAccent
                            )
                            Text(
                                text = jarvisReadinessText,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text
                            )
                        }
                        HudBadge(
                            text = if (shizukuReady) "Shizuku Ready" else capabilities.activePrivilegedBackend.label,
                            color = if (shizukuReady || adbConnected) palette.onHue else palette.textSecondary,
                            containerColor = if (shizukuReady || adbConnected) palette.sageContainer else palette.surfaceHigh
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    val usedRamMb = (totalRamMb - freeRamMb).coerceAtLeast(0L)
                    val ramRatio = if (totalRamMb > 0L) usedRamMb.toFloat() / totalRamMb.toFloat() else 0f
                    TelemetryProgressBar(
                        label = "Available memory · $thermalLabel · Batt $batteryPercent%",
                        valueText = "${freeRamMb} MB free",
                        progress = if (totalRamMb > 0L) ramRatio else 0.15f,
                        accentColor = palette.skyAccent
                    )

                    EditorialHairlineDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusChipPill(
                            label = "Model",
                            value = activeModelName.ifBlank { "None" },
                            accent = if (engineReady) palette.sageAccent else palette.textSecondary,
                            onClick = onOpenModels,
                            modifier = Modifier.weight(1f)
                        )
                        StatusChipPill(
                            label = "Shizuku / ADB",
                            value = when {
                                shizukuReady -> "Shizuku"
                                adbConnected -> "Wireless ADB"
                                else -> capabilities.shizuku.availability.label
                            },
                            accent = if (shizukuReady || adbConnected) palette.skyAccent else palette.sandAccent,
                            onClick = onOpenBridgeSetup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_bridge_status_chip")
                        )
                    }
                }
            }

            // Quick Action Tiles: Chat/Models Pair + Device & Secondary Shortcuts
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HomeActionTile(
                            title = stringResource(R.string.nav_chat),
                            subtitle = stringResource(R.string.home_tile_chat_subtitle),
                            meta = "${conversations.size} chats",
                            icon = Icons.Outlined.ChatBubbleOutline,
                            badgeBg = palette.cyanBadgeBg,
                            accentColor = palette.skyAccent,
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
                            is EngineLoadState.Unloaded -> "Unloaded"
                        }
                        HomeActionTile(
                            title = stringResource(R.string.nav_models),
                            subtitle = activeModelName.ifBlank { stringResource(R.string.home_tile_models_subtitle) },
                            meta = engineStatus,
                            icon = Icons.Outlined.Memory,
                            badgeBg = palette.tealBadgeBg,
                            accentColor = palette.sageAccent,
                            onClick = onOpenModels,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("home_bento_models")
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenDevice)
                            .testTag("home_bento_device"),
                        shape = RoundedCornerShape(20.dp),
                        color = palette.surface,
                        border = BorderStroke(hairlineDp(), palette.hairline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = palette.amberBadgeBg
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MonitorHeart,
                                    contentDescription = null,
                                    tint = palette.sandAccent,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(20.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.nav_device),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.text
                                )
                                Text(
                                    text = stringResource(R.string.home_tile_device_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "$batteryPercent% · ${freeRamMb}MB",
                                style = JarvisMonoTelemetryStyle,
                                color = palette.sandAccent
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactQuickActionPill(
                            title = "Shizuku & ADB",
                            icon = Icons.Outlined.Cable,
                            accent = palette.skyAccent,
                            onClick = onOpenBridgeSetup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_quick_bridge")
                        )
                        CompactQuickActionPill(
                            title = "Activity Log",
                            icon = Icons.Outlined.Shield,
                            accent = palette.lavenderAccent,
                            onClick = onOpenActivityHistory,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_quick_history")
                        )
                    }
                }
            }

            // Recent History
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
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
        }
    }
}

@Composable
private fun StatusChipPill(
    label: String,
    value: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = JarvisThemeTokens.palette
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = palette.surfaceHigh,
        border = BorderStroke(hairlineDp(), palette.hairline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
            Text(
                text = value,
                style = JarvisMonoTelemetryStyle,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeActionTile(
    title: String,
    subtitle: String,
    meta: String,
    icon: ImageVector,
    badgeBg: Color,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = JarvisThemeTokens.palette
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = palette.surface,
        border = BorderStroke(hairlineDp(), palette.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeBg
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp)
                    )
                }
                Text(
                    text = meta,
                    style = JarvisMonoTelemetryStyle,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
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
private fun CompactQuickActionPill(
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = JarvisThemeTokens.palette
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = palette.surface,
        border = BorderStroke(hairlineDp(), palette.hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            totalRamMb = 8192L,
            thermalLabel = "Nominal",
            startupDurationMs = 285L,
            conversations = listOf(
                ConversationWithPreview(
                    id = "1",
                    title = "Morning briefing & schedule",
                    createdAt = 1000L,
                    updatedAt = 2000L,
                    pinned = true,
                    lastMessage = "Battery is at 82%, no conflicts before 10:30.",
                    messageCount = 4
                )
            ),
            isModelGenerating = false,
            orbAnimationEnabled = false,
            orbGlowIntensity = 0.85f,
            onOpenVoice = {},
            onOpenChat = {},
            onOpenModels = {},
            onOpenDevice = {},
            onOpenBridgeSetup = {},
            onOpenActivityHistory = {},
            onOpenSettings = {},
            onSelectConversation = {},
            onSeeAllHistory = {}
        )
    }
}
