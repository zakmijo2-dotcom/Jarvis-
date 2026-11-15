package com.example.core.voice

import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.wakeword.WakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shared observable status for Android 14+/15+ Microphone Foreground Service lifecycle.
 */
object VoiceServiceStatus {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isAudioCaptureActive = MutableStateFlow(false)
    val isAudioCaptureActive: StateFlow<Boolean> = _isAudioCaptureActive.asStateFlow()

    private val _lastRestrictionNotice = MutableStateFlow<String?>(null)
    val lastRestrictionNotice: StateFlow<String?> = _lastRestrictionNotice.asStateFlow()

    private val _userStopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val userStopRequests: SharedFlow<Unit> = _userStopRequests.asSharedFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _pendingWakeArmFromNotification = MutableStateFlow(false)
    val pendingWakeArmFromNotification: StateFlow<Boolean> = _pendingWakeArmFromNotification.asStateFlow()

    internal fun setRunning(running: Boolean, capturing: Boolean) {
        _isRunning.value = running
        _isAudioCaptureActive.value = capturing
    }

    internal fun setRestrictionNotice(message: String?) {
        _lastRestrictionNotice.value = message
    }

    internal fun emitUserStopRequest() {
        _userStopRequests.tryEmit(Unit)
    }

    internal fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun requestWakeArmFromNotification() {
        _pendingWakeArmFromNotification.value = true
    }

    fun consumePendingWakeArm(): Boolean {
        val current = _pendingWakeArmFromNotification.value
        if (current) {
            _pendingWakeArmFromNotification.value = false
        }
        return current
    }
}

/**
 * Android 14/15/16-compliant Microphone Foreground Service (`foregroundServiceType="microphone"`).
 *
 * - Can only be started when the app is in the foreground and `RECORD_AUDIO` is granted.
 * - Displays a persistent OS notification with a single-tap "Stop Mic" kill control.
 * - Uses a single pre-allocated 512-sample ShortArray buffer for AudioRecord energy/VAD framing,
 *   never retaining PCM audio in memory or touching LLM inference threads.
 */
