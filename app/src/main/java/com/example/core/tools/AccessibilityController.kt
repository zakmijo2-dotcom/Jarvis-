package com.example.core.tools

import android.content.Context
import com.example.core.security.PromptSecuritySanitizer
import kotlinx.coroutines.delay

data class UiObservedElement(
    val label: String,
    val viewId: String?,
    val roleType: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: String
)

data class UiObservationSnapshot(
    val capturedAtMs: Long,
    val activePackageName: String?,
    val elements: List<UiObservedElement>,
    val windowSignatureHash: String
) {
    fun toSanitizedPromptSummary(maxItems: Int = 15): String {
        if (elements.isEmpty()) {
            return "Active package: ${activePackageName ?: "unknown"}; no visible elements captured."
        }
        val body = buildString {
            appendLine("Active package: ${activePackageName ?: "unknown"} (${elements.size} elements)")
            elements.take(maxItems).forEachIndexed { idx, el ->
                appendLine(
                    "${idx + 1}. [${el.roleType}] \"${el.label}\" id=${el.viewId ?: "-"} bounds=${el.bounds}"
                )
            }
        }
        return PromptSecuritySanitizer.wrapUntrustedBlock(
            sourceLabel = "AccessibilityScreenSnapshot",
            rawContent = body
        )
    }
}

data class ActionVerificationResult(
    val actionName: String,
    val actionDispatched: Boolean,
    val stateChangedVerified: Boolean,
    val evidence: String,
    val beforeHash: String,
    val afterHash: String
)

/**
 * 5-Layer Android Automation Abstraction:
 * AccessibilityController -> UiObservationEngine -> ElementIdentificationEngine -> ActionExecutor -> StateVerificationEngine
 */
