package com.example.ui.screens.bridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.device.DeviceCapabilityManager
import com.example.core.device.DeviceCapabilitySnapshot
import com.example.core.device.DeviceControlBackend
import com.example.core.device.ShizukuController
import com.example.core.device.ShizukuStatusSnapshot
import com.example.core.device.WirelessAdbConnectionState
import com.example.core.device.WirelessAdbController
import com.example.core.device.WirelessAdbStatusSnapshot
import com.example.core.security.SecurityLogRedactor
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeviceBridgeSetupUiState(
    val shizuku: ShizukuStatusSnapshot = ShizukuStatusSnapshot(),
    val wirelessAdb: WirelessAdbStatusSnapshot = WirelessAdbStatusSnapshot(),
    val capabilities: DeviceCapabilitySnapshot = DeviceCapabilitySnapshot(),
    val hostInput: String = "127.0.0.1",
    val pairingPortInput: String = "",
    val pairingCodeInput: String = "",
    val connectPortInput: String = "",
    val isPairingBusy: Boolean = false,
    val isConnectingBusy: Boolean = false,
    val feedbackBanner: String? = null,
    val feedbackIsError: Boolean = false
) {
    val activePreferredRoute: String
        get() = when {
            shizuku.isBinderAlive && shizuku.hasPermission -> DeviceControlBackend.SHIZUKU.label
            wirelessAdb.connectionState == WirelessAdbConnectionState.CONNECTED -> DeviceControlBackend.WIRELESS_ADB.label
            else -> DeviceControlBackend.NATIVE_ANDROID.label
        }
}

