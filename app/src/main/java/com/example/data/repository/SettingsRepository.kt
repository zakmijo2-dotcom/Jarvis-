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
import com.example.core.agent.AgentRoutingMode
import com.example.core.cloud.CloudProviderKind
import com.example.core.security.KeystoreHealthReport
import com.example.core.security.SecureCredentialStore
import com.example.core.web.WebSearchProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

enum class InferenceBackendPreference(val label: String) {
    AUTO_GPU_FALLBACK("Auto (GPU -> CPU)"),
    GPU_ONLY("Force GPU (OpenCL)"),
    CPU_ONLY("Force CPU (XNNPACK)")
}

enum class AppThemeMode(val label: String) {
    DARK("Dark Graphite"),
    LIGHT("Light Mode"),
    SYSTEM("Follow System");

    companion object {
        fun fromName(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DARK
    }
}

data class JarvisSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val orbAnimationEnabled: Boolean = true,
    val orbGlowIntensity: Float = 0.85f,
    val ttsAutoSpeak: Boolean = true,
    val ttsSpeechRate: Float = 1.0f,
    val voiceLanguageTag: String = "auto",
    val selectedTtsVoiceName: String = "",
    val voiceContinuousMode: Boolean = false,
    val pauseMicInBackground: Boolean = true,
    val wakeWordStandbyEnabled: Boolean = false,
    val porcupineAccessKey: String = "",
    val notificationReaderEnabled: Boolean = false,
    val notificationPrivacyMaskEnabled: Boolean = true,
    val accessibilityAutomationEnabled: Boolean = false,
    val telemetryPollingSeconds: Int = 5,
    val hapticsEnabled: Boolean = true,
    val inferenceBackend: InferenceBackendPreference = InferenceBackendPreference.AUTO_GPU_FALLBACK,
    val contextLengthTokens: Int = 4096,
    val maxOutputTokens: Int = 512,
    val samplerTemperature: Float = 0.7f,
    val samplerTopK: Int = 40,
    val samplerTopP: Float = 0.95f,
    val huggingFaceToken: String = "",
    // Phase 6 — Hybrid Routing, Cloud AI, Privacy Consent & Web Research
    val routingMode: AgentRoutingMode = AgentRoutingMode.AUTO_HYBRID,
    val cloudUploadConsentGranted: Boolean = false,
    val cloudProviderKind: CloudProviderKind = CloudProviderKind.OPENAI_COMPATIBLE,
    val cloudEndpointUrl: String = "",
    val cloudModelId: String = "",
    val cloudApiKey: String = "",
    val cloudTimeoutSeconds: Int = 30,
    val webResearchEnabled: Boolean = false,
    val webSearchProvider: WebSearchProviderKind = WebSearchProviderKind.WIKIPEDIA_OPENSEARCH,
    val webSearchCustomUrl: String = "",
    val webSearchApiKey: String = "",
    // Wireless ADB saved non-secret endpoint metadata (pairing codes are never stored)
    val wirelessAdbHost: String = "127.0.0.1",
    val wirelessAdbConnectPort: Int = 0,
    val wirelessAdbPaired: Boolean = false
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
        val VOICE_LANGUAGE_TAG = stringPreferencesKey("voice_language_tag")
        val SELECTED_TTS_VOICE = stringPreferencesKey("selected_tts_voice")
        val VOICE_CONTINUOUS_MODE = booleanPreferencesKey("voice_continuous_mode")
        val PAUSE_MIC_IN_BACKGROUND = booleanPreferencesKey("pause_mic_in_background")
        val WAKE_WORD_STANDBY = booleanPreferencesKey("wake_word_standby")
        val PORCUPINE_ACCESS_KEY_ENC = stringPreferencesKey("porcupine_access_key_encrypted_v1")
        val NOTIFICATION_READER_ENABLED = booleanPreferencesKey("notification_reader_enabled")
        val NOTIFICATION_PRIVACY_MASK = booleanPreferencesKey("notification_privacy_mask")
        val ACCESSIBILITY_AUTOMATION_ENABLED = booleanPreferencesKey("accessibility_automation_enabled")
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

