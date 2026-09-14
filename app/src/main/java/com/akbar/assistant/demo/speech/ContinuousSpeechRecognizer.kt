package com.akbar.assistant.demo.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Continuous SpeechRecognizer loop with beep silencing and bilingual wake support.
 * While waiting for the wake word, locales alternate fa-IR / en-US so both
 * «هی اکبر» and "Hey Akbar" can be recognized.
 */
class ContinuousSpeechRecognizer(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onFinalAlternatives: (List<String>) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val rmsCallback: (Float) -> Unit = {},
) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var shouldRun = false
    private var paused = false
    private val handler = Handler(Looper.getMainLooper())
    private var preferredLocale: Locale = Locale("fa", "IR")
    private var bilingualWake = false
    private var nextWakeEnglish = false
    private val beepSilencer = RecognitionBeepSilencer(context)

    fun setPreferredLocale(locale: Locale) {
        preferredLocale = locale
    }

    /** Alternate fa-IR / en-US each listen cycle (for wake mode). */
    fun setBilingualWakeMode(enabled: Boolean) {
        bilingualWake = enabled
        if (enabled) nextWakeEnglish = false
    }

    fun start() {
        shouldRun = true
        paused = false
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device")
            return
        }
        // Recreate after cancel/busy/handoff — reuse often leaves ERROR_CLIENT forever.
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
        ensureRecognizer()
        listening = false
        handler.removeCallbacksAndMessages(null)
        startInternal()
    }

    fun pause() {
        paused = true
        listening = false
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.cancel()
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        beepSilencer.restoreBeeps()
    }

    fun resume() {
        if (!shouldRun) return
        paused = false
        scheduleRestart(300)
    }

    fun stop() {
        shouldRun = false
        paused = false
        listening = false
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.cancel()
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        beepSilencer.restoreBeeps()
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("Speech recognition is not available on this device")
                return
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)?.also {
                it.setRecognitionListener(listener)
            }
            if (recognizer == null) {
                onError("Could not create speech recognizer")
            }
        } catch (e: Exception) {
            recognizer = null
            onError(e.message ?: "Speech recognizer init failed")
        }
    }

    private fun activeLocale(): Locale {
        if (!bilingualWake) return preferredLocale
        nextWakeEnglish = !nextWakeEnglish
        return if (nextWakeEnglish) Locale.US else Locale("fa", "IR")
    }

    private fun startInternal() {
        if (!shouldRun || paused) return
        ensureRecognizer()
        if (recognizer == null) {
            scheduleRestart(1000)
            return
        }
        try {
            beepSilencer.muteBeeps()
        } catch (_: Exception) {
        }

        val locale = activeLocale()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 12)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            // Longer silence so short commands are not cut mid-phrase.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }
        try {
            listening = true
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            listening = false
            try {
                beepSilencer.restoreBeeps()
            } catch (_: Exception) {
            }
            onError(e.message ?: "Failed to start listening")
            scheduleRestart(800)
        }
    }

    private fun scheduleRestart(delayMs: Long = 400) {
        if (!shouldRun || paused) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (shouldRun && !paused && !listening) startInternal()
        }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            rmsCallback(level)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            listening = false
        }

        override fun onError(error: Int) {
            listening = false
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(350)
                SpeechRecognizer.ERROR_CLIENT -> {
                    // Dead recognizer instance — recreate then retry.
                    try {
                        recognizer?.destroy()
                    } catch (_: Exception) {
                    }
                    recognizer = null
                    scheduleRestart(500)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRestart(800)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    onError("Microphone permission required")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    onError("Speech network error — check connection / Google app")
                    scheduleRestart(1200)
                }
                else -> scheduleRestart(700)
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
                .filter { it.isNotBlank() }
            if (matches.isNotEmpty()) {
                onFinalAlternatives(matches)
                onFinalResult(matches.first())
            }
            scheduleRestart(400)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val best = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (best.isNotBlank()) onPartialResult(best)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
