package com.example.ui.screens.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.agent.AgentCore
import com.example.core.ai.AiEngine
import com.example.core.ai.AiStreamEvent
import com.example.core.ai.EngineLoadState
import com.example.core.model.ArtifactState
import com.example.core.model.ModelManager
import com.example.core.model.RuntimeFormatId
import com.example.core.tools.AndroidTools
import java.io.File
import com.example.core.voice.JarvisVoiceService
import com.example.core.voice.MicStatus
import com.example.core.voice.TtsVoiceInfo
import com.example.core.voice.VoiceConversationMode
import com.example.core.voice.VoiceEngine
import com.example.core.voice.VoiceEngineState
import com.example.core.voice.VoiceErrorKind
import com.example.core.voice.VoiceLanguageDetector
import com.example.core.voice.VoiceLanguageOption
import com.example.core.voice.VoiceLocaleCheck
import com.example.core.voice.VoiceServiceStatus
import com.example.core.voice.VoiceTurnItem
import com.example.core.wakeword.WakeWordAudioMetrics
import com.example.core.wakeword.WakeWordEngine
import com.example.core.wakeword.WakeWordEvaluationReport
import com.example.core.wakeword.WakeWordState
import com.example.data.local.db.MessageRole
import com.example.data.local.db.ModelBenchmarkDao
import com.example.data.local.db.ModelBenchmarkEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class VoiceUiState(
    val engineState: VoiceEngineState = VoiceEngineState.Idle,
    val lastTranscript: String = "",
    val isRecognitionAvailable: Boolean = false,
    val isTtsReady: Boolean = false,
    val selectedLanguage: VoiceLanguageOption = VoiceLanguageOption.AUTO,
    val detectedLanguage: VoiceLanguageOption = VoiceLanguageOption.ENGLISH,
    val availableVoices: List<TtsVoiceInfo> = emptyList(),
    val localeChecks: List<VoiceLocaleCheck> = emptyList(),
    val conversationMode: VoiceConversationMode = VoiceConversationMode.PUSH_TO_TALK,
    val isContinuousSessionActive: Boolean = false,
    val micPermissionGranted: Boolean = false,
    val isAppInForeground: Boolean = true,
    val micStatus: MicStatus = MicStatus.OFF,
    val isForegroundServiceRunning: Boolean = false,
    val serviceRestrictionNotice: String? = null,
    val wakeWordState: WakeWordState = WakeWordState.DORMANT,
    val wakeWordMetrics: WakeWordAudioMetrics = WakeWordAudioMetrics(),
    val wakeWordReport: WakeWordEvaluationReport? = null,
    val isGeneratingReply: Boolean = false,
    val streamingReply: String = "",
    val voiceTurns: List<VoiceTurnItem> = emptyList(),
    val settings: JarvisSettings = JarvisSettings()
)

