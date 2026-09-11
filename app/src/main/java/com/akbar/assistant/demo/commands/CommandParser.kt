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
            .replace(Regex("[؟?!,.،؛:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun containsWakeWord(text: String): Boolean {
        val n = normalize(text)
        // Speech engines often mis-hear Persian wake phrases; keep a wide net.
        val fa = listOf(
            "هی اکبر", "هی اکبر", "هی، اکبر", "هی اگبر", "هی اقبر",
            "هی اکبر جان", "سلام اکبر", "اکبر جان",
            "hey اکبر", "هی akbar"
        )
        val en = listOf(
            "hey akbar", "hi akbar", "hay akbar", "hey, akbar",
            "hey akber", "hey akbar please", "okay akbar", "ok akbar"
        )
        return fa.any { n.contains(it) } || en.any { n.contains(it) }
    }

    fun wakeLanguage(text: String): AppLanguage = detectLanguage(text)

    /** Strip wake phrase so "hey akbar what time is it" still parses as time. */
    fun stripWakeWord(text: String): String {
        var n = normalize(text)
        val wakes = listOf(
            "هی اکبر", "هی، اکبر", "هی اگبر", "هی اقبر",
            "hey اکبر", "هی akbar",
            "hey akbar", "hi akbar", "hay akbar", "okay akbar", "ok akbar"
        )
        wakes.forEach { wake ->
            n = n.replace(wake, " ")
        }
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
            (n.contains("ساعت") && (
                n.contains("چند") || n.contains("چنده") || n.contains("چیست") ||
                    n.contains("چیه") || n.contains("بگو")
                )) || n.contains("ساعت چنده") || n.contains("ساعت چند")
        } else {
            n.contains("what time") || n.contains("what's the time") || n.contains("whats the time") ||
                n.contains("tell me the time") || n.contains("current time") ||
                n == "time" || n.contains("the time")
        }
    }

    private fun isWeather(n: String, language: AppLanguage): Boolean {
        return if (language == AppLanguage.PERSIAN) {
            n.contains("هوا") || n.contains("وضعیت هوا") ||
                n.contains("آب و هوا") || n.contains("اب و هوا") || n.contains("آب‌وهوا")
        } else {
            n.contains("weather") || n.contains("temperature") ||
                n.contains("how's the weather") || n.contains("how is the weather") ||
                n.contains("what's the weather") || n.contains("whats the weather")
        }
    }

    private fun isLightOn(n: String, language: AppLanguage): Boolean {
        return if (language == AppLanguage.PERSIAN) {
            (n.contains("چراغ") || n.contains("لامپ") || n.contains("نور") ||
                n.contains("چراغ قوه") || n.contains("چراغ‌قوه") || n.contains("فلش")) &&
                (n.contains("روشن") || n.contains("باز"))
        } else {
            val mentionsLight = n.contains("light") || n.contains("lights") ||
                n.contains("lamp") || n.contains("bulb") ||
                n.contains("flashlight") || n.contains("torch") || n.contains("flash")
            val wantsOn = n.contains("turn on") || n.contains("switch on") ||
                n.contains("enable") || Regex("\\bon\\b").containsMatchIn(n) ||
                n.contains("light on") || n.endsWith(" on")
            mentionsLight && wantsOn && !n.contains("off")
        }
    }

    private fun isLightOff(n: String, language: AppLanguage): Boolean {
        return if (language == AppLanguage.PERSIAN) {
            (n.contains("چراغ") || n.contains("لامپ") || n.contains("نور") ||
                n.contains("چراغ قوه") || n.contains("چراغ‌قوه") || n.contains("فلش")) &&
                (n.contains("خاموش") || n.contains("ببند") || n.contains("قطع") || n.contains("بسته"))
        } else {
            val mentionsLight = n.contains("light") || n.contains("lights") ||
                n.contains("lamp") || n.contains("bulb") ||
                n.contains("flashlight") || n.contains("torch") || n.contains("flash")
            val wantsOff = n.contains("turn off") || n.contains("switch off") ||
                n.contains("disable") || Regex("\\boff\\b").containsMatchIn(n) ||
                n.contains("light off") || n.endsWith(" off")
            mentionsLight && wantsOff
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
                if (command.language == AppLanguage.PERSIAN) "چراغ روشن شد" else "Okay, the light is on"
            is AssistantCommand.LightOff ->
                if (command.language == AppLanguage.PERSIAN) "چراغ خاموش شد" else "Okay, the light is off"
            is AssistantCommand.Unknown ->
                if (command.language == AppLanguage.PERSIAN) {
                    "متوجه نشدم. بپرسید ساعت، هوا، یا چراغ."
                } else {
                    "I didn't catch that. Ask for time, weather, or the light."
                }
        }
    }

    private fun time(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            val t = SimpleDateFormat("HH:mm", Locale("fa", "IR")).format(Date())
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
