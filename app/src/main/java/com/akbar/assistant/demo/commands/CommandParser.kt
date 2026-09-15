package com.akbar.assistant.demo.commands

import com.akbar.assistant.demo.AppLanguage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class AssistantCommand {
    data class TellTime(val language: AppLanguage) : AssistantCommand()
    data class Weather(val language: AppLanguage) : AssistantCommand()
    data class LightOn(val language: AppLanguage) : AssistantCommand()
    data class LightOff(val language: AppLanguage) : AssistantCommand()
    data class CameraOn(val language: AppLanguage) : AssistantCommand()
    data class CameraOff(val language: AppLanguage) : AssistantCommand()
    data class GmailOpen(val language: AppLanguage) : AssistantCommand()
    data class GmailClose(val language: AppLanguage) : AssistantCommand()
    data class Unknown(val language: AppLanguage, val raw: String) : AssistantCommand()
}

object CommandParser {

    private val persianChars = Regex("[\\u0600-\\u06FF]")
    private val latinLetters = Regex("[A-Za-z]")

    fun detectLanguage(text: String): AppLanguage {
        val hasPersian = persianChars.containsMatchIn(text)
        val hasLatin = latinLetters.containsMatchIn(text)
        val n = normalize(text)
        // Strong English wake / command cues win over accidental Persian glyphs from ASR.
        if (looksEnglish(n)) return AppLanguage.ENGLISH
        if (hasPersian && !hasLatin) return AppLanguage.PERSIAN
        if (hasLatin && !hasPersian) return AppLanguage.ENGLISH
        return if (hasPersian) AppLanguage.PERSIAN else AppLanguage.ENGLISH
    }

    private fun looksEnglish(n: String): Boolean {
        val englishCues = listOf(
            "hey intel tech", "hi intel tech", "hey inteltek", "hey intel",
            "intel tech", "inteltek", "intel tec",
            "what time", "weather", "temperature", "forecast", "flashlight",
            "turn on", "turn off", "switch on", "switch off", "lights",
            "camera", "gmail", "mail", "email",
        )
        if (englishCues.any { n.contains(it) }) return true
        val words = n.split(" ")
        val latinWordCount = words.count { it.any { ch -> ch in 'a'..'z' } }
        val persianWordCount = words.count { persianChars.containsMatchIn(it) }
        return latinWordCount > 0 && latinWordCount >= persianWordCount
    }

