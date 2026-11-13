package com.example.core.tools

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

enum class AuthorizedAccessibilityAction(
    val actionKey: String,
    val label: String,
    val globalActionId: Int
) {
    HOME("home", "Return to Home Screen", AccessibilityService.GLOBAL_ACTION_HOME),
    BACK("back", "Navigate Back", AccessibilityService.GLOBAL_ACTION_BACK),
    RECENTS("recents", "Open Recent Apps", AccessibilityService.GLOBAL_ACTION_RECENTS),
    NOTIFICATIONS("notifications", "Expand Notification Shade", AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),
    QUICK_SETTINGS("quick_settings", "Open Quick Settings", AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS),
    LOCK_SCREEN("lock_screen", "Lock Device Screen", AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);

    companion object {
        fun fromKey(key: String): AuthorizedAccessibilityAction? =
            entries.firstOrNull { it.actionKey.equals(key.trim(), ignoreCase = true) }
    }
}

object AccessibilityBridge {
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    @Volatile
    private var serviceRef: WeakReference<JarvisAccessibilityService>? = null

    @Volatile
    private var testActionOverride: ((AuthorizedAccessibilityAction) -> Boolean)? = null

    fun attach(service: JarvisAccessibilityService) {
        serviceRef = WeakReference(service)
        _isServiceConnected.value = true
    }

    fun detach() {
        serviceRef = null
        _isServiceConnected.value = false
    }

    fun setTestOverride(handler: ((AuthorizedAccessibilityAction) -> Boolean)?) {
        testActionOverride = handler
        _isServiceConnected.value = handler != null
    }

    fun isAccessibilityAuthorized(context: Context): Boolean {
        if (testActionOverride != null) return true
        val expectedComponent = ComponentName(context, JarvisAccessibilityService::class.java)
        val enabledServices = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull().orEmpty()
        return enabledServices.split(':')
            .any { entry ->
                ComponentName.unflattenFromString(entry.trim()) == expectedComponent
            }
    }

    fun performAuthorizedGlobalAction(action: AuthorizedAccessibilityAction): Boolean {
        testActionOverride?.let { return it(action) }
        val service = serviceRef?.get() ?: return false
        if (action == AuthorizedAccessibilityAction.LOCK_SCREEN && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false
        }
        return runCatching {
            service.performGlobalAction(action.globalActionId)
        }.getOrDefault(false)
    }
}

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Strictly no-op: JARVIS never silently reads or collects screen contents.
    }

    override fun onInterrupt() {
        // No background observation state to tear down.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AccessibilityBridge.detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityBridge.detach()
        super.onDestroy()
    }
}
