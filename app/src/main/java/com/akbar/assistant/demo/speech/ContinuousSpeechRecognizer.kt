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
 * Continuous SpeechRecognizer loop with beep silencing and calmer restart timing.
 * Pause while TTS speaks so the mic does not fight the voice reply.
 */
class ContinuousSpeechRecognizer(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onFinalAlternatives: (List<String>) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val rmsCallback: (Float) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var shouldRun = false
    private var paused = false
    private val handler = Handler(Looper.getMainLooper())
    private var preferredLocale: Locale = Locale("fa", "IR")
    private val beepSilencer = RecognitionBeepSilencer(context)

    fun setPreferredLocale(locale: Locale) {
        preferredLocale = locale
    }

    fun start() {
        shouldRun = true
        paused = false
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device")
            return
        }
        ensureRecognizer()
        listening = false
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }
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
        // Unmute so TTS can play on media / system routes cleanly.
        beepSilencer.restoreBeeps()
    }

    fun resume() {
        if (!shouldRun) return
        paused = false
        scheduleRestart(350)
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

    private fun startInternal() {
        if (!shouldRun || paused) return
        ensureRecognizer()
        if (recognizer == null) {
            scheduleRestart(1200)
            return
        }
        try {
            beepSilencer.muteBeeps()
        } catch (_: Exception) {
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 8)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR,en-US")
            // Longer silence windows so Persian phrases are less often cut off / timed out.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600L)
            putExtra("android.speech.extra.DICTATION_MODE", true)
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
            scheduleRestart(900)
        }
    }

    private fun scheduleRestart(delayMs: Long = 500) {
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
            // Normalize typical SpeechRecognizer RMS (-2..10) into 0..1
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
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(450)
                SpeechRecognizer.ERROR_CLIENT -> scheduleRestart(600)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRestart(900)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    onError("Microphone permission required")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    onError("Speech network error — check connection / Google app")
                    scheduleRestart(1500)
                }
                else -> {
                    scheduleRestart(800)
                }
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
            // Give the UI/ViewModel a beat before restarting listen loop.
            scheduleRestart(450)
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