    fun normalize(text: String): String {
        return text.trim()
            .lowercase(Locale.ROOT)
            .replace('\u064a', '\u06cc')
            .replace('\u0643', '\u06a9')
            .replace('\u06c0', '\u0647')
            .replace('\u0629', '\u0647')
            .replace('\u0624', '\u0648')
            .replace('\u0623', '\u0627')
            .replace('\u0625', '\u0627')
            .replace('\u0622', '\u0627')
            .replace('\u200c', ' ')
            .replace(Regex("[\u061f?!,.\u060c\u061b:\"'\u2026\\-_/\\\\()\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun containsWakeWord(text: String): Boolean {
        val n = normalize(text)
        val compact = n.replace(" ", "")
        // Primary: «هی اینتل تک» / "Hey intel tech" — also bare «اینتل تک» / "intel tech".
        val phrases = listOf(
            "هی اینتل تک", "های اینتل تک", "حی اینتل تک", "هی این تلتک",
            "هی اینتل تک", "هی اینتل‌تک", "هی این تِل تک",
            "اینتل تک", "اینتلتک", "این تل تک", "اینتل‌تک",
            "هی intel tech", "hey اینتل تک", "هی inteltek",
            "hey intel tech", "hi intel tech", "hay intel tech",
            "okay intel tech", "ok intel tech", "yo intel tech",
            "hey inteltek", "hi inteltek", "hey intel tec", "hey intel tek",
            "hey intel", "hi intel tech", "hei intel tech", "he intel tech",
            "intel tech", "inteltek", "intel tec", "intel tek", "inteltech",
        )
        if (phrases.any { n.contains(it) }) return true
        if (compact.contains("هیاینتلتک") || compact.contains("هایاینتلتک") ||
            compact.contains("حیاینتلتک") || compact.contains("اینتلتک") ||
            compact.contains("heyinteltech") || compact.contains("hiinteltech") ||
            compact.contains("inteltech") || compact.contains("inteltek")
        ) {
            return true
        }
        // Short utterance that is just the brand (or brand + hey).
        val words = n.split(" ").filter { it.isNotBlank() }
        return words.size <= 5 && (
            (n.contains("اینتل") && (n.contains("تک") || n.contains("tech") || n.contains("tek") || n.contains("tec"))) ||
                (n.contains("intel") && (n.contains("tech") || n.contains("tek") || n.contains("tec") || n.contains("تک")))
            )
    }

    fun wakeLanguage(text: String): AppLanguage {
        val n = normalize(text)
        val englishWake = listOf(
            "hey intel tech", "hi intel tech", "hay intel tech", "okay intel tech",
            "ok intel tech", "yo intel tech", "hey inteltek", "hey intel tec",
            "hey intel tek", "hei intel tech", "heyinteltech", "hiinteltech",
            "intel tech", "inteltek", "inteltech", "intel tec", "intel tek",
        )
        if (englishWake.any { n.contains(it) || n.replace(" ", "").contains(it.replace(" ", "")) }) {
            return AppLanguage.ENGLISH
        }
        if (n == "intel" || n == "inteltech" || n == "inteltek") {
            return AppLanguage.ENGLISH
        }
        return detectLanguage(text)
    }

    fun stripWakeWord(text: String): String {
        var n = normalize(text)
        // Longer phrases first so "hey intel tech" is removed before "intel tech".
        val wakes = listOf(
            "هی اینتل تک", "های اینتل تک", "حی اینتل تک", "هی اینتلتک",
            "هی این تل تک", "هی اینتل‌تک",
            "hey intel tech", "hi intel tech", "hay intel tech",
            "okay intel tech", "ok intel tech", "yo intel tech",
            "hey inteltek", "hi inteltek", "hey intel tec", "hey intel tek",
            "hei intel tech", "he intel tech", "hey intel",
            "هی intel tech", "hey اینتل تک", "هی inteltek",
            "اینتل تک", "اینتلتک", "این تل تک", "اینتل‌تک",
            "intel tech", "inteltek", "intel tec", "intel tek", "inteltech",
        )
        wakes.forEach { wake -> n = n.replace(wake, " ") }
        return n.replace(Regex("\\s+"), " ").trim()
    }

    /** Prefer the first alternative that maps to a known command. */
    fun parseBest(candidates: List<String>): AssistantCommand {
        if (candidates.isEmpty()) {
            return AssistantCommand.Unknown(AppLanguage.PERSIAN, "")
        }
        var bestUnknown: AssistantCommand.Unknown? = null
        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            val parsed = parse(candidate)
            if (parsed !is AssistantCommand.Unknown) return parsed
            if (bestUnknown == null) bestUnknown = parsed
        }
        return bestUnknown
            ?: AssistantCommand.Unknown(detectLanguage(candidates.first()), candidates.first())
    }

    fun parse(text: String): AssistantCommand {
        val primary = detectLanguage(text)
        val n = stripWakeWord(text).ifBlank { normalize(text) }
        val compact = n.replace(" ", "")
        parseWithLanguage(n, compact, primary)?.let { return it }
        val secondary =
            if (primary == AppLanguage.PERSIAN) AppLanguage.ENGLISH else AppLanguage.PERSIAN
        parseWithLanguage(n, compact, secondary)?.let { return it }
        return AssistantCommand.Unknown(primary, text)
    }

    private fun parseWithLanguage(
        n: String,
        compact: String,
        language: AppLanguage,
    ): AssistantCommand? {
        return when {
            isTime(n, compact, language) -> AssistantCommand.TellTime(language)
            isWeather(n, compact, language) -> AssistantCommand.Weather(language)
            isCameraOn(n, language) -> AssistantCommand.CameraOn(language)
            isCameraOff(n, language) -> AssistantCommand.CameraOff(language)
            isGmailOpen(n, language) -> AssistantCommand.GmailOpen(language)
            isGmailClose(n, language) -> AssistantCommand.GmailClose(language)
            isLightOn(n, language) -> AssistantCommand.LightOn(language)
            isLightOff(n, language) -> AssistantCommand.LightOff(language)
            else -> null
        }
    }

    private fun isTime(n: String, compact: String, language: AppLanguage): Boolean {
        if (language == AppLanguage.PERSIAN) {
            val phrases = listOf(
                "\u0633\u0627\u0639\u062a", "\u0632\u0645\u0627\u0646", "\u0633\u0627\u0639\u062a \u0686\u0646\u062f", "\u0633\u0627\u0639\u062a \u0686\u0646\u062f\u0647", "\u0633\u0627\u0639\u062a \u0686\u0646\u062f \u0627\u0633\u062a",
                "\u0633\u0627\u0639\u062a \u0686\u0646\u062f \u0634\u062f", "\u0686\u0647 \u0633\u0627\u0639\u062a\u06cc", "\u0686\u0647 \u0633\u0627\u0639\u062a\u06cc\u0647", "\u0627\u0644\u0627\u0646 \u0633\u0627\u0639\u062a",
                "\u0633\u0627\u0639\u062a \u0627\u0644\u0627\u0646", "\u0628\u06af\u0648 \u0633\u0627\u0639\u062a", "\u0633\u0627\u0639\u062a\u0648 \u0628\u06af\u0648", "\u0633\u0627\u0639\u062a \u0631\u0627 \u0628\u06af\u0648",
                "\u0686\u0646\u062f \u0634\u062f\u0647", "\u0686\u0646\u062f \u0627\u0633\u062a \u0627\u0644\u0627\u0646", "\u0633\u0627\u0639\u062a\u0648 \u0628\u06af\u0648 \u0628\u0628\u06cc\u0646\u0645",
            )
            if (phrases.any { n.contains(it) }) return true
            if (compact.contains("\u0633\u0627\u0639\u062a\u0686\u0646\u062f") || compact.contains("\u0686\u0646\u062f\u0633\u0627\u0639\u062a") ||
                compact.contains("\u0686\u0647\u0633\u0627\u0639\u062a\u06cc") || compact == "\u0632\u0645\u0627\u0646"
            ) {
                return true
            }
            return n.contains("\u0633\u0627\u0639\u062a") && (
                n.contains("\u0686\u0646\u062f") || n.contains("\u0686\u0647") || n.contains("\u0627\u0644\u0627\u0646") ||
                    n.contains("\u0628\u06af\u0648") || n.contains("\u0627\u0633\u062a") || n.contains("\u0634\u062f\u0647")
                )
        }
        return n.contains("time") || n.contains("clock") || n.contains("what time") ||
            n.contains("tell me the time") || n.contains("current time") ||
            n.contains("tell time") || n == "time" ||
            n.contains("o clock") || n.contains("oclock") ||
            n.contains("what's the time") || n.contains("whats the time") ||
            n.contains("tell me time")
    }

    private fun isWeather(n: String, compact: String, language: AppLanguage): Boolean {
        if (language == AppLanguage.PERSIAN) {
            val phrases = listOf(
                "\u0647\u0648\u0627", "\u0627\u0628 \u0648 \u0647\u0648\u0627", "\u0627\u0628\u0648\u0647\u0648\u0627", "\u062f\u0645\u0627", "\u0686\u0646\u062f \u062f\u0631\u062c\u0647",
                "\u0647\u0648\u0627 \u0686\u0637\u0648\u0631", "\u0647\u0648\u0627 \u0686\u0637\u0648\u0631\u0647", "\u0647\u0648\u0627 \u0686\u0637\u0648\u0631 \u0627\u0633\u062a", "\u0647\u0648\u0627 \u0686\u06cc\u0647",
                "\u0647\u0648\u0627 \u0686\u06cc \u0627\u0633\u062a", "\u0647\u0648\u0627 \u062e\u0648\u0628\u0647", "\u0647\u0648\u0627 \u062e\u0648\u0628 \u0627\u0633\u062a", "\u0648\u0636\u0639\u06cc\u062a \u0647\u0648\u0627",
                "\u0647\u0648\u0627\u06cc \u0627\u0645\u0631\u0648\u0632", "\u0647\u0648\u0627\u06cc \u0628\u06cc\u0631\u0648\u0646", "\u062f\u0631\u062c\u0647 \u0647\u0648\u0627", "\u06af\u0631\u0645 \u0627\u0633\u062a",
                "\u0633\u0631\u062f \u0627\u0633\u062a", "\u0628\u0627\u0631\u0648\u0646", "\u0628\u0627\u0631\u0627\u0646", "\u067e\u06cc\u0634 \u0628\u06cc\u0646\u06cc \u0647\u0648\u0627",
            )
            if (phrases.any { n.contains(it) }) return true
            if (compact.contains("\u0627\u0628\u0648\u0647\u0648\u0627") || compact.contains("\u0647\u0648\u0627\u0686\u0637\u0648\u0631") ||
                compact.contains("\u0647\u0648\u0627\u0686\u06cc\u0647") || compact.contains("\u0686\u0646\u062f\u062f\u0631\u062c\u0647") ||
                compact.contains("\u0648\u0636\u0639\u06cc\u062a\u0647\u0648\u0627")
            ) {
                return true
            }
            return n.contains("\u0647\u0648\u0627") || n.contains("\u062f\u0645\u0627") || n.contains("\u062f\u0631\u062c\u0647")
        }
        return n.contains("weather") || n.contains("temperature") || n.contains("forecast") ||
            n.contains("how hot") || n.contains("how's the weather") ||
            n.contains("how is the weather") || n.contains("is it raining") ||
            n.contains("hows the weather") || n.contains("whats the weather") ||
            n.contains("what's the weather") || n.contains("how cold")
    }

    private fun mentionsLight(n: String): Boolean {
        return n.contains("\u0686\u0631\u0627\u063a") || n.contains("\u0644\u0627\u0645\u067e") || n.contains("\u0646\u0648\u0631") ||
            n.contains("\u0641\u0644\u0634") || n.contains("\u0686\u0631\u0627\u063a \u0642\u0648\u0647") || n.contains("\u0686\u0631\u0627\u063a\u0642\u0648\u0647") ||
            n.contains("\u0641\u0644\u0627\u0634") || n.contains("light") || n.contains("lights") ||
            n.contains("lamp") || n.contains("bulb") || n.contains("flashlight") ||
            n.contains("torch") || n.contains("flash") || n.contains("flash light")
    }

    private fun isLightOn(n: String, language: AppLanguage): Boolean {
        if (!mentionsLight(n)) return false
        if (language == AppLanguage.PERSIAN) {
            return n.contains("\u0631\u0648\u0634\u0646") || n.contains("\u0628\u0627\u0632 \u06a9\u0646") || n.contains("\u0628\u0627\u0632\u06a9\u0646") ||
                n.contains("\u0641\u0639\u0627\u0644") || n.contains("\u0631\u0648\u0634\u0646 \u06a9\u0646") || n.contains("\u0631\u0648\u0634\u0646\u06a9\u0646") ||
                n.contains("\u0628\u0632\u0646") || n.contains("\u0631\u0648\u0634\u0646 \u0634\u0648") || n.contains("\u0686\u0631\u0627\u063a \u0631\u0648\u0634\u0646")
        }
        return (n.contains("turn on") || n.contains("switch on") || n.contains("enable") ||
            n.contains("light on") || n.contains("lights on") ||
            n.contains("flashlight on") || Regex("\\bon\\b").containsMatchIn(n)) &&
            !n.contains("off")
    }

    private fun isLightOff(n: String, language: AppLanguage): Boolean {
        if (!mentionsLight(n)) return false
        if (language == AppLanguage.PERSIAN) {
            return n.contains("\u062e\u0627\u0645\u0648\u0634") || n.contains("\u0628\u0628\u0646\u062f") || n.contains("\u0642\u0637\u0639") ||
                n.contains("\u0628\u0633\u062a\u0647") || n.contains("\u062e\u0627\u0645\u0648\u0634 \u06a9\u0646") || n.contains("\u062e\u0627\u0645\u0648\u0634\u06a9\u0646") ||
                n.contains("\u0628\u0628\u0646\u062f\u0634") || n.contains("\u0686\u0631\u0627\u063a \u062e\u0627\u0645\u0648\u0634")
        }
        return n.contains("turn off") || n.contains("switch off") || n.contains("disable") ||
            n.contains("light off") || n.contains("lights off") ||
            n.contains("flashlight off") || Regex("\\boff\\b").containsMatchIn(n)
    }
    private fun mentionsCamera(n: String): Boolean {
        return n.contains("\u062f\u0648\u0631\u0628\u06cc\u0646") || n.contains("\u062f\u0648\u0631\u0628\u064a\u0646") || n.contains("camera") ||
            n.contains("webcam") || n.contains("cam")
    }

    private fun isCameraOn(n: String, language: AppLanguage): Boolean {
        if (!mentionsCamera(n)) return false
        if (language == AppLanguage.PERSIAN) {
            return n.contains("\u0631\u0648\u0634\u0646") || n.contains("\u0628\u0627\u0632") || n.contains("\u0641\u0639\u0627\u0644") ||
                n.contains("\u0634\u0631\u0648\u0639") || n.contains("\u0628\u0632\u0646")
        }
        return n.contains("turn on") || n.contains("switch on") || n.contains("open") ||
            n.contains("start") || n.contains("enable") ||
            (Regex("\\bon\\b").containsMatchIn(n) && !n.contains("off"))
    }

    private fun isCameraOff(n: String, language: AppLanguage): Boolean {
        if (!mentionsCamera(n)) return false
        if (language == AppLanguage.PERSIAN) {
            return n.contains("\u062e\u0627\u0645\u0648\u0634") || n.contains("\u0628\u0628\u0646\u062f") || n.contains("\u0642\u0637\u0639") ||
                n.contains("\u0645\u062a\u0648\u0642\u0641") || n.contains("\u0628\u0633\u062a\u0647")
        }
        return n.contains("turn off") || n.contains("switch off") || n.contains("close") ||
            n.contains("stop") || n.contains("disable") || Regex("\\boff\\b").containsMatchIn(n)
    }

    private fun mentionsGmail(n: String): Boolean {
        return n.contains("\u062c\u06cc\u0645\u06cc\u0644") || n.contains("\u062c\u06cc\u0645\u064a\u0644") || n.contains("\u062c\u06cc \u0645\u06cc\u0644") ||
            n.contains("\u0627\u06cc\u0645\u06cc\u0644") || n.contains("\u0627\u06cc \u0645\u064a\u0644") ||
            n.contains("gmail") || n.contains("g mail") || n.contains("email") ||
            n.contains("inbox") || (n.contains("mail") && !n.contains("male"))
    }

    private fun isGmailOpen(n: String, language: AppLanguage): Boolean {
        if (!mentionsGmail(n)) return false
        if (language == AppLanguage.PERSIAN) {
            return n.contains("\u0628\u0627\u0632") || n.contains("\u0631\u0648\u0634\u0646") || n.contains("\u0628\u0627\u0632 \u06a9\u0646") ||
                n.contains("\u0628\u0627\u0632\u06a9\u0646") || n.contains("\u0628\u06cc\u0627\u0631") || n.contains("\u0646\u0634\u0627\u0646") ||
                n.contains("\u0627\u062c\u0631\u0627") || n == "\u062c\u06cc\u0645\u06cc\u0644" || n == "\u0627\u06cc\u0645\u06cc\u0644" || n.contains("\u062c\u06cc\u0645\u06cc\u0644 \u0631\u0648")
        }
        return n.contains("open") || n.contains("launch") || n.contains("start") ||
            n.contains("show") || n.contains("turn on") ||
            n == "gmail" || n == "email" || n == "mail" || n.contains("open gmail")
    }

    private fun isGmailClose(n: String, language: AppLanguage): Boolean {
        if (!mentionsGmail(n)) return false
        if (language == AppLanguage.PERSIAN) {
            return n.contains("\u0628\u0628\u0646\u062f") || n.contains("\u062e\u0627\u0645\u0648\u0634") || n.contains("\u0628\u0633\u062a\u0647") ||
                n.contains("\u0642\u0637\u0639") || n.contains("\u0628\u0628\u0646\u062f\u0634")
        }
        return n.contains("close") || n.contains("quit") || n.contains("exit") ||
            n.contains("turn off") || n.contains("stop")
    }

}

object ResponseBuilder {

