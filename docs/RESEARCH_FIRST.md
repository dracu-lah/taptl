# Research first: mandatory web searches

Before coding a milestone, run its searches and read the **official** sources (developer.android.com, developers.google.com/ml-kit, android.googlesource.com, library GitHub repos). Then write `docs/research/<NN>-<milestone>.md`:

```
# <NN> <milestone> research (<date>)
## Findings
- <fact> — <source URL>
## Changes to the plan
- <what changes and why>  (or "none")
## Questions for the user
- ...
```

Think it through with that context, then code. If a search finds nothing, say so. Never invent facts or version numbers.

## Session start
- Latest stable AGP, Kotlin, Compose BOM and Gradle versions, plus the compatibility table.
- Android 16 + 17 behaviour changes. Look for anything new on MediaProjection, overlays or foreground services. Already known: the 17 memory limiter, the BAL `IntentSender` change, forced edge-to-edge, 16 KB pages.
- Is compileSdk 37 in `~/Android/Sdk/platforms`? If not, which `sdkmanager` package?

## M1: Tile, consent, capture
- `TileService.startActivityAndCollapse(PendingIntent)` (34+) and tile state sync in `onStartListening`.
- MediaProjection guide: consent each session, single-use token, register the callback before `createVirtualDisplay`, `onCapturedContentResize`, single-app capture, auto-stop on lock.
- The `mediaProjection` FGS type and the start-order rules.
- PendingIntent BAL options on Android 17.
- VirtualDisplay rotation: `resize()` + swapping the surface.

## M2: Overlay
- Untrusted-touch rule (max obscuring opacity 0.8, window alpha vs. pixel alpha).
- Display cutout in landscape, `WindowMetrics`.
- Is there any **public** API to exclude an overlay from capture on 14–17? (Expected: no → hide-for-capture.)

## M3: OCR + translation
- ML Kit Text Recognition v2 Chinese: bundled artifact, size, 16 KB alignment.
- ML Kit Translation: model download API, size, attribution rules.
- valorant-api.com endpoints + `language=zh-CN` / `en-US`, joining by `uuid`.
- Room + KSP current versions.

## M4: Efficiency + scrolling
- ImageReader `maxImages`, `Image.close()` timing, reading the Y/luma plane fast from RGBA.
- Android 17 memory limiter (`am memory-limiter`).

## M5: Polish + APK
- Signing config with the keystore outside the repo. `assembleRelease` + R8 rules for ML Kit.
