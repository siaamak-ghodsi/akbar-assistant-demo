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

    private var speechRecognizer: ContinuousSpeechRecognizer? = null
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
                    // After activation prompt, wait for command; after command reply, return to wake listening
                    if (_uiState.value.state == AssistantState.SPEAKING ||
                        _uiState.value.state == AssistantState.ACTIVATED ||
                        _uiState.value.state == AssistantState.PROCESSING
                    ) {
                        // If we just spoke activation, stay in command mode briefly
                        // handled by activate() / handleCommand()
                    }
                    resumeAfterSpeech()
                } else {
                    setIdleWaitingWake(_uiState.value.language)
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

    fun runTestCommand(command: AssistantCommand) {
        viewModelScope.launch {
            // Simulate hearing the phrase
            val heard = when (command) {
                is AssistantCommand.TellTime -> if (command.language == AppLanguage.PERSIAN) "ساعت چنده؟" else "What time is it?"
                is AssistantCommand.Weather -> if (command.language == AppLanguage.PERSIAN) "هوا چطوره؟" else "What's the weather?"
                is AssistantCommand.LightOn -> if (command.language == AppLanguage.PERSIAN) "چراغ رو روشن کن" else "Turn on the light"
                is AssistantCommand.LightOff -> if (command.language == AppLanguage.PERSIAN) "چراغ رو خاموش کن" else "Turn off the light"
                is AssistantCommand.Unknown -> command.raw
            }
            _uiState.update {
                it.copy(
                    language = when (command) {
                        is AssistantCommand.TellTime -> command.language
                        is AssistantCommand.Weather -> command.language
                        is AssistantCommand.LightOn -> command.language
                        is AssistantCommand.LightOff -> command.language
                        is AssistantCommand.Unknown -> command.language
                    },
                    lastHeard = heard,
                    state = AssistantState.PROCESSING,
                    statusText = statusFor(AssistantState.PROCESSING, when (command) {
                        is AssistantCommand.TellTime -> command.language
                        is AssistantCommand.Weather -> command.language
                        is AssistantCommand.LightOn -> command.language
                        is AssistantCommand.LightOff -> command.language
                        is AssistantCommand.Unknown -> command.language
                    })
                )
            }
            delay(250)
            executeCommand(command, fromVoice = false)
        }
    }

    fun simulateWake(language: AppLanguage) {
        onWakeDetected(language, if (language == AppLanguage.PERSIAN) "هی اکبر" else "Hey Akbar")
    }

    private fun startWakeListening() {
        activated = false
        setIdleWaitingWake(_uiState.value.language)
        ensureSpeechRecognizer()
        speechRecognizer?.setPreferredLocale(Locale("fa", "IR"))
        speechRecognizer?.start()
        _uiState.update { it.copy(state = AssistantState.LISTENING_WAKE) }
    }

    private fun restartWakeListening() {
        speechRecognizer?.stop()
        viewModelScope.launch {
            delay(200)
            if (_uiState.value.permissionGranted && !activated) {
                startWakeListening()
            }
        }
    }

    private fun ensureSpeechRecognizer() {
        if (speechRecognizer != null) return
        speechRecognizer = ContinuousSpeechRecognizer(
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
                    handleCommandText(text)
                }
            },
            onError = { message ->
                if (message.contains("permission", ignoreCase = true)) {
                    _uiState.update { it.copy(errorMessage = message) }
                }
            },
            onReady = {
                // no-op
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
        // Briefly pause recognition while TTS speaks
        speechRecognizer?.stop()
        tts?.speak(prompt, language)
        // After speak done, resumeAfterSpeech will start command listening
        commandTimeoutJob = viewModelScope.launch {
            delay(12_000)
            if (activated) {
                activated = false
                setIdleWaitingWake(language)
                restartWakeListening()
            }
        }
    }

    private fun resumeAfterSpeech() {
        if (!activated) return
        // Enter command listening after activation TTS
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
        speechRecognizer?.setPreferredLocale(locale)
        speechRecognizer?.start()
    }

    private fun handleCommandText(text: String) {
        // Ignore if still contains only wake word
        if (CommandParser.containsWakeWord(text) && text.trim().split(" ").size <= 3) {
            return
        }
        commandTimeoutJob?.cancel()
        val command = CommandParser.parse(text)
        _uiState.update {
            it.copy(
                language = when (command) {
                    is AssistantCommand.TellTime -> command.language
                    is AssistantCommand.Weather -> command.language
                    is AssistantCommand.LightOn -> command.language
                    is AssistantCommand.LightOff -> command.language
                    is AssistantCommand.Unknown -> command.language
                },
                state = AssistantState.PROCESSING,
                statusText = statusFor(
                    AssistantState.PROCESSING,
                    when (command) {
                        is AssistantCommand.TellTime -> command.language
                        is AssistantCommand.Weather -> command.language
                        is AssistantCommand.LightOn -> command.language
                        is AssistantCommand.LightOff -> command.language
                        is AssistantCommand.Unknown -> command.language
                    }
                ),
                lastHeard = text
            )
        }
        speechRecognizer?.stop()
        executeCommand(command, fromVoice = true)
    }

    private fun executeCommand(command: AssistantCommand, fromVoice: Boolean) {
        var nextLight = _uiState.value.lightOn
        when (command) {
            is AssistantCommand.LightOn -> nextLight = true
            is AssistantCommand.LightOff -> nextLight = false
            else -> Unit
        }
        val reply = ResponseBuilder.forCommand(command, nextLight)
        val language = when (command) {
            is AssistantCommand.TellTime -> command.language
            is AssistantCommand.Weather -> command.language
            is AssistantCommand.LightOn -> command.language
            is AssistantCommand.LightOff -> command.language
            is AssistantCommand.Unknown -> command.language
        }
        _uiState.update {
            it.copy(
                lightOn = nextLight,
                lastReply = reply,
                language = language,
                statusText = reply
            )
        }
        if (fromVoice) {
            activated = false
            tts?.speak(reply, language)
            // After TTS done, onSpeakDone restarts wake listening because activated=false
        } else {
            tts?.speak(reply, language)
            viewModelScope.launch {
                delay(50)
                // keep wake listening for further voice use
                if (_uiState.value.permissionGranted && speechRecognizer != null) {
                    // ensure continuous wake listening still runs under test mode
                    activated = false
                }
            }
        }
    }

    private fun setIdleWaitingWake(language: AppLanguage) {
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

    override fun onCleared() {
        super.onCleared()
        commandTimeoutJob?.cancel()
        speechRecognizer?.destroy()
        tts?.shutdown()
    }
}
