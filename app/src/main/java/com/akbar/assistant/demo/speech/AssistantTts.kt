package com.akbar.assistant.demo.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.akbar.assistant.demo.AppLanguage
import java.util.Locale
import java.util.UUID

class AssistantTts(
    context: Context,
    private val onReady: () -> Unit = {},
    private val onSpeakStart: () -> Unit = {},
    private val onSpeakDone: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = onSpeakStart()
                override fun onDone(utteranceId: String?) = onSpeakDone()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = onSpeakDone()
                override fun onError(utteranceId: String?, errorCode: Int) = onSpeakDone()
            })
            onReady()
        }
    }

    fun speak(text: String, language: AppLanguage) {
        val engine = tts ?: return
        if (!ready || text.isBlank()) {
            onSpeakDone()
            return
        }
        val locale = if (language == AppLanguage.PERSIAN) Locale("fa", "IR") else Locale.US
        engine.language = locale
        val available = engine.isLanguageAvailable(locale)
        if (
            available == TextToSpeech.LANG_MISSING_DATA ||
            available == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            engine.language = Locale.US
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
