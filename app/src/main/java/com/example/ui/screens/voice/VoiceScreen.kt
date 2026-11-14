package com.example.ui.screens.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.voice.VoiceConversationMode
import com.example.core.voice.VoiceEngineState
import com.example.core.voice.VoiceLanguageOption
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.JarvisOrb
import com.example.ui.components.OrbMode

@Composable
fun VoiceScreen(
    uiState: VoiceUiState,
    onMicPermissionChanged: (Boolean) -> Unit,
    onToggleListening: () -> Unit,
    onPushToTalkPressed: () -> Unit,
    onPushToTalkReleased: () -> Unit,
    onInterruptAllVoice: () -> Unit,
    onSpeakStatus: () -> Unit,
    onSpeakTranscript: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onDismissError: () -> Unit,
    onSubmitTextFallback: (String) -> Unit,
    onSelectConversationMode: (VoiceConversationMode) -> Unit,
    onSelectLanguage: (VoiceLanguageOption) -> Unit,
    onSelectTtsVoice: (String) -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onSetAutoSpeak: (Boolean) -> Unit,
    onToggleWakeWordStandby: (Boolean) -> Unit,
    onTogglePauseInBackground: (Boolean) -> Unit,
    onLogTranscriptToChat: (String) -> Unit,
    onSavePorcupineAccessKey: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var fallbackInput by remember { mutableStateOf("") }
    var showVoicePicker by remember { mutableStateOf(false) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(hasMicPermission) {
        onMicPermissionChanged(hasMicPermission)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensurePostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var pendingActionAfterMic by remember { mutableStateOf<(() -> Unit)?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        onMicPermissionChanged(granted)
        val pending = pendingActionAfterMic
        pendingActionAfterMic = null
        if (granted && pending != null) {
            pending()
        }
    }

    fun requestMicThen(action: () -> Unit) {
        if (hasMicPermission) {
            action()
        } else {
            pendingActionAfterMic = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val engineState = uiState.engineState
    val orbMode = when {
        uiState.isGeneratingReply -> OrbMode.PROCESSING
        engineState is VoiceEngineState.Listening -> OrbMode.LISTENING
        engineState is VoiceEngineState.Processing -> OrbMode.PROCESSING
        engineState is VoiceEngineState.Speaking -> OrbMode.SPEAKING
        engineState is VoiceEngineState.Error || engineState is VoiceEngineState.Unavailable -> OrbMode.WARNING
        else -> OrbMode.IDLE
    }
    val rmsDb = (engineState as? VoiceEngineState.Listening)?.rmsDb ?: 0f

    val statusText = when {
        uiState.isGeneratingReply -> "Local AI generating spoken response…"
        engineState is VoiceEngineState.Idle -> if (hasMicPermission) {
            if (uiState.conversationMode == VoiceConversationMode.CONTINUOUS) {
                if (uiState.isContinuousSessionActive) "Continuous mode armed — listening loop active"
                else "Tap orb or Start Continuous Conversation"
            } else {
                stringResource(R.string.voice_tap_to_listen)
            }
        } else {
            stringResource(R.string.voice_permission_needed)
        }
        engineState is VoiceEngineState.Listening -> stringResource(R.string.voice_listening)
        engineState is VoiceEngineState.Processing -> "Processing speech buffer…"
        engineState is VoiceEngineState.Speaking -> "Speaking via Android TextToSpeech…"
        engineState is VoiceEngineState.Unavailable -> engineState.reason
        engineState is VoiceEngineState.Error -> engineState.message
        else -> ""
    }

    HudBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.voice_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                HudBadge(
                    text = if (uiState.isRecognitionAvailable) "ANDROID STT READY" else "STT UNAVAILABLE",
                    color = if (uiState.isRecognitionAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
            }

            PersistentMicStatusBanner(
                micStatus = uiState.micStatus,
                isFgsRunning = uiState.isForegroundServiceRunning,
                isContinuousActive = uiState.isContinuousSessionActive,
                detectedLanguage = uiState.detectedLanguage,
                restrictionNotice = uiState.serviceRestrictionNotice,
                onDisableListening = onInterruptAllVoice,
                onDismissNotice = onDismissError
            )

            JarvisOrb(
                mode = orbMode,
                size = 216.dp,
                audioLevel = rmsDb,
                animationEnabled = uiState.settings.orbAnimationEnabled,
                glowIntensity = uiState.settings.orbGlowIntensity,
                statusDescription = stringResource(R.string.cd_voice_orb),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        onDismissError()
                        if (engineState is VoiceEngineState.Speaking) {
                            onStopSpeaking()
                        } else {
                            requestMicThen {
                                if (uiState.conversationMode == VoiceConversationMode.CONTINUOUS) {
                                    ensurePostNotificationsIfNeeded()
                                }
                                onToggleListening()
                            }
                        }
                    }
                    .testTag("voice_orb_button")
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (engineState is VoiceEngineState.Error || engineState is VoiceEngineState.Unavailable) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 520.dp)
            )

            if (!hasMicPermission) {
                Button(
                    onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("voice_grant_permission_button")
                ) {
                    Icon(imageVector = Icons.Filled.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.voice_grant_permission))
                }
            }

            // Primary voice action row: Push-to-Talk Hold / Continuous Toggle + Stop Speaking
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onDismissError()
                        requestMicThen {
                            if (uiState.conversationMode == VoiceConversationMode.CONTINUOUS) {
                                ensurePostNotificationsIfNeeded()
                            }
                            onToggleListening()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("voice_listen_button")
                ) {
                    Icon(
                        imageVector = if (engineState is VoiceEngineState.Listening || uiState.isContinuousSessionActive) {
                            Icons.Filled.Stop
                        } else {
                            Icons.Filled.Mic
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when {
                            uiState.conversationMode == VoiceConversationMode.CONTINUOUS && uiState.isContinuousSessionActive -> "Stop Loop"
                            uiState.conversationMode == VoiceConversationMode.CONTINUOUS -> "Start Loop"
                            engineState is VoiceEngineState.Listening -> "Stop"
                            else -> "Listen"
                        }
                    )
                }

                if (engineState is VoiceEngineState.Speaking || uiState.isGeneratingReply) {
                    Button(
                        onClick = onInterruptAllVoice,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("voice_stop_speaking_button")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.voice_stop_tts))
                    }
                } else {
                    FilledTonalButton(
                        onClick = onSpeakStatus,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("voice_tts_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.voice_speak_status))
                    }
                }
            }

            // Dedicated Press-and-Hold Push-to-Talk surface when in PTT mode
            if (uiState.conversationMode == VoiceConversationMode.PUSH_TO_TALK) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (engineState is VoiceEngineState.Listening) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .minimumInteractiveComponentSize()
                        .pointerInput(hasMicPermission) {
                            detectTapGestures(
                                onPress = {
                                    if (hasMicPermission) {
                                        onPushToTalkPressed()
                                        tryAwaitRelease()
                                        onPushToTalkReleased()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            )
                        }
                        .testTag("voice_ptt_hold_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (engineState is VoiceEngineState.Listening) {
                                    "Release Push-to-Talk to Send"
                                } else {
                                    "Hold for Push-to-Talk"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            VoiceTranscriptAndConversationCard(
                engineState = engineState,
                lastTranscript = uiState.lastTranscript,
                isGeneratingReply = uiState.isGeneratingReply,
                streamingReply = uiState.streamingReply,
                voiceTurns = uiState.voiceTurns,
                onSpeakTurn = onSpeakTranscript
            )

            if (uiState.lastTranscript.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSpeakTranscript(uiState.lastTranscript) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Text("Read Transcript")
                    }
                    Button(
                        onClick = { onLogTranscriptToChat(uiState.lastTranscript) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("voice_log_to_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.voice_send_to_chat))
                    }
                }
            }

            VoiceTextFallbackBar(
                inputText = fallbackInput,
                isGenerating = uiState.isGeneratingReply,
                onInputChange = { fallbackInput = it },
                onSubmit = {
                    val text = fallbackInput.trim()
                    if (text.isNotEmpty()) {
                        fallbackInput = ""
                        onSubmitTextFallback(text)
                    }
                }
            )

            VoiceConversationControlsCard(
                conversationMode = uiState.conversationMode,
                selectedLanguage = uiState.selectedLanguage,
                localeChecks = uiState.localeChecks,
                ttsAutoSpeak = uiState.settings.ttsAutoSpeak,
                ttsSpeechRate = uiState.settings.ttsSpeechRate,
                selectedVoiceName = uiState.settings.selectedTtsVoiceName,
                availableVoicesCount = uiState.availableVoices.size,
                onSelectMode = { mode ->
                    if (mode == VoiceConversationMode.CONTINUOUS) {
                        ensurePostNotificationsIfNeeded()
                    }
                    onSelectConversationMode(mode)
                },
                onSelectLanguage = onSelectLanguage,
                onSetAutoSpeak = onSetAutoSpeak,
                onSetSpeechRate = onSetSpeechRate,
                onOpenVoicePicker = { showVoicePicker = true }
            )

            WakeWordEvaluationCard(
                wakeWordEnabled = uiState.settings.wakeWordStandbyEnabled,
                wakeWordState = uiState.wakeWordState,
                metrics = uiState.wakeWordMetrics,
                report = uiState.wakeWordReport,
                pauseMicInBackground = uiState.settings.pauseMicInBackground,
                porcupineAccessKey = uiState.settings.porcupineAccessKey,
                onToggleStandby = { enabled ->
                    if (enabled) {
                        ensurePostNotificationsIfNeeded()
                        requestMicThen { onToggleWakeWordStandby(true) }
                    } else {
                        onToggleWakeWordStandby(false)
                    }
                },
                onTogglePauseInBackground = onTogglePauseInBackground,
                onSavePorcupineAccessKey = onSavePorcupineAccessKey
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showVoicePicker) {
        TtsVoicePickerDialog(
            voices = uiState.availableVoices,
            selectedVoiceName = uiState.settings.selectedTtsVoiceName,
            onSelectVoice = onSelectTtsVoice,
            onDismiss = { showVoicePicker = false }
        )
    }
}
