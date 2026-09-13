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
        if (!_uiState.value.permissionGranted) return
        try {
            if (sessionActive) startCommandListening() else enterWakeMode(_uiState.value.language)
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    fun onAppBackground() {
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
            // Alternate fa-IR / en-US so both «هی اکبر» and "Hey Akbar" are heard.
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
                if (awaitingCommand && activated && !speaking) {
                    _uiState.update { it.copy(lastHeard = text) }
                }
                if (!activated && !speaking && CommandParser.containsWakeWord(text)) {
                    if (CommandParser.stripWakeWord(text).isBlank()) {
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
                when {
                    !activated -> {
                        val wakeHit = matches.firstOrNull { CommandParser.containsWakeWord(it) }
                        if (wakeHit != null) {
                            onWakeDetected(CommandParser.wakeLanguage(wakeHit), wakeHit)
                        }
                    }
                    awaitingCommand -> {
                        _uiState.update { it.copy(lastHeard = matches.first()) }
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
                _uiState.update {
                    it.copy(
                        language = wakeLang,
                        lastReply = prompt,
                        statusText = prompt,
                    )
                }
                resumeCommandAfterSpeak = true
                speakReply(prompt, wakeLang)
            }
            return
        }

        commandTimeoutJob?.cancel()
        awaitingCommand = false
        speech?.pause()
        appendChat(fromUser = true, text)

        val command = CommandParser.parseBest(matches)
        // If ASR returned Latin but we were stuck on Persian (or vice versa),
        // follow the language of the spoken text for the reply + next listen.
        val language = when {
            command !is AssistantCommand.Unknown -> languageOf(command)
            CommandParser.detectLanguage(text) == AppLanguage.ENGLISH -> AppLanguage.ENGLISH
            else -> languageOf(command)
        }
        val resolved = when {
            command is AssistantCommand.Unknown && language == AppLanguage.ENGLISH ->
                CommandParser.parse(text).let { parsed ->
                    if (parsed is AssistantCommand.Unknown) {
                        AssistantCommand.Unknown(AppLanguage.ENGLISH, text)
                    } else {
                        parsed
                    }
                }
            else -> command
        }
        _uiState.update {
            it.copy(
                language = languageOf(resolved).let { lang ->
                    if (resolved is AssistantCommand.Unknown) language else lang
                },
                lastHeard = text,
                state = AssistantState.PROCESSING,
                statusText = if (language == AppLanguage.PERSIAN) "یک لحظه…" else "One moment…",
            )
        }
        executeCommand(
            if (resolved is AssistantCommand.Unknown) {
                AssistantCommand.Unknown(language, text)
            } else {
                resolved
            },
        )
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
                        reply = ResponseBuilder.forCommand(AssistantCommand.LightOn(language))
                    }
                    FlashlightController.Result.NO_PERMISSION -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "اجازه دوربین را بدهید، بعد دوباره بگویید."
                        } else {
                            "Allow camera access, then ask again."
                        }
                        _uiState.update { it.copy(needsCameraPermission = true) }
                    }
                    FlashlightController.Result.NO_FLASH -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "این گوشی چراغ‌قوه ندارد."
                        } else {
                            "This phone has no flashlight."
                        }
                    }
                    else -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "الان نتوانستم چراغ را روشن کنم."
                        } else {
                            "I couldn't turn the light on just now."
                        }
                    }
                }
            }
            is AssistantCommand.LightOff -> {
                when (flashlight.setEnabled(false)) {
                    FlashlightController.Result.OFF -> {
                        nextLight = false
                        reply = ResponseBuilder.forCommand(AssistantCommand.LightOff(language))
                    }
                    FlashlightController.Result.NO_PERMISSION -> {
                        reply = if (language == AppLanguage.PERSIAN) {
                            "اجازه دوربین را بدهید، بعد دوباره بگویید."
                        } else {
                            "Allow camera access, then ask again."
                        }
                        _uiState.update { it.copy(needsCameraPermission = true) }
                    }
                    FlashlightController.Result.NO_FLASH -> {
                        nextLight = false
                        reply = if (language == AppLanguage.PERSIAN) {
                            "این گوشی چراغ‌قوه ندارد."
                        } else {
                            "This phone has no flashlight."
                        }
                    }
                    else -> {
                        nextLight = flashlight.isOn
                        reply = if (language == AppLanguage.PERSIAN) {
                            "الان نتوانستم چراغ را خاموش کنم."
                        } else {
                            "I couldn't turn the light off just now."
                        }
                    }
                }
            }
            else -> Unit
        }

        awaitingCommand = false
        resumeCommandAfterSpeak = true
        returnToWakeAfterSpeak = false
        sessionActive = true

        appendChat(fromUser = false, reply)
        _uiState.update {
            it.copy(
                lightOn = nextLight,
                lastReply = reply,
                language = language,
                statusText = reply,
                hintText = if (language == AppLanguage.PERSIAN) {
                    "می‌توانید دستور بعدی را بگویید"
                } else {
                    "You can give another command"
                },
                errorMessage = error,
                state = AssistantState.SPEAKING,
                sessionActive = true,
                lastHeard = ""
            )
        }
        speakReply(reply, language)
        armSessionTimeout(language)
    }

    private fun armSessionTimeout(language: AppLanguage) {
        commandTimeoutJob?.cancel()
        commandTimeoutJob = viewModelScope.launch {
            delay(120_000)
            if (sessionActive && !speaking) {
                sessionActive = false
                activated = false
                awaitingCommand = false
                _uiState.update {
                    it.copy(
                        sessionActive = false,
                        statusText = if (language == AppLanguage.PERSIAN) {
                            "جلسه تمام شد — دوباره هی اکبر بگویید"
                        } else {
                            "Session ended — say Hey Akbar again"
                        }
                    )
                }
                enterWakeMode(language)
            }
        }
    }

    private fun speakReply(text: String, language: AppLanguage) {
        if (text.isBlank()) {
            if (resumeCommandAfterSpeak || sessionActive) {
                resumeCommandAfterSpeak = false
                startCommandListening()
            }
            return
        }
        try {
            speaking = true
            // Stop mic + restore any muted streams before TTS so the reply is audible.
            speech?.pause()
            _uiState.update { it.copy(state = AssistantState.SPEAKING) }
            // Tiny delay lets the recognizer fully release the audio path.
            viewModelScope.launch {
                delay(180)
                if (!speaking) return@launch
                try {
                    tts?.speak(text, language)
                } catch (e: Exception) {
                    speaking = false
                    _uiState.update { it.copy(errorMessage = e.message) }
                    if (resumeCommandAfterSpeak || sessionActive) {
                        resumeCommandAfterSpeak = false
                        startCommandListening()
                    }
                }
            }
        } catch (e: Exception) {
            speaking = false
            _uiState.update { it.copy(errorMessage = e.message) }
            if (resumeCommandAfterSpeak || sessionActive) {
                resumeCommandAfterSpeak = false
                startCommandListening()
            }
        }
    }

    private fun appendChat(fromUser: Boolean, text: String) {
        if (text.isBlank()) return
        val msg = ChatMessage(
            id = messageIds.getAndIncrement(),
            fromUser = fromUser,
            text = text
        )
        _uiState.update { it.copy(chatMessages = it.chatMessages + msg) }
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
