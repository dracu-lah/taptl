# 04 Efficiency + scrolling research (2026-10-11)

## Findings
- `acquireLatestImage()` needs `maxImages ≥ 2`; holding `maxImages` images throws. Close every Image right after use, never keep one across frames. — https://developer.android.com/reference/android/media/ImageReader#acquireLatestImage()
- RGBA_8888: `pixelStride` 4, `rowStride` may exceed width×4; `Bitmap.copyPixelsFromBuffer` needs packed rows. — https://developer.android.com/reference/android/media/Image.Plane#getRowStride()
- Android 17 memory limiter: cgroup `memory.high` per app, tiered by RAM (8 GB phone: 5120 MB visible / 3072 MB background). Over the limit = reclaim and throttling, kills only in extreme cases (`REASON_OTHER`, "MemoryLimiter:AnonSwap"). `am memory-limiter status|ignore|manual`. — https://developer.android.com/about/versions/17/behavior-changes-all , https://source.android.com/docs/core/perf/memory-limiter

## Design (as built)
- Capture thread, per frame: sample luma every 4th px of the half-res frame (1/8 of the screen) → frame-to-frame diff (threshold 24) → changed 128 px tiles. ~10–40k reads, no allocation.
- Changed frame → copy into one reused bitmap (needed because a still screen sends no more frames).
- Boxes whose own area changed vanish at once (scroll hide). Changed tiles wait 250 ms still, then the biggest group of ready tiles is cropped, our boxes painted out, OCR'd on one low-priority worker. If the area changed during OCR, the result is dropped and redone.
- Our own drawing shows up in capture; those rects are ignored for 300 ms after each overlay change.
- Motion pause: ≥ 40 % of tiles changing for > 1.5 s → hide all, no OCR; 1 s calm → re-read everything.
- Number-only lines have no Chinese → never boxed. Black frames for 10 s → notification + stop.

## Changes to the plan
- Hide-for-capture → mask-for-OCR (see 02).
- The image is never held across frames, so a still screen is handled with the copied bitmap plus a timer, not by waiting for another frame.

## Questions for the user
- none

## Verified on the phone (Poco M2 Pro, Android 16)
- Still screen (TapTap Rank list), 60 s: **0 OCR calls**. ~1 frame/s still arrives (the status-bar capture timer).
- Diff: ~2.1–3.5 ms per frame (bulk row copy; was 32 ms with per-byte `get()`).
- CPU on a still screen 0–3.5 %; PSS 172 MB while translating (ML Kit models), 84 MB after 10 cycles.
- Scroll in TapTap: boxes hide at once, new rows rendered 0.45–0.7 s after the last change when cached, 1–2 s with many new lines.
- Areas that change but have no Chinese (timers, search-box carousels) back off 0.25 → 8 s; a big page change resets the back-off.
- Motion pause: 4 s of non-stop scrolling → "overlay hidden" after 1.5 s, 0 OCR while paused, re-read ~1 s after it stopped.
- 10 tile on/off cycles: 10× OFF→REQUESTING→ON→OFF, same pid, empty crash buffer, 0 leaked virtual displays or overlay windows.
- Not tested yet: black-frame stop (needs an app with FLAG_SECURE), the game itself and a real deathmatch (Poco F5).
