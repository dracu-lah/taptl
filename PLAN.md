# TapTL: MVP plan

## TL;DR
- Tap the **TapTL tile** in Quick Settings → English **replaces** the Chinese on screen. Tap again → off.
- Taps still reach the game.
- Free and offline: ML Kit OCR + ML Kit translation, plus a glossary for game words.
- Works where you read: **menus, settings, store, popups**. Hides itself during gameplay.
- Scrolling: boxes hide while the list moves and come back translated ~0.5 s after you stop.
- Android 14+ asks "start screen capture?" every time you switch it on. That's an OS rule.

## MVP scope
**In:** tile toggle · screen capture · Chinese OCR · glossary → cache → offline translate · replace-mode overlay · only work on change · pause during motion · scroll handling (hide → redo) · landscape game support · minimal onboarding (overlay + notification permissions) · debug log of timings · signed APK built locally.

**Out (see "Later"):** online engine, chat ticker, pinned regions, floating bubble, per-game profiles, Usage access, corrections screen, translation packs, export/import, presets, thermal throttling, duty-cycled capture, scroll tracking, paragraph mode, update checker, GitHub Actions, testbed app.

## Decisions
| Topic | MVP choice |
|---|---|
| Stack | Kotlin, Gradle KTS + version catalog, minSdk 29, targetSdk 36, compileSdk 37 (confirm in research) |
| UI | One Compose screen: permissions + a "how to add the tile" hint. Overlay = plain Canvas `View` |
| Capture | MediaProjection → VirtualDisplay (half-res) → ImageReader (maxImages 2), in a `mediaProjection` foreground service |
| OCR | ML Kit Text Recognition v2, Chinese, bundled model |
| Translate | Glossary → in-memory LRU + SQLite cache → ML Kit on-device zh→en (model downloaded on first run, ~30 MB) |
| Glossary | Hand-made settings list (`docs/GLOSSARY_SEED.md`) + official Valorant names generated once from valorant-api.com (zh-CN ↔ en-US) into a bundled JSON |
| Overlay | One `TYPE_APPLICATION_OVERLAY` window, `NOT_TOUCHABLE | NOT_FOCUSABLE`, alpha 0.8 (needed for touch pass-through on Android 12+), cutout-aware |
| Anti-cheat | No AccessibilityService, no input injection, no memory access. Test on a secondary account first |

## Architecture
```
taptl/app/src/main/java/dev/dracu/taptl/
  TapTLApp.kt
  MainActivity.kt                    // Compose: permissions, model download, tile hint
  tile/TranslateTileService.kt       // OFF / REQUESTING / ON; startActivityAndCollapse(PendingIntent) on 34+
  capture/ConsentActivity.kt         // transparent; consent → start service → finish
  capture/CaptureService.kt          // FGS; owns FrameSource + Pipeline + Overlay; onStop → tile OFF
  capture/FrameSource.kt             // VirtualDisplay + ImageReader; resize on rotation
  pipeline/FrameDiffer.kt            // 1/8 luma tile hashes → dirty tiles, stable/moving state
  pipeline/OcrEngine.kt              // ML Kit on dirty crops; keep CJK lines only
  pipeline/Translator.kt             // glossary → cache → ML Kit
  pipeline/Glossary.kt               // exact match, then longest-match on known terms
  pipeline/TranslationCache.kt       // LRU + SQLite
  pipeline/Pipeline.kt               // one low-priority worker, latest-frame-wins
  overlay/OverlayController.kt       // add/remove window, hide-for-capture, rotation
  overlay/OverlayView.kt             // boxes: sampled bg colour, contrast text, auto-shrink/2-line wrap
app/src/main/assets/glossary/{settings_zh_en.json, valorant_zh_en.json}
tools/build_glossary.kts             // one-off generator for valorant_zh_en.json
```

