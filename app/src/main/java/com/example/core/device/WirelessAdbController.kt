package com.example.core.device

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable

data class AdbShellExecution(
    val exitCode: Int,
    val output: String
)

interface WirelessAdbTransport {
    suspend fun pair(host: String, pairingPort: Int, pairingCode: String): Result<Unit>
    suspend fun connectAndVerify(host: String, connectPort: Int): Result<WirelessAdbSession>
}

interface WirelessAdbSession : Closeable {
    val host: String
    val port: Int
    fun isOpen(): Boolean
    suspend fun executeArgv(argv: List<String>): AdbShellExecution
}

/**
 * Pure-Kotlin Kadb transport implementing Android 11+ Wireless Debugging (SPAKE2 + TLS 1.3).
 * Never runs unsanitized shell strings or logs pairing codes.
 */
class KadbWirelessTransport : WirelessAdbTransport {
    private fun verifyTcpReachable(host: String, port: Int, timeoutMs: Int = 2500) {
        java.net.Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
        }
    }

    override suspend fun pair(host: String, pairingPort: Int, pairingCode: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                verifyTcpReachable(host, pairingPort)
                Kadb.pair(host, pairingPort, pairingCode)
            }
        }

    override suspend fun connectAndVerify(host: String, connectPort: Int): Result<WirelessAdbSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                verifyTcpReachable(host, connectPort)
                val client = Kadb.create(host, connectPort)
                val probe = client.shell("id")
                if (probe.exitCode != 0 && probe.output.isBlank()) {
                    runCatching { client.close() }
                    error("ADB daemon did not respond to verification probe (exit=${probe.exitCode}).")
                }
                KadbSessionAdapter(host, connectPort, client)
            }
        }

    private class KadbSessionAdapter(
        override val host: String,
        override val port: Int,
        private val client: Kadb
    ) : WirelessAdbSession {
        @Volatile
        private var closed = false

        override fun isOpen(): Boolean = !closed

        override suspend fun executeArgv(argv: List<String>): AdbShellExecution = withContext(Dispatchers.IO) {
            if (closed) {
                return@withContext AdbShellExecution(-1, "ADB session closed")
            }
            val safeCmd = argv.joinToString(" ") { token -> shellEscapeSingleArg(token) }
            val response = client.shell(safeCmd)
            AdbShellExecution(response.exitCode, response.output.take(2000))
        }

        override fun close() {
            closed = true
            runCatching { client.close() }
        }

        private fun shellEscapeSingleArg(arg: String): String {
            if (arg.matches(Regex("^[a-zA-Z0-9._:/-]+$"))) return arg
            return "'" + arg.replace("'", "'\"'\"'") + "'"
        }
    }
}

/**
 * Manages Android Wireless Debugging (pairing via SPAKE2 + TLS 1.3, mDNS service discovery,
 * active TLS verification, allowlisted command dispatch, and explicit disconnect handling).
 *
 * Never stores or logs the ephemeral 6-digit pairing code and never silently assumes a dead TCP
 * port is still active without verifying daemon reachability.
 */
