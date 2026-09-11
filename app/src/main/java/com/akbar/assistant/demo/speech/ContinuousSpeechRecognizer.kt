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

class ContinuousSpeechRecognizer(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onReady: () -> Unit = {},
    private val onRmsChanged: (Float) -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldRestart = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var preferredLocale: Locale = Locale("fa", "IR")

    fun setPreferredLocale(locale: Locale) {
        preferredLocale = locale
    }

    fun start() {
        shouldRestart = true
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device")
            return
        }
        ensureRecognizer()
        startListeningInternal()
    }

    fun stop() {
        shouldRestart = false
        isListening = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    fun destroy() {
        stop()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun ensureRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
        }
    }

    private fun startListeningInternal() {
        if (!shouldRestart) return
        ensureRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLocale.toLanguageTag())
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                listOf("fa-IR", "en-US").joinToString(",")
            )
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }
        try {
            isListening = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            onError(e.message ?: "Failed to start listening")
            scheduleRestart(600)
        }
    }

    private fun scheduleRestart(delayMs: Long = 350) {
        if (!shouldRestart) return
        mainHandler.postDelayed({
            if (shouldRestart && !isListening) {
                startListeningInternal()
            }
        }, delayMs)
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onReady()
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            onRmsChanged(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Recognition error ($error)"
            }
            if (
                error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_CLIENT
            ) {
                scheduleRestart(200)
            } else {
                onError(message)
                scheduleRestart(700)
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = texts?.firstOrNull().orEmpty()
            if (best.isNotBlank()) {
                onFinalResult(best)
            }
            scheduleRestart(250)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = texts?.firstOrNull().orEmpty()
            if (best.isNotBlank()) {
                onPartialResult(best)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
