package com.example.ui.screens.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.voice.MicStatus
import com.example.core.voice.VoiceConversationMode
import com.example.core.voice.VoiceEngineState
import com.example.core.voice.VoiceLanguageOption
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.JarvisOrb
import com.example.ui.components.OrbMode
import com.example.ui.components.hairlineDp
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JarvisThemeTokens

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
    onSavePorcupineAccessKey: (String) -> Unit = {},
    onConfirmPendingTool: () -> Unit = {},
    onCancelPendingTool: () -> Unit = {},
    onCancelActiveTool: () -> Unit = {},
    onDismissPermissionPrompt: () -> Unit = {},
    onRuntimePermissionResult: (String, Boolean) -> Unit = { _, _ -> },
    onOpenDeviceControls: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val palette = JarvisThemeTokens.palette
    var fallbackInput by remember { mutableStateOf("") }
    var showVoicePicker by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(true) }

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

    var activeToolPermissionRequest by remember { mutableStateOf<String?>(null) }
    val toolPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val perm = activeToolPermissionRequest ?: uiState.pendingRuntimePermission ?: ""
        activeToolPermissionRequest = null
        if (perm.isNotBlank()) {
            onRuntimePermissionResult(perm, granted)
        } else {
            onDismissPermissionPrompt()
        }
    }

    val engineState = uiState.engineState
    val isListening = engineState is VoiceEngineState.Listening || uiState.isContinuousSessionActive
    val isSpeakingOrGenerating = engineState is VoiceEngineState.Speaking || uiState.isGeneratingReply

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
        uiState.isGeneratingReply -> "Preparing response…"
        engineState is VoiceEngineState.Idle -> if (hasMicPermission) {
            if (uiState.conversationMode == VoiceConversationMode.CONTINUOUS) {
                if (uiState.isContinuousSessionActive) "Continuous listening active"
                else "Tap the sphere or start continuous listening"
            } else {
                stringResource(R.string.voice_tap_to_listen)
            }
        } else {
            stringResource(R.string.voice_permission_needed)
        }
        engineState is VoiceEngineState.Listening -> stringResource(R.string.voice_listening)
        engineState is VoiceEngineState.Processing -> "Processing speech…"
        engineState is VoiceEngineState.Speaking -> "Speaking reply…"
        engineState is VoiceEngineState.Unavailable -> engineState.reason
        engineState is VoiceEngineState.Error -> engineState.message
        else -> ""
    }

    val focalCopy = when {
        engineState is VoiceEngineState.Listening && engineState.partialTranscript.isNotBlank() ->
            engineState.partialTranscript
        uiState.streamingReply.isNotBlank() -> uiState.streamingReply
        uiState.lastTranscript.isNotBlank() -> uiState.lastTranscript
        uiState.voiceTurns.isNotEmpty() -> uiState.voiceTurns.last().text
        else -> statusText
    }
    val focalDirection = if (containsArabicText(focalCopy)) {
        LayoutDirection.Rtl
    } else {
        LocalLayoutDirection.current
    }

    HudBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top bar with Peach section hue icon + status pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.peachContainer
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GraphicEq,
                                contentDescription = null,
                                tint = palette.onHue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.voice_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.text,
                        modifier = Modifier.testTag("voice_screen_title")
                    )
                }

                HudBadge(
                    text = if (uiState.isRecognitionAvailable) "Speech ready" else "Speech unavailable",
                    color = if (uiState.isRecognitionAvailable) palette.peachAccent else palette.textSecondary
                )
            }

            // Active tool / plan progress banner
            AnimatedVisibility(visible = uiState.activePlan != null || uiState.activeToolName != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.surface,
                    border = BorderStroke(hairlineDp(), palette.peachAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .testTag("voice_active_plan_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activePlan = uiState.activePlan
                        val stepInfo = when {
                            uiState.activeToolName != null -> "Running ${uiState.activeToolName}…"
                            activePlan != null -> {
                                val done = activePlan.steps.count { it.status.name == "COMPLETED" || it.status.name == "RECOVERED" }
                                "${activePlan.routeDecision.route.label} ($done/${activePlan.steps.size})"
                            }
                            else -> "Working…"
                        }
                        Text(
                            text = stepInfo,
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.peachAccent,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onCancelActiveTool,
                            shape = CircleShape,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("voice_cancel_plan_button")
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }

            // Tool confirmation card
            val pendingConfirm = uiState.pendingToolConfirmation
            AnimatedVisibility(visible = pendingConfirm != null) {
                if (pendingConfirm != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = palette.surface,
                        border = BorderStroke(hairlineDp(), palette.peachAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .testTag("voice_confirmation_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = palette.peachAccent
                                )
                                Text(
                                    text = "Confirm ${pendingConfirm.request.toolName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.text
                                )
                            }
                            Text(
                                text = pendingConfirm.confirmationMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.text
                            )
                            Text(
                                text = pendingConfirm.sensitivityWarning,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onCancelPendingTool,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("voice_cancel_tool_button")
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = onConfirmPendingTool,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.peachContainer,
                                        contentColor = palette.onHue
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("voice_confirm_tool_button")
                                ) {
                                    Text("Approve")
                                }
                            }
                        }
                    }
                }
            }

            // Tool runtime / special permission prompt card
            val pendingPerm = uiState.pendingRuntimePermission
            val pendingSpecial = uiState.pendingSpecialAccess
            AnimatedVisibility(visible = pendingPerm != null || pendingSpecial != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.surface,
                    border = BorderStroke(hairlineDp(), palette.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .testTag("voice_permission_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (pendingPerm != null) "Permission required" else "${pendingSpecial?.label} required",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.text
                        )
                        Text(
                            text = uiState.permissionRationale
                                ?: "Explicit authorization is required before using this Android feature.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onDismissPermissionPrompt()
                                    if (pendingSpecial != null) {
                                        onOpenDeviceControls()
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("voice_dismiss_permission_button")
                            ) {
                                Text(if (pendingSpecial != null) "Privacy controls" else "Dismiss")
                            }
                            Button(
                                onClick = {
                                    if (pendingPerm != null) {
                                        activeToolPermissionRequest = pendingPerm
                                        toolPermissionLauncher.launch(pendingPerm)
                                    } else if (pendingSpecial != null) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(pendingSpecial.settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                        onDismissPermissionPrompt()
                                    }
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.peachContainer,
                                    contentColor = palette.onHue
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("voice_grant_tool_permission_button")
                            ) {
                                Text(if (pendingPerm != null) "Allow" else "Open settings")
                            }
                        }
                    }
                }
            }

            // Centered hero stage: Iridescent Orb + large readable transcript/answer
            Spacer(modifier = Modifier.height(8.dp))

            JarvisOrb(
                mode = orbMode,
                size = 212.dp,
                audioLevel = rmsDb,
                animationEnabled = uiState.settings.orbAnimationEnabled,
                glowIntensity = uiState.settings.orbGlowIntensity,
                isModelGenerating = uiState.isGeneratingReply,
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

            // Big readable transcript / answer below the orb
            CompositionLocalProvider(LocalLayoutDirection provides focalDirection) {
                Text(
                    text = focalCopy,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (engineState is VoiceEngineState.Error || engineState is VoiceEngineState.Unavailable) {
                        palette.errorAccent
                    } else {
                        palette.text
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .padding(horizontal = 8.dp)
                )
            }

            if (focalCopy != statusText) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 520.dp)
                )
            }

            // Permission denied card with Coral error tint & pill CTA
            if (!hasMicPermission) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.coralContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = palette.onHue
                        )
                        Text(
                            text = stringResource(R.string.voice_permission_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onHue,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.surface,
                                contentColor = palette.text
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("voice_grant_permission_button")
                        ) {
                            Text(stringResource(R.string.voice_grant_permission))
                        }
                    }
                }
            }

            // Centered primary control row: Type button | Large round Mic (hold to talk) | Close
            val micCd = stringResource(R.string.cd_voice_orb)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(vertical = 8.dp)
                    .testTag("voice_bottom_controls"),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type button
                Surface(
                    shape = CircleShape,
                    color = if (showTextInput) palette.skyContainer else palette.surfaceHigh,
                    border = BorderStroke(hairlineDp(), palette.hairline),
                    modifier = Modifier
                        .size(56.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable { showTextInput = !showTextInput }
                        .testTag("voice_toggle_type_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Keyboard,
                            contentDescription = stringResource(R.string.cd_voice_type),
                            tint = if (showTextInput) palette.onHue else palette.text,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Large round Mic button (supports tap + hold-to-talk in PTT + semantics click)
                val micFill = when {
                    isListening -> palette.coralContainer
                    else -> palette.peachContainer
                }
                Surface(
                    shape = CircleShape,
                    color = micFill,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .semantics {
                            contentDescription = micCd
                            role = Role.Button
                            onClick(label = micCd) {
                                onDismissError()
                                if (isSpeakingOrGenerating) {
                                    onInterruptAllVoice()
                                } else {
                                    requestMicThen {
                                        if (uiState.conversationMode == VoiceConversationMode.CONTINUOUS) {
                                            ensurePostNotificationsIfNeeded()
                                        }
                                        onToggleListening()
                                    }
                                }
                                true
                            }
                        }
                        .pointerInput(hasMicPermission, uiState.conversationMode, isSpeakingOrGenerating) {
                            detectTapGestures(
                                onPress = {
                                    if (isSpeakingOrGenerating) {
                                        return@detectTapGestures
                                    }
                                    if (uiState.conversationMode == VoiceConversationMode.PUSH_TO_TALK && hasMicPermission) {
                                        val startedAt = System.currentTimeMillis()
                                        onPushToTalkPressed()
                                        val released = tryAwaitRelease()
                                        val heldMs = System.currentTimeMillis() - startedAt
                                        if (released) {
                                            onPushToTalkReleased()
                                            if (heldMs < 180L) {
                                                onToggleListening()
                                            }
                                        } else {
                                            onPushToTalkReleased()
                                        }
                                    }
                                },
                                onTap = {
                                    onDismissError()
                                    if (isSpeakingOrGenerating) {
                                        onInterruptAllVoice()
                                    } else if (uiState.conversationMode != VoiceConversationMode.PUSH_TO_TALK || !hasMicPermission) {
                                        requestMicThen {
                                            if (uiState.conversationMode == VoiceConversationMode.CONTINUOUS) {
                                                ensurePostNotificationsIfNeeded()
                                            }
                                            onToggleListening()
                                        }
                                    }
                                }
                            )
                        }
                        .testTag("voice_listen_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isListening || isSpeakingOrGenerating) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = micCd,
                            tint = palette.onHue,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Close button
                Surface(
                    shape = CircleShape,
                    color = palette.surfaceHigh,
                    border = BorderStroke(hairlineDp(), palette.hairline),
                    modifier = Modifier
                        .size(56.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable {
                            onInterruptAllVoice()
                            onClose()
                        }
                        .testTag("voice_close_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_voice_close),
                            tint = palette.text,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Secondary quick actions row (Hold-to-Talk label / Read Status / Stop Speaking / Open in Chat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSpeakingOrGenerating) {
                    Button(
                        onClick = onInterruptAllVoice,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.coralContainer,
                            contentColor = palette.onHue
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("voice_stop_speaking_button")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.voice_stop_tts))
                    }
                } else {
                    OutlinedButton(
                        onClick = onSpeakStatus,
                        shape = CircleShape,
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
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.voice_speak_status))
                    }
                }

                if (uiState.lastTranscript.isNotBlank()) {
                    Button(
                        onClick = { onLogTranscriptToChat(uiState.lastTranscript) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.skyContainer,
                            contentColor = palette.onHue
                        ),
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
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.voice_send_to_chat))
                    }
                }
            }

            AnimatedVisibility(visible = showTextInput) {
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
            }

            EditorialHairlineDivider(modifier = Modifier.widthIn(max = 720.dp))

            PersistentMicStatusBanner(
                micStatus = uiState.micStatus,
                isFgsRunning = uiState.isForegroundServiceRunning,
                isContinuousActive = uiState.isContinuousSessionActive,
                detectedLanguage = uiState.detectedLanguage,
                restrictionNotice = uiState.serviceRestrictionNotice,
                onDisableListening = onInterruptAllVoice,
                onDismissNotice = onDismissError
            )

            VoiceTranscriptAndConversationCard(
                engineState = engineState,
                lastTranscript = uiState.lastTranscript,
                isGeneratingReply = uiState.isGeneratingReply,
                streamingReply = uiState.streamingReply,
                voiceTurns = uiState.voiceTurns,
                onSpeakTurn = onSpeakTranscript
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

private fun containsArabicText(text: String): Boolean =
    text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }

@Preview(name = "Voice Light", showBackground = true)
@Preview(name = "Voice Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceScreenPreview() {
    JarvisTheme {
        VoiceScreen(
            uiState = VoiceUiState(
                isRecognitionAvailable = true,
                micStatus = MicStatus.OFF,
                lastTranscript = "Check battery level"
            ),
            onMicPermissionChanged = {},
            onToggleListening = {},
            onPushToTalkPressed = {},
            onPushToTalkReleased = {},
            onInterruptAllVoice = {},
            onSpeakStatus = {},
            onSpeakTranscript = {},
            onStopSpeaking = {},
            onDismissError = {},
            onSubmitTextFallback = {},
            onSelectConversationMode = {},
            onSelectLanguage = {},
            onSelectTtsVoice = {},
            onSetSpeechRate = {},
            onSetAutoSpeak = {},
            onToggleWakeWordStandby = {},
            onTogglePauseInBackground = {},
            onLogTranscriptToChat = {}
        )
    }
}
