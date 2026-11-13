package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.security.SecureCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

enum class InferenceBackendPreference(val label: String) {
    AUTO_GPU_FALLBACK("Auto (GPU -> CPU)"),
    GPU_ONLY("Force GPU (OpenCL)"),
    CPU_ONLY("Force CPU (XNNPACK)")
}

data class JarvisSettings(
    val orbAnimationEnabled: Boolean = true,
    val orbGlowIntensity: Float = 0.85f,
    val ttsAutoSpeak: Boolean = true,
    val ttsSpeechRate: Float = 1.0f,
    val wakeWordStandbyEnabled: Boolean = false,
    val telemetryPollingSeconds: Int = 5,
    val hapticsEnabled: Boolean = true,
    val inferenceBackend: InferenceBackendPreference = InferenceBackendPreference.AUTO_GPU_FALLBACK,
    val contextLengthTokens: Int = 4096,
    val maxOutputTokens: Int = 512,
    val samplerTemperature: Float = 0.7f,
    val samplerTopK: Int = 40,
    val samplerTopP: Float = 0.95f,
    val huggingFaceToken: String = ""
)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val credentialStore: SecureCredentialStore = SecureCredentialStore()
) {
    constructor(context: Context) : this(context.applicationContext.jarvisDataStore)

    private object Keys {
        val ORB_ANIMATION_ENABLED = booleanPreferencesKey("orb_animation_enabled")
        val ORB_GLOW_INTENSITY = floatPreferencesKey("orb_glow_intensity")
        val TTS_AUTO_SPEAK = booleanPreferencesKey("tts_auto_speak")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val WAKE_WORD_STANDBY = booleanPreferencesKey("wake_word_standby")
        val TELEMETRY_POLLING_SECONDS = intPreferencesKey("telemetry_polling_seconds")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val INFERENCE_BACKEND = stringPreferencesKey("inference_backend")
        val CONTEXT_LENGTH_TOKENS = intPreferencesKey("context_length_tokens")
        val MAX_OUTPUT_TOKENS = intPreferencesKey("max_output_tokens")
        val SAMPLER_TEMPERATURE = floatPreferencesKey("sampler_temperature")
        val SAMPLER_TOP_K = intPreferencesKey("sampler_top_k")
        val SAMPLER_TOP_P = floatPreferencesKey("sampler_top_p")
        val HUGGING_FACE_TOKEN_LEGACY = stringPreferencesKey("hugging_face_token")
        val HUGGING_FACE_TOKEN_ENC = stringPreferencesKey("hf_token_encrypted_v1")
    }

    val settingsFlow: Flow<JarvisSettings> = dataStore.data.map { prefs ->
        val backendRaw = prefs[Keys.INFERENCE_BACKEND]
        val backend = runCatching {
            if (backendRaw != null) InferenceBackendPreference.valueOf(backendRaw)
            else InferenceBackendPreference.AUTO_GPU_FALLBACK
        }.getOrDefault(InferenceBackendPreference.AUTO_GPU_FALLBACK)

        val encryptedToken = prefs[Keys.HUGGING_FACE_TOKEN_ENC].orEmpty()
        val resolvedToken = if (encryptedToken.isNotBlank()) {
            credentialStore.decrypt(encryptedToken)
        } else {
            prefs[Keys.HUGGING_FACE_TOKEN_LEGACY].orEmpty()
        }

        JarvisSettings(
            orbAnimationEnabled = prefs[Keys.ORB_ANIMATION_ENABLED] ?: true,
            orbGlowIntensity = (prefs[Keys.ORB_GLOW_INTENSITY] ?: 0.85f).coerceIn(0.2f, 1.0f),
            ttsAutoSpeak = prefs[Keys.TTS_AUTO_SPEAK] ?: true,
            ttsSpeechRate = (prefs[Keys.TTS_SPEECH_RATE] ?: 1.0f).coerceIn(0.6f, 1.6f),
            wakeWordStandbyEnabled = prefs[Keys.WAKE_WORD_STANDBY] ?: false,
            telemetryPollingSeconds = (prefs[Keys.TELEMETRY_POLLING_SECONDS] ?: 5).coerceIn(2, 30),
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            inferenceBackend = backend,
            contextLengthTokens = (prefs[Keys.CONTEXT_LENGTH_TOKENS] ?: 4096).coerceIn(1024, 8192),
            maxOutputTokens = (prefs[Keys.MAX_OUTPUT_TOKENS] ?: 512).coerceIn(64, 2048),
            samplerTemperature = (prefs[Keys.SAMPLER_TEMPERATURE] ?: 0.7f).coerceIn(0.0f, 1.5f),
            samplerTopK = (prefs[Keys.SAMPLER_TOP_K] ?: 40).coerceIn(1, 100),
            samplerTopP = (prefs[Keys.SAMPLER_TOP_P] ?: 0.95f).coerceIn(0.1f, 1.0f),
            huggingFaceToken = resolvedToken
        )
    }

    suspend fun setOrbAnimationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ORB_ANIMATION_ENABLED] = enabled }
    }

    suspend fun setOrbGlowIntensity(intensity: Float) {
        dataStore.edit { it[Keys.ORB_GLOW_INTENSITY] = intensity.coerceIn(0.2f, 1.0f) }
    }

    suspend fun setTtsAutoSpeak(enabled: Boolean) {
        dataStore.edit { it[Keys.TTS_AUTO_SPEAK] = enabled }
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        dataStore.edit { it[Keys.TTS_SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setWakeWordStandbyEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WAKE_WORD_STANDBY] = enabled }
    }

    suspend fun setTelemetryPollingSeconds(seconds: Int) {
        dataStore.edit { it[Keys.TELEMETRY_POLLING_SECONDS] = seconds.coerceIn(2, 30) }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setInferenceBackend(pref: InferenceBackendPreference) {
        dataStore.edit { it[Keys.INFERENCE_BACKEND] = pref.name }
    }

    suspend fun setContextLengthTokens(tokens: Int) {
        dataStore.edit { it[Keys.CONTEXT_LENGTH_TOKENS] = tokens.coerceIn(1024, 8192) }
    }

    suspend fun setMaxOutputTokens(tokens: Int) {
        dataStore.edit { it[Keys.MAX_OUTPUT_TOKENS] = tokens.coerceIn(64, 2048) }
    }

    suspend fun setSamplerTemperature(temp: Float) {
        dataStore.edit { it[Keys.SAMPLER_TEMPERATURE] = temp.coerceIn(0.0f, 1.5f) }
    }

    suspend fun setSamplerTopK(topK: Int) {
        dataStore.edit { it[Keys.SAMPLER_TOP_K] = topK.coerceIn(1, 100) }
    }

    suspend fun setSamplerTopP(topP: Float) {
        dataStore.edit { it[Keys.SAMPLER_TOP_P] = topP.coerceIn(0.1f, 1.0f) }
    }

    suspend fun setHuggingFaceToken(token: String) {
        val trimmed = token.trim()
        dataStore.edit { prefs ->
            prefs.remove(Keys.HUGGING_FACE_TOKEN_LEGACY)
            if (trimmed.isEmpty()) {
                prefs.remove(Keys.HUGGING_FACE_TOKEN_ENC)
            } else {
                prefs[Keys.HUGGING_FACE_TOKEN_ENC] = credentialStore.encrypt(trimmed)
            }
        }
    }
}
