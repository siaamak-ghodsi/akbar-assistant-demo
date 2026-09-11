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
import android.speech.tts.Voice
import com.akbar.assistant.demo.AppLanguage
import java.util.Locale
import java.util.UUID

/**
 * Google TTS preferred; fa-IR / en-US; audio focus; queue-until-ready.
 * Ensures assistant replies are actually audible on STREAM_MUSIC.
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

    private var tts: TextToSpeech? = TextToSpeech(appContext, this, GOOGLE_ENGINE)
    private var ready = false
    private var pending: Pair<String, AppLanguage>? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var readyWait: Runnable? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            try {
                tts?.shutdown()
            } catch (_: Exception) {
            }
            tts = TextToSpeech(appContext) { fallbackStatus ->
                ready = fallbackStatus == TextToSpeech.SUCCESS
                if (ready) {
                    configureEngine()
                    flushPending()
                } else {
                    mainHandler.post { onSpeakDone() }
                }
            }
            return
        }
        ready = true
        configureEngine()
        flushPending()
    }

    private fun configureEngine() {
        val engine = tts ?: return
        try {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        } catch (_: Exception) {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
        engine.setSpeechRate(0.95f)
        engine.setPitch(1.0f)
        // Warm language packs so the first reply is not silent.
        preferLocale(engine, Locale.forLanguageTag("fa-IR"))
        preferLocale(engine, Locale.US)
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
            readyWait?.let { mainHandler.removeCallbacks(it) }
            val wait = Runnable {
                if (!ready) onSpeakDone() else flushPending()
            }
            readyWait = wait
            mainHandler.postDelayed(wait, 3000)
            return
        }

        val engine = tts ?: run {
            mainHandler.post { onSpeakDone() }
            return
        }

        ensureAudibleMusicStream()
        requestFocus()

        val chosen = if (language == AppLanguage.PERSIAN) {
            pickLocale(
                engine,
                listOf(
                    Locale.forLanguageTag("fa-IR"),
                    Locale("fa", "IR"),
                    Locale("fa"),
                    Locale.US
                )
            )
        } else {
            pickLocale(
                engine,
                listOf(Locale.US, Locale.ENGLISH, Locale.forLanguageTag("en-US"))
            )
        }
        preferLocale(engine, chosen)
        preferVoice(engine, chosen)
        engine.setSpeechRate(if (language == AppLanguage.PERSIAN) 0.90f else 0.95f)

        // Update UI immediately even if utterance callback is delayed.
        mainHandler.post { onSpeakStart() }

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        val result = try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }

        if (result == TextToSpeech.ERROR) {
            try {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine.language = if (language == AppLanguage.PERSIAN) {
                    Locale.forLanguageTag("fa-IR")
                } else {
                    Locale.US
                }
                val retryId = UUID.randomUUID().toString()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, retryId)
                val retry = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, retryId)
                if (retry == TextToSpeech.ERROR) {
                    abandonFocus()
                    mainHandler.post { onSpeakDone() }
                }
            } catch (_: Exception) {
                abandonFocus()
                mainHandler.post { onSpeakDone() }
            }
        }
    }

    private fun pickLocale(engine: TextToSpeech, candidates: List<Locale>): Locale {
        for (locale in candidates) {
            val avail = try {
                engine.isLanguageAvailable(locale)
            } catch (_: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (avail >= TextToSpeech.LANG_AVAILABLE) return locale
        }
        return Locale.US
    }

    private fun preferLocale(engine: TextToSpeech, locale: Locale) {
        try {
            engine.language = locale
        } catch (_: Exception) {
        }
    }

    private fun preferVoice(engine: TextToSpeech, locale: Locale) {
        try {
            val voices: Set<Voice> = engine.voices ?: return
            val match = voices.firstOrNull { voice ->
                !voice.isNetworkConnectionRequired &&
                    voice.locale.language.equals(locale.language, ignoreCase = true) &&
                    (locale.country.isEmpty() ||
                        voice.locale.country.equals(locale.country, ignoreCase = true))
            } ?: voices.firstOrNull {
                it.locale.language.equals(locale.language, ignoreCase = true)
            }
            if (match != null) engine.voice = match
        } catch (_: Exception) {
        }
    }

    private fun ensureAudibleMusicStream() {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (max > 0 && cur == 0) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (max * 0.65f).toInt().coerceAtLeast(1),
                    0
                )
            }
        } catch (_: Exception) {
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
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = req
            hasFocus = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!hasFocus) {
                val mediaReq = AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = mediaReq
                hasFocus =
                    audioManager.requestAudioFocus(mediaReq) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
        hasFocus = false
        focusRequest = null
    }

    fun stop() {
        pending = null
        readyWait?.let { mainHandler.removeCallbacks(it) }
        readyWait = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        abandonFocus()
    }

    fun shutdown() {
        pending = null
        readyWait?.let { mainHandler.removeCallbacks(it) }
        readyWait = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
        abandonFocus()
    }

    companion object {
        private const val GOOGLE_ENGINE = "com.google.android.tts"
    }
}
