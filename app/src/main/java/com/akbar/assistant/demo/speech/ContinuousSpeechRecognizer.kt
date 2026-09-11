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
 * Continuous SpeechRecognizer loop (KontinuousSpeechRecognizer-style):
 * restarts after results / NO_MATCH / timeout.
 * Supports pause/resume so TTS is not interrupted by the mic loop.
 */
class ContinuousSpeechRecognizer(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit = {},
    private val onRmsChanged: (Float) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var shouldRun = false
    private var paused = false
    private val handler = Handler(Looper.getMainLooper())
    private var preferredLocale: Locale = Locale("fa", "IR")

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
        // Cancel any in-flight session before starting fresh.
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
    }

    fun resume() {
        if (!shouldRun) return
        paused = false
        scheduleRestart(200)
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
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
    }

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
    }

    private fun startInternal() {
        if (!shouldRun || paused) return
        ensureRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR,en-US")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600L)
        }
        try {
            listening = true
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            listening = false
            onError(e.message ?: "Failed to start listening")
            scheduleRestart(700)
        }
    }

    private fun scheduleRestart(delayMs: Long = 300) {
        if (!shouldRun || paused) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (shouldRun && !paused && !listening) startInternal()
        }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = onRmsChanged(rmsdB)
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            listening = false
        }

        override fun onError(error: Int) {
            listening = false
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_CLIENT -> scheduleRestart(180)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRestart(500)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    onError("Microphone permission required")
                else -> {
                    onError("Recognition error ($error)")
                    scheduleRestart(700)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val best = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (best.isNotBlank()) onFinalResult(best)
            scheduleRestart(220)
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
