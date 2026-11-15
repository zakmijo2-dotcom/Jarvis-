package com.example.core.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.DeadObjectException
import android.os.IBinder
import com.example.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

interface ShizukuGateway {
    fun isManagerInstalled(): Boolean
    fun pingBinder(): Boolean
    fun getVersion(): Int
    fun isPreV11(): Boolean
    fun getUid(): Int
    fun checkSelfPermission(): Int
    fun shouldShowRequestPermissionRationale(): Boolean
    fun requestPermission(requestCode: Int)
    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener)
    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener)
    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener)
    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener)
    fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener)
    fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener)
    fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection)
    fun unbindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection, remove: Boolean)
    fun openShizukuAppOrInstall(): Boolean
}

class RealShizukuGateway(context: Context) : ShizukuGateway {
    private val appContext = context.applicationContext

    override fun isManagerInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(SHIZUKU_MANAGER_PACKAGE, 0)
        true
    }.getOrDefault(false)

    override fun pingBinder(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    override fun getVersion(): Int = runCatching { Shizuku.getVersion() }.getOrDefault(-1)

    override fun isPreV11(): Boolean = runCatching { Shizuku.isPreV11() }.getOrDefault(false)

    override fun getUid(): Int = runCatching { Shizuku.getUid() }.getOrDefault(-1)

    override fun checkSelfPermission(): Int = runCatching {
        Shizuku.checkSelfPermission()
    }.getOrDefault(PackageManager.PERMISSION_DENIED)

    override fun shouldShowRequestPermissionRationale(): Boolean = runCatching {
        Shizuku.shouldShowRequestPermissionRationale()
    }.getOrDefault(false)

    override fun requestPermission(requestCode: Int) {
        Shizuku.requestPermission(requestCode)
    }

    override fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        Shizuku.addBinderReceivedListenerSticky(listener)
    }

    override fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        runCatching { Shizuku.removeBinderReceivedListener(listener) }
    }

    override fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        Shizuku.addBinderDeadListener(listener)
    }

    override fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        runCatching { Shizuku.removeBinderDeadListener(listener) }
    }

    override fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    override fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    override fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection) {
        Shizuku.bindUserService(args, connection)
    }

    override fun unbindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection, remove: Boolean) {
        runCatching { Shizuku.unbindUserService(args, connection, remove) }
    }

    override fun openShizukuAppOrInstall(): Boolean {
        val launch = appContext.packageManager.getLaunchIntentForPackage(SHIZUKU_MANAGER_PACKAGE)
            ?: Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://shizuku.rikka.app/")
            )
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(launch)
            true
        }.getOrDefault(false)
    }

    companion object {
        const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.privileged.api"
    }
}

