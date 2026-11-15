package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.HueCoral
import com.example.ui.theme.HueLavender
import com.example.ui.theme.HuePeach
import com.example.ui.theme.HueSage
import com.example.ui.theme.HueSand
import com.example.ui.theme.HueSky
import com.example.ui.theme.JarvisTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

enum class OrbMode {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    WARNING
}

private const val STEP_SECONDS_30FPS = 1f / 30f
private const val STEP_SECONDS_THROTTLED = 0.1f // 10fps while local model is generating
private const val STATIC_PHASE_SECONDS = 0.85f

private const val ORB_IRIDESCENT_AGSL_SHADER = """
uniform float2 iResolution;
uniform float iTime;
uniform float iAmp;
uniform float iProcess;
uniform float iSpeak;
uniform float iWarning;
uniform float iRimStrength;
uniform float iIsDark;
uniform half4 uSky;
uniform half4 uPeach;
uniform half4 uSage;
uniform half4 uSand;
uniform half4 uLavender;
uniform half4 uCoral;
uniform half4 uSphereBase;
uniform half4 uShadowColor;

half4 main(float2 fragCoord) {
    float minDim = max(min(iResolution.x, iResolution.y), 1.0);
    float2 uv = (fragCoord - 0.5 * iResolution) / minDim;

    // Soft elliptical ground shadow below sphere
    float2 shadowUv = float2(uv.x / 0.31, (uv.y - 0.37) / 0.068);
    float shadowDist = dot(shadowUv, shadowUv);
    float shadowAlpha = clamp(1.0 - shadowDist, 0.0, 1.0);
    shadowAlpha = shadowAlpha * shadowAlpha * (iIsDark > 0.5 ? 0.28 : 0.16);

    float breath = 0.5 + 0.5 * sin(iTime * 1.25);
    float baseR = 0.34 * (1.0 + 0.016 * breath + 0.055 * iAmp + 0.048 * iSpeak);
    float r = length(uv);
    float edgeSmooth = 1.8 / minDim;

    float sphereMask = 1.0 - smoothstep(baseR - edgeSmooth, baseR + edgeSmooth, r);
    if (sphereMask <= 0.001) {
        return half4(uShadowColor.rgb * half(shadowAlpha), half(shadowAlpha));
    }

    float2 p = uv / max(baseR, 0.001);
    float xy2 = min(dot(p, p), 0.999);
    float z = sqrt(1.0 - xy2);
    float3 N = normalize(float3(p.x, p.y, z));
    float3 V = float3(0.0, 0.0, 1.0);

    // Top-left key light and gentle opposite bounce light
    float3 keyDir = normalize(float3(-0.48, -0.56, 0.68));
    float3 bounceDir = normalize(float3(0.55, 0.58, 0.42));
    float3 halfVec = normalize(keyDir + V);

    float wrapKey = clamp((dot(N, keyDir) + 0.52) / 1.52, 0.0, 1.0);
    float bounce = max(dot(N, bounceDir), 0.0) * 0.24;
    float spec = pow(max(dot(N, halfVec), 0.0001), 32.0) * (0.34 + 0.12 * iSpeak);
    float softGlint = pow(max(dot(N, normalize(float3(-0.36, -0.44, 0.82))), 0.0001), 10.0) * 0.16;
    float fresnel = pow(max(1.0 - max(dot(N, V), 0.0), 0.0001), 2.35);

    // Smooth slow 5-hue iridescent drift (faster color travel only while processing)
    float driftRate = 0.32 + 1.15 * iProcess + 0.35 * iAmp + 0.45 * iSpeak;
    float phase = atan(p.y, p.x) + iTime * driftRate + N.z * 1.45;
    float band = 0.5 + 0.5 * sin(phase);
    float band2 = 0.5 + 0.5 * cos(phase * 0.72 - iTime * (0.24 + 0.75 * iProcess) + p.y * 1.6);

    half3 cWarm = mix(uPeach.rgb, uSand.rgb, half(band));
    half3 cCool = mix(uSky.rgb, uSage.rgb, half(band2));
    half3 cTop = mix(cCool, uLavender.rgb, half(0.45 + 0.45 * sin(phase * 0.5 + 1.2)));
    half3 irid = mix(cWarm, cTop, half(clamp(0.48 + 0.42 * cos(phase * 0.85), 0.0, 1.0)));

    if (iWarning > 0.01) {
        irid = mix(irid, uCoral.rgb, half(clamp(iWarning * 0.68, 0.0, 0.85)));
    }

    // Soft glass body: keep saturation gentle & glassy
    float iridFillWeight = clamp(0.28 + 0.18 * iProcess + 0.18 * iAmp + 0.20 * iSpeak + 0.22 * fresnel, 0.24, 0.72);
    half3 glassBody = mix(uSphereBase.rgb, irid, half(iridFillWeight * (0.55 + 0.45 * wrapKey)));
    half3 rimGlow = mix(irid, half3(1.0, 1.0, 1.0), half(0.20)) * half(fresnel * (0.48 + 0.32 * iRimStrength));
    half3 bounceTint = mix(uSage.rgb, uLavender.rgb, half(0.5)) * half(bounce * 0.35);
    half3 highlight = half3(half(spec + softGlint));

    half3 lit = glassBody * half(0.72 + 0.32 * wrapKey) + bounceTint + rimGlow + highlight;

    half3 shadowPremul = uShadowColor.rgb * half(shadowAlpha * (1.0 - sphereMask));
    half3 outRgb = lit * half(sphereMask) + shadowPremul;
    half outA = half(max(sphereMask, shadowAlpha));
    return half4(outRgb, outA);
}
"""

