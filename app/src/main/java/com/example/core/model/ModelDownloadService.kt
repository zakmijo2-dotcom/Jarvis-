package com.example.core.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R

class ModelDownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading Model"
                val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: "Preparing transfer…"
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0).coerceIn(0, 100)

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("JARVIS Hub • $title")
                    .setContentText(subtitle)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(100, progress, progress == 0)
                    .build()

                if (!startForegroundSafely(notification)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            else -> {
                val fallbackNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("JARVIS Hub")
                    .setContentText("Closing download service…")
                    .setOngoing(false)
                    .build()
                startForegroundSafely(fallbackNotification)
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafely(notification: android.app.Notification): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        }.getOrDefault(false)
    }

    private fun stopForegroundCompat() {
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background Hugging Face model downloads for JARVIS"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_UPDATE = "com.example.action.DOWNLOAD_UPDATE"
        const val ACTION_STOP = "com.example.action.DOWNLOAD_STOP"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_PROGRESS = "extra_progress"
        private const val CHANNEL_ID = "jarvis_model_downloads"
        private const val NOTIFICATION_ID = 3011
    }
}
