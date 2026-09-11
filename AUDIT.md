# Self-debug loop — Akbar Assistant

## Stage 1 audit (user + senior designer)

### Composition / hierarchy
1. First viewport reads like a mini-dashboard (chips + transcript card + test panel), not one calm assistant composition.
2. Brand ("اکبر") competes with a large status headline.
3. Permanent flashlight chip when OFF adds noise; Siri never shows unused tool chrome.
4. Permanent "شنیده‌شده" card is utilitarian, not luxurious.
5. Test Mode is too discoverable for a polished demo surface.

### Motion / orb
6. Equalizer bars feel like a music visualizer, not Gemini/Siri intelligence.
7. LinearEasing pulse is mechanical; needs soft breathing / spring.
8. No crossfade when status / hint / reply change.
9. RMS reaction is weak / disconnected from a single soft glow.

### Typography / color / space
10. Dual-language hints always on screen create clutter.
11. Teal/blue glow blobs lean generic "AI gradient".
12. Missing nav-bar inset; content can sit under system gesture area.
13. Spacing rhythm is uneven (large gaps then dense chips).

### States / UX
14. Permission denied is a red warning line, not a calm first-run state with CTA.
15. Errors expose raw permission codes.
16. Camera permission asked at cold start; should be deferred until torch command.
17. No clear listening-wake vs listening-command visual language beyond copy.
18. Reply text can linger awkwardly across state changes.

### Voice reliability (carry into polish)
19. Need AudioFocus around TTS so replies win over ambient audio.
20. Combined wake+command should accept short leftovers ("ساعت").
21. Debounce double wake from partial+final.
22. Keep beep mute + pause-during-TTS (already present; verify).

## Target bar
Minimal · luxurious · soft motion · one job per screen · trust & intelligence.

## Stage 2 rewrite
- Rebuilt UI as a single calm composition (brand + breathing orb + status)
- Removed permanent flashlight chip / transcript card chrome
- Soft spring orb instead of equalizer bars
- Permission as a full calm gate with CTA
- Mic-first permissions; camera deferred
- TTS audio focus + Google engine + queue
- Combined wake+command accepts short leftovers
- Warmer bilingual copy

## Stage 3 critical pass
- Hide transcript while speaking (less visual noise)
- Explicit needsCameraPermission flag
- Soften denial / wake copy
- Version 1.2
- Verified clean assembleDebug

## Stage 4 final critical pass (Gemini / Siri bar)

### Issues found on re-review
23. Spoken reply duplicated: status and lastReply showed the same sentence while speaking.
24. Wake phase streamed every partial into lastHeard — visual noise vs Siri’s quiet standby.
25. Status “هی اکبر” competed with brand wordmark; wake should feel quieter.
26. Flashlight pill always bilingual; should follow active language.
27. Speak accent used purple-leaning tint; shifted to warm champagne for trust/luxury.
28. Orb spring was slightly bouncy; softened to low-stiffness no-bounce breath.
29. Weather parser missed common FA phrases (`چند درجه`, `هوا چطوره`).
30. Stale lastHeard / lastReply lingered after returning to wake.
31. Test lab still too loud; reduced to a quieter “···” affordance.
32. Camera-grant path should clear the flag calmly and invite a second ask.
33. Layer naming drift across ViewModel / speech / UI broke reliability — unified API contracts.

### Fixes applied
- Single spoken surface while SPEAKING (status holds reply; no duplicate block)
- No captions during LISTENING_WAKE; clear heard/reply on wake re-entry
- Language-aware torch pill; champagne speak glow; calmer orb physics
- Richer FA weather/time patterns; quieter test entry; version 1.3
- Coherent ViewModel ↔ speech ↔ flashlight ↔ Compose contracts


## Stage 4 complete — delivery gate
- Clean `./gradlew clean assembleDebug` succeeded
- Contracts unified across Models / ViewModel / speech / flashlight / Compose
- Single spoken surface, quiet wake captions, champagne speak glow, deferred camera permission
- Version **1.3** (versionCode 4)
- Ready for APK delivery
