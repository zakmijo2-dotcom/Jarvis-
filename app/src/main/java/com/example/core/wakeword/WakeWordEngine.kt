package com.example.core.wakeword

import ai.picovoice.porcupine.Porcupine
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

enum class WakeWordState(val label: String) {
    DORMANT("Dormant"),
    STANDBY_AUDIO_GATE("Porcupine KWS Standby ('Jarvis')"),
    UNAVAILABLE_PENDING_MODEL_OR_KEY("Engine Evaluated — Key/Model Needed"),
    BACKGROUND_RESTRICTED("Paused (Background Restriction)"),
    PERMISSION_DENIED("Mic Permission Denied"),
    TRIGGERED("Wake Word Triggered")
}

enum class WakeWordEngineKind(val label: String) {
    PORCUPINE_EVALUATED("Picovoice Porcupine v4 ('Jarvis' Built-In)"),
    SHERPA_ONNX_PLANNED("Sherpa-ONNX KWS (Open Alternative)")
}

data class WakeWordEvaluationReport(
    val targetPhrase: String = "Jarvis",
    val selectedEngine: WakeWordEngineKind = WakeWordEngineKind.PORCUPINE_EVALUATED,
    val sampleRateHz: Int = 16_000,
    val frameLengthSamples: Int = 512,
    val isPhraseDetectionVerified: Boolean = false,
    val summary: String,
    val blockers: List<String>
)

data class WakeWordAudioMetrics(
    val rmsDbfs: Float = -60f,
    val speechBandEnergyActive: Boolean = false,
    val framesProcessed: Long = 0L
)

fun interface NativeKeywordSpotter : AutoCloseable {
    fun process(frame512: ShortArray): Int
    override fun close() {}
}

interface WakeWordEngine {
    val state: StateFlow<WakeWordState>
    val keyword: String
    val isPhraseDetectionVerified: Boolean
    val audioMetrics: StateFlow<WakeWordAudioMetrics>
    val evaluationReport: WakeWordEvaluationReport
    val triggerEvents: SharedFlow<Long>
        get() = EMPTY_TRIGGER_FLOW

    fun updateAccessKey(accessKey: String) {}

    fun syncStandbyPreference(
        enabled: Boolean,
        hasMicPermission: Boolean = true,
        isAppInForeground: Boolean = true,
        porcupineAccessKeyPresent: Boolean = false
    )

    fun processPcm16Frame(samples: ShortArray, sampleCount: Int)
    fun resetMetrics()
    fun release()

    companion object {
        private val EMPTY_TRIGGER_FLOW = MutableSharedFlow<Long>(extraBufferCapacity = 1).asSharedFlow()
    }
}

/**
 * Local wake-word engine for target phrase "Jarvis" backed by Picovoice Porcupine Android v4.0.2
 * (`Porcupine.BuiltInKeyword.JARVIS` @ 16 kHz / 512-sample PCM16 frames) with a deterministic
 * speech-band RMS + Zero-Crossing Rate (ZCR) acoustic pre-filter and 2.5s cooldown debouncer.
 *
 * Honesty & verification contract:
 * - When a valid Picovoice AccessKey is configured on-device, `Porcupine` initializes the native
 *   ARM64 `"Jarvis"` spotter (`libpv_porcupine.so`).
 * - When no AccessKey is configured or when running on a JVM/cloud container without physical
 *   microphone audio, `isPhraseDetectionVerified` remains `false` and the engine never fabricates
 *   synthetic wake-word triggers from raw noise.
 */
