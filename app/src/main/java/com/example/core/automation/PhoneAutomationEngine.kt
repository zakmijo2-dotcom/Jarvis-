package com.example.core.automation

import android.content.Context
import com.example.core.device.AllowlistedCommandValidator
import com.example.core.device.DeviceCommandRequest
import com.example.core.device.DeviceCommandRouter
import com.example.core.device.DeviceControlBackend
import com.example.core.device.PrivilegedActionId
import com.example.core.tools.AccessibilityBridge
import com.example.core.tools.AccessibilityController
import com.example.core.tools.AuthorizedAccessibilityAction
import com.example.core.vision.ScreenClassification
import com.example.core.vision.ScreenClassifier
import com.example.core.vision.ScreenObservation
import com.example.core.vision.ScreenPerceptionEngine
import com.example.core.vision.VisualElementLocator
import com.example.core.vision.VisualElementMatch
import com.example.data.repository.JarvisSettings
import kotlinx.coroutines.delay

enum class AutomationBackendUsed(val label: String) {
    SHIZUKU("Shizuku"),
    WIRELESS_ADB("Wireless ADB"),
    ACCESSIBILITY("Accessibility Service"),
    NATIVE_INTENT("Native Android API"),
    NONE("Unavailable")
}

data class AutomationActionResult(
    val action: String,
    val success: Boolean,
    val verified: Boolean,
    val backendUsed: AutomationBackendUsed,
    val summary: String,
    val verificationEvidence: String,
    val beforePackage: String? = null,
    val afterPackage: String? = null,
    val screenChanged: Boolean = false
)

enum class DeviceScrollDirection(val wireName: String) {
    FORWARD("down"),
    BACKWARD("up")
}

enum class WaitScreenCondition(val wireName: String) {
    NOT_LOADING("not_loading"),
    TEXT_APPEARS("text_appears"),
    PACKAGE_CHANGES("package_changes"),
    SCREEN_CHANGES("screen_changes");

    companion object {
        fun fromWire(raw: String?): WaitScreenCondition =
            entries.firstOrNull { it.wireName.equals(raw?.trim(), ignoreCase = true) } ?: NOT_LOADING
    }
}

data class ScreenWaitResult(
    val conditionMet: Boolean,
    val elapsedMs: Long,
    val latestObservation: ScreenObservation?,
    val latestClassification: ScreenClassification?,
    val summary: String
)

/**
 * Unified Android Phone Control Engine (Stage 3).
 *
 * Capability priority routing (no root dependency):
 *   1. Shizuku API (when binder alive & permitted)
 *   2. Wireless ADB (when paired & verified connected)
 *   3. Android Accessibility Service (semantic UI tree + gestures + non-ASCII text input)
 *   4. MediaProjection (screen capture fallback inside ScreenPerceptionEngine)
 *   5. Native Android Intents (app launch, home, settings)
 *
 * Every important UI interaction captures pre/post state when available so completion is never
 * claimed without verification evidence.
 */
