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

    init {
        tts = AssistantTts(
            context = application,
            onSpeakStart = {
                speaking = true
                _uiState.update { it.copy(state = AssistantState.SPEAKING) }
            },
            onSpeakDone = {
                speaking = false
                if (activated) {
                    resumeCommandListening()
                } else {
                    setWakeStatus(_uiState.value.language)
                    restartWakeListening()
                }
            }
        )
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        if (granted) {
            startWakeListening()
        } else {
            _uiState.update {
                it.copy(
                    state = AssistantState.IDLE,
                    statusText = "Microphone permission required / دسترسی میکروفون لازم است",
                    errorMessage = "RECORD_AUDIO permission denied"
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
            val heard = when (command) {
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
            val language = languageOf(command)
            _uiState.update {
                it.copy(
                    language = language,
                    lastHeard = heard,
                    state = AssistantState.PROCESSING,
                    statusText = statusFor(AssistantState.PROCESSING, language)
                )
            }
            delay(200)
            executeCommand(command, fromVoice = false)
        }
    }

    private fun startWakeListening() {
        activated = false
        setWakeStatus(_uiState.value.language)
        ensureSpeech()
        speech?.setPreferredLocale(Locale("fa", "IR"))
        speech?.start()
        _uiState.update { it.copy(state = AssistantState.LISTENING_WAKE) }
    }

    private fun restartWakeListening() {
        speech?.stop()
        viewModelScope.launch {
            delay(200)
            if (_uiState.value.permissionGranted && !activated) {
                startWakeListening()
            }
        }
    }

    private fun ensureSpeech() {
        if (speech != null) return
        speech = ContinuousSpeechRecognizer(
            context = getApplication(),
            onPartialResult = { text ->
                _uiState.update { it.copy(lastHeard = text) }
                if (!activated && CommandParser.containsWakeWord(text)) {
                    onWakeDetected(CommandParser.wakeLanguage(text), text)
                }
            },
            onFinalResult = { text ->
                _uiState.update { it.copy(lastHeard = text) }
                if (!activated) {
                    if (CommandParser.containsWakeWord(text)) {
                        onWakeDetected(CommandParser.wakeLanguage(text), text)
                    }
                } else if (!speaking) {
                    handleCommand(text)
                }
            },
            onError = { message ->
                if (message.contains("permission", ignoreCase = true)) {
                    _uiState.update { it.copy(errorMessage = message) }
                }
            },
            onRmsChanged = { rms ->
                _uiState.update { it.copy(rmsLevel = rms.coerceIn(0f, 10f) / 10f) }
            }
        )
    }

    private fun onWakeDetected(language: AppLanguage, heard: String) {
        if (activated) return
        activated = true
        commandTimeoutJob?.cancel()
        _uiState.update {
            it.copy(
                language = language,
                lastHeard = heard,
                state = AssistantState.ACTIVATED,
                statusText = statusFor(AssistantState.ACTIVATED, language),
                errorMessage = null
            )
        }
        val prompt = ResponseBuilder.activationPrompt(language)
        _uiState.update { it.copy(lastReply = prompt) }
        speech?.stop()
        tts?.speak(prompt, language)
        commandTimeoutJob = viewModelScope.launch {
            delay(12_000)
            if (activated) {
                activated = false
                setWakeStatus(language)
                restartWakeListening()
            }
        }
    }

    private fun resumeCommandListening() {
        if (!activated) return
        _uiState.update {
            it.copy(
                state = AssistantState.LISTENING_COMMAND,
                statusText = statusFor(AssistantState.LISTENING_COMMAND, it.language)
            )
        }
        val locale = if (_uiState.value.language == AppLanguage.PERSIAN) {
            Locale("fa", "IR")
        } else {
            Locale.US
        }
        speech?.setPreferredLocale(locale)
        speech?.start()
    }

    private fun handleCommand(text: String) {
        if (CommandParser.containsWakeWord(text) && text.trim().split(" ").size <= 3) return
        commandTimeoutJob?.cancel()
        val command = CommandParser.parse(text)
        val language = languageOf(command)
        _uiState.update {
            it.copy(
                language = language,
                state = AssistantState.PROCESSING,
                statusText = statusFor(AssistantState.PROCESSING, language),
                lastHeard = text
            )
        }
        speech?.stop()
        executeCommand(command, fromVoice = true)
    }

    private fun executeCommand(command: AssistantCommand, fromVoice: Boolean) {
        var nextLight = _uiState.value.lightOn
        when (command) {
            is AssistantCommand.LightOn -> nextLight = true
            is AssistantCommand.LightOff -> nextLight = false
            else -> Unit
        }
        val reply = ResponseBuilder.forCommand(command)
        val language = languageOf(command)
        _uiState.update {
            it.copy(
                lightOn = nextLight,
                lastReply = reply,
                language = language,
                statusText = reply
            )
        }
        activated = false
        tts?.speak(reply, language)
        if (!fromVoice) {
            // Keep wake listening available under test mode.
        }
    }

    private fun setWakeStatus(language: AppLanguage) {
        _uiState.update {
            it.copy(
                state = AssistantState.LISTENING_WAKE,
                language = language,
                statusText = statusFor(AssistantState.IDLE, language)
            )
        }
    }

    private fun statusFor(state: AssistantState, language: AppLanguage): String {
        val fa = language == AppLanguage.PERSIAN
        return when (state) {
            AssistantState.IDLE, AssistantState.LISTENING_WAKE ->
                if (fa) "بگو هی اکبر" else "Say Hey Akbar"
            AssistantState.ACTIVATED, AssistantState.LISTENING_COMMAND ->
                if (fa) "گوش می‌دم..." else "Listening..."
            AssistantState.PROCESSING ->
                if (fa) "در حال پردازش..." else "Processing..."
            AssistantState.SPEAKING ->
                if (fa) "در حال پاسخ..." else "Speaking..."
        }
    }

    private fun languageOf(command: AssistantCommand): AppLanguage = when (command) {
        is AssistantCommand.TellTime -> command.language
        is AssistantCommand.Weather -> command.language
        is AssistantCommand.LightOn -> command.language
        is AssistantCommand.LightOff -> command.language
        is AssistantCommand.Unknown -> command.language
    }

    override fun onCleared() {
        super.onCleared()
        commandTimeoutJob?.cancel()
        speech?.destroy()
        tts?.shutdown()
    }
}
