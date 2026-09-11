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
    data class Unknown(val language: AppLanguage, val raw: String) : AssistantCommand()
}

object CommandParser {

    private val persianChars = Regex("[\\u0600-\\u06FF]")

    fun detectLanguage(text: String): AppLanguage {
        return if (persianChars.containsMatchIn(text)) AppLanguage.PERSIAN else AppLanguage.ENGLISH
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
        val phrases = listOf(
            "\u0647\u06cc \u0627\u06a9\u0628\u0631", "\u0647\u06cc \u0627\u06af\u0628\u0631", "\u0647\u06cc \u0627\u0642\u0628\u0631", "\u0647\u0627\u06cc \u0627\u06a9\u0628\u0631", "\u0633\u0644\u0627\u0645 \u0627\u06a9\u0628\u0631", "\u0627\u06a9\u0628\u0631 \u062c\u0627\u0646",
            "\u06cc\u0627 \u0627\u06a9\u0628\u0631", "\u0627\u06cc \u0627\u06a9\u0628\u0631", "\u0627\u0647\u0627\u06cc \u0627\u06a9\u0628\u0631", "\u0647\u06cc akbar", "hey \u0627\u06a9\u0628\u0631",
            "hey akbar", "hi akbar", "hay akbar", "okay akbar", "ok akbar",
            "hey akber", "hey aqbar", "hey ekbar", "yo akbar"
        )
        if (phrases.any { n.contains(it) }) return true
        if (compact.contains("\u0647\u06cc\u0627\u06a9\u0628\u0631") || compact.contains("\u0647\u0627\u06cc\u0627\u06a9\u0628\u0631") ||
            compact.contains("heyakbar") || compact.contains("hiakbar") ||
            compact.contains("hayakbar")
        ) {
            return true
        }
        return n == "\u0627\u06a9\u0628\u0631" || n == "\u0627\u06af\u0628\u0631" || n == "\u0627\u0642\u0628\u0631" ||
            n == "akbar" || n == "akber" || n == "aqbar" || n == "ekbar" ||
            (n.split(" ").size <= 3 &&
                (n.contains("\u0627\u06a9\u0628\u0631") || n.contains("\u0627\u06af\u0628\u0631") || n.contains("\u0627\u0642\u0628\u0631") ||
                    n.contains("akbar")))
    }

    fun wakeLanguage(text: String): AppLanguage = detectLanguage(text)