@Composable
fun JarvisOrb(
    mode: OrbMode,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    audioLevel: Float = 0f,
    animationEnabled: Boolean = true,
    glowIntensity: Float = 0.85f,
    statusDescription: String = "Jarvis assistant sphere",
    ttsLevel: Float = 0f,
    isModelGenerating: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f

    val isSystemStatic = rememberOrbStaticMode(context = context, animationEnabled = animationEnabled)
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val shouldAnimate = animationEnabled && !isSystemStatic && isResumed
    val frameStepSeconds = if (isModelGenerating) STEP_SECONDS_THROTTLED else STEP_SECONDS_30FPS

    val normalizedMic = (audioLevel.coerceIn(0f, 12f) / 12f)
    val motionSpec = tween<Float>(durationMillis = 220)

    val smoothedMicState: State<Float> = animateFloatAsState(
        targetValue = if (!shouldAnimate || mode != OrbMode.LISTENING) 0f else normalizedMic,
        animationSpec = motionSpec,
        label = "orbMicAmplitude"
    )
    val processingBlendState: State<Float> = animateFloatAsState(
        targetValue = if (mode == OrbMode.PROCESSING) 1f else 0f,
        animationSpec = motionSpec,
        label = "orbProcessing"
    )
    val speakingBlendState: State<Float> = animateFloatAsState(
        targetValue = if (mode == OrbMode.SPEAKING) 1f else 0f,
        animationSpec = motionSpec,
        label = "orbSpeaking"
    )
    val warningBlendState: State<Float> = animateFloatAsState(
        targetValue = if (mode == OrbMode.WARNING) 1f else 0f,
        animationSpec = motionSpec,
        label = "orbWarning"
    )
    val ttsAmplitudeState: State<Float> = animateFloatAsState(
        targetValue = if (!shouldAnimate || mode != OrbMode.SPEAKING) 0f else ttsLevel.coerceIn(0f, 1f),
        animationSpec = motionSpec,
        label = "orbTtsLevel"
    )

    // Use Compose InfiniteTransition only while RESUMED and motion is allowed;
    // reading clockState.value strictly inside Canvas avoids any Composable recomposition per frame
    // and respects Compose Test InfiniteAnimationPolicy (prevents Robolectric waitForIdle hangs).
    val clockState: State<Float> = if (shouldAnimate) {
        val infiniteTransition = rememberInfiniteTransition(label = "jarvisOrbClock")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (100f * 2f * PI.toFloat()),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 120_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "jarvisOrbTime"
        )
    } else {
        remember { mutableFloatStateOf(STATIC_PHASE_SECONDS) }
    }

    val sphereBase = if (isDarkTheme) colorScheme.surfaceContainerHighest else colorScheme.surface
    val shadowColor = if (isDarkTheme) {
        colorScheme.surfaceContainerLowest
    } else {
        colorScheme.onSurface.copy(alpha = 0.20f)
    }

    val agslShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(ORB_IRIDESCENT_AGSL_SHADER) }.getOrNull()
        } else {
            null
        }
    }
    val rimStrength = glowIntensity.coerceIn(0.25f, 1.0f)

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = statusDescription },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rawTime = if (shouldAnimate) clockState.value else STATIC_PHASE_SECONDS
            val quantizedTime = if (shouldAnimate) {
                floor(rawTime / frameStepSeconds) * frameStepSeconds
            } else {
                STATIC_PHASE_SECONDS
            }

            val amp = smoothedMicState.value
            val proc = processingBlendState.value
            val spkBlend = speakingBlendState.value
            val warn = warningBlendState.value
            val ttsAmp = ttsAmplitudeState.value

            val speakPulse = if (spkBlend > 0.01f) {
                val basePulse = when {
                    !shouldAnimate -> 0.35f
                    ttsAmp > 0.02f -> ttsAmp
                    else -> 0.38f + 0.32f * sin(quantizedTime * 4.8f)
                }
                (spkBlend * basePulse).coerceIn(0f, 1f)
            } else {
                0f
            }

            val drawnWithAgsl = if (
                agslShader != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                this.size.width > 1f &&
                this.size.height > 1f
            ) {
                runCatching {
                    agslShader.setFloatUniform("iResolution", this.size.width, this.size.height)
                    agslShader.setFloatUniform("iTime", quantizedTime)
                    agslShader.setFloatUniform("iAmp", amp)
                    agslShader.setFloatUniform("iProcess", proc)
                    agslShader.setFloatUniform("iSpeak", speakPulse)
                    agslShader.setFloatUniform("iWarning", warn)
                    agslShader.setFloatUniform("iRimStrength", rimStrength)
                    agslShader.setFloatUniform("iIsDark", if (isDarkTheme) 1f else 0f)
                    agslShader.setFloatUniform("uSky", HueSky.red, HueSky.green, HueSky.blue, 1f)
                    agslShader.setFloatUniform("uPeach", HuePeach.red, HuePeach.green, HuePeach.blue, 1f)
                    agslShader.setFloatUniform("uSage", HueSage.red, HueSage.green, HueSage.blue, 1f)
                    agslShader.setFloatUniform("uSand", HueSand.red, HueSand.green, HueSand.blue, 1f)
                    agslShader.setFloatUniform("uLavender", HueLavender.red, HueLavender.green, HueLavender.blue, 1f)
                    agslShader.setFloatUniform("uCoral", HueCoral.red, HueCoral.green, HueCoral.blue, 1f)
                    agslShader.setFloatUniform("uSphereBase", sphereBase.red, sphereBase.green, sphereBase.blue, 1f)
                    agslShader.setFloatUniform("uShadowColor", shadowColor.red, shadowColor.green, shadowColor.blue, 1f)
                    drawRect(brush = ShaderBrush(agslShader))
                    true
                }.getOrDefault(false)
            } else {
                false
            }

            if (!drawnWithAgsl) {
                drawIridescentFallbackSphere(
                    time = quantizedTime,
                    micAmplitude = amp,
                    processing = proc,
                    speakingPulse = speakPulse,
                    warning = warn,
                    rimStrength = rimStrength,
                    isDarkTheme = isDarkTheme,
                    sphereBase = sphereBase,
                    shadowColor = shadowColor
                )
            }
        }
    }
}

