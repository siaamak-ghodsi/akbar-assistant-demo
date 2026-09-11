package com.akbar.assistant.demo.wake

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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.akbar.assistant.demo.MainActivity
import com.akbar.assistant.demo.R
import com.akbar.assistant.demo.commands.CommandParser
import com.akbar.assistant.demo.speech.ContinuousSpeechRecognizer
import java.util.Locale

/**
 * Keeps wake-word listening alive while the app is backgrounded or the phone is locked.
 *
 * Must be started while an Activity is visible (while-in-use mic permission).
 * In-app listening (ViewModel) covers the foreground case — this service is the
 * background / lock-screen path only.
 */
class WakeWordForegroundService : Service() {

    private var recognizer: ContinuousSpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var armed = false
    private var lastWakeAtMs = 0L
    private var lastStatus = "idle"
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                armed = false
                stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                armed = false
                stopListening()
                updateStatus("paused")
                startAsForeground(listening = false)
                return START_STICKY
            }
            ACTION_RESUME, ACTION_START, null -> {
                armed = true
                startAsForeground(listening = true)
                // Slight delay so Activity can release the mic first.
                mainHandler.postDelayed({
                    if (armed) startListening()
                }, 350)
                return START_STICKY
            }
            else -> {
                armed = true
                startAsForeground(listening = true)
                mainHandler.postDelayed({
                    if (armed) startListening()
                }, 350)
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        armed = false
        mainHandler.removeCallbacksAndMessages(null)
        stopListening()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startAsForeground(listening: Boolean) {
        val notification = buildNotification(listening)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startListening() {
        if (!armed) return
        if (recognizer == null) {
            recognizer = ContinuousSpeechRecognizer(
                context = applicationContext,
                onPartialResult = { text -> maybeHandleWake(text) },
                onFinalResult = { text -> maybeHandleWake(text) },
                onError = { msg ->
                    Log.w(TAG, "Wake STT error: $msg")
                    updateStatus(msg.take(40))
                },
                rmsCallback = {},
                onStatus = { status ->
                    updateStatus(status)
                }
            )
        }
        // Persian-first — this user speaks FA; EN still accepted via language preference.
        recognizer?.setPreferredLocale(Locale("fa", "IR"))
        recognizer?.start()
        updateStatus("listening")
        startAsForeground(listening = true)
    }

    private fun stopListening() {
        recognizer?.stop()
        recognizer?.destroy()
        recognizer = null
    }

    private fun maybeHandleWake(text: String) {
        if (!armed) return
        if (text.isNotBlank()) {
            updateStatus("heard: ${text.take(28)}")
        }
        if (!CommandParser.containsWakeWord(text)) return

        val now = System.currentTimeMillis()
        if (now - lastWakeAtMs < 2_500L) return
        lastWakeAtMs = now

        Log.i(TAG, "Wake detected: $text")
        armed = false
        stopListening()
        startAsForeground(listening = false)

        val language = CommandParser.wakeLanguage(text)
        val launch = Intent(applicationContext, MainActivity::class.java).apply {
            action = ACTION_WAKE_DETECTED
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(EXTRA_WAKE_TEXT, text)
            putExtra(EXTRA_WAKE_LANGUAGE, language.name)
        }

        val fullScreen = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val wakeNotification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.wake_detected_title))
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setTimeoutAfter(15_000L)
            .build()
        nm.notify(WAKE_EVENT_NOTIFICATION_ID, wakeNotification)

        try {
            startActivity(launch)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start activity on wake", e)
        }
    }

    private fun updateStatus(status: String) {
        lastStatus = status
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(listening = armed))
    }

    private fun buildNotification(listening: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (listening) {
            getString(R.string.wake_service_text) + " · " + lastStatus
        } else {
            getString(R.string.wake_service_paused_text)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.wake_service_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.wake_channel_desc)
                setShowBadge(false)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_ALERT,
                getString(R.string.wake_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.wake_alert_channel_desc)
                setShowBadge(false)
            }
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "akbar:wake-word"
        ).apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    companion object {
        private const val TAG = "AkbarWake"

        const val ACTION_START = "com.akbar.assistant.demo.wake.START"
        const val ACTION_STOP = "com.akbar.assistant.demo.wake.STOP"
        const val ACTION_PAUSE = "com.akbar.assistant.demo.wake.PAUSE"
        const val ACTION_RESUME = "com.akbar.assistant.demo.wake.RESUME"
        const val ACTION_WAKE_DETECTED = "com.akbar.assistant.demo.WAKE_DETECTED"

        const val EXTRA_WAKE_TEXT = "wake_text"
        const val EXTRA_WAKE_LANGUAGE = "wake_language"

        private const val CHANNEL_ID = "akbar_wake_word"
        private const val CHANNEL_ID_ALERT = "akbar_wake_alert"
        private const val NOTIFICATION_ID = 42
        private const val WAKE_EVENT_NOTIFICATION_ID = 43

        fun start(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
}
