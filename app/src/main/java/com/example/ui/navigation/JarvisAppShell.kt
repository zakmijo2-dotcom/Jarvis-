package com.example.ui.navigation

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.di.JarvisContainer
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.chat.HistoryDrawerContent
import com.example.ui.screens.device.DeviceDashboardScreen
import com.example.ui.screens.device.DeviceDashboardTab
import com.example.ui.screens.device.DeviceDashboardViewModel
import com.example.ui.screens.models.ModelManagerScreen
import com.example.ui.screens.models.ModelManagerViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.voice.VoiceScreen
import com.example.ui.screens.voice.VoiceViewModel
import kotlinx.coroutines.launch

@Composable
fun JarvisAppShell(
    container: JarvisContainer
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            conversationRepository = container.conversationRepository,
            modelManager = container.modelManager,
            agentCore = container.agentCore,
            aiEngine = container.aiEngine,
            settingsRepository = container.settingsRepository,
            modelBenchmarkDao = container.modelBenchmarkDao
        )
    )
    val voiceViewModel: VoiceViewModel = viewModel(
        factory = VoiceViewModel.factory(
            context = context,
            voiceEngine = container.voiceEngine,
            wakeWordEngine = container.wakeWordEngine,
            settingsRepository = container.settingsRepository,
            agentCore = container.agentCore,
            aiEngine = container.aiEngine,
            modelManager = container.modelManager,
            conversationRepository = container.conversationRepository,
            modelBenchmarkDao = container.modelBenchmarkDao
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
    val modelManagerViewModel: ModelManagerViewModel = viewModel(
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
            cloudProviderRegistry = container.cloudProviderRegistry
        )
    )
    val deviceDashboardViewModel: DeviceDashboardViewModel = viewModel(
        factory = DeviceDashboardViewModel.factory(
            androidTools = container.androidTools,
            settingsRepository = container.settingsRepository,
            context = context,
            agentCore = container.agentCore
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
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            settingsRepository = container.settingsRepository,
            conversationRepository = container.conversationRepository,
            wakeWordEngine = container.wakeWordEngine,
            localMemory = container.localMemory
        )
    )

    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by voiceViewModel.uiState.collectAsStateWithLifecycle()
    val modelState by modelManagerViewModel.uiState.collectAsStateWithLifecycle()
    val deviceState by deviceDashboardViewModel.uiState.collectAsStateWithLifecycle()
    val deviceControlsState by deviceDashboardViewModel.controlsState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun navigateToDestination(destination: TopDestination) {
        if (settingsState.settings.hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute == TopDestination.CHAT.routeQualifiedName || drawerState.isOpen,
        drawerContent = {
            HistoryDrawerContent(
                conversations = chatState.conversations,
                activeConversationId = chatState.activeConversationId,
                searchQuery = chatState.drawerSearchQuery,
                onSearchQueryChange = chatViewModel::updateSearchQuery,
                onSelectConversation = { id ->
                    chatViewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                    navigateToDestination(TopDestination.CHAT)
                },
                onCreateNewConversation = {
                    chatViewModel.createNewConversation()
                    scope.launch { drawerState.close() }
                    navigateToDestination(TopDestination.CHAT)
                },
                onRenameConversation = chatViewModel::renameConversation,
                onTogglePinned = chatViewModel::togglePinned,
                onDeleteConversation = chatViewModel::deleteConversation
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
                    if (!useRail) {
                        Column {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            )
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = NavigationBarDefaults.windowInsets
                            ) {
                                TopDestination.entries.forEach { dest ->
                                    val selected = currentRoute == dest.routeQualifiedName
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
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            TopDestination.entries.forEach { dest ->
                                val selected = currentRoute == dest.routeQualifiedName
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
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ChatRoute,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        composable<ChatRoute> {
                            ChatScreen(
                                uiState = chatState,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onNewSession = chatViewModel::createNewConversation,
                                onSendMessage = chatViewModel::sendMessage,
                                onCancelGeneration = chatViewModel::cancelGeneration,
                                onLoadActiveModel = chatViewModel::loadActiveModelNow,
                                onRunSystemSnapshot = chatViewModel::runSystemSnapshot,
                                onRunReadinessAudit = chatViewModel::runEngineReadinessAudit,
                                onNavigateToVoice = { navigateToDestination(TopDestination.VOICE) },
                                onNavigateToModels = { navigateToDestination(TopDestination.MODELS) },
                                onConfirmPendingTool = chatViewModel::confirmPendingTool,
                                onCancelPendingTool = chatViewModel::cancelPendingTool,
                                onCancelActiveTool = chatViewModel::cancelActiveTool,
                                onDismissPermissionPrompt = chatViewModel::dismissPermissionPrompt,
                                onRuntimePermissionResult = chatViewModel::onRuntimePermissionResult,
                                onOpenDeviceControls = {
                                    deviceDashboardViewModel.selectTab(DeviceDashboardTab.CONTROLS)
                                    navigateToDestination(TopDestination.DEVICE)
                                }
                            )
                        }
                        composable<VoiceRoute> {
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
                                    chatViewModel.sendMessage(transcript)
                                    navigateToDestination(TopDestination.CHAT)
                                }
                            )
                        }
                        composable<ModelsRoute> {
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
                        composable<DeviceRoute> {
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
                                onSetAccessibilityAutomationEnabled = deviceDashboardViewModel::setAccessibilityAutomationEnabled
                            )
                        }
                        composable<SettingsRoute> {
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
                                onDismissBanner = settingsViewModel::dismissBanner
                            )
                        }
                    }
                }
            }
        }
    }
}
