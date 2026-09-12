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
    private val latinLetters = Regex("[A-Za-z]")

    fun detectLanguage(text: String): AppLanguage {
        val hasPersian = persianChars.containsMatchIn(text)
        val hasLatin = latinLetters.containsMatchIn(text)
        val n = normalize(text)
        if (looksEnglish(n)) return AppLanguage.ENGLISH
        if (hasPersian && !hasLatin) return AppLanguage.PERSIAN
        if (hasLatin && !hasPersian) return AppLanguage.ENGLISH
        return if (hasPersian) AppLanguage.PERSIAN else AppLanguage.ENGLISH
    }

    private fun looksEnglish(n: String): Boolean {
        val englishCues = listOf(
            "hey akbar", "hi akbar", "hay akbar", "ok akbar", "okay akbar", "yo akbar",
            "what time", "weather", "temperature", "forecast", "flashlight",
            "turn on", "turn off", "switch on", "switch off", "lights",
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
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('ۀ', 'ه')
            .replace('ة', 'ه')
            .replace('ؤ', 'و')
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('‌', ' ')
            .replace(Regex("[؟?!,.،؛:\"'…\\-_/\\\\()\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun containsWakeWord(text: String): Boolean {
        val n = normalize(text)
        val compact = n.replace(" ", "")
        val phrases = listOf(
            "هی اکبر", "هی اگبر", "هی اقبر", "های اکبر", "سلام اکبر", "اکبر جان",
            "یا اکبر", "ای اکبر", "اهای اکبر", "هی akbar", "hey اکبر",
            "hey akbar", "hi akbar", "hay akbar", "okay akbar", "ok akbar",
            "hey akber", "hey aqbar", "hey ekbar", "yo akbar",
            "hey aakbar", "hei akbar", "he akbar", "a akbar",
        )
        if (phrases.any { n.contains(it) }) return true
        if (compact.contains("هیاکبر") || compact.contains("هایاکبر") ||
            compact.contains("heyakbar") || compact.contains("hiakbar") ||
            compact.contains("hayakbar") || compact.contains("okakbar")
        ) {
            return true
        }
        return n == "اکبر" || n == "اگبر" || n == "اقبر" ||
            n == "akbar" || n == "akber" || n == "aqbar" || n == "ekbar" ||
            (n.split(" ").size <= 4 &&
                (n.contains("اکبر") || n.contains("اگبر") || n.contains("اقبر") ||
                    n.contains("akbar") || n.contains("akber")))
    }

    fun wakeLanguage(text: String): AppLanguage {
        val n = normalize(text)
        val englishWake = listOf(
            "hey akbar", "hi akbar", "hay akbar", "okay akbar", "ok akbar",
            "yo akbar", "hey akber", "hey aqbar", "hei akbar", "heyakbar", "hiakbar",
        )
        if (englishWake.any { n.contains(it) || n.replace(" ", "").contains(it.replace(" ", "")) }) {
            return AppLanguage.ENGLISH
        }
        if (n == "akbar" || n == "akber" || n == "aqbar" || n == "ekbar") {
            return AppLanguage.ENGLISH
        }
        return detectLanguage(text)
    }

    fun stripWakeWord(text: String): String {
        var n = normalize(text)
        val wakes = listOf(
            "هی اکبر", "هی اگبر", "هی اقبر", "های اکبر", "سلام اکبر", "اکبر جان",
            "یا اکبر", "ای اکبر", "اهای اکبر", "hey اکبر", "هی akbar",
            "hey akbar", "hi akbar", "hay akbar", "okay akbar", "ok akbar",
            "hey akber", "hey aqbar", "hey ekbar", "yo akbar", "hei akbar",
            "hey aakbar", "he akbar",
            "اکبر", "اگبر", "اقبر", "akbar", "akber", "aqbar", "ekbar",
        )
        wakes.forEach { wake -> n = n.replace(wake, " ") }
        return n.replace(Regex("\\s+"), " ").trim()
    }

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
            isLightOn(n, language) -> AssistantCommand.LightOn(language)
            isLightOff(n, language) -> AssistantCommand.LightOff(language)
            else -> null
        }
    }

    private fun isTime(n: String, compact: String, language: AppLanguage): Boolean {
        if (language == AppLanguage.PERSIAN) {
            val phrases = listOf(
                "ساعت", "زمان", "ساعت چند", "ساعت چنده", "ساعت چند است",
                "ساعت چند شد", "چه ساعتی", "چه ساعتیه", "الان ساعت",
                "ساعت الان", "بگو ساعت", "ساعتو بگو", "ساعت را بگو",
                "چند شده", "چند است الان", "ساعتو بگو ببینم",
            )
            if (phrases.any { n.contains(it) }) return true
            if (compact.contains("ساعتچند") || compact.contains("چندساعت") ||
                compact.contains("چهساعتی") || compact == "زمان"
            ) {
                return true
            }
            return n.contains("ساعت") && (
                n.contains("چند") || n.contains("چه") || n.contains("الان") ||
                    n.contains("بگو") || n.contains("است") || n.contains("شده")
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
                "هوا", "اب و هوا", "ابوهوا", "دما", "چند درجه",
                "هوا چطور", "هوا چطوره", "هوا چطور است", "هوا چیه",
                "هوا چی است", "هوا خوبه", "هوا خوب است", "وضعیت هوا",
                "هوای امروز", "هوای بیرون", "درجه هوا", "گرم است",
                "سرد است", "بارون", "باران", "پیش بینی هوا",
            )
            if (phrases.any { n.contains(it) }) return true
            if (compact.contains("ابوهوا") || compact.contains("هواچطور") ||
                compact.contains("هواچیه") || compact.contains("چنددرجه") ||
                compact.contains("وضعیتهوا")
            ) {
                return true
            }
            return n.contains("هوا") || n.contains("دما") || n.contains("درجه")
        }
        return n.contains("weather") || n.contains("temperature") || n.contains("forecast") ||
            n.contains("how hot") || n.contains("how's the weather") ||
            n.contains("how is the weather") || n.contains("is it raining") ||
            n.contains("hows the weather") || n.contains("whats the weather") ||
            n.contains("what's the weather") || n.contains("how cold")
    }

    private fun mentionsLight(n: String): Boolean {
        return n.contains("چراغ") || n.contains("لامپ") || n.contains("نور") ||
            n.contains("فلش") || n.contains("چراغ قوه") || n.contains("چراغقوه") ||
            n.contains("فلاش") || n.contains("light") || n.contains("lights") ||
            n.contains("lamp") || n.contains("bulb") || n.contains("flashlight") ||
            n.contains("torch") || n.contains("flash") || n.contains("flash light")
    }

    private fun isLightOn(n: String, language: AppLanguage): Boolean {
        if (!mentionsLight(n)) return false
        if (language == AppLanguage.PERSIAN) {
            return n.contains("روشن") || n.contains("باز کن") || n.contains("بازکن") ||
                n.contains("فعال") || n.contains("روشن کن") || n.contains("روشنکن") ||
                n.contains("بزن") || n.contains("روشن شو") || n.contains("چراغ روشن")
        }
        return (n.contains("turn on") || n.contains("switch on") || n.contains("enable") ||
            n.contains("light on") || n.contains("lights on") ||
            n.contains("flashlight on") || Regex("\\bon\\b").containsMatchIn(n)) &&
            !n.contains("off")
    }

    private fun isLightOff(n: String, language: AppLanguage): Boolean {
        if (!mentionsLight(n)) return false
        if (language == AppLanguage.PERSIAN) {
            return n.contains("خاموش") || n.contains("ببند") || n.contains("قطع") ||
                n.contains("بسته") || n.contains("خاموش کن") || n.contains("خاموشکن") ||
                n.contains("ببندش") || n.contains("چراغ خاموش")
        }
        return n.contains("turn off") || n.contains("switch off") || n.contains("disable") ||
            n.contains("light off") || n.contains("lights off") ||
            n.contains("flashlight off") || Regex("\\boff\\b").containsMatchIn(n)
    }
}

