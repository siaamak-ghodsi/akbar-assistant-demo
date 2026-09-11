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
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('ۀ', 'ه')
            .replace('‌', ' ')
            .replace(Regex("[؟?!,.،؛:\"']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun containsWakeWord(text: String): Boolean {
        val n = normalize(text)
        val compact = n.replace(" ", "")
        val phrases = listOf(
            "هی اکبر", "هی اگبر", "هی اقبر", "های اکبر", "سلام اکبر", "اکبر جان",
            "یا اکبر", "ای اکبر", "آهای اکبر", "هی akbar", "hey اکبر",
            "hey akbar", "hi akbar", "hay akbar", "okay akbar", "ok akbar",
            "hey akber", "hey aqbar", "hey ekbar"
        )
        if (phrases.any { n.contains(it) }) return true
        if (compact.contains("هیاکبر") || compact.contains("heyakbar") || compact.contains("hiakbar")) {
            return true
        }
        // Single-token wake: «اکبر» / Akbar
        return n == "اکبر" || n == "اگبر" || n == "اقبر" ||
            n == "akbar" || n == "akber" || n == "aqbar" || n == "ekbar" ||
            (n.split(" ").size <= 2 && (n.contains("اکبر") || n.contains("akbar")))
    }

    fun wakeLanguage(text: String): AppLanguage = detectLanguage(text)

    fun stripWakeWord(text: String): String {
        var n = normalize(text)
        val wakes = listOf(
            "هی اکبر", "هی اگبر", "هی اقبر", "های اکبر", "سلام اکبر", "اکبر جان",
            "یا اکبر", "ای اکبر", "آهای اکبر", "hey اکبر", "هی akbar",
            "hey akbar", "hi akbar", "hay akbar", "okay akbar", "ok akbar",
            "hey akber", "hey aqbar", "hey ekbar", "اکبر", "اگبر", "akbar", "akber"
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
            n.contains("ساعت") || n.contains("زمان") || n == "ساعت" ||
                n.contains("چند است") || n.contains("ساعت چند") ||
                n.contains("ساعت چنده") || n.contains("چه ساعتی") ||
                n.contains("ساعت چند شد")
        } else {
            n.contains("time") || n.contains("clock") || n == "time" ||
                n.contains("what time")
        }
    }

    private fun isWeather(n: String, language: AppLanguage): Boolean {
        return if (language == AppLanguage.PERSIAN) {
            n.contains("هوا") || n.contains("آب و هوا") || n.contains("اب و هوا") ||
                n.contains("آب‌وهوا") || n.contains("دما") ||
                n.contains("چند درجه") || n.contains("هوا چطور") || n.contains("هوا چطوره") ||
                n.contains("وضعیت هوا")
        } else {
            n.contains("weather") || n.contains("temperature") || n.contains("forecast") ||
                n.contains("how hot") || n.contains("how's the weather")
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

    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun activationPrompt(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "بله، بفرمایید. می‌توانید درباره ساعت، هوا، یا چراغ‌قوه بپرسید."
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
                    "چراغ‌قوه روشن شد."
                } else {
                    "Flashlight is on."
                }
            is AssistantCommand.LightOff ->
                if (command.language == AppLanguage.PERSIAN) {
                    "چراغ‌قوه خاموش شد."
                } else {
                    "Flashlight is off."
                }
            is AssistantCommand.Unknown ->
                if (command.language == AppLanguage.PERSIAN) {
                    "متوجه نشدم. می‌توانید بگویید ساعت، وضعیت هوا، یا چراغ‌قوه را روشن یا خاموش کن."
                } else {
                    "I didn't catch that. Try time, weather, or flashlight on/off."
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
            // Spoken words work more reliably with Persian TTS than Eastern digits.
            val spoken = "الان ساعت ${persianNumberWords(hour)} و ${persianNumberWords(minute)} دقیقه است."
            val display = toPersianDigits(
                String.format(Locale.US, "%02d:%02d", hour, minute)
            )
            "$spoken ساعت نمایشی $display. اگر کار دیگری دارید بفرمایید."
        } else {
            val t = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            "It's $t right now. What else can I help with?"
        }
    }

    private fun weather(language: AppLanguage): String {
        // Demo forecast — spoken aloud and kept on screen / in chat.
        return if (language == AppLanguage.PERSIAN) {
            "وضعیت هوای امروز این‌طور است: آسمان صاف و آفتابی است. " +
                "دمای هوا حدود بیست و هشت درجه سانتی‌گراد است، " +
                "رطوبت نسبی کم است و وزش باد ملایم گزارش شده. " +
                "برای بیرون رفتن هوای خوبی دارید. " +
                "اگر می‌خواهید ساعت را هم بگویم، کافی است بپرسید."
        } else {
            "Today's weather: clear and sunny, about 28 degrees Celsius, low humidity, " +
                "with a light breeze. It's a nice day to be outside. Ask if you'd like the time too."
        }
    }

    private fun persianNumberWords(n: Int): String {
        val value = ((n % 100) + 100) % 100
        val ones = arrayOf(
            "صفر", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه",
            "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده",
            "هفده", "هجده", "نوزده"
        )
        val tens = arrayOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
        return when {
            value < 20 -> ones[value]
            value % 10 == 0 -> tens[value / 10]
            else -> "${tens[value / 10]} و ${ones[value % 10]}"
        }
    }
}
