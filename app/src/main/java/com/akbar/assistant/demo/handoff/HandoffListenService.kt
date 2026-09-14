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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.akbar.assistant.demo.MainActivity
import com.akbar.assistant.demo.R

/**
 * Foreground mic service while Camera/Gmail is open.
 * Also owns the most reliable return-to-demo paths (FGS startActivity +
 * full-screen / high-priority notification PendingIntent).
 */
class HandoffListenService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RETURN -> {
                ensureChannels()
                // Keep FGS alive briefly so BAL / startActivity are more likely to work.
                startAsForeground(buildListeningNotification())
                performReturn()
                // Stop handoff after a short moment so the activity can finish coming forward.
                applicationContext.mainExecutor.execute {
                    android.os.Handler(mainLooper).postDelayed({
                        HandoffCoordinator.endHandoff(this)
                    }, 800)
                }
                return START_STICKY
            }
            else -> {
                ensureChannels()
                startAsForeground(buildListeningNotification())
                return START_STICKY
            }
        }
    }

    private fun startAsForeground(notification: Notification) {
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
    }

    private fun performReturn() {
        Log.i(TAG, "performReturn")
        HandoffCoordinator.returnToDemo(this)
        // Extra direct attempt from the Service itself.
        try {
            startActivity(HandoffCoordinator.launchIntent(this))
        } catch (e: Exception) {
            Log.w(TAG, "service startActivity failed", e)
        }
        fireReturnNotification(this)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "intel tech listening",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps listening while Camera or Gmail is open"
                setShowBadge(false)
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                RETURN_CHANNEL_ID,
                "intel tech return",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Brings intel tech back to the front"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    private fun returnActivityPendingIntent(context: Context): PendingIntent {
        val launch = HandoffCoordinator.launchIntent(context)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = android.app.ActivityOptions.makeBasic().apply {
                setPendingIntentCreatorBackgroundActivityStartMode(
                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
            PendingIntent.getActivity(context, 2002, launch, flags, options.toBundle())
        } else {
            PendingIntent.getActivity(context, 2002, launch, flags)
        }
    }

    private fun buildListeningNotification(): Notification {
        val openApp = returnActivityPendingIntent(this)
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
        private const val TAG = "HandoffListenService"
        private const val CHANNEL_ID = "intel_tech_handoff"
        private const val RETURN_CHANNEL_ID = "intel_tech_return"
        private const val NOTIFICATION_ID = 42
        private const val RETURN_NOTIFICATION_ID = 43
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

        fun requestReturn(context: Context) {
            val intent = Intent(context, HandoffListenService::class.java).setAction(ACTION_RETURN)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // May throw if FGS already running — fall back to startService.
                    try {
                        context.startForegroundService(intent)
                    } catch (_: Exception) {
                        context.startService(intent)
                    }
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "requestReturn failed", e)
                HandoffCoordinator.returnToDemo(context)
            }
        }

        /** High-priority / full-screen intent — strongest background→foreground path. */
        fun fireReturnNotification(context: Context) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        RETURN_CHANNEL_ID,
                        "intel tech return",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        setSound(null, null)
                        enableVibration(false)
                    },
                )
            }
            val launch = HandoffCoordinator.launchIntent(context)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val fullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = android.app.ActivityOptions.makeBasic().apply {
                    setPendingIntentCreatorBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }
                PendingIntent.getActivity(context, 2003, launch, flags, options.toBundle())
            } else {
                PendingIntent.getActivity(context, 2003, launch, flags)
            }
            val notification = NotificationCompat.Builder(context, RETURN_CHANNEL_ID)
                .setContentTitle("intel tech")
                .setContentText("بازگشت به intel tech")
                .setSmallIcon(R.drawable.ic_mic_notify)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setTimeoutAfter(4_000)
                .build()
            mgr.notify(RETURN_NOTIFICATION_ID, notification)

            // Also try sending the notification PendingIntent directly.
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = android.app.ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                        )
                    }
                    fullScreen.send(context, 0, null, null, null, null, options.toBundle())
                } else {
                    fullScreen.send()
                }
            } catch (e: Exception) {
                Log.w(TAG, "fullScreen send failed", e)
            }
        }
    }
}
