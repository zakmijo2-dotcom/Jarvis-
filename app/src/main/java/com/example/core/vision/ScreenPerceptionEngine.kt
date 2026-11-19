package com.example.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.core.overlay.ExecutionOverlayManager
import com.example.core.security.AppBlocklistPolicy
import com.example.core.tools.AccessibilityBridge
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.LinkedHashMap

data class FrameCacheEntry(
    val stabilityHash: String,
    val capturedAtMs: Long,
    val base64Jpeg: String,
    val width: Int,
    val height: Int
)

/**
 * Unified non-root screen perception engine combining:
 * 1) AppBlocklistPolicy safeguard
 * 2) Pruned/compressed Accessibility UI tree with (cx, cy) bounds & password redaction
 * 3) Screen classification (loading, dialogs, search, feed, media player, forms)
 * 4) API 30+ AccessibilityService.takeScreenshot -> MediaProjection fallback bitmap capture
 * 5) Offline ML Kit OCR (always local, zero cloud upload required)
 * 6) LRU frame cache + stability-hash diffing + context-aware cloud image selection
 */
class ScreenPerceptionEngine(
    private val context: Context,
    private val ocrEngine: OnDeviceOcrEngine = OnDeviceOcrEngine(),
    private val overlayManager: ExecutionOverlayManager? = null
) {
    private val frameCacheLock = Any()
    private val frameCache = object : LinkedHashMap<String, FrameCacheEntry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FrameCacheEntry>?): Boolean =
            size > MAX_CACHED_FRAMES
    }

    @Volatile
    private var lastStabilityHash: String? = null

    suspend fun observeScreen(
        includeScreenshot: Boolean = false,
        includeOcr: Boolean = true,
        customBlockedPackagesCsv: String? = null,
        maxElements: Int = 35,
        forceFreshScreenshot: Boolean = false
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

        val now = System.currentTimeMillis()
        val cachedFrame = if (includeScreenshot && !forceFreshScreenshot && compressedNodes.isNotEmpty()) {
            getCachedFrame(stabilityHash, now)
        } else {
            null
        }

        var bitmap: Bitmap? = null
        var captureSource = ScreenCaptureSource.NONE

        val needsBitmapCapture = (includeScreenshot && cachedFrame == null) ||
            (includeOcr && compressedNodes.isEmpty())

        if (needsBitmapCapture) {
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

        val encoded: Triple<String, Int, Int>? = when {
            includeScreenshot && cachedFrame != null -> {
                captureSource = ScreenCaptureSource.ACCESSIBILITY_API30
                Triple(cachedFrame.base64Jpeg, cachedFrame.width, cachedFrame.height)
            }
            includeScreenshot && bitmap != null -> {
                encodeDownscaledJpegBase64(bitmap)?.also { (b64, w, h) ->
                    putCachedFrame(
                        FrameCacheEntry(
                            stabilityHash = stabilityHash,
                            capturedAtMs = now,
                            base64Jpeg = b64,
                            width = w,
                            height = h
                        )
                    )
                }
            }
            else -> null
        }

        if (bitmap != null && !bitmap.isRecycled) {
            runCatching { bitmap.recycle() }
        }

        val effectiveHash = if (compressedNodes.isEmpty() && ocrResult?.succeeded == true && ocrResult.fullText.isNotBlank()) {
            "ocr:${activePkg.orEmpty()}:${ocrResult.fullText.take(120).hashCode().toString(16)}"
        } else {
            stabilityHash
        }
        lastStabilityHash = effectiveHash

        return ScreenObservation(
            capturedAtMs = now,
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

    /**
     * Context-aware image selection policy:
     * Avoids sending screenshots to cloud models unless visual inspection adds real value
     * (sparse UI tree, visual reasoning requested, previous action failed/stalled, or screen changed
     * into a media/browser/dialog surface) AND the model supports vision.
     */
    fun shouldAttachScreenshotToCloud(
        observation: ScreenObservation?,
        previousSentHash: String?,
        userPrompt: String,
        previousActionFailed: Boolean,
        modelSupportsVision: Boolean
    ): Boolean {
        if (!modelSupportsVision || observation == null || observation.blockedByPolicy) return false
        if (observation.screenshotBase64Jpeg.isNullOrBlank()) return false

        // Skip re-sending identical unchanged frames unless recovering from a failure
        if (!previousActionFailed && previousSentHash != null && previousSentHash == observation.stabilityHash) {
            return false
        }

        if (previousActionFailed) return true
        if (observation.elements.size < 5) return true

        val lower = userPrompt.lowercase()
        val explicitlyVisual = lower.contains("screenshot") ||
            lower.contains("look at") ||
            lower.contains("what is on") ||
            lower.contains("image") ||
            lower.contains("video") ||
            lower.contains("chart") ||
            lower.contains("شوف الشاشة") ||
            lower.contains("صورة")
        if (explicitlyVisual) return true

        val classification = ScreenClassifier.classify(observation)
        return classification.screenType == ScreenType.MEDIA_PLAYER ||
            classification.screenType == ScreenType.BROWSER ||
            classification.screenType == ScreenType.DIALOG_PERMISSION ||
            classification.screenType == ScreenType.UNKNOWN
    }

    fun getCachedFrameCount(): Int = synchronized(frameCacheLock) { frameCache.size }

    fun clearFrameCache() {
        synchronized(frameCacheLock) {
            frameCache.clear()
        }
        lastStabilityHash = null
    }

    internal fun putCachedFrame(entry: FrameCacheEntry) {
        synchronized(frameCacheLock) {
            frameCache[entry.stabilityHash] = entry
        }
    }

    internal fun getCachedFrame(hash: String, nowMs: Long = System.currentTimeMillis()): FrameCacheEntry? {
        synchronized(frameCacheLock) {
            val existing = frameCache[hash] ?: return null
            return if (nowMs - existing.capturedAtMs <= FRAME_CACHE_TTL_MS) existing else {
                frameCache.remove(hash)
                null
            }
        }
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

    companion object {
        private const val MAX_CACHED_FRAMES = 6
        private const val FRAME_CACHE_TTL_MS = 12_000L
    }
}
