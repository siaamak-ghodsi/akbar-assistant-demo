package com.akbar.assistant.demo.speech

import android.content.Context
import android.media.AudioManager

/**
 * Softly mutes only the notification stream for recognition chimes.
 * Avoid muting RING/SYSTEM — on some OEMs that breaks SpeechRecognizer audio.
 */
class RecognitionBeepSilencer(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var muted = false
    private var savedNotification = 0

    @Synchronized
    fun muteBeeps() {
        if (muted) return
        savedNotification = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
        muted = true
    }

    @Synchronized
    fun restoreBeeps() {
        if (!muted) return
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotification, 0)
        muted = false
    }
}
