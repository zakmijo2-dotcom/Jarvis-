package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisIconSize
import com.example.ui.theme.JarvisMotion
import com.example.ui.theme.JarvisRadii
import com.example.ui.theme.JarvisSpacing
import com.example.ui.theme.JarvisThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun JarvisGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = JarvisRadii.cardShape,
    accentBorder: Color? = null,
    contentPadding: Dp = JarvisSpacing.md,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = JarvisThemeTokens.palette
    val resolvedBorder = accentBorder ?: palette.hairline
    val glassBrush = remember(palette.isDark, palette.surface, palette.surfaceHigh) {
        if (palette.isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    palette.surfaceHigh.copy(alpha = 0.72f),
                    palette.surface.copy(alpha = 0.96f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    palette.surface,
                    palette.surfaceHigh.copy(alpha = 0.35f)
                )
            )
        }
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(hairlineDp(), resolvedBorder),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .background(glassBrush)
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun JarvisStatusIndicator(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    containerColor: Color = JarvisThemeTokens.palette.surfaceHigh
) {
    val dotAlpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "jarvisStatusPulse")
        val anim by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = JarvisMotion.pulseCycleMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "jarvisStatusDotAlpha"
        )
        anim
    } else {
        1f
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(hairlineDp(), accentColor.copy(alpha = 0.32f)),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun JarvisSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color = JarvisThemeTokens.palette.skyAccent,
    badgeBackground: Color = JarvisThemeTokens.palette.cyanBadgeBg,
    trailing: (@Composable () -> Unit)? = null
) {
    val palette = JarvisThemeTokens.palette
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm)
        ) {
            Surface(
                shape = RoundedCornerShape(JarvisRadii.sm),
                color = badgeBackground
            ) {
                Box(
                    modifier = Modifier.size(JarvisIconSize.badgeContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(JarvisIconSize.lg)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun JarvisEmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    accentColor: Color = JarvisThemeTokens.palette.skyAccent,
    badgeBackground: Color = JarvisThemeTokens.palette.cyanBadgeBg
) {
    val palette = JarvisThemeTokens.palette
    JarvisGlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm)
        ) {
            Surface(
                shape = RoundedCornerShape(JarvisRadii.sm),
                color = badgeBackground
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(JarvisIconSize.lg)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.text
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
fun JarvisMarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    accentColor: Color = JarvisThemeTokens.palette.skyAccent,
    codeBackground: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val annotated = remember(markdown, color, accentColor, codeBackground) {
        parseSimpleMarkdown(
            raw = markdown,
            baseColor = color,
            accentColor = accentColor,
            codeBackground = codeBackground
        )
    }
    Text(
        text = annotated,
        style = style,
        color = color,
        modifier = modifier
    )
}

private val INLINE_TOKEN_REGEX = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*|__[^_]+__|\\*[^*\\n]+\\*)")
private val ORDERED_LIST_REGEX = Regex("^(\\d+\\.)\\s+(.*)$")

internal fun parseSimpleMarkdown(
    raw: String,
    baseColor: Color,
    accentColor: Color,
    codeBackground: Color
): AnnotatedString = buildAnnotatedString {
    val lines = raw.lines()
    lines.forEachIndexed { lineIndex, line ->
        if (lineIndex > 0) append('\n')
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = baseColor)) {
                    appendInlineMarkdown(trimmed.removePrefix("### "), baseColor, accentColor, codeBackground)
                }
            }
            trimmed.startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = baseColor)) {
                    appendInlineMarkdown(trimmed.removePrefix("## "), baseColor, accentColor, codeBackground)
                }
            }
            trimmed.startsWith("# ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = baseColor)) {
                    appendInlineMarkdown(trimmed.removePrefix("# "), baseColor, accentColor, codeBackground)
                }
            }
            trimmed.startsWith("> ") -> {
                withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                    append("▎ ")
                }
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor.copy(alpha = 0.86f))) {
                    appendInlineMarkdown(trimmed.removePrefix("> "), baseColor.copy(alpha = 0.86f), accentColor, codeBackground)
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                    append("•  ")
                }
                appendInlineMarkdown(trimmed.drop(2), baseColor, accentColor, codeBackground)
            }
            else -> {
                val ordered = ORDERED_LIST_REGEX.matchEntire(trimmed)
                if (ordered != null) {
                    withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold)) {
                        append("${ordered.groupValues[1]} ")
                    }
                    appendInlineMarkdown(ordered.groupValues[2], baseColor, accentColor, codeBackground)
                } else {
                    appendInlineMarkdown(line, baseColor, accentColor, codeBackground)
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    baseColor: Color,
    accentColor: Color,
    codeBackground: Color
) {
    var cursor = 0
    for (match in INLINE_TOKEN_REGEX.findAll(text)) {
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                withStyle(
                    SpanStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        color = accentColor,
                        background = codeBackground
                    )
                ) {
                    append(" ${token.substring(1, token.length - 1)} ")
                }
            }
            (token.startsWith("**") && token.endsWith("**") || token.startsWith("__") && token.endsWith("__")) &&
                token.length >= 4 -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = baseColor)) {
                    append(token.substring(2, token.length - 2))
                }
            }
            token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                    append(token.substring(1, token.length - 1))
                }
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}