class PhoneAutomationEngine(
    private val context: Context,
    private val deviceCommandRouter: DeviceCommandRouter? = null,
    private val screenPerceptionEngine: ScreenPerceptionEngine? = null,
    private val accessibilityController: AccessibilityController = AccessibilityController(context)
) {

    fun activeControlPriorityOrder(settings: JarvisSettings = JarvisSettings()): List<AutomationBackendUsed> {
        val ordered = mutableListOf<AutomationBackendUsed>()
        val router = deviceCommandRouter
        if (router != null) {
            val shizuku = router.shizukuController.status.value
            if (shizuku.isBinderAlive && shizuku.hasPermission) {
                ordered.add(AutomationBackendUsed.SHIZUKU)
            }
            val adb = router.wirelessAdbController.status.value
            if (adb.connectionState == com.example.core.device.WirelessAdbConnectionState.CONNECTED) {
                ordered.add(AutomationBackendUsed.WIRELESS_ADB)
            }
        }
        if (settings.accessibilityAutomationEnabled && accessibilityController.isAuthorized()) {
            ordered.add(AutomationBackendUsed.ACCESSIBILITY)
        }
        ordered.add(AutomationBackendUsed.NATIVE_INTENT)
        return ordered
    }

    suspend fun detectActiveAppPackage(): String? {
        // 1. Accessibility observed package (instant, zero IPC overhead)
        val a11yPkg = AccessibilityBridge.getLastObservedPackageName()
        if (!a11yPkg.isNullOrBlank()) return a11yPkg

        // 2. Shizuku / Wireless ADB window query
        val router = deviceCommandRouter ?: return null
        val res = router.routeAndExecute(DeviceCommandRequest(PrivilegedActionId.QUERY_TOP_APP))
        if (res.success) {
            val match = Regex("""package=([a-zA-Z0-9._]+)""").find(res.rawOutput)
            if (match != null) return match.groupValues[1]
            val focusMatch = Regex("""mCurrentFocus=Window\{[^\n}]*\s([a-zA-Z0-9._]+)/""").find(res.rawOutput)
            if (focusMatch != null) return focusMatch.groupValues[1]
        }
        return null
    }

    suspend fun tap(x: Int, y: Int, settings: JarvisSettings = JarvisSettings()): AutomationActionResult {
        val beforeObs = observeQuietly(settings)

        // Priority 1 & 2: Shizuku -> Wireless ADB via DeviceCommandRouter
        val router = deviceCommandRouter
        if (router != null && isPrivilegedTapAvailable(router)) {
            val res = router.routeAndExecute(
                DeviceCommandRequest(
                    actionId = PrivilegedActionId.INPUT_TAP,
                    arg1 = x.toString(),
                    arg2 = y.toString(),
                    userConfirmed = true
                )
            )
            if (res.success) {
                delay(220L)
                val afterObs = observeQuietly(settings)
                val changed = hasScreenChanged(beforeObs, afterObs)
                return AutomationActionResult(
                    action = "tap",
                    success = true,
                    verified = res.verified || changed,
                    backendUsed = mapBackend(res.backendUsed),
                    summary = "Tapped ($x, $y) via ${res.backendUsed.label}.",
                    verificationEvidence = buildVerificationNote(res.verificationEvidence, beforeObs, afterObs),
                    beforePackage = beforeObs?.activePackageName,
                    afterPackage = afterObs?.activePackageName,
                    screenChanged = changed
                )
            }
        }

        // Priority 3: Accessibility gesture dispatch
        if (settings.accessibilityAutomationEnabled && accessibilityController.isAuthorized()) {
            val ok = AccessibilityBridge.tapAtCoordinates(x, y)
            delay(220L)
            val afterObs = observeQuietly(settings)
            val changed = hasScreenChanged(beforeObs, afterObs)
            return AutomationActionResult(
                action = "tap",
                success = ok,
                verified = ok && (beforeObs == null || changed),
                backendUsed = AutomationBackendUsed.ACCESSIBILITY,
                summary = if (ok) "Tapped ($x, $y) via Accessibility gesture." else "Accessibility tap gesture rejected at ($x, $y).",
                verificationEvidence = buildVerificationNote("Accessibility dispatchGesture=$ok", beforeObs, afterObs),
                beforePackage = beforeObs?.activePackageName,
                afterPackage = afterObs?.activePackageName,
                screenChanged = changed
            )
        }

        return AutomationActionResult(
            action = "tap",
            success = false,
            verified = false,
            backendUsed = AutomationBackendUsed.NONE,
            summary = "Cannot tap ($x, $y): enable Shizuku, Wireless ADB, or Accessibility Service.",
            verificationEvidence = "No UI input backend active."
        )
    }

    suspend fun longPress(
        x: Int,
        y: Int,
        durationMs: Long = 650L,
        settings: JarvisSettings = JarvisSettings()
    ): AutomationActionResult {
        val safeDur = durationMs.coerceIn(350L, 2500L)
        val beforeObs = observeQuietly(settings)

        val router = deviceCommandRouter
        if (router != null && isPrivilegedTapAvailable(router)) {
            val res = router.routeAndExecute(
                DeviceCommandRequest(
                    actionId = PrivilegedActionId.INPUT_SWIPE,
                    arg1 = "$x,$y,$x,$y",
                    arg2 = safeDur.toString(),
                    userConfirmed = true
                )
            )
            if (res.success) {
                delay(240L)
                val afterObs = observeQuietly(settings)
                val changed = hasScreenChanged(beforeObs, afterObs)
                return AutomationActionResult(
                    action = "long_press",
                    success = true,
                    verified = res.verified || changed,
                    backendUsed = mapBackend(res.backendUsed),
                    summary = "Long-pressed ($x, $y) for ${safeDur}ms via ${res.backendUsed.label}.",
                    verificationEvidence = buildVerificationNote(res.verificationEvidence, beforeObs, afterObs),
                    beforePackage = beforeObs?.activePackageName,
                    afterPackage = afterObs?.activePackageName,
                    screenChanged = changed
                )
            }
        }

        if (settings.accessibilityAutomationEnabled && accessibilityController.isAuthorized()) {
            val ok = AccessibilityBridge.longPressAtCoordinates(x, y, safeDur)
            delay(240L)
            val afterObs = observeQuietly(settings)
            val changed = hasScreenChanged(beforeObs, afterObs)
            return AutomationActionResult(
                action = "long_press",
                success = ok,
                verified = ok && (beforeObs == null || changed),
                backendUsed = AutomationBackendUsed.ACCESSIBILITY,
                summary = if (ok) "Long-pressed ($x, $y) for ${safeDur}ms via Accessibility." else "Long-press rejected at ($x, $y).",
                verificationEvidence = buildVerificationNote("Accessibility longPress=$ok", beforeObs, afterObs),
                beforePackage = beforeObs?.activePackageName,
                afterPackage = afterObs?.activePackageName,
                screenChanged = changed
            )
        }

        return AutomationActionResult(
            action = "long_press",
            success = false,
            verified = false,
            backendUsed = AutomationBackendUsed.NONE,
            summary = "Cannot long-press ($x, $y): no active input backend (Shizuku, Wireless ADB, or Accessibility).",
            verificationEvidence = "No UI input backend active."
        )
    }

    suspend fun swipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Long = 320L,
        settings: JarvisSettings = JarvisSettings()
    ): AutomationActionResult {
        val safeDur = durationMs.coerceIn(80L, 2500L)
        val beforeObs = observeQuietly(settings)

        val router = deviceCommandRouter
        if (router != null && isPrivilegedTapAvailable(router)) {
            val res = router.routeAndExecute(
                DeviceCommandRequest(
                    actionId = PrivilegedActionId.INPUT_SWIPE,
                    arg1 = "$x1,$y1,$x2,$y2",
                    arg2 = safeDur.toString(),
                    userConfirmed = true
                )
            )
            if (res.success) {
                delay(240L)
                val afterObs = observeQuietly(settings)
                val changed = hasScreenChanged(beforeObs, afterObs)
                return AutomationActionResult(
                    action = "swipe",
                    success = true,
                    verified = res.verified || changed,
                    backendUsed = mapBackend(res.backendUsed),
                    summary = "Swiped ($x1,$y1)->($x2,$y2) via ${res.backendUsed.label}.",
                    verificationEvidence = buildVerificationNote(res.verificationEvidence, beforeObs, afterObs),
                    beforePackage = beforeObs?.activePackageName,
                    afterPackage = afterObs?.activePackageName,
                    screenChanged = changed
                )
            }
        }

        if (settings.accessibilityAutomationEnabled && accessibilityController.isAuthorized()) {
            val ok = AccessibilityBridge.swipeCoordinates(
                x1,
                y1,
                x2,
                y2,
                safeDur
            )
            delay(240L)
            val afterObs = observeQuietly(settings)
            val changed = hasScreenChanged(beforeObs, afterObs)
            return AutomationActionResult(
                action = "swipe",
                success = ok,
                verified = ok && (beforeObs == null || changed),
                backendUsed = AutomationBackendUsed.ACCESSIBILITY,
                summary = if (ok) "Swiped ($x1,$y1)->($x2,$y2) via Accessibility." else "Swipe rejected.",
                verificationEvidence = buildVerificationNote("Accessibility swipe=$ok", beforeObs, afterObs),
                beforePackage = beforeObs?.activePackageName,
                afterPackage = afterObs?.activePackageName,
                screenChanged = changed
            )
        }

        return AutomationActionResult(
            action = "swipe",
            success = false,
            verified = false,
            backendUsed = AutomationBackendUsed.NONE,
            summary = "Cannot swipe: enable Shizuku, Wireless ADB, or Accessibility Service.",
            verificationEvidence = "No UI input backend active."
        )
    }

    suspend fun scroll(direction: DeviceScrollDirection, settings: JarvisSettings = JarvisSettings()): AutomationActionResult {
        if (settings.accessibilityAutomationEnabled && accessibilityController.isAuthorized()) {
            val res = accessibilityController.executeScrollWithVerification(direction.wireName)
            return AutomationActionResult(
                action = "scroll_${direction.wireName}",
                success = res.actionDispatched,
                verified = res.stateChangedVerified,
                backendUsed = AutomationBackendUsed.ACCESSIBILITY,
                summary = res.evidence,
                verificationEvidence = res.evidence,
                screenChanged = res.stateChangedVerified
            )
        }
        val (w, h) = AccessibilityBridge.getDisplaySize(context)
        val cx = (w / 2).coerceAtLeast(200)
        val topY = (h * 0.28f).toInt().coerceAtLeast(250)
        val botY = (h * 0.72f).toInt().coerceAtLeast(topY + 200)
        return if (direction == DeviceScrollDirection.FORWARD) {
            swipe(cx, botY, cx, topY, 350L, settings)
        } else {
            swipe(cx, topY, cx, botY, 350L, settings)
        }
    }

    suspend fun typeText(
        text: String,
        targetHint: String? = null,
        settings: JarvisSettings = JarvisSettings()
    ): AutomationActionResult {
        // Accessibility supports full Unicode/Arabic + targeted Editable node matching
        if (settings.accessibilityAutomationEnabled && accessibilityController.isAuthorized()) {
            val res = accessibilityController.executeTextInputWithVerification(targetHint, text)
            if (res.actionDispatched) {
                return AutomationActionResult(
                    action = "type_text",
                    success = true,
                    verified = res.stateChangedVerified,
                    backendUsed = AutomationBackendUsed.ACCESSIBILITY,
                    summary = res.evidence,
                    verificationEvidence = res.evidence,
                    screenChanged = res.stateChangedVerified
                )
            }
        }

        // Fallback to Shizuku / Wireless ADB for ASCII text into the focused field
        val router = deviceCommandRouter
        if (router != null && isPrivilegedTapAvailable(router) && AllowlistedCommandValidator.sanitizeInputText(text) != null) {
            val res = router.routeAndExecute(
                DeviceCommandRequest(
                    actionId = PrivilegedActionId.INPUT_TEXT,
                    arg1 = text,
                    userConfirmed = true
                )
            )
            if (res.success) {
                return AutomationActionResult(
                    action = "type_text",
                    success = true,
                    verified = res.verified,
                    backendUsed = mapBackend(res.backendUsed),
                    summary = "Typed text via ${res.backendUsed.label}.",
                    verificationEvidence = res.verificationEvidence
                )
            }
        }

        return AutomationActionResult(
            action = "type_text",
            success = false,
            verified = false,
            backendUsed = AutomationBackendUsed.NONE,
            summary = "Could not input text: enable Accessibility (recommended for Unicode/targeted fields) or Shizuku/Wireless ADB.",
            verificationEvidence = "No capable text input backend succeeded."
        )
    }

    suspend fun navigate(
        action: AuthorizedAccessibilityAction,
        settings: JarvisSettings = JarvisSettings()
    ): AutomationActionResult {
        val privId = when (action) {
            AuthorizedAccessibilityAction.HOME -> PrivilegedActionId.NAV_HOME
            AuthorizedAccessibilityAction.BACK -> PrivilegedActionId.NAV_BACK
            AuthorizedAccessibilityAction.RECENTS -> PrivilegedActionId.NAV_RECENTS
            else -> null
        }
        val router = deviceCommandRouter
        if (privId != null && router != null) {
            val res = router.routeAndExecute(DeviceCommandRequest(actionId = privId, userConfirmed = true))
            if (res.success) {
                return AutomationActionResult(
                    action = "navigate_${action.actionKey}",
                    success = true,
                    verified = res.verified,
                    backendUsed = mapBackend(res.backendUsed),
                    summary = res.summary,
                    verificationEvidence = res.verificationEvidence
                )
            }
        }
        if (settings.accessibilityAutomationEnabled && accessibilityController.isAuthorized()) {
            val res = accessibilityController.executeGlobalActionWithVerification(action)
            return AutomationActionResult(
                action = "navigate_${action.actionKey}",
                success = res.actionDispatched,
                verified = res.stateChangedVerified,
                backendUsed = AutomationBackendUsed.ACCESSIBILITY,
                summary = res.evidence,
                verificationEvidence = res.evidence,
                screenChanged = res.stateChangedVerified
            )
        }
        return AutomationActionResult(
            action = "navigate_${action.actionKey}",
            success = false,
            verified = false,
            backendUsed = AutomationBackendUsed.NONE,
            summary = "Cannot perform '${action.actionKey}': Shizuku, Wireless ADB, and Accessibility are unavailable.",
            verificationEvidence = "All navigation backends unavailable."
        )
    }

    /**
     * Hybrid semantic + visual element tap: tries Accessibility node tree first, then OCR
     * bounding-box localization, and routes the resulting coordinate tap through Shizuku -> ADB -> Accessibility.
     */
    suspend fun locateAndTapElement(
        query: String,
        settings: JarvisSettings = JarvisSettings()
    ): Pair<VisualElementMatch?, AutomationActionResult> {
        val perception = screenPerceptionEngine
        val obs = perception?.observeScreen(
            includeScreenshot = false,
            includeOcr = true,
            customBlockedPackagesCsv = settings.blockedPackagesCsv
        )
        if (obs == null) {
            return null to AutomationActionResult(
                action = "locate_and_tap",
                success = false,
                verified = false,
                backendUsed = AutomationBackendUsed.NONE,
                summary = "Screen perception engine unavailable.",
                verificationEvidence = "No observation captured."
            )
        }
        if (obs.blockedByPolicy) {
            return null to AutomationActionResult(
                action = "locate_and_tap",
                success = false,
                verified = false,
                backendUsed = AutomationBackendUsed.NONE,
                summary = obs.blockReason ?: "Blocked by AppBlocklistPolicy.",
                verificationEvidence = "Sensitive screen blocked."
            )
        }

        val match = VisualElementLocator.locateElement(obs, query)
            ?: return null to AutomationActionResult(
                action = "locate_and_tap",
                success = false,
                verified = false,
                backendUsed = AutomationBackendUsed.NONE,
                summary = "Element '$query' not found via Accessibility tree or on-device OCR.",
                verificationEvidence = "Searched ${obs.elements.size} UI nodes and ${obs.ocrResult?.blocks?.size ?: 0} OCR blocks."
            )

        val tapRes = tap(match.centerX, match.centerY, settings)
        return match to tapRes.copy(
            summary = "${tapRes.summary} [Matched '${match.matchedLabel}' via ${match.source.label}, conf=${"%.2f".format(match.confidence)}]"
        )
    }

    /**
     * Waits deterministically (with bounded timeout) until a screen condition is met:
     * loading completes, expected text appears, active app changes, or UI hash transitions.
     */
    suspend fun waitUntilCondition(
        condition: WaitScreenCondition,
        expectedValue: String = "",
        timeoutMs: Long = 5_000L,
        pollIntervalMs: Long = 450L,
        settings: JarvisSettings = JarvisSettings()
    ): ScreenWaitResult {
        val boundedTimeout = timeoutMs.coerceIn(500L, 15_000L)
        val boundedPoll = pollIntervalMs.coerceIn(200L, 2_000L)
        val started = System.currentTimeMillis()
        val initialObs = observeQuietly(settings)
        val initialHash = initialObs?.stabilityHash
        val initialPkg = initialObs?.activePackageName

        var latestObs = initialObs
        var latestClass = initialObs?.let { ScreenClassifier.classify(it) }

        while (true) {
            val elapsed = System.currentTimeMillis() - started
            val met = evaluateWaitCondition(
                condition = condition,
                expectedValue = expectedValue,
                initialHash = initialHash,
                initialPkg = initialPkg,
                currentObs = latestObs,
                currentClass = latestClass
            )
            if (met) {
                return ScreenWaitResult(
                    conditionMet = true,
                    elapsedMs = elapsed,
                    latestObservation = latestObs,
                    latestClassification = latestClass,
                    summary = "Condition '${condition.wireName}' verified in ${elapsed}ms (pkg=${latestObs?.activePackageName ?: "unknown"}, screen=${latestClass?.screenType?.label ?: "Unknown"})."
                )
            }
            if (elapsed >= boundedTimeout) {
                return ScreenWaitResult(
                    conditionMet = false,
                    elapsedMs = elapsed,
                    latestObservation = latestObs,
                    latestClassification = latestClass,
                    summary = "Timed out after ${elapsed}ms waiting for '${condition.wireName}' (screen=${latestClass?.screenType?.label ?: "Unknown"})."
                )
            }
            delay(boundedPoll)
            latestObs = observeQuietly(settings)
            latestClass = latestObs?.let { ScreenClassifier.classify(it) }
        }
    }

    internal fun evaluateWaitCondition(
        condition: WaitScreenCondition,
        expectedValue: String,
        initialHash: String?,
        initialPkg: String?,
        currentObs: ScreenObservation?,
        currentClass: ScreenClassification?
    ): Boolean {
        if (currentObs == null) return false
        return when (condition) {
            WaitScreenCondition.NOT_LOADING ->
                currentClass != null && !currentClass.isLoading
            WaitScreenCondition.TEXT_APPEARS -> {
                val needle = expectedValue.trim()
                if (needle.isEmpty()) return currentObs.elements.isNotEmpty()
                currentObs.elements.any { it.label.contains(needle, ignoreCase = true) } ||
                    (currentObs.ocrResult?.fullText?.contains(needle, ignoreCase = true) == true)
            }
            WaitScreenCondition.PACKAGE_CHANGES -> {
                val target = expectedValue.trim()
                val curPkg = currentObs.activePackageName.orEmpty()
                if (target.isNotEmpty()) curPkg.contains(target, ignoreCase = true)
                else initialPkg != null && curPkg.isNotEmpty() && curPkg != initialPkg
            }
            WaitScreenCondition.SCREEN_CHANGES ->
                initialHash != null && currentObs.stabilityHash != initialHash
        }
    }

    private suspend fun observeQuietly(settings: JarvisSettings): ScreenObservation? {
        val perception = screenPerceptionEngine ?: return null
        if (!settings.accessibilityAutomationEnabled || !accessibilityController.isAuthorized()) return null
        return runCatching {
            perception.observeScreen(
                includeScreenshot = false,
                includeOcr = false,
                customBlockedPackagesCsv = settings.blockedPackagesCsv,
                maxElements = 25
            )
        }.getOrNull()
    }

    private fun isPrivilegedTapAvailable(router: DeviceCommandRouter): Boolean {
        val shizuku = router.shizukuController.status.value
        if (shizuku.isBinderAlive && shizuku.hasPermission) return true
        val adb = router.wirelessAdbController.status.value
        return adb.connectionState == com.example.core.device.WirelessAdbConnectionState.CONNECTED
    }

    private fun hasScreenChanged(before: ScreenObservation?, after: ScreenObservation?): Boolean {
        if (before == null || after == null) return false
        return before.stabilityHash != after.stabilityHash || before.activePackageName != after.activePackageName
    }

    private fun buildVerificationNote(
        backendEvidence: String,
        before: ScreenObservation?,
        after: ScreenObservation?
    ): String {
        if (before == null || after == null) return backendEvidence
        val changed = hasScreenChanged(before, after)
        return "$backendEvidence • before=${before.activePackageName ?: "?"}#${before.stabilityHash.take(8)} -> after=${after.activePackageName ?: "?"}#${after.stabilityHash.take(8)} (changed=$changed)"
    }

    private fun mapBackend(backend: DeviceControlBackend): AutomationBackendUsed = when (backend) {
        DeviceControlBackend.SHIZUKU -> AutomationBackendUsed.SHIZUKU
        DeviceControlBackend.WIRELESS_ADB -> AutomationBackendUsed.WIRELESS_ADB
        DeviceControlBackend.NATIVE_ANDROID -> AutomationBackendUsed.NATIVE_INTENT
        DeviceControlBackend.ACCESSIBILITY -> AutomationBackendUsed.ACCESSIBILITY
    }
}