        // Phase 6
        val ROUTING_MODE = stringPreferencesKey("agent_routing_mode")
        val CLOUD_UPLOAD_CONSENT = booleanPreferencesKey("cloud_upload_consent")
        val CLOUD_PROVIDER_KIND = stringPreferencesKey("cloud_provider_kind")
        val CLOUD_ENDPOINT_URL = stringPreferencesKey("cloud_endpoint_url")
        val CLOUD_MODEL_ID = stringPreferencesKey("cloud_model_id")
        val CLOUD_API_KEY_ENC = stringPreferencesKey("cloud_api_key_encrypted_v1")
        val CLOUD_TIMEOUT_SECONDS = intPreferencesKey("cloud_timeout_seconds")
        val WEB_RESEARCH_ENABLED = booleanPreferencesKey("web_research_enabled")
        val WEB_SEARCH_PROVIDER = stringPreferencesKey("web_search_provider")
        val WEB_SEARCH_CUSTOM_URL = stringPreferencesKey("web_search_custom_url")
        val WEB_SEARCH_API_KEY_ENC = stringPreferencesKey("web_search_api_key_encrypted_v1")
        val THEME_MODE = stringPreferencesKey("app_theme_mode")
        val WIRELESS_ADB_HOST = stringPreferencesKey("wireless_adb_host")
        val WIRELESS_ADB_PORT = intPreferencesKey("wireless_adb_port")
        val WIRELESS_ADB_PAIRED = booleanPreferencesKey("wireless_adb_paired")
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

        val cloudKeyEnc = prefs[Keys.CLOUD_API_KEY_ENC].orEmpty()
        val resolvedCloudKey = if (cloudKeyEnc.isNotBlank()) credentialStore.decrypt(cloudKeyEnc) else ""

        val webKeyEnc = prefs[Keys.WEB_SEARCH_API_KEY_ENC].orEmpty()
        val resolvedWebKey = if (webKeyEnc.isNotBlank()) credentialStore.decrypt(webKeyEnc) else ""

        val porcupineKeyEnc = prefs[Keys.PORCUPINE_ACCESS_KEY_ENC].orEmpty()
        val resolvedPorcupineKey = if (porcupineKeyEnc.isNotBlank()) credentialStore.decrypt(porcupineKeyEnc) else ""

