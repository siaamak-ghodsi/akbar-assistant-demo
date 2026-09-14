package com.akbar.assistant.demo.handoff

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.akbar.assistant.demo.MainActivity

/**
 * Brings intel tech back after Camera/Gmail handoff.
 *
 * Android 10+ blocks background activity starts. We keep an Activity-created
 * PendingIntent and, on return, try several strategies (send with BAL options,
 * startActivity from the FGS, full-screen notification intent).
 */
object HandoffCoordinator {
    private const val TAG = "HandoffCoordinator"

    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    private var returnIntent: PendingIntent? = null

    fun attachActivity(activity: Activity) {
        val launch = launchIntent(activity)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        returnIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = ActivityOptions.makeBasic().apply {
                setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
            PendingIntent.getActivity(activity, 1001, launch, flags, options.toBundle())
        } else {
            PendingIntent.getActivity(activity, 1001, launch, flags)
        }
    }

    fun startHandoff(context: Context) {
        active = true
        HandoffListenService.start(context)
    }

    fun endHandoff(context: Context) {
        active = false
        HandoffListenService.stop(context)
    }

    /**
     * Ask the foreground service to bring the demo forward.
     * Prefer this over calling [returnToDemo] directly from the ViewModel,
     * because the FGS context + notification PendingIntent are more likely
     * to be allowed to start an activity.
     */
    fun requestReturn(context: Context) {
        HandoffListenService.requestReturn(context.applicationContext)
    }

    /** Multi-strategy bring-to-front. Returns true if at least one path did not throw. */
    fun returnToDemo(context: Context): Boolean {
        var ok = false
        val appCtx = context.applicationContext

        // 1) Activity-created PendingIntent, with BAL options on the *send* side (API 34+).
        val pi = returnIntent
        if (pi != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                        )
                    }
                    pi.send(appCtx, 0, null, null, null, null, options.toBundle())
                } else {
                    pi.send()
                }
                ok = true
                Log.i(TAG, "return PendingIntent.send ok")
            } catch (e: Exception) {
                Log.w(TAG, "return PendingIntent.send failed", e)
            }
        }

        // 2) Direct startActivity (often allowed from a running FGS on many OEMs / API < 34).
        try {
            appCtx.startActivity(launchIntent(appCtx))
            ok = true
            Log.i(TAG, "return startActivity ok")
        } catch (e: Exception) {
            Log.w(TAG, "return startActivity failed", e)
        }

        // 3) Fire a high-priority / full-screen notification intent as last resort.
        try {
            HandoffListenService.fireReturnNotification(appCtx)
            ok = true
        } catch (e: Exception) {
            Log.w(TAG, "return notification failed", e)
        }

        return ok
    }

    /** @deprecated use returnToDemo(context) */
    fun returnToDemo(): Boolean {
        // Keep binary compat for older call sites; without a Context we can only try the PI.
        val pi = returnIntent ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // No context — plain send; may no-op on Android 14+.
                pi.send()
            } else {
                pi.send()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
    }
}
