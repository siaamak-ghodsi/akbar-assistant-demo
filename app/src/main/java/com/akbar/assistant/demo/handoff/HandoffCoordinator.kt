package com.akbar.assistant.demo.handoff

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.akbar.assistant.demo.MainActivity

/**
 * Keeps a return-to-demo PendingIntent created while MainActivity is visible.
 * Android 10+ blocks starting activities from the background; a PendingIntent
 * created by the Activity (with background-start allowed) is the reliable path.
 */
object HandoffCoordinator {
    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    private var returnIntent: PendingIntent? = null

    fun attachActivity(activity: Activity) {
        val launch = Intent(activity, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
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

    /** Bring intel tech back without force-stopping the other app. */
    fun returnToDemo(): Boolean {
        val pi = returnIntent
        if (pi != null) {
            return try {
                pi.send()
                true
            } catch (_: Exception) {
                false
            }
        }
        return false
    }
}
