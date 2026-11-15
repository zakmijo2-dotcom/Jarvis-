package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.agent.AgentCore
import com.example.core.agent.AgentExecutionRecord
import com.example.core.agent.AgentRoutingMode
import com.example.core.cloud.CloudProviderKind
import com.example.core.cloud.CloudUsageStats
import com.example.core.memory.LocalMemory
import com.example.core.memory.MemoryFact
import com.example.core.memory.MemoryKind
import com.example.core.skills.AgentSkill
import com.example.core.skills.SkillEngine
import com.example.core.wakeword.WakeWordEngine
import com.example.core.wakeword.WakeWordState
import com.example.core.web.WebSearchProviderKind
import com.example.data.repository.AppThemeMode
import com.example.data.repository.ConversationRepository
import com.example.data.repository.InferenceBackendPreference
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: JarvisSettings = JarvisSettings(),
    val wakeWordState: WakeWordState = WakeWordState.DORMANT,
    val wakeWordKeyword: String = "Jarvis",
    val memoryFactsCount: Int = 0,
    val memorySearchQuery: String = "",
    val memories: List<MemoryFact> = emptyList(),
    val skills: List<AgentSkill> = emptyList(),
    val cloudUsageStats: CloudUsageStats = CloudUsageStats(),
    val executionHistory: List<AgentExecutionRecord> = emptyList(),
    val statusBannerMessage: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val wakeWordEngine: WakeWordEngine,
    private val localMemory: LocalMemory,
    private val agentCore: AgentCore? = null,
    private val skillEngine: SkillEngine? = null
) : ViewModel() {

    private val statusBanner = MutableStateFlow<String?>(null)
    private val memorySearchQuery = MutableStateFlow("")

    private val cloudUsageFlow = agentCore?.cloudUsageStats ?: flowOf(CloudUsageStats())
    private val executionHistoryFlow = agentCore?.observeExecutionHistory() ?: flowOf(emptyList())
    private val skillsFlow = skillEngine?.observeSkills() ?: flowOf(emptyList())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        wakeWordEngine.state,
        localMemory.observeFacts(),
        memorySearchQuery,
        cloudUsageFlow,
        executionHistoryFlow,
        skillsFlow,
        statusBanner
    ) { values ->
        val settings = values[0] as JarvisSettings
        val wakeState = values[1] as WakeWordState
        @Suppress("UNCHECKED_CAST")
        val allFacts = values[2] as List<MemoryFact>
        val query = values[3] as String
        val usage = values[4] as CloudUsageStats
        @Suppress("UNCHECKED_CAST")
        val executions = values[5] as List<AgentExecutionRecord>
        @Suppress("UNCHECKED_CAST")
        val skills = values[6] as List<AgentSkill>
        val banner = values[7] as String?

        val filtered = if (query.isBlank()) {
            allFacts
        } else {
            val needle = query.trim().lowercase()
            allFacts.filter {
                it.content.lowercase().contains(needle) ||
                    it.category.lowercase().contains(needle) ||
                    it.kind.label.lowercase().contains(needle)
            }
        }

        SettingsUiState(
            settings = settings,
            wakeWordState = wakeState,
            wakeWordKeyword = wakeWordEngine.keyword,
            memoryFactsCount = allFacts.size,
            memorySearchQuery = query,
            memories = filtered,
            skills = skills,
            cloudUsageStats = usage,
            executionHistory = executions,
            statusBannerMessage = banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

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

    fun setVoiceLanguageTag(tag: String) {
        viewModelScope.launch { settingsRepository.setVoiceLanguageTag(tag) }
    }

    fun setPauseMicInBackground(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPauseMicInBackground(enabled) }
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

    // Phase 6 — Hybrid Routing, Cloud AI, Web Research & Local Memory Controls
    fun setRoutingMode(mode: AgentRoutingMode) {
        viewModelScope.launch { settingsRepository.setRoutingMode(mode) }
    }

    fun setCloudUploadConsentGranted(granted: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCloudUploadConsentGranted(granted)
            statusBanner.value = if (granted) {
                "Cloud API transmission consented. Local Only mode still keeps data on-device."
            } else {
                "Cloud transmission disabled. All conversations remain strictly on-device."
            }
        }
    }

    fun setCloudProviderKind(kind: CloudProviderKind) {
        viewModelScope.launch { settingsRepository.setCloudProviderKind(kind) }
    }

    fun setCloudEndpointUrl(url: String) {
        viewModelScope.launch { settingsRepository.setCloudEndpointUrl(url) }
    }

    fun setCloudModelId(modelId: String) {
        viewModelScope.launch { settingsRepository.setCloudModelId(modelId) }
    }

    fun setCloudApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepository.setCloudApiKey(apiKey)
            statusBanner.value = if (apiKey.isBlank()) {
                "Cloud API key cleared from encrypted storage."
            } else {
                "Cloud API key encrypted with Android Keystore & saved."
            }
        }
    }

    fun setCloudTimeoutSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setCloudTimeoutSeconds(seconds) }
    }

    fun resetCloudUsageStats() {
        agentCore?.resetCloudUsageStats()
    }

    fun setWebResearchEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWebResearchEnabled(enabled) }
    }

    fun setWebSearchProvider(provider: WebSearchProviderKind) {
        viewModelScope.launch { settingsRepository.setWebSearchProvider(provider) }
    }

    fun setWebSearchCustomUrl(url: String) {
        viewModelScope.launch { settingsRepository.setWebSearchCustomUrl(url) }
    }

    fun setWebSearchApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepository.setWebSearchApiKey(apiKey)
            statusBanner.value = if (apiKey.isBlank()) {
                "Web Search API key cleared."
            } else {
                "Web Search API key encrypted & saved."
            }
        }
    }

    fun updateMemorySearchQuery(query: String) {
        memorySearchQuery.value = query
    }

    fun addMemoryItem(
        kind: MemoryKind,
        category: String,
        content: String,
        allowSensitiveConsent: Boolean = false
    ) {
        viewModelScope.launch {
            val res = localMemory.saveMemory(
                kind = kind,
                category = category,
                content = content,
                userApproved = true,
                containsSensitiveConsent = allowSensitiveConsent
            )
            statusBanner.value = if (res.isSuccess) {
                "Saved ${kind.label.lowercase()} to local Room memory."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to save memory."
            }
        }
    }

    fun editMemoryItem(
        id: String,
        category: String,
        content: String,
        allowSensitiveConsent: Boolean = false
    ) {
        viewModelScope.launch {
            val res = localMemory.updateMemory(id, category, content, allowSensitiveConsent)
            statusBanner.value = if (res.isSuccess) {
                "Memory updated locally."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update memory."
            }
        }
    }

    fun deleteMemoryItem(id: String) {
        viewModelScope.launch {
            localMemory.deleteMemory(id)
        }
    }

    fun deleteSkill(id: String) {
        viewModelScope.launch {
            skillEngine?.deleteSkill(id)
            statusBanner.value = "Skill deleted from Skills Library."
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
            localMemory: LocalMemory,
            agentCore: AgentCore? = null,
            skillEngine: SkillEngine? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    settingsRepository = settingsRepository,
                    conversationRepository = conversationRepository,
                    wakeWordEngine = wakeWordEngine,
                    localMemory = localMemory,
                    agentCore = agentCore,
                    skillEngine = skillEngine
                ) as T
            }
        }
    }
}
