package com.example.core.tools

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

object NotificationBridge {
    private const val MAX_BUFFER_SIZE = 25

    private val _notifications = MutableStateFlow<List<CapturedNotificationItem>>(emptyList())
    val notifications: StateFlow<List<CapturedNotificationItem>> = _notifications.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    @Volatile
    private var readerEnabled: Boolean = false

    @Volatile
    private var privacyMaskEnabled: Boolean = true

    @Volatile
    private var blockedPackages: Set<String> = setOf("com.android.systemui")

    fun setServiceConnected(connected: Boolean) {
        _isServiceConnected.value = connected
    }

    fun syncPrivacyControls(
        enabled: Boolean,
        maskEnabled: Boolean,
        excludedPackages: Set<String> = blockedPackages
    ) {
        readerEnabled = enabled
        privacyMaskEnabled = maskEnabled
        blockedPackages = excludedPackages
        if (!enabled) {
            _notifications.value = emptyList()
        }
    }

    fun isListenerAuthorized(context: Context): Boolean {
        val enabledPackages = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context)
        }.getOrDefault(emptyList())
        return enabledPackages.contains(context.packageName)
    }

    fun clearBuffer() {
        _notifications.value = emptyList()
    }

    fun recordNotification(
        key: String,
        packageName: String,
        appLabel: String,
        title: String,
        bodyText: String,
        postedAtMillis: Long,
        selfPackageName: String
    ) {
        if (!readerEnabled) return
        if (packageName == selfPackageName || packageName in blockedPackages) return

        val safeTitle = title.trim().ifBlank { "Notification" }.take(80)
        val trimmedBody = bodyText.trim().take(180)
        val preview = if (privacyMaskEnabled) {
            maskSensitiveText(trimmedBody)
        } else {
            trimmedBody.ifBlank { "(No body text)" }
        }

        val item = CapturedNotificationItem(
            key = key,
            packageName = packageName,
            appLabel = appLabel.ifBlank { packageName },
            title = safeTitle,
            maskedPreview = preview,
            rawContentRedacted = privacyMaskEnabled,
            postedAtMillis = postedAtMillis
        )

        _notifications.update { current ->
            (current.filterNot { it.key == key } + item)
                .sortedByDescending { it.postedAtMillis }
                .take(MAX_BUFFER_SIZE)
        }
    }

    fun removeByKey(key: String) {
        _notifications.update { current ->
            current.filterNot { it.key == key }
        }
    }

    internal fun maskSensitiveText(text: String): String {
        if (text.isBlank()) return "[Private notification content hidden]"
        val maskedDigits = text.replace(Regex("\\b\\d{4,8}\\b"), "••••")
        return if (maskedDigits.length <= 14) {
            "[Masked • ${maskedDigits.length} chars]"
        } else {
            "[Masked preview] ${maskedDigits.take(12)}…"
        }
    }
}

class JarvisNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationBridge.setServiceConnected(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationBridge.setServiceConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val sourcePackage = sbn.packageName ?: return
        val notifKey = sbn.key ?: "$sourcePackage:${sbn.id}:${sbn.postTime}"

        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        val appLabel = runCatching {
            val appInfo = packageManager.getApplicationInfo(sourcePackage, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(sourcePackage.substringAfterLast('.').replaceFirstChar {
            it.titlecase(Locale.getDefault())
        })

        NotificationBridge.recordNotification(
            key = notifKey,
            packageName = sourcePackage,
            appLabel = appLabel,
            title = title,
            bodyText = body,
            postedAtMillis = sbn.postTime,
            selfPackageName = packageName
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        NotificationBridge.removeByKey(key)
    }
}
