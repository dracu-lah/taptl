# 01 Tile, consent, capture research (2026-10-11)

Covers the "Session start" and "M1" searches in `docs/RESEARCH_FIRST.md`. API signatures were checked against the local `~/Android/Sdk/platforms/android-37.0/android-stubs-src.jar` (the reference pages did not render for the fetcher).

## Findings

### Toolchain
- AGP latest stable is **9.4.0** (Sept 2026): Gradle min/default **9.6.0**, JDK min 17, build-tools 36.0.0, max API **37**. — https://developer.android.com/build/releases/gradle-plugin
- AGP 9 has **built-in Kotlin**: do not apply `org.jetbrains.kotlin.android` (it errors). `kotlinOptions {}` → `kotlin { compilerOptions {} }`. kapt is incompatible (use KSP later for Room). — https://developer.android.com/build/migrate-to-built-in-kotlin
- The Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` is still applied, with the same version as Kotlin. — https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Kotlin latest stable is **2.4.21** (2026-10-08). — https://kotlinlang.org/docs/releases.html
- Gradle latest is 9.8.1 and runs on JDK 17–27. I use **9.6.0**, AGP 9.4's tested default. — https://gradle.org/releases/ , https://docs.gradle.org/current/userguide/compatibility.html
- Compose BOM **2026.09.00** (ui 1.12.1, material3 1.4.0). Compose 1.12 **requires compileSdk 37 and AGP 9**. — https://developer.android.com/develop/ui/compose/bom
- `androidx.activity:activity-compose` **1.13.0**; `androidx.core:core` **1.19.1** (core-ktx APIs merged into core; core-ktx is now empty). — https://developer.android.com/jetpack/androidx/releases/activity , https://developer.android.com/jetpack/androidx/releases/core
- Local: `platforms/android-37.0` is installed (Platform 17, rev 2), so no `sdkmanager` step is needed. The system Java is a **JRE 25 (no javac)**, so the build uses the JDK 21 at `~/.jdks/jdk-21.0.12+8` (Gradle daemon toolchain).

### Android 16 / 17 behaviour
- 17 (target 37): BAL protections extended to `IntentSender`. Move off `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` to `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` (API 36). — https://developer.android.com/about/versions/17/behavior-changes-17
- 17 (all apps): RAM-based app memory limits. Kills show `REASON_OTHER` with `MemoryLimiter:AnonSwap`; inspect with `am memory-limiter status`. — https://developer.android.com/about/versions/17/behavior-changes-all
- 16 (target 36): edge-to-edge can't be opted out; predictive back is on by default. Orientation/resizability is ignored on sw≥600dp (phone is below that). — https://developer.android.com/about/versions/16/behavior-changes-16
- 16 (all apps): 16 KB page compat mode exists, but apps should still be 16 KB aligned (matters at M3 with ML Kit natives). — https://developer.android.com/about/versions/16/behavior-changes-all
- **Nothing new on MediaProjection, overlays, FGS mediaProjection or TileService** in the 16/17 change pages.

### Tile
- `startActivityAndCollapse(Intent)` throws on 34+; use the `PendingIntent` overload (`FLAG_IMMUTABLE`). — https://developer.android.com/about/versions/14/behavior-changes-14
- Active tile: `<meta-data android:name="android.service.quicksettings.ACTIVE_TILE" android:value="true"/>`, then the app calls `TileService.requestListeningState()` when its state changes. One update per listening window. Label ≤ 18 chars. `unlockAndRun()` when locked. — https://developer.android.com/develop/ui/views/quicksettings-tiles
- PendingIntent creator BAL opt-in (target 35+): `ActivityOptions.setPendingIntentCreatorBackgroundActivityStartMode(...)`, passed as the options bundle to `PendingIntent.getActivity`. Blocked launches log "Background activity launch blocked!". — https://developer.android.com/guide/components/activities/background-starts

### MediaProjection
- Consent is needed before every session. A token is single-use; on 14+ reusing the intent or calling `createVirtualDisplay()` twice throws `SecurityException`. — https://developer.android.com/media/grow/media-projection
- You **must register `MediaProjection.Callback` before `createVirtualDisplay()`** or it throws `IllegalStateException`. After `onStop()`, no new virtual display can be made.
- `onCapturedContentResize(w, h)` fires right after capture starts and when the captured size changes (e.g. rotation). Resize the VirtualDisplay and the surface to match. `VirtualDisplay.resize(w, h, dpi)` and `setSurface(surface)` are both public.
- 14+ defaults to letting the user pick single-app or full screen. `createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())` forces full screen (OEMs may override).
- 15 QPR1+: a status-bar chip lets the user stop capture, and capture **auto-stops on lock**. Both arrive as `onStop()`.
- FGS order: get consent from `createScreenCaptureIntent()` → start the FGS (`foregroundServiceType="mediaProjection"`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION`) → **then** `getMediaProjection()`. Can't start it from `BOOT_COMPLETED` (15+). — https://developer.android.com/develop/background-work/services/fgs/service-types#media-projection

## Changes to the plan
- Gradle: no `kotlin-android` plugin (AGP 9 built-in Kotlin). Use `androidx.core:core` instead of `core-ktx`.
- compileSdk 37 / targetSdk 36 / minSdk 29 confirmed workable (Compose 1.12 even requires compileSdk 37).
- Build JDK = 21 from `~/.jdks` via `gradle/gradle-daemon-jvm.properties`, because the system Java has no compiler.
- Consent uses `createConfigForDefaultDisplay()` on 34+ so the user isn't asked to pick an app (the plan assumes whole-screen capture; M1's "540×1200 on home" check needs it).
- Rotation: use `onCapturedContentResize` for resizing, as the docs suggest. Verify on the phone that it fires on game rotation.
- Tile PendingIntent: creator BAL mode `ALLOW_IF_VISIBLE` on 36+. If the phone logs a blocked launch, switch to the safer working option and note it here.

## Questions for the user
- **Single-app capture (later, not M1):** if the user picks "a single app" (the game) on 14+, our overlay is not in the captured image at all, which would remove the need for hide-for-capture. The cost is one extra pick in the consent dialog each time. MVP keeps full screen as planned.

## Verified on the phone (Poco F5, Android 17)
- Tile → consent opened with no "Background activity launch blocked" in logcat, so the creator mode `ALLOW_IF_VISIBLE` works.
- With `createConfigForDefaultDisplay()` the dialog shows only "Share entire screen".
- `onCapturedContentResize` fires right after start and on every rotation (home → game → home). Frames: **540×1200** at home, **1200×540** in the game.
- The game sends ~31 frames/s even on a still settings screen (UE4 keeps redrawing; this time a "重连中" spinner was showing). M4's diff gate is essential.
- Status-bar chip → "Stop sharing" → `onStop` → service destroyed → tile OFF.
- 10 on/off cycles: 10× OFF→REQUESTING→ON→OFF, empty crash buffer, same process, no leaked VirtualDisplay. Back on consent → OFF.
- `am memory-limiter status` → "disabled" on this ROM.
