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

    val effectiveOuter = if (animationEnabled) outerAngle else 24f
    val effectiveInner = if (animationEnabled) innerAngle else 160f
    val effectivePulse = if (animationEnabled) pulseFactor else 0.95f
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

            // Outer ambient radial bloom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.28f * glowAlpha * effectivePulse),
                        accentColor.copy(alpha = 0.08f * glowAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = minDim * 0.5f
                ),
                radius = minDim * 0.5f,
                center = center
            )

            // Segmented outer HUD ticks
            val tickRadius = baseRadius * 0.98f
            for (i in 0 until 36) {
                val deg = i * 10f + (effectiveOuter * 0.2f)
                val rad = deg * (PI / 180f)
                val innerTick = if (i % 3 == 0) tickRadius * 0.91f else tickRadius * 0.945f
                val start = Offset(
                    center.x + (cos(rad) * innerTick).toFloat(),
                    center.y + (sin(rad) * innerTick).toFloat()
                )
                val end = Offset(
                    center.x + (cos(rad) * tickRadius).toFloat(),
                    center.y + (sin(rad) * tickRadius).toFloat()
                )
                drawLine(
                    color = accentColor.copy(alpha = if (i % 3 == 0) 0.65f else 0.26f),
                    start = start,
                    end = end,
                    strokeWidth = if (i % 3 == 0) 2.2f else 1.2f,
                    cap = StrokeCap.Round
                )
            }

            // Outer dual telemetry arcs
            val outerArcR = baseRadius * 0.84f
            val outerTopLeft = Offset(center.x - outerArcR, center.y - outerArcR)
            val outerArcSize = Size(outerArcR * 2f, outerArcR * 2f)
            drawArc(
                color = accentColor.copy(alpha = 0.85f),
                startAngle = effectiveOuter,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = outerTopLeft,
                size = outerArcSize,
                style = Stroke(width = minDim * 0.024f, cap = StrokeCap.Round)
            )
            drawArc(
                color = accentColor.copy(alpha = 0.45f),
                startAngle = effectiveOuter + 180f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = outerTopLeft,
                size = outerArcSize,
                style = Stroke(width = minDim * 0.018f, cap = StrokeCap.Round)
            )

            // Middle precision orbit ring
            val midR = baseRadius * 0.67f
            drawCircle(
                color = accentColor.copy(alpha = 0.22f),
                radius = midR,
                center = center,
                style = Stroke(width = 1.5f)
            )
            val midTopLeft = Offset(center.x - midR, center.y - midR)
            val midSize = Size(midR * 2f, midR * 2f)
            drawArc(
                color = IceBlue.copy(alpha = 0.9f),
                startAngle = effectiveInner,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = midTopLeft,
                size = midSize,
                style = Stroke(width = minDim * 0.02f, cap = StrokeCap.Round)
            )
            drawArc(
                color = IceBlue.copy(alpha = 0.5f),
                startAngle = effectiveInner + 140f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = midTopLeft,
                size = midSize,
                style = Stroke(width = minDim * 0.015f, cap = StrokeCap.Round)
            )

            // Inner reactor housing & pulsing core
            val coreR = baseRadius * 0.46f * effectivePulse
            drawCircle(
                color = coreSurface,
                radius = coreR,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.95f * glowAlpha),
                        accentColor.copy(alpha = 0.45f * glowAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreR
                ),
                radius = coreR,
                center = center
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.9f),
                radius = coreR,
                center = center,
                style = Stroke(width = minDim * 0.014f)
            )

            // Bright central pin
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = coreR * 0.24f,
                center = center
            )
        }
    }
}