    fun stripWakeWord(text: String): String {
        var n = normalize(text)
        val wakes = listOf(
            "\u0647\u06cc \u0627\u06a9\u0628\u0631", "\u0647\u06cc \u0627\u06af\u0628\u0631", "\u0647\u06cc \u0627\u0642\u0628\u0631", "\u0647\u0627\u06cc \u0627\u06a9\u0628\u0631", "\u0633\u0644\u0627\u0645 \u0627\u06a9\u0628\u0631", "\u0627\u06a9\u0628\u0631 \u062c\u0627\u0646",
            "\u06cc\u0627 \u0627\u06a9\u0628\u0631", "\u0627\u06cc \u0627\u06a9\u0628\u0631", "\u0627\u0647\u0627\u06cc \u0627\u06a9\u0628\u0631", "hey \u0627\u06a9\u0628\u0631", "\u0647\u06cc akbar",
            "hey akbar", "hi akbar", "hay akbar", "okay akbar", "ok akbar",
            "hey akber", "hey aqbar", "hey ekbar", "yo akbar",
            "\u0627\u06a9\u0628\u0631", "\u0627\u06af\u0628\u0631", "\u0627\u0642\u0628\u0631", "akbar", "akber", "aqbar", "ekbar"
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
        val language = detectLanguage(text)
        val n = stripWakeWord(text).ifBlank { normalize(text) }
        val compact = n.replace(" ", "")
        return when {
            isTime(n, compact, language) -> AssistantCommand.TellTime(language)
            isWeather(n, compact, language) -> AssistantCommand.Weather(language)
            isLightOn(n, language) -> AssistantCommand.LightOn(language)
            isLightOff(n, language) -> AssistantCommand.LightOff(language)
            else -> AssistantCommand.Unknown(language, text)
        }
    }

    private fun isTime(n: String, compact: String, language: AppLanguage): Boolean {
        if (language == AppLanguage.PERSIAN) {
            val phrases = listOf(
                "\u0633\u0627\u0639\u062a", "\u0632\u0645\u0627\u0646", "\u0633\u0627\u0639\u062a \u0686\u0646\u062f", "\u0633\u0627\u0639\u062a \u0686\u0646\u062f\u0647", "\u0633\u0627\u0639\u062a \u0686\u0646\u062f \u0627\u0633\u062a",
                "\u0633\u0627\u0639\u062a \u0686\u0646\u062f \u0634\u062f", "\u0686\u0647 \u0633\u0627\u0639\u062a\u06cc", "\u0686\u0647 \u0633\u0627\u0639\u062a\u06cc\u0647", "\u0627\u0644\u0627\u0646 \u0633\u0627\u0639\u062a",
                "\u0633\u0627\u0639\u062a \u0627\u0644\u0627\u0646", "\u0628\u06af\u0648 \u0633\u0627\u0639\u062a", "\u0633\u0627\u0639\u062a\u0648 \u0628\u06af\u0648", "\u0633\u0627\u0639\u062a \u0631\u0627 \u0628\u06af\u0648",
                "\u0686\u0646\u062f \u0634\u062f\u0647", "\u0686\u0646\u062f \u0627\u0633\u062a \u0627\u0644\u0627\u0646"
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
            n.contains("tell me the time") || n == "time" ||
            n.contains("o'clock") || n.contains("o clock")
    }

    private fun isWeather(n: String, compact: String, language: AppLanguage): Boolean {
        if (language == AppLanguage.PERSIAN) {
            val phrases = listOf(
                "\u0647\u0648\u0627", "\u0627\u0628 \u0648 \u0647\u0648\u0627", "\u0627\u0628\u0648\u0647\u0648\u0627", "\u062f\u0645\u0627", "\u0686\u0646\u062f \u062f\u0631\u062c\u0647",
                "\u0647\u0648\u0627 \u0686\u0637\u0648\u0631", "\u0647\u0648\u0627 \u0686\u0637\u0648\u0631\u0647", "\u0647\u0648\u0627 \u0686\u0637\u0648\u0631 \u0627\u0633\u062a", "\u0647\u0648\u0627 \u0686\u06cc\u0647",
                "\u0647\u0648\u0627 \u0686\u06cc \u0627\u0633\u062a", "\u0647\u0648\u0627 \u062e\u0648\u0628\u0647", "\u0647\u0648\u0627 \u062e\u0648\u0628 \u0627\u0633\u062a", "\u0648\u0636\u0639\u06cc\u062a \u0647\u0648\u0627",
                "\u0647\u0648\u0627\u06cc \u0627\u0645\u0631\u0648\u0632", "\u0647\u0648\u0627\u06cc \u0628\u06cc\u0631\u0648\u0646", "\u062f\u0631\u062c\u0647 \u0647\u0648\u0627", "\u06af\u0631\u0645 \u0627\u0633\u062a",
                "\u0633\u0631\u062f \u0627\u0633\u062a", "\u0628\u0627\u0631\u0648\u0646", "\u0628\u0627\u0631\u0627\u0646", "\u067e\u06cc\u0634 \u0628\u06cc\u0646\u06cc \u0647\u0648\u0627"
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
            n.contains("how is the weather") || n.contains("is it raining")
    }

    private fun mentionsLight(n: String): Boolean {
        return n.contains("\u0686\u0631\u0627\u063a") || n.contains("\u0644\u0627\u0645\u067e") || n.contains("\u0646\u0648\u0631") ||
            n.contains("\u0641\u0644\u0634") || n.contains("\u0686\u0631\u0627\u063a \u0642\u0648\u0647") || n.contains("\u0686\u0631\u0627\u063a\u0642\u0648\u0647") ||
            n.contains("\u0641\u0644\u0627\u0634") || n.contains("light") || n.contains("lights") ||
            n.contains("lamp") || n.contains("bulb") || n.contains("flashlight") ||
            n.contains("torch") || n.contains("flash")
    }

    private fun isLightOn(n: String, language: AppLanguage): Boolean {
        if (!mentionsLight(n)) return false
        if (language == AppLanguage.PERSIAN) {
            return n.contains("\u0631\u0648\u0634\u0646") || n.contains("\u0628\u0627\u0632 \u06a9\u0646") || n.contains("\u0628\u0627\u0632\u06a9\u0646") ||
                n.contains("\u0641\u0639\u0627\u0644") || n.contains("\u0631\u0648\u0634\u0646 \u06a9\u0646") || n.contains("\u0631\u0648\u0634\u0646\u06a9\u0646") ||
                n.contains("\u0628\u0632\u0646") || n.contains("\u0631\u0648\u0634\u0646 \u0634\u0648") || n.contains("\u0686\u0631\u0627\u063a \u0631\u0648\u0634\u0646")
        }
        return (n.contains("turn on") || n.contains("switch on") || n.contains("enable") ||
            n.contains("light on") || Regex("\\bon\\b").containsMatchIn(n)) &&
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
            n.contains("light off") || Regex("\\boff\\b").containsMatchIn(n)
    }
}

object ResponseBuilder {

    private val persianDigits = charArrayOf('\u06f0', '\u06f1', '\u06f2', '\u06f3', '\u06f4', '\u06f5', '\u06f6', '\u06f7', '\u06f8', '\u06f9')

    fun activationPrompt(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "\u0628\u0644\u0647\u060c \u0628\u0641\u0631\u0645\u0627\u06cc\u06cc\u062f. \u0645\u06cc\u200c\u062a\u0648\u0627\u0646\u06cc\u062f \u0628\u06af\u0648\u06cc\u06cc\u062f \u0633\u0627\u0639\u062a\u060c \u0647\u0648\u0627\u060c \u06cc\u0627 \u0686\u0631\u0627\u063a\u200c\u0642\u0648\u0647 \u0631\u0627 \u0631\u0648\u0634\u0646 \u0648 \u062e\u0627\u0645\u0648\u0634 \u06a9\u0646\u06cc\u062f."
        } else {
            "Yes? You can ask about time, weather, or the flashlight."
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
            is AssistantCommand.Unknown ->
                if (command.language == AppLanguage.PERSIAN) {
                    "\u0645\u062a\u0648\u062c\u0647 \u0646\u0634\u062f\u0645. \u0645\u06cc\u200c\u062a\u0648\u0646\u06cc \u0628\u06af\u06cc \u0633\u0627\u0639\u062a\u060c \u0647\u0648\u0627 \u06cc\u0627 \u0686\u0631\u0627\u063a\u200c\u0642\u0648\u0647\u061f"
                } else {
                    "I didn't catch that. You can say time, weather, or flashlight."
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
                String.format(Locale.US, "%02d:%02d", hour, minute)
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
            "\u0647\u0641\u062f\u0647", "\u0647\u062c\u062f\u0647", "\u0646\u0648\u0632\u062f\u0647"
        )
        val tens = arrayOf("", "", "\u0628\u06cc\u0633\u062a", "\u0633\u06cc", "\u0686\u0647\u0644", "\u067e\u0646\u062c\u0627\u0647", "\u0634\u0635\u062a", "\u0647\u0641\u062a\u0627\u062f", "\u0647\u0634\u062a\u0627\u062f", "\u0646\u0648\u062f")
        return when {
            value < 20 -> ones[value]
            value % 10 == 0 -> tens[value / 10]
            else -> "${tens[value / 10]} \u0648 ${ones[value % 10]}"
        }
    }
}
