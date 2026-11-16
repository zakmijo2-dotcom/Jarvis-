package com.example.core.vision

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R

/**
 * Foreground service hosting user-consented MediaProjection fallback capture.
 * Required by Android 10+/14+ before acquiring a MediaProjection token.
 */
class MediaProjectionForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                MediaProjectionCaptureController.clearSession()
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_jarvis_notification)
                    .setContentTitle("Jarvis Screen Vision")
                    .setContentText("On-demand visual capture active while user task runs.")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()

                val started = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    true
                }.getOrDefault(false)

                if (!started) {
                    MediaProjectionCaptureController.markLastError("Foreground mediaProjection service failed to start.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == Activity.RESULT_OK && dataIntent != null) {
                    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                    val projection = runCatching { mgr?.getMediaProjection(resultCode, dataIntent) }.getOrNull()
                    if (projection != null) {
                        MediaProjectionCaptureController.attachProjection(this, projection)
                    } else {
                        MediaProjectionCaptureController.markLastError("MediaProjection token was null or rejected.")
                        stopSelf()
                    }
                } else {
                    MediaProjectionCaptureController.markLastError("Screen capture permission not granted.")
                    stopSelf()
                }
            }

            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        MediaProjectionCaptureController.clearSession()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Vision Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active indicator when user authorizes screen capture fallback."
            }
            mgr.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.vision.ACTION_START_MEDIA_PROJECTION"
        const val ACTION_STOP = "com.example.vision.ACTION_STOP_MEDIA_PROJECTION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CHANNEL_ID = "jarvis_screen_vision_channel"
        private const val NOTIFICATION_ID = 4208

        fun startWithConsent(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, MediaProjectionForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaProjectionForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
