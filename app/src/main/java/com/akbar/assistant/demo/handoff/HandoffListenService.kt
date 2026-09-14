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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.akbar.assistant.demo.R

/**
 * Foreground service while Camera/Gmail is open so we can keep the mic session
 * alive and bring MainActivity back. Listening notification stays quiet;
 * the *return* notification uses full-screen intent (v1.9.2) so voice-close
 * can actually surface the demo on Android 10+.
 */
class HandoffListenService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cleanupAndStop(cancelReturn = false)
                return START_NOT_STICKY
            }
            ACTION_RETURN -> {
                ensureChannels()
                startAsForeground(buildListeningNotification())
                performReturn()
                // Give full-screen / BAL return time to fire before tearing down.
                Handler(Looper.getMainLooper()).postDelayed({
                    HandoffCoordinator.endHandoff(applicationContext)
                }, 3_000)
                return START_STICKY
            }
            else -> {
                ensureChannels()
                startAsForeground(buildListeningNotification())
                return START_STICKY
            }
        }
    }

    private fun cleanupAndStop(cancelReturn: Boolean) {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        cancelListeningNotification(this)
        if (cancelReturn) cancelReturnNotification(this)
        stopSelf()
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                setSound(null, null)
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
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
            .setSilent(true)
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
            // Do not cancel the return notification here — it must stay until
            // the activity is actually in front (cleared from onAppForeground).
            cancelListeningNotification(context)
            try {
                context.startService(
                    Intent(context, HandoffListenService::class.java).setAction(ACTION_STOP),
                )
            } catch (_: Exception) {
            }
            try {
                context.stopService(Intent(context, HandoffListenService::class.java))
            } catch (_: Exception) {
            }
        }

        fun cancelListeningNotification(context: Context) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            mgr.cancel(NOTIFICATION_ID)
        }

        fun cancelReturnNotification(context: Context) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            mgr.cancel(RETURN_NOTIFICATION_ID)
        }

        fun cancelNotifications(context: Context) {
            cancelListeningNotification(context)
            cancelReturnNotification(context)
        }

        fun requestReturn(context: Context) {
            val intent = Intent(context, HandoffListenService::class.java).setAction(ACTION_RETURN)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        /**
         * Strongest background→foreground path (restored from v1.9.2).
         * Full-screen + CALL category. Cleared in onAppForeground before mic restarts
         * so it does not keep poisoning SpeechRecognizer after return.
         */
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
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setTimeoutAfter(8_000)
                .build()
            mgr.notify(RETURN_NOTIFICATION_ID, notification)

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
