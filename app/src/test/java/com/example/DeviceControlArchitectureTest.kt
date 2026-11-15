package com.example

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.core.device.AdbShellExecution
import com.example.core.device.AllowlistedCommandValidator
import com.example.core.device.AndroidNativeController
import com.example.core.device.DeviceCapabilityManager
import com.example.core.device.DeviceCommandRequest
import com.example.core.device.DeviceCommandRouter
import com.example.core.device.DeviceControlBackend
import com.example.core.device.IJarvisShizukuService
import com.example.core.device.PermissionManager
import com.example.core.device.PrivilegedActionId
import com.example.core.device.ShizukuAvailabilityState
import com.example.core.device.ShizukuController
import com.example.core.device.ShizukuGateway
import com.example.core.device.WirelessAdbConnectionState
import com.example.core.device.WirelessAdbController
import com.example.core.device.WirelessAdbSession
import com.example.core.device.WirelessAdbTransport
import com.example.core.tools.AccessibilityBridge
import com.example.core.tools.AndroidSystemTools
import com.example.core.tools.AuthorizedAccessibilityAction
import com.example.core.tools.ToolExecutionStatus
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rikka.shizuku.Shizuku

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceControlArchitectureTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `shizuku controller tracks permission request denial binder death and allowlisted execution`() = runBlocking {
        val fakeService = object : IJarvisShizukuService.Stub() {
            override fun getRuntimeInfo(): String = "OK|uid=2000|pid=4242|priv=shell"
            override fun executeAllowlistedAction(actionId: String?, arg1: String?, arg2: String?): String {
                return when (actionId) {
                    PrivilegedActionId.NAV_HOME.wireId -> "OK|Dispatched keyevent HOME (3)."
                    PrivilegedActionId.FORCE_STOP_APP.wireId ->
                        if (AllowlistedCommandValidator.isForceStopAllowedPackage(arg1.orEmpty())) {
                            "OK|Force-stopped $arg1."
                        } else {
                            "ERR|Blocked package"
                        }
                    else -> "ERR|Unsupported"
                }
            }

            override fun destroy() = Unit
        }

        val gateway = FakeShizukuGateway(context, fakeService)
        val controller = ShizukuController(context, gateway)
        controller.startLifecycleTracking()

        // 1. Installed, binder offline
        assertEquals(ShizukuAvailabilityState.INSTALLED_SERVICE_STOPPED, controller.refreshStatus().availability)

        // 2. Binder online, permission missing -> request triggers grant callback
        gateway.binderAlive = true
        gateway.permissionGranted = PackageManager.PERMISSION_DENIED
        assertEquals(ShizukuAvailabilityState.PERMISSION_REQUIRED, controller.refreshStatus().availability)

        gateway.grantOnNextRequest = true
        controller.requestPermission()
        assertTrue(controller.status.value.hasPermission)

        // 3. Execute allowlisted action via Shizuku UserService
        val res = controller.executeAllowlistedAction(DeviceCommandRequest(PrivilegedActionId.NAV_HOME))
        assertTrue(res.success)
        assertEquals(DeviceControlBackend.SHIZUKU, res.backendUsed)
        assertTrue(res.verified)

        // 4. Simulate binder disconnect/death -> cleanly drops bound service and reports offline
        gateway.simulateBinderDead()
        assertFalse(controller.status.value.isBinderAlive)
        assertEquals(ShizukuAvailabilityState.INSTALLED_SERVICE_STOPPED, controller.status.value.availability)

        // 5. Permission denied rationale path
        gateway.binderAlive = true
        gateway.permissionGranted = PackageManager.PERMISSION_DENIED
        gateway.grantOnNextRequest = false
        gateway.shouldShowRationale = true
        controller.requestPermission()
        assertEquals(ShizukuAvailabilityState.PERMISSION_DENIED, controller.status.value.availability)
    }

    @Test
    fun `wireless adb controller validates pairing redacts codes and never fakes active connection`() = runBlocking {
        val fakeTransport = FakeWirelessAdbTransport()
        val adbController = WirelessAdbController(context, fakeTransport)

        // 1. Validation rejects non-6-digit pairing code and invalid ports
        val badPort = adbController.pair("127.0.0.1", "80", "123456")
        assertTrue(badPort.isFailure)
        assertFalse(adbController.status.value.lastError.orEmpty().contains("123456"))

        val badCode = adbController.pair("127.0.0.1", "37123", "123")
        assertTrue(badCode.isFailure)
        assertEquals(WirelessAdbConnectionState.ERROR, adbController.status.value.connectionState)

        // 2. Failed pairing keeps disconnected error state with troubleshooting hint
        fakeTransport.failPairingWith = IllegalStateException("Connection refused for token 998877")
        val failedPair = adbController.pair("127.0.0.1", "37123", "998877")
        assertTrue(failedPair.isFailure)
        assertFalse(adbController.status.value.isPaired)
        assertFalse(adbController.status.value.lastError.orEmpty().contains("998877"))
        assertTrue(adbController.status.value.lastError.orEmpty().contains("••••••"))

        // 3. Successful pairing moves to PAIRED_DISCONNECTED (does NOT claim CONNECTED)
        fakeTransport.failPairingWith = null
        val okPair = adbController.pair("127.0.0.1", "37123", "654321")
        assertTrue(okPair.isSuccess)
        assertTrue(adbController.status.value.isPaired)
        assertEquals(WirelessAdbConnectionState.PAIRED_DISCONNECTED, adbController.status.value.connectionState)

        // 4. Connect fails verification probe -> NOT marked connected
        fakeTransport.failConnectProbe = true
        val probeFail = adbController.connect("127.0.0.1", "41234")
        assertTrue(probeFail.isFailure)
        assertEquals(WirelessAdbConnectionState.ERROR, adbController.status.value.connectionState)

        // 5. Connect succeeds when probe confirms identity -> executes allowlisted actions
        fakeTransport.failConnectProbe = false
        val okConnect = adbController.connect("127.0.0.1", "41234")
        assertTrue(okConnect.isSuccess)
        assertEquals(WirelessAdbConnectionState.CONNECTED, adbController.status.value.connectionState)

        val exec = adbController.executeAllowlistedAction(DeviceCommandRequest(PrivilegedActionId.NAV_HOME))
        assertTrue(exec.success)
        assertEquals(DeviceControlBackend.WIRELESS_ADB, exec.backendUsed)

        // 6. Disconnect updates state accurately
        adbController.disconnect()
        assertEquals(WirelessAdbConnectionState.PAIRED_DISCONNECTED, adbController.status.value.connectionState)
    }

    @Test
    fun `device command router enforces priority Shizuku then WirelessADB then Native then Accessibility and blocks protected packages`() = runBlocking {
        val fakeService = object : IJarvisShizukuService.Stub() {
            override fun getRuntimeInfo(): String = "OK|uid=2000|pid=101|priv=shell"
            override fun executeAllowlistedAction(actionId: String?, arg1: String?, arg2: String?): String =
                "OK|Executed $actionId via Shizuku"
            override fun destroy() = Unit
        }
        val shizukuGateway = FakeShizukuGateway(context, fakeService).apply {
            binderAlive = true
            permissionGranted = PackageManager.PERMISSION_GRANTED
        }
        val shizuku = ShizukuController(context, shizukuGateway)
        val adbTransport = FakeWirelessAdbTransport()
        val adb = WirelessAdbController(context, adbTransport)
        adb.connect("127.0.0.1", "40100")

        val native = AndroidNativeController(context, AndroidSystemTools(context))
        val permMgr = PermissionManager(context, shizuku)
        val settingsRepo = SettingsRepository(context)
        val capMgr = DeviceCapabilityManager(shizuku, adb, permMgr, settingsRepo)
        val router = DeviceCommandRouter(shizuku, adb, native, capMgr, permMgr, settingsRepo)

        // 1. Both Shizuku and Wireless ADB ready -> Shizuku wins
        val r1 = router.routeAndExecute(DeviceCommandRequest(PrivilegedActionId.NAV_HOME))
        assertTrue(r1.success)
        assertEquals(DeviceControlBackend.SHIZUKU, r1.backendUsed)

        // 2. Shizuku drops -> falls cleanly to Wireless ADB
        shizukuGateway.simulateBinderDead()
        val r2 = router.routeAndExecute(DeviceCommandRequest(PrivilegedActionId.NAV_HOME))
        assertTrue(r2.success)
        assertEquals(DeviceControlBackend.WIRELESS_ADB, r2.backendUsed)

        // 3. Wireless ADB drops -> Accessibility fallback executes NAV_BACK when service connected & enabled
        adb.disconnect()
        settingsRepo.setAccessibilityAutomationEnabled(true)
        AccessibilityBridge.setTestOverride { action ->
            action == AuthorizedAccessibilityAction.BACK
        }
        try {
            val r3 = router.routeAndExecute(DeviceCommandRequest(PrivilegedActionId.NAV_BACK))
            assertTrue(r3.success)
            assertEquals(DeviceControlBackend.ACCESSIBILITY, r3.backendUsed)
        } finally {
            AccessibilityBridge.setTestOverride(null)
            settingsRepo.setAccessibilityAutomationEnabled(false)
        }

        // 4. Dangerous FORCE_STOP_APP requires confirmation and rejects protected/injection packages
        val unconfirmed = router.routeAndExecute(
            DeviceCommandRequest(PrivilegedActionId.FORCE_STOP_APP, arg1 = "com.example.demo", userConfirmed = false)
        )
        assertFalse(unconfirmed.success)
        assertTrue(unconfirmed.requiresConfirmation)

        assertFalse(AllowlistedCommandValidator.isForceStopAllowedPackage("com.android.systemui"))
        assertFalse(AllowlistedCommandValidator.isForceStopAllowedPackage("com.demo.app; rm -rf /"))
        assertTrue(AllowlistedCommandValidator.isForceStopAllowedPackage("com.example.demoapp"))
    }

    private class FakeShizukuGateway(
        private val context: Context,
        private val service: IJarvisShizukuService.Stub
    ) : ShizukuGateway {
        var installed: Boolean = true
        var binderAlive: Boolean = false
        var permissionGranted: Int = PackageManager.PERMISSION_DENIED
        var shouldShowRationale: Boolean = false
        var grantOnNextRequest: Boolean = false

        private val binderReceived = mutableListOf<Shizuku.OnBinderReceivedListener>()
        private val binderDead = mutableListOf<Shizuku.OnBinderDeadListener>()
        private val permListeners = mutableListOf<Shizuku.OnRequestPermissionResultListener>()

        override fun isManagerInstalled(): Boolean = installed
        override fun pingBinder(): Boolean = binderAlive
        override fun getVersion(): Int = 13
        override fun isPreV11(): Boolean = false
        override fun getUid(): Int = 2000
        override fun checkSelfPermission(): Int = permissionGranted
        override fun shouldShowRequestPermissionRationale(): Boolean = shouldShowRationale

        override fun requestPermission(requestCode: Int) {
            permissionGranted = if (grantOnNextRequest) {
                PackageManager.PERMISSION_GRANTED
            } else {
                PackageManager.PERMISSION_DENIED
            }
            permListeners.forEach { it.onRequestPermissionResult(requestCode, permissionGranted) }
        }

        override fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
            binderReceived.add(listener)
        }

        override fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
            binderReceived.remove(listener)
        }

        override fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
            binderDead.add(listener)
        }

        override fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
            binderDead.remove(listener)
        }

        override fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
            permListeners.add(listener)
        }

        override fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
            permListeners.remove(listener)
        }

        override fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection) {
            connection.onServiceConnected(
                ComponentName(context, com.example.core.device.JarvisShizukuUserService::class.java),
                service.asBinder()
            )
        }

        override fun unbindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection, remove: Boolean) = Unit
        override fun openShizukuAppOrInstall(): Boolean = true

        fun simulateBinderDead() {
            binderAlive = false
            binderDead.forEach { it.onBinderDead() }
        }
    }

    private class FakeWirelessAdbTransport : WirelessAdbTransport {
        var failPairingWith: Throwable? = null
        var failConnectProbe: Boolean = false

        override suspend fun pair(host: String, pairingPort: Int, pairingCode: String): Result<Unit> {
            return failPairingWith?.let { Result.failure(it) } ?: Result.success(Unit)
        }

        override suspend fun connectAndVerify(host: String, connectPort: Int): Result<WirelessAdbSession> {
            return Result.success(object : WirelessAdbSession {
                override val host: String = host
                override val port: Int = connectPort
                private var open = true
                override fun isOpen(): Boolean = open
                override suspend fun executeArgv(argv: List<String>): AdbShellExecution {
                    if (failConnectProbe && argv.firstOrNull() == "id") {
                        return AdbShellExecution(1, "unauthorized")
                    }
                    return when (argv.firstOrNull()) {
                        "id" -> AdbShellExecution(0, "uid=2000(shell) gid=2000(shell)")
                        else -> AdbShellExecution(0, "ok")
                    }
                }
                override fun close() {
                    open = false
                }
            })
        }
    }
}