private fun DrawScope.drawIridescentFallbackSphere(
    time: Float,
    micAmplitude: Float,
    processing: Float,
    speakingPulse: Float,
    warning: Float,
    rimStrength: Float,
    isDarkTheme: Boolean,
    sphereBase: Color,
    shadowColor: Color
) {
    val minDim = size.minDimension
    val center = Offset(size.width / 2f, size.height / 2f)
    val breath = 0.5f + 0.5f * sin(time * 1.25f)
    val baseRadius = minDim * 0.34f * (1f + 0.016f * breath + 0.055f * micAmplitude + 0.048f * speakingPulse)

    // Soft elliptical ground shadow beneath the sphere
    val shadowCenter = Offset(center.x, center.y + minDim * 0.37f)
    val shadowRadius = minDim * 0.31f
    scale(scaleX = 1f, scaleY = 0.24f, pivot = shadowCenter) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    shadowColor.copy(alpha = if (isDarkTheme) 0.28f else 0.16f),
                    shadowColor.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = shadowCenter,
                radius = shadowRadius
            ),
            radius = shadowRadius,
            center = shadowCenter
        )
    }

    val spherePath = Path().apply {
        addOval(
            oval = androidx.compose.ui.geometry.Rect(
                offset = Offset(center.x - baseRadius, center.y - baseRadius),
                size = Size(baseRadius * 2f, baseRadius * 2f)
            )
        )
    }

    val driftAngle = time * (0.28f + 0.95f * processing + 0.35f * micAmplitude)
    val swirlOffsetA = Offset(
        x = center.x + cos(driftAngle) * baseRadius * 0.36f,
        y = center.y + sin(driftAngle) * baseRadius * 0.30f
    )
    val swirlOffsetB = Offset(
        x = center.x - cos(driftAngle * 0.8f) * baseRadius * 0.38f,
        y = center.y - sin(driftAngle * 0.8f) * baseRadius * 0.34f
    )

    clipPath(spherePath) {
        // 1. Translucent frosted base shaded from top-left
        val keyLightCenter = Offset(center.x - baseRadius * 0.38f, center.y - baseRadius * 0.42f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to sphereBase,
                    0.65f to lerpColor(sphereBase, HueSky, if (isDarkTheme) 0.16f else 0.24f),
                    1.00f to lerpColor(sphereBase, HueLavender, if (isDarkTheme) 0.22f else 0.30f)
                ),
                center = keyLightCenter,
                radius = baseRadius * 1.65f
            ),
            radius = baseRadius,
            center = center
        )

        // 2. Soft low-saturation iridescent caustics blending the 5 muted hues
        val sheenAlpha = (0.34f + 0.18f * processing + 0.16f * micAmplitude + 0.18f * speakingPulse).coerceIn(0.30f, 0.65f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    HuePeach.copy(alpha = sheenAlpha * 0.72f),
                    HueSand.copy(alpha = sheenAlpha * 0.55f),
                    Color.Transparent
                ),
                center = swirlOffsetA,
                radius = baseRadius * 1.15f
            ),
            radius = baseRadius,
            center = center
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    HueSky.copy(alpha = sheenAlpha * 0.70f),
                    HueSage.copy(alpha = sheenAlpha * 0.58f),
                    HueLavender.copy(alpha = sheenAlpha * 0.40f),
                    Color.Transparent
                ),
                center = swirlOffsetB,
                radius = baseRadius * 1.25f
            ),
            radius = baseRadius,
            center = center
        )

        if (warning > 0.01f) {
            drawCircle(
                color = HueCoral.copy(alpha = (warning * 0.38f).coerceIn(0f, 0.45f)),
                radius = baseRadius,
                center = center
            )
        }

        // 3. Soft sweep along the glass edge using all 5 muted hues
        val rimAlpha = (0.26f + 0.22f * processing + 0.24f * micAmplitude + 0.24f * speakingPulse) * rimStrength
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    HueSky.copy(alpha = rimAlpha.coerceIn(0.18f, 0.62f)),
                    HueLavender.copy(alpha = rimAlpha.coerceIn(0.18f, 0.62f)),
                    HuePeach.copy(alpha = rimAlpha.coerceIn(0.18f, 0.62f)),
                    HueSand.copy(alpha = rimAlpha.coerceIn(0.18f, 0.62f)),
                    HueSage.copy(alpha = rimAlpha.coerceIn(0.18f, 0.62f)),
                    HueSky.copy(alpha = rimAlpha.coerceIn(0.18f, 0.62f))
                ),
                center = center
            ),
            radius = baseRadius,
            center = center,
            alpha = 0.42f
        )

        // 4. Inner glass depth & soft Fresnel rim highlight
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.58f to Color.Transparent,
                    0.88f to Color.White.copy(alpha = if (isDarkTheme) 0.14f else 0.28f),
                    1.00f to Color.White.copy(alpha = if (isDarkTheme) 0.26f else 0.45f)
                ),
                center = center,
                radius = baseRadius
            ),
            radius = baseRadius,
            center = center
        )

        // 5. Top-left gentle specular glass highlight
        val specCenter = Offset(center.x - baseRadius * 0.34f, center.y - baseRadius * 0.38f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDarkTheme) 0.38f else 0.56f),
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = specCenter,
                radius = baseRadius * 0.52f
            ),
            radius = baseRadius * 0.52f,
            center = specCenter
        )
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t
    )
}

