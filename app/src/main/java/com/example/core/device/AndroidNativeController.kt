package com.example.core.device

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Process
import com.example.core.tools.AccessibilityBridge
import com.example.core.tools.AndroidTools
import com.example.core.tools.AuthorizedAccessibilityAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidNativeController(
    context: Context,
    private val androidTools: AndroidTools
) {
    private val appContext = context.applicationContext

    suspend fun executeNativeAction(request: DeviceCommandRequest): DeviceCommandResult = withContext(Dispatchers.IO) {
        when (request.actionId) {
            PrivilegedActionId.PING_RUNTIME -> {
                val uid = Process.myUid()
                val pid = Process.myPid()
                DeviceCommandResult(
                    actionId = request.actionId,
                    success = true,
                    backendUsed = DeviceControlBackend.NATIVE_ANDROID,
                    verified = true,
                    verificationEvidence = "Native app process uid=$uid pid=$pid.",
                    summary = "App process active (UID $uid, PID $pid).",
                    rawOutput = "uid=$uid|pid=$pid|priv=app"
                )
            }

            PrivilegedActionId.READ_SYSTEM_PROPERTIES -> {
                val telemetry = androidTools.readDeviceTelemetry()
                val info = "release=${telemetry.androidRelease}|sdk=${telemetry.sdkInt}|patch=${telemetry.securityPatch}|model=${telemetry.model}"
                DeviceCommandResult(
                    actionId = request.actionId,
                    success = true,
                    backendUsed = DeviceControlBackend.NATIVE_ANDROID,
                    verified = true,
                    verificationEvidence = "Read directly from android.os.Build and ActivityManager.",
                    summary = "Android ${telemetry.androidRelease} (API ${telemetry.sdkInt}, ${telemetry.socModel})",
                    rawOutput = info
                )
            }

            PrivilegedActionId.SET_MEDIA_VOLUME -> adjustVolumeNative(request.arg1.toIntOrNull())

            PrivilegedActionId.NAV_HOME -> {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val resolved = homeIntent.resolveActivity(appContext.packageManager) != null
                val launched = if (resolved) {
                    runCatching {
                        appContext.startActivity(homeIntent)
                        true
                    }.getOrDefault(false)
                } else {
                    false
                }
                if (launched) {
                    DeviceCommandResult(
                        actionId = request.actionId,
                        success = true,
                        backendUsed = DeviceControlBackend.NATIVE_ANDROID,
                        verified = true,
                        verificationEvidence = "Resolved and launched CATEGORY_HOME activity.",
                        summary = "Returned to home screen via Android CATEGORY_HOME launcher intent."
                    )
                } else {
                    DeviceCommandResult(
                        actionId = request.actionId,
                        success = false,
                        backendUsed = DeviceControlBackend.NATIVE_ANDROID,
                        verified = false,
                        verificationEvidence = "CATEGORY_HOME launcher activity could not be started.",
                        summary = "Unable to launch Home intent.",
                        errorReason = "Home intent unavailable"
                    )
                }
            }

            else -> DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = DeviceControlBackend.NATIVE_ANDROID,
                verified = false,
                verificationEvidence = "Native Android APIs do not support '${request.actionId.wireId}'.",
                summary = "${request.actionId.title} requires Shizuku, Wireless ADB, or Accessibility.",
                errorReason = "Requires elevated capability"
            )
        }
    }

    private fun adjustVolumeNative(percent: Int?): DeviceCommandResult {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return DeviceCommandResult(
                actionId = PrivilegedActionId.SET_MEDIA_VOLUME,
                success = false,
                backendUsed = DeviceControlBackend.NATIVE_ANDROID,
                verified = false,
                verificationEvidence = "AudioManager unavailable.",
                summary = "AudioManager system service unavailable.",
                errorReason = "AudioManager missing"
            )

        val targetPct = (percent ?: 50).coerceIn(0, 100)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val before = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val targetIndex = ((targetPct / 100f) * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, AudioManager.FLAG_SHOW_UI)
        val after = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val afterPct = ((after * 100f) / maxVol).toInt().coerceIn(0, 100)
        return DeviceCommandResult(
            actionId = PrivilegedActionId.SET_MEDIA_VOLUME,
            success = true,
            backendUsed = DeviceControlBackend.NATIVE_ANDROID,
            verified = true,
            verificationEvidence = "Read back STREAM_MUSIC index $after/$maxVol ($afterPct%, previously $before/$maxVol).",
            summary = "Media volume verified at $afterPct% ($after/$maxVol)."
        )
    }

    fun executeAccessibilityFallback(actionId: PrivilegedActionId): DeviceCommandResult {
        val mapped = when (actionId) {
            PrivilegedActionId.NAV_HOME -> AuthorizedAccessibilityAction.HOME
            PrivilegedActionId.NAV_BACK -> AuthorizedAccessibilityAction.BACK
            PrivilegedActionId.NAV_RECENTS -> AuthorizedAccessibilityAction.RECENTS
            else -> null
        } ?: return DeviceCommandResult(
            actionId = actionId,
            success = false,
            backendUsed = DeviceControlBackend.ACCESSIBILITY,
            verified = false,
            verificationEvidence = "Action '${actionId.wireId}' is not an Accessibility fallback action.",
            summary = "Accessibility fallback cannot execute '${actionId.title}'.",
            errorReason = "Unsupported Accessibility fallback"
        )

        val ok = AccessibilityBridge.isServiceConnected.value && AccessibilityBridge.performAuthorizedGlobalAction(mapped)
        return if (ok) {
            DeviceCommandResult(
                actionId = actionId,
                success = true,
                backendUsed = DeviceControlBackend.ACCESSIBILITY,
                verified = true,
                verificationEvidence = "AccessibilityService performGlobalAction(${mapped.actionKey}) returned true.",
                summary = "Executed ${actionId.title} via Accessibility fallback."
            )
        } else {
            DeviceCommandResult(
                actionId = actionId,
                success = false,
                backendUsed = DeviceControlBackend.ACCESSIBILITY,
                verified = false,
                verificationEvidence = "AccessibilityService not connected or performGlobalAction returned false.",
                summary = "Accessibility fallback unavailable or failed.",
                errorReason = "Accessibility service inactive"
            )
        }
    }
}
