package com.akbar.assistant.demo.speech

import android.content.Context
import android.content.Intent
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
 * Continuous SpeechRecognizer loop for wake + commands.
 *
 * Important OEM lessons baked in:
 * - Prefer the **default cloud recognizer** for Persian. On-device + EXTRA_PREFER_OFFLINE
 *   often fails silently for fa-IR and returns endless NO_MATCH / LANGUAGE errors.
 * - Do not mute RING/SYSTEM forever — some phones route recognition through those streams.
 * - Always recreate after hard client errors.
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
    private var restartGeneration = 0

    fun setPreferredLocale(locale: Locale) {
        preferredLocale = locale
    }

    fun start() {
        shouldRun = true
        paused = false
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available — install/update Google app")
            onStatus("STT unavailable")
            return
        }
        handler.post {
            ensureRecognizer(forceRecreate = true)
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
        scheduleRestart(300)
    }

    fun stop() {
        shouldRun = false
        paused = false
        listening = false
        restartGeneration++
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

        // Always use the default (usually Google cloud) recognizer for FA wake.
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
        onStatus("STT ready")
        Log.i(TAG, "Created default SpeechRecognizer")
    }

    private fun startInternal() {
        if (!shouldRun || paused) return
        ensureRecognizer(forceRecreate = false)
        // Mute only notification beep briefly — do not kill ring/system long-term.
        beepSilencer.muteBeeps()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 8)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR,en-US")
            // Shorter silence = faster wake response.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 400L)
            // Do NOT set EXTRA_PREFER_OFFLINE — breaks Persian on many devices.
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
            if (hardErrorCount >= 2) {
                hardErrorCount = 0
                ensureRecognizer(forceRecreate = true)
            }
            scheduleRestart(800)
        }
    }

    private fun scheduleRestart(delayMs: Long = 450) {
        if (!shouldRun || paused) return
        val gen = ++restartGeneration
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (gen != restartGeneration) return@postDelayed
            if (shouldRun && !paused && !listening) startInternal()
        }, delayMs)
    }

    private fun pickBest(matches: List<String>): String {
        val clean = matches.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return ""
        return clean.firstOrNull { CommandParser.containsWakeWord(it) } ?: clean.first()
    }

    private fun emitAllForDebug(matches: List<String>) {
        if (matches.isEmpty()) return
        Log.i(TAG, "STT candidates: ${matches.take(5)}")
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
                    scheduleRestart(350)
                }
                SpeechRecognizer.ERROR_CLIENT -> {
                    hardErrorCount++
                    onStatus("client err")
                    if (hardErrorCount >= 2) {
                        hardErrorCount = 0
                        ensureRecognizer(forceRecreate = true)
                    }
                    scheduleRestart(600)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    onStatus("busy")
                    scheduleRestart(900)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    onError("Microphone permission required")
                    onStatus("no mic permission")
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    onError("نیاز به اینترنت برای تشخیص گفتار (Google)")
                    onStatus("network")
                    ensureRecognizer(forceRecreate = true)
                    scheduleRestart(1500)
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    onError("زبان فارسی روی تشخیص گفتار نصب نیست")
                    onStatus("lang missing")
                    // Fall back to English locale so "Hey Akbar" can still work.
                    preferredLocale = Locale.US
                    ensureRecognizer(forceRecreate = true)
                    scheduleRestart(800)
                }
                else -> {
                    onStatus(label)
                    scheduleRestart(700)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            hardErrorCount = 0
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            emitAllForDebug(matches)
            val best = pickBest(matches)
            if (best.isNotBlank()) {
                Log.i(TAG, "STT final: $best")
                onFinalResult(best)
            }
            scheduleRestart(400)
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
