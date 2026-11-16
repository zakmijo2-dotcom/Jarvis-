package com.example.core.vision

import com.example.core.security.PromptSecuritySanitizer
import com.example.core.tools.ScreenElementSummary

/**
 * Clean-room UI hierarchy compressor inspired by OpenPhoneUse TreeCompressor and Zafiro DisplayNodeExtractor.
 * Parses bounds into center coordinates, filters degenerate/occluded elements, redacts passwords,
 * and computes a noise-resistant stability hash for loop detection.
 */
object ScreenTreeCompressor {

    private val BOUNDS_REGEX = Regex("""\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]""")
    private val VOLATILE_DIGITS_REGEX = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b|\b\d{1,3}%""")

    fun compress(
        rawElements: List<ScreenElementSummary>,
        activePackage: String?,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        maxElements: Int = 40
    ): Pair<List<ScreenElementNode>, String> {
        val nodes = mutableListOf<ScreenElementNode>()
        val seenKeys = HashSet<String>()

        for (raw in rawElements) {
            if (nodes.size >= maxElements) break
            val parsedBounds = parseBounds(raw.bounds)
            val l = parsedBounds?.get(0) ?: 0
            val t = parsedBounds?.get(1) ?: 0
            val r = parsedBounds?.get(2) ?: 0
            val b = parsedBounds?.get(3) ?: 0

            // Skip completely off-screen or zero-size elements when bounds are present
            if (parsedBounds != null) {
                val width = r - l
                val height = b - t
                if (width <= 1 || height <= 1) continue
                if (screenWidth > 0 && (r <= 0 || l >= screenWidth)) continue
                if (screenHeight > 0 && (b <= 0 || t >= screenHeight)) continue
            }

            val isPassword = raw.label == "[REDACTED_PASSWORD]"
            val cleanLabel = if (isPassword) {
                "[REDACTED_PASSWORD]"
            } else {
                PromptSecuritySanitizer.sanitizeExternalText(raw.label, maxChars = 90)
            }
            if (cleanLabel.isBlank() && raw.viewId.isNullOrBlank() && !raw.isClickable && !raw.isEditable && !raw.isScrollable) {
                continue
            }

            val dedupeKey = "${cleanLabel.lowercase()}|${raw.viewId.orEmpty()}|$l,$t,$r,$b"
            if (!seenKeys.add(dedupeKey)) continue

            val roleType = when {
                isPassword -> "PASSWORD_INPUT"
                raw.isEditable -> "TEXT_INPUT"
                raw.isScrollable -> "SCROLL_CONTAINER"
                raw.isClickable -> "BUTTON_OR_LINK"
                else -> "TEXT"
            }
            val cx = ((l + r) / 2).coerceAtLeast(0)
            val cy = ((t + b) / 2).coerceAtLeast(0)

            nodes.add(
                ScreenElementNode(
                    index = nodes.size + 1,
                    id = raw.viewId,
                    className = raw.className,
                    label = cleanLabel.ifBlank { raw.viewId ?: raw.className },
                    roleType = roleType,
                    clickable = raw.isClickable,
                    editable = raw.isEditable,
                    scrollable = raw.isScrollable,
                    passwordProtected = isPassword,
                    boundsLeft = l,
                    boundsTop = t,
                    boundsRight = r,
                    boundsBottom = b,
                    centerX = cx,
                    centerY = cy
                )
            )
        }

        val stabilityHash = computeStabilityHash(activePackage, nodes)
        return nodes to stabilityHash
    }

    fun computeStabilityHash(
        activePackage: String?,
        nodes: List<ScreenElementNode>
    ): String {
        val normalized = buildString {
            append(activePackage.orEmpty().lowercase())
            append('|')
            nodes.take(20).forEach { node ->
                val clean = if (node.passwordProtected) {
                    "[REDACTED]"
                } else {
                    VOLATILE_DIGITS_REGEX.replace(node.label.lowercase(), "#").take(24)
                }
                append(clean)
                append('@')
                append(node.roleType)
                append(';')
            }
        }
        return normalized.hashCode().toString(16)
    }

    fun parseBounds(bounds: String): IntArray? {
        val m = BOUNDS_REGEX.find(bounds) ?: return null
        val l = m.groupValues[1].toIntOrNull() ?: return null
        val t = m.groupValues[2].toIntOrNull() ?: return null
        val r = m.groupValues[3].toIntOrNull() ?: return null
        val b = m.groupValues[4].toIntOrNull() ?: return null
        return intArrayOf(l, t, r, b)
    }
}
