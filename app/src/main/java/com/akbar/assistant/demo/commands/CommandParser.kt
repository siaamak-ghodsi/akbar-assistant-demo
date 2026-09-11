package com.akbar.assistant.demo.commands

import com.akbar.assistant.demo.AppLanguage
import java.text.SimpleDateFormat
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
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('ۀ', 'ه')
            .replace('‌', ' ') // ZWNJ
            .replace(Regex("[؟?!,.،؛:\"']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun containsWakeWord(text: String): Boolean {
        val n = normalize(text)
        val fa = listOf(
            "هی اکبر", "هی اکبر", "هی اگبر", "هی اقبر", "هی اکبر",
            "های اکبر", "هی، اکبر", "سلام اکبر", "اکبر جان",
            "هی akbar", "hey اکبر"
        )
        val en = listOf(
            "hey akbar", "hi akbar", "hay akbar", "hey, akbar",
            "hey akber", "hey aqbar", "okay akbar", "ok akbar",
            "hey akbaar"
        )
        return fa.any { n.contains(it) } || en.any { n.contains(it) }
    }

    fun wakeLanguage(text: String): AppLanguage = detectLanguage(text)

    fun stripWakeWord(text: String): String {
        var n = normalize(text)
        val wakes = listOf(
            "هی اکبر", "هی اگبر", "هی اقبر", "های اکبر", "هی، اکبر",
            "سلام اکبر", "اکبر جان", "hey اکبر", "هی akbar",
            "hey akbar", "hi akbar", "hay akbar", "okay akbar", "ok akbar",
            "hey akber", "hey aqbar", "hey akbaar"
        )
        wakes.forEach { wake -> n = n.replace(wake, " ") }
        return n.replace(Regex("\\s+"), " ").trim()
    }

    fun parse(text: String): AssistantCommand {
        val language = detectLanguage(text)
        val n = stripWakeWord(text).ifBlank { normalize(text) }
        return when {
            isTime(n, language) -> AssistantCommand.TellTime(language)
            isWeather(n, language) -> AssistantCommand.Weather(language)
            isLightOn(n, language) -> AssistantCommand.LightOn(language)
            isLightOff(n, language) -> AssistantCommand.LightOff(language)
            else -> AssistantCommand.Unknown(language, text)
        }
    }

    private fun isTime(n: String, language: AppLanguage): Boolean {
        return if (language == AppLanguage.PERSIAN) {
            n.contains("ساعت") || n.contains("زمان") ||
                n.contains("چند است") || n.contains("ساعت چند")
        } else {
            n.contains("time") || n.contains("clock")
        }
    }

    private fun isWeather(n: String, language: AppLanguage): Boolean {
        return if (language == AppLanguage.PERSIAN) {
            n.contains("هوا") || n.contains("آب و هوا") || n.contains("اب و هوا") ||
                n.contains("آب‌وهوا") || n.contains("دما")
        } else {
            n.contains("weather") || n.contains("temperature") || n.contains("forecast")
        }
    }

    private fun mentionsLight(n: String): Boolean {
        return n.contains("چراغ") || n.contains("لامپ") || n.contains("نور") ||
            n.contains("فلش") || n.contains("چراغ قوه") || n.contains("چراغ‌قوه") ||
            n.contains("light") || n.contains("lights") || n.contains("lamp") ||
            n.contains("bulb") || n.contains("flashlight") || n.contains("torch") ||
            n.contains("flash")
    }

    private fun isLightOn(n: String, language: AppLanguage): Boolean {
        if (!mentionsLight(n)) return false
        return if (language == AppLanguage.PERSIAN) {
            n.contains("روشن") || n.contains("باز کن") || n.contains("فعال")
        } else {
            (n.contains("turn on") || n.contains("switch on") || n.contains("enable") ||
                Regex("\\bon\\b").containsMatchIn(n)) && !n.contains("off")
        }
    }

    private fun isLightOff(n: String, language: AppLanguage): Boolean {
        if (!mentionsLight(n)) return false
        return if (language == AppLanguage.PERSIAN) {
            n.contains("خاموش") || n.contains("ببند") || n.contains("قطع") || n.contains("بسته")
        } else {
            n.contains("turn off") || n.contains("switch off") || n.contains("disable") ||
                Regex("\\boff\\b").containsMatchIn(n)
        }
    }
}

object ResponseBuilder {

    fun activationPrompt(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) "بله، بفرمایید" else "Yes, I'm listening"
    }

    fun forCommand(command: AssistantCommand): String {
        return when (command) {
            is AssistantCommand.TellTime -> time(command.language)
            is AssistantCommand.Weather -> weather(command.language)
            is AssistantCommand.LightOn ->
                if (command.language == AppLanguage.PERSIAN) "چراغ‌قوه روشن شد" else "Flashlight is on"
            is AssistantCommand.LightOff ->
                if (command.language == AppLanguage.PERSIAN) "چراغ‌قوه خاموش شد" else "Flashlight is off"
            is AssistantCommand.Unknown ->
                if (command.language == AppLanguage.PERSIAN) {
                    "متوجه نشدم. بگو ساعت، هوا، یا چراغ‌قوه."
                } else {
                    "I didn't catch that. Ask for time, weather, or flashlight."
                }
        }
    }

    private fun time(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            val t = SimpleDateFormat("HH:mm", Locale.US).format(Date())
            "الان ساعت $t است"
        } else {
            val t = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            "It's $t"
        }
    }

    private fun weather(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "امروز هوا آفتابی و ۲۸ درجه است"
        } else {
            "It's sunny and 28 degrees today"
        }
    }
}