class VoiceViewModel(
    private val voiceEngine: VoiceEngine,
    private val wakeWordEngine: WakeWordEngine,
    private val settingsRepository: SettingsRepository,
    private val agentCore: AgentCore,
    private val aiEngine: AiEngine,
    private val modelManager: ModelManager,
    private val conversationRepository: ConversationRepository,
    private val modelBenchmarkDao: ModelBenchmarkDao? = null,
    private val serviceStarter: (modeLabel: String, capturePcmGate: Boolean) -> Boolean = { _, _ -> false },
    private val serviceStopper: () -> Unit = {},
    private val androidTools: AndroidTools? = null
) : ViewModel() {

    private val micPermissionGranted = MutableStateFlow(false)
    private val isAppInForeground = MutableStateFlow(true)
    private val isContinuousSessionActive = MutableStateFlow(false)
    private val isGeneratingReply = MutableStateFlow(false)
    private val streamingReply = MutableStateFlow("")
    private val voiceTurns = MutableStateFlow<List<VoiceTurnItem>>(emptyList())
    private val localNotice = MutableStateFlow<String?>(null)
    private var consecutiveTimeoutCount = 0
    private var generationJob: Job? = null
    private var continuousRearmJob: Job? = null

    private data class CoreVoiceSnapshot(
        val engineState: VoiceEngineState,
        val lastTranscript: String,
        val isTtsReady: Boolean,
        val detectedLang: VoiceLanguageOption,
        val voices: List<TtsVoiceInfo>
    )

    private data class ControlSnapshot(
        val localeChecks: List<VoiceLocaleCheck>,
        val settings: JarvisSettings,
        val micGranted: Boolean,
        val inForeground: Boolean,
        val continuousActive: Boolean
    )

    private data class RuntimeSnapshot(
        val fgsRunning: Boolean,
        val fgsNotice: String?,
        val wakeWordState: WakeWordState,
        val wakeWordMetrics: WakeWordAudioMetrics,
        val isGenerating: Boolean
    )

    private val coreFlow = combine(
        voiceEngine.state,
        voiceEngine.lastTranscript,
        voiceEngine.isTtsReady,
        voiceEngine.detectedLanguage,
        voiceEngine.availableVoices
    ) { state, transcript, ttsReady, detected, voices ->
        CoreVoiceSnapshot(state, transcript, ttsReady, detected, voices)
    }

    private val controlFlow = combine(
        voiceEngine.localeChecks,
        settingsRepository.settingsFlow,
        micPermissionGranted,
        isAppInForeground,
        isContinuousSessionActive
    ) { checks, settings, micGranted, foreground, continuous ->
        ControlSnapshot(checks, settings, micGranted, foreground, continuous)
    }

    private val runtimeFlow = combine(
        VoiceServiceStatus.isRunning,
        VoiceServiceStatus.lastRestrictionNotice,
        wakeWordEngine.state,
        wakeWordEngine.audioMetrics,
        isGeneratingReply
    ) { fgsRunning, fgsNotice, wakeState, wakeMetrics, generating ->
        RuntimeSnapshot(fgsRunning, fgsNotice, wakeState, wakeMetrics, generating)
    }

    val uiState: StateFlow<VoiceUiState> = combine(
        coreFlow,
        controlFlow,
        runtimeFlow,
        streamingReply,
        voiceTurns,
        localNotice
    ) { values ->
        val core = values[0] as CoreVoiceSnapshot
        val control = values[1] as ControlSnapshot
        val runtime = values[2] as RuntimeSnapshot
        val streaming = values[3] as String
        @Suppress("UNCHECKED_CAST")
        val turns = values[4] as List<VoiceTurnItem>
        val notice = values[5] as String?

        val selectedLanguage = VoiceLanguageOption.fromTag(control.settings.voiceLanguageTag)
        val convMode = if (control.settings.voiceContinuousMode) {
            VoiceConversationMode.CONTINUOUS
        } else {
            VoiceConversationMode.PUSH_TO_TALK
        }
        val sttAvailable = voiceEngine.isSpeechRecognitionAvailable()
        val micStatus = resolveMicStatus(
            micGranted = control.micGranted,
            inForeground = control.inForeground,
            engineState = core.engineState,
            fgsRunning = runtime.fgsRunning,
            wakeState = runtime.wakeWordState,
            sttAvailable = sttAvailable
        )

        VoiceUiState(
            engineState = core.engineState,
            lastTranscript = core.lastTranscript,
            isRecognitionAvailable = sttAvailable,
            isTtsReady = core.isTtsReady,
            selectedLanguage = selectedLanguage,
            detectedLanguage = core.detectedLang,
            availableVoices = core.voices,
            localeChecks = control.localeChecks,
            conversationMode = convMode,
            isContinuousSessionActive = control.continuousActive,
            micPermissionGranted = control.micGranted,
            isAppInForeground = control.inForeground,
            micStatus = micStatus,
            isForegroundServiceRunning = runtime.fgsRunning,
            serviceRestrictionNotice = notice ?: runtime.fgsNotice,
            wakeWordState = runtime.wakeWordState,
            wakeWordMetrics = runtime.wakeWordMetrics,
            wakeWordReport = wakeWordEngine.evaluationReport,
            isGeneratingReply = runtime.isGenerating,
            streamingReply = streaming,
            voiceTurns = turns,
            settings = control.settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VoiceUiState(
            isRecognitionAvailable = voiceEngine.isSpeechRecognitionAvailable(),
            wakeWordReport = wakeWordEngine.evaluationReport
        )
    )

    init {
        voiceEngine.initializeTtsIfNeeded()

        viewModelScope.launch {
            voiceEngine.transcriptEvents.collect { event ->
                consecutiveTimeoutCount = 0
                localNotice.value = null
                submitPromptInternal(
                    rawText = event.text,
                    detectedLanguage = event.detectedLanguage,
                    origin = "Voice"
                )
            }
        }

        viewModelScope.launch {
            voiceEngine.speechCompletionEvents.collect { event ->
                val stateSnapshot = uiState.value
                if (!event.interrupted &&
                    stateSnapshot.conversationMode == VoiceConversationMode.CONTINUOUS &&
                    stateSnapshot.isContinuousSessionActive &&
                    stateSnapshot.micPermissionGranted &&
                    stateSnapshot.isAppInForeground &&
                    !stateSnapshot.isGeneratingReply
                ) {
                    scheduleContinuousRearm()
                }
            }
        }

        viewModelScope.launch {
            voiceEngine.state.collect { engineState ->
                val stateSnapshot = uiState.value
                if (engineState is VoiceEngineState.Error &&
                    stateSnapshot.conversationMode == VoiceConversationMode.CONTINUOUS &&
                    stateSnapshot.isContinuousSessionActive
                ) {
                    val isRecoverableSilence = engineState.kind == VoiceErrorKind.TRANSIENT_NO_MATCH ||
                        engineState.kind == VoiceErrorKind.TIMEOUT
                    if (isRecoverableSilence && stateSnapshot.micPermissionGranted && stateSnapshot.isAppInForeground) {
                        consecutiveTimeoutCount++
                        if (consecutiveTimeoutCount <= 2) {
                            scheduleContinuousRearm(delayMs = 500L)
                        } else {
                            stopContinuousSession(
                                reason = "Continuous mode paused after repeated silence to conserve battery."
                            )
                        }
                    } else if (engineState.kind == VoiceErrorKind.PERMISSION_DENIED ||
                        engineState.kind == VoiceErrorKind.FATAL ||
                        engineState.kind == VoiceErrorKind.UNAVAILABLE
                    ) {
                        stopContinuousSession(reason = engineState.message)
                    }
                }
            }
        }

        viewModelScope.launch {
            VoiceServiceStatus.userStopRequests.collect {
                interruptAllVoice()
                settingsRepository.setWakeWordStandbyEnabled(false)
                syncWakeWordBoundary(
                    enabled = false,
                    hasMic = micPermissionGranted.value,
                    inForeground = isAppInForeground.value
                )
            }
        }

        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { s ->
                wakeWordEngine.updateAccessKey(s.porcupineAccessKey)
                syncWakeWordBoundary(
                    enabled = s.wakeWordStandbyEnabled,
                    hasMic = micPermissionGranted.value,
                    inForeground = isAppInForeground.value,
                    keyPresent = s.porcupineAccessKey.isNotBlank()
                )
            }
        }

        viewModelScope.launch {
            wakeWordEngine.triggerEvents.collect {
                val snap = uiState.value
                if (snap.micPermissionGranted &&
                    snap.isAppInForeground &&
                    !snap.isGeneratingReply &&
                    snap.engineState !is VoiceEngineState.Listening
                ) {
                    startListeningInternal()
                }
            }
        }
    }

    fun onMicPermissionChanged(granted: Boolean) {
        val prev = micPermissionGranted.value
        micPermissionGranted.value = granted
        if (!granted) {
            isContinuousSessionActive.value = false
            continuousRearmJob?.cancel()
            voiceEngine.cancelListening()
            serviceStopper()
            if (prev || uiState.value.settings.wakeWordStandbyEnabled) {
                localNotice.value = "Microphone permission denied. Voice capture & standby paused; text fallback remains active."
            }
        } else {
            localNotice.value = null
        }
        syncWakeWordBoundary(
            enabled = uiState.value.settings.wakeWordStandbyEnabled,
            hasMic = granted,
            inForeground = isAppInForeground.value
        )
    }

    fun onAppLifecycleChanged(inForeground: Boolean) {
        isAppInForeground.value = inForeground
        val settings = uiState.value.settings
        if (!inForeground) {
            continuousRearmJob?.cancel()
            voiceEngine.cancelListening()
            if (isContinuousSessionActive.value) {
                isContinuousSessionActive.value = false
                localNotice.value = "Continuous voice paused while app moved to background (Android mic restriction)."
            }
            if (settings.pauseMicInBackground || !settings.wakeWordStandbyEnabled) {
                serviceStopper()
            }
        } else {
            // Re-enable standby audio gate only when returning to foreground if toggle is on
            if (settings.wakeWordStandbyEnabled && micPermissionGranted.value) {
                startWakeWordServiceGate()
            }
        }
        syncWakeWordBoundary(
            enabled = settings.wakeWordStandbyEnabled,
            hasMic = micPermissionGranted.value,
            inForeground = inForeground
        )
    }

    fun toggleListening() {
        val currentState = voiceEngine.state.value
        val stateSnapshot = uiState.value
        if (currentState is VoiceEngineState.Speaking) {
            voiceEngine.stopSpeaking()
            startListeningInternal()
            return
        }
        if (stateSnapshot.conversationMode == VoiceConversationMode.CONTINUOUS) {
            if (stateSnapshot.isContinuousSessionActive || currentState is VoiceEngineState.Listening) {
                stopContinuousSession()
            } else {
                startContinuousSession()
            }
        } else {
            when (currentState) {
                is VoiceEngineState.Listening -> voiceEngine.stopListening()
                else -> startListeningInternal()
            }
        }
    }

    fun onPushToTalkPressed() {
        if (voiceEngine.state.value is VoiceEngineState.Speaking) {
            voiceEngine.stopSpeaking()
        }
        if (isGeneratingReply.value) {
            cancelAiReply()
        }
        startListeningInternal()
    }

    fun onPushToTalkReleased() {
        if (voiceEngine.state.value is VoiceEngineState.Listening) {
            voiceEngine.stopListening()
        }
    }

    fun startContinuousSession() {
        if (!micPermissionGranted.value) {
            localNotice.value = "Grant microphone permission before enabling Continuous Conversation mode."
            return
        }
        if (!isAppInForeground.value) {
            localNotice.value = "Cannot start continuous microphone capture while backgrounded."
            return
        }
        consecutiveTimeoutCount = 0
        isContinuousSessionActive.value = true
        localNotice.value = null
        serviceStarter("Continuous voice conversation active", false)
        startListeningInternal()
    }

    fun stopContinuousSession(reason: String? = null) {
        continuousRearmJob?.cancel()
        isContinuousSessionActive.value = false
        voiceEngine.cancelListening()
        if (!uiState.value.settings.wakeWordStandbyEnabled) {
            serviceStopper()
        }
        if (reason != null) {
            localNotice.value = reason
        }
    }

    private fun startListeningInternal() {
        if (!micPermissionGranted.value) {
            localNotice.value = "Microphone permission is required. Use text fallback below or grant RECORD_AUDIO."
            return
        }
        if (!isAppInForeground.value) {
            localNotice.value = "SpeechRecognizer cannot start from the background."
            return
        }
        if (voiceEngine.state.value is VoiceEngineState.Speaking) {
            voiceEngine.stopSpeaking()
        }
        voiceEngine.resetError()
        voiceEngine.startListening(uiState.value.selectedLanguage)
    }

    private fun scheduleContinuousRearm(delayMs: Long = 350L) {
        continuousRearmJob?.cancel()
        continuousRearmJob = viewModelScope.launch {
            delay(delayMs)
            val snap = uiState.value
            if (snap.conversationMode == VoiceConversationMode.CONTINUOUS &&
                snap.isContinuousSessionActive &&
                snap.micPermissionGranted &&
                snap.isAppInForeground &&
                !snap.isGeneratingReply &&
                snap.engineState !is VoiceEngineState.Speaking
            ) {
                voiceEngine.resetError()
                voiceEngine.startListening(snap.selectedLanguage)
            }
        }
    }

    fun submitTextFallback(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        val detected = VoiceLanguageDetector.detectLanguage(text, uiState.value.selectedLanguage)
        localNotice.value = null
        submitPromptInternal(
            rawText = text,
            detectedLanguage = detected,
            origin = "Fallback"
        )
    }

    private fun submitPromptInternal(
        rawText: String,
        detectedLanguage: VoiceLanguageOption,
        origin: String
    ) {
        val prompt = rawText.trim()
        if (prompt.isEmpty() || isGeneratingReply.value) return

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            isGeneratingReply.value = true
            streamingReply.value = ""
            appendTurn(
                VoiceTurnItem(
                    id = UUID.randomUUID().toString(),
                    isUser = true,
                    text = prompt,
                    detectedLanguage = detectedLanguage,
                    telemetryNote = "$origin • ${detectedLanguage.shortLabel}"
                )
            )

            try {
                val convId = conversationRepository.ensureDefaultConversation()
                val settings = uiState.value.settings
                val activeModel = modelManager.getActiveModelOnce()
                val historyBeforeSend = conversationRepository.getMessagesOnce(convId)

                conversationRepository.appendMessage(
                    conversationId = convId,
                    role = MessageRole.USER,
                    content = prompt,
                    modelLabel = "Operator ($origin)"
                )

                val readyState = aiEngine.loadState.value as? EngineLoadState.Ready
                if (readyState == null || (activeModel != null && readyState.modelId != activeModel.id)) {
                    if (activeModel != null && activeModel.runtimeFormat != RuntimeFormatId.LITERT_LM) {
                        val notice = "${activeModel.displayName} uses ${activeModel.runtimeFormat.label}, which is not yet active for inference. Switch to a LiteRT-LM model in Models."
                        handleSystemVoiceNotice(convId, notice, detectedLanguage)
                        return@launch
                    } else if (activeModel != null && activeModel.artifactState == ArtifactState.READY) {
                        val loadRes = aiEngine.loadModel(activeModel, settings)
                        if (loadRes.isFailure) {
                            val err = loadRes.exceptionOrNull()?.localizedMessage ?: "Failed to load local LiteRT-LM model."
                            handleSystemVoiceNotice(convId, err, detectedLanguage)
                            return@launch
                        }
                    } else {
                        val notice = "Local LiteRT-LM model is not installed yet. Download or import a .litertlm model in the Models tab to enable spoken AI replies."
                        handleSystemVoiceNotice(convId, notice, detectedLanguage)
                        return@launch
                    }
                }

                val modelLabel = activeModel?.displayName ?: "LiteRT-LM"
                agentCore.streamPrompt(
                    conversationId = convId,
                    prompt = prompt,
                    history = historyBeforeSend,
                    settings = settings
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Started -> Unit
                        is AiStreamEvent.Delta -> {
                            streamingReply.value = event.accumulatedText
                        }
                        is AiStreamEvent.Completed -> {
                            streamingReply.value = ""
                            val reply = event.fullText.trim()
                            if (reply.isNotEmpty()) {
                                val replyLang = VoiceLanguageDetector.detectLanguage(
                                    text = reply,
                                    preferred = uiState.value.selectedLanguage,
                                    systemLocale = detectedLanguage.toLocale()
                                )
                                conversationRepository.appendMessage(
                                    conversationId = convId,
                                    role = MessageRole.ASSISTANT,
                                    content = reply,
                                    modelLabel = modelLabel,
                                    ttftMs = event.benchmark.ttftMs,
                                    tokensPerSecond = event.benchmark.tokensPerSecond,
                                    outputTokens = event.benchmark.outputTokens,
                                    backendUsed = event.benchmark.backendUsed
                                )
                                val telemetry = androidTools?.let { tools ->
                                    runCatching { tools.readDeviceTelemetry() }.getOrNull()
                                }
                                val resolvedModelSize = event.benchmark.modelSizeBytes.takeIf { it > 0L }
                                    ?: activeModel?.localFilePath?.let { path ->
                                        runCatching { File(path).length() }.getOrNull()?.takeIf { it > 0L }
                                    }
                                    ?: activeModel?.expectedSizeBytes
                                    ?: 0L
                                val resolvedInitTime = event.benchmark.initTimeMs.takeIf { it > 0L }
                                    ?: (aiEngine.loadState.value as? EngineLoadState.Ready)?.loadTimeMs
                                    ?: 0L
                                val resolvedThermal = telemetry?.thermalStatusLabel?.takeIf { it != "Unavailable" }
                                    ?: event.benchmark.thermalStatus
                                modelBenchmarkDao?.insertBenchmark(
                                    ModelBenchmarkEntity(
                                        id = UUID.randomUUID().toString(),
                                        modelId = activeModel?.id ?: "unknown",
                                        modelName = modelLabel,
                                        backendUsed = event.benchmark.backendUsed,
                                        ttftMs = event.benchmark.ttftMs,
                                        tokensPerSecond = event.benchmark.tokensPerSecond,
                                        outputTokens = event.benchmark.outputTokens,
                                        prefillTokens = event.benchmark.prefillTokens,
                                        nativeHeapMb = event.benchmark.nativeHeapMb,
                                        timestamp = System.currentTimeMillis(),
                                        initTimeMs = resolvedInitTime,
                                        peakMemoryMb = maxOf(event.benchmark.peakMemoryMb, event.benchmark.nativeHeapMb),
                                        modelSizeBytes = resolvedModelSize,
                                        thermalStatus = resolvedThermal,
                                        batteryPercent = telemetry?.batteryPercent ?: event.benchmark.batteryPercent,
                                        batteryTempCelsius = telemetry?.batteryTempCelsius ?: event.benchmark.batteryTempCelsius
                                    )
                                )
                                appendTurn(
                                    VoiceTurnItem(
                                        id = UUID.randomUUID().toString(),
                                        isUser = false,
                                        text = reply,
                                        detectedLanguage = replyLang,
                                        telemetryNote = String.format(
                                            Locale.US,
                                            "%s • %d ms TTFT • %.1f tok/s",
                                            event.benchmark.backendUsed,
                                            event.benchmark.ttftMs,
                                            event.benchmark.tokensPerSecond
                                        )
                                    )
                                )
                                if (uiState.value.settings.ttsAutoSpeak) {
                                    voiceEngine.speak(
                                        text = reply,
                                        speechRate = uiState.value.settings.ttsSpeechRate,
                                        languageOption = replyLang,
                                        preferredVoiceName = uiState.value.settings.selectedTtsVoiceName
                                    )
                                }
                            }
                        }
                        is AiStreamEvent.Cancelled -> {
                            streamingReply.value = ""
                            val partial = event.partialText.trim()
                            if (partial.isNotEmpty()) {
                                appendTurn(
                                    VoiceTurnItem(
                                        id = UUID.randomUUID().toString(),
                                        isUser = false,
                                        text = partial,
                                        detectedLanguage = detectedLanguage,
                                        telemetryNote = "Interrupted"
                                    )
                                )
                            }
                        }
                        is AiStreamEvent.Error -> {
                            streamingReply.value = ""
                            handleSystemVoiceNotice(convId, event.message, detectedLanguage)
                        }
                    }
                }
            } finally {
                streamingReply.value = ""
                isGeneratingReply.value = false
            }
        }
    }

    private suspend fun handleSystemVoiceNotice(
        convId: String,
        notice: String,
        language: VoiceLanguageOption
    ) {
        conversationRepository.appendMessage(
            conversationId = convId,
            role = MessageRole.SYSTEM_NOTICE,
            content = notice,
            modelLabel = "Voice // Engine Notice"
        )
        appendTurn(
            VoiceTurnItem(
                id = UUID.randomUUID().toString(),
                isUser = false,
                text = notice,
                detectedLanguage = language,
                telemetryNote = "System Notice"
            )
        )
        if (uiState.value.settings.ttsAutoSpeak) {
            voiceEngine.speak(
                text = notice,
                speechRate = uiState.value.settings.ttsSpeechRate,
                languageOption = language,
                preferredVoiceName = uiState.value.settings.selectedTtsVoiceName
            )
        }
    }

    private fun appendTurn(item: VoiceTurnItem) {
        voiceTurns.value = (voiceTurns.value + item).takeLast(24)
    }

    fun speakStatusReadback() {
        val snap = uiState.value
        val summary = when (snap.selectedLanguage) {
            VoiceLanguageOption.ARABIC,
            VoiceLanguageOption.ARABIC_ALGERIAN ->
                "نظام جارفيس الصوتي جاهز. التعرف على الكلام وتوليد الصوت متاحان على الجهاز."
            VoiceLanguageOption.FRENCH ->
                "Console vocale JARVIS active. La reconnaissance vocale et la synthèse Android sont prêtes."
            else ->
                "JARVIS voice console active. Speech recognition and text to speech synthesis are online."
        }
        voiceEngine.speak(
            text = summary,
            speechRate = snap.settings.ttsSpeechRate,
            languageOption = snap.selectedLanguage,
            preferredVoiceName = snap.settings.selectedTtsVoiceName
        )
    }

    fun speakTranscript(text: String) {
        val snap = uiState.value
        val lang = VoiceLanguageDetector.detectLanguage(text, snap.selectedLanguage)
        voiceEngine.speak(
            text = text,
            speechRate = snap.settings.ttsSpeechRate,
            languageOption = lang,
            preferredVoiceName = snap.settings.selectedTtsVoiceName
        )
    }

    fun stopSpeaking() {
        voiceEngine.stopSpeaking()
    }

    fun interruptAllVoice() {
        continuousRearmJob?.cancel()
        isContinuousSessionActive.value = false
        voiceEngine.cancelListening()
        voiceEngine.stopSpeaking()
        if (isGeneratingReply.value) {
            cancelAiReply()
        }
        if (!uiState.value.settings.wakeWordStandbyEnabled) {
            serviceStopper()
        }
    }

    private fun cancelAiReply() {
        viewModelScope.launch {
            aiEngine.cancelGeneration()
        }
    }

    fun setLanguageOption(option: VoiceLanguageOption) {
        viewModelScope.launch {
            settingsRepository.setVoiceLanguageTag(option.tag)
        }
    }

    fun selectTtsVoice(voiceName: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedTtsVoiceName(voiceName)
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch {
            settingsRepository.setTtsSpeechRate(rate)
        }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTtsAutoSpeak(enabled)
        }
    }

    fun setConversationMode(mode: VoiceConversationMode) {
        viewModelScope.launch {
            val continuous = mode == VoiceConversationMode.CONTINUOUS
            settingsRepository.setVoiceContinuousMode(continuous)
            if (!continuous) {
                stopContinuousSession()
            }
        }
    }

    fun setPauseMicInBackground(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPauseMicInBackground(enabled)
        }
    }

    fun setWakeWordStandbyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWakeWordStandbyEnabled(enabled)
            if (enabled) {
                if (micPermissionGranted.value && isAppInForeground.value) {
                    startWakeWordServiceGate()
                }
            } else {
                if (!isContinuousSessionActive.value) {
                    serviceStopper()
                }
            }
            syncWakeWordBoundary(
                enabled = enabled,
                hasMic = micPermissionGranted.value,
                inForeground = isAppInForeground.value
            )
        }
    }

    fun setPorcupineAccessKey(accessKey: String) {
        viewModelScope.launch {
            val trimmed = accessKey.trim()
            wakeWordEngine.updateAccessKey(trimmed)
            settingsRepository.setPorcupineAccessKey(trimmed)
            syncWakeWordBoundary(
                enabled = uiState.value.settings.wakeWordStandbyEnabled,
                hasMic = micPermissionGranted.value,
                inForeground = isAppInForeground.value,
                keyPresent = trimmed.isNotBlank()
            )
        }
    }

    private fun startWakeWordServiceGate() {
        serviceStarter("Wake-word audio gate standby (\"${wakeWordEngine.keyword}\")", true)
    }

    private fun syncWakeWordBoundary(
        enabled: Boolean,
        hasMic: Boolean,
        inForeground: Boolean,
        keyPresent: Boolean = uiState.value.settings.porcupineAccessKey.isNotBlank()
    ) {
        wakeWordEngine.syncStandbyPreference(
            enabled = enabled,
            hasMicPermission = hasMic,
            isAppInForeground = inForeground,
            porcupineAccessKeyPresent = keyPresent
        )
    }

    fun dismissError() {
        voiceEngine.resetError()
        localNotice.value = null
    }

    private fun resolveMicStatus(
        micGranted: Boolean,
        inForeground: Boolean,
        engineState: VoiceEngineState,
        fgsRunning: Boolean,
        wakeState: WakeWordState,
        sttAvailable: Boolean
    ): MicStatus = when {
        !micGranted -> MicStatus.PERMISSION_DENIED
        !inForeground && wakeState == WakeWordState.BACKGROUND_RESTRICTED -> MicStatus.PAUSED_BACKGROUND
        engineState is VoiceEngineState.Listening || engineState is VoiceEngineState.Processing -> MicStatus.ACTIVE_STT
        fgsRunning || wakeState == WakeWordState.STANDBY_AUDIO_GATE -> MicStatus.STANDBY_WAKE_WORD
        !sttAvailable -> MicStatus.UNAVAILABLE
        else -> MicStatus.OFF
    }

    override fun onCleared() {
        continuousRearmJob?.cancel()
        voiceEngine.cancelListening()
        voiceEngine.stopSpeaking()
        serviceStopper()
        super.onCleared()
    }

    companion object {
        fun factory(
            context: Context,
            voiceEngine: VoiceEngine,
            wakeWordEngine: WakeWordEngine,
            settingsRepository: SettingsRepository,
            agentCore: AgentCore,
            aiEngine: AiEngine,
            modelManager: ModelManager,
            conversationRepository: ConversationRepository,
            modelBenchmarkDao: ModelBenchmarkDao,
            androidTools: AndroidTools? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            private val appContext = context.applicationContext

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VoiceViewModel(
                    voiceEngine = voiceEngine,
                    wakeWordEngine = wakeWordEngine,
                    settingsRepository = settingsRepository,
                    agentCore = agentCore,
                    aiEngine = aiEngine,
                    modelManager = modelManager,
                    conversationRepository = conversationRepository,
                    modelBenchmarkDao = modelBenchmarkDao,
                    serviceStarter = { label, capturePcm ->
                        JarvisVoiceService.startForegroundMic(appContext, label, capturePcm)
                    },
                    serviceStopper = {
                        JarvisVoiceService.stop(appContext)
                    },
                    androidTools = androidTools
                ) as T
            }
        }
    }
}
