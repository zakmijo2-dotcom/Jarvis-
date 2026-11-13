package com.example.ui.screens.voice

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.example.ui.components.HudBadge
import com.example.ui.components.HudPanel
import com.example.ui.components.TelemetryProgressBar
import com.example.ui.theme.NominalGreen
import com.example.ui.theme.SignalAmber
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
    val badgeColor = when (micStatus) {
        MicStatus.ACTIVE_STT -> NominalGreen
        MicStatus.STANDBY_WAKE_WORD -> MaterialTheme.colorScheme.secondary
        MicStatus.PERMISSION_DENIED, MicStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
        MicStatus.PAUSED_BACKGROUND -> SignalAmber
        MicStatus.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .testTag("mic_status_indicator"),
        borderColor = badgeColor.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (micStatus == MicStatus.OFF || micStatus == MicStatus.PERMISSION_DENIED) {
                        Icons.Filled.MicOff
                    } else {
                        Icons.Filled.Mic
                    },
                    contentDescription = "Microphone state",
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = micStatus.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = badgeColor
                    )
                    Text(
                        text = buildString {
                            append("Detected: ${detectedLanguage.label}")
                            if (isFgsRunning) append(" • FGS Mic Notification Active")
                        },
                        style = MaterialTheme.typography.labelSmall,
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
                    Spacer(Modifier.width(4.dp))
                    Text("Disable")
                }
            } else {
                HudBadge(
                    text = if (isFgsRunning) "FGS ON" else "BATTERY SAFE",
                    color = if (isFgsRunning) NominalGreen else MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!restrictionNotice.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = restrictionNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = SignalAmber,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismissNotice) {
                    Text("OK")
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
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .testTag("voice_controls_panel")
    ) {
        Text(
            text = "CONVERSATION & LANGUAGE MATRIX",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

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
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag(tag)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(
                text = "Speech & Voice Language",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VoiceLanguageOption.entries.forEach { option ->
                val check = localeChecks.firstOrNull { it.option == option }
                val suffix = when {
                    check == null -> ""
                    check.isOfflineDataInstalled -> " ✓"
                    check.isTtsSupported -> " ◌"
                    else -> ""
                }
                FilterChip(
                    selected = selectedLanguage == option,
                    onClick = { onSelectLanguage(option) },
                    label = { Text("${option.label}$suffix") },
                    modifier = Modifier.testTag("voice_lang_${option.tag.lowercase(Locale.ROOT)}")
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Automatic Response Playback",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sanitizes markdown, links, & telemetry before speaking",
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

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(Locale.US, "Speech Rate: %.2fx", ttsSpeechRate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
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
                        "System Voice ($availableVoicesCount)"
                    } else {
                        "Voice: ${selectedVoiceName.take(14)}"
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
                text = "VOICE ACTIVITY & TRANSCRIPT",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            HudBadge(
                text = if (vadActive) "VAD: SPEECH" else "VAD: STANDBY",
                color = if (vadActive) NominalGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TelemetryProgressBar(
            label = "AUDIO INPUT RMS",
            valueText = String.format(Locale.US, "%.1f dB", rmsDb),
            progress = (rmsDb / 12f).coerceIn(0f, 1f),
            accentColor = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        val displayedTranscript = when {
            engineState is VoiceEngineState.Listening && engineState.partialTranscript.isNotBlank() ->
                engineState.partialTranscript
            lastTranscript.isNotBlank() -> lastTranscript
            else -> "No speech captured yet. Use Push-to-Talk, Continuous mode, or the text fallback below."
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
            Spacer(modifier = Modifier.height(10.dp))
            val replyDirection = if (containsArabicVoiceScript(streamingReply)) {
                LayoutDirection.Rtl
            } else {
                LocalLayoutDirection.current
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides replyDirection) {
                    Text(
                        text = if (streamingReply.isBlank()) "JARVIS generating reply…" else streamingReply,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }
        }

        if (voiceTurns.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "RECENT VOICE EXCHANGES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                voiceTurns.takeLast(4).forEach { turn ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (turn.isUser) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = (if (turn.isUser) "YOU" else "JARVIS") +
                                        (turn.telemetryNote?.let { " • $it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (turn.isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
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
                    }
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
            text = "TEXT FALLBACK INPUT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(6.dp))
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
                modifier = Modifier
                    .weight(1f)
                    .testTag("voice_fallback_input_field")
            )
            Button(
                onClick = onSubmit,
                enabled = inputText.isNotBlank() && !isGenerating,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("voice_fallback_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send fallback prompt")
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
    onToggleStandby: (Boolean) -> Unit,
    onTogglePauseInBackground: (Boolean) -> Unit
) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Radar, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Column {
                    Text(
                        text = "WAKE WORD ENGINE (\"${report?.targetPhrase ?: "Jarvis"}\")",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = wakeWordState.label,
                        style = MaterialTheme.typography.labelSmall,
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

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudBadge(
                text = if (report?.isPhraseDetectionVerified == true) "MIC VERIFIED" else "KWS UNVERIFIED (EVALUATION)",
                color = SignalAmber
            )
            Text(
                text = String.format(Locale.US, "PCM Gate: %.1f dBFS (%d frames)", metrics.rmsDbfs, metrics.framesProcessed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (report != null) {
            Text(
                text = report.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            report.blockers.forEach { blocker ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(14.dp), tint = SignalAmber)
                    Text(
                        text = blocker,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp), tint = NominalGreen)
                Column {
                    Text(
                        text = "Pause Mic on Background",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Stops FGS AudioRecord when app leaves foreground to save battery & honor Android 15 limits",
                        style = MaterialTheme.typography.labelSmall,
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
        title = { Text("Select Android System Voice") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        onSelectVoice("")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedVoiceName.isBlank()) "✓ Auto / Language Default" else "Auto / Language Default")
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (voices.isEmpty()) {
                    Text(
                        text = "No enumerated TTS voices reported yet by this Android environment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(voices, key = { it.name }) { voice ->
                            val isSelected = voice.name == selectedVoiceName
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectVoice(voice.name)
                                        onDismiss()
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${if (isSelected) "✓ " else ""}${voice.displayLocale} (${voice.localeTag})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${voice.name} • ${if (voice.isNetworkRequired) "Network" else "Offline"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