class ModularWakeWordEngine(
    context: Context? = null,
    private val kwsSpotterFactory: ((Context?, String) -> NativeKeywordSpotter)? = null,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : WakeWordEngine {

    private val appContext = context?.applicationContext
    private val lock = Any()

    private val _state = MutableStateFlow(WakeWordState.DORMANT)
    override val state: StateFlow<WakeWordState> = _state.asStateFlow()

    override val keyword: String = "Jarvis"

    @Volatile
    private var activeAccessKey: String = ""

    @Volatile
    private var nativeSpotter: NativeKeywordSpotter? = null

    @Volatile
    private var lastNativeInitError: String? = null

    @Volatile
    private var lastTriggerTimestampMs: Long = 0L

    private val _triggerEvents = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    override val triggerEvents: SharedFlow<Long> = _triggerEvents.asSharedFlow()

    private val _audioMetrics = MutableStateFlow(WakeWordAudioMetrics())
    override val audioMetrics: StateFlow<WakeWordAudioMetrics> = _audioMetrics.asStateFlow()

    override val isPhraseDetectionVerified: Boolean
        get() = nativeSpotter != null && _audioMetrics.value.framesProcessed > 0L

    override val evaluationReport: WakeWordEvaluationReport
        get() {
            val verified = isPhraseDetectionVerified
            val spotterReady = nativeSpotter != null
            val initErr = lastNativeInitError
            val blockers = buildList {
                if (activeAccessKey.isBlank()) {
                    add("Picovoice Porcupine v4.0.2 SDK is bundled with built-in 'JARVIS' keyword support; enter a Picovoice AccessKey in Voice Console to activate native KWS.")
                } else if (!spotterReady && !initErr.isNullOrBlank()) {
                    add("Porcupine native initialization error: $initErr")
                }
                if (!verified) {
                    add("Live 'Jarvis' phrase detection is marked verified only after native Porcupine initialization and real 16 kHz microphone PCM16 frame capture on device.")
                }
            }
            return WakeWordEvaluationReport(
                targetPhrase = keyword,
                selectedEngine = WakeWordEngineKind.PORCUPINE_EVALUATED,
                sampleRateHz = SAMPLE_RATE_HZ,
                frameLengthSamples = FRAME_LENGTH_SAMPLES,
                isPhraseDetectionVerified = verified,
                summary = if (spotterReady) {
                    "Picovoice Porcupine v4.0.2 ('JARVIS' built-in keyword, 512 samples @ 16 kHz PCM16, sensitivity 0.65) initialized with speech-band VAD gate and 2.5s false-positive cooldown."
                } else {
                    "Bundled Picovoice Porcupine v4.0.2 ('JARVIS' built-in keyword, 512 samples @ 16 kHz PCM16) + speech-band RMS/ZCR pre-filter and Android 15 microphone FGS lifecycle. Add a Picovoice AccessKey to arm native keyword spotting on Poco X7 Pro."
                },
                blockers = blockers
            )
        }

    override fun updateAccessKey(accessKey: String) {
        val trimmed = accessKey.trim()
        synchronized(lock) {
            if (trimmed == activeAccessKey) return
            activeAccessKey = trimmed
            closeSpotterLocked()
            if (trimmed.isNotEmpty()) {
                initSpotterIfPossibleLocked(trimmed)
            }
        }
    }

    override fun syncStandbyPreference(
        enabled: Boolean,
        hasMicPermission: Boolean,
        isAppInForeground: Boolean,
        porcupineAccessKeyPresent: Boolean
    ) {
        val keyAvailable = porcupineAccessKeyPresent || activeAccessKey.isNotBlank()
        if (enabled && hasMicPermission && isAppInForeground && activeAccessKey.isNotBlank() && nativeSpotter == null) {
            synchronized(lock) {
                initSpotterIfPossibleLocked(activeAccessKey)
            }
        }
        _state.value = when {
            !enabled -> WakeWordState.DORMANT
            !hasMicPermission -> WakeWordState.PERMISSION_DENIED
            !isAppInForeground -> WakeWordState.BACKGROUND_RESTRICTED
            keyAvailable -> WakeWordState.STANDBY_AUDIO_GATE
            else -> WakeWordState.UNAVAILABLE_PENDING_MODEL_OR_KEY
        }
    }

    override fun processPcm16Frame(samples: ShortArray, sampleCount: Int) {
        if (sampleCount <= 0 || sampleCount > samples.size) return
        var sumSq = 0.0
        var zeroCrossings = 0
        var prevPositive = samples[0] >= 0

        for (i in 0 until sampleCount) {
            val s = samples[i].toDouble()
            sumSq += s * s
            val curPositive = samples[i] >= 0
            if (curPositive != prevPositive) {
                zeroCrossings++
                prevPositive = curPositive
            }
        }
        val rms = sqrt(sumSq / sampleCount.toDouble()) / 32768.0
        val db = if (rms <= 1e-6) -60f else (20.0 * log10(rms)).toFloat().coerceIn(-60f, 0f)
        val zcrRatio = zeroCrossings.toFloat() / sampleCount.toFloat()
        val speechActivity = db > -34f && zcrRatio in 0.02f..0.38f

        val prev = _audioMetrics.value
        val nextFrames = prev.framesProcessed + 1L
        if (nextFrames == 1L || abs(db - prev.rmsDbfs) > 1.5f || speechActivity != prev.speechBandEnergyActive || nextFrames % 15L == 0L) {
            _audioMetrics.value = WakeWordAudioMetrics(
                rmsDbfs = db,
                speechBandEnergyActive = speechActivity,
                framesProcessed = nextFrames
            )
        }

        // Only run native Porcupine KWS when speech-band acoustic energy is present and frame size >= 512
        val spotter = nativeSpotter
        if (spotter != null && sampleCount >= FRAME_LENGTH_SAMPLES && db > -40f && zcrRatio in 0.015f..0.45f) {
            val frame = if (sampleCount == FRAME_LENGTH_SAMPLES && samples.size == FRAME_LENGTH_SAMPLES) {
                samples
            } else {
                samples.copyOfRange(0, FRAME_LENGTH_SAMPLES)
            }
            val keywordIndex = runCatching { spotter.process(frame) }.getOrDefault(-1)
            if (keywordIndex >= 0) {
                val now = clockMillis()
                if (now - lastTriggerTimestampMs >= TRIGGER_COOLDOWN_MS) {
                    lastTriggerTimestampMs = now
                    _state.value = WakeWordState.TRIGGERED
                    _triggerEvents.tryEmit(now)
                }
            }
        }
    }

    private fun initSpotterIfPossibleLocked(accessKey: String) {
        if (accessKey.isBlank()) return
        try {
            val factory = kwsSpotterFactory ?: defaultPorcupineFactory
            nativeSpotter = factory(appContext, accessKey)
            lastNativeInitError = null
        } catch (t: Throwable) {
            nativeSpotter = null
            lastNativeInitError = t.localizedMessage ?: t.javaClass.simpleName
        }
    }

    private fun closeSpotterLocked() {
        runCatching { nativeSpotter?.close() }
        nativeSpotter = null
    }

    override fun resetMetrics() {
        _audioMetrics.value = WakeWordAudioMetrics()
    }

    override fun release() {
        synchronized(lock) {
            closeSpotterLocked()
        }
        _state.value = WakeWordState.DORMANT
        resetMetrics()
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FRAME_LENGTH_SAMPLES = 512
        const val TRIGGER_COOLDOWN_MS = 2_500L

        private val defaultPorcupineFactory: (Context?, String) -> NativeKeywordSpotter = { ctx, key ->
            requireNotNull(ctx) { "Android Context required to initialize Picovoice Porcupine." }
            val porcupine = Porcupine.Builder()
                .setAccessKey(key)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .setSensitivity(0.65f)
                .build(ctx)
            object : NativeKeywordSpotter {
                override fun process(frame512: ShortArray): Int = porcupine.process(frame512)
                override fun close() = porcupine.delete()
            }
        }
    }
}
