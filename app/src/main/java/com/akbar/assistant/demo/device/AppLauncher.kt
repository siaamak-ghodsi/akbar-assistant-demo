package com.akbar.assistant.demo.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.akbar.assistant.demo.handoff.HandoffCoordinator

/**
 * Opens/closes external apps for the smart-board demo.
 *
 * Android does not allow killing other apps. "Close" brings our demo
 * back to the foreground via a PendingIntent created while we were visible.
 */
class AppLauncher(private val context: Context) {

    enum class Result {
        OPENED,
        CLOSED,
        NOT_INSTALLED,
        ERROR,
    }

    fun openCamera(): Result {
        val intents = listOf(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent("android.media.action.IMAGE_CAPTURE"),
        )
        for (intent in intents) {
            if (launch(intent)) return Result.OPENED
        }
        // Last resort: open any installed camera package.
        val packages = listOf(
            "com.google.android.GoogleCamera",
            "com.android.camera",
            "com.android.camera2",
            "com.sec.android.app.camera",
            "com.huawei.camera",
            "com.xiaomi.camera",
        )
        for (pkg in packages) {
            if (launchPackage(pkg)) return Result.OPENED
        }
        return Result.NOT_INSTALLED
    }

    fun closeCamera(): Result = bringDemoToFront()

    fun openGmail(): Result {
        if (launchPackage("com.google.android.gm")) return Result.OPENED
        // Fallback: mailto opens Gmail chooser / default mail app.
        val mailto = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (launch(mailto)) Result.OPENED else Result.NOT_INSTALLED
    }

    fun closeGmail(): Result = bringDemoToFront()

    private fun bringDemoToFront(): Result {
        // Prefer the Activity-created PendingIntent (works from background on Android 10+).
        if (HandoffCoordinator.returnToDemo()) {
            return Result.CLOSED
        }
        return try {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                launch.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                context.startActivity(launch)
                Result.CLOSED
            } else {
                val home = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(home)
                Result.CLOSED
            }
        } catch (_: Exception) {
            Result.ERROR
        }
    }

    private fun launchPackage(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(intent)
    }

    private fun launch(intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null &&
                intent.`package` == null
            ) {
                // Still try — some OEMs resolve only at startActivity time.
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