    private val persianDigits = charArrayOf('\u06f0', '\u06f1', '\u06f2', '\u06f3', '\u06f4', '\u06f5', '\u06f6', '\u06f7', '\u06f8', '\u06f9')

    fun activationPrompt(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "\u0628\u0644\u0647\u060c \u0628\u0641\u0631\u0645\u0627\u06cc\u06cc\u062f. \u0645\u06cc\u200c\u062a\u0648\u0627\u0646\u06cc\u062f \u0628\u06af\u0648\u06cc\u06cc\u062f \u0633\u0627\u0639\u062a\u060c \u0647\u0648\u0627\u060c \u0686\u0631\u0627\u063a\u200c\u0642\u0648\u0647\u060c \u062f\u0648\u0631\u0628\u06cc\u0646 \u06cc\u0627 \u062c\u06cc\u0645\u06cc\u0644."
        } else {
            "Yes? You can ask about time, weather, flashlight, camera, or Gmail."
        }
    }

    fun forCommand(command: AssistantCommand): String {
        return when (command) {
            is AssistantCommand.TellTime -> time(command.language)
            is AssistantCommand.Weather -> weather(command.language)
            is AssistantCommand.LightOn ->
                if (command.language == AppLanguage.PERSIAN) {
                    "\u062d\u062a\u0645\u0627\u064b. \u0686\u0631\u0627\u063a\u200c\u0642\u0648\u0647 \u0627\u0644\u0627\u0646 \u0631\u0648\u0634\u0646 \u0634\u062f."
                } else {
                    "Sure. The flashlight is on now."
                }
            is AssistantCommand.LightOff ->
                if (command.language == AppLanguage.PERSIAN) {
                    "\u0628\u0627\u0634\u0647. \u0686\u0631\u0627\u063a\u200c\u0642\u0648\u0647 \u062e\u0627\u0645\u0648\u0634 \u0634\u062f."
                } else {
                    "Okay. The flashlight is off."
                }
            is AssistantCommand.CameraOn ->
                if (command.language == AppLanguage.PERSIAN) {
                    "\u0628\u0627\u0634\u0647. \u062f\u0648\u0631\u0628\u06cc\u0646 \u0631\u0627 \u0628\u0627\u0632 \u06a9\u0631\u062f\u0645."
                } else {
                    "Okay. I opened the camera."
                }
            is AssistantCommand.CameraOff ->
                if (command.language == AppLanguage.PERSIAN) {
                    "\u0628\u0627\u0634\u0647. \u0627\u0632 \u062f\u0648\u0631\u0628\u06cc\u0646 \u0628\u0631\u06af\u0634\u062a\u0645."
                } else {
                    "Okay. I closed the camera and came back."
                }
            is AssistantCommand.GmailOpen ->
                if (command.language == AppLanguage.PERSIAN) {
                    "\u0628\u0627\u0634\u0647. \u062c\u06cc\u0645\u06cc\u0644 \u0631\u0627 \u0628\u0627\u0632 \u06a9\u0631\u062f\u0645."
                } else {
                    "Okay. I opened Gmail."
                }
            is AssistantCommand.GmailClose ->
                if (command.language == AppLanguage.PERSIAN) {
                    "\u0628\u0627\u0634\u0647. \u062c\u06cc\u0645\u06cc\u0644 \u0631\u0627 \u0628\u0633\u062a\u0645 \u0648 \u0628\u0631\u06af\u0634\u062a\u0645."
                } else {
                    "Okay. I closed Gmail and came back."
                }
            is AssistantCommand.Unknown ->
                if (command.language == AppLanguage.PERSIAN) {
                    "\u0645\u062a\u0648\u062c\u0647 \u0646\u0634\u062f\u0645. \u0645\u06cc\u200c\u062a\u0648\u0646\u06cc \u0628\u06af\u06cc \u0633\u0627\u0639\u062a\u060c \u0647\u0648\u0627\u060c \u0686\u0631\u0627\u063a\u200c\u0642\u0648\u0647\u060c \u062f\u0648\u0631\u0628\u06cc\u0646 \u06cc\u0627 \u062c\u06cc\u0645\u06cc\u0644\u061f"
                } else {
                    "I didn't catch that. You can say time, weather, flashlight, camera, or Gmail."
                }
        }
    }

