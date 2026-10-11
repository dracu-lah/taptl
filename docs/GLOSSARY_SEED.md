# Glossary seed and design

## Lookup order (MVP)
1. **Mobile settings glossary** (hand-made, below) → `assets/glossary/settings_zh_en.json`
2. **Official Valorant glossary** (generated from valorant-api.com, zh-CN ↔ en-US, joined by uuid) → `assets/glossary/valorant_zh_en.json`
3. Cache → ML Kit on-device

Matching: normalise (trim, full-width → half-width punctuation, strip trailing "!" / "?" icons that OCR picks up), try an **exact match** first, then **longest-match** replacement of known terms inside a longer line, and send any leftover CJK to the engine. Digits and Latin text pass through unchanged.

## Generator: `tools/build_glossary.kts`
- Fetch `https://valorant-api.com/v1/{agents?isPlayableCharacter=true, weapons, maps, competitivetiers, gamemodes, gear}` twice, with `language=zh-CN` and `language=en-US` (confirm the parameters in research).
- Join by `uuid`: agent `displayName`, each ability `displayName`, `role.displayName`, weapon `displayName` and `shopData.categoryText`, map `displayName`, tier `tierName`, game mode `displayName`, gear `displayName`.
- Output `app/src/main/assets/glossary/valorant_zh_en.json` as `[{"zh": "...", "en": "...", "kind": "agent|ability|weapon|map|rank|mode|gear"}]`.
- Mobile CN may use different Chinese names than PC CN. Spot-check against the screenshots and the user's captures. Mismatches go into a `valorant_mobile_overrides.json`.

## Mobile settings glossary (taken from real screens: docs/reference/*.png)
| zh | en |
|---|---|
| 设置 | Settings |
| 基础 | Basic |
| 界面布局 | HUD Layout |
| 音画设置 | Audio & Graphics |
| 英雄 | Agents |
| 灵敏度 | Sensitivity |
| 准星设置 | Crosshair |
| 其他 | Other |
| 操作 | Controls |
| 智能开火 | Auto Fire |
| 地图 | Map |
| 战斗信息 | Combat Info |
| 网络诊断 | Network Check |
| 还原默认 | Reset Defaults |
| 客服 | Support |
| 反馈 | Feedback |
| 退出登录 | Log Out |
| 操控模式 | Control Mode |
| 静步操作模式 | Walk Mode |
| 按钮静步 | Walk Button |
| 摇杆内圈静步 | Joystick Inner-Ring Walk |
| 独立摇杆静步 | Separate Walk Stick |
| 开镜模式 | ADS Mode |
| 下蹲模式 | Crouch Mode |
| 点击 | Tap |
| 长按 | Hold |
| 混合模式 | Hybrid |
| 进阶设置 | Advanced |
| 辅助瞄准 | Aim Assist |
| 开火时自动静步 | Auto-Walk When Firing |
| 狙击枪自动开镜 | Sniper Auto-Scope |
| 左手持枪 | Left-Handed |
| 快捷上绳 | Quick Rope |
| 自动切副武器 | Auto Switch to Sidearm |
| 开启 | On |
| 显示彩色枪口闪光 | Colored Muzzle Flash |
| 独立检视按钮 | Separate Inspect Button |
| 优先自动装备 | Auto-Equip Priority |
| 上一个武器 | Last Weapon |
| 最强武器 | Strongest Weapon |
| 避免近战武器 | Avoid Melee |
| 按钮转动视角 | Look With Buttons |
| 右开火按钮转动视角 | Right Fire Btn Turns Camera |
| 开镜按钮转动视角 | ADS Btn Turns Camera |
| 下蹲按钮转动视角 | Crouch Btn Turns Camera |
| 跳跃按钮转动视角 | Jump Btn Turns Camera |
| 换弹按钮转动视角 | Reload Btn Turns Camera |
| 智能开火按钮转动视角 | Auto-Fire Btn Turns Camera |
| 静步按钮转动视角 | Walk Btn Turns Camera |
| 检视按钮转动视角 | Inspect Btn Turns Camera |
| 滑屏设置 | Swipe Settings |
| 陀螺仪设置 | Gyro Settings |
| 大神方案 | Pro Presets |
| 总体设置 | General |
| 灵敏度方案 | Sensitivity Preset |
| 低 / 中 / 高 | Low / Med / High |
| 自定义方案 | Custom |
| 镜头转向模式 | Camera Turn Mode |
| 固定速度 | Fixed Speed |
| 速度加速 | Acceleration |
| 转向辅助 | Turn Assist |
| 滑屏灵敏度 | Swipe Sensitivity |
| 灵敏度一键调节 | One-Tap Sensitivity |
| 开火复用镜头灵敏度 | Use Camera Sens When Firing |
| 垂直灵敏度（Y轴灵敏度） | Vertical (Y-Axis) Sensitivity |

Short, single-character labels (低/中/高) are only translated when they're an exact match inside a settings row, never on their own elsewhere.

## Likely next strings (verify from captures before adding)
锁定 (Lock In), 选择英雄 (Select Agent), 决斗者/先锋/控场者/哨卫 (Duelist/Initiator/Controller/Sentinel: verify the Mobile CN wording), 匹配/排位/死斗 (Unrated/Competitive/Deathmatch: verify), 商店 (Store), 战令 (Battle Pass: verify), 回合胜利/失败 (Round Won/Lost), 购买 (Buy), 陀螺仪 (Gyroscope), 开火键 (Fire Button), 自定义布局 (Custom Layout).

## Later (not MVP)
Chat slang (yyds, 666, 白给, 带飞…) and quick-chat lines, once the chat ticker is built.