class WirelessAdbController(
    context: Context,
    private val transport: WirelessAdbTransport = KadbWirelessTransport(),
    private val defaultHost: String = "127.0.0.1"
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()

    private val _status = MutableStateFlow(WirelessAdbStatusSnapshot(host = defaultHost))
    val status: StateFlow<WirelessAdbStatusSnapshot> = _status.asStateFlow()

    @Volatile
    private var activeSession: WirelessAdbSession? = null

    private var nsdManager: NsdManager? = null
    private var connectDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var pairingDiscoveryListener: NsdManager.DiscoveryListener? = null

    fun restoreSavedEndpoint(host: String?, connectPort: Int?, paired: Boolean) {
        val resolvedHost = host?.trim().takeUnless { it.isNullOrEmpty() } ?: defaultHost
        val validPort = connectPort?.takeIf { it in 1..65535 }
        val state = when {
            activeSession?.isOpen() == true -> WirelessAdbConnectionState.CONNECTED
            paired -> WirelessAdbConnectionState.PAIRED_DISCONNECTED
            else -> WirelessAdbConnectionState.UNCONFIGURED
        }
        _status.value = _status.value.copy(
            host = resolvedHost,
            connectPort = validPort,
            isPaired = paired,
            connectionState = state
        )
    }

    suspend fun pair(host: String, portText: String, pairingCodeRaw: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().ifEmpty { defaultHost }
        val port = portText.trim().toIntOrNull()
        val pairingCode = pairingCodeRaw.filter { it.isDigit() }

        if (!isValidHost(cleanHost)) {
            val err = "Enter a valid local IPv4/hostname (e.g. 127.0.0.1 or 192.168.x.x)."
            updateError(err, "Check Developer Options -> Wireless debugging -> IP address & Port.")
            return@withContext Result.failure(IllegalArgumentException(err))
        }
        if (port == null || port !in 1024..65535) {
            val err = "Pairing port must be between 1024 and 65535."
            updateError(err, "Read the pairing port shown under 'Pair device with pairing code'.")
            return@withContext Result.failure(IllegalArgumentException(err))
        }
        if (pairingCode.length != 6) {
            val err = "Wireless debugging pairing code must be 6 digits."
            updateError(err, "Open 'Pair device with pairing code' and enter the active 6-digit code.")
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        _status.value = _status.value.copy(
            connectionState = WirelessAdbConnectionState.PAIRING,
            host = cleanHost,
            lastPairingPort = port,
            lastError = null,
            troubleshootingHint = null
        )

        val result = withTimeoutOrNull(12_000L) {
            transport.pair(cleanHost, port, pairingCode)
        } ?: Result.failure(IllegalStateException("Pairing handshake timed out after 12 seconds."))

        return@withContext result.fold(
            onSuccess = {
                _status.value = _status.value.copy(
                    connectionState = WirelessAdbConnectionState.PAIRED_DISCONNECTED,
                    isPaired = true,
                    host = cleanHost,
                    lastError = null,
                    troubleshootingHint = "Paired successfully. Next, enter the main Wireless Debugging port and tap Connect."
                )
                Result.success("Paired with Wireless Debugging at $cleanHost:$port.")
            },
            onFailure = { err ->
                val reason = sanitizeErrorMessage(err.localizedMessage ?: err.javaClass.simpleName)
                val hint = when {
                    reason.contains("refused", ignoreCase = true) ->
                        "Keep the 'Pair device with pairing code' dialog open while pairing; the port closes when dismissed."
                    reason.contains("timeout", ignoreCase = true) ->
                        "Ensure Wireless Debugging is enabled and the pairing port matches the live dialog."
                    else ->
                        "Verify the 6-digit pairing code has not rotated and no VPN blocks localhost/LAN traffic."
                }
                _status.value = _status.value.copy(
                    connectionState = WirelessAdbConnectionState.ERROR,
                    lastError = "Pairing failed: $reason",
                    troubleshootingHint = hint
                )
                Result.failure(err)
            }
        )
    }

    suspend fun connect(host: String, connectPortText: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().ifEmpty { defaultHost }
        val port = connectPortText.trim().toIntOrNull()

        if (!isValidHost(cleanHost)) {
            val err = "Enter a valid IPv4 address or hostname (e.g. 127.0.0.1)."
            updateError(err, "Use 127.0.0.1 when connecting from this device.")
            return@withContext Result.failure(IllegalArgumentException(err))
        }
        if (port == null || port !in 1024..65535) {
            val err = "Connection port must be between 1024 and 65535."
            updateError(err, "Check the main Wireless debugging settings screen for the current active port.")
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        sessionMutex.withLock {
            runCatching { activeSession?.close() }
            activeSession = null

            _status.value = _status.value.copy(
                connectionState = WirelessAdbConnectionState.CONNECTING,
                host = cleanHost,
                connectPort = port,
                lastError = null,
                troubleshootingHint = null
            )

            val connectRes = withTimeoutOrNull(10_000L) {
                transport.connectAndVerify(cleanHost, port)
            } ?: Result.failure(IllegalStateException("ADB TLS connect timed out after 10 seconds."))

            return@withContext connectRes.fold(
                onSuccess = { session ->
                    val probe = runCatching { session.executeArgv(listOf("id")) }.getOrNull()
                    if (probe == null || probe.exitCode != 0) {
                        runCatching { session.close() }
                        val err = "ADB TLS socket connected, but verification probe failed."
                        updateError(err, "Ensure the device authorized this app's key via Wireless Debugging pairing first.")
                        Result.failure(IllegalStateException(err))
                    } else {
                        activeSession = session
                        val identity = probe.output.trim().ifBlank { "uid=2000(shell)" }
                        _status.value = _status.value.copy(
                            connectionState = WirelessAdbConnectionState.CONNECTED,
                            isPaired = true,
                            host = cleanHost,
                            connectPort = port,
                            verifiedIdentity = identity,
                            lastVerifiedAtMs = System.currentTimeMillis(),
                            lastError = null,
                            troubleshootingHint = "Wireless ADB verified. If Shizuku is also running, JARVIS prefers Shizuku automatically."
                        )
                        Result.success("Connected & verified ($identity) at $cleanHost:$port.")
                    }
                },
                onFailure = { err ->
                    val reason = sanitizeErrorMessage(err.localizedMessage ?: err.javaClass.simpleName)
                    val hint = when {
                        reason.contains("refused", ignoreCase = true) ->
                            "Wireless debugging may be toggled off or using a different dynamic port."
                        reason.contains("SSL", ignoreCase = true) || reason.contains("TLS", ignoreCase = true) || reason.contains("EOF", ignoreCase = true) ->
                            "This device has not trusted JARVIS yet — complete the 6-digit Pairing step first."
                        else ->
                            "Toggle Wireless debugging off/on or tap Auto-Discover Ports."
                    }
                    _status.value = _status.value.copy(
                        connectionState = WirelessAdbConnectionState.ERROR,
                        verifiedIdentity = null,
                        lastError = "Connection failed: $reason",
                        troubleshootingHint = hint
                    )
                    Result.failure(err)
                }
            )
        }
    }

    suspend fun verifyActiveConnection(): Boolean = sessionMutex.withLock {
        val session = activeSession ?: run {
            markDisconnected("No active Wireless ADB socket.")
            return@withLock false
        }
        if (!session.isOpen()) {
            activeSession = null
            markDisconnected("Wireless ADB session closed.")
            return@withLock false
        }
        val probe = runCatching {
            withTimeoutOrNull(3_500L) { session.executeArgv(listOf("id")) }
        }.getOrNull()
        return@withLock if (probe != null && probe.exitCode == 0) {
            _status.value = _status.value.copy(
                connectionState = WirelessAdbConnectionState.CONNECTED,
                lastVerifiedAtMs = System.currentTimeMillis()
            )
            true
        } else {
            runCatching { session.close() }
            activeSession = null
            markDisconnected("Wireless ADB health check failed or daemon restarted.")
            false
        }
    }

    suspend fun disconnect() = sessionMutex.withLock {
        runCatching { activeSession?.close() }
        activeSession = null
        val paired = _status.value.isPaired
        _status.value = _status.value.copy(
            connectionState = if (paired) WirelessAdbConnectionState.PAIRED_DISCONNECTED else WirelessAdbConnectionState.UNCONFIGURED,
            verifiedIdentity = null,
            lastError = null
        )
    }

    suspend fun executeAllowlistedAction(request: DeviceCommandRequest): DeviceCommandResult = withContext(Dispatchers.IO) {
        if (DeviceControlBackend.WIRELESS_ADB !in request.actionId.supportedBackends) {
            return@withContext DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.WIRELESS_ADB,
                verified = false,
                verificationEvidence = "Operation '${request.actionId.wireId}' is not mapped to Wireless ADB.",
                summary = "${request.actionId.title} is not supported on Wireless ADB.",
                errorReason = "Unsupported operation on Wireless ADB backend."
            )
        }

        val session = activeSession?.takeIf { it.isOpen() } ?: run {
            markDisconnected("Wireless ADB is not connected.")
            return@withContext DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.WIRELESS_ADB,
                verified = false,
                verificationEvidence = "No verified Wireless ADB session.",
                summary = "Connect Wireless ADB first in Shizuku & Wireless ADB Setup.",
                errorReason = "Wireless ADB disconnected."
            )
        }

        val argv: List<String> = when (request.actionId) {
            PrivilegedActionId.PING_RUNTIME -> listOf("id")
            PrivilegedActionId.READ_SYSTEM_PROPERTIES ->
                listOf("getprop", "ro.build.version.release")
            PrivilegedActionId.NAV_HOME -> listOf("input", "keyevent", "3")
            PrivilegedActionId.NAV_BACK -> listOf("input", "keyevent", "4")
            PrivilegedActionId.NAV_RECENTS -> listOf("input", "keyevent", "187")
            PrivilegedActionId.FORCE_STOP_APP -> {
                val pkg = request.arg1.trim()
                if (!AllowlistedCommandValidator.isForceStopAllowedPackage(pkg)) {
                    return@withContext DeviceCommandResult(
                        actionId = request.actionId,
                        success = false,
                        backendUsed = DeviceControlBackend.WIRELESS_ADB,
                        verified = false,
                        verificationEvidence = "Package '$pkg' rejected by allowlist security filter.",
                        summary = "Blocked unsafe or protected package '$pkg'.",
                        errorReason = "Invalid or protected package name."
                    )
                }
                listOf("am", "force-stop", pkg)
            }
            PrivilegedActionId.TRIM_APP_CACHES -> {
                val mb = request.arg1.trim().toIntOrNull()?.coerceIn(64, 4096) ?: 512
                listOf("pm", "trim-caches", (mb.toLong() * 1024L * 1024L).toString())
            }
            PrivilegedActionId.INPUT_TAP -> {
                val x = request.arg1.trim().toIntOrNull()?.coerceIn(0, 8192)
                val y = request.arg2.trim().toIntOrNull()?.coerceIn(0, 8192)
                if (x == null || y == null) {
                    return@withContext DeviceCommandResult(
                        actionId = request.actionId,
                        success = false,
                        backendUsed = DeviceControlBackend.WIRELESS_ADB,
                        verified = false,
                        verificationEvidence = "Invalid tap coordinates.",
                        summary = "Blocked invalid tap coordinates.",
                        errorReason = "Invalid coordinates"
                    )
                }
                listOf("input", "tap", x.toString(), y.toString())
            }
            PrivilegedActionId.INPUT_SWIPE -> {
                val parts = request.arg1.split(",")
                val x1 = parts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(0, 8192)
                val y1 = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(0, 8192)
                val x2 = parts.getOrNull(2)?.trim()?.toIntOrNull()?.coerceIn(0, 8192)
                val y2 = parts.getOrNull(3)?.trim()?.toIntOrNull()?.coerceIn(0, 8192)
                val dur = request.arg2.trim().toIntOrNull()?.coerceIn(50, 3000) ?: 320
                if (x1 == null || y1 == null || x2 == null || y2 == null) {
                    return@withContext DeviceCommandResult(
                        actionId = request.actionId,
                        success = false,
                        backendUsed = DeviceControlBackend.WIRELESS_ADB,
                        verified = false,
                        verificationEvidence = "Invalid swipe coordinates.",
                        summary = "Blocked invalid swipe coordinates.",
                        errorReason = "Invalid coordinates"
                    )
                }
                listOf("input", "swipe", x1.toString(), y1.toString(), x2.toString(), y2.toString(), dur.toString())
            }
            PrivilegedActionId.INPUT_TEXT -> {
                val safe = AllowlistedCommandValidator.sanitizeInputText(request.arg1)
                    ?: return@withContext DeviceCommandResult(
                        actionId = request.actionId,
                        success = false,
                        backendUsed = DeviceControlBackend.WIRELESS_ADB,
                        verified = false,
                        verificationEvidence = "Text requires Accessibility input (non-ASCII or restricted punctuation).",
                        summary = "Text unsupported on ADB shell input.",
                        errorReason = "Unsupported text for shell input"
                    )
                listOf("input", "text", safe)
            }
            PrivilegedActionId.QUERY_TOP_APP ->
                listOf("dumpsys", "window", "displays")
            else -> return@withContext DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.WIRELESS_ADB,
                verified = false,
                verificationEvidence = "No allowlisted argv template for '${request.actionId.wireId}'.",
                summary = "Blocked unmapped command.",
                errorReason = "Unsupported action"
            )
        }

        val execResult = runCatching {
            withTimeoutOrNull(8_000L) { session.executeArgv(argv) }
                ?: AdbShellExecution(-1, "ADB command timed out after 8s.")
        }.getOrElse { err ->
            runCatching { session.close() }
            activeSession = null
            markDisconnected(sanitizeErrorMessage(err.localizedMessage ?: err.javaClass.simpleName))
            return@withContext DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.WIRELESS_ADB,
                verified = false,
                verificationEvidence = "Socket error during ADB execution.",
                summary = "Wireless ADB connection dropped during command.",
                errorReason = err.localizedMessage ?: err.javaClass.simpleName
            )
        }

        if (execResult.exitCode != 0) {
            return@withContext DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.WIRELESS_ADB,
                verified = false,
                verificationEvidence = "ADB exit=${execResult.exitCode}: ${execResult.output}",
                summary = "Wireless ADB command failed (exit ${execResult.exitCode}).",
                rawOutput = execResult.output,
                errorReason = execResult.output.ifBlank { "exit=${execResult.exitCode}" }
            )
        }

        // Secondary verification for force-stop
        if (request.actionId == PrivilegedActionId.FORCE_STOP_APP) {
            val pidRes = runCatching { session.executeArgv(listOf("pidof", request.arg1.trim())) }.getOrNull()
            val pidStillAlive = pidRes != null && pidRes.exitCode == 0 && pidRes.output.trim().isNotEmpty()
            if (pidStillAlive) {
                return@withContext DeviceCommandResult(
                    actionId = request.actionId,
                    success = false,
                    backendUsed = DeviceControlBackend.WIRELESS_ADB,
                    verified = false,
                    verificationEvidence = "pidof reported running PID '${pidRes?.output?.trim()}' after am force-stop.",
                    summary = "Target package '${request.arg1}' appears still running.",
                    errorReason = "Verification failed"
                )
            }
        }

        val cleanOut = execResult.output.trim().ifBlank { "exit=0" }
        DeviceCommandResult(
            actionId = request.actionId,
            success = true,
            backendUsed = DeviceControlBackend.WIRELESS_ADB,
            verified = true,
            verificationEvidence = "${request.actionId.verificationMethod} Output: $cleanOut",
            summary = "Executed via Wireless ADB (${_status.value.host}:${_status.value.connectPort}): $cleanOut",
            rawOutput = cleanOut
        )
    }

    fun startMdnsDiscovery() {
        val manager = runCatching {
            appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        }.getOrNull() ?: return

        nsdManager = manager
        stopMdnsDiscovery()
        _status.value = _status.value.copy(isMdnsDiscoveryActive = true)

        connectDiscoveryListener = createDiscoveryListener(manager, isPairing = false)
        pairingDiscoveryListener = createDiscoveryListener(manager, isPairing = true)

        runCatching {
            manager.discoverServices(MDNS_CONNECT_TYPE, NsdManager.PROTOCOL_DNS_SD, connectDiscoveryListener)
        }
        runCatching {
            manager.discoverServices(MDNS_PAIRING_TYPE, NsdManager.PROTOCOL_DNS_SD, pairingDiscoveryListener)
        }
    }

    fun stopMdnsDiscovery() {
        val manager = nsdManager
        if (manager != null) {
            connectDiscoveryListener?.let { listener ->
                runCatching { manager.stopServiceDiscovery(listener) }
            }
            pairingDiscoveryListener?.let { listener ->
                runCatching { manager.stopServiceDiscovery(listener) }
            }
        }
        connectDiscoveryListener = null
        pairingDiscoveryListener = null
        _status.value = _status.value.copy(isMdnsDiscoveryActive = false)
    }

    fun release() {
        stopMdnsDiscovery()
        runCatching { activeSession?.close() }
        activeSession = null
        scope.cancel()
    }

    private fun createDiscoveryListener(manager: NsdManager, isPairing: Boolean): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                val info = serviceInfo ?: return
                resolveNsdPort(manager, info, isPairing)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit
            override fun onDiscoveryStopped(serviceType: String?) = Unit
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNsdPort(manager: NsdManager, serviceInfo: NsdServiceInfo, isPairing: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                manager.registerServiceInfoCallback(
                    serviceInfo,
                    { r -> r.run() },
                    object : NsdManager.ServiceInfoCallback {
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                        override fun onServiceUpdated(updated: NsdServiceInfo) {
                            recordDiscoveredPort(updated.port, isPairing)
                            runCatching { manager.unregisterServiceInfoCallback(this) }
                        }
                        override fun onServiceLost() = Unit
                        override fun onServiceInfoCallbackUnregistered() = Unit
                    }
                )
            }
        } else {
            runCatching {
                manager.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) = Unit
                        override fun onServiceResolved(resolved: NsdServiceInfo?) {
                            resolved?.port?.let { recordDiscoveredPort(it, isPairing) }
                        }
                    }
                )
            }
        }
    }

    private fun recordDiscoveredPort(port: Int, isPairing: Boolean) {
        if (port !in 1024..65535) return
        val current = _status.value
        if (isPairing) {
            val next = (listOf(port) + current.discoveredPairingPorts).distinct().take(4)
            _status.value = current.copy(discoveredPairingPorts = next)
        } else {
            val next = (listOf(port) + current.discoveredConnectPorts).distinct().take(4)
            _status.value = current.copy(
                discoveredConnectPorts = next,
                connectPort = current.connectPort ?: port
            )
        }
    }

    private fun updateError(error: String, hint: String) {
        _status.value = _status.value.copy(
            connectionState = WirelessAdbConnectionState.ERROR,
            lastError = error,
            troubleshootingHint = hint
        )
    }

    private fun markDisconnected(reason: String) {
        val current = _status.value
        _status.value = current.copy(
            connectionState = if (current.isPaired) WirelessAdbConnectionState.PAIRED_DISCONNECTED else WirelessAdbConnectionState.UNCONFIGURED,
            verifiedIdentity = null,
            lastError = reason
        )
    }

    private fun isValidHost(host: String): Boolean {
        if (host.length > 64) return false
        return HOST_REGEX.matches(host)
    }

    private fun sanitizeErrorMessage(raw: String): String {
        // Never allow accidental 6-digit pairing tokens or secret-looking fragments in diagnostic text
        return raw.replace(Regex("\\b\\d{6}\\b"), "••••••").take(220)
    }

    companion object {
        private const val MDNS_PAIRING_TYPE = "_adb-tls-pairing._tcp."
        private const val MDNS_CONNECT_TYPE = "_adb-tls-connect._tcp."
        private val HOST_REGEX = Regex("^[a-zA-Z0-9.-]+$")
    }
}
