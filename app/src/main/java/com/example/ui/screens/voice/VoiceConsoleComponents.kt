package com.example.ui.screens.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.voice.MicStatus
import com.example.core.voice.TtsVoiceInfo
import com.example.core.voice.VoiceConversationMode
import com.example.core.voice.VoiceEngineState
import com.example.core.voice.VoiceLanguageOption
import com.example.core.voice.VoiceLocaleCheck
import com.example.core.voice.VoiceTurnItem
import com.example.core.wakeword.WakeWordAudioMetrics
import com.example.core.wakeword.WakeWordEvaluationReport
import com.example.core.wakeword.WakeWordState
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.components.HudPanel
import com.example.ui.components.TelemetryProgressBar
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisThemeTokens
import java.util.Locale

@Composable
fun PersistentMicStatusBanner(
    micStatus: MicStatus,
    isFgsRunning: Boolean,
    isContinuousActive: Boolean,
    detectedLanguage: VoiceLanguageOption,
    restrictionNotice: String?,
    onDisableListening: () -> Unit,
    onDismissNotice: () -> Unit
) {
    val palette = JarvisThemeTokens.palette
    val stateColor = when (micStatus) {
        MicStatus.ACTIVE_STT, MicStatus.STANDBY_WAKE_WORD -> palette.peachAccent
        MicStatus.PERMISSION_DENIED, MicStatus.UNAVAILABLE -> palette.errorAccent
        MicStatus.PAUSED_BACKGROUND, MicStatus.OFF -> palette.textSecondary
    }

    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .testTag("mic_status_indicator")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (micStatus == MicStatus.OFF || micStatus == MicStatus.PERMISSION_DENIED) {
                        Icons.Filled.MicOff
                    } else {
                        Icons.Filled.Mic
                    },
                    contentDescription = "Microphone status",
                    tint = stateColor,
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = micStatus.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = stateColor
                    )
                    Text(
                        text = buildString {
                            append("Language: ${detectedLanguage.label}")
                            if (isFgsRunning) append(" · Background service active")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (micStatus == MicStatus.ACTIVE_STT || isFgsRunning || isContinuousActive) {
                OutlinedButton(
                    onClick = onDisableListening,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mic_disable_button")
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Disable")
                }
            } else {
                HudBadge(
                    text = if (isFgsRunning) "Service on" else "Idle",
                    color = if (isFgsRunning) palette.peachAccent else palette.textSecondary
                )
            }
        }

        if (!restrictionNotice.isNullOrBlank()) {
            EditorialHairlineDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = restrictionNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onDismissNotice,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceConversationControlsCard(
    conversationMode: VoiceConversationMode,
    selectedLanguage: VoiceLanguageOption,
    localeChecks: List<VoiceLocaleCheck>,
    ttsAutoSpeak: Boolean,
    ttsSpeechRate: Float,
    selectedVoiceName: String,
    availableVoicesCount: Int,
    onSelectMode: (VoiceConversationMode) -> Unit,
    onSelectLanguage: (VoiceLanguageOption) -> Unit,
    onSetAutoSpeak: (Boolean) -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onOpenVoicePicker: () -> Unit
) {
    val palette = JarvisThemeTokens.palette
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .testTag("voice_controls_panel")
    ) {
        Text(
            text = "Conversation & language",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoiceConversationMode.entries.forEach { mode ->
                val tag = if (mode == VoiceConversationMode.PUSH_TO_TALK) {
                    "voice_mode_ptt"
                } else {
                    "voice_mode_continuous"
                }
                    FilterChip(
                    selected = conversationMode == mode,
                    onClick = { onSelectMode(mode) },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.peachContainer,
                        selectedLabelColor = palette.onHue
                    ),
                    modifier = Modifier.testTag(tag)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Speech & voice language",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VoiceLanguageOption.entries.forEach { option ->
                val check = localeChecks.firstOrNull { it.option == option }
                val leadingIcon: (@Composable () -> Unit)? = when {
                    check == null -> null
                    check.isOfflineDataInstalled -> {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Offline voice installed",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    check.isTtsSupported -> {
                        {
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = "Online voice available",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    else -> null
                }
                val chipPalette = JarvisThemeTokens.palette
                FilterChip(
                    selected = selectedLanguage == option,
                    onClick = { onSelectLanguage(option) },
                    leadingIcon = leadingIcon,
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipPalette.peachContainer,
                        selectedLabelColor = chipPalette.onHue,
                        selectedLeadingIconColor = chipPalette.onHue
                    ),
                    modifier = Modifier.testTag("voice_lang_${option.tag.lowercase(Locale.ROOT)}")
                )
            }
        }

        EditorialHairlineDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Speak replies automatically",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Reads assistant replies aloud with markdown stripped",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = ttsAutoSpeak,
                onCheckedChange = onSetAutoSpeak,
                modifier = Modifier.testTag("voice_autospeak_switch")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speech rate",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val ratePalette = JarvisThemeTokens.palette
                Text(
                    text = String.format(Locale.US, "%.2fx", ttsSpeechRate),
                    style = JarvisMonoTelemetryStyle,
                    color = ratePalette.peachAccent
                )
            }
            OutlinedButton(
                onClick = onOpenVoicePicker,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("voice_picker_button")
            ) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (selectedVoiceName.isBlank()) {
                        "System voice ($availableVoicesCount)"
                    } else {
                        selectedVoiceName.take(16)
                    }
                )
            }
        }
        Slider(
            value = ttsSpeechRate,
            onValueChange = onSetSpeechRate,
            valueRange = 0.6f..1.6f,
            modifier = Modifier.testTag("voice_rate_slider")
        )
    }
}

