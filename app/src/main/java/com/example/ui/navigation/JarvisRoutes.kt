package com.example.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object ChatRoute

@Serializable
data object VoiceRoute

@Serializable
data object ModelsRoute

@Serializable
data object MoreRoute

@Serializable
data object DeviceRoute

@Serializable
data object SettingsRoute

enum class TopDestination(
    val route: Any,
    val routeQualifiedName: String,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(
        route = HomeRoute,
        routeQualifiedName = HomeRoute::class.qualifiedName.orEmpty(),
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_tab_home"
    ),
    CHAT(
        route = ChatRoute,
        routeQualifiedName = ChatRoute::class.qualifiedName.orEmpty(),
        labelRes = R.string.nav_chat,
        selectedIcon = Icons.Filled.ChatBubble,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        testTag = "nav_tab_chat"
    ),
    MODELS(
        route = ModelsRoute,
        routeQualifiedName = ModelsRoute::class.qualifiedName.orEmpty(),
        labelRes = R.string.nav_models,
        selectedIcon = Icons.Filled.Memory,
        unselectedIcon = Icons.Outlined.Memory,
        testTag = "nav_tab_models"
    ),
    MORE(
        route = MoreRoute,
        routeQualifiedName = MoreRoute::class.qualifiedName.orEmpty(),
        labelRes = R.string.nav_more,
        selectedIcon = Icons.Filled.GridView,
        unselectedIcon = Icons.Outlined.GridView,
        testTag = "nav_tab_more"
    )
}
