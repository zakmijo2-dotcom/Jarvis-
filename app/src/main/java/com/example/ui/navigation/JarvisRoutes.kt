package com.example.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import kotlinx.serialization.Serializable

@Serializable
data object ChatRoute

@Serializable
data object VoiceRoute

@Serializable
data object ModelsRoute

@Serializable
data object DeviceRoute

@Serializable
data object SettingsRoute

enum class TopDestination(
    val route: Any,
    val routeQualifiedName: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CHAT(
        route = ChatRoute,
        routeQualifiedName = ChatRoute::class.qualifiedName.orEmpty(),
        labelRes = R.string.nav_chat,
        selectedIcon = Icons.Filled.ChatBubble,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        testTag = "nav_tab_chat"
    ),
    VOICE(
        route = VoiceRoute,
        routeQualifiedName = VoiceRoute::class.qualifiedName.orEmpty(),
        labelRes = R.string.nav_voice,
        selectedIcon = Icons.Filled.GraphicEq,
        unselectedIcon = Icons.Outlined.GraphicEq,
        testTag = "nav_tab_voice"
    ),
    MODELS(
        route = ModelsRoute,
        routeQualifiedName = ModelsRoute::class.qualifiedName.orEmpty(),
        labelRes = R.string.nav_models,
        selectedIcon = Icons.Filled.Memory,
        unselectedIcon = Icons.Outlined.Memory,
        testTag = "nav_tab_models"
    ),
    DEVICE(
        route = DeviceRoute,
        routeQualifiedName = DeviceRoute::class.qualifiedName.orEmpty(),
        labelRes = R.string.nav_device,
        selectedIcon = Icons.Filled.MonitorHeart,
        unselectedIcon = Icons.Outlined.MonitorHeart,
        testTag = "nav_tab_device"
    ),
    SETTINGS(
        route = SettingsRoute,
        routeQualifiedName = SettingsRoute::class.qualifiedName.orEmpty(),
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
}
