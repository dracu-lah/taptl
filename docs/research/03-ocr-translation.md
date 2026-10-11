# 03 OCR + translation research (2026-10-11)

## Findings
- OCR: `com.google.mlkit:text-recognition-chinese:16.0.1` (bundled, latest). Adds ~4.9 MB model + `libmlkit_google_ocr_pipeline.so` (arm64 ~11 MB). Characters should be ≥ 16×16 px; above ~24 px gives no gain. `Tasks.await` works off the main thread (throws on main). — https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Translate: `com.google.mlkit:translate:17.0.3` (latest). `downloadModelIfNeeded()`, `RemoteModelManager.isModelDownloaded(TranslateRemoteModel)`. Models ~30 MB; works offline after download. — https://developers.google.com/ml-kit/language/translation/android
- Attribution: ML Kit translation terms require following the Cloud Translation attribution rules (make clear it's an automatic Google translation, disclaimer). — https://developers.google.com/ml-kit/language/translation/translation-terms , https://docs.cloud.google.com/translate/attribution
- All ML Kit native libs (arm64, x86_64) have 16 KB-aligned LOAD segments (checked with readelf). Release APK passes `zipalign -c -P 16`.
- Room 2.8.5 stable (Room 3 exists as `androidx.room3` 3.0.3). KSP 2.3.12; KSP no longer follows Kotlin versions, and 2.3.10+ fixed AGP 9 built-in Kotlin. Built fine here with Kotlin 2.4.21 + AGP 9.4.0. — https://github.com/google/ksp/releases , https://developer.android.com/jetpack/androidx/releases/room
- valorant-api.com: `language=zh-CN` / `en-US` confirmed. Agents (29), abilities by `slot`, `role`, weapons + `shopData.categoryText`, maps (filter on `tacticalDescription != null`), game modes, gear, competitive tiers (no uuid: join on `tier` in the last table; skip tiers 1–2 "Unused"). zh-CN = simplified. — https://valorant-api.com/v1/agents?isPlayableCharacter=true&language=zh-CN

## Changes to the plan
- Room 2.8.5 + KSP 2.3.12 (not Room 3: 2.8 is the documented, safer option).
- `abiFilters = arm64-v8a` to keep the APK small (release APK 32 MB).
- OCR runs on a crop scaled ×1.5 (half-res text is ~15 px, below ML Kit's 16 px minimum).
- `tools/build_glossary.kts` is dependency-free (`kotlinc -script`). It produced 241 entries.
- A small "automatic translation by Google Translate" note is on the main screen and in the README.
- The plan's "whole-screen OCR every ~1 s" interim step was skipped: M4's change-driven pipeline was built directly.

## Questions for the user
- Do you want a visible "Google Translate" credit on the overlay itself? For now it is on the main screen and in the README only.

## Verified on the phone (Poco M2 Pro, release build)
- Reference settings screen: 39 of 42 lines from the glossary after adding (a) stripping icon glyphs (◇◆<|!?) at line ends and (b) a near-match (1 wrong char in 4–6 char terms, 2 in 7+), tried before the cache.
- OCR at ×2 of the half-res crop: full screen 1200×540 → ~300–370 ms. Small regions 50–150 ms.
- First visit with many new lines is slow: ML Kit translate ~50–80 ms per new line (48 lines ≈ 2 s). Second visit is cache (≈1 ms per line).
- Short 2-char labels are still misread now and then at half-res (英雄 → engine junk). Check on the game; if it's bad, capture at full res for OCR.
- Chinese Settings (per-app zh-CN): OCR reads 网络和互联网, 蓝牙、配对, 通知… correctly (logged); boxes can't show there (Settings hides overlays).
- Release build: R8 broke ML Kit's model registry (NPE in `RemoteModelManager`). Fixed with `-keep class com.google.mlkit.** { *; }`.
- Translator model downloaded in a few seconds over Wi-Fi from the main screen.
