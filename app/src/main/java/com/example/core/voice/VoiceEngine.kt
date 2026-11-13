package com.example.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

sealed interface VoiceEngineState {
    data object Idle : VoiceEngineState
    data class Listening(
        val rmsDb: Float = 0f,
        val partialTranscript: String = "",
        val isVoiceActive: Boolean = false
    ) : VoiceEngineState

    data object Processing : VoiceEngineState
    data class Speaking(val utterance: String) : VoiceEngineState
    data class Unavailable(val reason: String) : VoiceEngineState
    data class Error(
        val message: String,
        val kind: VoiceErrorKind = VoiceErrorKind.FATAL
    ) : VoiceEngineState
}

data class SpeechCompletionEvent(
    val interrupted: Boolean
)

data class TranscriptCapturedEvent(
    val text: String,
    val detectedLanguage: VoiceLanguageOption
)

interface VoiceEngine {
    val state: StateFlow<VoiceEngineState>
    val lastTranscript: StateFlow<String>
    val isTtsReady: StateFlow<Boolean>
    val detectedLanguage: StateFlow<VoiceLanguageOption>
    val availableVoices: StateFlow<List<TtsVoiceInfo>>
    val localeChecks: StateFlow<List<VoiceLocaleCheck>>
    val transcriptEvents: SharedFlow<TranscriptCapturedEvent>
    val speechCompletionEvents: SharedFlow<SpeechCompletionEvent>

    fun isSpeechRecognitionAvailable(): Boolean
    fun initializeTtsIfNeeded()
    fun startListening(languageOption: VoiceLanguageOption = VoiceLanguageOption.AUTO)
    fun stopListening()
    fun cancelListening()
    fun speak(
        text: String,
        speechRate: Float = 1.0f,
        languageOption: VoiceLanguageOption = VoiceLanguageOption.AUTO,
        preferredVoiceName: String = ""
    )
    fun stopSpeaking()
    fun resetError()
    fun release()
}

