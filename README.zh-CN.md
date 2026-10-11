<div align="center">

<img src="docs/images/banner.png" alt="TapTL：在安卓游戏画面上实时显示英文" width="100%">

<br>

[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/10)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/dracu-lah/taptl?label=release)](https://github.com/dracu-lah/taptl/releases/latest)

**安卓游戏实时屏幕翻译：12 种语言 → 英文。**<br>
最早为《无畏契约：源能行动》（Valorant Mobile 国服）打造，也适用于任何应用和游戏。<br>
设置完成后离线可用。通过快捷设置磁贴一键开关。

[下载](https://github.com/dracu-lah/taptl/releases/latest) · [安装](#安装) · [支持的语言](#支持的语言) · [工作原理](#工作原理) · [常见问题](#常见问题) · [构建](#构建)

[English](README.md)

</div>

> [!WARNING]
> **反作弊提醒。** TapTL 只是录制屏幕并在上面绘制文字。它不读取游戏内存、不模拟点击、不使用无障碍服务。即便如此，游戏的反作弊系统仍可能不喜欢屏幕悬浮层。**请先用小号测试。**

## 截图

| 之前 | 之后 | 应用 |
|:---:|:---:|:---:|
| <img src="docs/screenshots/settings-before.png" alt="中文的游戏设置界面" width="100%"> | <img src="docs/screenshots/settings-after.png" alt="开启 TapTL 后的同一界面，显示英文" width="100%"> | <img src="docs/screenshots/app.png" alt="TapTL 设置界面" width="100%"> |

<img src="docs/screenshots/audio-after.png" alt="开启 TapTL 后的音频设置" width="100%">

### 演示视频

在 Poco F5（Android 17）上录制，未剪辑：

- [安装和设置（1 分钟）](docs/videos/setup.mp4)
- [游戏内使用（75 秒）](docs/videos/in-game.mp4)

## 这是什么

TapTL 会读取屏幕上的文字，把英文翻译直接盖在原文上面。适合想用英文玩国服游戏的玩家，或者看不懂某个外语游戏菜单的人。

## 为什么选 TapTL

很多游戏翻译、屏幕翻译工具需要 root、无障碍服务、联网账号，或者每次都要手动点一下才翻译。TapTL 都不需要：**不需要 root，不用无障碍服务，设置后无需联网，也不碰游戏本身。** 它只看屏幕，然后把英文叠在上面。

## 功能

- **免费、离线。** 文字识别（OCR）和谷歌翻译都在手机上运行（ML Kit）。一次下载后不再联网。
- **12 种语言 → 英文。** 首次打开时选择语言，之后随时可改。
- **只在你阅读时工作。** 菜单、设置、商店、弹窗。对局中自动隐藏。
- **点击照样传给游戏。** 悬浮层无法被点击。
- **一键开关。** 在应用里点 Start 或用快捷设置磁贴开启；用磁贴或通知里的 Stop 关闭。没有悬浮按钮。
- **省电。** 只有画面变化时才工作。
- **内置游戏词表**（无畏契约 源能行动 国服）：英雄、武器、地图和设置项使用官方英文名称。

## 支持的语言

所有文字识别模型都已打包在应用内。每种语言的翻译模型（约 40 MB）只需下载一次，之后离线可用。

| 语言 | 识别 | 游戏词表 |
|---|---|---|
| 中文 | 是 | 无畏契约 源能行动 国服 |
| 日语（日本語） | 是 | |
| 韩语（한국어） | 是 | |
| 印地语（हिन्दी） | 是 | |
| 西班牙语（Español） | 是 | |
| 葡萄牙语（Português） | 是 | |
| 法语（Français） | 是 | |
| 德语（Deutsch） | 是 | |
| 意大利语（Italiano） | 是 | |
| 越南语（Tiếng Việt） | 是 | |
| 印尼语（Bahasa Indonesia） | 是 | |
| 土耳其语（Türkçe） | 是 | |

所有语言都翻译**成英文**。内置游戏词表目前只覆盖无畏契约 源能行动国服，其他语言只用翻译模型。

## 下载

从 **[GitHub Releases](https://github.com/dracu-lah/taptl/releases/latest)** 下载最新的 `app-release.apk`，或[自己构建](#构建)。

## 安装

1. 下载 `app-release.apk`（来自 [Releases](https://github.com/dracu-lah/taptl/releases/latest)，或见[构建](#构建)），在手机上打开。如有提示，允许“安装未知应用”。
2. 打开 **TapTL**。简短的引导让你选择语言，之后主页会列出还没完成的设置：
   - **显示在其他应用上层**：让它能在游戏上面显示英文。
   - **通知**：翻译开启时显示一条带 Stop 按钮的通知（安卓要求）。
   - **语言包**：每种语言约 40 MB，只需一次，有下载进度条。之后离线可用。
3. 可选：主页提示时添加 **Translate** 磁贴（安卓 13 及以上一键确认），或下拉两次通知栏，点铅笔图标，把 “Translate” 拖进去。

语言、权限和引导都在齿轮图标（设置）里。

## 使用

- 在应用里点 **Start**（或点 **Translate** 磁贴），然后打开游戏。安卓每次都会询问是否共享屏幕，点 **共享屏幕**。这是安卓的规定。
- 画面静止约半秒后，英文就会出现在原文上。列表滚动时，翻译框会先隐藏，停下后再出现。
- 点通知或应用里的 **Stop**、再点一次磁贴，或在状态栏的红色录屏标志里停止共享，即可关闭。锁屏也会关闭。
- 如果某个应用禁止截屏，TapTL 会提示你并自动关闭。

## 权限

| 权限 | 用途 |
|---|---|
| 显示在其他应用上层 | 在游戏上面显示英文。悬浮层不能点击，点击会传给游戏。 |
| 屏幕录制（每次询问） | 读取屏幕上的文字。画面不离开手机。 |
| 前台服务 | 游戏打开时保持录屏运行，并显示通知。 |
| 通知 | 就是那条通知。 |
| 网络 | 只用于每种语言下载一次翻译模型。 |

翻译由谷歌翻译（手机端）自动完成，可能有误。无畏契约 源能行动国服的游戏用语来自内置词表，按官方 Valorant 名称整理。

## 工作原理

```
录屏 → 只看变化的区域 → 识别文字（OCR，在手机上） → 词表 / 翻译 → 悬浮层（画面移动时隐藏）
```

1. **录屏。** 使用安卓正常的屏幕共享，半分辨率。画面不离开手机。
2. **只看变化的区域。** 每帧做一个很小的灰度缩略图，和上一帧比较。没变化就不工作。如果整个画面一直在动（对局中），悬浮层隐藏。
3. **识别文字。** ML Kit 只识别变化的部分，并保留你所选语言的文字行。
4. **翻译。** 先查内置游戏词表，再查缓存，最后用手机上的谷歌翻译。数字和英文保持不变。
5. **绘制。** 一个半透明、不可点击的图层，把英文框盖在原文上，颜色与背景相配。

它**不会**做的事：不使用无障碍服务，不模拟点击，不读取游戏内存。

## 常见问题

**支持哪些游戏？**
任何允许录屏的应用或游戏。它是在无畏契约 源能行动国服上开发和调校的，所以这款游戏效果最好。

**需要联网吗？**
每种语言只需联网一次下载翻译模型，之后完全离线。

**为什么要我自己添加磁贴？**
安卓要求用户亲自确认任何快捷设置磁贴。安卓 13 及以上，TapTL 会弹出一键确认。更早的版本请下拉两次通知栏，点铅笔图标，把 “Translate” 拖进去。

**为什么每次都要确认共享屏幕？**
这是安卓的规定（安卓 14 及以上），应用无法跳过。

**会被反作弊封号吗？**
TapTL 只是像录屏软件一样看屏幕，然后在上面绘制文字。它不碰游戏、不读内存、不动你的点击。但无法保证：反作弊怎么判断由游戏决定。请先用小号测试。

**对局中能翻译吗？**
不能。对局中它会隐藏，不会挡住你要看的东西。它用于菜单、设置、商店和弹窗。

**能翻译成中文或其他语言吗？**
暂时不能，目前只翻译成英文。

## 已知限制

- 只用于菜单。对局中隐藏，不翻译局内聊天。
- 安卓 14 及以上每次开启都会询问“是否共享屏幕”。
- 有些应用禁止截屏，TapTL 无法读取，会自动关闭。
- 翻译是机器翻译，可能出错或不通顺。
- 只能翻译成英文。游戏词表只覆盖无畏契约 源能行动国服。
- 只支持 64 位 ARM 手机（近几年的手机几乎都是）。
- 应用界面目前只有英文。

## 构建

需要 JDK 17+（构建会让 Gradle 使用 JDK 21）和带 platform 37 的 Android SDK。

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk
```

发布签名读取 `~/.android/taptl-keystore.properties`（不在仓库内）：

```properties
storeFile=/home/you/.android/taptl-release.jks
storePassword=...
keyAlias=taptl
keyPassword=...
```

没有这个文件时，发布版 APK 不签名。生成密钥：

```bash
keytool -genkeypair -keystore ~/.android/taptl-release.jks -alias taptl -keyalg RSA -keysize 4096 -validity 10000
```

重新生成游戏词表（需要 `kotlinc`）：

```bash
kotlinc -script tools/build_glossary.kts
```

调试日志：

```bash
adb logcat -s TapTL
```

## 参与贡献

欢迎提交问题反馈、词表修正和小的 Pull Request。请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)（英文）。

## 许可证

[MIT](LICENSE) © 2026 Nevil（[dracu-lah](https://github.com/dracu-lah)）。

翻译由谷歌翻译（ML Kit，手机端）提供。文字识别由 ML Kit 提供。Valorant 是 Riot Games 的商标。TapTL 与 Riot Games 和腾讯无关，也不是由它们制作。
