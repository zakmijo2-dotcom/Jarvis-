package com.example.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.core.overlay.ExecutionOverlayManager
import com.example.core.security.AppBlocklistPolicy
import com.example.core.tools.AccessibilityBridge
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

/**
 * Unified non-root screen perception engine combining:
 * 1) AppBlocklistPolicy safeguard
 * 2) Pruned/compressed Accessibility UI tree with (cx, cy) bounds & password redaction
 * 3) API 30+ AccessibilityService.takeScreenshot -> MediaProjection fallback bitmap capture
 * 4) Offline ML Kit OCR (always local, zero cloud upload required)
 * 5) Downscaled JPEG Base64 encoding for consented multimodal cloud vision turns
 */
class ScreenPerceptionEngine(
    private val context: Context,
    private val ocrEngine: OnDeviceOcrEngine = OnDeviceOcrEngine(),
    private val overlayManager: ExecutionOverlayManager? = null
) {

    suspend fun observeScreen(
        includeScreenshot: Boolean = false,
        includeOcr: Boolean = true,
        customBlockedPackagesCsv: String? = null,
        maxElements: Int = 35
    ): ScreenObservation {
        val a11yAuthorized = AccessibilityBridge.isAccessibilityAuthorized(context)
        val rawElements = if (a11yAuthorized) {
            AccessibilityBridge.inspectActiveWindowElements(maxElements)
        } else {
            emptyList()
        }
        val activePkg = AccessibilityBridge.getLastObservedPackageName()
        val (screenW, screenH) = AccessibilityBridge.getDisplaySize(context)

        if (AppBlocklistPolicy.isPackageBlocked(activePkg, customBlockedPackagesCsv)) {
            return ScreenObservation(
                capturedAtMs = System.currentTimeMillis(),
                activePackageName = activePkg,
                screenWidth = screenW,
                screenHeight = screenH,
                elements = emptyList(),
                captureSource = ScreenCaptureSource.NONE,
                stabilityHash = "blocked:${activePkg.orEmpty().hashCode().toString(16)}",
                blockedByPolicy = true,
                blockReason = "Active app '$activePkg' is restricted by JARVIS AppBlocklistPolicy."
            )
        }

        val (compressedNodes, stabilityHash) = ScreenTreeCompressor.compress(
            rawElements = rawElements,
            activePackage = activePkg,
            screenWidth = screenW,
            screenHeight = screenH,
            maxElements = maxElements
        )

        var bitmap: Bitmap? = null
        var captureSource = ScreenCaptureSource.NONE

        if (includeScreenshot || (includeOcr && compressedNodes.isEmpty())) {
            overlayManager?.setHiddenForCapture(true)
            delay(40L)
            try {
                if (a11yAuthorized) {
                    bitmap = AccessibilityBridge.captureScreenshotBitmap()
                    if (bitmap != null) {
                        captureSource = ScreenCaptureSource.ACCESSIBILITY_API30
                    }
                }
                if (bitmap == null && MediaProjectionCaptureController.isReady.value) {
                    bitmap = MediaProjectionCaptureController.captureSingleFrame(context)
                    if (bitmap != null) {
                        captureSource = ScreenCaptureSource.MEDIA_PROJECTION
                    }
                }
            } finally {
                overlayManager?.setHiddenForCapture(false)
            }
        }

        val ocrResult = if (includeOcr && bitmap != null) {
            ocrEngine.recognizeBitmap(bitmap)
        } else {
            null
        }

        val encoded = if (includeScreenshot && bitmap != null) {
            encodeDownscaledJpegBase64(bitmap)
        } else {
            null
        }

        if (bitmap != null && !bitmap.isRecycled) {
            runCatching { bitmap.recycle() }
        }

        val effectiveHash = if (compressedNodes.isEmpty() && ocrResult?.succeeded == true && ocrResult.fullText.isNotBlank()) {
            "ocr:${activePkg.orEmpty()}:${ocrResult.fullText.take(120).hashCode().toString(16)}"
        } else {
            stabilityHash
        }

        return ScreenObservation(
            capturedAtMs = System.currentTimeMillis(),
            activePackageName = activePkg,
            screenWidth = screenW,
            screenHeight = screenH,
            elements = compressedNodes,
            ocrResult = ocrResult,
            screenshotBase64Jpeg = encoded?.first,
            screenshotWidth = encoded?.second ?: 0,
            screenshotHeight = encoded?.third ?: 0,
            captureSource = captureSource,
            stabilityHash = effectiveHash,
            blockedByPolicy = false
        )
    }

    fun encodeDownscaledJpegBase64(
        source: Bitmap,
        maxDimensionPx: Int = 1024,
        jpegQuality: Int = 75
    ): Triple<String, Int, Int>? {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return null
        val longest = maxOf(w, h)
        val scaledBitmap = if (longest > maxDimensionPx) {
            val ratio = maxDimensionPx.toFloat() / longest.toFloat()
            val targetW = (w * ratio).toInt().coerceAtLeast(1)
            val targetH = (h * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        } else {
            source
        }
        val out = ByteArrayOutputStream()
        val compressedOk = scaledBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(40, 92), out)
        val finalW = scaledBitmap.width
        val finalH = scaledBitmap.height
        if (scaledBitmap !== source && !scaledBitmap.isRecycled) {
            runCatching { scaledBitmap.recycle() }
        }
        if (!compressedOk) return null
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return Triple(b64, finalW, finalH)
    }
}
