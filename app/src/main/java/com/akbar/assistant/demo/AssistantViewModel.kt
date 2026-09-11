package com.akbar.assistant.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akbar.assistant.demo.commands.AssistantCommand
import com.akbar.assistant.demo.commands.CommandParser
import com.akbar.assistant.demo.commands.ResponseBuilder
import com.akbar.assistant.demo.device.FlashlightController
import com.akbar.assistant.demo.speech.AssistantTts
import com.akbar.assistant.demo.speech.ContinuousSpeechRecognizer
import com.akbar.assistant.demo.wake.WakeWordForegroundService
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val flashlight = FlashlightController(application)
    private var speech: ContinuousSpeechRecognizer? = null
    private var tts: AssistantTts? = null

    private var commandTimeoutJob: Job? = null
    private var backgroundHandoffJob: Job? = null
    private var activated = false
    private var awaitingCommand = false
    private var speaking = false
    private var resumeCommandAfterSpeak = false
    private var returnToWakeAfterSpeak = false

    init {
        tts = AssistantTts(
            context = application,
            onSpeakStart = {
                speaking = true
                speech?.pause()
                _uiState.update { it.copy(state = AssistantState.SPEAKING) }
            },
            onSpeakDone = {
                speaking = false
                when {
                    resumeCommandAfterSpeak -> {
                        resumeCommandAfterSpeak = false
                        startCommandListening()
                    }
                    returnToWakeAfterSpeak -> {
                        returnToWakeAfterSpeak = false
                        activated = false
                        awaitingCommand = false
                        enterWakeMode(_uiState.value.language)
                    }
                    else -> {
                        if (!activated) enterWakeMode(_uiState.value.language)
                    }
                }
            }
        )
    }

    fun onPermissionsResult(micGranted: Boolean, cameraGranted: Boolean) {
        _uiState.update {
            it.copy(
                permissionGranted = micGranted,
                cameraPermissionGranted = cameraGranted,
                needsCameraPermission = false,
                errorMessage = null
            )
        }
        if (micGranted) {
            enterWakeMode(_uiState.value.language)
        } else {
            WakeWordForegroundService.stop(getApplication())
            speech?.stop()
            _uiState.update {
                it.copy(
                    state = AssistantState.IDLE,
                    statusText = "برای شنیدن شما آماده‌ام",
                    hintText = "Microphone access is needed",
                    errorMessage = null
                )
            }
        }
    }

    /**
     * Called when [WakeWordForegroundService] hears the wake phrase while the app
     * is backgrounded or the phone is locked.
     */
    fun onWakeWordFromService(heard: String, language: AppLanguage) {
        onWakeDetected(language, heard)
    }

    fun toggleTestMode() {
        _uiState.update { it.copy(testModeVisible = !it.testModeVisible) }
    }

    fun simulateWake(language: AppLanguage) {
        onWakeDetected(
            language,
            if (language == AppLanguage.PERSIAN) "هی اکبر" else "Hey Akbar"
        )
    }

    fun runTestCommand(command: AssistantCommand) {
        viewModelScope.launch {
            val language = languageOf(command)
            activated = true
            awaitingCommand = true
            _uiState.update {
                it.copy(
                    language = language,
                    lastHeard = samplePhrase(command),
                    state = AssistantState.PROCESSING,
                    statusText = if (language == AppLanguage.PERSIAN) "یک لحظه…" else "One moment…"
                )
            }
            delay(120)
            executeCommand(command)
        }
    }

    fun releaseHardware() {
        flashlight.turnOffQuietly()
        _uiState.update { it.copy(lightOn = false) }
    }

    /**
     * Foreground path: listen in-process (reliable on most OEMs).
     * Background / lock path: [WakeWordForegroundService] via [onAppBackground].
     */
    private fun enterWakeMode(language: AppLanguage) {
        activated = false
        awaitingCommand = false
        resumeCommandAfterSpeak = false
        returnToWakeAfterSpeak = false
        // Foreground: in-app STT owns the mic. Stop FGS so it cannot steal audio.
        WakeWordForegroundService.stop(getApplication())
        startInAppWakeListening(language)
        _uiState.update {
            it.copy(
                state = AssistantState.LISTENING_WAKE,
                language = language,
                statusText = if (language == AppLanguage.PERSIAN) "در حال گوش دادن" else "Listening",
                hintText = if (language == AppLanguage.PERSIAN) {
                    "بگویید هی اکبر — اپ باز = مطمئن‌تر"
                } else {
                    "Say Hey Akbar — most reliable with app open"
                },
                lastHeard = "",
                lastReply = "",
                errorMessage = null,
                rmsLevel = 0f
            )
        }
    }

    /** Activity became visible — keep mic in-process (most reliable). */
    fun onAppForeground() {
        backgroundHandoffJob?.cancel()
        backgroundHandoffJob = null
        if (!_uiState.value.permissionGranted) return
        // Always stop FGS so it cannot steal the mic from the Activity.
        WakeWordForegroundService.stop(getApplication())
        if (activated || speaking) return
        startInAppWakeListening(_uiState.value.language)
        _uiState.update {
            it.copy(
                state = AssistantState.LISTENING_WAKE,
                statusText = if (it.language == AppLanguage.PERSIAN) "در حال گوش دادن" else "Listening",
                hintText = if (it.language == AppLanguage.PERSIAN) {
                    "بلند بگویید: هی اکبر"
                } else {
                    "Say clearly: Hey Akbar"
                }
            )
        }
    }

    /**
     * Debounced background. Permission dialogs trigger a brief onStop; handing
     * the mic to FGS immediately was killing in-app wake on real devices.
     * Until foreground wake is confirmed, we only pause — we do not start FGS.
     */
    fun onAppBackground() {
        if (!_uiState.value.permissionGranted) return
        backgroundHandoffJob?.cancel()
        backgroundHandoffJob = viewModelScope.launch {
            delay(1500)
            // Still backgrounded after debounce: pause in-app mic only.
            speech?.stop()
            speech?.destroy()
            speech = null
            // FGS deliberately disabled in v1.6 until foreground wake is solid.
            WakeWordForegroundService.stop(getApplication())
            if (!activated) {
                _uiState.update {
                    it.copy(
                        hintText = if (it.language == AppLanguage.PERSIAN) {
                            "برای بیدارباش اپ را باز نگه دارید"
                        } else {
                            "Keep the app open for wake (for now)"
                        }
                    )
                }
            }
        }
    }

    private fun startInAppWakeListening(language: AppLanguage) {
        ensureWakeSpeech()
        val locale = if (language == AppLanguage.PERSIAN) {
            Locale("fa", "IR")
        } else {
            Locale.US
        }
        speech?.setPreferredLocale(locale)
        speech?.start()
    }

    private fun ensureWakeSpeech() {
        if (speech != null) return
        speech = ContinuousSpeechRecognizer(
            context = getApplication(),
            onPartialResult = { text ->
                if (!(activated || speaking)) {
                    _uiState.update { it.copy(lastHeard = text) }
                    if (CommandParser.containsWakeWord(text)) {
                        onWakeDetected(CommandParser.wakeLanguage(text), text)
                    }
                }
            },
            onFinalResult = { text ->
                if (activated || speaking) {
                    if (activated && awaitingCommand) {
                        _uiState.update { it.copy(lastHeard = text) }
                        handleCommand(text)
                    }
                } else {
                    _uiState.update { it.copy(lastHeard = text) }
                    if (CommandParser.containsWakeWord(text)) {
                        onWakeDetected(CommandParser.wakeLanguage(text), text)
                    }
                }
            },
            onError = { message ->
                if (message.contains("permission", ignoreCase = true)) {
                    _uiState.update {
                        it.copy(
                            permissionGranted = false,
                            errorMessage = null,
                            statusText = "برای شنیدن شما آماده‌ام",
                            hintText = "Microphone access is needed"
                        )
                    }
                } else {
                    // Surface soft STT issues so debugging is not blind.
                    _uiState.update {
                        it.copy(hintText = message.take(48))
                    }
                }
            },
            rmsCallback = { level ->
                _uiState.update { it.copy(rmsLevel = level) }
            },
            onStatus = { status ->
                if (!activated && _uiState.value.state == AssistantState.LISTENING_WAKE) {
                    // Keep a stable wake prompt; only show STT health when not listening.
                    if (status != "listening" && status != "ready" && status != "speech") {
                        _uiState.update {
                            it.copy(hintText = "STT: $status — بگویید هی اکبر")
                        }
                    }
                }
            }
        )
    }

    /**
     * Command-phase uses the same recognizer instance as wake (callbacks already
     * branch on [activated] / [awaitingCommand] in [ensureWakeSpeech]).
     */
    private fun ensureCommandSpeech() {
        ensureWakeSpeech()
    }

    private fun onWakeDetected(language: AppLanguage, heard: String) {
        if (activated || speaking) return
        activated = true
        awaitingCommand = true
        commandTimeoutJob?.cancel()

        // Pause background wake listener so it does not fight the in-app mic.
        WakeWordForegroundService.pause(getApplication())

        val leftover = CommandParser.stripWakeWord(heard)
        val combinedCommand = if (leftover.isNotBlank()) {
            val parsed = CommandParser.parse(heard)
            if (parsed is AssistantCommand.Unknown) null else parsed
        } else {
            null
        }

        _uiState.update {
            it.copy(
                language = language,
                lastHeard = heard,
                state = AssistantState.ACTIVATED,
                statusText = if (language == AppLanguage.PERSIAN) "بله، بفرمایید" else "Yes?",
                hintText = if (language == AppLanguage.PERSIAN) {
                    "ساعت · هوا · چراغ‌قوه"
                } else {
                    "Time · weather · flashlight"
                },
                errorMessage = null
            )
        }

        speech?.pause()

        if (combinedCommand != null) {
            executeCommand(combinedCommand)
            return
        }

        val prompt = ResponseBuilder.activationPrompt(language)
        _uiState.update {
            it.copy(
                statusText = prompt,
                lastReply = "",
                hintText = if (language == AppLanguage.PERSIAN) {
                    "ساعت · هوا · چراغ‌قوه"
                } else {
                    "Time · weather · flashlight"
                }
            )
        }
        resumeCommandAfterSpeak = true
        returnToWakeAfterSpeak = false
        tts?.speak(prompt, language)

        commandTimeoutJob = viewModelScope.launch {
            delay(15_000)
            if (activated && awaitingCommand && !speaking) {
                awaitingCommand = false
                activated = false
                enterWakeMode(language)
            }
        }
    }

    private fun startCommandListening() {
        if (!activated || !awaitingCommand) return
        _uiState.update {
            it.copy(
                state = AssistantState.LISTENING_COMMAND,
                statusText = if (it.language == AppLanguage.PERSIAN) "گوش می‌دهم" else "Listening",
                hintText = if (it.language == AppLanguage.PERSIAN) {
                    "آرام صحبت کنید"
                } else {
                    "Speak naturally"
                }
            )
        }
        ensureCommandSpeech()
        val locale = if (_uiState.value.language == AppLanguage.PERSIAN) {
            Locale("fa", "IR")
        } else {
            Locale.US
        }
        speech?.setPreferredLocale(locale)
        speech?.start()
    }

    private fun handleCommand(text: String) {
        val stripped = CommandParser.stripWakeWord(text)
        if (stripped.isBlank()) return
        if (CommandParser.containsWakeWord(text) && stripped.split(" ").size <= 1) return

        commandTimeoutJob?.cancel()
        awaitingCommand = false
        speech?.pause()

        val command = CommandParser.parse(text)
        val language = languageOf(command)
        _uiState.update {
            it.copy(
                language = language,
                lastHeard = text,
                state = AssistantState.PROCESSING,
                statusText = if (language == AppLanguage.PERSIAN) "یک لحظه…" else "One moment…"
            )
        }
        executeCommand(command)
    }

    private fun executeCommand(command: AssistantCommand) {
        val language = languageOf(command)
        var nextLight = _uiState.value.lightOn
        var reply = ResponseBuilder.forCommand(command)
        var error: String? = null

        when (command) {
            is AssistantCommand.LightOn -> {
                when (flashlight.setEnabled(true)) {
                    FlashlightController.Result.ON -> {
                        nextLight = true
                        reply = if (language == AppLanguage.PERSIAN) "چراغ‌قوه روشن شد" else "Flashlight is on"
                    }
                    FlashlightController.Result.NO_PERMISSION -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "اجازه دوربین را بدهید، بعد دوباره بگویید"
                        } else {
                            "Allow camera access, then ask again"
                        }
                        _uiState.update { it.copy(needsCameraPermission = true) }
                    }
                    FlashlightController.Result.NO_FLASH -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "این گوشی چراغ‌قوه ندارد"
                        } else {
                            "This phone has no flashlight"
                        }
                    }
                    else -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "الان نتوانستم چراغ را روشن کنم"
                        } else {
                            "I couldn't turn the light on just now"
                        }
                    }
                }
            }
            is AssistantCommand.LightOff -> {
                when (flashlight.setEnabled(false)) {
                    FlashlightController.Result.OFF -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) "چراغ‌قوه خاموش شد" else "Flashlight is off"
                    }
                    FlashlightController.Result.NO_PERMISSION -> {
                        reply = if (language == AppLanguage.PERSIAN) {
                            "اجازه دوربین را بدهید، بعد دوباره بگویید"
                        } else {
                            "Allow camera access, then ask again"
                        }
                        _uiState.update { it.copy(needsCameraPermission = true) }
                    }
                    FlashlightController.Result.NO_FLASH -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "این گوشی چراغ‌قوه ندارد"
                        } else {
                            "This phone has no flashlight"
                        }
                    }
                    else -> {
                        nextLight = flashlight.isOn
                        reply = if (language == AppLanguage.PERSIAN) {
                            "الان نتوانستم چراغ را خاموش کنم"
                        } else {
                            "I couldn't turn the light off just now"
                        }
                    }
                }
            }
            else -> Unit
        }

        awaitingCommand = false
        resumeCommandAfterSpeak = false
        returnToWakeAfterSpeak = true

        _uiState.update {
            it.copy(
                lightOn = nextLight,
                lastReply = "",
                language = language,
                statusText = reply,
                hintText = "",
                errorMessage = error,
                state = AssistantState.SPEAKING,
                lastHeard = ""
            )
        }
        tts?.speak(reply, language)
    }

    private fun languageOf(command: AssistantCommand): AppLanguage = when (command) {
        is AssistantCommand.TellTime -> command.language
        is AssistantCommand.Weather -> command.language
        is AssistantCommand.LightOn -> command.language
        is AssistantCommand.LightOff -> command.language
        is AssistantCommand.Unknown -> command.language
    }

    private fun samplePhrase(command: AssistantCommand): String = when (command) {
        is AssistantCommand.TellTime ->
            if (command.language == AppLanguage.PERSIAN) "ساعت چنده؟" else "What time is it?"
        is AssistantCommand.Weather ->
            if (command.language == AppLanguage.PERSIAN) "هوا چطوره؟" else "What's the weather?"
        is AssistantCommand.LightOn ->
            if (command.language == AppLanguage.PERSIAN) "چراغ رو روشن کن" else "Turn on the light"
        is AssistantCommand.LightOff ->
            if (command.language == AppLanguage.PERSIAN) "چراغ رو خاموش کن" else "Turn off the light"
        is AssistantCommand.Unknown -> command.raw
    }

    override fun onCleared() {
        super.onCleared()
        commandTimeoutJob?.cancel()
        WakeWordForegroundService.stop(getApplication())
        flashlight.turnOffQuietly()
        speech?.destroy()
        tts?.shutdown()
    }
}
