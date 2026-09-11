package com.akbar.assistant.demo.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.akbar.assistant.demo.AppLanguage
import java.util.Locale
import java.util.UUID

/**
 * TTS with Google engine preference, speech queue until ready, and STREAM_MUSIC audio attrs
 * so recognition-beep muting (system/notification) does not silence replies.
 */
class AssistantTts(
    context: Context,
    private val onSpeakStart: () -> Unit = {},
    private val onSpeakDone: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = TextToSpeech(appContext, this, "com.google.android.tts")
    private var ready = false
    private var pending: Pair<String, AppLanguage>? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            // Fallback to default engine if Google TTS is missing.
            tts?.shutdown()
            tts = TextToSpeech(appContext, { fallbackStatus ->
                ready = fallbackStatus == TextToSpeech.SUCCESS
                if (ready) {
                    attachListener()
                    flushPending()
                } else {
                    mainHandler.post { onSpeakDone() }
                }
            })
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
                mainHandler.post { onSpeakDone() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { onSpeakDone() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { onSpeakDone() }
            }
        })
        tts?.setSpeechRate(0.95f)
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
            // Safety: if engine never becomes ready, unblock the state machine.
            mainHandler.postDelayed({
                if (!ready) onSpeakDone()
            }, 2500)
            return
        }
        val engine = tts ?: run {
            mainHandler.post { onSpeakDone() }
            return
        }

        val preferred = if (language == AppLanguage.PERSIAN) {
            listOf(Locale("fa", "IR"), Locale("fa"), Locale.US)
        } else {
            listOf(Locale.US, Locale.ENGLISH)
        }
        var chosen = Locale.US
        for (locale in preferred) {
            val availability = engine.isLanguageAvailable(locale)
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                chosen = locale
                break
            }
        }
        engine.language = chosen

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            // Ensure audible volume on media path.
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            mainHandler.post { onSpeakDone() }
        }
    }

    fun stop() {
        pending = null
        tts?.stop()
    }

    fun shutdown() {
        pending = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
