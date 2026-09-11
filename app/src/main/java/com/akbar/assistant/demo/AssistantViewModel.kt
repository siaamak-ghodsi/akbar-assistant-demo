package com.akbar.assistant.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akbar.assistant.demo.commands.AssistantCommand
import com.akbar.assistant.demo.commands.CommandParser
import com.akbar.assistant.demo.commands.ResponseBuilder
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
    val testModeVisible: Boolean = false
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

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

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted, errorMessage = null) }
        if (granted) {
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
                if (speaking) return@onFinalResult
                if (!activated) {
                    if (CommandParser.containsWakeWord(text)) {
                        onWakeDetected(CommandParser.wakeLanguage(text), text)
                    }
                } else if (awaitingCommand) {
                    handleCommand(text)
                }
            },
            onError = { message ->
                if (message.contains("permission", ignoreCase = true)) {
                    _uiState.update { it.copy(errorMessage = message) }
                }
            },
            onRmsChanged = { rms ->
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
                    "ساعت، هوا، یا چراغ را بگو"
                } else {
                    "Ask for time, weather, or light"
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
        // Ignore bare wake-word repeats while waiting for a real command.
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
        var nextLight = _uiState.value.lightOn
        when (command) {
            is AssistantCommand.LightOn -> nextLight = true
            is AssistantCommand.LightOff -> nextLight = false
            else -> Unit
        }
        val reply = ResponseBuilder.forCommand(command)
        val language = languageOf(command)

        activated = false
        awaitingCommand = false

        _uiState.update {
            it.copy(
                lightOn = nextLight,
                lastReply = reply,
                language = language,
                statusText = reply,
                hintText = if (language == AppLanguage.PERSIAN) "دوباره بگو هی اکبر" else "Say Hey Akbar again"
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
        speech?.destroy()
        tts?.shutdown()
    }
}
