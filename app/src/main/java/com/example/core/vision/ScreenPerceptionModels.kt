package com.example.core.vision

import com.example.core.security.PromptSecuritySanitizer

enum class ScreenCaptureSource(val label: String) {
    NONE("No Screenshot"),
    ACCESSIBILITY_API30("Accessibility Screenshot"),
    MEDIA_PROJECTION("MediaProjection Frame"),
    TEST_STUB("Test Stub")
}

data class ScreenElementNode(
    val index: Int,
    val id: String?,
    val className: String,
    val label: String,
    val roleType: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val passwordProtected: Boolean,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
    val centerX: Int,
    val centerY: Int
)

data class OcrTextBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class OcrResult(
    val fullText: String,
    val blocks: List<OcrTextBlock>,
    val engineName: String = "ML Kit Offline OCR",
    val durationMs: Long = 0L,
    val succeeded: Boolean = true,
    val errorMessage: String? = null
)

data class ScreenObservation(
    val capturedAtMs: Long,
    val activePackageName: String?,
    val screenWidth: Int,
    val screenHeight: Int,
    val elements: List<ScreenElementNode>,
    val ocrResult: OcrResult? = null,
    val screenshotBase64Jpeg: String? = null,
    val screenshotWidth: Int = 0,
    val screenshotHeight: Int = 0,
    val captureSource: ScreenCaptureSource = ScreenCaptureSource.NONE,
    val stabilityHash: String,
    val blockedByPolicy: Boolean = false,
    val blockReason: String? = null
) {
    fun toPromptContextSummary(maxElements: Int = 24, includeOcr: Boolean = true): String {
        if (blockedByPolicy) {
            return "Active package '${activePackageName ?: "unknown"}' is protected by AppBlocklistPolicy: ${blockReason ?: "Observation blocked."}"
        }
        val body = buildString {
            appendLine("Package: ${activePackageName ?: "unknown"} | Display: ${screenWidth}x${screenHeight} | Hash: $stabilityHash | Screenshot: ${captureSource.label}")
            if (elements.isEmpty()) {
                appendLine("UI Tree Elements: none visible.")
            } else {
                appendLine("Indexed UI Elements (${elements.size}):")
                elements.take(maxElements).forEach { el ->
                    val flags = buildList {
                        if (el.clickable) add("tap")
                        if (el.editable) add("edit")
                        if (el.scrollable) add("scroll")
                    }.joinToString(",")
                    appendLine(
                        "[${el.index}] (${el.centerX},${el.centerY}) [${el.roleType}${if (flags.isNotEmpty()) ":$flags" else ""}] \"${el.label}\"${el.id?.let { " id=$it" } ?: ""}"
                    )
                }
            }
            if (includeOcr && ocrResult != null && ocrResult.succeeded && ocrResult.fullText.isNotBlank()) {
                appendLine("OCR Text:")
                appendLine(ocrResult.fullText.take(1200))
            }
        }
        return PromptSecuritySanitizer.wrapUntrustedBlock(
            sourceLabel = "ScreenObservation",
            rawContent = body,
            maxChars = 3500
        )
    }
}
