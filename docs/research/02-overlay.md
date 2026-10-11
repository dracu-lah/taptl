# 02 Overlay research (2026-10-11)

## Findings
- Touches pass through a `FLAG_NOT_TOUCHABLE` `TYPE_APPLICATION_OVERLAY` window when it is the only overlay window from our UID in the touch path and its `LayoutParams.alpha` is **≤** the maximum obscuring opacity (0.8). Only window alpha counts, not pixel alpha. Several overlay windows combine as `1-(1-a1)(1-a2)…`, so use one. Blocked touches log "Untrusted touch due to occlusion by <pkg>". — https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE , https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events
- `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` (30+) extends under cutouts on all edges in portrait and landscape; on 29 use `SHORT_EDGES`. `setFitInsetsTypes(0)` (30+). `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS` for screen coordinates. — https://developer.android.com/reference/android/view/WindowManager.LayoutParams#LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
- No public API (14–17) excludes our own window from MediaProjection. `FLAG_SECURE` and Android 15's `View.setContentSensitivity(SENSITIVE)` make the window *blocked* (black) in capture, not transparent, which would ruin OCR. — https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE , https://developer.android.com/reference/android/view/View#setContentSensitivity(int)

## Changes to the plan
- Window alpha 0.8 with fully opaque pixels inside (allowed: only window alpha counts).
- The view also subtracts its own on-screen position (`getLocationOnScreen`) before drawing, so boxes stay in screen coordinates even if the system shifts the window.
- **Hide-for-capture becomes "mask-for-OCR"** (see 04): instead of hiding the overlay for 1–2 frames, boxes that are still valid are painted out of the OCR crop, and boxes whose own area changed are removed at once. Same goal (never read our own boxes, nor the 20 % ghost of the Chinese under them), no flicker and no waiting for frames.

## Questions for the user
- none

## Verified on the phone (Poco M2 Pro, Android 16, TapTap + the reference screenshot shown full screen)
- Overlay window frame = `[0,0][1080,2400]`: capture → screen mapping is exactly ×2, no offset.
- Boxes line up with the settings rows of `01_settings_basic.png` (landscape 2400×1080).
- Tap on "Rank list" under a box switched the TapTap tab; no "Untrusted touch" in logcat.
- Rotation landscape → portrait while on: capture resized to 540×1200, boxes cleared and re-read, no crash.
- A service context has no display: the overlay needs `createDisplayContext(display).createWindowContext(...)` (it crashed without it).
- The status bar (with the always-ticking capture chip) is excluded using the overlay's status-bar inset; boxes in that strip are dropped when it shows or hides.
- Android's Settings app force-hides all non-system overlays (`mIsForceHiddenNonSystemOverlayWindow=true`). Nothing to fix; games don't do this.
- On Android 11+ `ACTION_MANAGE_OVERLAY_PERMISSION` always opens the full app list, so the step text says "find TapTL in the list".
