package com.akbar.assistant.demo.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.akbar.assistant.demo.AppLanguage
import java.util.Locale
import java.util.UUID

/**
 * TTS with Google engine preference, queue-until-ready, assistant audio attributes,
 * and transient audio focus so replies are actually heard.
 */
class AssistantTts(
    context: Context,
    private val onSpeakStart: () -> Unit = {},
    private val onSpeakDone: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = TextToSpeech(appContext, this, "com.google.android.tts")
    private var ready = false
    private var pending: Pair<String, AppLanguage>? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            tts?.shutdown()
            tts = TextToSpeech(appContext) { fallbackStatus ->
                ready = fallbackStatus == TextToSpeech.SUCCESS
                if (ready) {
                    attachListener()
                    flushPending()
                } else {
                    mainHandler.post { onSpeakDone() }
                }
            }
            return
        }
        attachListener()
        flushPending()
    }

    private fun attachListener() {
        tts?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post { onSpeakStart() }
            }

            override fun onDone(utteranceId: String?) {
                abandonFocus()
                mainHandler.post { onSpeakDone() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                abandonFocus()
                mainHandler.post { onSpeakDone() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                abandonFocus()
                mainHandler.post { onSpeakDone() }
            }
        })
        tts?.setSpeechRate(0.96f)
        tts?.setPitch(1.0f)
    }

    private fun flushPending() {
        val next = pending ?: return
        pending = null
        speak(next.first, next.second)
    }

    fun speak(text: String, language: AppLanguage) {
        if (text.isBlank()) {
            mainHandler.post { onSpeakDone() }
            return
        }
        if (!ready || tts == null) {
            pending = text to language
            mainHandler.postDelayed({
                if (!ready) onSpeakDone()
            }, 2500)
            return
        }
        val engine = tts ?: run {
            mainHandler.post { onSpeakDone() }
            return
        }

        requestFocus()

        val preferred = if (language == AppLanguage.PERSIAN) {
            listOf(Locale("fa", "IR"), Locale("fa"), Locale.US)
        } else {
            listOf(Locale.US, Locale.ENGLISH)
        }
        var chosen = Locale.US
        for (locale in preferred) {
            if (engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                chosen = locale
                break
            }
        }
        engine.language = chosen

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            abandonFocus()
            mainHandler.post { onSpeakDone() }
        }
    }

    private fun requestFocus() {
        if (hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = req
            hasFocus = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            hasFocus = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        hasFocus = false
        focusRequest = null
    }

    fun stop() {
        pending = null
        tts?.stop()
        abandonFocus()
    }

    fun shutdown() {
        pending = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        abandonFocus()
    }
}
