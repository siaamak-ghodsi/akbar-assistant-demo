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

data class AssistantUiState(
    val state: AssistantState = AssistantState.IDLE,
    val language: AppLanguage = AppLanguage.PERSIAN,
    val statusText: String = "بگو هی اکبر",
    val hintText: String = "Say Hey Akbar",
    val lastHeard: String = "",
    val lastReply: String = "",
    val lightOn: Boolean = false,
    val rmsLevel: Float = 0f,
    val errorMessage: String? = null,
    val permissionGranted: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val testModeVisible: Boolean = false
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val flashlight = FlashlightController(application)

    private var speech: ContinuousSpeechRecognizer? = null
    private var tts: AssistantTts? = null
    private var commandTimeoutJob: Job? = null
    private var activated = false
    private var speaking = false
    private var awaitingCommand = false

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
                if (awaitingCommand && activated) {
                    startCommandListening()
                } else {
                    awaitingCommand = false
                    activated = false
                    enterWakeMode(_uiState.value.language)
                }
            }
        )
    }

    fun onPermissionsResult(micGranted: Boolean, cameraGranted: Boolean) {
        _uiState.update {
            it.copy(
                permissionGranted = micGranted,
                cameraPermissionGranted = cameraGranted,
                errorMessage = null
            )
        }
        if (micGranted) {
            enterWakeMode(_uiState.value.language)
        } else {
            _uiState.update {
                it.copy(
                    state = AssistantState.IDLE,
                    statusText = "دسترسی میکروفون لازم است",
                    hintText = "Microphone permission required",
                    errorMessage = "RECORD_AUDIO denied"
                )
            }
        }
    }

    fun toggleTestMode() {
        _uiState.update { it.copy(testModeVisible = !it.testModeVisible) }
    }

    fun simulateWake(language: AppLanguage) {
        onWakeDetected(language, if (language == AppLanguage.PERSIAN) "هی اکبر" else "Hey Akbar")
    }

    fun runTestCommand(command: AssistantCommand) {
        viewModelScope.launch {
            val language = languageOf(command)
            val heard = samplePhrase(command)
            _uiState.update {
                it.copy(
                    language = language,
                    lastHeard = heard,
                    state = AssistantState.PROCESSING,
                    statusText = if (language == AppLanguage.PERSIAN) "در حال پردازش..." else "Processing...",
                    hintText = heard
                )
            }
            delay(180)
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
        ensureSpeech()
        speech?.setPreferredLocale(Locale("fa", "IR"))
        speech?.start()
        _uiState.update {
            it.copy(
                state = AssistantState.LISTENING_WAKE,
                language = language,
                statusText = if (language == AppLanguage.PERSIAN) "بگو هی اکبر" else "Say Hey Akbar",
                hintText = if (language == AppLanguage.PERSIAN) "Say Hey Akbar" else "بگو هی اکبر"
            )
        }
    }

    private fun ensureSpeech() {
        if (speech != null) return
        speech = ContinuousSpeechRecognizer(
            context = getApplication(),
            onPartialResult = { text ->
                _uiState.update { it.copy(lastHeard = text) }
                if (!activated && !speaking && CommandParser.containsWakeWord(text)) {
                    onWakeDetected(CommandParser.wakeLanguage(text), text)
                }
            },
            onFinalResult = { text ->
                _uiState.update { it.copy(lastHeard = text) }
                if (!speaking) {
                    if (!activated) {
                        if (CommandParser.containsWakeWord(text)) {
                            onWakeDetected(CommandParser.wakeLanguage(text), text)
                        }
                    } else if (awaitingCommand) {
                        handleCommand(text)
                    }
                }
            },
            onError = { message ->
                if (message.contains("permission", ignoreCase = true)) {
                    _uiState.update { it.copy(errorMessage = message) }
                }
            },
            rmsCallback = { rms ->
                _uiState.update { it.copy(rmsLevel = (rms / 10f).coerceIn(0f, 1f)) }
            }
        )
    }

    private fun onWakeDetected(language: AppLanguage, heard: String) {
        if (activated || speaking) return
        activated = true
        awaitingCommand = true
        commandTimeoutJob?.cancel()

        _uiState.update {
            it.copy(
                language = language,
                lastHeard = heard,
                state = AssistantState.ACTIVATED,
                statusText = if (language == AppLanguage.PERSIAN) "گوش می‌دم..." else "Listening...",
                hintText = if (language == AppLanguage.PERSIAN) {
                    "ساعت، هوا، یا چراغ‌قوه را بگو"
                } else {
                    "Ask for time, weather, or flashlight"
                },
                errorMessage = null
            )
        }

        val prompt = ResponseBuilder.activationPrompt(language)
        _uiState.update { it.copy(lastReply = prompt) }
        speech?.pause()
        tts?.speak(prompt, language)

        commandTimeoutJob = viewModelScope.launch {
            delay(12_000)
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
                statusText = if (it.language == AppLanguage.PERSIAN) "گوش می‌دم..." else "Listening...",
                hintText = if (it.language == AppLanguage.PERSIAN) {
                    "دستورت را بگو"
                } else {
                    "Say your command"
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
        if (stripped.isBlank() || (CommandParser.containsWakeWord(text) && stripped.split(" ").size <= 1)) {
            return
        }
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
                statusText = if (language == AppLanguage.PERSIAN) "در حال پردازش..." else "Processing..."
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
                when (val result = flashlight.setEnabled(true)) {
                    FlashlightController.Result.ON -> {
                        nextLight = true
                        reply = if (language == AppLanguage.PERSIAN) {
                            "چراغ‌قوه روشن شد"
                        } else {
                            "Flashlight is on"
                        }
                    }
                    FlashlightController.Result.NO_PERMISSION -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "برای روشن کردن چراغ‌قوه، دسترسی دوربین لازم است"
                        } else {
                            "Camera permission is required for the flashlight"
                        }
                        error = "CAMERA permission required"
                    }
                    FlashlightController.Result.NO_FLASH -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "این دستگاه چراغ‌قوه ندارد"
                        } else {
                            "This device has no flashlight"
                        }
                        error = "No flashlight hardware"
                    }
                    else -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "نتوانستم چراغ‌قوه را روشن کنم"
                        } else {
                            "Couldn't turn on the flashlight"
                        }
                        error = "Torch error"
                    }
                }
            }
            is AssistantCommand.LightOff -> {
                when (val result = flashlight.setEnabled(false)) {
                    FlashlightController.Result.OFF -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "چراغ‌قوه خاموش شد"
                        } else {
                            "Flashlight is off"
                        }
                    }
                    FlashlightController.Result.NO_PERMISSION -> {
                        reply = if (language == AppLanguage.PERSIAN) {
                            "برای کنترل چراغ‌قوه، دسترسی دوربین لازم است"
                        } else {
                            "Camera permission is required for the flashlight"
                        }
                        error = "CAMERA permission required"
                    }
                    FlashlightController.Result.NO_FLASH -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "این دستگاه چراغ‌قوه ندارد"
                        } else {
                            "This device has no flashlight"
                        }
                    }
                    else -> {
                        nextLight = flashlight.isOn
                        reply = if (language == AppLanguage.PERSIAN) {
                            "نتوانستم چراغ‌قوه را خاموش کنم"
                        } else {
                            "Couldn't turn off the flashlight"
                        }
                        error = "Torch error"
                    }
                }
            }
            else -> Unit
        }

        activated = false
        awaitingCommand = false

        _uiState.update {
            it.copy(
                lightOn = nextLight,
                lastReply = reply,
                language = language,
                statusText = reply,
                hintText = if (language == AppLanguage.PERSIAN) "دوباره بگو هی اکبر" else "Say Hey Akbar again",
                errorMessage = error
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
