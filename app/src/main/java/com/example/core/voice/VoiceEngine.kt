package com.example.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

sealed interface VoiceEngineState {
    data object Idle : VoiceEngineState
    data class Listening(
        val rmsDb: Float = 0f,
        val partialTranscript: String = ""
    ) : VoiceEngineState

    data object Processing : VoiceEngineState
    data class Speaking(val utterance: String) : VoiceEngineState
    data class Unavailable(val reason: String) : VoiceEngineState
    data class Error(val message: String) : VoiceEngineState
}

interface VoiceEngine {
    val state: StateFlow<VoiceEngineState>
    val lastTranscript: StateFlow<String>
    val isTtsReady: StateFlow<Boolean>
    fun isSpeechRecognitionAvailable(): Boolean
    fun startListening()
    fun stopListening()
    fun speak(text: String, speechRate: Float = 1.0f)
    fun stopSpeaking()
    fun resetError()
    fun release()
}

class AndroidVoiceEngine(
    context: Context
) : VoiceEngine {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<VoiceEngineState>(VoiceEngineState.Idle)
    override val state: StateFlow<VoiceEngineState> = _state.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    override val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _isTtsReady = MutableStateFlow(false)
    override val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false

    private fun ensureTtsInitialized(onReady: (() -> Unit)? = null) {
        if (textToSpeech != null) {
            if (ttsInitialized) onReady?.invoke()
            return
        }
        runOnMain {
            if (textToSpeech == null) {
                textToSpeech = TextToSpeech(appContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        textToSpeech?.language = Locale.getDefault()
                        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit
                            override fun onDone(utteranceId: String?) {
                                if (_state.value is VoiceEngineState.Speaking) {
                                    _state.value = VoiceEngineState.Idle
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                _state.value = VoiceEngineState.Error("Text-to-speech playback failed.")
                            }
                        })
                        ttsInitialized = true
                        _isTtsReady.value = true
                        onReady?.invoke()
                    } else {
                        _isTtsReady.value = false
                    }
                }
            }
        }
    }

    override fun isSpeechRecognitionAvailable(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)

    override fun startListening() {
        runOnMain {
            if (!isSpeechRecognitionAvailable()) {
                _state.value = VoiceEngineState.Unavailable(
                    "No Android SpeechRecognition service is available on this device/environment."
                )
                return@runOnMain
            }
            stopSpeakingInternal()
            val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
                it.setRecognitionListener(recognitionListener)
                speechRecognizer = it
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            _state.value = VoiceEngineState.Listening(rmsDb = 0f, partialTranscript = "")
            runCatching { recognizer.startListening(intent) }
                .onFailure {
                    _state.value = VoiceEngineState.Error(
                        it.localizedMessage ?: "Unable to start SpeechRecognizer."
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

    override fun speak(text: String, speechRate: Float) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        ensureTtsInitialized {
            runOnMain {
                val tts = textToSpeech
                if (tts == null || !ttsInitialized) {
                    _state.value = VoiceEngineState.Unavailable(
                        "Android TextToSpeech engine is not initialized on this device."
                    )
                    return@runOnMain
                }
                tts.setSpeechRate(speechRate.coerceIn(0.6f, 1.6f))
                _state.value = VoiceEngineState.Speaking(clean)
                val utteranceId = UUID.randomUUID().toString()
                tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
        }
    }

    override fun stopSpeaking() {
        runOnMain {
            stopSpeakingInternal()
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
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            runCatching {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            }
            textToSpeech = null
            ttsInitialized = false
            _isTtsReady.value = false
        }
    }

    private fun stopSpeakingInternal() {
        runCatching { textToSpeech?.stop() }
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
            _state.value = VoiceEngineState.Listening(rmsDb = 0f, partialTranscript = "")
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            val current = _state.value
            if (current is VoiceEngineState.Listening) {
                _state.value = current.copy(rmsDb = rmsdB.coerceAtLeast(0f))
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (_state.value is VoiceEngineState.Listening) {
                _state.value = VoiceEngineState.Processing
            }
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                SpeechRecognizer.ERROR_CLIENT -> "Speech client error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network speech service unavailable."
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy."
                SpeechRecognizer.ERROR_SERVER -> "Speech server error."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out."
                else -> "Speech recognition error ($error)."
            }
            _state.value = VoiceEngineState.Error(message)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognized = matches?.firstOrNull().orEmpty().trim()
            if (recognized.isNotEmpty()) {
                _lastTranscript.value = recognized
            }
            _state.value = VoiceEngineState.Idle
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            val current = _state.value
            if (current is VoiceEngineState.Listening && partial.isNotEmpty()) {
                _state.value = current.copy(partialTranscript = partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
