package com.akbar.assistant.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akbar.assistant.demo.commands.AssistantCommand
import com.akbar.assistant.demo.commands.CommandParser
import com.akbar.assistant.demo.commands.ResponseBuilder
import com.akbar.assistant.demo.device.AppLauncher
import com.akbar.assistant.demo.device.FlashlightController
import com.akbar.assistant.demo.handoff.HandoffCoordinator
import com.akbar.assistant.demo.handoff.HandoffListenService
import com.akbar.assistant.demo.speech.AssistantTts
import com.akbar.assistant.demo.speech.ContinuousSpeechRecognizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
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
    private val apps = AppLauncher(application)
    private var speech: ContinuousSpeechRecognizer? = null
    private var tts: AssistantTts? = null

    private var commandTimeoutJob: Job? = null
    private var activated = false
    private var awaitingCommand = false
    private var speaking = false
    private var resumeCommandAfterSpeak = false
    private var returnToWakeAfterSpeak = false
    private var sessionActive = false
    private val messageIds = AtomicLong(1L)

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
                        if (!sessionActive) {
                            activated = false
                            awaitingCommand = false
                            enterWakeMode(_uiState.value.language)
                        } else {
                            startCommandListening()
                        }
                    }
                    else -> {
                        if (sessionActive) {
                            startCommandListening()
                        } else if (!activated) {
                            enterWakeMode(_uiState.value.language)
                        }
                    }
                }
            },
            onPersianVoiceMissing = {
                _uiState.update {
                    it.copy(
                        needsPersianTtsInstall = true,
                        hintText = "\u0628\u0631\u0627\u06cc \u0635\u062f\u0627\u06cc \u0641\u0627\u0631\u0633\u06cc\u060c \u0628\u0633\u062a\u0647\u0654 \u0632\u0628\u0627\u0646 TTS \u0631\u0627 \u0646\u0635\u0628 \u06a9\u0646\u06cc\u062f",
                    )
                }
            },
        )
    }

    fun clearPersianTtsInstallPrompt() {
        _uiState.update { it.copy(needsPersianTtsInstall = false) }
    }

    // CONTENT_CONTINUES_IN_PART2
}
