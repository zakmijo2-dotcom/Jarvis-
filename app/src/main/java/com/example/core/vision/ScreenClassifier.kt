package com.example.core.vision

import java.util.Locale

enum class ScreenType(val label: String) {
    HOME_LAUNCHER("Home / Launcher"),
    SEARCH("Search Interface"),
    FEED_LIST("Feed / Results List"),
    MEDIA_PLAYER("Video / Media Player"),
    FORM_INPUT("Form / Editor"),
    DIALOG_PERMISSION("Permission / System Dialog"),
    LOADING("Loading / Transition"),
    BROWSER("Web Browser"),
    SETTINGS("System Settings"),
    SENSITIVE_BLOCKED("Sensitive / Protected"),
    UNKNOWN("App Screen")
}

data class ScreenClassification(
    val screenType: ScreenType,
    val isLoading: Boolean,
    val hasPermissionOrSystemDialog: Boolean,
    val dialogPromptText: String? = null,
    val clickableCount: Int = 0,
    val editableCount: Int = 0,
    val confidenceReason: String
)

/**
 * Classifies active Android screens from Accessibility nodes + optional OCR text so the
 * autonomous agent can detect loading states, permission dialogs, search views, media players,
 * and forms without wasting cloud tokens.
 */
object ScreenClassifier {

