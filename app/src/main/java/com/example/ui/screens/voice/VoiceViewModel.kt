package com.example.ui.screens.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.voice.VoiceEngine
import com.example.core.voice.VoiceEngineState
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class VoiceUiState(
    val engineState: VoiceEngineState = VoiceEngineState.Idle,
    val lastTranscript: String = "",
    val isRecognitionAvailable: Boolean = false,
    val isTtsReady: Boolean = false,
    val settings: JarvisSettings = JarvisSettings()
)

class VoiceViewModel(
    private val voiceEngine: VoiceEngine,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<VoiceUiState> = combine(
        voiceEngine.state,
        voiceEngine.lastTranscript,
        voiceEngine.isTtsReady,
        settingsRepository.settingsFlow
    ) { state, transcript, ttsReady, settings ->
        VoiceUiState(
            engineState = state,
            lastTranscript = transcript,
            isRecognitionAvailable = voiceEngine.isSpeechRecognitionAvailable(),
            isTtsReady = ttsReady,
            settings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VoiceUiState(
            isRecognitionAvailable = voiceEngine.isSpeechRecognitionAvailable()
        )
    )

    fun toggleListening() {
        when (voiceEngine.state.value) {
            is VoiceEngineState.Listening -> voiceEngine.stopListening()
            is VoiceEngineState.Speaking -> voiceEngine.stopSpeaking()
            else -> voiceEngine.startListening()
        }
    }

    fun speakStatusReadback() {
        val rate = uiState.value.settings.ttsSpeechRate
        val summary = "JARVIS Phase 1 voice console active. Android speech recognition and text-to-speech pipelines are online. Neural AI inference will link in Phase 2."
        voiceEngine.speak(summary, speechRate = rate)
    }

    fun speakTranscript(text: String) {
        voiceEngine.speak(text, speechRate = uiState.value.settings.ttsSpeechRate)
    }

    fun stopSpeaking() {
        voiceEngine.stopSpeaking()
    }

    fun dismissError() {
        voiceEngine.resetError()
    }

    override fun onCleared() {
        voiceEngine.stopListening()
        voiceEngine.stopSpeaking()
        super.onCleared()
    }

    companion object {
        fun factory(
            voiceEngine: VoiceEngine,
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VoiceViewModel(
                    voiceEngine = voiceEngine,
                    settingsRepository = settingsRepository
                ) as T
            }
        }
    }
}
