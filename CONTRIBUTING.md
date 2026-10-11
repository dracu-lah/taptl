# Contributing to TapTL

Thanks for helping. Small, focused changes are easiest to review.

## Build

Needs JDK 17+ (the build asks Gradle for JDK 21) and the Android SDK with platform 37.

```bash
./gradlew :app:assembleDebug
```

## Test on a phone

Plug in a phone with USB debugging on, then:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s TapTL                          # timings and errors
adb exec-out screencap -p > screen.png       # check what the overlay drew
```

Please test on a secondary game account. Never queue into a real match to test. Say which source language you picked when reporting a bug.

## Game word list

The built-in word list covers Valorant Mobile CN (Chinese) only. Other languages use the on-device translator alone; open an issue before starting a word list for another game or language.

The Valorant names come from a generator script. To refresh them (needs `kotlinc`):

```bash
kotlinc -script tools/build_glossary.kts
```

Settings words are hand-made in `app/src/main/assets/glossary/settings_zh_en.json`. Fixes there are very welcome; say where in the game you saw the text.

## Code

- Match the style of the code around your change (Kotlin, Compose for app screens, a plain Canvas `View` for the overlay).
- Keep it simple. TapTL is an MVP: see the "Later" list in [PLAN.md](PLAN.md) before adding big features, and open an issue first.
- No accessibility services, no input injection, no reading game memory. Pull requests that add these will be closed.
- Keep work on the capture path cheap: no per-frame allocations, only work when the screen changes.

## Pull requests

- One topic per pull request.
- Say what you changed, why, and how you tested it (phone model and Android version).
- Add before/after screenshots for anything you can see.
