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
 * Foreground service while Camera/Gmail is open so we can keep listening and
 * return to MainActivity. Does NOT use CATEGORY_CALL (that breaks wake listening).
 */
class HandoffListenService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cleanupAndStop()
                return START_NOT_STICKY
            }
            ACTION_RETURN -> {
                ensureChannels()
                startAsForeground(buildListeningNotification())
                performReturn()
                // End handoff after the activity has a chance to come forward.
                Handler(Looper.getMainLooper()).postDelayed({
                    HandoffCoordinator.endHandoff(applicationContext)
                }, 600)
                return START_NOT_STICKY
            }
            else -> {
                ensureChannels()
                startAsForeground(buildListeningNotification())
                return START_STICKY
            }
        }
    }

    private fun cleanupAndStop() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        cancelNotifications(this)
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
        // Mild heads-up only — never CATEGORY_CALL (blocks SpeechRecognizer on many OEMs).
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
            cancelNotifications(context)
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

        fun cancelNotifications(context: Context) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            mgr.cancel(NOTIFICATION_ID)
            mgr.cancel(RETURN_NOTIFICATION_ID)
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
         * High-priority heads-up to help bring the app forward.
         * Avoid CATEGORY_CALL / aggressive full-screen — they break wake-word mic
         * on many devices after returning.
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
                    },
                )
            }
            val launch = HandoffCoordinator.launchIntent(context)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
                .setContentText("بازگشت به intel tech — لمس کنید")
                .setSmallIcon(R.drawable.ic_mic_notify)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setAutoCancel(true)
                .setTimeoutAfter(3_000)
                .setSilent(true)
                .build()
            mgr.notify(RETURN_NOTIFICATION_ID, notification)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = android.app.ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                        )
                    }
                    pi.send(context, 0, null, null, null, null, options.toBundle())
                } else {
                    pi.send()
                }
            } catch (e: Exception) {
                Log.w(TAG, "return PI send failed", e)
            }
        }
    }
}
