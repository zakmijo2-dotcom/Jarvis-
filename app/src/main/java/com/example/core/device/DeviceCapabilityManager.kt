package com.example.core.device

import com.example.core.tools.AccessibilityBridge
import com.example.core.tools.NotificationBridge
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DeviceCapabilityManager(
    val shizukuController: ShizukuController,
    val wirelessAdbController: WirelessAdbController,
    val permissionManager: PermissionManager,
    settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshTick = MutableStateFlow(0)

    val capabilities: StateFlow<DeviceCapabilitySnapshot> = combine(
        shizukuController.status,
        wirelessAdbController.status,
        NotificationBridge.isServiceConnected,
        AccessibilityBridge.isServiceConnected,
        settingsRepository.settingsFlow,
        refreshTick
    ) { values ->
        val shizuku = values[0] as ShizukuStatusSnapshot
        val wireless = values[1] as WirelessAdbStatusSnapshot
        val notifConnected = values[2] as Boolean
        val a11yConnected = values[3] as Boolean
        val settings = values[4] as com.example.data.repository.JarvisSettings
        val perms = permissionManager.inspectPermissions()

        DeviceCapabilitySnapshot(
            shizuku = shizuku,
            wirelessAdb = wireless,
            hasContactsPermission = perms.hasContactsPermission,
            hasAudioPermission = perms.hasAudioPermission,
            hasPostNotificationsPermission = perms.hasPostNotificationsPermission,
            notificationListenerAuthorized = perms.notificationListenerAuthorized,
            notificationListenerConnected = notifConnected,
            accessibilityAuthorized = perms.accessibilityAuthorized,
            accessibilityConnected = a11yConnected,
            accessibilityOptInEnabled = settings.accessibilityAutomationEnabled
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DeviceCapabilitySnapshot()
    )

    fun refreshAll() {
        shizukuController.refreshStatus()
        refreshTick.value += 1
    }

    fun release() {
        scope.cancel()
    }
}
