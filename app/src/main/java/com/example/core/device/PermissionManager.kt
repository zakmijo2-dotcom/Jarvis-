package com.example.core.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.core.tools.AccessibilityBridge
import com.example.core.tools.NotificationBridge
import com.example.data.repository.JarvisSettings

data class RuntimePermissionState(
    val hasContactsPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val hasPostNotificationsPermission: Boolean = true,
    val notificationListenerAuthorized: Boolean = false,
    val accessibilityAuthorized: Boolean = false
)

class PermissionManager(
    context: Context,
    private val shizukuController: ShizukuController
) {
    private val appContext = context.applicationContext

    fun inspectPermissions(): RuntimePermissionState {
        val contacts = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        val audio = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val postNotifs = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return RuntimePermissionState(
            hasContactsPermission = contacts,
            hasAudioPermission = audio,
            hasPostNotificationsPermission = postNotifs,
            notificationListenerAuthorized = NotificationBridge.isListenerAuthorized(appContext),
            accessibilityAuthorized = AccessibilityBridge.isAccessibilityAuthorized(appContext)
        )
    }

    fun isShizukuAuthorized(): Boolean = shizukuController.hasValidPermission()

    fun isAccessibilityUsable(settings: JarvisSettings): Boolean {
        return settings.accessibilityAutomationEnabled &&
            AccessibilityBridge.isAccessibilityAuthorized(appContext) &&
            AccessibilityBridge.isServiceConnected.value
    }
}