    fun classify(observation: ScreenObservation): ScreenClassification {
        if (observation.blockedByPolicy) {
            return ScreenClassification(
                screenType = ScreenType.SENSITIVE_BLOCKED,
                isLoading = false,
                hasPermissionOrSystemDialog = false,
                confidenceReason = observation.blockReason ?: "Blocked by AppBlocklistPolicy"
            )
        }

        val pkg = observation.activePackageName.orEmpty().lowercase(Locale.ROOT)
        val elements = observation.elements
        val clickableCount = elements.count { it.clickable }
        val editableCount = elements.count { it.editable }
        val scrollableCount = elements.count { it.scrollable }

        val combinedText = buildString {
            elements.forEach { el ->
                if (el.label.isNotBlank()) append(el.label).append(' ')
                if (el.className.isNotBlank()) append(el.className).append(' ')
                if (!el.id.isNullOrBlank()) append(el.id).append(' ')
            }
            observation.ocrResult?.fullText?.let { append(it) }
        }.lowercase(Locale.ROOT)

        // 1. Permission / System Dialog detection
        val isPermissionPkg = pkg.contains("permissioncontroller") ||
            pkg.contains("packageinstaller") ||
            pkg == "com.android.systemui"
        val hasDialogButtons = (combinedText.contains("allow") && (combinedText.contains("deny") || combinedText.contains("don't allow") || combinedText.contains("while using"))) ||
            (combinedText.contains("سماح") && combinedText.contains("رفض")) ||
            (combinedText.contains("autoriser") && combinedText.contains("refuser")) ||
            (combinedText.contains("cancel") && (combinedText.contains("ok") || combinedText.contains("continue") || combinedText.contains("accept")))

        if (isPermissionPkg || (hasDialogButtons && elements.size <= 14)) {
            val promptSnippet = elements
                .firstOrNull { !it.clickable && it.label.length >= 10 }
                ?.label
                ?.take(120)
            return ScreenClassification(
                screenType = ScreenType.DIALOG_PERMISSION,
                isLoading = false,
                hasPermissionOrSystemDialog = true,
                dialogPromptText = promptSnippet,
                clickableCount = clickableCount,
                editableCount = editableCount,
                confidenceReason = if (isPermissionPkg) "System permission controller active ($pkg)" else "Dialog action pair detected"
            )
        }

        // 2. Loading / progress state detection
        val hasProgressBar = elements.any {
            it.className.contains("ProgressBar", ignoreCase = true) ||
                it.id.orEmpty().contains("loading", ignoreCase = true) ||
                it.id.orEmpty().contains("spinner", ignoreCase = true)
        }
        val loadingText = combinedText.contains("loading...") ||
            combinedText.contains("please wait") ||
            combinedText.contains("جاري التحميل") ||
            combinedText.contains("chargement")
        val isLoading = (hasProgressBar && clickableCount <= 3) || (loadingText && clickableCount <= 2)
        if (isLoading) {
            return ScreenClassification(
                screenType = ScreenType.LOADING,
                isLoading = true,
                hasPermissionOrSystemDialog = false,
                clickableCount = clickableCount,
                editableCount = editableCount,
                confidenceReason = "Progress indicator / sparse loading UI detected"
            )
        }

        // 3. Launcher / Home screen
        if (pkg.contains("launcher") || pkg.contains("trebuchet") || pkg.contains("nexuslauncher")) {
            return ScreenClassification(
                screenType = ScreenType.HOME_LAUNCHER,
                isLoading = false,
                hasPermissionOrSystemDialog = false,
                clickableCount = clickableCount,
                editableCount = editableCount,
                confidenceReason = "Launcher package ($pkg)"
            )
        }

        // 4. Settings
        if (pkg == "com.android.settings" || pkg.endsWith(".settings")) {
            return ScreenClassification(
                screenType = ScreenType.SETTINGS,
                isLoading = false,
                hasPermissionOrSystemDialog = false,
                clickableCount = clickableCount,
                editableCount = editableCount,
                confidenceReason = "Settings package ($pkg)"
            )
        }

        // 5. Browser / WebView
        if (pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("browser") || pkg.contains("edge") || pkg.contains("brave") ||
            elements.any { it.className.contains("WebView", ignoreCase = true) }
        ) {
            return ScreenClassification(
                screenType = ScreenType.BROWSER,
                isLoading = false,
                hasPermissionOrSystemDialog = false,
                clickableCount = clickableCount,
                editableCount = editableCount,
                confidenceReason = "Browser / WebView surface ($pkg)"
            )
        }

        // 6. Media / Video player
        val isMediaPkg = pkg.contains("youtube") || pkg.contains("spotify") || pkg.contains("netflix") || pkg.contains("vlc")
        val hasMediaControls = combinedText.contains("pause video") ||
            combinedText.contains("play video") ||
            combinedText.contains("subscribe") ||
            combinedText.contains("fullscreen") ||
            combinedText.contains("seekbar") ||
            combinedText.contains("watch_player")
        if ((isMediaPkg && hasMediaControls) || (combinedText.contains("play") && combinedText.contains("pause") && combinedText.contains("fullscreen"))) {
            return ScreenClassification(
                screenType = ScreenType.MEDIA_PLAYER,
                isLoading = false,
                hasPermissionOrSystemDialog = false,
                clickableCount = clickableCount,
                editableCount = editableCount,
                confidenceReason = "Media player controls detected in $pkg"
            )
        }

        // 7. Search UI
        val hasSearchEdit = elements.any {
            it.editable && (it.label.contains("search", ignoreCase = true) ||
                it.id.orEmpty().contains("search", ignoreCase = true) ||
                it.label.contains("بحث"))
        }
        if (hasSearchEdit) {
            return ScreenClassification(
                screenType = ScreenType.SEARCH,
                isLoading = false,
                hasPermissionOrSystemDialog = false,
                clickableCount = clickableCount,
                editableCount = editableCount,
                confidenceReason = "Active search input field detected"
            )
        }

        // 8. Form / Editor
        if (editableCount >= 2 || (editableCount == 1 && (combinedText.contains("submit") || combinedText.contains("send") || combinedText.contains("sign in")))) {
            return ScreenClassification(
                screenType = ScreenType.FORM_INPUT,
                isLoading = false,
                hasPermissionOrSystemDialog = false,
                clickableCount = clickableCount,
                editableCount = editableCount,
                confidenceReason = "$editableCount editable input(s) detected"
            )
        }

        // 9. Feed / Scrollable List
        if (scrollableCount >= 1 || clickableCount >= 6) {
            return ScreenClassification(
                screenType = ScreenType.FEED_LIST,
                isLoading = false,
                hasPermissionOrSystemDialog = false,
                clickableCount = clickableCount,
                editableCount = editableCount,
                confidenceReason = "Scrollable feed or rich list ($clickableCount clickable elements)"
            )
        }

        return ScreenClassification(
            screenType = ScreenType.UNKNOWN,
            isLoading = false,
            hasPermissionOrSystemDialog = false,
            clickableCount = clickableCount,
            editableCount = editableCount,
            confidenceReason = "General screen ($pkg, ${elements.size} elements)"
        )
    }
}
