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
                        hintText = "برای صدای فارسی، بستهٔ زبان TTS را نصب کنید",
                    )
                }
            },
        )
    }

    fun clearPersianTtsInstallPrompt() {
        _uiState.update { it.copy(needsPersianTtsInstall = false) }
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
            if (sessionActive) startCommandListening() else enterWakeMode(_uiState.value.language)
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
            openSession(languageOf(command))
            val phrase = samplePhrase(command)
            appendChat(fromUser = true, phrase)
            _uiState.update {
                it.copy(
                    language = languageOf(command),
                    lastHeard = phrase,
                    state = AssistantState.PROCESSING,
                    statusText = if (languageOf(command) == AppLanguage.PERSIAN) {
                        "یک لحظه…"
                    } else {
                        "One moment…"
                    }
                )
            }
            delay(120)
            executeCommand(command)
        }
    }

    fun onDraftChanged(text: String) {
        _uiState.update { it.copy(draftText = text) }
    }

    fun sendTextCommand() {
        val text = _uiState.value.draftText.trim()
        if (text.isBlank()) return
        _uiState.update { it.copy(draftText = "") }

        if (!sessionActive) {
            openSession(CommandParser.detectLanguage(text))
        }

        if (CommandParser.containsWakeWord(text) && CommandParser.stripWakeWord(text).isBlank()) {
            onWakeDetected(CommandParser.wakeLanguage(text), text)
            return
        }

        activated = true
        awaitingCommand = true
        handleCommandAlternatives(listOf(text))
    }

    fun onAppForeground() {
        val wasHandoff = HandoffCoordinator.active
        // End handoff FGS first, then clear return/CALL notifications *before* mic restart
        // so CATEGORY_CALL leftovers cannot poison SpeechRecognizer.
        HandoffCoordinator.endHandoff(getApplication())
        HandoffCoordinator.clearReturnNotifications(getApplication())
        HandoffListenService.cancelNotifications(getApplication())

        if (!_uiState.value.permissionGranted) return
        try {
            if (speaking) {
                // Let TTS finish; onSpeakDone will resume listening.
                return
            }
            // Only recreate recognizer when returning from Camera/Gmail. Destroying on
            // every onStart broke the multi-command loop (one command then silence).
            if (wasHandoff) {
                speech?.destroy()
                speech = null
            }
            if (sessionActive) startCommandListening() else enterWakeMode(_uiState.value.language)
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    fun onAppBackground() {
        // While Camera/Gmail is open we keep listening via the handoff FGS.
        if (HandoffCoordinator.active) return
        try {
            speech?.pause()
        } catch (_: Exception) {
        }
    }

    fun releaseHardware() {
        flashlight.turnOffQuietly()
        _uiState.update { it.copy(lightOn = false) }
    }

    private fun openSession(language: AppLanguage) {
        sessionActive = true
        activated = true
        awaitingCommand = true
        _uiState.update {
            it.copy(
                sessionActive = true,
                language = language,
                statusText = if (language == AppLanguage.PERSIAN) {
                    "جلسه فعال — دستور بدهید"
                } else {
                    "Session active — give a command"
                },
                hintText = if (language == AppLanguage.PERSIAN) {
                    "دیگر لازم نیست هی اکبر بگویید"
                } else {
                    "No need to say Hey Akbar again"
                }
            )
        }
    }

    private fun enterWakeMode(language: AppLanguage) {
        activated = false
        awaitingCommand = false
        resumeCommandAfterSpeak = false
        returnToWakeAfterSpeak = false
        if (!sessionActive) {
            ensureSpeech()
            // Prefer fa-IR for «هی اکبر»; occasional en-US for "Hey Akbar".
            speech?.setBilingualWakeMode(true)
            speech?.setPreferredLocale(Locale("fa", "IR"))
            speech?.start()
            _uiState.update {
                it.copy(
                    state = AssistantState.LISTENING_WAKE,
                    language = language,
                    sessionActive = false,
                    statusText = if (language == AppLanguage.PERSIAN) {
                        "در حال گوش دادن"
                    } else {
                        "Listening"
                    },
                    hintText = if (language == AppLanguage.PERSIAN) {
                        "بگویید هی اکبر / Hey Akbar — بعد دستور بدهید"
                    } else {
                        "Say Hey Akbar / هی اکبر — then give a command"
                    },
                    lastHeard = "",
                    errorMessage = null,
                    rmsLevel = 0f
                )
            }
        } else {
            startCommandListening()
        }
    }

    private fun ensureSpeech() {
        if (speech != null) return
        speech = ContinuousSpeechRecognizer(
            context = getApplication(),
            onPartialResult = { text ->
                if (speaking || text.isBlank()) return@ContinuousSpeechRecognizer
                // Always show live transcript so the demo proves the mic is hearing.
                _uiState.update { it.copy(lastHeard = text) }
                if (!activated && CommandParser.containsWakeWord(text)) {
                    val leftover = CommandParser.stripWakeWord(text)
                    // Wake alone, or wake + short trailing noise from partial ASR.
                    if (leftover.isBlank() || leftover.length <= 2) {
                        onWakeDetected(CommandParser.wakeLanguage(text), text)
                    }
                }
            },
            onFinalResult = { text ->
                if (speaking || text.isBlank()) return@ContinuousSpeechRecognizer
                _uiState.update { it.copy(lastHeard = text) }
            },
            onFinalAlternatives = { matches ->
                if (speaking || matches.isEmpty()) return@ContinuousSpeechRecognizer
                _uiState.update { it.copy(lastHeard = matches.first()) }
                when {
                    !activated -> {
                        val wakeHit = matches.firstOrNull { CommandParser.containsWakeWord(it) }
                        if (wakeHit != null) {
                            onWakeDetected(CommandParser.wakeLanguage(wakeHit), wakeHit)
                        }
                    }
                    awaitingCommand -> {
                        handleCommandAlternatives(matches)
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
        if (speaking) return
        commandTimeoutJob?.cancel()
        openSession(language)
        appendChat(fromUser = true, heard)

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
                    "ساعت · هوا · چراغ‌قوه — بدون تکرار بیدارباش"
                } else {
                    "Time · weather · flashlight — no re-wake needed"
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
        appendChat(fromUser = false, prompt)
        _uiState.update {
            it.copy(
                statusText = prompt,
                lastReply = prompt
            )
        }
        resumeCommandAfterSpeak = true
        returnToWakeAfterSpeak = false
        speakReply(prompt, language)
        armSessionTimeout(language)
    }

    private fun startCommandListening() {
        if (!activated || !sessionActive) return
        awaitingCommand = true
        ensureSpeech()
        _uiState.update {
            it.copy(
                state = AssistantState.LISTENING_COMMAND,
                sessionActive = true,
                statusText = if (it.language == AppLanguage.PERSIAN) {
                    "گوش می‌دهم"
                } else {
                    "Listening"
                },
                hintText = if (it.language == AppLanguage.PERSIAN) {
                    "دستور بعدی را بگویید یا بنویسید"
                } else {
                    "Say or type the next command"
                }
            )
        }
        // Lock to session language for clearer command recognition.
        speech?.setBilingualWakeMode(false)
        val locale = if (_uiState.value.language == AppLanguage.PERSIAN) {
            Locale("fa", "IR")
        } else {
            Locale.US
        }
        speech?.setPreferredLocale(locale)
        speech?.start()
        armSessionTimeout(_uiState.value.language)
    }

    private fun handleCommandAlternatives(matches: List<String>) {
        val text = matches.firstOrNull { it.isNotBlank() } ?: return
        val stripped = CommandParser.stripWakeWord(text)
        if (stripped.isBlank()) {
            if (CommandParser.containsWakeWord(text)) {
                val wakeLang = CommandParser.wakeLanguage(text)
                val prompt = ResponseBuilder.activationPrompt(wakeLang)
                appendChat(fromUser = false, prompt)