class JarvisVoiceService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var wakeTriggerMonitorJob: Job? = null
    private var activeWakeWordEngine: WakeWordEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MIC -> {
                VoiceServiceStatus.emitUserStopRequest()
                stopCaptureAndSelf()
                return START_NOT_STICKY
            }
            else -> {
                val hasMicPermission = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasMicPermission) {
                    VoiceServiceStatus.setRestrictionNotice(
                        "Cannot start microphone service without RECORD_AUDIO permission."
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }

                val modeLabel = intent?.getStringExtra(EXTRA_MODE_LABEL)
                    ?: "Voice console active"
                val captureStandbyFrames = intent?.getBooleanExtra(EXTRA_CAPTURE_PCM_GATE, false) ?: false

                val started = startForegroundSafely(buildNotification(modeLabel))
                if (!started) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (captureStandbyFrames) {
                    startPcmAudioGateLoop()
                } else {
                    stopPcmAudioGateLoop()
                }
                VoiceServiceStatus.setRestrictionNotice(null)
                VoiceServiceStatus.setRunning(running = true, capturing = captureStandbyFrames)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafely(notification: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (sec: SecurityException) {
            VoiceServiceStatus.setRestrictionNotice(
                "Android denied microphone foreground service: ${sec.localizedMessage ?: "missing permission"}."
            )
            false
        } catch (t: Throwable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && t is ForegroundServiceStartNotAllowedException) {
                VoiceServiceStatus.setRestrictionNotice(
                    "Android blocked starting the microphone service from the background."
                )
            } else {
                VoiceServiceStatus.setRestrictionNotice(
                    t.localizedMessage ?: "Unable to start microphone foreground service."
                )
            }
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPcmAudioGateLoop() {
        if (captureJob?.isActive == true) return
        val app = applicationContext as? com.example.JarvisApplication
        val wakeEngine = app?.container?.wakeWordEngine
        activeWakeWordEngine = wakeEngine
        startWakeTriggerMonitor(wakeEngine)

        captureJob = serviceScope.launch {
            val sampleRate = 16_000
            val frameSamples = 512
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                VoiceServiceStatus.setRunning(running = true, capturing = false)
                return@launch
            }
            val bufferBytes = maxOf(minBuf, frameSamples * 4)
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    VoiceServiceStatus.setRestrictionNotice("AudioRecord hardware failed to initialize.")
                    return@launch
                }
                val reusableFrame = ShortArray(frameSamples)
                recorder.startRecording()
                while (isActive) {
                    val read = recorder.read(reusableFrame, 0, frameSamples)
                    if (read > 0) {
                        activeWakeWordEngine?.processPcm16Frame(reusableFrame, read)
                    } else if (read < 0) {
                        break
                    }
                }
            } catch (t: Throwable) {
                VoiceServiceStatus.setRestrictionNotice(
                    "Audio gate paused: ${t.localizedMessage ?: "hardware unavailable"}"
                )
            } finally {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                VoiceServiceStatus.setRunning(running = VoiceServiceStatus.isRunning.value, capturing = false)
            }
        }
    }

    private fun startWakeTriggerMonitor(wakeEngine: WakeWordEngine?) {
        wakeTriggerMonitorJob?.cancel()
        if (wakeEngine == null) return
        wakeTriggerMonitorJob = serviceScope.launch {
            wakeEngine.triggerEvents.collect {
                if (!VoiceServiceStatus.isAppInForeground.value) {
                    postWakeTriggeredNotification()
                }
            }
        }
    }

    private fun stopPcmAudioGateLoop() {
        captureJob?.cancel()
        captureJob = null
        wakeTriggerMonitorJob?.cancel()
        wakeTriggerMonitorJob = null
    }

    private fun stopCaptureAndSelf() {
        stopPcmAudioGateLoop()
        VoiceServiceStatus.setRunning(running = false, capturing = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        VoiceServiceStatus.setRestrictionNotice(
            "Android FGS timeout reached; microphone service stopped to preserve battery."
        )
        stopCaptureAndSelf()
    }

    override fun onDestroy() {
        stopPcmAudioGateLoop()
        VoiceServiceStatus.setRunning(running = false, capturing = false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(modeLabel: String): Notification {
        ensureNotificationChannel()
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, JarvisVoiceService::class.java).apply {
            action = ACTION_STOP_MIC
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("JARVIS Microphone Active")
            .setContentText(modeLabel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPending)
            .addAction(0, "Stop Mic", stopPending)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.voice_fgs_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.voice_fgs_channel_desc)
                }
                nm?.createNotificationChannel(channel)
            }
            if (nm?.getNotificationChannel(WAKE_ALERT_CHANNEL_ID) == null) {
                val wakeChannel = NotificationChannel(
                    WAKE_ALERT_CHANNEL_ID,
                    "JARVIS Wake-Word Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High-priority alerts when \"Jarvis\" wake word fires in the background"
                }
                nm?.createNotificationChannel(wakeChannel)
            }
        }
    }

    private fun postWakeTriggeredNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) return
        }
        ensureNotificationChannel()
        VoiceServiceStatus.requestWakeArmFromNotification()
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ARM_VOICE_FROM_WAKE, true)
        }
        val openPending = PendingIntent.getActivity(
            this,
            WAKE_ALERT_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, WAKE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("JARVIS Heard Wake Word")
            .setContentText("Tap to open Voice Console and start listening")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(WAKE_ALERT_NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "jarvis_voice_microphone_fgs"
        const val WAKE_ALERT_CHANNEL_ID = "jarvis_wake_alert_channel"
        const val NOTIFICATION_ID = 4101
        const val WAKE_ALERT_NOTIFICATION_ID = 4102
        const val ACTION_START_MIC = "com.example.core.voice.START_MIC"
        const val ACTION_STOP_MIC = "com.example.core.voice.STOP_MIC"
        const val EXTRA_MODE_LABEL = "extra_mode_label"
        const val EXTRA_CAPTURE_PCM_GATE = "extra_capture_pcm_gate"
        const val EXTRA_ARM_VOICE_FROM_WAKE = "extra_arm_voice_from_wake"

        fun cancelWakeAlertNotification(context: Context) {
            runCatching {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.cancel(WAKE_ALERT_NOTIFICATION_ID)
            }
        }

        fun startForegroundMic(
            context: Context,
            modeLabel: String,
            capturePcmGate: Boolean
        ): Boolean {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                VoiceServiceStatus.setRestrictionNotice(
                    "Microphone permission denied. Grant RECORD_AUDIO before enabling active mic service."
                )
                return false
            }
            return try {
                val intent = Intent(context, JarvisVoiceService::class.java).apply {
                    action = ACTION_START_MIC
                    putExtra(EXTRA_MODE_LABEL, modeLabel)
                    putExtra(EXTRA_CAPTURE_PCM_GATE, capturePcmGate)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (t: Throwable) {
                val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && t is ForegroundServiceStartNotAllowedException) {
                    "Android restricted microphone foreground service start while app is backgrounded."
                } else {
                    t.localizedMessage ?: "Unable to start JarvisVoiceService."
                }
                VoiceServiceStatus.setRestrictionNotice(msg)
                false
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, JarvisVoiceService::class.java))
            }
            VoiceServiceStatus.setRunning(running = false, capturing = false)
        }
    }
}