class AccessibilityController(
    private val context: Context,
    private val stabilizationDelayMs: Long = DEFAULT_STABILIZATION_DELAY_MS,
    private val maxVerificationPolls: Int = DEFAULT_MAX_VERIFICATION_POLLS,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val sleepFn: suspend (Long) -> Unit = { ms -> if (ms > 0L) delay(ms) }
) {
    val observationEngine = UiObservationEngine(context)
    val elementIdentifier = ElementIdentificationEngine()
    val actionExecutor = ActionExecutor()
    val stateVerifier = StateVerificationEngine()

    fun isAuthorized(): Boolean = AccessibilityBridge.isAccessibilityAuthorized(context)

    fun observeCurrentScreen(maxElements: Int = 25): UiObservationSnapshot =
        observationEngine.captureActiveWindow(maxElements, elementIdentifier)

    fun findElement(query: String): UiObservedElement? {
        val snapshot = observeCurrentScreen(35)
        return elementIdentifier.matchElement(snapshot.elements, query)
    }

    suspend fun executeTapWithVerification(targetQuery: String): ActionVerificationResult {
        val before = observeCurrentScreen(25)
        val ok = actionExecutor.tap(targetQuery)
        val after = captureSettledAfterSnapshot(before, ok, 25)
        return stateVerifier.verifyTransition(
            actionName = "tap_ui_element($targetQuery)",
            dispatched = ok,
            before = before,
            after = after
        )
    }

    suspend fun executeScrollWithVerification(direction: String): ActionVerificationResult {
        val before = observeCurrentScreen(25)
        val ok = actionExecutor.scroll(direction)
        val after = captureSettledAfterSnapshot(before, ok, 25)
        return stateVerifier.verifyTransition(
            actionName = "scroll_screen($direction)",
            dispatched = ok,
            before = before,
            after = after
        )
    }

    suspend fun executeTextInputWithVerification(targetQuery: String?, text: String): ActionVerificationResult {
        val before = observeCurrentScreen(25)
        val ok = actionExecutor.inputText(targetQuery, text)
        val after = captureSettledAfterSnapshot(before, ok, 25)
        return stateVerifier.verifyTransition(
            actionName = "input_ui_text(${targetQuery ?: "focused"})",
            dispatched = ok,
            before = before,
            after = after
        )
    }

    suspend fun executeGlobalActionWithVerification(action: AuthorizedAccessibilityAction): ActionVerificationResult {
        val before = observeCurrentScreen(20)
        val ok = actionExecutor.globalAction(action)
        val after = captureSettledAfterSnapshot(before, ok, 20)
        return stateVerifier.verifyTransition(
            actionName = "global_action(${action.actionKey})",
            dispatched = ok,
            before = before,
            after = after
        )
    }

    suspend fun executeCoordinateTapWithVerification(x: Int, y: Int, longPress: Boolean = false): ActionVerificationResult {
        val before = observeCurrentScreen(25)
        val ok = if (longPress) {
            actionExecutor.longPressCoordinates(x, y)
        } else {
            actionExecutor.tapCoordinates(x, y)
        }
        val after = captureSettledAfterSnapshot(before, ok, 25)
        return stateVerifier.verifyTransition(
            actionName = "${if (longPress) "long_press" else "tap_coordinates"}($x,$y)",
            dispatched = ok,
            before = before,
            after = after
        )
    }

    suspend fun executeSwipeWithVerification(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 280L
    ): ActionVerificationResult {
        val before = observeCurrentScreen(25)
        val ok = actionExecutor.swipe(startX, startY, endX, endY, durationMs)
        val after = captureSettledAfterSnapshot(before, ok, 25)
        return stateVerifier.verifyTransition(
            actionName = "swipe_screen($startX,$startY->$endX,$endY)",
            dispatched = ok,
            before = before,
            after = after
        )
    }

    suspend fun verifyExternalTransition(
        actionName: String,
        dispatched: Boolean,
        before: UiObservationSnapshot,
        maxElements: Int = 20
    ): ActionVerificationResult {
        val after = captureSettledAfterSnapshot(before, dispatched, maxElements)
        return stateVerifier.verifyTransition(
            actionName = actionName,
            dispatched = dispatched,
            before = before,
            after = after
        )
    }

    private suspend fun captureSettledAfterSnapshot(
        before: UiObservationSnapshot,
        dispatched: Boolean,
        maxElements: Int
    ): UiObservationSnapshot {
        if (!dispatched) {
            return observeCurrentScreen(maxElements)
        }
        if (stabilizationDelayMs > 0L) {
            sleepFn(stabilizationDelayMs)
        }
        var after = observeCurrentScreen(maxElements)
        var poll = 0
        while (poll < maxVerificationPolls &&
            before.windowSignatureHash == after.windowSignatureHash &&
            before.activePackageName == after.activePackageName
        ) {
            poll++
            if (pollIntervalMs > 0L) {
                sleepFn(pollIntervalMs)
            }
            after = observeCurrentScreen(maxElements)
        }
        return after
    }

    companion object {
        const val DEFAULT_STABILIZATION_DELAY_MS = 180L
        const val DEFAULT_MAX_VERIFICATION_POLLS = 3
        const val DEFAULT_POLL_INTERVAL_MS = 120L
    }
}

class UiObservationEngine(
    private val context: Context
) {
    fun captureActiveWindow(
        maxElements: Int,
        classifier: ElementIdentificationEngine
    ): UiObservationSnapshot {
        if (!AccessibilityBridge.isAccessibilityAuthorized(context)) {
            return UiObservationSnapshot(
                capturedAtMs = System.currentTimeMillis(),
                activePackageName = null,
                elements = emptyList(),
                windowSignatureHash = "unauthorized"
            )
        }
        val rawElements = AccessibilityBridge.inspectActiveWindowElements(maxElements)
        val pkgName = AccessibilityBridge.getLastObservedPackageName()
        val classified = rawElements.map { raw -> classifier.classify(raw) }
        val signature = buildString {
            append(pkgName.orEmpty())
            append('|')
            classified.forEach { el ->
                append(el.label.take(24))
                append(':')
                append(el.bounds)
                append(';')
            }
        }.hashCode().toString(16)

        return UiObservationSnapshot(
            capturedAtMs = System.currentTimeMillis(),
            activePackageName = pkgName,
            elements = classified,
            windowSignatureHash = signature
        )
    }
}

