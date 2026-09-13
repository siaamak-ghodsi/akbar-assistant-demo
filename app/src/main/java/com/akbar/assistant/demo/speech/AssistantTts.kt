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
 * Spoken replies for the demo:
 * - Prefer Google TTS
 * - STREAM_MUSIC + USAGE_MEDIA (audible on speakers)
 * - Prefer any Persian (fa*) voice; if missing, notify UI to install language pack
 * - Do not fall back to English for Persian text (often silent)
 */
class AssistantTts(
    context: Context,
    private val onSpeakStart: () -> Unit = {},
    private val onSpeakDone: () -> Unit = {},
    private val onPersianVoiceMissing: () -> Unit = {},
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
    private var persianMissingNotified = false

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
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
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
        checkPersianAvailability(engine)
    }

    private fun checkPersianAvailability(engine: TextToSpeech) {
        val avail = try {
            engine.isLanguageAvailable(Locale.forLanguageTag("fa-IR"))
        } catch (_: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        val hasFaVoice = runCatching {
            engine.voices?.any { isPersianVoice(it) } == true
        }.getOrDefault(false)
        if (!hasFaVoice &&
            (avail == TextToSpeech.LANG_MISSING_DATA || avail < TextToSpeech.LANG_AVAILABLE)
        ) {
            notifyPersianMissing()
        }
    }

    private fun notifyPersianMissing() {
        if (persianMissingNotified) return
        persianMissingNotified = true
        mainHandler.post { onPersianVoiceMissing() }
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

        val persianOk = applyVoice(engine, language)
        if (language == AppLanguage.PERSIAN && !persianOk) {
            notifyPersianMissing()
        }

        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setSpeechRate(if (language == AppLanguage.PERSIAN) 0.88f else 0.94f)

        mainHandler.post { onSpeakStart() }

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

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
            if (language == AppLanguage.PERSIAN) {
                notifyPersianMissing()
                clearSafetyDone()
                abandonFocus()
                mainHandler.post { onSpeakDone() }
                return
            }
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

    /**
     * @return true if a usable voice/locale was applied for the requested language
     */
    private fun applyVoice(engine: TextToSpeech, language: AppLanguage): Boolean {
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()

        if (language == AppLanguage.PERSIAN) {
            val offlineFa = voices.firstOrNull { isPersianVoice(it) && !it.isNetworkConnectionRequired }
            val anyFa = voices.firstOrNull { isPersianVoice(it) }
            when {
                offlineFa != null -> {
                    engine.voice = offlineFa
                    return true
                }
                anyFa != null -> {
                    engine.voice = anyFa
                    return true
                }
            }
            for (locale in listOf(
                Locale.forLanguageTag("fa-IR"),
                Locale("fa", "IR"),
                Locale("fa"),
            )) {
                val code = try {
                    engine.isLanguageAvailable(locale)
                } catch (_: Exception) {
                    TextToSpeech.LANG_NOT_SUPPORTED
                }
                if (code >= TextToSpeech.LANG_AVAILABLE) {
                    try {
                        engine.language = locale
                    } catch (_: Exception) {
                    }
                    return true
                }
                // LANG_MISSING_DATA (-1) or NOT_SUPPORTED (-2): keep looking / fail
                if (code == TextToSpeech.LANG_MISSING_DATA) {
                    return false
                }
            }
            return false
        }

        val offlineEn = voices.firstOrNull {
            it.locale.language.equals("en", ignoreCase = true) && !it.isNetworkConnectionRequired
        }
        val anyEn = voices.firstOrNull {
            it.locale.language.equals("en", ignoreCase = true)
        }
        when {
            offlineEn != null -> engine.voice = offlineEn
            anyEn != null -> engine.voice = anyEn
            else -> {
                try {
                    engine.language = Locale.US
                } catch (_: Exception) {
                }
            }
        }
        return true
    }

    private fun isPersianVoice(voice: Voice): Boolean =
        voice.locale.language.equals("fa", ignoreCase = true)

    private fun prepareAudibleOutput() {
        try {
            runCatching {
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE,
                        0,
                    )
                }
            }
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (max > 0 && cur < (max * 0.25f).toInt()) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (max * 0.6f).toInt().coerceAtLeast(1),
                    0,
                )
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
                        .build(),
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
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
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
