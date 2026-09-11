# Based on / adapted from

This demo reuses well-known open-source patterns instead of inventing speech plumbing from scratch:

1. **[StephenVinouze/KontinuousSpeechRecognizer](https://github.com/StephenVinouze/KontinuousSpeechRecognizer)** — continuous `SpeechRecognizer` restart loop + wake-keyword gate (adapted to detect «هی اکبر» / "Hey Akbar").
2. **[Android SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer)** + common continuous-listening restart pattern (restart after `onResults` / `ERROR_NO_MATCH` / timeout).
3. **Android `TextToSpeech`** official API for bilingual `fa-IR` / `en-US` replies.
4. **Jetpack Compose Material 3** dark UI (official Android samples / Compose BOM).

Only the bilingual command set, wake phrases, bulb UI, and Test Mode are custom for this demo.

---

# How to open and run (Android Studio)

1. Install **Android Studio Hedgehog or newer** (SDK 34).
2. `git clone https://github.com/siaamak-ghodsi/akbar-assistant-demo.git`
3. **File → Open** → select the project root.
4. Wait for Gradle sync (wrapper JAR is included).
5. Use a **device/emulator with Google Play** (speech needs it). Grant **Microphone**.
6. Run ▶ `app`.
7. Say **«هی اکبر»** or **"Hey Akbar"**, then a command — or tap **Test Mode**.

---

# Akbar Assistant (اکبر دستیار)

Bilingual Persian + English voice assistant demo — Kotlin + Jetpack Compose.

| Item | Value |
|------|-------|
| Package | `com.akbar.assistant.demo` |
| Min / Target SDK | 26 / 34 |
| Permission | `RECORD_AUDIO` |

## Wake
- FA: هی اکبر  
- EN: Hey Akbar  

## Commands
- **Time** — ساعت چنده؟ / What time is it?
- **Weather (mock)** — هوا چطوره؟ / What's the weather?
- **Light** — چراغ رو روشن/خاموش کن / Turn on/off the light
