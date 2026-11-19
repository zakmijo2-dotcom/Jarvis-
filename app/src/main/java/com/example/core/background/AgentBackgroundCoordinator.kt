package com.example.core.background

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import com.example.core.agent.AgentExecutionPhase
import com.example.core.agent.AgentTimelineEntry
import com.example.core.agent.ObservationPreviewSnapshot
import com.example.core.device.DeviceCapabilitySnapshot
import com.example.core.device.DeviceControlBackend
import com.example.data.local.db.AgentCheckpointDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class VirtualDisplayFeasibility(val label: String) {
    SUPPORTED_WITH_SHELL_CONSTRAINTS("Shell-Assisted Virtual Display Feasible (Android 14+ with OEM caveats)"),
    MAIN_DISPLAY_FOREGROUND_REQUIRED("Main Display Automation Only (Standard Android Sandbox)")
}

data class VirtualDisplayAssessment(
    val feasibility: VirtualDisplayFeasibility,
    val androidSdk: Int,
    val privilegedShellAvailable: Boolean,
    val activeBackendLabel: String,
    val technicalExplanation: String
)

data class BackgroundTaskSnapshot(
    val activePlanId: String? = null,
    val goal: String = "",
    val phase: AgentExecutionPhase = AgentExecutionPhase.IDLE,
    val phaseDetail: String = "Ready",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isDeviceLocked: Boolean = false,
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val activePackageName: String? = null,
    val lastObservationPreview: ObservationPreviewSnapshot? = null,
    val timeline: List<AgentTimelineEntry> = emptyList(),
    val recoveredInterruptedCount: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Honest runtime evaluator of Android 12-16 background/virtual display capabilities.
 * Never claims unrestricted invisible background UI control without shell-level prerequisites.
 */
object VirtualDisplayFeasibilityProbe {
    fun evaluate(
        capabilities: DeviceCapabilitySnapshot? = null,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): VirtualDisplayAssessment {
        val activeBackend = capabilities?.activePrivilegedBackend ?: DeviceControlBackend.NATIVE_ANDROID
        val hasShellPrivilege = activeBackend == DeviceControlBackend.SHIZUKU ||
            activeBackend == DeviceControlBackend.WIRELESS_ADB

        return if (sdkInt >= 34 && hasShellPrivilege) {
            VirtualDisplayAssessment(
                feasibility = VirtualDisplayFeasibility.SUPPORTED_WITH_SHELL_CONSTRAINTS,
                androidSdk = sdkInt,
                privilegedShellAvailable = true,
                activeBackendLabel = capabilities?.privilegedBridgeSummary ?: activeBackend.label,
                technicalExplanation = "Android $sdkInt + ${activeBackend.label} shell bridge detected. Shell-scoped virtual displays are technically possible on AOSP API 34+, but FLAG_SECURE screens, locked keyguards, and strict OEM window policies still apply. JARVIS defaults to transparent Foreground Service + main-display verified automation."
            )
        } else {
            val reason = when {
                !hasShellPrivilege -> "Neither Shizuku nor Wireless ADB is active; non-system Android apps cannot create interactive third-party virtual displays."
                else -> "Android SDK $sdkInt (< 34) lacks stable non-focus-stealing secondary display input isolation."
            }
            VirtualDisplayAssessment(
                feasibility = VirtualDisplayFeasibility.MAIN_DISPLAY_FOREGROUND_REQUIRED,
                androidSdk = sdkInt,
                privilegedShellAvailable = hasShellPrivilege,
                activeBackendLabel = capabilities?.privilegedBridgeSummary ?: activeBackend.label,
                technicalExplanation = "$reason JARVIS executes multi-app tasks transparently via a Foreground Service with Pause/Resume/Stop notification controls."
            )
        }
    }
}

/**
 * Process-scoped Background Phone Agent Coordinator (Stage 5).
 *
 * Bridges live autonomous agent execution, the Foreground Service notification controls
 * (Pause / Resume / Stop), Keyguard lock-state safety checks, and process-recreation
 * checkpoint recovery.
 */
class AgentBackgroundCoordinator(
    private val appContext: Context,
    private val checkpointDao: AgentCheckpointDao? = null
) {
    private val _snapshot = MutableStateFlow(BackgroundTaskSnapshot())
    val snapshot: StateFlow<BackgroundTaskSnapshot> = _snapshot.asStateFlow()

    private val pauseRequested = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)

    @Volatile
    private var activePauseCallback: (() -> Unit)? = null

    @Volatile
    private var activeResumeCallback: (() -> Unit)? = null

    @Volatile
    private var activeStopCallback: (() -> Unit)? = null

    fun bindControlCallbacks(
        onPause: () -> Unit,
        onResume: () -> Unit,
        onStop: () -> Unit
    ) {
        activePauseCallback = onPause
        activeResumeCallback = onResume
        activeStopCallback = onStop
    }

    fun isDeviceKeyguardLocked(): Boolean {
        val km = runCatching {
            appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        }.getOrNull() ?: return false
        return runCatching { km.isKeyguardLocked }.getOrDefault(false)
    }

    fun isPauseRequested(): Boolean = pauseRequested.get()
    fun isStopRequested(): Boolean = stopRequested.get()

    fun beginTask(planId: String, goal: String, totalSteps: Int) {
        pauseRequested.set(false)
        stopRequested.set(false)
        val locked = isDeviceKeyguardLocked()
        _snapshot.value = BackgroundTaskSnapshot(
            activePlanId = planId,
            goal = goal.take(180),
            phase = AgentExecutionPhase.THINKING,
            phaseDetail = if (locked) "Device locked — UI interaction gated until unlocked" else "Planning autonomous steps",
            isRunning = true,
            isPaused = false,
            isDeviceLocked = locked,
            completedSteps = 0,
            totalSteps = totalSteps.coerceAtLeast(1),
            timeline = emptyList(),
            recoveredInterruptedCount = _snapshot.value.recoveredInterruptedCount,
            updatedAtMs = System.currentTimeMillis()
        )
        AgentExecutionForegroundService.startOrUpdate(appContext, _snapshot.value)
    }

    fun updatePhase(
        phase: AgentExecutionPhase,
        detail: String,
        completedSteps: Int? = null,
        totalSteps: Int? = null,
        activePackage: String? = null
    ) {
        val current = _snapshot.value
        val updated = current.copy(
            phase = phase,
            phaseDetail = detail.take(160),
            isRunning = !phase.isTerminal && phase != AgentExecutionPhase.IDLE,
            isPaused = phase == AgentExecutionPhase.PAUSED,
            isDeviceLocked = isDeviceKeyguardLocked(),
            completedSteps = completedSteps ?: current.completedSteps,
            totalSteps = totalSteps ?: current.totalSteps,
            activePackageName = activePackage ?: current.activePackageName,
            updatedAtMs = System.currentTimeMillis()
        )
        _snapshot.value = updated
        if (updated.isRunning) {
            AgentExecutionForegroundService.startOrUpdate(appContext, updated)
        } else {
            AgentExecutionForegroundService.stopService(appContext)
        }
    }

    fun appendTimeline(entry: AgentTimelineEntry) {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            timeline = (current.timeline + entry).takeLast(40),
            updatedAtMs = System.currentTimeMillis()
        )
    }

    fun updateObservation(preview: ObservationPreviewSnapshot) {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            activePackageName = preview.packageName ?: current.activePackageName,
            lastObservationPreview = preview,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    fun requestPause() {
        if (!_snapshot.value.isRunning) return
        pauseRequested.set(true)
        activePauseCallback?.invoke()
        updatePhase(AgentExecutionPhase.PAUSED, "Paused by user — tap Resume to continue.")
    }

    fun requestResume() {
        if (!_snapshot.value.isRunning && !_snapshot.value.isPaused) return
        pauseRequested.set(false)
        activeResumeCallback?.invoke()
        updatePhase(AgentExecutionPhase.EXECUTING, "Resumed by user.")
    }

    fun requestStop() {
        stopRequested.set(true)
        pauseRequested.set(false)
        activeStopCallback?.invoke()
        updatePhase(AgentExecutionPhase.CANCELLED, "Stopped by user.")
    }

    fun finishTask(finalPhase: AgentExecutionPhase, detail: String) {
        pauseRequested.set(false)
        val current = _snapshot.value
        _snapshot.value = current.copy(
            phase = finalPhase,
            phaseDetail = detail.take(160),
            isRunning = false,
            isPaused = false,
            updatedAtMs = System.currentTimeMillis()
        )
        AgentExecutionForegroundService.stopService(appContext)
    }

    /**
     * Runs at app startup / process recreation: finds any orphaned `RUNNING` checkpoints left
     * behind by an OS process kill or app crash and marks them `INTERRUPTED_RECOVERABLE` so
     * the user can cleanly resume them from Chat / Activity History.
     */
    suspend fun recoverInterruptedCheckpoints(): Int {
        val dao = checkpointDao ?: return 0
        val recent = runCatching { dao.getRecentOnce() }.getOrDefault(emptyList())
        var recovered = 0
        val now = System.currentTimeMillis()
        for (cp in recent) {
            if (cp.status == "RUNNING") {
                runCatching {
                    dao.upsertCheckpoint(
                        cp.copy(
                            status = "INTERRUPTED_RECOVERABLE",
                            lastObservationSummary = "${cp.lastObservationSummary}\n[Process interrupted — ready to resume]".trim().take(600),
                            updatedAt = now
                        )
                    )
                    recovered++
                }
            }
        }
        if (recovered > 0) {
            _snapshot.value = _snapshot.value.copy(
                recoveredInterruptedCount = recovered,
                updatedAtMs = now
            )
        }
        return recovered
    }
}