    private fun toPersianDigits(input: String): String {
        val out = StringBuilder(input.length)
        for (ch in input) {
            out.append(if (ch in '0'..'9') persianDigits[ch - '0'] else ch)
        }
        return out.toString()
    }

    private fun time(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val spoken =
                "\u0627\u0644\u0627\u0646 \u0633\u0627\u0639\u062a ${persianNumberWords(hour)} \u0648 ${persianNumberWords(minute)} \u062f\u0642\u06cc\u0642\u0647 \u0627\u0633\u062a."
            val display = toPersianDigits(
                String.format(Locale.US, "%02d:%02d", hour, minute),
            )
            "$spoken \u0627\u06af\u0631 \u0631\u0648\u06cc \u0635\u0641\u062d\u0647 \u0646\u06af\u0627\u0647 \u06a9\u0646\u06cc\u062f\u060c \u0633\u0627\u0639\u062a \u0646\u0645\u0627\u06cc\u0634\u06cc $display \u0627\u0633\u062a. " +
                "\u0627\u06af\u0631 \u06a9\u0627\u0631 \u062f\u06cc\u06af\u0631\u06cc \u0645\u062b\u0644 \u0647\u0648\u0627 \u06cc\u0627 \u0686\u0631\u0627\u063a\u200c\u0642\u0648\u0647 \u062f\u0627\u0631\u06cc\u062f\u060c \u0628\u0641\u0631\u0645\u0627\u06cc\u06cc\u062f."
        } else {
            val t = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            "Right now it's $t. You can also ask about the weather or the flashlight."
        }
    }

    private fun weather(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "\u0648\u0636\u0639\u06cc\u062a \u0647\u0648\u0627\u06cc \u0627\u0645\u0631\u0648\u0632 \u0627\u06cc\u0646\u200c\u0637\u0648\u0631 \u0627\u0633\u062a: \u0622\u0633\u0645\u0627\u0646 \u0635\u0627\u0641 \u0648 \u0622\u0641\u062a\u0627\u0628\u06cc \u0627\u0633\u062a\u060c " +
                "\u062f\u0645\u0627\u06cc \u0647\u0648\u0627 \u062d\u062f\u0648\u062f \u0628\u06cc\u0633\u062a \u0648 \u0647\u0634\u062a \u062f\u0631\u062c\u0647 \u0633\u0627\u0646\u062a\u06cc\u200c\u06af\u0631\u0627\u062f \u0627\u0633\u062a\u060c " +
                "\u0631\u0637\u0648\u0628\u062a \u0646\u0633\u0628\u06cc \u06a9\u0645 \u0627\u0633\u062a \u0648 \u0628\u0627\u062f \u0645\u0644\u0627\u06cc\u0645\u06cc \u0645\u06cc\u200c\u0648\u0632\u062f. " +
                "\u0628\u0631\u0627\u06cc \u0628\u06cc\u0631\u0648\u0646 \u0631\u0641\u062a\u0646 \u0647\u0648\u0627\u06cc \u062e\u0648\u0628\u06cc \u062f\u0627\u0631\u06cc\u062f \u0648 \u0646\u06cc\u0627\u0632\u06cc \u0628\u0647 \u0686\u062a\u0631 \u0646\u06cc\u0633\u062a. " +
                "\u0627\u06af\u0631 \u0628\u062e\u0648\u0627\u0647\u06cc\u062f \u0633\u0627\u0639\u062a \u0631\u0627 \u0647\u0645 \u0628\u06af\u0648\u06cc\u0645\u060c \u06a9\u0627\u0641\u06cc \u0627\u0633\u062a \u0628\u067e\u0631\u0633\u06cc\u062f."
        } else {
            "Today's weather looks clear and sunny, around 28 degrees Celsius, " +
                "with low humidity and a light breeze. It's a nice day to be outside. " +
                "Ask if you'd like the time as well."
        }
    }

    private fun persianNumberWords(n: Int): String {
        val value = ((n % 100) + 100) % 100
        val ones = arrayOf(
            "\u0635\u0641\u0631", "\u06cc\u06a9", "\u062f\u0648", "\u0633\u0647", "\u0686\u0647\u0627\u0631", "\u067e\u0646\u062c", "\u0634\u0634", "\u0647\u0641\u062a", "\u0647\u0634\u062a", "\u0646\u0647",
            "\u062f\u0647", "\u06cc\u0627\u0632\u062f\u0647", "\u062f\u0648\u0627\u0632\u062f\u0647", "\u0633\u06cc\u0632\u062f\u0647", "\u0686\u0647\u0627\u0631\u062f\u0647", "\u067e\u0627\u0646\u0632\u062f\u0647", "\u0634\u0627\u0646\u0632\u062f\u0647",
            "\u0647\u0641\u062f\u0647", "\u0647\u062c\u062f\u0647", "\u0646\u0648\u0632\u062f\u0647",
        )
        val tens = arrayOf("", "", "\u0628\u06cc\u0633\u062a", "\u0633\u06cc", "\u0686\u0647\u0644", "\u067e\u0646\u062c\u0627\u0647", "\u0634\u0635\u062a", "\u0647\u0641\u062a\u0627\u062f", "\u0647\u0634\u062a\u0627\u062f", "\u0646\u0648\u062f")
        return when {
            value < 20 -> ones[value]
            value % 10 == 0 -> tens[value / 10]
            else -> "${tens[value / 10]} \u0648 ${ones[value % 10]}"
        }
    }
}