@Composable
fun VoiceTranscriptAndConversationCard(
    engineState: VoiceEngineState,
    lastTranscript: String,
    isGeneratingReply: Boolean,
    streamingReply: String,
    voiceTurns: List<VoiceTurnItem>,
    onSpeakTurn: (String) -> Unit
) {
    val palette = JarvisThemeTokens.palette
    val rmsDb = (engineState as? VoiceEngineState.Listening)?.rmsDb ?: 0f
    val vadActive = (engineState as? VoiceEngineState.Listening)?.isVoiceActive == true

    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transcript",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            HudBadge(
                text = if (vadActive) "Speech detected" else "Standby",
                color = if (vadActive) palette.peachAccent else palette.textSecondary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        TelemetryProgressBar(
            label = "Input level",
            valueText = String.format(Locale.US, "%.1f dB", rmsDb),
            progress = (rmsDb / 12f).coerceIn(0f, 1f),
            accentColor = palette.peachAccent
        )
        Spacer(modifier = Modifier.height(12.dp))
        val displayedTranscript = when {
            engineState is VoiceEngineState.Listening && engineState.partialTranscript.isNotBlank() ->
                engineState.partialTranscript
            lastTranscript.isNotBlank() -> lastTranscript
            else -> "No speech captured yet. Hold Push-to-Talk, enable Continuous mode, or type below."
        }
        val transcriptDirection = if (containsArabicVoiceScript(displayedTranscript)) {
            LayoutDirection.Rtl
        } else {
            LocalLayoutDirection.current
        }
        CompositionLocalProvider(LocalLayoutDirection provides transcriptDirection) {
            Text(
                text = displayedTranscript,
                style = MaterialTheme.typography.bodyMedium,
                color = if (lastTranscript.isNotBlank() || (engineState is VoiceEngineState.Listening && engineState.partialTranscript.isNotBlank())) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_transcript_text")
            )
        }

        if (isGeneratingReply) {
            EditorialHairlineDivider(modifier = Modifier.padding(vertical = 12.dp))
            val replyDirection = if (containsArabicVoiceScript(streamingReply)) {
                LayoutDirection.Rtl
            } else {
                LocalLayoutDirection.current
            }
            CompositionLocalProvider(LocalLayoutDirection provides replyDirection) {
                Text(
                    text = if (streamingReply.isBlank()) "Thinking…" else streamingReply,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (voiceTurns.isNotEmpty()) {
            EditorialHairlineDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = "Recent turns",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            val recent = voiceTurns.takeLast(4)
            recent.forEachIndexed { index, turn ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = (if (turn.isUser) "You" else "Jarvis") +
                                (turn.telemetryNote?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (turn.isUser) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                        val turnDir = if (containsArabicVoiceScript(turn.text)) {
                            LayoutDirection.Rtl
                        } else {
                            LocalLayoutDirection.current
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides turnDir) {
                            Text(
                                text = turn.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (!turn.isUser) {
                        IconButton(
                            onClick = { onSpeakTurn(turn.text) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Speak reply",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (index < recent.lastIndex) {
                    EditorialHairlineDivider()
                }
            }
        }
    }
}

@Composable
fun VoiceTextFallbackBar(
    inputText: String,
    isGenerating: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
    ) {
        Text(
            text = "Text input",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text(stringResource(R.string.voice_fallback_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .weight(1f)
                    .testTag("voice_fallback_input_field")
            )
            OutlinedButton(
                onClick = onSubmit,
                enabled = inputText.isNotBlank() && !isGenerating,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("voice_fallback_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send command")
            }
        }
    }
}

@Composable
fun WakeWordEvaluationCard(
    wakeWordEnabled: Boolean,
    wakeWordState: WakeWordState,
    metrics: WakeWordAudioMetrics,
    report: WakeWordEvaluationReport?,
    pauseMicInBackground: Boolean,
    porcupineAccessKey: String = "",
    onToggleStandby: (Boolean) -> Unit,
    onTogglePauseInBackground: (Boolean) -> Unit,
    onSavePorcupineAccessKey: (String) -> Unit = {}
) {
    var keyDraft by remember(porcupineAccessKey) { mutableStateOf(porcupineAccessKey) }

    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .testTag("wake_word_evaluation_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val wakePalette = JarvisThemeTokens.palette
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Radar, contentDescription = null, tint = wakePalette.peachAccent)
                Column {
                    Text(
                        text = "Wake word (\"${report?.targetPhrase ?: "Jarvis"}\")",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = wakeWordState.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = wakeWordEnabled,
                onCheckedChange = onToggleStandby,
                modifier = Modifier.testTag("voice_wakeword_switch")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val wakePalette = JarvisThemeTokens.palette
            val verified = report?.isPhraseDetectionVerified == true
            HudBadge(
                text = when {
                    verified -> "Keyword verified"
                    porcupineAccessKey.isNotBlank() -> "Key configured"
                    else -> "Evaluation mode"
                },
                color = if (verified) wakePalette.successAccent else wakePalette.textSecondary
            )
            Text(
                text = String.format(Locale.US, "%.1f dBFS · %d frames", metrics.rmsDbfs, metrics.framesProcessed),
                style = JarvisMonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = { Text("Porcupine AccessKey (optional)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .weight(1f)
                    .testTag("porcupine_access_key_input")
            )
            OutlinedButton(
                onClick = { onSavePorcupineAccessKey(keyDraft) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("porcupine_access_key_save_button")
            ) {
                Text("Save")
            }
        }

        if (report != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = report.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            report.blockers.forEach { blocker ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = blocker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        EditorialHairlineDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    Text(
                        text = "Pause mic in background",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Stops foreground mic capture when app is minimized",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = pauseMicInBackground,
                onCheckedChange = onTogglePauseInBackground,
                modifier = Modifier.testTag("voice_background_pause_switch")
            )
        }
    }
}

@Composable
fun TtsVoicePickerDialog(
    voices: List<TtsVoiceInfo>,
    selectedVoiceName: String,
    onSelectVoice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("System voice") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        onSelectVoice("")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                ) {
                    if (selectedVoiceName.isBlank()) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Automatic (match language)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (voices.isEmpty()) {
                    Text(
                        text = "No TextToSpeech voices reported on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(voices, key = { it.name }) { voice ->
                            val isSelected = voice.name == selectedVoiceName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .minimumInteractiveComponentSize()
                                    .clickable {
                                        onSelectVoice(voice.name)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${voice.displayLocale} (${voice.localeTag})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${voice.name} · ${if (voice.isNetworkRequired) "Online" else "Offline"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun containsArabicVoiceScript(text: String): Boolean =
    text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
