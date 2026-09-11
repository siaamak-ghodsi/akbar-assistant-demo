# How to open and run (Android Studio)

1. Install **Android Studio Hedgehog or newer** (with Android SDK 34).
2. Clone this repo, then **File → Open** and select the project root folder (`akbar-assistant-demo`).
3. Wait for Gradle sync to finish (Android Studio will download the Gradle wrapper if needed).
4. Connect a device/emulator with **Google Play services** (speech recognition requires it) and grant **Microphone** permission when prompted.
5. Click **Run ▶** on the `app` configuration.
6. Say **«هی اکبر»** or **"Hey Akbar"** to activate, then try a command — or tap **Test Mode** to exercise all commands without voice.

---

# Akbar Assistant (اکبر دستیار)

Bilingual (Persian + English) Android voice assistant demo — Kotlin + Jetpack Compose.

## Features

- **Wake words:** `هی اکبر` / `Hey Akbar` via continuous `SpeechRecognizer`
- **Commands (both languages):** current time, mock weather, light on/off
- **TTS** replies in `fa-IR` or `en-US` matching the user’s language
- Dark bilingual UI with listening waveform, language badge (FA/EN), bulb visual, and **Test Mode**

## Requirements

| Item | Value |
|------|-------|
| Package | `com.akbar.assistant.demo` |
| Min SDK | 26 |
| Target SDK | 34 |
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

- Speech recognition needs a network connection on most devices (Google speech service).
- For Persian TTS, install a Persian voice pack in device settings if the default engine lacks `fa-IR`.
- Emulators without Google Play may not support `SpeechRecognizer`; use a physical device or a Play-enabled AVD.