@Composable
private fun rememberOrbStaticMode(
    context: Context,
    animationEnabled: Boolean
): Boolean {
    val powerManager = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    fun readPowerSave(): Boolean = powerManager?.isPowerSaveMode == true
    fun readThermalThrottled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) >= PowerManager.THERMAL_STATUS_MODERATE

    fun readAnimatorZero(): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }.getOrDefault(false)

    var powerSave by remember { mutableStateOf(readPowerSave()) }
    var thermalThrottled by remember { mutableStateOf(readThermalThrottled()) }
    var animatorZero by remember { mutableStateOf(readAnimatorZero()) }

    DisposableEffect(context, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                powerSave = readPowerSave()
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        val thermalListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            PowerManager.OnThermalStatusChangedListener { status ->
                thermalThrottled = status >= PowerManager.THERMAL_STATUS_MODERATE
            }.also { listener ->
                runCatching { powerManager.addThermalStatusListener(listener) }
            }
        } else {
            null
        }

        val scaleObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animatorZero = readAnimatorZero()
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                scaleObserver
            )
        }

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null && powerManager != null) {
                runCatching { powerManager.removeThermalStatusListener(thermalListener) }
            }
            runCatching { context.contentResolver.unregisterContentObserver(scaleObserver) }
        }
    }

    return !animationEnabled || powerSave || thermalThrottled || animatorZero
}

@Preview(name = "Orb Light", showBackground = true)
@Preview(name = "Orb Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JarvisOrbPreview() {
    JarvisTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                JarvisOrb(mode = OrbMode.IDLE, size = 110.dp)
                JarvisOrb(mode = OrbMode.PROCESSING, size = 110.dp)
            }
        }
    }
}