        JarvisSettings(
            themeMode = AppThemeMode.fromName(prefs[Keys.THEME_MODE]),
            orbAnimationEnabled = prefs[Keys.ORB_ANIMATION_ENABLED] ?: true,
            orbGlowIntensity = (prefs[Keys.ORB_GLOW_INTENSITY] ?: 0.85f).coerceIn(0.2f, 1.0f),
            ttsAutoSpeak = prefs[Keys.TTS_AUTO_SPEAK] ?: true,
            ttsSpeechRate = (prefs[Keys.TTS_SPEECH_RATE] ?: 1.0f).coerceIn(0.6f, 1.6f),
            voiceLanguageTag = prefs[Keys.VOICE_LANGUAGE_TAG] ?: "auto",
            selectedTtsVoiceName = prefs[Keys.SELECTED_TTS_VOICE] ?: "",
            voiceContinuousMode = prefs[Keys.VOICE_CONTINUOUS_MODE] ?: false,
            pauseMicInBackground = prefs[Keys.PAUSE_MIC_IN_BACKGROUND] ?: true,
            wakeWordStandbyEnabled = prefs[Keys.WAKE_WORD_STANDBY] ?: false,
            porcupineAccessKey = resolvedPorcupineKey,
            notificationReaderEnabled = prefs[Keys.NOTIFICATION_READER_ENABLED] ?: false,
            notificationPrivacyMaskEnabled = prefs[Keys.NOTIFICATION_PRIVACY_MASK] ?: true,
            accessibilityAutomationEnabled = prefs[Keys.ACCESSIBILITY_AUTOMATION_ENABLED] ?: false,
            telemetryPollingSeconds = (prefs[Keys.TELEMETRY_POLLING_SECONDS] ?: 5).coerceIn(2, 30),
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            inferenceBackend = backend,
            contextLengthTokens = (prefs[Keys.CONTEXT_LENGTH_TOKENS] ?: 4096).coerceIn(1024, 8192),
            maxOutputTokens = (prefs[Keys.MAX_OUTPUT_TOKENS] ?: 512).coerceIn(64, 2048),
            samplerTemperature = (prefs[Keys.SAMPLER_TEMPERATURE] ?: 0.7f).coerceIn(0.0f, 1.5f),
            samplerTopK = (prefs[Keys.SAMPLER_TOP_K] ?: 40).coerceIn(1, 100),
            samplerTopP = (prefs[Keys.SAMPLER_TOP_P] ?: 0.95f).coerceIn(0.1f, 1.0f),
            huggingFaceToken = resolvedToken,
            routingMode = AgentRoutingMode.fromName(prefs[Keys.ROUTING_MODE]),
            cloudUploadConsentGranted = prefs[Keys.CLOUD_UPLOAD_CONSENT] ?: false,
            cloudProviderKind = CloudProviderKind.fromIdOrName(prefs[Keys.CLOUD_PROVIDER_KIND]),
            cloudEndpointUrl = prefs[Keys.CLOUD_ENDPOINT_URL].orEmpty(),
            cloudModelId = prefs[Keys.CLOUD_MODEL_ID].orEmpty(),
            cloudApiKey = resolvedCloudKey,
            cloudTimeoutSeconds = (prefs[Keys.CLOUD_TIMEOUT_SECONDS] ?: 30).coerceIn(5, 120),
            webResearchEnabled = prefs[Keys.WEB_RESEARCH_ENABLED] ?: false,
            webSearchProvider = WebSearchProviderKind.fromName(prefs[Keys.WEB_SEARCH_PROVIDER]),
            webSearchCustomUrl = prefs[Keys.WEB_SEARCH_CUSTOM_URL].orEmpty(),
            webSearchApiKey = resolvedWebKey,
            wirelessAdbHost = prefs[Keys.WIRELESS_ADB_HOST]?.ifBlank { "127.0.0.1" } ?: "127.0.0.1",
            wirelessAdbConnectPort = (prefs[Keys.WIRELESS_ADB_PORT] ?: 0).coerceIn(0, 65535),
            wirelessAdbPaired = prefs[Keys.WIRELESS_ADB_PAIRED] ?: false
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

    suspend fun setVoiceLanguageTag(tag: String) {
        dataStore.edit { it[Keys.VOICE_LANGUAGE_TAG] = tag.ifBlank { "auto" } }
    }

    suspend fun setSelectedTtsVoiceName(voiceName: String) {
        dataStore.edit { it[Keys.SELECTED_TTS_VOICE] = voiceName }
    }

    suspend fun setVoiceContinuousMode(enabled: Boolean) {
        dataStore.edit { it[Keys.VOICE_CONTINUOUS_MODE] = enabled }
    }

    suspend fun setPauseMicInBackground(enabled: Boolean) {
        dataStore.edit { it[Keys.PAUSE_MIC_IN_BACKGROUND] = enabled }
    }

    suspend fun setWakeWordStandbyEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WAKE_WORD_STANDBY] = enabled }
    }

    fun verifyKeystoreHealth(): KeystoreHealthReport = credentialStore.verifyKeystoreHealth()

    fun lastKeystoreError(): String? = credentialStore.lastKeystoreError

    suspend fun setPorcupineAccessKey(accessKey: String) {
        val trimmed = accessKey.trim()
        dataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(Keys.PORCUPINE_ACCESS_KEY_ENC)
            } else {
                val encrypted = credentialStore.encryptResult(trimmed).getOrElse { "" }
                if (encrypted.isNotBlank()) {
                    prefs[Keys.PORCUPINE_ACCESS_KEY_ENC] = encrypted
                }
            }
        }
    }

    suspend fun setNotificationReaderEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATION_READER_ENABLED] = enabled }
    }

    suspend fun setNotificationPrivacyMaskEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATION_PRIVACY_MASK] = enabled }
    }

    suspend fun setAccessibilityAutomationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ACCESSIBILITY_AUTOMATION_ENABLED] = enabled }
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
                val encrypted = credentialStore.encryptResult(trimmed).getOrElse { "" }
                if (encrypted.isNotBlank()) {
                    prefs[Keys.HUGGING_FACE_TOKEN_ENC] = encrypted
                }
            }
        }
    }

    suspend fun setRoutingMode(mode: AgentRoutingMode) {
        dataStore.edit { it[Keys.ROUTING_MODE] = mode.name }
    }

    suspend fun setCloudUploadConsentGranted(granted: Boolean) {
        dataStore.edit { it[Keys.CLOUD_UPLOAD_CONSENT] = granted }
    }

    suspend fun setCloudProviderKind(kind: CloudProviderKind) {
        dataStore.edit { it[Keys.CLOUD_PROVIDER_KIND] = kind.name }
    }

    suspend fun setCloudEndpointUrl(url: String) {
        dataStore.edit { it[Keys.CLOUD_ENDPOINT_URL] = url.trim() }
    }

    suspend fun setCloudModelId(modelId: String) {
        dataStore.edit { it[Keys.CLOUD_MODEL_ID] = modelId.trim() }
    }

    suspend fun setCloudApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        dataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(Keys.CLOUD_API_KEY_ENC)
            } else {
                val encrypted = credentialStore.encryptResult(trimmed).getOrElse { "" }
                if (encrypted.isNotBlank()) {
                    prefs[Keys.CLOUD_API_KEY_ENC] = encrypted
                }
            }
        }
    }

    suspend fun setCloudTimeoutSeconds(seconds: Int) {
        dataStore.edit { it[Keys.CLOUD_TIMEOUT_SECONDS] = seconds.coerceIn(5, 120) }
    }

    suspend fun setWebResearchEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WEB_RESEARCH_ENABLED] = enabled }
    }

    suspend fun setWebSearchProvider(provider: WebSearchProviderKind) {
        dataStore.edit { it[Keys.WEB_SEARCH_PROVIDER] = provider.name }
    }

    suspend fun setWebSearchCustomUrl(url: String) {
        dataStore.edit { it[Keys.WEB_SEARCH_CUSTOM_URL] = url.trim() }
    }

    suspend fun setWebSearchApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        dataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(Keys.WEB_SEARCH_API_KEY_ENC)
            } else {
                val encrypted = credentialStore.encryptResult(trimmed).getOrElse { "" }
                if (encrypted.isNotBlank()) {
                    prefs[Keys.WEB_SEARCH_API_KEY_ENC] = encrypted
                }
            }
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun saveWirelessAdbEndpoint(host: String, port: Int, paired: Boolean) {
        dataStore.edit {
            it[Keys.WIRELESS_ADB_HOST] = host.trim().ifBlank { "127.0.0.1" }
            it[Keys.WIRELESS_ADB_PORT] = port.coerceIn(0, 65535)
            it[Keys.WIRELESS_ADB_PAIRED] = paired
        }
    }

    suspend fun migrateLegacyTokensIfNeeded() {
        dataStore.edit { prefs ->
            val legacy = prefs[Keys.HUGGING_FACE_TOKEN_LEGACY].orEmpty().trim()
            if (legacy.isNotEmpty() && prefs[Keys.HUGGING_FACE_TOKEN_ENC].isNullOrBlank()) {
                val enc = credentialStore.encrypt(legacy)
                if (enc.isNotBlank()) {
                    prefs[Keys.HUGGING_FACE_TOKEN_ENC] = enc
                }
            }
            prefs.remove(Keys.HUGGING_FACE_TOKEN_LEGACY)
        }
    }
}
