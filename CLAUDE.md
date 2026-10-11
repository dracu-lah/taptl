# TapTL: instructions for the building AI

You are building **TapTL**, a native Kotlin Android app that shows a live English overlay over Chinese text in games (Valorant Mobile CN first). It is toggled from a Quick Settings tile. Nothing has been built yet. This folder holds the plan and research inputs.

## Read first, in this order
1. `PLAN.md`: the **MVP** plan: scope (in/out), decisions, architecture, pipeline, scroll behaviour, 14 edge cases, device facts and a "Later" list. **Build only what is in scope.** Don't add "Later" items unless the user asks.
2. `docs/MILESTONES.md`: 5 MVP milestones, each with "done when" checks and adb commands.
3. `docs/RESEARCH_FIRST.md`: the **mandatory** web searches to run before coding each milestone.
4. `docs/DEVICE.md`: the test phone and the game, plus adb commands.
5. `docs/GLOSSARY_SEED.md`: strings taken from the real game, plus the glossary design.
6. `docs/reference/*.png`: real screenshots of the game's settings screens (2400×1080 landscape).

## Workflow (every milestone)
1. **Research:** run the web searches listed in `docs/RESEARCH_FIRST.md` for this milestone. Do not trust any version number, API name or behaviour in the plan without checking it. The plan was written on 2026-10-11 and things move.
2. **Think:** write `docs/research/<NN>-<milestone>.md` with findings, sources (URLs) and how they change the milestone. If a finding contradicts the plan, say so and pick the safer option. Ask the user if it's a product decision.
3. **Build:** implement only that milestone. Match the plan's file layout (`app/src/main/java/dev/dracu/taptl/...`).
4. **Verify on the phone** over adb (commands in `docs/DEVICE.md`) and against the milestone's acceptance criteria. Take screenshots with `adb exec-out screencap -p > file.png` and look at them.
5. **Report** to the user in short, plain words: what works, what doesn't, the numbers you measured, and what comes next. Then wait for the go-ahead before starting the next milestone.

## Hard rules
- **Stack:** Kotlin, Gradle Kotlin DSL + version catalog, Jetpack Compose for app screens, a plain Canvas `View` for the overlay. minSdk 29, targetSdk 36, compileSdk 37 (confirm in research).
- **Capture:** MediaProjection + VirtualDisplay + ImageReader only. **No AccessibilityService, no input injection, no reading game memory** (anti-cheat safety).
- **Overlay:** `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE`, window alpha ≤ 0.8 (Android 12+ untrusted-touch rule). Taps must reach the game.
- **Never draw over the game during gameplay.** The motion pause hides the overlay.
- **Efficiency is a feature:** work only on change, no per-frame allocations, latest-frame-wins, single low-priority worker, at most 2 ImageReader images, and watch the Android 17 memory limiter.
- **Keep it MVP:** the simplest code that meets the milestone's "done when". No speculative abstractions.
- **Never queue into a real match or play one yourself.** No unrated, deathmatch or ranked: the game has real players and the account can be penalised. The user plays while you record (`docs/DEVICE.md` → capture session). In menus, never buy anything and never change game settings (no toggles, sliders or "reset defaults"). Scrolling and switching tabs is fine.
- **Git:** `git init` is fine. Commit only when the user asks. **Never add a `Co-Authored-By` trailer** or any Claude/Anthropic attribution to commits.
- **Releases:** before any version bump, tag or `gh release create`, run `gh release list --limit 10` and `git tag --sort=-creatordate | head`. If the last release is under 7 days old, or there were 2+ releases in 7 days, stop and tell the user (back-to-back releases make installed apps prompt for updates too often). Proceed only if they confirm.
- **Images:** never compress, downscale or re-encode images or screenshots unless asked.
- **Screenshots/visual checks:** use the phone over adb. If a browser is ever needed, use local headless Chromium, not Google Chrome.
- **Ask the user** when a decision is genuinely theirs (UX, scope). Otherwise pick the option in `PLAN.md` and move on.

## User-facing writing
- Reports and UI copy: super short and plain. No jargon in user-facing text.
- README (M5): install steps, each permission and why, the anti-cheat caveat ("test on a secondary account first"), how to build.