## How it works (per frame)
1. Frames arrive only when the screen changes. Process at most ~5 per second and drop stale ones.
2. **Diff:** compare a tiny 1/8-res grayscale grid tile by tile. Nothing changed → do nothing.
3. **Motion pause:** if most of the screen keeps changing for more than 1.5 s (gameplay), hide the overlay and skip OCR until it calms down.
4. **Stable gate:** OCR a changed area only after it has been still for ~250 ms.
5. **Hide-for-capture:** hide the overlay for 1–2 frames, grab a clean frame, show it again, so the app never reads its own boxes.
6. **OCR** only the changed areas, and keep lines that contain Chinese characters.
7. **Translate:** glossary, then cache, then ML Kit. Digits and English stay unchanged.
8. **Draw:** one Canvas pass. Box colour is sampled from the real background, and text auto-shrinks, then wraps to 2 lines.

## Scrolling (MVP behaviour)
- The list starts moving → the boxes in that area hide right away.
- You stop → ~250 ms settle → OCR that area → English back in about 0.3–0.6 s. Rows already seen are instant from cache.
- Tab switch or popup → clear the changed area and redo it.
- Slider % changes and timers → skipped (numbers only).
- Pages that don't scroll → no change, nothing happens.

## Device and game (checked over adb)
- Poco F5, **Android 17**, 1080×2400, 120 Hz, AOSP ROM. Details in `docs/DEVICE.md`.
- Game `com.tencent.tmgp.codev` (UE4), **landscape 2400×1080**, **no FLAG_SECURE**, so capture works.
- Settings UI: white text on translucent dark rows over fixed character art. Short repeated labels → the glossary covers most of it. Screenshots in `docs/reference/`.
- Android 16/17 rules that matter: memory limiter (keep buffers small), BAL `IntentSender` change (tile PendingIntent with allow-if-visible), forced edge-to-edge, 16 KB native-lib alignment.

## MVP edge cases
| # | Case | Fix |
|---|---|---|
| 1 | Overlay reads itself | Hide-for-capture |
| 2 | Gameplay / constant motion | Motion pause, overlay hidden |
| 3 | Scrolling | Hide moving boxes, redo after settle |
| 4 | Landscape game / rotation | `VirtualDisplay.resize()` + new reader surface, remap coordinates, cutout-aware |
| 5 | Taps blocked by overlay | `NOT_TOUCHABLE` + window alpha 0.8 |
| 6 | English longer than Chinese | Auto-shrink → 2 lines → ellipsis |
| 7 | Literal game names | Settings glossary + official Valorant names |
| 8 | Numbers / mixed text | Translate Chinese parts only, skip number-only changes |
| 9 | Animated menu background | Judge stability per area, not the whole screen |
| 10 | Game blocks capture (black frames) | Detect → notify → stop |
| 11 | Capture stopped by system (chip, lock) | `onStop` → service stops, tile OFF |
| 12 | Tile double-tap / token reuse (14+ throws) | Strict state machine, fresh consent each time |
| 13 | No translation model / offline first run | Download in the app with progress. OCR model is bundled |
| 14 | Memory (Android 17 limiter) | 2 reader images, reuse buffers, close images immediately |

## Milestones
See `docs/MILESTONES.md` (5 steps).

## Later (after MVP works)
Online engine upgrade (gtx) · in-match chat ticker + pinned regions · floating bubble / notification actions (Translate now, Peek) · per-game profiles via Usage access · corrections screen + shareable translation packs · speed presets + thermal throttling · duty-cycled capture · scroll tracking (boxes slide with the list) · paragraph mode for patch notes · ignore regions (player names, minimap) · update checker + GitHub Actions releases · testbed app + unit tests.

Ideas from research (multi-language follow-ups):
- Per-game word lists keyed by app package, plus import of user-made lists.
- User corrections: pick a wrong line from a list of recent translations, save the fix.
- OCR fix-up rules (common misreads per language/game, applied before translation).
- Floating panel mode: translations listed in a small movable panel instead of boxes over the text.
- Join broken lines into sentences before translating; vertical Japanese text.
- Chinese UI localization of the app itself.
- IzzyOnDroid listing (main F-Droid will likely reject ML Kit, which is proprietary).