class DeviceBridgeSetupViewModel(
    private val shizukuController: ShizukuController,
    private val wirelessAdbController: WirelessAdbController,
    private val deviceCapabilityManager: DeviceCapabilityManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val hostInput = MutableStateFlow("127.0.0.1")
    private val pairingPortInput = MutableStateFlow("")
    private val pairingCodeInput = MutableStateFlow("")
    private val connectPortInput = MutableStateFlow("")
    private val isPairingBusy = MutableStateFlow(false)
    private val isConnectingBusy = MutableStateFlow(false)
    private val feedbackBanner = MutableStateFlow<String?>(null)
    private val feedbackIsError = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val saved = runCatching { settingsRepository.settingsFlow.first() }.getOrNull()
            if (saved != null) {
                if (saved.wirelessAdbHost.isNotBlank()) {
                    hostInput.value = saved.wirelessAdbHost
                }
                if (saved.wirelessAdbConnectPort > 0) {
                    connectPortInput.value = saved.wirelessAdbConnectPort.toString()
                }
            }
        }
    }

    val uiState: StateFlow<DeviceBridgeSetupUiState> = combine(
        shizukuController.status,
        wirelessAdbController.status,
        deviceCapabilityManager.capabilities,
        hostInput,
        pairingPortInput,
        pairingCodeInput,
        connectPortInput,
        isPairingBusy,
        isConnectingBusy,
        feedbackBanner,
        feedbackIsError
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        DeviceBridgeSetupUiState(
            shizuku = values[0] as ShizukuStatusSnapshot,
            wirelessAdb = values[1] as WirelessAdbStatusSnapshot,
            capabilities = values[2] as DeviceCapabilitySnapshot,
            hostInput = values[3] as String,
            pairingPortInput = values[4] as String,
            pairingCodeInput = values[5] as String,
            connectPortInput = values[6] as String,
            isPairingBusy = values[7] as Boolean,
            isConnectingBusy = values[8] as Boolean,
            feedbackBanner = values[9] as String?,
            feedbackIsError = values[10] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DeviceBridgeSetupUiState()
    )

    fun refreshAll() {
        deviceCapabilityManager.refreshAll()
        val liveAdbPort = wirelessAdbController.status.value.connectPort
        if (connectPortInput.value.isBlank() && liveAdbPort != null) {
            connectPortInput.value = liveAdbPort.toString()
        }
    }

    fun requestShizukuPermission() {
        val snapshot = shizukuController.requestPermission()
        snapshot.lastError?.let { err ->
            feedbackBanner.value = SecurityLogRedactor.redact(err)
            feedbackIsError.value = true
        }
    }

    fun bindShizukuService() {
        viewModelScope.launch {
            val service = shizukuController.ensureUserServiceBound()
            if (service != null) {
                feedbackBanner.value = "Shizuku privileged helper service bound and verified."
                feedbackIsError.value = false
            } else {
                feedbackBanner.value = SecurityLogRedactor.redact(
                    shizukuController.status.value.lastError
                        ?: "Shizuku UserService could not bind. Confirm Shizuku is running and authorized."
                )
                feedbackIsError.value = true
            }
        }
    }

    fun openShizukuManagerApp() {
        val launched = shizukuController.openShizukuManager()
        if (!launched) {
            feedbackBanner.value = "Unable to open Shizuku app or download page."
            feedbackIsError.value = true
        }
    }

    fun updateHost(host: String) {
        hostInput.value = host.take(64)
    }

    fun updatePairingPort(port: String) {
        pairingPortInput.value = port.filter { it.isDigit() }.take(5)
    }

    fun updatePairingCode(code: String) {
        // Ephemeral in-memory entry only; never persisted or logged
        pairingCodeInput.value = code.filter { it.isDigit() }.take(6)
    }

    fun updateConnectPort(port: String) {
        connectPortInput.value = port.filter { it.isDigit() }.take(5)
    }

    fun selectDiscoveredPairingPort(port: Int) {
        pairingPortInput.value = port.toString()
    }

    fun selectDiscoveredConnectPort(port: Int) {
        connectPortInput.value = port.toString()
    }

    fun toggleMdnsDiscovery() {
        val active = wirelessAdbController.status.value.isMdnsDiscoveryActive
        if (active) {
            wirelessAdbController.stopMdnsDiscovery()
        } else {
            wirelessAdbController.startMdnsDiscovery()
        }
    }

    fun pairDevice() {
        val host = hostInput.value.trim().ifBlank { "127.0.0.1" }
        val port = pairingPortInput.value.trim()
        val code = pairingCodeInput.value.trim()
        if (isPairingBusy.value) return

        viewModelScope.launch {
            isPairingBusy.value = true
            val result = wirelessAdbController.pair(host, port, code)
            // Always wipe ephemeral pairing code immediately after handshake attempt
            pairingCodeInput.value = ""
            result.fold(
                onSuccess = { msg ->
                    val existingPort = connectPortInput.value.toIntOrNull()
                        ?: wirelessAdbController.status.value.connectPort
                        ?: 0
                    settingsRepository.saveWirelessAdbEndpoint(
                        host = host,
                        port = existingPort,
                        paired = true
                    )
                    feedbackBanner.value = msg
                    feedbackIsError.value = false
                },
                onFailure = { err ->
                    val msg = SecurityLogRedactor.redact(err.localizedMessage ?: "Pairing failed.")
                    feedbackBanner.value = msg
                    feedbackIsError.value = true
                }
            )
            isPairingBusy.value = false
        }
    }

    fun connectAndVerify() {
        val host = hostInput.value.trim().ifBlank { "127.0.0.1" }
        val port = connectPortInput.value.trim()
        if (isConnectingBusy.value) return

        viewModelScope.launch {
            isConnectingBusy.value = true
            val result = wirelessAdbController.connect(host, port)
            result.fold(
                onSuccess = { msg ->
                    settingsRepository.saveWirelessAdbEndpoint(
                        host = host,
                        port = port.toIntOrNull() ?: 0,
                        paired = true
                    )
                    feedbackBanner.value = msg
                    feedbackIsError.value = false
                },
                onFailure = { err ->
                    val msg = SecurityLogRedactor.redact(err.localizedMessage ?: "Wireless ADB connection failed.")
                    feedbackBanner.value = msg
                    feedbackIsError.value = true
                }
            )
            isConnectingBusy.value = false
        }
    }

    fun verifyLiveConnection() {
        if (isConnectingBusy.value) return
        viewModelScope.launch {
            isConnectingBusy.value = true
            val alive = wirelessAdbController.verifyActiveConnection()
            feedbackBanner.value = if (alive) {
                "Wireless ADB live TLS session verified active."
            } else {
                "Wireless ADB verification failed — reconnect using current Developer Options port."
            }
            feedbackIsError.value = !alive
            isConnectingBusy.value = false
        }
    }

    fun disconnectAdb() {
        viewModelScope.launch {
            wirelessAdbController.disconnect()
            feedbackBanner.value = "Wireless ADB session disconnected."
            feedbackIsError.value = false
        }
    }

    fun clearSavedWirelessAdb() {
        viewModelScope.launch {
            wirelessAdbController.disconnect()
            pairingCodeInput.value = ""
            pairingPortInput.value = ""
            connectPortInput.value = ""
            settingsRepository.saveWirelessAdbEndpoint(host = "127.0.0.1", port = 0, paired = false)
            wirelessAdbController.restoreSavedEndpoint(host = "127.0.0.1", connectPort = null, paired = false)
            feedbackBanner.value = "Cleared saved Wireless ADB host endpoint."
            feedbackIsError.value = false
        }
    }

    fun dismissBanner() {
        feedbackBanner.value = null
    }

    override fun onCleared() {
        wirelessAdbController.stopMdnsDiscovery()
        super.onCleared()
    }

    companion object {
        fun factory(
            shizukuController: ShizukuController,
            wirelessAdbController: WirelessAdbController,
            deviceCapabilityManager: DeviceCapabilityManager,
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DeviceBridgeSetupViewModel(
                    shizukuController = shizukuController,
                    wirelessAdbController = wirelessAdbController,
                    deviceCapabilityManager = deviceCapabilityManager,
                    settingsRepository = settingsRepository
                ) as T
            }
        }
    }
}
