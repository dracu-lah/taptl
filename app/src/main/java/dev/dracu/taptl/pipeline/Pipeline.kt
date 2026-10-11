package dev.dracu.taptl.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.Image
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import dev.dracu.taptl.Lang
import dev.dracu.taptl.TAG
import dev.dracu.taptl.overlay.Box
import dev.dracu.taptl.overlay.OverlayController
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Decides when to read the screen. Runs on the capture thread ([capture]); OCR and translation
 * run on one low-priority worker, one job at a time (latest frame wins).
 *
 * Per frame: diff a tiny luma grid → boxes whose own area changed disappear → changed tiles wait
 * until they have been still for [SETTLE_MS] → OCR just that area (our boxes masked out) →
 * translate → draw. If most of the screen keeps moving for [MOTION_MS] (gameplay), the overlay is
 * hidden and nothing is read until it calms down.
 *
 * Game menus often play video behind the text, so some areas never go still. An area that has
 * kept changing for [ANIM_MS] counts as animated: its boxes stay up (only a big change such as a
 * page switch or scroll removes them) and it is re-read on a slow timer that backs off while the
 * reads find nothing new.
 */
class Pipeline(
    context: Context,
    private val capture: Handler,
    private val overlay: OverlayController,
    private val onBlackScreen: () -> Unit,
) {
    private val app = context.applicationContext
    private val measurePaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG) // worker only
    private val minTextPx = 9f * app.resources.displayMetrics.scaledDensity
    private val workerThread = HandlerThread("TapTL-ocr", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val worker = Handler(workerThread.looper)
    private var ocr: OcrEngine? = null
    private var translator: Translator? = null

    // ---- capture-thread state ----
    private val differ = FrameDiffer()
    private var frameW = 0
    private var frameH = 0
    private var scale = 2f
    private var generation = 0 // bumped on rotation; results from an older one are dropped

    private var pending = BooleanArray(0)
    private var readyTiles = BooleanArray(0)
    private var emptyStreak = IntArray(0) // reads in a row that found no Chinese (timers, art)
    private var lastChange = LongArray(0)
    private var heat = FloatArray(0)      // recent changes, halving every [HEAT_HALF_MS]
    private var heatAt = LongArray(0)
    private var activeSince = LongArray(0) // when the tile's current busy spell began
    private var lastRead = LongArray(0)
    private var lookAt = LongArray(0)      // when a still area gets its second look (0 = none due)
    private var looked = BooleanArray(0)   // second look done since the last change
    private var reread = BooleanArray(0)   // read again for a label cut by a crop edge

    private val boxes = ArrayList<Box>()
    private val ignores = ArrayList<Ignore>()
    private val drift = HashMap<Box, Long>() // when each box's background started looking different
    private val removed = ArrayList<Ignore>() // boxes just taken down; frames may still show them

    private var frame: Image? = null // latest screen content, kept open instead of copied

    private var busy = false
    private var released = false

    private var motionStart = 0L
    private var lastBigChange = 0L
    private var paused = false
    private var blackSince = 0L

    // Stats for the periodic log line.
    private var statFrames = 0
    private var statDiffNs = 0L
    private var statOcrCalls = 0
    private var statBigFrames = 0
    private var statSince = 0L

    private var statusBarPx = 0 // screen px; the status bar is never the app's text
    private val statusBarRect = Rect()
    private val tmpRect = Rect()
    private val tick = Runnable { onTick() }

    private class Ignore(val rect: Rect, val until: Long, val bg: Int = 0)
    private class Job(val gen: Int, val tiles: IntArray, val region: Rect, val capturedAt: Long, val settledAt: Long) {
        val cut = ArrayList<Rect>() // lines the crop edge cut through, in frame px
    }

    init {
        worker.post {
            val lang = Lang.get(app)
            ocr = OcrEngine(lang)
            translator = Translator(app, lang)
        }
    }

    // ---- frames ----

    /** Returns true when the pipeline keeps [image] open as the latest frame. */
    fun onFrame(image: Image, capturedWidth: Int): Boolean {
        if (released) return false
        val now = SystemClock.uptimeMillis()
        if (image.width != frameW || image.height != frameH) resetGeometry(image.width, image.height, capturedWidth, now)
        val plane = image.planes[0]
        val buf = plane.buffer

        val t0 = System.nanoTime()
        differ.update(buf, plane.rowStride, now)
        ignores.removeAll { it.until < now }
        for (ig in ignores) differ.clear(ig.rect)
        if (statusBarPx > 0) differ.clear(statusBarRect.apply { set(0, 0, frameW, (statusBarPx / scale).toInt() + 1) })
        val changedTiles = differ.computeTiles()
        statDiffNs += System.nanoTime() - t0
        statFrames++
        logStats(now)

        checkBlack(now)

        // Keep the newest frame that differs; an identical one adds nothing.
        val keep = changedTiles > 0 || frame == null
        if (keep) {
            frame?.close()
            frame = image
        }
        if (changedTiles == 0) return keep

        // Motion: most of the screen changing frame after frame means gameplay or a big animation.
        val big = changedTiles >= differ.tileCount * BIG_CHANGE_FRACTION
        if (big) {
            statBigFrames++
            if (motionStart == 0L) motionStart = now
            lastBigChange = now
        } else if (motionStart != 0L && now - lastBigChange > MOTION_GAP_MS) {
            motionStart = 0L
        }
        if (!paused && motionStart != 0L && now - motionStart > MOTION_MS) {
            paused = true
            Log.i(TAG, "motion: overlay hidden")
            setBoxes(emptyList(), fullScreen())
        }

        // Mark changed tiles; they wait until still.
        val tc = differ.tileChanged
        for (t in tc.indices) if (tc[t]) {
            val h = heatNow(t, now)
            if (h < HEAT_QUIET) activeSince[t] = now
            heat[t] = h + 1f
            heatAt[t] = now
            pending[t] = true
            lastChange[t] = now
            lookAt[t] = 0L
            looked[t] = false
            reread[t] = false
        }

        if (!paused) {
            // A box whose own area changed (scrolling, new page) is wrong now: hide it at once.
            // Over an animated background only pixels that had been still count; the art moves
            // all the time, the menu doesn't.
            // A panel sliding over video is different: the ring around the box changes, then holds still.
            // A see-through panel over video never holds still; its colour gives it away instead.
            val stale = boxes.filter {
                // When most of the screen changed at once (a new page), no area gets the video allowance.
                differ.anyStale(it.rect, BOX_MARGIN_SAMPLES) { t -> !big && animated(t, now) } ||
                    differ.covered(it.rect, SWEEP_RING_SAMPLES, it.shownAt, now) ||
                    backgroundGone(image, it, now)
            }
            drift.keys.retainAll(boxes.toSet())
            if (stale.isNotEmpty()) {
                val keep = boxes.filterNot { it in stale }
                setBoxes(keep, stale.map { it.rect })
                for (b in stale) removed += Ignore(b.rect, now + REMOVED_MASK_MS, b.bg)
                Log.v(TAG, "stale: ${stale.joinToString { it.text }}")
                for (b in stale) forTiles(b.rect) { emptyStreak[it] = 0 }
            }
        }
        schedule(now)
        return keep
    }

    private fun resetGeometry(w: Int, h: Int, capturedWidth: Int, now: Long) {
        generation++
        frameW = w
        frameH = h
        scale = capturedWidth.toFloat() / w
        differ.reset(w, h)
        pending = BooleanArray(differ.tileCount) { true }
        readyTiles = BooleanArray(differ.tileCount)
        emptyStreak = IntArray(differ.tileCount)
        lastChange = LongArray(differ.tileCount) { now }
        heat = FloatArray(differ.tileCount)
        heatAt = LongArray(differ.tileCount)
        activeSince = LongArray(differ.tileCount) { now }
        lastRead = LongArray(differ.tileCount)
        lookAt = LongArray(differ.tileCount)
        looked = BooleanArray(differ.tileCount)
        reread = BooleanArray(differ.tileCount)
        frame?.close()
        frame = null
        ignores.clear()
        removed.clear()
        drift.clear()
        setBoxes(emptyList(), null)
        Log.i(TAG, "frame ${w}x$h, scale $scale, ${differ.tileCols}x${differ.tileRows} tiles")
    }

    /** Copies just [region] of the kept frame into a new bitmap. */
    private fun cropFrame(image: Image, region: Rect): Bitmap? = try {
        val plane = image.planes[0]
        val src = plane.buffer.duplicate()
        val w = region.width()
        val out = ByteBuffer.allocateDirect(w * region.height() * 4)
        for (y in region.top until region.bottom) {
            val off = y * plane.rowStride + region.left * 4
            src.limit(src.capacity())
            src.position(off)
            src.limit(off + w * 4)
            out.put(src)
        }
        out.flip()
        Bitmap.createBitmap(w, region.height(), Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(out) }
    } catch (e: IllegalStateException) {
        null // the image was closed under us (reader swapped on rotation)
    }

    private fun checkBlack(now: Long) {
        if (differ.maxLuma > BLACK_LUMA) { blackSince = 0L; return }
        if (blackSince == 0L) blackSince = now
        if (now - blackSince > BLACK_MS) {
            blackSince = Long.MAX_VALUE / 2 // report once
            Log.w(TAG, "screen has been black for ${BLACK_MS}ms: the app may block capture")
            onBlackScreen()
        }
    }

    // ---- scheduling ----

    private fun onTick() {
        if (released) return
        val now = SystemClock.uptimeMillis()
        if (paused && now - lastBigChange > CALM_MS) {
            paused = false
            motionStart = 0L
            Log.i(TAG, "motion over: reading the screen again")
            pending.fill(true)
            lastChange.fill(lastBigChange)
        }
        schedule(now)
    }

    private fun schedule(now: Long) {
        if (released) return
        if (paused) {
            // Don't push the check back on every frame; one pending check is enough.
            if (!capture.hasCallbacks(tick)) capture.postDelayed(tick, CALM_MS)
            return
        }
        capture.removeCallbacks(tick)
        if (busy) return

        var nextReady = Long.MAX_VALUE
        val ready = readyTiles
        ready.fill(false)
        var anyReady = false
        var animDue = false
        for (t in pending.indices) {
            val look = lookAt[t]
            if (!pending[t] && look == 0L) continue
            val at = if (pending[t]) readyAt(t, now) else look
            if (at <= now) { ready[t] = true; anyReady = true; if (animated(t, now)) animDue = true }
            else if (at < nextReady) nextReady = at
        }
        // One animated area is due: read every animated area due soon in the same pass, so a
        // moving background costs one read every few seconds instead of a stream of small ones.
        if (animDue) for (t in pending.indices) {
            if (pending[t] && !ready[t] && animated(t, now) && readyAt(t, now) <= now + ANIM_BATCH_MS) ready[t] = true
        }
        if (anyReady && frame != null) {
            startJob(if (animDue) ready.indices.filter { ready[it] }.toIntArray() else largestComponent(ready), now)
        } else if (nextReady != Long.MAX_VALUE) {
            capture.postAtTime(tick, nextReady)
        }
    }

    /**
     * A still area is read [SETTLE_MS] after its last change. An animated one (video, particles,
     * a ticking timer) is read on a timer instead, waiting longer each time a read adds nothing:
     * 1, 2, 4, 8, 10 s.
     */
    private fun readyAt(t: Int, now: Long) = if (animated(t, now)) {
        maxOf(activeSince[t] + ANIM_MS, lastRead[t] + minOf(ANIM_READ_MS shl emptyStreak[t], MAX_WAIT_MS))
    } else {
        lastChange[t] + SETTLE_MS
    }

    /** Has [t] kept changing, with only short breaks, for [ANIM_MS]? (video or particle art) */
    private fun animated(t: Int, now: Long) = heatNow(t, now) >= HEAT_QUIET && now - activeSince[t] >= ANIM_MS

    private fun heatNow(t: Int, now: Long): Float {
        val dt = now - heatAt[t]
        return if (dt > HEAT_HALF_MS * 8) 0f else heat[t] * Math.pow(0.5, dt / HEAT_HALF_MS.toDouble()).toFloat()
    }

    private inline fun forTiles(r: Rect, block: (Int) -> Unit) {
        val size = FrameDiffer.TILE * FrameDiffer.STEP
        val x0 = (r.left / size).coerceIn(0, differ.tileCols - 1)
        val x1 = (r.right / size).coerceIn(0, differ.tileCols - 1)
        val y0 = (r.top / size).coerceIn(0, differ.tileRows - 1)
        val y1 = (r.bottom / size).coerceIn(0, differ.tileRows - 1)
        for (y in y0..y1) for (x in x0..x1) block(y * differ.tileCols + x)
    }

    /** Biggest group of touching ready tiles (8-neighbour). */
    private fun largestComponent(ready: BooleanArray): IntArray {
        val cols = differ.tileCols
        val rows = differ.tileRows
        val seen = BooleanArray(ready.size)
        var best = IntArray(0)
        val stack = ArrayDeque<Int>()
        for (start in ready.indices) {
            if (!ready[start] || seen[start]) continue
            val comp = ArrayList<Int>()
            stack.addLast(start); seen[start] = true
            while (stack.isNotEmpty()) {
                val t = stack.removeLast()
                comp += t
                val tx = t % cols
                val ty = t / cols
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = tx + dx
                    val ny = ty + dy
                    if (nx !in 0 until cols || ny !in 0 until rows) continue
                    val n = ny * cols + nx
                    if (ready[n] && !seen[n]) { seen[n] = true; stack.addLast(n) }
                }
            }
            if (comp.size > best.size) best = comp.toIntArray()
        }
        return best
    }

    private fun startJob(tiles: IntArray, now: Long) {
        val region = Rect()
        var settledAt = 0L
        for (t in tiles) {
            region.union(differ.tileRect(t, tmpRect, frameW, frameH))
            pending[t] = false
            lastRead[t] = now
            // Pages fade in slower than the change threshold; look once more after the first read.
            if (lookAt[t] == 0L && !looked[t] && !animated(t, now)) { lookAt[t] = now + SECOND_LOOK_MS; looked[t] = true } else lookAt[t] = 0L
            settledAt = maxOf(settledAt, lastChange[t])
        }
        region.inset(-REGION_PAD_X, -REGION_PAD) // labels run sideways
        if (!region.intersect(0, 0, frameW, frameH)) return

        // Crop now (the kept frame is replaced as the screen changes) and paint over our own boxes
        // so OCR never reads our English or the faint Chinese showing through it.
        val crop = cropFrame(frame ?: return, region) ?: return
        val canvas = Canvas(crop)
        val paint = Paint()
        removed.removeAll { it.until < now }
        fun mask(r: Rect, bg: Int) {
            if (!Rect.intersects(r, region)) return
            paint.color = bg
            canvas.drawRect(
                (r.left - region.left).toFloat(), (r.top - region.top).toFloat(),
                (r.right - region.left).toFloat(), (r.bottom - region.top).toFloat(), paint,
            )
        }
        for (b in boxes) mask(b.rect, b.bg)
        for (g in removed) mask(g.rect, g.bg)

        busy = true
        statOcrCalls++
        val job = Job(generation, tiles, region, now, settledAt)
        worker.post { runJob(job, crop) }
    }

    // ---- worker ----

    private fun runJob(job: Job, crop: Bitmap) {
        var out: List<Box> = emptyList()
        try {
            out = readAndTranslate(job, crop)
        } catch (e: Exception) {
            Log.e(TAG, "job failed", e)
        } finally {
            crop.recycle()
            capture.post { applyResult(job, out) }
        }
    }

    private fun readAndTranslate(job: Job, crop: Bitmap): List<Box> {
        val ocr = ocr ?: return emptyList()
        val translator = translator ?: return emptyList()
        val t0 = SystemClock.uptimeMillis()
        val input = if (OCR_SCALE != 1f) {
            Bitmap.createScaledBitmap(crop, (crop.width * OCR_SCALE).toInt(), (crop.height * OCR_SCALE).toInt(), true)
        } else crop
        val lines = try {
            ocr.read(input)
        } catch (e: Exception) {
            Log.w(TAG, "ocr failed", e)
            emptyList()
        }
        val t1 = SystemClock.uptimeMillis()
        val g0 = translator.glossaryHits
        val c0 = translator.cacheHits
        val e0 = translator.engineCalls
        val out = ArrayList<Box>(lines.size)
        for (line in lines) {
            // A line touching a side of the crop (not the screen's) is only part of a label: read it
            // again around its own position rather than show half a translation.
            val b = line.box
            if ((b.left <= EDGE_PX && job.region.left > 0) ||
                (b.right >= crop.width * OCR_SCALE - EDGE_PX && job.region.right < frameW)) {
                job.cut += Rect(b.left + job.region.left, b.top + job.region.top, b.right + job.region.left, b.bottom + job.region.top)
                continue
            }
            val en = translator.translate(line.text) ?: continue
            val r = Rect(
                (line.box.left / OCR_SCALE).toInt() - 4, (line.box.top / OCR_SCALE).toInt() - 2,
                (line.box.right / OCR_SCALE).toInt() + 4, (line.box.bottom / OCR_SCALE).toInt() + 2,
            )
            r.intersect(0, 0, crop.width, crop.height)
            val bg = sampleBackground(crop, r)
            r.offset(job.region.left, job.region.top)
            widenToFit(r, en)
            out += Box(r, en, bg, if (luma(bg) > 150) Color.BLACK else Color.WHITE, SystemClock.uptimeMillis())
        }
        val t2 = SystemClock.uptimeMillis()
        if (input !== crop) input.recycle()
        Log.d(
            TAG,
            "ocr ${job.region.toShortString()} lines=${lines.size} boxes=${out.size} " +
                "ocr=${t1 - t0}ms translate=${t2 - t1}ms " +
                "(glossary ${translator.glossaryHits - g0}, cache ${translator.cacheHits - c0}, " +
                "engine ${translator.engineCalls - e0}) ${lines.joinToString("|") { it.text }.take(80)}",
        )
        return out
    }

    /**
     * English is often longer than the Chinese (发现 → "Discover"). Widen the box around its centre
     * so the text fits at a readable size, up to [MAX_WIDEN] × its width. Done here, not in the
     * view, so masking and change detection use the box as drawn.
     */
    private fun widenToFit(r: Rect, en: String) {
        measurePaint.textSize = maxOf(r.height() * scale * 0.6f, minTextPx) / scale
        val need = (measurePaint.measureText(en) + r.height() * 0.3f).toInt()
        val w = minOf(need, (r.width() * MAX_WIDEN).toInt())
        if (w <= r.width()) return
        val grow = w - r.width()
        r.left -= grow / 2
        r.right += grow - grow / 2
        if (r.left < 0) r.offset(-r.left, 0)
        if (r.right > frameW) r.offset(frameW - r.right, 0)
    }

    /** Median colour just outside the text box ([r] in [bmp] pixels): the real background. */
    private fun sampleBackground(bmp: Bitmap, r: Rect): Int {
        val pts = ArrayList<Int>(8)
        fun at(x: Int, y: Int) {
            if (x in 0 until bmp.width && y in 0 until bmp.height) pts += bmp.getPixel(x, y)
        }
        val d = 6
        for (f in floatArrayOf(0.15f, 0.5f, 0.85f)) {
            val x = r.left + (r.width() * f).toInt()
            at(x, r.top - d)
            at(x, r.bottom + d)
        }
        at(r.left - d, r.centerY())
        at(r.right + d, r.centerY())
        if (pts.isEmpty()) return Color.DKGRAY
        fun med(shift: Int) = pts.map { (it shr shift) and 0xFF }.sorted()[pts.size / 2]
        return Color.rgb(med(16), med(8), med(0))
    }

    /** True once the colour around [b] has been far from the colour it was drawn on for [DRIFT_MS]. */
    private fun backgroundGone(image: Image, b: Box, now: Long): Boolean {
        val plane = image.planes[0]
        val buf = plane.buffer
        val stride = plane.rowStride
        val r = b.rect
        var n = 0
        var far = 0
        fun at(x: Int, y: Int) {
            if (x !in 0 until frameW || y !in 0 until frameH) return
            val i = y * stride + x * 4
            val d = abs((buf.get(i).toInt() and 0xFF) - Color.red(b.bg)) +
                abs((buf.get(i + 1).toInt() and 0xFF) - Color.green(b.bg)) +
                abs((buf.get(i + 2).toInt() and 0xFF) - Color.blue(b.bg))
            n++
            if (d > DRIFT_DIST) far++
        }
        val d = 6
        for (f in DRIFT_POINTS) {
            val x = r.left + (r.width() * f).toInt()
            at(x, r.top - d)
            at(x, r.bottom + d)
        }
        at(r.left - d, r.centerY())
        at(r.right + d, r.centerY())
        if (n == 0 || far * 4 < n * 3) { drift.remove(b); return false }
        val since = drift.getOrPut(b) { now }
        return now - since >= DRIFT_MS
    }

    private fun luma(c: Int) = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8

    // ---- results ----

    private fun applyResult(job: Job, found: List<Box>) {
        busy = false
        val now = SystemClock.uptimeMillis()
        if (released || paused || job.gen != generation) { schedule(now); return }
        // If the area moved again while we were reading it, drop the result and read it again.
        // Animated areas always move; their result stands.
        if (job.tiles.any { lastChange[it] > job.capturedAt && !animated(it, now) }) {
            for (t in job.tiles) pending[t] = true
            schedule(now)
            return
        }
        for (r in job.cut) forTiles(r) { t ->
            if (t !in job.tiles && !reread[t]) { reread[t] = true; pending[t] = true }
        }
        val barBottom = (statusBarPx / scale).toInt()
        val added = found.filter { nb -> nb.rect.top >= barBottom && boxes.none { Rect.intersects(it.rect, nb.rect) } }
        // Back off tiles where this read added nothing (no Chinese, or only text already shown).
        val hit = BooleanArray(job.tiles.size)
        for (b in added) for ((k, t) in job.tiles.withIndex()) {
            if (Rect.intersects(b.rect, differ.tileRect(t, tmpRect, frameW, frameH))) hit[k] = true
        }
        for ((k, t) in job.tiles.withIndex()) {
            emptyStreak[t] = if (hit[k]) 0 else minOf(emptyStreak[t] + 1, MAX_BACKOFF)
        }
        if (added.isNotEmpty()) {
            setBoxes(boxes + added, added.map { it.rect })
            Log.d(TAG, "render ${added.size} boxes, last change→render ${now - job.settledAt}ms")
        }
        schedule(now)
    }

    /** Publishes boxes and ignores the screen change our own drawing is about to cause. */
    private fun setBoxes(newBoxes: List<Box>, changed: List<Rect>?) {
        boxes.clear()
        boxes += newBoxes
        val until = SystemClock.uptimeMillis() + IGNORE_MS
        changed?.forEach { r ->
            // The view may grow a box to fit two lines; cover that too.
            val grow = r.height() / 2 + 4
            ignores += Ignore(Rect(r.left - 4, r.top - grow, r.right + 4, r.bottom + grow), until)
        }
        overlay.setBoxes(boxes.toList(), scale)
    }

    private fun fullScreen() = listOf(Rect(0, 0, frameW, frameH))

    private fun logStats(now: Long) {
        if (statSince == 0L) statSince = now
        if (now - statSince < STATS_MS) return
        Log.d(
            TAG,
            "stats ${now - statSince}ms: frames=$statFrames diff avg=${"%.2f".format(statDiffNs / 1e6 / statFrames)}ms " +
                "ocr calls=$statOcrCalls big frames=$statBigFrames boxes=${boxes.size} paused=$paused",
        )
        statSince = now
        statFrames = 0
        statDiffNs = 0
        statOcrCalls = 0
        statBigFrames = 0
    }

    fun setStatusBarHeight(px: Int) = capture.post {
        if (px == statusBarPx) return@post
        Log.i(TAG, "status bar ${px}px (ignored)")
        // The strip changed hands (bar shown or hidden): drop what was read there and redo it.
        val strip = ((maxOf(px, statusBarPx) / scale).toInt() + 1)
        statusBarPx = px
        if (frameW == 0) return@post
        val keep = boxes.filter { it.rect.top >= strip }
        if (keep.size != boxes.size) setBoxes(keep, listOf(Rect(0, 0, frameW, strip)))
        val now = SystemClock.uptimeMillis()
        for (t in pending.indices) {
            if (differ.tileRect(t, tmpRect, frameW, frameH).top < strip) { pending[t] = true; lastChange[t] = now }
        }
        schedule(now)
    }

    fun release() {
        capture.post {
            released = true
            capture.removeCallbacks(tick)
            frame?.close()
            frame = null
        }
        worker.post {
            ocr?.close()
            translator?.close()
            workerThread.quitSafely()
        }
    }

    private companion object {
        const val SETTLE_MS = 250L          // an area must be still this long before OCR
        const val MAX_BACKOFF = 5
        const val HEAT_HALF_MS = 2000L      // video moves in bursts; remember them a while
        const val HEAT_QUIET = 0.4f         // below this a tile counts as still again
        const val ANIM_MS = 1500L           // busy this long → animated background
        const val ANIM_READ_MS = 1000L      // animated areas: re-read after 1, 2, 4, 8 s…
        const val MAX_WAIT_MS = 10_000L
        const val ANIM_BATCH_MS = 3000L
        const val MOTION_MS = 1500L         // big changes this long → gameplay → hide
        const val MOTION_GAP_MS = 300L      // a pause this long ends a motion run
        const val CALM_MS = 1000L           // no big change this long → back to reading
        const val BIG_CHANGE_FRACTION = 0.4f
        const val REMOVED_MASK_MS = 500L
        const val SECOND_LOOK_MS = 800L
        const val DRIFT_DIST = 90                 // |dR|+|dG|+|dB| that counts as a different colour
        const val DRIFT_MS = 400L
        val DRIFT_POINTS = floatArrayOf(0.15f, 0.5f, 0.85f)
        const val IGNORE_MS = 300L          // our own draw shows up in capture within this
        const val BOX_MARGIN_SAMPLES = 1
        const val SWEEP_RING_SAMPLES = 2
        const val REGION_PAD = 48           // frame px above and below the OCR area
        const val REGION_PAD_X = 256        // and to each side
        const val EDGE_PX = 3
        const val MAX_WIDEN = 2.5f
        const val OCR_SCALE = 1f            // full-res menu text is ~30 px; ML Kit wants ≥ 16–24 px
        const val BLACK_LUMA = 12
        const val BLACK_MS = 10_000L
        const val STATS_MS = 5_000L
    }
}
