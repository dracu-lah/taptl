# Test device and game

Read over adb on 2026-10-11. Re-check with the commands below. Things change.

| Item | Value |
|---|---|
| Phone | Xiaomi Poco F5 (`marble`, model `23049PCD8I`), adb serial `387acac9` (USB) |
| SoC / RAM | Snapdragon 7+ Gen 2 (`taro`), ~7 GB |
| OS | **Android 17 (SDK 37)**, AOSP-based ROM with the Pixel launcher (`com.google.android.apps.nexuslauncher`), not HyperOS |
| Display | 1080×2400 portrait, 440 dpi, **120 Hz** (90/60 available), rounded corners r=70, **cutout 94 px at the top in portrait** (it's on the side in landscape) |
| Game | `com.tencent.tmgp.codev` v1.25.0 (versionCode 3465), targetSdk 30, arm64 |
| Game activity | `com.epicgames.ue4.GameActivity` (**Unreal Engine 4**) |
| Game window | landscape `ROTATION_270`, 2400×1080, flags `KEEP_SCREEN_ON LAYOUT_IN_SCREEN FULLSCREEN …`, **no FLAG_SECURE** (capture works) |

## adb commands
```bash
adb devices -l
adb shell getprop ro.build.version.sdk
adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"           # what's on screen
adb shell dumpsys window windows | grep -A30 codev | grep -E "fl=|SECURE" # secure flag check
adb exec-out screencap -p > shot.png                                    # screenshot (landscape 2400x1080 in game)
adb shell input swipe 1300 880 1300 420 400                             # scroll the settings list (safe)
adb shell input tap 230 668                                             # sidebar "灵敏度" Sensitivity tab (safe navigation)
./gradlew :app:installDebug :testbed:installDebug
adb logcat -s TapTL
adb shell top -n 1 | grep -E "taptl|codev"
adb shell dumpsys gfxinfo dev.dracu.taptl.testbed
adb shell dumpsys thermalservice | head -40
adb shell am memory-limiter status                                      # Android 17
adb shell cmd locale set-app-locales com.android.settings --locales zh-CN   # Chinese Settings test (per-app, reversible)
adb shell cmd locale set-app-locales com.android.settings --locales en-IN   # revert
```

`input` coordinates are in the **current rotation** (landscape 2400×1080 while in game).

## Settings screen layout (landscape px, from the reference screenshots)
- Header: back + 设置 top-left. Buttons 网络诊断 / 还原默认 top-right (**never tap 还原默认**).
- Sidebar tabs x≈40–360: 基础, 界面布局, 音画设置, 英雄, 灵敏度, 准星设置, 其他 (y≈180, 300, 425, 545, 668, 790, 910).
- Top sub-tabs y≈160.
- **Scroll viewport:** about x 400–2260, y 200–940. Only this moves when scrolling. Background art is fixed and shows through translucent rows.
- Bottom fixed buttons: 客服, 反馈, 退出登录 (**never tap 退出登录**: it logs out).

## Capture sessions (the user plays, you record)
You must never queue into or play a match. Ask the user to play, then record:
```bash
adb shell screenrecord --size 1200x540 --bit-rate 8M --time-limit 120 /sdcard/taptl_<scene>.mp4
adb pull /sdcard/taptl_<scene>.mp4 docs/captures/
ffmpeg -i docs/captures/taptl_<scene>.mp4 -vf fps=5 docs/captures/<scene>/%04d.png
```
MVP needs only **deathmatch gameplay** (to check the motion pause) and the **in-match settings menu over gameplay**. Later features (chat ticker, etc.) will need agent select, buy phase, chat and the scoreboard. For pixel-exact stills, also take `screencap` (full res) during the static scenes. Don't re-encode those PNGs.

Safe to drive yourself (with the user's OK): lobby → 英雄 Agents / collection / store / battle pass / events / settings (scroll and switch tabs only).
