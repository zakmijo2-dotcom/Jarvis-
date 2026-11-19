package com.example.core.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.JarvisApplication
import com.example.MainActivity
import com.example.R

/**
 * Foreground Service keeping autonomous multi-app phone workflows alive when JARVIS
 * switches to third-party apps, with direct Pause / Resume / Stop notification controls.
 *
 * Uses `FOREGROUND_SERVICE_TYPE_DATA_SYNC` for Google Play policy compliance on Android 14-16.
 */
class AgentExecutionForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val coordinator = (application as? JarvisApplication)?.container?.backgroundCoordinator

        when (intent?.action) {
            ACTION_PAUSE -> {
                coordinator?.requestPause()
                val snap = coordinator?.snapshot?.value ?: BackgroundTaskSnapshot()
                postForeground(buildNotification(snap))
                return START_NOT_STICKY
            }

            ACTION_RESUME -> {
                coordinator?.requestResume()
                val snap = coordinator?.snapshot?.value ?: BackgroundTaskSnapshot()
                postForeground(buildNotification(snap))
                return START_NOT_STICKY
            }

            ACTION_STOP -> {
                coordinator?.requestStop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                val snap = coordinator?.snapshot?.value ?: BackgroundTaskSnapshot(
                    goal = intent?.getStringExtra(EXTRA_GOAL).orEmpty(),
                    phaseDetail = intent?.getStringExtra(EXTRA_DETAIL) ?: "Autonomous task active",
                    isRunning = true
                )
                postForeground(buildNotification(snap))
                return START_NOT_STICKY
            }
        }
    }

    private fun postForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(snapshot: BackgroundTaskSnapshot): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseOrResumeAction = if (snapshot.isPaused) {
            val resumeIntent = Intent(this, AgentExecutionForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            val pending = PendingIntent.getService(
                this,
                101,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(android.R.drawable.ic_media_play, "Resume", pending).build()
        } else {
            val pauseIntent = Intent(this, AgentExecutionForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pending = PendingIntent.getService(
                this,
                102,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(android.R.drawable.ic_media_pause, "Pause", pending).build()
        }

        val stopIntent = Intent(this, AgentExecutionForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            103,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPending
        ).build()

        val title = "JARVIS • ${snapshot.phase.label}"
        val content = buildString {
            if (snapshot.goal.isNotBlank()) {
                append(snapshot.goal.take(64))
                append(" — ")
            }
            append(snapshot.phaseDetail.take(80))
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(
                snapshot.totalSteps.coerceAtLeast(1),
                snapshot.completedSteps.coerceIn(0, snapshot.totalSteps.coerceAtLeast(1)),
                snapshot.totalSteps <= 1
            )
            .addAction(pauseOrResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "JARVIS Autonomous Agent",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live status and Pause/Resume/Stop controls for active autonomous tasks."
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "jarvis_agent_execution"
        const val NOTIFICATION_ID = 4108
        const val ACTION_START_UPDATE = "com.example.jarvis.ACTION_AGENT_UPDATE"
        const val ACTION_PAUSE = "com.example.jarvis.ACTION_AGENT_PAUSE"
        const val ACTION_RESUME = "com.example.jarvis.ACTION_AGENT_RESUME"
        const val ACTION_STOP = "com.example.jarvis.ACTION_AGENT_STOP"
        private const val EXTRA_GOAL = "extra_goal"
        private const val EXTRA_DETAIL = "extra_detail"

        fun startOrUpdate(context: Context, snapshot: BackgroundTaskSnapshot) {
            runCatching {
                val intent = Intent(context, AgentExecutionForegroundService::class.java).apply {
                    action = ACTION_START_UPDATE
                    putExtra(EXTRA_GOAL, snapshot.goal)
                    putExtra(EXTRA_DETAIL, snapshot.phaseDetail)
                }
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stopService(context: Context) {
            runCatching {
                context.stopService(Intent(context, AgentExecutionForegroundService::class.java))
            }
        }
    }
}
