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
 * Reliable spoken replies for the demo:
 * - Prefer Google TTS engine
 * - Always play on STREAM_MUSIC with USAGE_MEDIA (audible on speakers)
 * - Request transient audio focus
 * - Unmute / raise music volume if needed
 * - Queue speech until the engine is ready
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
    private var safetyDone: Runnable? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            try {
                tts?.shutdown()
            } catch (_: Exception) {
            }
            // Device default engine fallback
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
        // USAGE_MEDIA + STREAM_MUSIC is the most reliable audible path on phones.
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post { onSpeakStart() }
            }

            override fun onDone(utteranceId: String?) {
                clearSafetyDone()
                abandonFocus()
                mainHandler.post { onSpeakDone() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                clearSafetyDone()
                abandonFocus()
                mainHandler.post { onSpeakDone() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                clearSafetyDone()
                abandonFocus()
                mainHandler.post { onSpeakDone() }
            }
        })
        engine.setSpeechRate(0.94f)
        engine.setPitch(1.0f)
        // Warm locales; ignore missing-data results here.
        engine.setLanguage(Locale.forLanguageTag("fa-IR"))
        engine.setLanguage(Locale.US)
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
            mainHandler.postDelayed(wait, 3500)
            return
        }

        val engine = tts ?: run {
            mainHandler.post { onSpeakDone() }
            return
        }

        prepareAudibleOutput()
        requestFocus()

        val locales = if (language == AppLanguage.PERSIAN) {
            listOf(
                Locale.forLanguageTag("fa-IR"),
                Locale("fa", "IR"),
                Locale("fa"),
                Locale.US
            )
        } else {
            listOf(Locale.US, Locale.ENGLISH, Locale.forLanguageTag("en-US"))
        }
        var chosen = Locale.US
        for (locale in locales) {
            val code = try {
                engine.isLanguageAvailable(locale)
            } catch (_: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (code >= TextToSpeech.LANG_AVAILABLE) {
                chosen = locale
                break
            }
        }
        try {
            engine.language = chosen
        } catch (_: Exception) {
        }
        // Prefer an offline voice when possible.
        try {
            val voices = engine.voices
            if (voices != null) {
                val match = voices.firstOrNull { voice ->
                    !voice.isNetworkConnectionRequired &&
                        voice.locale.language.equals(chosen.language, ignoreCase = true)
                } ?: voices.firstOrNull {
                    it.locale.language.equals(chosen.language, ignoreCase = true)
                }
                if (match != null) engine.voice = match
            }
        } catch (_: Exception) {
        }
        engine.setSpeechRate(if (language == AppLanguage.PERSIAN) 0.88f else 0.94f)

        // Ensure media attributes right before speaking (some OEMs reset them).
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )

        mainHandler.post { onSpeakStart() }

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        // Safety: if utterance callbacks never fire, still resume listening.
        clearSafetyDone()
        val estimatedMs = (text.length * 70L).coerceIn(2500L, 20000L)
        val safety = Runnable {
            abandonFocus()
            onSpeakDone()
        }
        safetyDone = safety
        mainHandler.postDelayed(safety, estimatedMs)

        val result = try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }

        if (result == TextToSpeech.ERROR) {
            // Last-resort retry on default locale / media stream.
            try {
                engine.language = Locale.US
                val retryId = UUID.randomUUID().toString()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, retryId)
                val retry = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, retryId)
                if (retry == TextToSpeech.ERROR) {
                    clearSafetyDone()
                    abandonFocus()
                    mainHandler.post { onSpeakDone() }
                }
            } catch (_: Exception) {
                clearSafetyDone()
                abandonFocus()
                mainHandler.post { onSpeakDone() }
            }
        }
    }

    private fun prepareAudibleOutput() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE,
                        0
                    )
                }
            }
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (max > 0 && cur < (max * 0.25f).toInt()) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (max * 0.6f).toInt().coerceAtLeast(1),
                    0
                )
            }
            // Exit silent ringer mode does not mute media, but some OEMs couple them.
            @Suppress("DEPRECATION")
            if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                // Do not change ringer; media should still play. Volume raise above is enough.
            }
        } catch (_: Exception) {
        }
    }

    private fun requestFocus() {
        if (hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .setAcceptsDelayedFocusGain(false)
                .build()
            focusRequest = req
            hasFocus = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            hasFocus = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
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

    private fun clearSafetyDone() {
        safetyDone?.let { mainHandler.removeCallbacks(it) }
        safetyDone = null
    }

    fun stop() {
        pending = null
        readyWait?.let { mainHandler.removeCallbacks(it) }
        readyWait = null
        clearSafetyDone()
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
        clearSafetyDone()
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
