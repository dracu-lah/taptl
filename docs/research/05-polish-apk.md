# 05 Polish + APK research (2026-10-11)

## Findings
- Signing: load a properties file with `java.util.Properties` before `android {}` and build `signingConfigs.create("release")` from it; the file can live outside the repo. Guard with `exists()` so other builds still work. — https://developer.android.com/studio/publish/app-signing#secure-shared-keystore
- R8: both ML Kit AARs ship consumer rules (`text-recognition-chinese` keeps native names; `translate` has OkHttp/Conscrypt `-dontwarn`). No app rules needed. — inspected AARs from https://dl.google.com/android/maven2/com/google/mlkit/

## Changes to the plan
- Keystore: `~/.android/taptl-release.jks`, settings in `~/.android/taptl-keystore.properties` (mode 600). Without that file, `assembleRelease` builds unsigned.
- Release: R8 + resource shrinking on. 32 MB APK, arm64 only, 16 KB aligned. No version bump or tag (no releases exist).

## Questions for the user
- none

## Verified on the phone
- Fresh install of the signed release APK (32.8 MB) → overlay, notifications, model download, add tile → translated TapTap with no adb help except tapping. `apksigner verify` OK (CN=TapTL), `zipalign -c -P 16` OK.