class AndroidVoiceEngine(
    context: Context
) : VoiceEngine {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _state = MutableStateFlow<VoiceEngineState>(VoiceEngineState.Idle)
    override val state: StateFlow<VoiceEngineState> = _state.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    override val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _isTtsReady = MutableStateFlow(false)
    override val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _detectedLanguage = MutableStateFlow(
        VoiceLanguageDetector.detectLanguage("", VoiceLanguageOption.AUTO)
    )
    override val detectedLanguage: StateFlow<VoiceLanguageOption> = _detectedLanguage.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceInfo>>(emptyList())
    override val availableVoices: StateFlow<List<TtsVoiceInfo>> = _availableVoices.asStateFlow()

    private val _localeChecks = MutableStateFlow<List<VoiceLocaleCheck>>(emptyList())
    override val localeChecks: StateFlow<List<VoiceLocaleCheck>> = _localeChecks.asStateFlow()

    private val _transcriptEvents = MutableSharedFlow<TranscriptCapturedEvent>(extraBufferCapacity = 8)
    override val transcriptEvents: SharedFlow<TranscriptCapturedEvent> = _transcriptEvents.asSharedFlow()

    private val _speechCompletionEvents = MutableSharedFlow<SpeechCompletionEvent>(extraBufferCapacity = 8)
    override val speechCompletionEvents: SharedFlow<SpeechCompletionEvent> = _speechCompletionEvents.asSharedFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false
    private var ttsInitializing = false
    private val pendingTtsCallbacks = mutableListOf<() -> Unit>()
    private var activeFinalUtteranceId: String? = null
    private var activeLanguageOption: VoiceLanguageOption = VoiceLanguageOption.AUTO
    private var focusRequest: AudioFocusRequest? = null

    override fun isSpeechRecognitionAvailable(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)

    override fun initializeTtsIfNeeded() {
        ensureTtsInitialized(null)
    }

    private fun ensureTtsInitialized(onReady: (() -> Unit)?) {
        if (ttsInitialized && textToSpeech != null) {
            onReady?.invoke()
            return
        }
        if (onReady != null) {
            pendingTtsCallbacks.add(onReady)
        }
        if (ttsInitializing) return
        ttsInitializing = true

        runOnMain {
            if (textToSpeech == null) {
                runCatching {
                    textToSpeech = TextToSpeech(appContext) { status ->
                        ttsInitializing = false
                        if (status == TextToSpeech.SUCCESS) {
                            configureTtsListeners()
                            ttsInitialized = true
                            _isTtsReady.value = true
                            refreshSystemVoicesAndLocales()
                            val callbacks = pendingTtsCallbacks.toList()
                            pendingTtsCallbacks.clear()
                            callbacks.forEach { it.invoke() }
                        } else {
                            ttsInitialized = false
                            _isTtsReady.value = false
                            val callbacks = pendingTtsCallbacks.toList()
                            pendingTtsCallbacks.clear()
                            callbacks.forEach { it.invoke() }
                        }
                    }
                }.onFailure {
                    ttsInitializing = false
                    ttsInitialized = false
                    _isTtsReady.value = false
                    pendingTtsCallbacks.clear()
                }
            }
        }
    }

    private fun configureTtsListeners() {
        val tts = textToSpeech ?: return
        runCatching { tts.language = Locale.getDefault() }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null && utteranceId == activeFinalUtteranceId) {
                    activeFinalUtteranceId = null
                    abandonAudioFocus()
                    if (_state.value is VoiceEngineState.Speaking) {
                        _state.value = VoiceEngineState.Idle
                    }
                    _speechCompletionEvents.tryEmit(SpeechCompletionEvent(interrupted = false))
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId != null && utteranceId == activeFinalUtteranceId) {
                    activeFinalUtteranceId = null
                    abandonAudioFocus()
                    if (_state.value is VoiceEngineState.Speaking) {
                        _state.value = VoiceEngineState.Idle
                    }
                    _speechCompletionEvents.tryEmit(SpeechCompletionEvent(interrupted = true))
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId != null && utteranceId == activeFinalUtteranceId) {
                    activeFinalUtteranceId = null
                    abandonAudioFocus()
                    _state.value = VoiceEngineState.Error(
                        message = "Text-to-speech playback failed.",
                        kind = VoiceErrorKind.FATAL
                    )
                    _speechCompletionEvents.tryEmit(SpeechCompletionEvent(interrupted = true))
                }
            }
        })
    }

    private fun refreshSystemVoicesAndLocales() {
        val tts = textToSpeech ?: return
        val voices = runCatching {
            tts.voices.orEmpty()
                .filter { v ->
                    val lang = v.locale.language.lowercase(Locale.ROOT)
                    lang == "ar" || lang == "fr" || lang == "en" || lang == Locale.getDefault().language.lowercase(Locale.ROOT)
                }
                .sortedWith(compareBy<android.speech.tts.Voice> { it.locale.toLanguageTag() }.thenByDescending { it.quality }.thenBy { it.isNetworkConnectionRequired })
                .map { voice ->
                    TtsVoiceInfo(
                        name = voice.name,
                        localeTag = voice.locale.toLanguageTag(),
                        displayLocale = voice.locale.displayName,
                        isNetworkRequired = voice.isNetworkConnectionRequired,
                        quality = voice.quality
                    )
                }
        }.getOrDefault(emptyList())
        _availableVoices.value = voices

        val checks = listOf(
            VoiceLanguageOption.ARABIC_ALGERIAN,
            VoiceLanguageOption.ARABIC,
            VoiceLanguageOption.FRENCH,
            VoiceLanguageOption.ENGLISH
        ).map { opt ->
            val avail = runCatching { tts.isLanguageAvailable(opt.toLocale()) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            VoiceLocaleCheck(
                option = opt,
                isTtsSupported = avail >= TextToSpeech.LANG_AVAILABLE,
                isOfflineDataInstalled = avail >= TextToSpeech.LANG_AVAILABLE && avail != TextToSpeech.LANG_MISSING_DATA
            )
        }
        _localeChecks.value = checks
    }

    override fun startListening(languageOption: VoiceLanguageOption) {
        activeLanguageOption = languageOption
        runOnMain {
            if (!isSpeechRecognitionAvailable()) {
                _state.value = VoiceEngineState.Unavailable(
                    "No Android SpeechRecognition service is available on this device/environment. Use the text fallback below."
                )
                return@runOnMain
            }
            stopSpeakingInternal(emitInterruption = false)
            val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
                it.setRecognitionListener(recognitionListener)
                speechRecognizer = it
            }
            val locale = languageOption.toLocale()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
            }
            _state.value = VoiceEngineState.Listening(
                rmsDb = 0f,
                partialTranscript = "",
                isVoiceActive = false
            )
            runCatching { recognizer.startListening(intent) }
                .onFailure {
                    _state.value = VoiceEngineState.Error(
                        message = it.localizedMessage ?: "Unable to start SpeechRecognizer.",
                        kind = VoiceErrorKind.FATAL
                    )
                }
        }
    }

    override fun stopListening() {
        runOnMain {
            runCatching { speechRecognizer?.stopListening() }
            if (_state.value is VoiceEngineState.Listening) {
                _state.value = VoiceEngineState.Processing
            }
        }
    }

    override fun cancelListening() {
        runOnMain {
            runCatching { speechRecognizer?.cancel() }
            if (_state.value is VoiceEngineState.Listening || _state.value is VoiceEngineState.Processing) {
                _state.value = VoiceEngineState.Idle
            }
        }
    }

    override fun speak(
        text: String,
        speechRate: Float,
        languageOption: VoiceLanguageOption,
        preferredVoiceName: String
    ) {
        val sanitized = TtsSanitizer.sanitizeForSpeech(text)
        if (sanitized.isEmpty()) return

        val resolvedLang = VoiceLanguageDetector.detectLanguage(
            text = sanitized,
            preferred = languageOption
        )
        _detectedLanguage.value = resolvedLang

        ensureTtsInitialized {
            runOnMain {
                val tts = textToSpeech
                if (tts == null || !ttsInitialized) {
                    _state.value = VoiceEngineState.Unavailable(
                        "Android TextToSpeech engine is not initialized on this device."
                    )
                    return@runOnMain
                }

                applyTtsVoiceAndLanguage(tts, resolvedLang, preferredVoiceName)
                tts.setSpeechRate(speechRate.coerceIn(0.6f, 1.6f))
                requestAudioFocus()

                val chunks = TtsSanitizer.chunkForTts(sanitized)
                if (chunks.isEmpty()) return@runOnMain
                val finalId = UUID.randomUUID().toString()
                activeFinalUtteranceId = finalId
                _state.value = VoiceEngineState.Speaking(sanitized)

                chunks.forEachIndexed { idx, chunk ->
                    val queueMode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    val utteranceId = if (idx == chunks.lastIndex) finalId else "${finalId}_$idx"
                    tts.speak(chunk, queueMode, null, utteranceId)
                }
            }
        }
    }

    private fun applyTtsVoiceAndLanguage(
        tts: TextToSpeech,
        targetLang: VoiceLanguageOption,
        preferredVoiceName: String
    ) {
        if (preferredVoiceName.isNotBlank()) {
            val matched = runCatching { tts.voices?.firstOrNull { it.name == preferredVoiceName } }.getOrNull()
            if (matched != null) {
                runCatching { tts.voice = matched }
                return
            }
        }

        val primaryLocale = targetLang.toLocale()
        val primaryStatus = runCatching { tts.setLanguage(primaryLocale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (primaryStatus < TextToSpeech.LANG_AVAILABLE) {
            val fallback = targetLang.fallbackLocale()
            val fallbackStatus = runCatching { tts.setLanguage(fallback) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (fallbackStatus < TextToSpeech.LANG_AVAILABLE) {
                runCatching { tts.setLanguage(Locale.getDefault()) }
            }
        }
    }

    override fun stopSpeaking() {
        runOnMain {
            val wasSpeaking = _state.value is VoiceEngineState.Speaking || activeFinalUtteranceId != null
            stopSpeakingInternal(emitInterruption = wasSpeaking)
            if (_state.value is VoiceEngineState.Speaking) {
                _state.value = VoiceEngineState.Idle
            }
        }
    }

    override fun resetError() {
        if (_state.value is VoiceEngineState.Error || _state.value is VoiceEngineState.Unavailable) {
            _state.value = VoiceEngineState.Idle
        }
    }

    override fun release() {
        runOnMain {
            runCatching { speechRecognizer?.cancel() }
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            stopSpeakingInternal(emitInterruption = false)
            runCatching { textToSpeech?.shutdown() }
            textToSpeech = null
            ttsInitialized = false
            ttsInitializing = false
            pendingTtsCallbacks.clear()
            _isTtsReady.value = false
        }
    }

    private fun stopSpeakingInternal(emitInterruption: Boolean) {
        activeFinalUtteranceId = null
        runCatching { textToSpeech?.stop() }
        abandonAudioFocus()
        if (emitInterruption) {
            _speechCompletionEvents.tryEmit(SpeechCompletionEvent(interrupted = true))
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        stopSpeaking()
                    }
                }
                .build()
                .also { focusRequest = it }
            runCatching { am.requestAudioFocus(req) }
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { req -> runCatching { am.abandonAudioFocusRequest(req) } }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceEngineState.Listening(
                rmsDb = 0f,
                partialTranscript = "",
                isVoiceActive = false
            )
        }

        override fun onBeginningOfSpeech() {
            val current = _state.value
            if (current is VoiceEngineState.Listening) {
                _state.value = current.copy(isVoiceActive = true)
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            val clamped = rmsdB.coerceAtLeast(0f)
            val current = _state.value
            if (current is VoiceEngineState.Listening) {
                _state.value = current.copy(
                    rmsDb = clamped,
                    isVoiceActive = current.isVoiceActive || clamped >= 4.0f
                )
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (_state.value is VoiceEngineState.Listening) {
                _state.value = VoiceEngineState.Processing
            }
        }

        override fun onError(error: Int) {
            val (message, kind) = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error." to VoiceErrorKind.FATAL
                SpeechRecognizer.ERROR_CLIENT -> "Speech recognition cancelled or client error." to VoiceErrorKind.BUSY
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required." to VoiceErrorKind.PERMISSION_DENIED
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network speech recognition service unavailable." to VoiceErrorKind.NETWORK
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Speak clearly or try text fallback." to VoiceErrorKind.TRANSIENT_NO_MATCH
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy; retrying standby." to VoiceErrorKind.BUSY
                SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error." to VoiceErrorKind.NETWORK
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out." to VoiceErrorKind.TIMEOUT
                12, 13 -> "Selected STT language pack is unavailable on device; try Arabic or system locale." to VoiceErrorKind.LANGUAGE_UNSUPPORTED
                5 -> "Speech recognizer rate limit or client interrupt." to VoiceErrorKind.BUSY
                else -> "Speech recognition error ($error)." to VoiceErrorKind.FATAL
            }
            _state.value = VoiceEngineState.Error(message = message, kind = kind)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognized = matches?.firstOrNull().orEmpty().trim()
            if (recognized.isNotEmpty()) {
                val detected = VoiceLanguageDetector.detectLanguage(recognized, activeLanguageOption)
                _detectedLanguage.value = detected
                _lastTranscript.value = recognized
                _state.value = VoiceEngineState.Idle
                _transcriptEvents.tryEmit(TranscriptCapturedEvent(recognized, detected))
            } else {
                _state.value = VoiceEngineState.Idle
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            val current = _state.value
            if (current is VoiceEngineState.Listening && partial.isNotEmpty()) {
                _detectedLanguage.value = VoiceLanguageDetector.detectLanguage(partial, activeLanguageOption)
                _state.value = current.copy(partialTranscript = partial, isVoiceActive = true)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