class ShizukuController(
    context: Context,
    private val gateway: ShizukuGateway = RealShizukuGateway(context)
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bindMutex = Mutex()

    private val _status = MutableStateFlow(ShizukuStatusSnapshot())
    val status: StateFlow<ShizukuStatusSnapshot> = _status.asStateFlow()

    @Volatile
    private var remoteService: IJarvisShizukuService? = null

    @Volatile
    private var pendingBindDeferred: CompletableDeferred<IJarvisShizukuService?>? = null

    @Volatile
    private var registered = false

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext, JarvisShizukuUserService::class.java))
            .daemon(false)
            .processNameSuffix("jarvis_shizuku")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val iface = if (binder != null && binder.isBinderAlive) {
                IJarvisShizukuService.Stub.asInterface(binder)
            } else {
                null
            }
            remoteService = iface
            pendingBindDeferred?.complete(iface)
            refreshStatus(clearError = iface != null)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
            pendingBindDeferred?.complete(null)
            refreshStatus(overrideError = "Shizuku privileged helper disconnected; will rebind on next use.")
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus(clearError = true)
        if (hasValidPermission()) {
            scope.launch { ensureUserServiceBound() }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        remoteService = null
        pendingBindDeferred?.complete(null)
        pendingBindDeferred = null
        refreshStatus(overrideError = "Shizuku binder disconnected or service restarted.")
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            if (granted) {
                refreshStatus(clearError = true)
                scope.launch { ensureUserServiceBound() }
            } else {
                refreshStatus(overrideError = "Shizuku permission was denied by user.")
            }
        }
    }

    fun startLifecycleTracking() {
        if (!registered) {
            registered = true
            gateway.addBinderReceivedListener(binderReceivedListener)
            gateway.addBinderDeadListener(binderDeadListener)
            gateway.addRequestPermissionResultListener(permissionResultListener)
        }
        refreshStatus()
    }

    fun stopLifecycleTracking() {
        if (registered) {
            gateway.removeBinderReceivedListener(binderReceivedListener)
            gateway.removeBinderDeadListener(binderDeadListener)
            gateway.removeRequestPermissionResultListener(permissionResultListener)
            registered = false
        }
        runCatching { gateway.unbindUserService(userServiceArgs, serviceConnection, true) }
        remoteService = null
        scope.cancel()
    }

    fun refreshStatus(overrideError: String? = null, clearError: Boolean = false): ShizukuStatusSnapshot {
        val installed = gateway.isManagerInstalled()
        val binderAlive = gateway.pingBinder()
        val version = if (binderAlive) gateway.getVersion() else -1
        val preV11 = if (binderAlive) gateway.isPreV11() else false
        val uid = if (binderAlive) gateway.getUid() else -1
        val granted = binderAlive && !preV11 && gateway.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        val showRationale = binderAlive && !preV11 && !granted && gateway.shouldShowRequestPermissionRationale()
        val bound = remoteService?.asBinder()?.isBinderAlive == true

        val availability = when {
            !installed -> ShizukuAvailabilityState.NOT_INSTALLED
            !binderAlive -> ShizukuAvailabilityState.INSTALLED_SERVICE_STOPPED
            preV11 || version in 0..10 -> ShizukuAvailabilityState.INSTALLED_SERVICE_STOPPED
            !granted && showRationale -> ShizukuAvailabilityState.PERMISSION_DENIED
            !granted -> ShizukuAvailabilityState.PERMISSION_REQUIRED
            bound -> ShizukuAvailabilityState.READY
            else -> ShizukuAvailabilityState.CONNECTED_UNBOUND
        }

        val currentError = when {
            overrideError != null -> overrideError
            clearError -> null
            preV11 -> "Shizuku API v11+ is required on this device."
            else -> _status.value.lastError
        }

        val snapshot = ShizukuStatusSnapshot(
            isInstalled = installed,
            isBinderAlive = binderAlive,
            hasPermission = granted,
            shouldShowRationale = showRationale,
            isUserServiceBound = bound,
            shizukuVersion = version,
            serverUid = uid,
            isRootBacked = uid == 0,
            availability = availability,
            lastError = currentError,
            updatedAtMs = System.currentTimeMillis()
        )
        _status.value = snapshot
        return snapshot
    }

    fun hasValidPermission(): Boolean {
        return gateway.pingBinder() &&
            !gateway.isPreV11() &&
            gateway.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission(): ShizukuStatusSnapshot {
        val current = refreshStatus()
        if (!current.isInstalled) {
            return refreshStatus(overrideError = "Install Shizuku first, then start its service via Wireless Debugging.")
        }
        if (!current.isBinderAlive) {
            return refreshStatus(overrideError = "Start the Shizuku service in the Shizuku app before requesting permission.")
        }
        if (current.hasPermission) {
            scope.launch { ensureUserServiceBound() }
            return current
        }
        runCatching {
            gateway.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }.onFailure { err ->
            refreshStatus(overrideError = "Unable to show Shizuku permission dialog: ${err.localizedMessage ?: err.javaClass.simpleName}")
        }
        return _status.value
    }

    fun openShizukuManager(): Boolean = gateway.openShizukuAppOrInstall()

    suspend fun ensureUserServiceBound(): IJarvisShizukuService? = withContext(Dispatchers.IO) {
        remoteService?.takeIf { it.asBinder().isBinderAlive }?.let { return@withContext it }

        val st = refreshStatus()
        if (!st.isBinderAlive || !st.hasPermission) {
            return@withContext null
        }

        bindMutex.withLock {
            remoteService?.takeIf { it.asBinder().isBinderAlive }?.let { return@withLock it }
            val deferred = CompletableDeferred<IJarvisShizukuService?>()
            pendingBindDeferred = deferred
            runCatching {
                gateway.bindUserService(userServiceArgs, serviceConnection)
            }.onFailure { err ->
                deferred.complete(null)
                refreshStatus(overrideError = "Failed binding Shizuku UserService: ${err.localizedMessage ?: err.javaClass.simpleName}")
            }
            val result = withTimeoutOrNull(4_000L) { deferred.await() }
            if (pendingBindDeferred === deferred) {
                pendingBindDeferred = null
            }
            refreshStatus(clearError = result != null)
            result
        }
    }

    suspend fun executeAllowlistedAction(request: DeviceCommandRequest): DeviceCommandResult = withContext(Dispatchers.IO) {
        if (DeviceControlBackend.SHIZUKU !in request.actionId.supportedBackends) {
            return@withContext DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.SHIZUKU,
                verified = false,
                verificationEvidence = "Operation '${request.actionId.wireId}' does not support Shizuku.",
                summary = "${request.actionId.title} is not a Shizuku operation.",
                errorReason = "Unsupported operation on Shizuku backend."
            )
        }

        val st = refreshStatus()
        if (!st.isBinderAlive) {
            return@withContext DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.SHIZUKU,
                verified = false,
                verificationEvidence = "Shizuku binder is offline.",
                summary = "Shizuku service is not running.",
                errorReason = st.availability.label
            )
        }
        if (!st.hasPermission) {
            return@withContext DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.SHIZUKU,
                verified = false,
                verificationEvidence = "Shizuku permission missing.",
                summary = "Grant Shizuku authorization to JARVIS first.",
                errorReason = "Shizuku permission denied or not granted."
            )
        }

        val service = ensureUserServiceBound()
            ?: return@withContext DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.SHIZUKU,
                verified = false,
                verificationEvidence = "Shizuku UserService failed to bind within 4s.",
                summary = "Could not bind JARVIS Shizuku helper service.",
                errorReason = _status.value.lastError ?: "UserService bind timeout"
            )

        val rawResponse = try {
            withTimeoutOrNull(8_000L) {
                service.executeAllowlistedAction(
                    request.actionId.wireId,
                    request.arg1,
                    request.arg2
                )
            } ?: "ERR|Shizuku execution timed out after 8s."
        } catch (_: DeadObjectException) {
            remoteService = null
            refreshStatus(overrideError = "Shizuku process died during execution; rebind required.")
            return@withContext DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.SHIZUKU,
                verified = false,
                verificationEvidence = "Remote Shizuku binder died unexpectedly (DeadObjectException).",
                summary = "Shizuku connection dropped during command execution.",
                errorReason = "Shizuku binder disconnected."
            )
        } catch (t: Throwable) {
            val msg = t.localizedMessage ?: t.javaClass.simpleName
            refreshStatus(overrideError = msg)
            return@withContext DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.SHIZUKU,
                verified = false,
                verificationEvidence = "Exception invoking Shizuku UserService: $msg",
                summary = "Shizuku command failed: $msg",
                errorReason = msg
            )
        }

        if (rawResponse.startsWith("OK|")) {
            val payload = rawResponse.removePrefix("OK|")
            DeviceCommandResult(
                actionId = request.actionId,
                success = true,
                backendUsed = DeviceControlBackend.SHIZUKU,
                verified = true,
                verificationEvidence = "${request.actionId.verificationMethod} Output: $payload",
                summary = "Executed via Shizuku (${if (st.isRootBacked) "UID 0" else "ADB Shell"}): $payload",
                rawOutput = payload
            )
        } else {
            val err = rawResponse.removePrefix("ERR|").ifBlank { rawResponse }
            DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.SHIZUKU,
                verified = false,
                verificationEvidence = "Shizuku reported failure: $err",
                summary = "Shizuku rejected or failed operation: $err",
                rawOutput = rawResponse,
                errorReason = err
            )
        }
    }

    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1301
    }
}
