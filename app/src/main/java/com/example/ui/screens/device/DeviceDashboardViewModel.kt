package com.example.ui.screens.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.agent.AgentCore
import com.example.core.tools.AccessibilityBridge
import com.example.core.tools.AndroidTools
import com.example.core.tools.CapturedNotificationItem
import com.example.core.tools.DeviceTelemetry
import com.example.core.tools.NotificationBridge
import com.example.core.tools.PendingToolConfirmation
import com.example.core.tools.SpecialAccessType
import com.example.core.tools.ToolCallRequest
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolExecutionStatus
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DeviceDashboardTab(val label: String) {
    TELEMETRY("Telemetry"),
    CONTROLS("Device Controls")
}

sealed interface DeviceTelemetryUiState {
    data object Loading : DeviceTelemetryUiState
    data class Ready(
        val telemetry: DeviceTelemetry,
        val pollingIntervalSeconds: Int,
        val autoPollingEnabled: Boolean
    ) : DeviceTelemetryUiState
}

data class DeviceControlsState(
    val activeTab: DeviceDashboardTab = DeviceDashboardTab.TELEMETRY,
    val settings: JarvisSettings = JarvisSettings(),
    val isNotificationAuthorized: Boolean = false,
    val isNotificationConnected: Boolean = false,
    val capturedNotifications: List<CapturedNotificationItem> = emptyList(),
    val isAccessibilityAuthorized: Boolean = false,
    val isAccessibilityConnected: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val registeredToolCount: Int = 0,
    val activeToolName: String? = null,
    val pendingConfirmation: PendingToolConfirmation? = null,
    val pendingRuntimePermission: String? = null,
    val pendingSpecialAccess: SpecialAccessType? = null,
    val permissionRationale: String? = null,
    val lastResult: ToolExecutionResult? = null,
    val executionLog: List<ToolExecutionResult> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDashboardViewModel(
    private val androidTools: AndroidTools,
    private val settingsRepository: SettingsRepository,
    private val appContext: Context? = null,
    private val agentCore: AgentCore? = null
) : ViewModel() {

    private val autoPollingEnabled = MutableStateFlow(true)
    private val manualRefreshTick = MutableStateFlow(0)
    private val activeTab = MutableStateFlow(DeviceDashboardTab.TELEMETRY)
    private val activeToolName = MutableStateFlow<String?>(null)
    private val pendingConfirmation = MutableStateFlow<PendingToolConfirmation?>(null)
    private val pendingRuntimePermission = MutableStateFlow<String?>(null)
    private val pendingSpecialAccess = MutableStateFlow<SpecialAccessType?>(null)
    private val permissionRationale = MutableStateFlow<String?>(null)
    private val lastResult = MutableStateFlow<ToolExecutionResult?>(null)
    private val executionLog = MutableStateFlow<List<ToolExecutionResult>>(emptyList())
    private val authRefreshTick = MutableStateFlow(0)

    private var activeToolJob: Job? = null
    private var deferredRequest: ToolCallRequest? = null

    private val telemetryFlow = combine(
        settingsRepository.settingsFlow,
        autoPollingEnabled,
        manualRefreshTick
    ) { settings, autoEnabled, _ ->
        settings.telemetryPollingSeconds to autoEnabled
    }.flatMapLatest { (intervalSeconds, autoEnabled) ->
        flow {
            emit(
                DeviceTelemetryUiState.Ready(
                    telemetry = androidTools.readDeviceTelemetry(),
                    pollingIntervalSeconds = intervalSeconds,
                    autoPollingEnabled = autoEnabled
                )
            )
            while (autoEnabled) {
                delay(intervalSeconds * 1000L)
                emit(
                    DeviceTelemetryUiState.Ready(
                        telemetry = androidTools.readDeviceTelemetry(),
                        pollingIntervalSeconds = intervalSeconds,
                        autoPollingEnabled = true
                    )
                )
            }
        }
    }

    val uiState: StateFlow<DeviceTelemetryUiState> = telemetryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceTelemetryUiState.Loading
    )

    private val controlsBaseFlow = combine(
        activeTab,
        settingsRepository.settingsFlow,
        NotificationBridge.notifications,
        NotificationBridge.isServiceConnected,
        AccessibilityBridge.isServiceConnected,
        authRefreshTick
    ) { values ->
        val tab = values[0] as DeviceDashboardTab
        val settings = values[1] as JarvisSettings
        @Suppress("UNCHECKED_CAST")
        val notifications = values[2] as List<CapturedNotificationItem>
        val notifConnected = values[3] as Boolean
        val a11yConnected = values[4] as Boolean

        NotificationBridge.syncPrivacyControls(
            enabled = settings.notificationReaderEnabled,
            maskEnabled = settings.notificationPrivacyMaskEnabled
        )

        val ctx = appContext
        val notifAuth = ctx?.let { NotificationBridge.isListenerAuthorized(it) } ?: false
        val a11yAuth = ctx?.let { AccessibilityBridge.isAccessibilityAuthorized(it) } ?: false
        val contactsGranted = ctx?.let {
            ContextCompat.checkSelfPermission(it, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        } ?: false

        DeviceControlsState(
            activeTab = tab,
            settings = settings,
            isNotificationAuthorized = notifAuth,
            isNotificationConnected = notifConnected,
            capturedNotifications = notifications,
            isAccessibilityAuthorized = a11yAuth,
            isAccessibilityConnected = a11yConnected,
            hasContactsPermission = contactsGranted,
            registeredToolCount = agentCore?.listRegisteredTools()?.size ?: 0
        )
    }

    val controlsState: StateFlow<DeviceControlsState> = combine(
        controlsBaseFlow,
        activeToolName,
        pendingConfirmation,
        pendingRuntimePermission,
        pendingSpecialAccess,
        permissionRationale,
        lastResult,
        executionLog
    ) { values ->
        val base = values[0] as DeviceControlsState
        @Suppress("UNCHECKED_CAST")
        val log = values[7] as List<ToolExecutionResult>
        base.copy(
            activeToolName = values[1] as String?,
            pendingConfirmation = values[2] as PendingToolConfirmation?,
            pendingRuntimePermission = values[3] as String?,
            pendingSpecialAccess = values[4] as SpecialAccessType?,
            permissionRationale = values[5] as String?,
            lastResult = values[6] as ToolExecutionResult?,
            executionLog = log
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceControlsState()
    )

    fun refreshNow() {
        viewModelScope.launch {
            manualRefreshTick.value += 1
            authRefreshTick.value += 1
        }
    }

    fun refreshAuthorizationStatus() {
        authRefreshTick.value += 1
    }

    fun toggleAutoPolling() {
        autoPollingEnabled.value = !autoPollingEnabled.value
    }

    fun selectTab(tab: DeviceDashboardTab) {
        activeTab.value = tab
        authRefreshTick.value += 1
    }

    fun setNotificationReaderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationReaderEnabled(enabled)
            authRefreshTick.value += 1
        }
    }

    fun setNotificationPrivacyMaskEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationPrivacyMaskEnabled(enabled)
        }
    }

    fun clearNotificationBuffer() {
        NotificationBridge.clearBuffer()
    }

    fun setAccessibilityAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAccessibilityAutomationEnabled(enabled)
            authRefreshTick.value += 1
        }
    }

    fun executeDeviceTool(
        toolName: String,
        args: Map<String, String> = emptyMap(),
        userConfirmed: Boolean = false
    ) {
        val core = agentCore ?: return
        val request = ToolCallRequest(
            toolName = toolName,
            arguments = args,
            source = "Device Controls"
        )
        activeToolJob = viewModelScope.launch {
            dispatchTool(core, request, userConfirmed)
        }
    }

    fun confirmPendingTool() {
        val pending = pendingConfirmation.value ?: return
        val core = agentCore ?: return
        pendingConfirmation.value = null
        activeToolJob = viewModelScope.launch {
            dispatchTool(core, pending.request, userConfirmed = true)
        }
    }

    fun cancelPendingTool() {
        val pending = pendingConfirmation.value ?: return
        pendingConfirmation.value = null
        val cancelledResult = ToolExecutionResult(
            toolName = pending.request.toolName,
            status = ToolExecutionStatus.CANCELLED,
            summary = "Action '${pending.request.toolName}' cancelled by user confirmation prompt."
        )
        lastResult.value = cancelledResult
        executionLog.value = (listOf(cancelledResult) + executionLog.value).take(15)
    }

    fun cancelActiveTool() {
        val activeName = activeToolName.value
        agentCore?.cancelActiveTool()
        activeToolJob?.cancel()
        activeToolName.value = null
        if (activeName != null) {
            val cancelledResult = ToolExecutionResult(
                toolName = activeName,
                status = ToolExecutionStatus.CANCELLED,
                summary = "Execution of '$activeName' cancelled."
            )
            lastResult.value = cancelledResult
            executionLog.value = (listOf(cancelledResult) + executionLog.value).take(15)
        }
    }

    fun dismissPermissionPrompt() {
        deferredRequest = null
        pendingRuntimePermission.value = null
        pendingSpecialAccess.value = null
        permissionRationale.value = null
    }

    fun onRuntimePermissionResult(permission: String, granted: Boolean) {
        val deferred = deferredRequest
        dismissPermissionPrompt()
        authRefreshTick.value += 1
        if (deferred != null && granted) {
            val core = agentCore ?: return
            activeToolJob = viewModelScope.launch {
                dispatchTool(core, deferred, userConfirmed = false)
            }
        } else if (!granted) {
            val deniedResult = ToolExecutionResult(
                toolName = deferred?.toolName ?: "permission",
                status = ToolExecutionStatus.PERMISSION_DENIED,
                summary = "Permission $permission denied by user."
            )
            lastResult.value = deniedResult
            executionLog.value = (listOf(deniedResult) + executionLog.value).take(15)
        }
    }

    private suspend fun dispatchTool(
        core: AgentCore,
        request: ToolCallRequest,
        userConfirmed: Boolean
    ) {
        activeToolName.value = request.toolName
        val result = try {
            core.executeTool(
                request = request,
                settings = controlsState.value.settings,
                userConfirmed = userConfirmed
            )
        } catch (_: CancellationException) {
            ToolExecutionResult(
                toolName = request.toolName,
                status = ToolExecutionStatus.CANCELLED,
                summary = "Tool execution '${request.toolName}' cancelled."
            )
        } finally {
            activeToolName.value = null
            authRefreshTick.value += 1
        }

        if (result.status == ToolExecutionStatus.CONFIRMATION_REQUIRED) {
            pendingConfirmation.value = core.describeConfirmation(request)
            return
        }

        if (result.status == ToolExecutionStatus.PERMISSION_DENIED &&
            (result.missingRuntimePermission != null || result.missingSpecialAccess != null)
        ) {
            deferredRequest = request
            pendingRuntimePermission.value = result.missingRuntimePermission
            pendingSpecialAccess.value = result.missingSpecialAccess
            permissionRationale.value = result.summary
        }

        lastResult.value = result
        executionLog.value = (listOf(result) + executionLog.value).take(15)
    }

    companion object {
        fun factory(
            androidTools: AndroidTools,
            settingsRepository: SettingsRepository,
            context: Context? = null,
            agentCore: AgentCore? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DeviceDashboardViewModel(
                    androidTools = androidTools,
                    settingsRepository = settingsRepository,
                    appContext = context?.applicationContext,
                    agentCore = agentCore
                ) as T
            }
        }
    }
}
