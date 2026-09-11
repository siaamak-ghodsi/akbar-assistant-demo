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

    private fun enterWakeMode(language: AppLanguage) {
        activated = false
        awaitingCommand = false
        resumeCommandAfterSpeak = false
        returnToWakeAfterSpeak = false
        ensureSpeech()
        speech?.setPreferredLocale(Locale("fa", "IR"))
        speech?.start()
        _uiState.update {
            it.copy(
                state = AssistantState.LISTENING_WAKE,
                language = language,
                statusText = if (language == AppLanguage.PERSIAN) "در حال گوش دادن" else "Listening",
                hintText = if (language == AppLanguage.PERSIAN) {
                    "بگویید هی اکبر"
                } else {
                    "Say Hey Akbar"
                },
                lastHeard = "",
                lastReply = "",
                errorMessage = null,
                rmsLevel = 0f
            )
        }
    }

    private fun ensureSpeech() {
        if (speech != null) return
        speech = ContinuousSpeechRecognizer(
            context = getApplication(),
            onPartialResult = { text ->
                if (awaitingCommand && activated && !speaking) {
                    _uiState.update { it.copy(lastHeard = text) }
                }
                if (!activated && !speaking && CommandParser.containsWakeWord(text)) {
                    onWakeDetected(CommandParser.wakeLanguage(text), text)
                }
            },
            onFinalResult = { text ->
                if (!speaking) {
                    if (!activated) {
                        if (CommandParser.containsWakeWord(text)) {
                            onWakeDetected(CommandParser.wakeLanguage(text), text)
                        }
                    } else if (awaitingCommand) {
                        _uiState.update { it.copy(lastHeard = text) }
                        handleCommand(text)
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
                }
            },
            rmsCallback = { level ->
                _uiState.update { it.copy(rmsLevel = level) }
            }
        )
    }

    private fun onWakeDetected(language: AppLanguage, heard: String) {
        if (activated || speaking) return
        activated = true
        awaitingCommand = true
        commandTimeoutJob?.cancel()

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
        val locale = if (_uiState.value.language == AppLanguage.PERSIAN) {
            Locale("fa", "IR")
        } else {
            Locale.US
        }
        speech?.setPreferredLocale(locale)
        speech?.resume()
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
        flashlight.turnOffQuietly()
        speech?.destroy()
        tts?.shutdown()
    }
}
