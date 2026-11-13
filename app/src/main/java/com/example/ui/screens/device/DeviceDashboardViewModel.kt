package com.example.ui.screens.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.tools.AndroidTools
import com.example.core.tools.DeviceTelemetry
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DeviceTelemetryUiState {
    data object Loading : DeviceTelemetryUiState
    data class Ready(
        val telemetry: DeviceTelemetry,
        val pollingIntervalSeconds: Int,
        val autoPollingEnabled: Boolean
    ) : DeviceTelemetryUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDashboardViewModel(
    private val androidTools: AndroidTools,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val autoPollingEnabled = MutableStateFlow(true)
    private val manualRefreshTick = MutableStateFlow(0)

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

    fun refreshNow() {
        viewModelScope.launch {
            manualRefreshTick.value += 1
        }
    }

    fun toggleAutoPolling() {
        autoPollingEnabled.value = !autoPollingEnabled.value
    }

    companion object {
        fun factory(
            androidTools: AndroidTools,
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DeviceDashboardViewModel(
                    androidTools = androidTools,
                    settingsRepository = settingsRepository
                ) as T
            }
        }
    }
}
