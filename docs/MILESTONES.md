# MVP milestones

Each one: research (`docs/RESEARCH_FIRST.md`) → note in `docs/research/` → build → verify on the phone → short report → wait for the go-ahead.

MVP targets: **English shows < 600 ms after a screen settles** · **0 OCR calls on a still screen** · **taps always pass through** · **no crash over 10 tile on/off cycles**.

## M1: Tile, consent, capture
**Files:** Gradle setup, `AndroidManifest.xml`, `TapTLApp.kt`, `MainActivity.kt` (permissions screen), `tile/TranslateTileService.kt`, `capture/ConsentActivity.kt`, `capture/CaptureService.kt`, `capture/FrameSource.kt`.
**Do:** tile OFF → REQUESTING → ON → `startActivityAndCollapse(PendingIntent)` → consent → start the FGS → `getMediaProjection` → register the callback → VirtualDisplay (half-res) + ImageReader (2 images). Resize on rotation. `onStop` → tile OFF.
**Done when:** 10 on/off cycles with no crash. Logcat shows frames at **1200×540** in the game (landscape) and 540×1200 on the home screen. Stopping from the status-bar chip turns the tile OFF.

## M2: Overlay
**Files:** `overlay/OverlayController.kt`, `overlay/OverlayView.kt`.
**Do:** one non-touchable overlay window, alpha 0.8, cutout-aware, coordinates mapped from capture space to screen space. Draw test boxes.
**Done when:** test boxes line up with the settings rows (compare with `docs/reference/01_settings_basic.png`). Tapping a sidebar tab under a box still switches tabs (navigation only, never toggles). Survives rotation.

## M3: OCR + translation
**Files:** `pipeline/OcrEngine.kt`, `pipeline/Translator.kt`, `pipeline/Glossary.kt`, `pipeline/TranslationCache.kt`, `assets/glossary/*.json`, `tools/build_glossary.kts`.
**Do:** whole-screen OCR every ~1 s (temporary) → CJK lines → glossary → cache → ML Kit → replace-mode boxes with sampled colour and auto-fit text. Model download on the main screen.
**Done when:** game 基础 → 操作 shows the English from `docs/GLOSSARY_SEED.md`. The Chinese Settings app (per-app zh-CN) reads OK. A 2nd visit is all cache/glossary hits (logged). Works with Wi-Fi off after the model download.

## M4: Make it efficient + scrolling
**Files:** `pipeline/FrameDiffer.kt`, `pipeline/Pipeline.kt`, updates to the overlay.
**Do:** replace the 1 s timer with the diff → stable gate → OCR of changed areas only. Add motion pause, hide-for-capture, scroll hide → redo, number-only skip, black-frame detection.
**Done when:**
- Still screen for 60 s → 0 OCR calls.
- `adb shell input swipe 1300 880 1300 420 400` on 基础 → 操作 → boxes hide while moving, then the new rows are translated < 600 ms after stopping (compare with `03_settings_after_scroll.png`).
- Sensitivity tab swipe (no scroll) → nothing changes.
- The user plays a deathmatch (you never play) → overlay hidden during action, back in menus.
- Report diff ms, OCR ms, settle→render ms.

## M5: Polish + APK
**Do:** permission screen copy, tile label and icon, quick README (install, permissions, anti-cheat caveat, build), signed release APK with `./gradlew assembleRelease` (keystore outside the repo).
**Done when:** a fresh install on the phone goes from first launch to translated menus without help.
