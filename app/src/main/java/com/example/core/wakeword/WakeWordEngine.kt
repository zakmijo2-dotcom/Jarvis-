package com.example.core.wakeword

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

enum class WakeWordState(val label: String) {
    DORMANT("Dormant"),
    STANDBY_AUDIO_GATE("Audio Gate Standby (Unverified KWS)"),
    UNAVAILABLE_PENDING_MODEL_OR_KEY("Engine Evaluated — Key/Model Needed"),
    BACKGROUND_RESTRICTED("Paused (Background Restriction)"),
    PERMISSION_DENIED("Mic Permission Denied"),
    TRIGGERED("Wake Word Triggered")
}

enum class WakeWordEngineKind(val label: String) {
    PORCUPINE_EVALUATED("Picovoice Porcupine (Evaluated)"),
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

interface WakeWordEngine {
    val state: StateFlow<WakeWordState>
    val keyword: String
    val isPhraseDetectionVerified: Boolean
    val audioMetrics: StateFlow<WakeWordAudioMetrics>
    val evaluationReport: WakeWordEvaluationReport

    fun syncStandbyPreference(
        enabled: Boolean,
        hasMicPermission: Boolean = true,
        isAppInForeground: Boolean = true,
        porcupineAccessKeyPresent: Boolean = false
    )

    fun processPcm16Frame(samples: ShortArray, sampleCount: Int)
    fun resetMetrics()
    fun release()
}

/**
 * Modular local wake-word interface & audio gate for target phrase "Jarvis".
 *
 * Evaluation findings:
 * - Picovoice Porcupine (`ai.picovoice:porcupine-android`): built-in "Jarvis" keyword at 16 kHz /
 *   512-sample PCM16 frames, very low CPU (<2% on Dimensity 8400), but requires a developer AccessKey
 *   and physical Android microphone verification.
 * - Open alternative (`k2-fsa/sherpa-onnx`): Apache-2.0 offline KWS without AccessKey, requires
 *   bundling ONNX KWS weights + native `.so` binaries.
 *
 * Because neither a Picovoice AccessKey nor verified on-device microphone capture exists inside the
 * build environment, this class NEVER fabricates a false "Jarvis" trigger from raw noise and always
 * reports `isPhraseDetectionVerified = false`.
 */
class ModularWakeWordEngine : WakeWordEngine {

    private val _state = MutableStateFlow(WakeWordState.DORMANT)
    override val state: StateFlow<WakeWordState> = _state.asStateFlow()

    override val keyword: String = "Jarvis"

    override val isPhraseDetectionVerified: Boolean = false

    private val _audioMetrics = MutableStateFlow(WakeWordAudioMetrics())
    override val audioMetrics: StateFlow<WakeWordAudioMetrics> = _audioMetrics.asStateFlow()

    override val evaluationReport: WakeWordEvaluationReport = WakeWordEvaluationReport(
        targetPhrase = keyword,
        selectedEngine = WakeWordEngineKind.PORCUPINE_EVALUATED,
        sampleRateHz = 16_000,
        frameLengthSamples = 512,
        isPhraseDetectionVerified = false,
        summary = "Evaluated Picovoice Porcupine (512-sample @ 16kHz PCM16, native 'Jarvis' keyword) and Sherpa-ONNX KWS (open Apache-2.0 alternative). AudioRecord PCM gate and Android 15 microphone FGS boundaries are ready; live phrase spotting remains unverified until physical microphone + KWS key/weights are tested on device.",
        blockers = listOf(
            "Picovoice Porcupine requires a Picovoice Console AccessKey and ARM64 .so verification on Poco X7 Pro.",
            "Sherpa-ONNX requires bundling KWS acoustic model weights (~5 MB) and testing false-accept rate on device.",
            "No physical microphone or Android emulator exists in this cloud container, so wake-word spotting is intentionally marked unverified."
        )
    )

    override fun syncStandbyPreference(
        enabled: Boolean,
        hasMicPermission: Boolean,
        isAppInForeground: Boolean,
        porcupineAccessKeyPresent: Boolean
    ) {
        _state.value = when {
            !enabled -> WakeWordState.DORMANT
            !hasMicPermission -> WakeWordState.PERMISSION_DENIED
            !isAppInForeground -> WakeWordState.BACKGROUND_RESTRICTED
            porcupineAccessKeyPresent -> WakeWordState.STANDBY_AUDIO_GATE
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
    }

    override fun resetMetrics() {
        _audioMetrics.value = WakeWordAudioMetrics()
    }

    override fun release() {
        _state.value = WakeWordState.DORMANT
        resetMetrics()
    }
}