class ElementIdentificationEngine {
    fun classify(summary: ScreenElementSummary): UiObservedElement {
        val sanitizedLabel = if (summary.label == "[REDACTED_PASSWORD]") {
            "[REDACTED_PASSWORD]"
        } else {
            PromptSecuritySanitizer.sanitizeExternalText(summary.label, maxChars = 80)
        }
        val role = when {
            sanitizedLabel == "[REDACTED_PASSWORD]" -> "PASSWORD_INPUT"
            summary.isEditable -> "TEXT_INPUT"
            summary.isScrollable -> "SCROLL_CONTAINER"
            summary.isClickable -> "BUTTON_OR_LINK"
            else -> "TEXT_OR_VIEW"
        }
        return UiObservedElement(
            label = sanitizedLabel,
            viewId = summary.viewId,
            roleType = role,
            className = summary.className,
            isClickable = summary.isClickable,
            isEditable = summary.isEditable,
            isScrollable = summary.isScrollable,
            bounds = summary.bounds
        )
    }

    fun matchElement(elements: List<UiObservedElement>, query: String): UiObservedElement? {
        val needle = query.trim()
        if (needle.isEmpty()) return null
        return elements.firstOrNull { el ->
            el.label != "[REDACTED_PASSWORD]" &&
                (el.label.contains(needle, ignoreCase = true) ||
                    el.viewId?.contains(needle, ignoreCase = true) == true)
        }
    }
}

class ActionExecutor {
    fun tap(targetQuery: String): Boolean = AccessibilityBridge.tapUiElement(targetQuery)
    fun tapCoordinates(x: Int, y: Int): Boolean = AccessibilityBridge.tapAtCoordinates(x, y)
    fun longPressCoordinates(x: Int, y: Int): Boolean = AccessibilityBridge.longPressAtCoordinates(x, y)
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 280L): Boolean =
        AccessibilityBridge.swipeCoordinates(startX, startY, endX, endY, durationMs)
    fun scroll(direction: String): Boolean = AccessibilityBridge.scrollUiScreen(direction)
    fun inputText(targetQuery: String?, text: String): Boolean = AccessibilityBridge.inputTextIntoUi(targetQuery, text)
    fun globalAction(action: AuthorizedAccessibilityAction): Boolean = AccessibilityBridge.performAuthorizedGlobalAction(action)
}

class StateVerificationEngine {
    fun verifyTransition(
        actionName: String,
        dispatched: Boolean,
        before: UiObservationSnapshot,
        after: UiObservationSnapshot
    ): ActionVerificationResult {
        if (!dispatched) {
            return ActionVerificationResult(
                actionName = actionName,
                actionDispatched = false,
                stateChangedVerified = false,
                evidence = "Action dispatch failed or target element was unavailable.",
                beforeHash = before.windowSignatureHash,
                afterHash = after.windowSignatureHash
            )
        }
        val packageChanged = before.activePackageName != after.activePackageName && after.activePackageName != null
        val windowChanged = before.windowSignatureHash != after.windowSignatureHash
        val verified = packageChanged || windowChanged
        val evidence = when {
            packageChanged -> "Verified transition to package '${after.activePackageName}'."
            windowChanged -> "Verified UI tree state change (${before.windowSignatureHash} -> ${after.windowSignatureHash})."
            else -> "Action dispatched, UI hierarchy hash unchanged (${after.windowSignatureHash})."
        }
        return ActionVerificationResult(
            actionName = actionName,
            actionDispatched = true,
            stateChangedVerified = verified,
            evidence = evidence,
            beforeHash = before.windowSignatureHash,
            afterHash = after.windowSignatureHash
        )
    }
}
