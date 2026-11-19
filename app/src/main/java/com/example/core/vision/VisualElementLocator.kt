package com.example.core.vision

import android.graphics.Bitmap
import android.graphics.Rect
import java.util.Locale

enum class LocalizationSource(val label: String) {
    ACCESSIBILITY_TREE("Accessibility Tree"),
    OCR_VISUAL_BLOCK("On-Device OCR")
}

data class VisualElementMatch(
    val query: String,
    val matchedLabel: String,
    val centerX: Int,
    val centerY: Int,
    val bounds: Rect?,
    val source: LocalizationSource,
    val confidence: Float
)

/**
 * Hybrid Accessibility-first + OCR visual fallback element locator.
 *
 * When a third-party app renders custom Canvas/WebView/Flutter/Game controls that omit
 * Accessibility labels, falls back to ML Kit OCR bounding boxes to find exact (cx, cy) tap points.
 */
object VisualElementLocator {

    fun locateElement(
        observation: ScreenObservation,
        query: String
    ): VisualElementMatch? {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty() || observation.blockedByPolicy) return null

        // 1. Accessibility-first semantic lookup
        val a11yBest = observation.elements
            .mapNotNull { el ->
                val score = scoreMatch(needle, el.label, el.id.orEmpty(), el.clickable)
                if (score <= 0f) null else el to score
            }
            .maxByOrNull { it.second }

        if (a11yBest != null && a11yBest.second >= 0.55f) {
            val el = a11yBest.first
            return VisualElementMatch(
                query = query,
                matchedLabel = el.label.ifBlank { el.id.orEmpty() },
                centerX = el.centerX,
                centerY = el.centerY,
                bounds = Rect(el.boundsLeft, el.boundsTop, el.boundsRight, el.boundsBottom),
                source = LocalizationSource.ACCESSIBILITY_TREE,
                confidence = a11yBest.second.coerceIn(0f, 1f)
            )
        }

        // 2. Visual OCR bounding-box fallback
        val ocrBlocks = observation.ocrResult?.blocks.orEmpty()
        val ocrBest = ocrBlocks
            .mapNotNull { block ->
                val rect = Rect(block.left, block.top, block.right, block.bottom)
                if (rect.width() <= 0 || rect.height() <= 0) return@mapNotNull null
                val score = scoreMatch(needle, block.text, "", clickable = true)
                if (score <= 0f) null else Triple(block, rect, score)
            }
            .maxByOrNull { it.third }

        if (ocrBest != null && ocrBest.third >= 0.45f) {
            val (block, rect, score) = ocrBest
            return VisualElementMatch(
                query = query,
                matchedLabel = block.text.take(80),
                centerX = rect.centerX(),
                centerY = rect.centerY(),
                bounds = rect,
                source = LocalizationSource.OCR_VISUAL_BLOCK,
                confidence = score.coerceIn(0f, 1f)
            )
        }

        // 3. Lower-threshold Accessibility fallback if available
        if (a11yBest != null) {
            val el = a11yBest.first
            return VisualElementMatch(
                query = query,
                matchedLabel = el.label.ifBlank { el.id.orEmpty() },
                centerX = el.centerX,
                centerY = el.centerY,
                bounds = Rect(el.boundsLeft, el.boundsTop, el.boundsRight, el.boundsBottom),
                source = LocalizationSource.ACCESSIBILITY_TREE,
                confidence = a11yBest.second.coerceIn(0f, 1f)
            )
        }
        return null
    }

    /**
     * Crops a padded region of interest from a screenshot bitmap for high-detail visual inspection.
     */
    fun cropRegionOfInterest(
        source: Bitmap,
        bounds: Rect,
        paddingPx: Int = 24
    ): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null
        val left = (bounds.left - paddingPx).coerceIn(0, source.width - 1)
        val top = (bounds.top - paddingPx).coerceIn(0, source.height - 1)
        val right = (bounds.right + paddingPx).coerceIn(left + 1, source.width)
        val bottom = (bounds.bottom + paddingPx).coerceIn(top + 1, source.height)
        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)
        return runCatching { Bitmap.createBitmap(source, left, top, width, height) }.getOrNull()
    }

    private fun scoreMatch(needle: String, label: String, viewId: String, clickable: Boolean): Float {
        val cleanLabel = label.trim().lowercase(Locale.ROOT)
        val cleanId = viewId.trim().lowercase(Locale.ROOT)
        var score = 0f
        when {
            cleanLabel == needle -> score = 0.95f
            cleanLabel.startsWith(needle) -> score = 0.85f
            cleanLabel.contains(needle) -> score = 0.72f
            cleanId.isNotEmpty() && cleanId.contains(needle) -> score = 0.62f
            else -> {
                val needleTokens = needle.split(Regex("\\s+")).filter { it.length >= 2 }
                if (needleTokens.isNotEmpty()) {
                    val hits = needleTokens.count { cleanLabel.contains(it) || cleanId.contains(it) }
                    if (hits > 0) {
                        score = (hits.toFloat() / needleTokens.size.toFloat()) * 0.65f
                    }
                }
            }
        }
        if (score > 0f && clickable) {
            score += 0.05f
        }
        return score.coerceAtMost(1f)
    }
}
