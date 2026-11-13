package com.example.core.voice

import java.util.Locale

enum class VoiceLanguageOption(
    val tag: String,
    val label: String,
    val shortLabel: String
) {
    AUTO("auto", "Auto Detect", "Auto"),
    ARABIC_ALGERIAN("ar-DZ", "Arabic (Algeria)", "ar-DZ"),
    ARABIC("ar-SA", "Arabic", "ar"),
    FRENCH("fr-FR", "French", "fr-FR"),
    ENGLISH("en-US", "English", "en-US");

    fun toLocale(systemLocale: Locale = Locale.getDefault()): Locale = when (this) {
        AUTO -> systemLocale
        ARABIC_ALGERIAN -> Locale.forLanguageTag("ar-DZ")
        ARABIC -> Locale.forLanguageTag("ar-SA")
        FRENCH -> Locale.forLanguageTag("fr-FR")
        ENGLISH -> Locale.forLanguageTag("en-US")
    }

    fun fallbackLocale(): Locale = when (this) {
        ARABIC_ALGERIAN -> Locale.forLanguageTag("ar")
        ARABIC -> Locale.forLanguageTag("ar")
        FRENCH -> Locale.FRENCH
        ENGLISH -> Locale.US
        AUTO -> Locale.getDefault()
    }

    companion object {
        fun fromTag(tag: String?): VoiceLanguageOption =
            entries.firstOrNull { it.tag.equals(tag?.trim(), ignoreCase = true) } ?: AUTO
    }
}

enum class VoiceConversationMode(val label: String) {
    PUSH_TO_TALK("Push-to-Talk"),
    CONTINUOUS("Continuous")
}

enum class MicStatus(val label: String) {
    OFF("MIC OFF"),
    ACTIVE_STT("MIC ACTIVE // STT"),
    STANDBY_WAKE_WORD("MIC STANDBY // FGS"),
    PAUSED_BACKGROUND("PAUSED // BACKGROUND"),
    PERMISSION_DENIED("MIC PERMISSION DENIED"),
    UNAVAILABLE("STT UNAVAILABLE")
}

enum class VoiceErrorKind {
    NONE,
    TRANSIENT_NO_MATCH,
    TIMEOUT,
    PERMISSION_DENIED,
    BUSY,
    NETWORK,
    LANGUAGE_UNSUPPORTED,
    UNAVAILABLE,
    FATAL
}

data class TtsVoiceInfo(
    val name: String,
    val localeTag: String,
    val displayLocale: String,
    val isNetworkRequired: Boolean,
    val quality: Int
)

data class VoiceLocaleCheck(
    val option: VoiceLanguageOption,
    val isTtsSupported: Boolean,
    val isOfflineDataInstalled: Boolean
)

data class VoiceTurnItem(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val detectedLanguage: VoiceLanguageOption,
    val telemetryNote: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
