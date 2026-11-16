package com.example.core.tools

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import kotlin.coroutines.resume

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

data class ScreenElementSummary(
    val label: String,
    val viewId: String?,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: String
)

object AccessibilityBridge {
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    @Volatile
    private var serviceRef: WeakReference<JarvisAccessibilityService>? = null

    @Volatile
    private var testActionOverride: ((AuthorizedAccessibilityAction) -> Boolean)? = null

    @Volatile
    private var testElementsOverride: ((Int) -> List<ScreenElementSummary>)? = null

    @Volatile
    private var testTapOverride: ((String) -> Boolean)? = null

    @Volatile
    private var testScrollOverride: ((String) -> Boolean)? = null

    @Volatile
    private var testInputOverride: ((String?, String) -> Boolean)? = null

    @Volatile
    private var testCoordinateTapOverride: ((Int, Int) -> Boolean)? = null

    @Volatile
    private var testSwipeOverride: ((Int, Int, Int, Int, Long) -> Boolean)? = null

    @Volatile
    private var testScreenshotOverride: (() -> Bitmap?)? = null

    @Volatile
    private var testPackageOverride: String? = null

    @Volatile
    private var lastObservedPackageName: String? = null

    fun getLastObservedPackageName(): String? = testPackageOverride ?: lastObservedPackageName

