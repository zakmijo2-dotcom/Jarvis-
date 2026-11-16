package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.JarvisApplication
import com.example.R
import com.example.core.ai.BatteryThermalSnapshot
import com.example.core.ai.EngineLoadState
import com.example.core.ai.ThermalAndMemoryPolicy
import com.example.core.device.DeviceCapabilitySnapshot
import com.example.core.device.ShizukuAvailabilityState
import com.example.core.device.WirelessAdbConnectionState
import com.example.core.memory.MemoryFact
import com.example.core.tools.DeviceTelemetry
import com.example.core.voice.MicStatus
import com.example.core.voice.VoiceEngineState
import com.example.core.voice.VoiceServiceStatus
import com.example.core.wakeword.WakeWordState
import com.example.data.repository.JarvisSettings
import com.example.di.JarvisContainer
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.hairlineDp
import com.example.ui.screens.bridge.DeviceBridgeSetupScreen
import com.example.ui.screens.bridge.DeviceBridgeSetupViewModel
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.chat.HistoryDrawerContent
import com.example.ui.screens.device.DeviceDashboardScreen
import com.example.ui.screens.device.DeviceDashboardTab
import com.example.ui.screens.device.DeviceDashboardViewModel
import com.example.ui.screens.history.ActivityHistoryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.models.ModelManagerScreen
import com.example.ui.screens.models.ModelManagerViewModel
import com.example.ui.screens.security.PermissionsSecurityScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.voice.VoiceScreen
import com.example.ui.screens.voice.VoiceViewModel
import com.example.ui.theme.JarvisThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun JarvisAppShell(
    container: JarvisContainer,
    app: JarvisApplication? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activityStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "JarvisAppShell requires a ViewModelStoreOwner."
    }
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Shared lightweight flows safe for cold launch (no heavy secondary ViewModel initialization)
    val settings by container.settingsRepository.settingsFlow.collectAsStateWithLifecycle(
        initialValue = JarvisSettings()
    )
    val memoryFacts by container.localMemory.observeFacts().collectAsStateWithLifecycle(
        initialValue = emptyList()
    )
    val conversations by container.conversationRepository.observeConversations().collectAsStateWithLifecycle(
        initialValue = emptyList()
    )
    val activeModel by container.modelManager.observeActiveModel().collectAsStateWithLifecycle(
        initialValue = null
    )
    val engineLoadState by container.aiEngine.loadState.collectAsStateWithLifecycle(
        initialValue = EngineLoadState.Unloaded
    )
    val isEngineGenerating by container.aiEngine.isGenerating.collectAsStateWithLifecycle(
        initialValue = false
    )
    val voiceEngineState by container.voiceEngine.state.collectAsStateWithLifecycle(
        initialValue = VoiceEngineState.Idle
    )
    val wakeWordState by container.wakeWordEngine.state.collectAsStateWithLifecycle(
        initialValue = WakeWordState.DORMANT
    )
    val micStatus = remember(voiceEngineState, wakeWordState) {
        when (voiceEngineState) {
            is VoiceEngineState.Listening -> MicStatus.ACTIVE_STT
            is VoiceEngineState.Unavailable -> MicStatus.UNAVAILABLE
            else -> when (wakeWordState) {
                WakeWordState.STANDBY_AUDIO_GATE -> MicStatus.STANDBY_WAKE_WORD
                WakeWordState.BACKGROUND_RESTRICTED -> MicStatus.PAUSED_BACKGROUND
                WakeWordState.PERMISSION_DENIED -> MicStatus.PERMISSION_DENIED
                else -> MicStatus.OFF
            }
        }
    }
    val capabilities by container.deviceCapabilityManager.capabilities.collectAsStateWithLifecycle(
        initialValue = DeviceCapabilitySnapshot()
    )

    // Cross-screen transient coordination flags so Chat/Device/Voice ViewModels initialize lazily
    var activeConversationIdHint by rememberSaveable { mutableStateOf<String?>(null) }
    var drawerSearchQuery by rememberSaveable { mutableStateOf("") }
    var pendingVoiceTranscript by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingResumeCheckpointId by rememberSaveable { mutableStateOf<String?>(null) }
    var openControlsTabOnDevice by rememberSaveable { mutableStateOf(false) }

    val defaultGreetingName = stringResource(R.string.home_default_name)
    val greetingName = remember(memoryFacts, defaultGreetingName) {
        extractPreferredUserName(memoryFacts) ?: defaultGreetingName
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val pendingWakeArm by VoiceServiceStatus.pendingWakeArmFromNotification.collectAsStateWithLifecycle()

    val voiceQualifiedName = VoiceRoute::class.qualifiedName.orEmpty()
    val deviceQualifiedName = DeviceRoute::class.qualifiedName.orEmpty()
    val settingsQualifiedName = SettingsRoute::class.qualifiedName.orEmpty()
    val bridgeQualifiedName = DeviceBridgeSetupRoute::class.qualifiedName.orEmpty()
    val permissionsQualifiedName = PermissionsSecurityRoute::class.qualifiedName.orEmpty()
    val activityQualifiedName = ActivityHistoryRoute::class.qualifiedName.orEmpty()

    fun isTopDestinationSelected(dest: TopDestination): Boolean = when (dest) {
        TopDestination.MORE -> currentRoute == dest.routeQualifiedName ||
            currentRoute == voiceQualifiedName ||
            currentRoute == deviceQualifiedName ||
            currentRoute == bridgeQualifiedName ||
            currentRoute == permissionsQualifiedName ||
            currentRoute == activityQualifiedName ||
            currentRoute == settingsQualifiedName
        else -> currentRoute == dest.routeQualifiedName
    }

    fun navigateToDestination(destination: TopDestination) {
        if (settings.hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (destination == TopDestination.MORE) {
            val popped = navController.popBackStack(MoreRoute, inclusive = false)
            if (!popped) {
                navController.navigate(MoreRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                }
            }
            return
        }
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToSubRoute(route: Any) {
        if (settings.hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(pendingWakeArm) {
        if (pendingWakeArm && currentRoute != voiceQualifiedName) {
            navigateToSubRoute(VoiceRoute)
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val filteredDrawerConversations = remember(conversations, drawerSearchQuery) {
        if (drawerSearchQuery.isBlank()) {
            conversations
        } else {
            val needle = drawerSearchQuery.trim().lowercase()
            conversations.filter {
                it.title.lowercase().contains(needle) ||
                    (it.lastMessage?.lowercase()?.contains(needle) == true)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute == TopDestination.CHAT.routeQualifiedName || drawerState.isOpen,
        drawerContent = {
            HistoryDrawerContent(
                conversations = if (drawerState.isOpen || drawerState.targetValue == DrawerValue.Open) {
                    filteredDrawerConversations
                } else {
                    emptyList()
                },
                activeConversationId = activeConversationIdHint ?: conversations.firstOrNull()?.id,
                searchQuery = drawerSearchQuery,
                onSearchQueryChange = { drawerSearchQuery = it },
                onSelectConversation = { id ->
                    activeConversationIdHint = id
                    scope.launch { drawerState.close() }
                    navigateToDestination(TopDestination.CHAT)
                },
                onCreateNewConversation = {
                    scope.launch {
                        val nextId = container.conversationRepository.createConversation(
                            title = "Core Session ${conversations.size + 1}"
                        )
                        activeConversationIdHint = nextId
                        drawerState.close()
                        navigateToDestination(TopDestination.CHAT)
                    }
                },
                onRenameConversation = { id, title ->
                    scope.launch { container.conversationRepository.renameConversation(id, title) }
                },
                onTogglePinned = { id, currentlyPinned ->
                    scope.launch { container.conversationRepository.togglePinned(id, currentlyPinned) }
                },
                onDeleteConversation = { id ->
                    scope.launch {
                        val fallbackId = container.conversationRepository.deleteConversation(id)
                        if (activeConversationIdHint == id) {
                            activeConversationIdHint = fallbackId
                        }
                    }
                },
                onOpenModels = {
                    scope.launch { drawerState.close() }
                    navigateToSubRoute(ModelsRoute)
                },
                onOpenVoice = {
                    scope.launch { drawerState.close() }
                    navigateToSubRoute(VoiceRoute)
                },
                onOpenActivityHistory = {
                    scope.launch { drawerState.close() }
                    navigateToSubRoute(ActivityHistoryRoute)
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    navigateToSubRoute(SettingsRoute)
                }
            )
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (settings.uiAdvancedModeEnabled && !useRail) {
                        Column {
                            EditorialHairlineDivider()
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp,
                                windowInsets = NavigationBarDefaults.windowInsets
                            ) {
                                TopDestination.entries.forEach { dest ->
                                    val selected = isTopDestinationSelected(dest)
                                    val palette = JarvisThemeTokens.palette
                                    val indicatorColor = when (dest) {
                                        TopDestination.HOME -> palette.skyContainer
                                        TopDestination.CHAT -> palette.skyContainer
                                        TopDestination.MODELS -> palette.sageContainer
                                        TopDestination.MORE -> palette.lavenderContainer
                                    }
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateToDestination(dest) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(dest.labelRes),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = palette.onHue,
                                            selectedTextColor = palette.text,
                                            indicatorColor = indicatorColor,
                                            unselectedIconColor = palette.textSecondary,
                                            unselectedTextColor = palette.textSecondary
                                        ),
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (settings.uiAdvancedModeEnabled && useRail) {
                        val palette = JarvisThemeTokens.palette
                        NavigationRail(
                            containerColor = palette.surface,
                            modifier = Modifier
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            TopDestination.entries.forEach { dest ->
                                val selected = isTopDestinationSelected(dest)
                                val indicatorColor = when (dest) {
                                    TopDestination.HOME -> palette.skyContainer
                                    TopDestination.CHAT -> palette.skyContainer
                                    TopDestination.MODELS -> palette.sageContainer
                                    TopDestination.MORE -> palette.lavenderContainer
                                }
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToDestination(dest) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = palette.onHue,
                                        selectedTextColor = palette.text,
                                        indicatorColor = indicatorColor,
                                        unselectedIconColor = palette.textSecondary,
                                        unselectedTextColor = palette.textSecondary
                                    ),
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                        VerticalDivider(
                            thickness = hairlineDp(),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ChatRoute,
                        enterTransition = { fadeIn(animationSpec = tween(180)) },
                        exitTransition = { fadeOut(animationSpec = tween(150)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(180)) },
                        popExitTransition = { fadeOut(animationSpec = tween(150)) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        composable<HomeRoute> {
                            val homeTelemetry by produceState<DeviceTelemetry?>(initialValue = null) {
                                value = withContext(Dispatchers.IO) {
                                    runCatching { container.androidTools.readDeviceTelemetry() }.getOrNull()
                                }
                            }
                            val batterySnapshot by produceState(initialValue = BatteryThermalSnapshot()) {
                                value = withContext(Dispatchers.IO) {
                                    runCatching { ThermalAndMemoryPolicy.readBatterySnapshot(context) }
                                        .getOrDefault(BatteryThermalSnapshot())
                                }
                            }
                            val freeRamMb = ((homeTelemetry?.availRamBytes ?: 0L) / (1024L * 1024L))
                            val totalRamMb = ((homeTelemetry?.totalRamBytes ?: 0L) / (1024L * 1024L))
                            val batteryPct = (homeTelemetry?.batteryPercent ?: batterySnapshot.batteryPercent)
                                .takeIf { it in 0..100 } ?: 100
                            val thermalLabel = homeTelemetry?.thermalStatusLabel
                                ?: batterySnapshot.thermalStatusLabel

                            HomeScreen(
                                displayName = greetingName,
                                activeModelName = activeModel?.displayName.orEmpty(),
                                engineLoadState = engineLoadState,
                                micStatus = micStatus,
                                batteryPercent = batteryPct,
                                freeRamMb = freeRamMb,
                                totalRamMb = totalRamMb,
                                thermalLabel = thermalLabel,
                                startupDurationMs = app?.measuredStartupDurationMs(),
                                capabilities = capabilities,
                                conversations = conversations,
                                isModelGenerating = isEngineGenerating,
                                orbAnimationEnabled = settings.orbAnimationEnabled,
                                orbGlowIntensity = settings.orbGlowIntensity,
                                onOpenVoice = { navigateToSubRoute(VoiceRoute) },
                                onOpenChat = { navigateToDestination(TopDestination.CHAT) },
                                onOpenModels = { navigateToDestination(TopDestination.MODELS) },
                                onOpenDevice = { navigateToSubRoute(DeviceRoute) },
                                onOpenBridgeSetup = { navigateToSubRoute(DeviceBridgeSetupRoute) },
                                onOpenActivityHistory = { navigateToSubRoute(ActivityHistoryRoute) },
                                onOpenSettings = { navigateToSubRoute(SettingsRoute) },
                                onSelectConversation = { id ->
                                    activeConversationIdHint = id
                                    navigateToDestination(TopDestination.CHAT)
                                },
                                onSeeAllHistory = {
                                    navigateToDestination(TopDestination.CHAT)
                                    scope.launch { drawerState.open() }
                                }
                            )
                        }

                        composable<ChatRoute> {
                            val chatViewModel: ChatViewModel = viewModel(
                                viewModelStoreOwner = activityStoreOwner,
                                factory = ChatViewModel.factory(
                                    conversationRepository = container.conversationRepository,
                                    modelManager = container.modelManager,
                                    agentCore = container.agentCore,
                                    aiEngine = container.aiEngine,
                                    settingsRepository = container.settingsRepository,
                                    modelBenchmarkDao = container.modelBenchmarkDao,
                                    localMemory = container.localMemory,
                                    androidTools = container.androidTools,
                                    executionOverlayManager = container.executionOverlayManager,
                                    onDeviceOcrEngine = container.onDeviceOcrEngine,
                                    screenPerceptionEngine = container.screenPerceptionEngine
                                )
                            )
                            val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()

                            LaunchedEffect(activeConversationIdHint) {
                                val targetId = activeConversationIdHint
                                if (targetId != null && targetId != chatState.activeConversationId) {
                                    chatViewModel.selectConversation(targetId)
                                }
                            }
                            LaunchedEffect(chatState.activeConversationId) {
                                if (chatState.activeConversationId != null) {
                                    activeConversationIdHint = chatState.activeConversationId
                                }
                            }
                            LaunchedEffect(pendingVoiceTranscript) {
                                val transcript = pendingVoiceTranscript
                                if (!transcript.isNullOrBlank()) {
                                    pendingVoiceTranscript = null
                                    chatViewModel.sendMessage(transcript)
                                }
                            }
                            LaunchedEffect(pendingResumeCheckpointId) {
                                val checkpointId = pendingResumeCheckpointId
                                if (!checkpointId.isNullOrBlank()) {
                                    pendingResumeCheckpointId = null
                                    chatViewModel.resumeSavedCheckpoint(checkpointId)
                                }
                            }

                            ChatScreen(
                                uiState = chatState,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onNewSession = chatViewModel::createNewConversation,
                                onSendMessage = chatViewModel::sendMessage,
                                onCancelGeneration = chatViewModel::cancelGeneration,
                                onLoadActiveModel = chatViewModel::loadActiveModelNow,
                                onRunSystemSnapshot = chatViewModel::runSystemSnapshot,
                                onRunReadinessAudit = chatViewModel::runEngineReadinessAudit,
                                onNavigateToVoice = { navigateToSubRoute(VoiceRoute) },
                                onNavigateToModels = { navigateToSubRoute(ModelsRoute) },
                                onConfirmPendingTool = chatViewModel::confirmPendingTool,
                                onCancelPendingTool = chatViewModel::cancelPendingTool,
                                onCancelActiveTool = chatViewModel::cancelActiveTool,
                                onDismissPermissionPrompt = chatViewModel::dismissPermissionPrompt,
                                onRuntimePermissionResult = chatViewModel::onRuntimePermissionResult,
                                onOpenDeviceControls = {
                                    openControlsTabOnDevice = true
                                    navigateToSubRoute(DeviceRoute)
                                },
                                onSelectRoutingMode = chatViewModel::setRoutingMode,
                                onSummarizeSession = chatViewModel::summarizeCurrentSessionToMemory,
                                onRegenerateResponse = chatViewModel::regenerateLastResponse,
                                onAttachImageUri = { uri -> chatViewModel.attachImageUri(context, uri) },
                                onClearAttachment = chatViewModel::clearAttachedImage,
                                onSubmitSteering = chatViewModel::submitSteering,
                                onOpenSettings = { navigateToSubRoute(SettingsRoute) }
                            )
                        }

                        composable<VoiceRoute> {
                            val voiceViewModel: VoiceViewModel = viewModel(
                                viewModelStoreOwner = activityStoreOwner,
                                factory = VoiceViewModel.factory(
                                    context = context,
                                    voiceEngine = container.voiceEngine,
                                    wakeWordEngine = container.wakeWordEngine,
                                    settingsRepository = container.settingsRepository,
                                    agentCore = container.agentCore,
                                    aiEngine = container.aiEngine,
                                    modelManager = container.modelManager,
                                    conversationRepository = container.conversationRepository,
                                    modelBenchmarkDao = container.modelBenchmarkDao,
                                    androidTools = container.androidTools
                                )
                            )
                            DisposableEffect(lifecycleOwner, voiceViewModel) {
                                val observer = LifecycleEventObserver { _, event ->
                                    when (event) {
                                        Lifecycle.Event.ON_START -> voiceViewModel.onAppLifecycleChanged(true)
                                        Lifecycle.Event.ON_STOP -> voiceViewModel.onAppLifecycleChanged(false)
                                        else -> Unit
                                    }
                                }
                                lifecycleOwner.lifecycle.addObserver(observer)
                                onDispose {
                                    lifecycleOwner.lifecycle.removeObserver(observer)
                                }
                            }
                            val voiceState by voiceViewModel.uiState.collectAsStateWithLifecycle()

                            VoiceScreen(
                                uiState = voiceState,
                                onMicPermissionChanged = voiceViewModel::onMicPermissionChanged,
                                onToggleListening = voiceViewModel::toggleListening,
                                onPushToTalkPressed = voiceViewModel::onPushToTalkPressed,
                                onPushToTalkReleased = voiceViewModel::onPushToTalkReleased,
                                onInterruptAllVoice = voiceViewModel::interruptAllVoice,
                                onSpeakStatus = voiceViewModel::speakStatusReadback,
                                onSpeakTranscript = voiceViewModel::speakTranscript,
                                onStopSpeaking = voiceViewModel::stopSpeaking,
                                onDismissError = voiceViewModel::dismissError,
                                onSubmitTextFallback = voiceViewModel::submitTextFallback,
                                onSelectConversationMode = voiceViewModel::setConversationMode,
                                onSelectLanguage = voiceViewModel::setLanguageOption,
                                onSelectTtsVoice = voiceViewModel::selectTtsVoice,
                                onSetSpeechRate = voiceViewModel::setSpeechRate,
                                onSetAutoSpeak = voiceViewModel::setAutoSpeak,
                                onToggleWakeWordStandby = voiceViewModel::setWakeWordStandbyEnabled,
                                onTogglePauseInBackground = voiceViewModel::setPauseMicInBackground,
                                onLogTranscriptToChat = { transcript ->
                                    pendingVoiceTranscript = transcript
                                    navigateToDestination(TopDestination.CHAT)
                                },
                                onSavePorcupineAccessKey = voiceViewModel::setPorcupineAccessKey,
                                onConfirmPendingTool = voiceViewModel::confirmPendingTool,
                                onCancelPendingTool = voiceViewModel::cancelPendingTool,
                                onCancelActiveTool = voiceViewModel::cancelActiveTool,
                                onDismissPermissionPrompt = voiceViewModel::dismissPermissionPrompt,
                                onRuntimePermissionResult = voiceViewModel::onRuntimePermissionResult,
                                onOpenDeviceControls = {
                                    openControlsTabOnDevice = true
                                    navigateToSubRoute(DeviceRoute)
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }

                        composable<ModelsRoute> {
                            val modelManagerViewModel: ModelManagerViewModel = viewModel(
                                viewModelStoreOwner = activityStoreOwner,
                                factory = ModelManagerViewModel.factory(
                                    modelManager = container.modelManager,
                                    modelArtifactManager = container.modelArtifactManager,
                                    downloadQueueManager = container.downloadQueueManager,
                                    huggingFaceApiClient = container.huggingFaceApiClient,
                                    compatibilityEngine = container.compatibilityEngine,
                                    formatRegistry = container.formatRegistry,
                                    androidTools = container.androidTools,
                                    modelBenchmarkDao = container.modelBenchmarkDao,
                                    aiEngine = container.aiEngine,
                                    settingsRepository = container.settingsRepository,
                                    cloudProviderRegistry = container.cloudProviderRegistry,
                                    modelRouter = container.modelRouter
                                )
                            )
                            val modelState by modelManagerViewModel.uiState.collectAsStateWithLifecycle()

                            ModelManagerScreen(
                                uiState = modelState,
                                onSelectTab = modelManagerViewModel::selectTab,
                                onSelectFilter = modelManagerViewModel::setFilter,
                                onSelectActiveModel = modelManagerViewModel::selectActiveModel,
                                onStartDownload = modelManagerViewModel::startDownload,
                                onImportLocalFile = modelManagerViewModel::importLocalModel,
                                onVerifyArtifact = modelManagerViewModel::verifyArtifact,
                                onDeleteArtifact = modelManagerViewModel::deleteArtifact,
                                onCancelTransfer = modelManagerViewModel::cancelTransfer,
                                onClearTransferStatus = modelManagerViewModel::clearTransferStatus,
                                onLoadModel = modelManagerViewModel::loadModel,
                                onReloadActiveModel = modelManagerViewModel::reloadActiveModel,
                                onUnloadModel = modelManagerViewModel::unloadModel,
                                onRegisterCustomSlot = modelManagerViewModel::registerCustomSlot,
                                onDeleteCustomSlot = modelManagerViewModel::deleteCustomSlot,
                                onUpdateSearchQuery = modelManagerViewModel::updateSearchQuery,
                                onUpdateAuthorFilter = modelManagerViewModel::updateAuthorFilter,
                                onUpdateManualRepoId = modelManagerViewModel::updateManualRepoId,
                                onSelectDiscoverTagFilter = modelManagerViewModel::selectDiscoverTagFilter,
                                onSearchHuggingFace = modelManagerViewModel::searchHuggingFace,
                                onOpenManualRepo = modelManagerViewModel::openManualRepo,
                                onOpenRepoDetail = modelManagerViewModel::openRepoDetail,
                                onDismissRepoDetail = modelManagerViewModel::dismissRepoDetail,
                                onEnqueueHubDownload = modelManagerViewModel::enqueueHubFileDownload,
                                onPauseDownload = modelManagerViewModel::pauseDownload,
                                onResumeDownload = modelManagerViewModel::resumeDownload,
                                onCancelDownload = modelManagerViewModel::cancelDownload,
                                onClearFinishedDownloads = modelManagerViewModel::clearFinishedDownloads,
                                onClearBenchmarks = modelManagerViewModel::clearBenchmarks,
                                onDismissHubNotice = modelManagerViewModel::dismissHubNotice
                            )
                        }

                        composable<MoreRoute> {
                            val activeName = activeModel?.displayName ?: "No model selected"
                            val engineSummary = when (val load = engineLoadState) {
                                is EngineLoadState.Ready -> "Loaded (${load.backendUsed})"
                                is EngineLoadState.Loading -> "Loading"
                                is EngineLoadState.Error -> "Error"
                                is EngineLoadState.Unloaded -> "Unloaded"
                            }
                            val shizukuSummary = when (capabilities.shizuku.availability) {
                                ShizukuAvailabilityState.READY ->
                                    "Ready (v${capabilities.shizuku.shizukuVersion})"
                                ShizukuAvailabilityState.CONNECTED_UNBOUND ->
                                    "Authorized (Binding...)"
                                ShizukuAvailabilityState.PERMISSION_REQUIRED -> "Permission Needed"
                                ShizukuAvailabilityState.PERMISSION_DENIED -> "Permission Denied"
                                ShizukuAvailabilityState.INSTALLED_SERVICE_STOPPED -> "Service Stopped"
                                ShizukuAvailabilityState.NOT_INSTALLED -> "Not Installed"
                            }
                            val wirelessSummary = when (capabilities.wirelessAdb.connectionState) {
                                WirelessAdbConnectionState.CONNECTED ->
                                    "Connected (${capabilities.wirelessAdb.host}:${capabilities.wirelessAdb.connectPort ?: "?"})"
                                WirelessAdbConnectionState.CONNECTING,
                                WirelessAdbConnectionState.PAIRING,
                                WirelessAdbConnectionState.DISCOVERING -> "Linking"
                                WirelessAdbConnectionState.PAIRED_DISCONNECTED -> "Paired (Idle)"
                                WirelessAdbConnectionState.ERROR -> "Error"
                                WirelessAdbConnectionState.UNCONFIGURED -> "Not Configured"
                            }

                            MoreScreen(
                                activeModelSummary = "$activeName · $engineSummary",
                                routingSummary = settings.routingMode.label,
                                shizukuSummary = shizukuSummary,
                                wirelessAdbSummary = wirelessSummary,
                                micSummary = micStatus.label,
                                startupDurationMs = app?.measuredStartupDurationMs(),
                                onOpenVoice = { navigateToSubRoute(VoiceRoute) },
                                onOpenDevice = { navigateToSubRoute(DeviceRoute) },
                                onOpenBridgeSetup = { navigateToSubRoute(DeviceBridgeSetupRoute) },
                                onOpenPermissionsSecurity = { navigateToSubRoute(PermissionsSecurityRoute) },
                                onOpenActivityHistory = { navigateToSubRoute(ActivityHistoryRoute) },
                                onOpenSettings = { navigateToSubRoute(SettingsRoute) }
                            )
                        }

                        composable<DeviceRoute> {
                            val deviceDashboardViewModel: DeviceDashboardViewModel = viewModel(
                                viewModelStoreOwner = activityStoreOwner,
                                factory = DeviceDashboardViewModel.factory(
                                    androidTools = container.androidTools,
                                    settingsRepository = container.settingsRepository,
                                    context = context,
                                    agentCore = container.agentCore,
                                    deviceCapabilityManager = container.deviceCapabilityManager
                                )
                            )
                            DisposableEffect(lifecycleOwner, deviceDashboardViewModel) {
                                val observer = LifecycleEventObserver { _, event ->
                                    if (event == Lifecycle.Event.ON_START) {
                                        deviceDashboardViewModel.refreshAuthorizationStatus()
                                    }
                                }
                                lifecycleOwner.lifecycle.addObserver(observer)
                                onDispose {
                                    lifecycleOwner.lifecycle.removeObserver(observer)
                                }
                            }
                            LaunchedEffect(openControlsTabOnDevice) {
                                if (openControlsTabOnDevice) {
                                    openControlsTabOnDevice = false
                                    deviceDashboardViewModel.selectTab(DeviceDashboardTab.CONTROLS)
                                }
                            }
                            val deviceState by deviceDashboardViewModel.uiState.collectAsStateWithLifecycle()
                            val deviceControlsState by deviceDashboardViewModel.controlsState.collectAsStateWithLifecycle()

                            DeviceDashboardScreen(
                                uiState = deviceState,
                                onRefreshNow = deviceDashboardViewModel::refreshNow,
                                onToggleAutoPolling = deviceDashboardViewModel::toggleAutoPolling,
                                controlsState = deviceControlsState,
                                onSelectTab = deviceDashboardViewModel::selectTab,
                                onRunTool = deviceDashboardViewModel::executeDeviceTool,
                                onConfirmPendingTool = deviceDashboardViewModel::confirmPendingTool,
                                onCancelPendingTool = deviceDashboardViewModel::cancelPendingTool,
                                onCancelActiveTool = deviceDashboardViewModel::cancelActiveTool,
                                onDismissPermissionPrompt = deviceDashboardViewModel::dismissPermissionPrompt,
                                onRuntimePermissionResult = deviceDashboardViewModel::onRuntimePermissionResult,
                                onSetNotificationReaderEnabled = deviceDashboardViewModel::setNotificationReaderEnabled,
                                onSetNotificationPrivacyMaskEnabled = deviceDashboardViewModel::setNotificationPrivacyMaskEnabled,
                                onClearNotificationBuffer = deviceDashboardViewModel::clearNotificationBuffer,
                                onSetAccessibilityAutomationEnabled = deviceDashboardViewModel::setAccessibilityAutomationEnabled,
                                onSetCloudUploadConsentGranted = deviceDashboardViewModel::setCloudUploadConsentGranted,
                                onOpenBridgeSetup = { navigateToSubRoute(DeviceBridgeSetupRoute) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable<DeviceBridgeSetupRoute> {
                            val bridgeViewModel: DeviceBridgeSetupViewModel = viewModel(
                                viewModelStoreOwner = activityStoreOwner,
                                factory = DeviceBridgeSetupViewModel.factory(
                                    shizukuController = container.shizukuController,
                                    wirelessAdbController = container.wirelessAdbController,
                                    deviceCapabilityManager = container.deviceCapabilityManager,
                                    settingsRepository = container.settingsRepository
                                )
                            )
                            DisposableEffect(lifecycleOwner, bridgeViewModel) {
                                val observer = LifecycleEventObserver { _, event ->
                                    if (event == Lifecycle.Event.ON_START) {
                                        bridgeViewModel.refreshAll()
                                    }
                                }
                                lifecycleOwner.lifecycle.addObserver(observer)
                                onDispose {
                                    lifecycleOwner.lifecycle.removeObserver(observer)
                                }
                            }
                            val bridgeState by bridgeViewModel.uiState.collectAsStateWithLifecycle()

                            DeviceBridgeSetupScreen(
                                uiState = bridgeState,
                                onRefreshAll = bridgeViewModel::refreshAll,
                                onRequestShizukuPermission = bridgeViewModel::requestShizukuPermission,
                                onBindShizukuService = bridgeViewModel::bindShizukuService,
                                onOpenShizukuApp = bridgeViewModel::openShizukuManagerApp,
                                onUpdateHost = bridgeViewModel::updateHost,
                                onUpdatePairingPort = bridgeViewModel::updatePairingPort,
                                onUpdatePairingCode = bridgeViewModel::updatePairingCode,
                                onUpdateConnectPort = bridgeViewModel::updateConnectPort,
                                onSelectDiscoveredPairingPort = bridgeViewModel::selectDiscoveredPairingPort,
                                onSelectDiscoveredConnectPort = bridgeViewModel::selectDiscoveredConnectPort,
                                onToggleMdnsDiscovery = bridgeViewModel::toggleMdnsDiscovery,
                                onPairDevice = bridgeViewModel::pairDevice,
                                onConnectAndVerify = bridgeViewModel::connectAndVerify,
                                onVerifyLiveConnection = bridgeViewModel::verifyLiveConnection,
                                onDisconnectAdb = bridgeViewModel::disconnectAdb,
                                onClearSavedAdb = bridgeViewModel::clearSavedWirelessAdb,
                                onDismissBanner = bridgeViewModel::dismissBanner,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable<PermissionsSecurityRoute> {
                            DisposableEffect(lifecycleOwner) {
                                val observer = LifecycleEventObserver { _, event ->
                                    if (event == Lifecycle.Event.ON_START) {
                                        container.deviceCapabilityManager.refreshAll()
                                    }
                                }
                                lifecycleOwner.lifecycle.addObserver(observer)
                                onDispose {
                                    lifecycleOwner.lifecycle.removeObserver(observer)
                                }
                            }
                            val initialKeystoreReport = remember {
                                container.settingsRepository.verifyKeystoreHealth()
                            }
                            PermissionsSecurityScreen(
                                capabilities = capabilities,
                                settings = settings,
                                initialKeystoreReport = initialKeystoreReport,
                                onRecheckKeystore = { container.settingsRepository.verifyKeystoreHealth() },
                                onRefreshPermissions = { container.deviceCapabilityManager.refreshAll() },
                                onSetNotificationReaderEnabled = { enabled ->
                                    scope.launch { container.settingsRepository.setNotificationReaderEnabled(enabled) }
                                },
                                onSetNotificationPrivacyMaskEnabled = { enabled ->
                                    scope.launch { container.settingsRepository.setNotificationPrivacyMaskEnabled(enabled) }
                                },
                                onSetAccessibilityAutomationEnabled = { enabled ->
                                    scope.launch { container.settingsRepository.setAccessibilityAutomationEnabled(enabled) }
                                },
                                onSetCloudUploadConsentGranted = { granted ->
                                    scope.launch { container.settingsRepository.setCloudUploadConsentGranted(granted) }
                                },
                                onOpenDeviceBridgeSetup = { navigateToSubRoute(DeviceBridgeSetupRoute) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable<ActivityHistoryRoute> {
                            val executions by container.agentCore.observeExecutionHistory()
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val commandResults by container.deviceCommandRouter.recentResults
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val benchmarks by container.modelBenchmarkDao.observeBenchmarks()
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val checkpoints by container.agentCheckpointDao.observeCheckpoints()
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val researchHistory by container.researchHistoryDao.observeHistory()
                                .collectAsStateWithLifecycle(initialValue = emptyList())

                            ActivityHistoryScreen(
                                executions = executions,
                                commandResults = commandResults,
                                benchmarks = benchmarks,
                                checkpoints = checkpoints,
                                researchHistory = researchHistory,
                                onResumeCheckpoint = { checkpointId ->
                                    val cp = checkpoints.firstOrNull { it.id == checkpointId }
                                    if (cp != null) {
                                        activeConversationIdHint = cp.conversationId
                                    }
                                    pendingResumeCheckpointId = checkpointId
                                    navigateToDestination(TopDestination.CHAT)
                                },
                                onDeleteCheckpoint = { checkpointId ->
                                    scope.launch {
                                        container.agentCheckpointDao.deleteCheckpoint(checkpointId)
                                    }
                                },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable<SettingsRoute> {
                            val settingsViewModel: SettingsViewModel = viewModel(
                                viewModelStoreOwner = activityStoreOwner,
                                factory = SettingsViewModel.factory(
                                    settingsRepository = container.settingsRepository,
                                    conversationRepository = container.conversationRepository,
                                    wakeWordEngine = container.wakeWordEngine,
                                    localMemory = container.localMemory,
                                    agentCore = container.agentCore,
                                    skillEngine = container.skillEngine,
                                    mcpManager = container.mcpManager
                                )
                            )
                            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

                            SettingsScreen(
                                uiState = settingsState,
                                onSetOrbAnimation = settingsViewModel::setOrbAnimationEnabled,
                                onSetOrbGlow = settingsViewModel::setOrbGlowIntensity,
                                onSetTtsAutoSpeak = settingsViewModel::setTtsAutoSpeak,
                                onSetTtsSpeechRate = settingsViewModel::setTtsSpeechRate,
                                onSetWakeWordStandby = settingsViewModel::setWakeWordStandbyEnabled,
                                onSetTelemetryInterval = settingsViewModel::setTelemetryPollingSeconds,
                                onSetHaptics = settingsViewModel::setHapticsEnabled,
                                onSetInferenceBackend = settingsViewModel::setInferenceBackend,
                                onSetContextLengthTokens = settingsViewModel::setContextLengthTokens,
                                onSetMaxOutputTokens = settingsViewModel::setMaxOutputTokens,
                                onSetSamplerTemperature = settingsViewModel::setSamplerTemperature,
                                onSetSamplerTopK = settingsViewModel::setSamplerTopK,
                                onSetSamplerTopP = settingsViewModel::setSamplerTopP,
                                onSetHuggingFaceToken = settingsViewModel::setHuggingFaceToken,
                                onPurgeHistory = settingsViewModel::purgeConversationArchive,
                                onDismissBanner = settingsViewModel::dismissBanner,
                                onSetRoutingMode = settingsViewModel::setRoutingMode,
                                onSetCloudUploadConsent = settingsViewModel::setCloudUploadConsentGranted,
                                onSetCloudProvider = settingsViewModel::setCloudProviderKind,
                                onSetCloudEndpoint = settingsViewModel::setCloudEndpointUrl,
                                onSetCloudModelId = settingsViewModel::setCloudModelId,
                                onSetCloudApiKey = settingsViewModel::setCloudApiKey,
                                onSetCloudTimeoutSeconds = settingsViewModel::setCloudTimeoutSeconds,
                                onResetCloudUsageStats = settingsViewModel::resetCloudUsageStats,
                                onSetWebResearchEnabled = settingsViewModel::setWebResearchEnabled,
                                onSetWebSearchProvider = settingsViewModel::setWebSearchProvider,
                                onSetWebCustomUrl = settingsViewModel::setWebSearchCustomUrl,
                                onSetWebApiKey = settingsViewModel::setWebSearchApiKey,
                                onMemorySearchQueryChange = settingsViewModel::updateMemorySearchQuery,
                                onAddMemory = settingsViewModel::addMemoryItem,
                                onEditMemory = settingsViewModel::editMemoryItem,
                                onDeleteMemory = settingsViewModel::deleteMemoryItem,
                                onDeleteSkill = settingsViewModel::deleteSkill,
                                onSetThemeMode = settingsViewModel::setThemeMode,
                                onOpenBridgeSetup = { navigateToSubRoute(DeviceBridgeSetupRoute) },
                                onOpenPermissionsSecurity = { navigateToSubRoute(PermissionsSecurityRoute) },
                                onOpenActivityHistory = { navigateToSubRoute(ActivityHistoryRoute) },
                                onNavigateBack = { navController.popBackStack() },
                                onSetUiAdvancedMode = settingsViewModel::setUiAdvancedModeEnabled,
                                onSetScreenshotVision = settingsViewModel::setScreenshotVisionEnabled,
                                onSetFloatingOverlay = settingsViewModel::setFloatingOverlayEnabled,
                                onSetBlockedPackagesCsv = settingsViewModel::setBlockedPackagesCsv,
                                onSetBraveApiKey = settingsViewModel::setBraveSearchApiKey,
                                onSetSerpApiKey = settingsViewModel::setSerpApiKey,
                                onAddMcpServer = settingsViewModel::addOrUpdateMcpServer,
                                onSetMcpServerEnabled = settingsViewModel::setMcpServerEnabled,
                                onSyncMcpServer = settingsViewModel::syncMcpServerTools,
                                onDeleteMcpServer = settingsViewModel::deleteMcpServer
                            )
                        }
                    }
                }
            }
        }
    }
}

private val GREETING_NAME_REGEX = Regex(
    """(?:my name is|call me|i am|i'm|name:|nom:|je m'appelle|اسمي)\s+([\p{L}\p{N}][^\n,.;:!?]*)""",
    RegexOption.IGNORE_CASE
)

private fun extractPreferredUserName(facts: List<MemoryFact>): String? {
    val approved = facts.filter { it.isUserApproved }
    for (fact in approved) {
        val match = GREETING_NAME_REGEX.find(fact.content) ?: continue
        val raw = match.groupValues.getOrNull(1)?.trim()?.take(24).orEmpty()
        if (raw.isNotEmpty()) {
            return raw
        }
    }
    val explicitNameFact = approved.firstOrNull {
        it.category.equals("name", ignoreCase = true) ||
            it.category.equals("profile", ignoreCase = true)
    }
    return explicitNameFact?.content?.trim()?.takeIf { it.isNotEmpty() && !it.contains(' ') }?.take(24)
}
