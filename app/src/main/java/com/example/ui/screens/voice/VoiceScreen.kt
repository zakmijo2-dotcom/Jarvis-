package com.example.ui.screens.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.voice.VoiceEngineState
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.HudPanel
import com.example.ui.components.JarvisOrb
import com.example.ui.components.OrbMode
import com.example.ui.components.TelemetryProgressBar

@Composable
fun VoiceScreen(
    uiState: VoiceUiState,
    onToggleListening: () -> Unit,
    onSpeakStatus: () -> Unit,
    onSpeakTranscript: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onDismissError: () -> Unit,
    onLogTranscriptToChat: (String) -> Unit
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            onToggleListening()
        }
    }

    val engineState = uiState.engineState
    val orbMode = when (engineState) {
        is VoiceEngineState.Listening -> OrbMode.LISTENING
        is VoiceEngineState.Processing -> OrbMode.PROCESSING
        is VoiceEngineState.Speaking -> OrbMode.SPEAKING
        is VoiceEngineState.Error, is VoiceEngineState.Unavailable -> OrbMode.WARNING
        is VoiceEngineState.Idle -> OrbMode.IDLE
    }
    val rmsDb = (engineState as? VoiceEngineState.Listening)?.rmsDb ?: 0f

    val statusText = when (engineState) {
        is VoiceEngineState.Idle -> if (hasMicPermission) {
            stringResource(R.string.voice_tap_to_listen)
        } else {
            stringResource(R.string.voice_permission_needed)
        }

        is VoiceEngineState.Listening -> stringResource(R.string.voice_listening)
        is VoiceEngineState.Processing -> "Processing speech buffer…"
        is VoiceEngineState.Speaking -> "Speaking via Android TextToSpeech…"
        is VoiceEngineState.Unavailable -> engineState.reason
        is VoiceEngineState.Error -> engineState.message
    }

    HudBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
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

            Spacer(modifier = Modifier.height(4.dp))

            JarvisOrb(
                mode = orbMode,
                size = 240.dp,
                audioLevel = rmsDb,
                animationEnabled = uiState.settings.orbAnimationEnabled,
                glowIntensity = uiState.settings.orbGlowIntensity,
                statusDescription = stringResource(R.string.cd_voice_orb),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        onDismissError()
                        if (hasMicPermission) {
                            onToggleListening()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

            HudPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            ) {
                TelemetryProgressBar(
                    label = "AUDIO INPUT RMS",
                    valueText = String.format("%.1f dB", rmsDb),
                    progress = (rmsDb / 12f).coerceIn(0f, 1f),
                    accentColor = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "TRANSCRIPT BUFFER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                val displayedTranscript = when {
                    engineState is VoiceEngineState.Listening && engineState.partialTranscript.isNotBlank() ->
                        engineState.partialTranscript

                    uiState.lastTranscript.isNotBlank() -> uiState.lastTranscript
                    else -> "No speech captured yet. Tap the central orb or press Listen to capture real audio via Android SpeechRecognizer."
                }
                Text(
                    text = displayedTranscript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.lastTranscript.isNotBlank() ||
                        (engineState is VoiceEngineState.Listening && engineState.partialTranscript.isNotBlank())
                    ) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag("voice_transcript_text")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onDismissError()
                        if (hasMicPermission) {
                            onToggleListening()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("voice_listen_button")
                ) {
                    Icon(
                        imageVector = if (engineState is VoiceEngineState.Listening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (engineState is VoiceEngineState.Listening) "Stop" else "Listen")
                }

                FilledTonalButton(
                    onClick = {
                        if (engineState is VoiceEngineState.Speaking) {
                            onStopSpeaking()
                        } else {
                            onSpeakStatus()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("voice_tts_button")
                ) {
                    Icon(
                        imageVector = if (engineState is VoiceEngineState.Speaking) {
                            Icons.Filled.Stop
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (engineState is VoiceEngineState.Speaking) {
                            stringResource(R.string.voice_stop_tts)
                        } else {
                            stringResource(R.string.voice_speak_status)
                        }
                    )
                }
            }

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
        }
    }
}
