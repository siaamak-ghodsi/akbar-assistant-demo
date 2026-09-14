package com.akbar.assistant.demo.handoff

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
import com.akbar.assistant.demo.MainActivity
import com.akbar.assistant.demo.R

/**
 * Foreground mic service so speech can keep running while Camera/Gmail is open,
 * and so returning to MainActivity is more likely to be allowed.
 */
class HandoffListenService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RETURN) {
            HandoffCoordinator.returnToDemo()
            HandoffCoordinator.endHandoff(this)
            return START_NOT_STICKY
        }
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "intel tech listening",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps listening while Camera or Gmail is open"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val returnAction = PendingIntent.getService(
            this,
            1,
            Intent(this, HandoffListenService::class.java).setAction(ACTION_RETURN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("intel tech")
            .setContentText("در حال گوش دادن — بگو جیمیل/دوربین را ببند")
            .setSmallIcon(R.drawable.ic_mic_notify)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "بازگشت", returnAction)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "intel_tech_handoff"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.akbar.assistant.demo.STOP_HANDOFF"
        const val ACTION_RETURN = "com.akbar.assistant.demo.RETURN_HANDOFF"

        fun start(context: Context) {
            val intent = Intent(context, HandoffListenService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HandoffListenService::class.java).setAction(ACTION_STOP)
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
            context.stopService(Intent(context, HandoffListenService::class.java))
        }
    }
}
