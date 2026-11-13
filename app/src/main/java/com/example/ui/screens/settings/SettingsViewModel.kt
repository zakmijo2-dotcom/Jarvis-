package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.memory.LocalMemory
import com.example.core.wakeword.WakeWordEngine
import com.example.core.wakeword.WakeWordState
import com.example.data.repository.ConversationRepository
import com.example.data.repository.InferenceBackendPreference
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: JarvisSettings = JarvisSettings(),
    val wakeWordState: WakeWordState = WakeWordState.DORMANT_PHASE_2,
    val wakeWordKeyword: String = "Hey Jarvis",
    val memoryFactsCount: Int = 0,
    val statusBannerMessage: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val wakeWordEngine: WakeWordEngine,
    private val localMemory: LocalMemory
) : ViewModel() {

    private val statusBanner = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        wakeWordEngine.state,
        localMemory.observeFacts(),
        statusBanner
    ) { settings, wakeState, facts, banner ->
        SettingsUiState(
            settings = settings,
            wakeWordState = wakeState,
            wakeWordKeyword = wakeWordEngine.keyword,
            memoryFactsCount = facts.size,
            statusBannerMessage = banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setOrbAnimationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setOrbAnimationEnabled(enabled) }
    }

    fun setOrbGlowIntensity(intensity: Float) {
        viewModelScope.launch { settingsRepository.setOrbGlowIntensity(intensity) }
    }

    fun setTtsAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTtsAutoSpeak(enabled) }
    }

    fun setTtsSpeechRate(rate: Float) {
        viewModelScope.launch { settingsRepository.setTtsSpeechRate(rate) }
    }

    fun setWakeWordStandbyEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWakeWordStandbyEnabled(enabled) }
    }

    fun setTelemetryPollingSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setTelemetryPollingSeconds(seconds) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticsEnabled(enabled) }
    }

    fun setInferenceBackend(pref: InferenceBackendPreference) {
        viewModelScope.launch { settingsRepository.setInferenceBackend(pref) }
    }

    fun setContextLengthTokens(tokens: Int) {
        viewModelScope.launch { settingsRepository.setContextLengthTokens(tokens) }
    }

    fun setMaxOutputTokens(tokens: Int) {
        viewModelScope.launch { settingsRepository.setMaxOutputTokens(tokens) }
    }

    fun setSamplerTemperature(temp: Float) {
        viewModelScope.launch { settingsRepository.setSamplerTemperature(temp) }
    }

    fun setSamplerTopK(topK: Int) {
        viewModelScope.launch { settingsRepository.setSamplerTopK(topK) }
    }

    fun setSamplerTopP(topP: Float) {
        viewModelScope.launch { settingsRepository.setSamplerTopP(topP) }
    }

    fun setHuggingFaceToken(token: String) {
        viewModelScope.launch {
            settingsRepository.setHuggingFaceToken(token)
            statusBanner.value = if (token.isBlank()) {
                "Hugging Face credential cleared from encrypted storage."
            } else {
                "Hugging Face token encrypted with Android Keystore & saved."
            }
        }
    }

    fun purgeConversationArchive() {
        viewModelScope.launch {
            conversationRepository.clearAllAndReset()
            localMemory.clearFacts()
            statusBanner.value = "Local conversation archive & memory purged."
        }
    }

    fun dismissBanner() {
        statusBanner.value = null
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            conversationRepository: ConversationRepository,
            wakeWordEngine: WakeWordEngine,
            localMemory: LocalMemory
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    settingsRepository = settingsRepository,
                    conversationRepository = conversationRepository,
                    wakeWordEngine = wakeWordEngine,
                    localMemory = localMemory
                ) as T
            }
        }
    }
}
