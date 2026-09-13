package com.akbar.assistant.demo.speech

import android.content.Context
import android.media.AudioManager

/**
 * Softly mutes recognizer chimes on SYSTEM/NOTIFICATION streams.
 * Never touches RING (can crash / be blocked on some OEMs) and always restores.
 */
class RecognitionBeepSilencer(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var muted = false
    private var savedSystem = 0
    private var savedNotification = 0

    @Synchronized
    fun muteBeeps() {
        if (muted) return
        try {
            savedSystem = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
            savedNotification = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            muted = true
        } catch (_: SecurityException) {
            muted = false
        } catch (_: Exception) {
            muted = false
        }
    }

    @Synchronized
    fun restoreBeeps() {
        if (!muted) return
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, savedSystem, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotification, 0)
        } catch (_: Exception) {
            // ignore restore failures
        } finally {
            muted = false
        }
    }
}