object ResponseBuilder {

    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun activationPrompt(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "بله، بفرمایید. می‌توانید بگویید ساعت، هوا، یا چراغ‌قوه را روشن و خاموش کنید."
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
                    "حتماً. چراغ‌قوه الان روشن شد."
                } else {
                    "Sure. The flashlight is on now."
                }
            is AssistantCommand.LightOff ->
                if (command.language == AppLanguage.PERSIAN) {
                    "باشه. چراغ‌قوه خاموش شد."
                } else {
                    "Okay. The flashlight is off."
                }
            is AssistantCommand.Unknown ->
                if (command.language == AppLanguage.PERSIAN) {
                    "متوجه نشدم. می‌تونی بگی ساعت، هوا یا چراغ‌قوه؟"
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
                "الان ساعت ${persianNumberWords(hour)} و ${persianNumberWords(minute)} دقیقه است."
            val display = toPersianDigits(
                String.format(Locale.US, "%02d:%02d", hour, minute),
            )
            "$spoken اگر روی صفحه نگاه کنید، ساعت نمایشی $display است. " +
                "اگر کار دیگری مثل هوا یا چراغ‌قوه دارید، بفرمایید."
        } else {
            val t = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            "Right now it's $t. You can also ask about the weather or the flashlight."
        }
    }

    private fun weather(language: AppLanguage): String {
        return if (language == AppLanguage.PERSIAN) {
            "وضعیت هوای امروز این‌طور است: آسمان صاف و آفتابی است، " +
                "دمای هوا حدود بیست و هشت درجه سانتی‌گراد است، " +
                "رطوبت نسبی کم است و باد ملایمی می‌وزد. " +
                "برای بیرون رفتن هوای خوبی دارید و نیازی به چتر نیست. " +
                "اگر بخواهید ساعت را هم بگویم، کافی است بپرسید."
        } else {
            "Today's weather looks clear and sunny, around 28 degrees Celsius, " +
                "with low humidity and a light breeze. It's a nice day to be outside. " +
                "Ask if you'd like the time as well."
        }
    }

    private fun persianNumberWords(n: Int): String {
        val value = ((n % 100) + 100) % 100
        val ones = arrayOf(
            "صفر", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه",
            "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده",
            "هفده", "هجده", "نوزده",
        )
        val tens = arrayOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
        return when {
            value < 20 -> ones[value]
            value % 10 == 0 -> tens[value / 10]
            else -> "${tens[value / 10]} و ${ones[value % 10]}"
        }
    }
}
