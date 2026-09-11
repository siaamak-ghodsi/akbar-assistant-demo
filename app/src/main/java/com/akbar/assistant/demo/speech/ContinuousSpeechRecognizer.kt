package com.akbar.assistant.demo.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.akbar.assistant.demo.commands.CommandParser
import java.util.Locale

/**
 * Continuous SpeechRecognizer loop with beep silencing and calmer restart timing.
 * Prefers on-device recognition when available (more reliable in background / lock).
 */
class ContinuousSpeechRecognizer(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit = {},
    private val rmsCallback: (Float) -> Unit = {},
    private val onStatus: (String) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var shouldRun = false
    private var paused = false
    private val handler = Handler(Looper.getMainLooper())
    private var preferredLocale: Locale = Locale("fa", "IR")
    private val beepSilencer = RecognitionBeepSilencer(context)
    private var hardErrorCount = 0

    fun setPreferredLocale(locale: Locale) {
        preferredLocale = locale
    }

    fun start() {
        shouldRun = true
        paused = false
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device")
            onStatus("STT unavailable")
            return
        }
        handler.post {
            ensureRecognizer(forceRecreate = false)
            listening = false
            handler.removeCallbacksAndMessages(null)
            try {
                recognizer?.cancel()
            } catch (_: Exception) {
            }
            startInternal()
        }
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
        handler.post {
            try {
                recognizer?.destroy()
            } catch (_: Exception) {
            }
            recognizer = null
        }
    }

    private fun ensureRecognizer(forceRecreate: Boolean) {
        if (forceRecreate) {
            try {
                recognizer?.destroy()
            } catch (_: Exception) {
            }
            recognizer = null
        }
        if (recognizer != null) return

        recognizer = createRecognizer().apply {
            setRecognitionListener(listener)
        }
        onStatus("STT ready")
    }

    private fun createRecognizer(): SpeechRecognizer {
        // On-device is far more reliable when the screen is off / app is backgrounded.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    Log.i(TAG, "Using on-device SpeechRecognizer")
                    return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "On-device recognizer failed, falling back", e)
            }
        }
        Log.i(TAG, "Using default SpeechRecognizer")
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun startInternal() {
        if (!shouldRun || paused) return
        ensureRecognizer(forceRecreate = false)
        beepSilencer.muteBeeps()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 8)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLocale.toLanguageTag())
            // Keep both languages available so FA/EN wake phrases both have a chance.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR,en-US")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600L)
            putExtra("android.speech.extra.DICTATION_MODE", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        try {
            listening = true
            onStatus("listening")
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            listening = false
            Log.e(TAG, "startListening failed", e)
            onError(e.message ?: "Failed to start listening")
            onStatus("start failed")
            hardErrorCount++
            if (hardErrorCount >= 3) {
                hardErrorCount = 0
                ensureRecognizer(forceRecreate = true)
            }
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

    private fun pickBest(matches: List<String>): String {
        val clean = matches.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return ""
        // Prefer any alternative that looks like the wake phrase.
        return clean.firstOrNull { CommandParser.containsWakeWord(it) } ?: clean.first()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onStatus("ready")
        }

        override fun onBeginningOfSpeech() {
            onStatus("speech")
        }

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
            val label = errorLabel(error)
            Log.w(TAG, "STT error: $label ($error)")
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    onStatus("retry")
                    scheduleRestart(400)
                }
                SpeechRecognizer.ERROR_CLIENT -> {
                    hardErrorCount++
                    onStatus("client err")
                    if (hardErrorCount >= 2) {
                        hardErrorCount = 0
                        ensureRecognizer(forceRecreate = true)
                    }
                    scheduleRestart(700)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    onStatus("busy")
                    scheduleRestart(1000)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    onError("Microphone permission required")
                    onStatus("no mic permission")
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    onError("Speech network error — trying offline / retry")
                    onStatus("network")
                    // Recreate; next start prefers offline / on-device.
                    ensureRecognizer(forceRecreate = true)
                    scheduleRestart(1200)
                }
                else -> {
                    onStatus(label)
                    scheduleRestart(800)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            hardErrorCount = 0
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            val best = pickBest(matches)
            if (best.isNotBlank()) {
                Log.i(TAG, "STT final: $best | alts=${matches.take(4)}")
                onFinalResult(best)
            }
            scheduleRestart(450)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            val best = pickBest(matches)
            if (best.isNotBlank()) onPartialResult(best)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun errorLabel(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "net timeout"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_AUDIO -> "audio"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "silence"
        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "rate limit"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "disconnected"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "lang"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "lang missing"
        else -> "err $error"
    }

    companion object {
        private const val TAG = "AkbarSTT"
    }
}
