package com.example.core.device

import android.os.Process
import kotlin.system.exitProcess

/**
 * Bound inside the Shizuku privileged host process via AIDL.
 * Strictly executes only structured, allowlisted commands using explicit argv arrays
 * (never unrestricted shell string evaluation) and never assumes root privileges.
 */
class JarvisShizukuUserService : IJarvisShizukuService.Stub() {

    override fun getRuntimeInfo(): String {
        val uid = Process.myUid()
        val pid = Process.myPid()
        val privilege = when (uid) {
            0 -> "root"
            2000 -> "shell"
            else -> "uid_$uid"
        }
        return "OK|uid=$uid|pid=$pid|priv=$privilege"
    }

    override fun executeAllowlistedAction(actionId: String?, arg1: String?, arg2: String?): String {
        val cleanAction = actionId.orEmpty().trim()
        val cleanArg1 = arg1.orEmpty().trim()
        val cleanArg2 = arg2.orEmpty().trim()

        return when (cleanAction) {
            PrivilegedActionId.PING_RUNTIME.wireId -> getRuntimeInfo()

            PrivilegedActionId.READ_SYSTEM_PROPERTIES.wireId -> {
                val release = runArgv(listOf("/system/bin/getprop", "ro.build.version.release")).getOrDefault("")
                val sdk = runArgv(listOf("/system/bin/getprop", "ro.build.version.sdk")).getOrDefault("")
                val patch = runArgv(listOf("/system/bin/getprop", "ro.build.version.security_patch")).getOrDefault("")
                val model = runArgv(listOf("/system/bin/getprop", "ro.product.model")).getOrDefault("")
                "OK|release=${release.trim()}|sdk=${sdk.trim()}|patch=${patch.trim()}|model=${model.trim()}"
            }

            PrivilegedActionId.NAV_HOME.wireId ->
                execKeyEvent(keyCode = 3, label = "HOME")

            PrivilegedActionId.NAV_BACK.wireId ->
                execKeyEvent(keyCode = 4, label = "BACK")

            PrivilegedActionId.NAV_RECENTS.wireId ->
                execKeyEvent(keyCode = 187, label = "RECENTS")

            PrivilegedActionId.FORCE_STOP_APP.wireId -> {
                val pkg = cleanArg1
                if (!AllowlistedCommandValidator.isForceStopAllowedPackage(pkg)) {
                    return "ERR|Blocked package '$pkg': fails strict Android package validation or is a protected system package."
                }
                val stopRes = runArgvWithCode(listOf("/system/bin/am", "force-stop", pkg))
                if (stopRes.first != 0) {
                    return "ERR|am force-stop returned exit=${stopRes.first}: ${stopRes.second}"
                }
                val pidCheck = runArgvWithCode(listOf("/system/bin/pidof", pkg))
                val stillRunning = pidCheck.first == 0 && pidCheck.second.trim().isNotEmpty()
                if (stillRunning) {
                    "ERR|Process for '$pkg' still reported running (pid=${pidCheck.second.trim()})."
                } else {
                    "OK|Force-stopped $pkg and verified no active process."
                }
            }

            PrivilegedActionId.TRIM_APP_CACHES.wireId -> {
                val mb = cleanArg1.toIntOrNull()?.coerceIn(64, 4096) ?: 512
                val targetBytes = mb.toLong() * 1024L * 1024L
                val res = runArgvWithCode(listOf("/system/bin/pm", "trim-caches", targetBytes.toString()))
                if (res.first == 0) {
                    "OK|Requested PackageManager cache trim threshold ${mb}MB. ${res.second.trim()}"
                } else {
                    "ERR|pm trim-caches failed (exit=${res.first}): ${res.second.trim()}"
                }
            }

            else -> "ERR|Unsupported allowlisted Shizuku action: '$cleanAction'."
        }
    }

    override fun destroy() {
        exitProcess(0)
    }

    private fun execKeyEvent(keyCode: Int, label: String): String {
        val (code, output) = runArgvWithCode(listOf("/system/bin/input", "keyevent", keyCode.toString()))
        return if (code == 0) {
            "OK|Dispatched keyevent $label ($keyCode)."
        } else {
            "ERR|input keyevent $keyCode failed (exit=$code): ${output.trim()}"
        }
    }

    private fun runArgv(argv: List<String>): Result<String> {
        val (code, output) = runArgvWithCode(argv)
        return if (code == 0) Result.success(output) else Result.failure(IllegalStateException(output))
    }

    private fun runArgvWithCode(argv: List<String>): Pair<Int, String> {
        return runCatching {
            val process = ProcessBuilder(argv)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            code to out.take(1500)
        }.getOrElse { err ->
            -1 to (err.localizedMessage ?: err.javaClass.simpleName)
        }
    }
}

object AllowlistedCommandValidator {
    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    private val PROTECTED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.providers.settings",
        "com.google.android.gms",
        "moe.shizuku.privileged.api",
        "com.aistudio.jarvis.k8v4mz"
    )

    fun isValidPackageName(packageName: String): Boolean =
        PACKAGE_NAME_REGEX.matches(packageName.trim()) && packageName.length <= 128

    fun isForceStopAllowedPackage(packageName: String): Boolean {
        val trimmed = packageName.trim()
        if (!isValidPackageName(trimmed)) return false
        return trimmed !in PROTECTED_PACKAGES && !trimmed.startsWith("com.android.internal")
    }
}
