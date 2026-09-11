# How to open and run (Android Studio)

1. Install **Android Studio Hedgehog or newer** (Android SDK 34).
2. Clone: `git clone https://github.com/siaamak-ghodsi/akbar-assistant-demo.git`
3. **File → Open** the project root (`akbar-assistant-demo`).
4. Let Gradle sync finish (Android Studio downloads the Gradle wrapper JAR automatically if missing).
5. Use a **physical device** or an emulator with **Google Play** (speech recognition needs it).
6. Click **Run ▶**, allow **Microphone** permission.
7. Say **«هی اکبر»** or **"Hey Akbar"**, then a command — or open **Test Mode** to run all commands without voice.

---

# Akbar Assistant (اکبر دستیار)

Bilingual (Persian + English) Android voice assistant demo — **Kotlin + Jetpack Compose**.

## Features

- Wake words: `هی اکبر` / `Hey Akbar` (continuous `SpeechRecognizer`)
- Commands in both languages: time, mock weather, light on/off
- TTS replies in `fa-IR` or `en-US` matching the user language
- Dark UI: status text, waveform listening indicator, FA/EN badge, bulb visual, last heard text, **Test Mode**

## Requirements

| Item | Value |
|------|-------|
| Package | `com.akbar.assistant.demo` |
| Min SDK | 26 |
| Target / Compile SDK | 34 |
| Permission | `RECORD_AUDIO` |

## Supported phrases

### Wake
- FA: هی اکبر
- EN: Hey Akbar

### Time
- FA: ساعت چنده؟ / ساعت چند است؟
- EN: What time is it? / What's the time?

### Weather (mock)
- FA: هوا چطوره؟ / وضعیت هوا
- EN: What's the weather? / How's the weather?

### Light
- FA: چراغ رو روشن کن / چراغ روشن / چراغ رو خاموش کن
- EN: Turn on the light / Turn off the light / Light on / Light off

## Project structure

```
app/src/main/java/com/akbar/assistant/demo/
  MainActivity.kt
  AssistantViewModel.kt
  Models.kt
  commands/CommandParser.kt
  speech/ContinuousSpeechRecognizer.kt
  speech/AssistantTts.kt
  ui/AssistantScreen.kt
```

## Notes

- Speech recognition usually needs network (Google speech service).
- Install a Persian TTS voice on the device if `fa-IR` is missing.
- Emulators without Google Play may not support `SpeechRecognizer`.
