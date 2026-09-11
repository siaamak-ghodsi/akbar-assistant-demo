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

    private val persianCharRegex = Regex("[\\u0600-\\u06FF]")

    fun detectLanguage(text: String): AppLanguage {
        return if (persianCharRegex.containsMatchIn(text)) {
            AppLanguage.PERSIAN
        } else {
            AppLanguage.ENGLISH
        }
    }

    fun normalize(text: String): String {
        return text
            .trim()
            .lowercase(Locale.ROOT)
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace(Regex("\\s+"), " ")
    }

    fun containsWakeWord(text: String): Boolean {
        val n = normalize(text)
        val persianWake = listOf(
            "هی اکبر",
            "هی، اکبر",
            "هی اگبر",
            "هی اکبر؟",
            "hey اکبر",
            "هی akbar"
        )
        val englishWake = listOf(
            "hey akbar",
            "hi akbar",
            "hay akbar",
            "hey, akbar",
            "hey akbar!",
            "hey akbar?"
        )
        return persianWake.any { n.contains(it) } || englishWake.any { n.contains(it) }
    }

    fun wakeLanguage(text: String): AppLanguage = detectLanguage(text)

    fun parse(text: String): AssistantCommand {
        val language = detectLanguage(text)
        val n = normalize(text)

        return when {
            isTimeCommand(n, language) -> AssistantCommand.TellTime(language)
            isWeatherCommand(n, language) -> AssistantCommand.Weather(language)
            isLightOnCommand(n, language) -> AssistantCommand.LightOn(language)
            isLightOffCommand(n, language) -> AssistantCommand.LightOff(language)
            else -> AssistantCommand.Unknown(language, text)
        }
    }

    private fun isTimeCommand(n: String, language: AppLanguage): Boolean {
        return if (language == AppLanguage.PERSIAN) {
            n.contains("ساعت") && (
                n.contains("چند") ||
                    n.contains("چنده") ||
                    n.contains("چیست") ||
                    n.contains("چیه")
                )
        } else {
            n.contains("what time") ||
                n.contains("what's the time") ||
                n.contains("whats the time") ||
                n.contains("tell me the time") ||
                n == "time" ||
                n.contains("current time")
        }
    }

    private fun isWeatherCommand(n: String, language: AppLanguage): Boolean {
        return if (language == AppLanguage.PERSIAN) {
            n.contains("هوا") ||
                n.contains("وضعیت هوا") ||
                n.contains("آب و هوا") ||
                n.contains("اب و هوا")
        } else {
            n.contains("weather") ||
                n.contains("how's the weather") ||
                n.contains("how is the weather") ||
                n.contains("what's the weather") ||
                n.contains("whats the weather")
        }
    }

    private fun isLightOnCommand(n: String, language: AppLanguage): Boolean {
        return if (language == AppLanguage.PERSIAN) {
            (n.contains("چراغ") || n.contains("لامپ") || n.contains("نور")) &&
                (n.contains("روشن") || n.contains("روشن کن") || n.contains("باز کن"))
        } else {
            (n.contains("light") || n.contains("lights") || n.contains("lamp") || n.contains("bulb")) &&
                (n.contains("on") || n.contains("turn on") || n.contains("enable")) &&
                !n.contains("off")
        }
    }

    private fun isLightOffCommand(n: String, language: AppLanguage): Boolean {
        return if (language == AppLanguage.PERSIAN) {
            (n.contains("چراغ") || n.contains("لامپ") || n.contains("نور")) &&
                (n.contains("خاموش") || n.contains("ببند") || n.contains("قطع"))
        } else {
            (n.contains("light") || n.contains("lights") || n.contains("lamp") || n.contains("bulb")) &&
                (n.contains("off") || n.contains("turn off") || n.contains("disable"))
        }
    }
}

object ResponseBuilder {

    fun activationPrompt(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "بله، بفرمایید"
        } else {
            "Yes, I'm listening"
        }
    }

    fun forCommand(command: AssistantCommand, @Suppress("UNUSED_PARAMETER") lightOn: Boolean = false): String {
        return when (command) {
            is AssistantCommand.TellTime -> timeResponse(command.language)
            is AssistantCommand.Weather -> weatherResponse(command.language)
            is AssistantCommand.LightOn -> lightOnResponse(command.language)
            is AssistantCommand.LightOff -> lightOffResponse(command.language)
            is AssistantCommand.Unknown -> unknownResponse(command.language)
        }
    }

    private fun timeResponse(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            val formatter = SimpleDateFormat("HH:mm", Locale("fa", "IR"))
            val time = formatter.format(Date())
            "الان ساعت $time است"
        } else {
            val formatter = SimpleDateFormat("h:mm a", Locale.US)
            val time = formatter.format(Date())
            "It's $time"
        }
    }

    private fun weatherResponse(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "امروز هوا آفتابی و ۲۸ درجه است"
        } else {
            "It's sunny and 28 degrees today"
        }
    }

    private fun lightOnResponse(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "چراغ روشن شد"
        } else {
            "The light is on"
        }
    }

    private fun lightOffResponse(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "چراغ خاموش شد"
        } else {
            "The light is off"
        }
    }

    private fun unknownResponse(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "متوجه نشدم. می‌توانید ساعت، هوا یا چراغ را بپرسید."
        } else {
            "I didn't catch that. You can ask about time, weather, or the light."
        }
    }
}
