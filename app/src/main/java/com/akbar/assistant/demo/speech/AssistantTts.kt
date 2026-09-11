package com.akbar.assistant.demo.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.akbar.assistant.demo.AppLanguage
import java.util.Locale
import java.util.UUID

class AssistantTts(
    context: Context,
    private val onSpeakStart: () -> Unit = {},
    private val onSpeakDone: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
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
    }

    fun speak(text: String, language: AppLanguage) {
        val engine = tts
        if (engine == null || !ready || text.isBlank()) {
            mainHandler.post { onSpeakDone() }
            return
        }
        val locale = if (language == AppLanguage.PERSIAN) Locale("fa", "IR") else Locale.US
        val availability = engine.isLanguageAvailable(locale)
        engine.language = if (
            availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Locale.US
        } else {
            locale
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
