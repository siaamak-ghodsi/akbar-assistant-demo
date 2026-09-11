package com.akbar.assistant.demo.speech

import android.content.Context
import android.media.AudioManager

/**
 * Mutes system/notification recognition "beep" sounds without muting media/TTS (STREAM_MUSIC).
 * SpeechRecognizer on many devices plays a tone on every startListening(); continuous loops
 * otherwise sound like constant beeping.
 */
class RecognitionBeepSilencer(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var muted = false
    private var savedSystem = 0
    private var savedNotification = 0
    private var savedRing = 0

    @Synchronized
    fun muteBeeps() {
        if (muted) return
        savedSystem = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
        savedNotification = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        savedRing = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
        // Some OEMs route the recognizer chime through ring.
        audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
        muted = true
    }

    @Synchronized
    fun restoreBeeps() {
        if (!muted) return
        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, savedSystem, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotification, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, savedRing, 0)
        muted = false
    }
}
