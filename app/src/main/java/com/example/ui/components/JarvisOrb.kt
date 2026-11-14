package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.IceBlue
import com.example.ui.theme.SignalAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbMode {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    WARNING
}

@Composable
fun JarvisOrb(
    mode: OrbMode,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    audioLevel: Float = 0f,
    animationEnabled: Boolean = true,
    glowIntensity: Float = 0.85f,
    statusDescription: String = "JARVIS core telemetry orb"
) {
    val accentColor = when (mode) {
        OrbMode.IDLE -> ElectricCyan
        OrbMode.LISTENING -> CyanGlow
        OrbMode.PROCESSING -> IceBlue
        OrbMode.SPEAKING -> ElectricCyan
        OrbMode.WARNING -> SignalAmber
    }

    val targetScale = when (mode) {
        OrbMode.IDLE -> 1.0f
        OrbMode.LISTENING -> 1.04f + (audioLevel.coerceIn(0f, 12f) / 48f)
        OrbMode.PROCESSING -> 1.02f
        OrbMode.SPEAKING -> 1.06f
        OrbMode.WARNING -> 0.98f
    }

    val reactiveScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "orbScale"
    )

    val effectiveOuter: Float
    val effectiveInner: Float
    val effectivePulse: Float
    if (animationEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "jarvisOrbMotion")
        val outerAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = when (mode) {
                        OrbMode.PROCESSING -> 4500
                        OrbMode.LISTENING -> 7000
                        OrbMode.SPEAKING -> 6000
                        else -> 14000
                    },
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "outerRotation"
        )
        val innerAngle by infiniteTransition.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = when (mode) {
                        OrbMode.PROCESSING -> 3200
                        OrbMode.LISTENING -> 5000
                        else -> 10000
                    },
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "innerRotation"
        )
        val pulseFactor by infiniteTransition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseFactor"
        )
        effectiveOuter = outerAngle
        effectiveInner = innerAngle
        effectivePulse = pulseFactor
    } else {
        effectiveOuter = 24f
        effectiveInner = 160f
        effectivePulse = 0.95f
    }
    val coreSurface = MaterialTheme.colorScheme.surfaceContainerLow

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = statusDescription },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = this.size.minDimension
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (minDim / 2f) * 0.86f * reactiveScale
            val glowAlpha = glowIntensity.coerceIn(0.2f, 1.0f)

            // Restrained ambient aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.16f * glowAlpha * effectivePulse),
                        accentColor.copy(alpha = 0.04f * glowAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = minDim * 0.5f
                ),
                radius = minDim * 0.5f,
                center = center
            )

            // Precision titanium & cyan outer ring ticks
            val tickRadius = baseRadius * 0.96f
            for (i in 0 until 24) {
                val deg = i * 15f + (effectiveOuter * 0.15f)
                val rad = deg * (PI / 180f)
                val innerTick = if (i % 3 == 0) tickRadius * 0.90f else tickRadius * 0.94f
                val start = Offset(
                    center.x + (cos(rad) * innerTick).toFloat(),
                    center.y + (sin(rad) * innerTick).toFloat()
                )
                val end = Offset(
                    center.x + (cos(rad) * tickRadius).toFloat(),
                    center.y + (sin(rad) * tickRadius).toFloat()
                )
                drawLine(
                    color = if (i % 3 == 0) accentColor.copy(alpha = 0.55f) else IceBlue.copy(alpha = 0.22f),
                    start = start,
                    end = end,
                    strokeWidth = if (i % 3 == 0) 1.8f else 1.0f,
                    cap = StrokeCap.Round
                )
            }

            // Outer dual precision arcs
            val outerArcR = baseRadius * 0.82f
            val outerTopLeft = Offset(center.x - outerArcR, center.y - outerArcR)
            val outerArcSize = Size(outerArcR * 2f, outerArcR * 2f)
            drawArc(
                color = accentColor.copy(alpha = 0.78f),
                startAngle = effectiveOuter,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = outerTopLeft,
                size = outerArcSize,
                style = Stroke(width = (minDim * 0.018f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
            )
            drawArc(
                color = IceBlue.copy(alpha = 0.42f),
                startAngle = effectiveOuter + 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = outerTopLeft,
                size = outerArcSize,
                style = Stroke(width = (minDim * 0.014f).coerceAtLeast(1.2f), cap = StrokeCap.Round)
            )

            // Middle titanium orbit ring
            val midR = baseRadius * 0.65f
            drawCircle(
                color = IceBlue.copy(alpha = 0.18f),
                radius = midR,
                center = center,
                style = Stroke(width = 1.2f)
            )
            val midTopLeft = Offset(center.x - midR, center.y - midR)
            val midSize = Size(midR * 2f, midR * 2f)
            drawArc(
                color = IceBlue.copy(alpha = 0.75f),
                startAngle = effectiveInner,
                sweepAngle = 68f,
                useCenter = false,
                topLeft = midTopLeft,
                size = midSize,
                style = Stroke(width = (minDim * 0.016f).coerceAtLeast(1.2f), cap = StrokeCap.Round)
            )
            drawArc(
                color = accentColor.copy(alpha = 0.45f),
                startAngle = effectiveInner + 145f,
                sweepAngle = 56f,
                useCenter = false,
                topLeft = midTopLeft,
                size = midSize,
                style = Stroke(width = (minDim * 0.012f).coerceAtLeast(1.0f), cap = StrokeCap.Round)
            )

            // Inner obsidian core & subtle state indicator
            val coreR = baseRadius * 0.44f * effectivePulse
            drawCircle(
                color = coreSurface,
                radius = coreR,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.72f * glowAlpha),
                        accentColor.copy(alpha = 0.25f * glowAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreR
                ),
                radius = coreR,
                center = center
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.80f),
                radius = coreR,
                center = center,
                style = Stroke(width = (minDim * 0.012f).coerceAtLeast(1.0f))
            )

            // Central focal node
            drawCircle(
                color = Color.White.copy(alpha = 0.88f),
                radius = coreR * 0.20f,
                center = center
            )
        }
    }
}