    fun getAttachedService(): JarvisAccessibilityService? = serviceRef?.get()

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
        updateConnectedStateForTests()
    }

    fun setTestElementsOverride(handler: ((Int) -> List<ScreenElementSummary>)?) {
        testElementsOverride = handler
        updateConnectedStateForTests()
    }

    fun setTestTapOverride(handler: ((String) -> Boolean)?) {
        testTapOverride = handler
        updateConnectedStateForTests()
    }

    fun setTestScrollOverride(handler: ((String) -> Boolean)?) {
        testScrollOverride = handler
        updateConnectedStateForTests()
    }

    fun setTestInputOverride(handler: ((String?, String) -> Boolean)?) {
        testInputOverride = handler
        updateConnectedStateForTests()
    }

    fun setTestCoordinateTapOverride(handler: ((Int, Int) -> Boolean)?) {
        testCoordinateTapOverride = handler
        updateConnectedStateForTests()
    }

    fun setTestSwipeOverride(handler: ((Int, Int, Int, Int, Long) -> Boolean)?) {
        testSwipeOverride = handler
        updateConnectedStateForTests()
    }

    fun setTestScreenshotOverride(handler: (() -> Bitmap?)?) {
        testScreenshotOverride = handler
        updateConnectedStateForTests()
    }

    fun setTestActivePackageOverride(packageName: String?) {
        testPackageOverride = packageName
    }

    fun clearAllTestOverrides() {
        testActionOverride = null
        testElementsOverride = null
        testTapOverride = null
        testScrollOverride = null
        testInputOverride = null
        testCoordinateTapOverride = null
        testSwipeOverride = null
        testScreenshotOverride = null
        testPackageOverride = null
        updateConnectedStateForTests()
    }

    private fun updateConnectedStateForTests() {
        val anyTestOverride = testActionOverride != null ||
            testElementsOverride != null ||
            testTapOverride != null ||
            testScrollOverride != null ||
            testInputOverride != null ||
            testCoordinateTapOverride != null ||
            testSwipeOverride != null ||
            testScreenshotOverride != null
        _isServiceConnected.value = anyTestOverride || serviceRef?.get() != null
    }

    fun isAccessibilityAuthorized(context: Context): Boolean {
        if (testActionOverride != null ||
            testElementsOverride != null ||
            testTapOverride != null ||
            testScrollOverride != null ||
            testInputOverride != null ||
            testCoordinateTapOverride != null ||
            testSwipeOverride != null ||
            testScreenshotOverride != null
        ) {
            return true
        }
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

    fun getDisplaySize(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        return dm.widthPixels.coerceAtLeast(320) to dm.heightPixels.coerceAtLeast(480)
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

    /**
     * Captures a one-shot screenshot via Android 11+ (API 30+) AccessibilityService.takeScreenshot.
     */
    suspend fun captureScreenshotBitmap(timeoutMs: Long = 2000L): Bitmap? {
        testScreenshotOverride?.let { return it() }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val service = serviceRef?.get() ?: return null

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    service.takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        service.mainExecutor,
                        object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                                val hwBuffer = screenshot.hardwareBuffer
                                val bitmap = runCatching {
                                    val wrapped = Bitmap.wrapHardwareBuffer(hwBuffer, screenshot.colorSpace)
                                    val copy = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                    wrapped?.recycle()
                                    copy
                                }.getOrNull()
                                runCatching { hwBuffer.close() }
                                if (cont.isActive) cont.resume(bitmap)
                            }

                            override fun onFailure(errorCode: Int) {
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                    )
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    /**
     * On-demand active window inspection with DFS child pruning & node recycling.
     * Password fields are strictly redacted as `[REDACTED_PASSWORD]`.
     */
    fun inspectActiveWindowElements(maxElements: Int = 35): List<ScreenElementSummary> {
        testElementsOverride?.let {
            lastObservedPackageName = testPackageOverride ?: "test.window"
            return it(maxElements)
        }
        val service = serviceRef?.get() ?: return emptyList()
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return emptyList()
        lastObservedPackageName = runCatching { root.packageName?.toString() }.getOrNull()

        val summaries = mutableListOf<ScreenElementSummary>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val nodesToRecycle = mutableListOf<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        val rect = Rect()

        while (queue.isNotEmpty() && visited < 120 && summaries.size < maxElements) {
            val node = queue.removeFirst()
            if (node !== root) {
                nodesToRecycle.add(node)
            }
            visited++
            runCatching {
                if (node.isVisibleToUser) {
                    val rawLabel = when {
                        node.isPassword -> "[REDACTED_PASSWORD]"
                        !node.text.isNullOrBlank() -> node.text.toString().trim()
                        !node.contentDescription.isNullOrBlank() -> node.contentDescription.toString().trim()
                        else -> ""
                    }
                    val viewId = node.viewIdResourceName?.substringAfterLast('/')
                    val actionable = node.isClickable || node.isEditable || node.isScrollable
                    if (rawLabel.isNotEmpty() || actionable || !viewId.isNullOrBlank()) {
                        node.getBoundsInScreen(rect)
                        if (rect.width() > 1 && rect.height() > 1) {
                            summaries.add(
                                ScreenElementSummary(
                                    label = rawLabel.ifEmpty {
                                        viewId ?: node.className?.toString()?.substringAfterLast('.') ?: "Element"
                                    }.take(90),
                                    viewId = viewId,
                                    className = node.className?.toString()?.substringAfterLast('.') ?: "View",
                                    isClickable = node.isClickable,
                                    isEditable = node.isEditable,
                                    isScrollable = node.isScrollable,
                                    bounds = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]"
                                )
                            )
                        }
                    }
                    // Cap excessive off-screen child traversal inside large scrollable containers
                    val childCap = if (node.isScrollable) 24 else node.childCount
                    val limit = minOf(node.childCount, childCap)
                    for (i in 0 until limit) {
                        node.getChild(i)?.let { queue.addLast(it) }
                    }
                }
            }
        }
        while (queue.isNotEmpty()) {
            val remaining = queue.removeFirst()
            if (remaining !== root) {
                nodesToRecycle.add(remaining)
            }
        }
        nodesToRecycle.forEach { n -> runCatching { n.recycle() } }
        return summaries
    }

    /**
     * Tap screen coordinates using Android Accessibility GestureDescription.
     */
    fun tapAtCoordinates(x: Int, y: Int, durationMs: Long = 70L): Boolean {
        if (x < 0 || y < 0) return false
        testCoordinateTapOverride?.let { return it(x, y) }
        val service = serviceRef?.get() ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return runCatching {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(40L, 200L)))
                .build()
            service.dispatchGesture(gesture, null, null)
        }.getOrDefault(false)
    }

    fun longPressAtCoordinates(x: Int, y: Int, durationMs: Long = 650L): Boolean {
        if (x < 0 || y < 0) return false
        testCoordinateTapOverride?.let { return it(x, y) }
        val service = serviceRef?.get() ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return runCatching {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(450L, 2000L)))
                .build()
            service.dispatchGesture(gesture, null, null)
        }.getOrDefault(false)
    }

    fun swipeCoordinates(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 280L): Boolean {
        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) return false
        testSwipeOverride?.let { return it(startX, startY, endX, endY, durationMs) }
        val service = serviceRef?.get() ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return runCatching {
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(100L, 1200L)))
                .build()
            service.dispatchGesture(gesture, null, null)
        }.getOrDefault(false)
    }

    /**
     * Multi-strategy tap: clickable node -> clickable ancestor -> screen bounds coordinate tap.
     */
    fun tapUiElement(targetQuery: String): Boolean {
        val cleanQuery = targetQuery.trim()
        if (cleanQuery.isEmpty()) return false
        testTapOverride?.let { return it(cleanQuery) }

        val service = serviceRef?.get() ?: return false
        return runCatching {
            val root = service.rootInActiveWindow ?: return@runCatching false
            val matched = findMatchingNode(root, cleanQuery) ?: return@runCatching false

            var clickable: AccessibilityNodeInfo? = matched
            var hops = 0
            while (clickable != null && !clickable.isClickable && hops < 5) {
                clickable = clickable.parent
                hops++
            }
            if (clickable != null && clickable.isClickable) {
                if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return@runCatching true
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val rect = Rect()
                matched.getBoundsInScreen(rect)
                if (!rect.isEmpty) {
                    val path = Path().apply {
                        moveTo(rect.exactCenterX(), rect.exactCenterY())
                    }
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0L, 65L))
                        .build()
                    return@runCatching service.dispatchGesture(gesture, null, null)
                }
            }
            false
        }.getOrDefault(false)
    }

    /**
     * On-demand screen scroll in [direction] ("down", "up", "left", "right").
     */
    fun scrollUiScreen(direction: String): Boolean {
        val norm = direction.trim().lowercase()
        testScrollOverride?.let { return it(norm) }

        val service = serviceRef?.get() ?: return false
        return runCatching {
            val root = service.rootInActiveWindow ?: return@runCatching false
            val scrollable = findFirstScrollableNode(root)
            val forward = norm == "down" || norm == "forward" || norm == "right"
            if (scrollable != null) {
                val action = if (forward) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                if (scrollable.performAction(action)) {
                    return@runCatching true
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val rect = Rect()
                root.getBoundsInScreen(rect)
                val width = rect.width().coerceAtLeast(400)
                val height = rect.height().coerceAtLeast(800)
                val cx = width / 2f
                val cy = height / 2f
                val path = Path().apply {
                    when (norm) {
                        "up", "backward" -> {
                            moveTo(cx, height * 0.30f)
                            lineTo(cx, height * 0.75f)
                        }
                        "left" -> {
                            moveTo(width * 0.25f, cy)
                            lineTo(width * 0.80f, cy)
                        }
                        "right" -> {
                            moveTo(width * 0.80f, cy)
                            lineTo(width * 0.25f, cy)
                        }
                        else -> {
                            moveTo(cx, height * 0.75f)
                            lineTo(cx, height * 0.25f)
                        }
                    }
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0L, 220L))
                    .build()
                return@runCatching service.dispatchGesture(gesture, null, null)
            }
            false
        }.getOrDefault(false)
    }

    /**
     * On-demand text entry into an editable non-password field matching [targetQuery]
     * (or the currently focused editable field when [targetQuery] is null/blank).
     */
    fun inputTextIntoUi(targetQuery: String?, text: String): Boolean {
        if (text.isEmpty()) return false
        testInputOverride?.let { return it(targetQuery, text) }

        val service = serviceRef?.get() ?: return false
        return runCatching {
            val root = service.rootInActiveWindow ?: return@runCatching false
            val targetNode = if (!targetQuery.isNullOrBlank()) {
                findMatchingNode(root, targetQuery.trim(), requireEditable = true)
            } else {
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
                    ?: findFirstEditableNode(root)
            } ?: return@runCatching false

            // Security guardrail: never allow automated text injection into password fields.
            if (targetNode.isPassword) return@runCatching false

            runCatching { targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)
    }

    private fun findMatchingNode(
        root: AccessibilityNodeInfo,
        query: String,
        requireEditable: Boolean = false
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 120) {
            val node = queue.removeFirst()
            visited++
            val matched = runCatching {
                if (node.isVisibleToUser && (!requireEditable || node.isEditable)) {
                    val textMatch = node.text?.toString()?.contains(query, ignoreCase = true) == true
                    val descMatch = node.contentDescription?.toString()?.contains(query, ignoreCase = true) == true
                    val idMatch = node.viewIdResourceName?.contains(query, ignoreCase = true) == true
                    textMatch || descMatch || idMatch
                } else {
                    false
                }
            }.getOrDefault(false)
            if (matched) return node
            runCatching {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.addLast(it) }
                }
            }
        }
        return null
    }

    private fun findFirstScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 100) {
            val node = queue.removeFirst()
            visited++
            val found = runCatching { node.isVisibleToUser && node.isScrollable }.getOrDefault(false)
            if (found) return node
            runCatching {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.addLast(it) }
                }
            }
        }
        return null
    }

    private fun findFirstEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 100) {
            val node = queue.removeFirst()
            visited++
            val found = runCatching { node.isVisibleToUser && node.isEditable && !node.isPassword }.getOrDefault(false)
            if (found) return node
            runCatching {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.addLast(it) }
                }
            }
        }
        return null
    }
}

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Strictly on-demand: JARVIS never streams screen contents silently in background.
    }

    override fun onInterrupt() {
        // Nothing passive running.
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
