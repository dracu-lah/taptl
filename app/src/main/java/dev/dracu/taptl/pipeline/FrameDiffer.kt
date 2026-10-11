package dev.dracu.taptl.pipeline

import android.graphics.Rect
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * A tiny luma grid (every [STEP]th pixel of the frame, i.e. 1/8 of the screen each way),
 * compared frame to frame. Gives changed samples, changed tiles and the frame's brightest
 * sample. All arrays are reused; nothing is allocated per frame.
 */
class FrameDiffer {

    var cols = 0; private set
    var rows = 0; private set
    var tileCols = 0; private set
    var tileRows = 0; private set
    val tileCount get() = tileCols * tileRows

    /** Brightest sample in the last frame (0–255); used for black-frame detection. */
    var maxLuma = 0; private set

    private var luma = IntArray(0)
    private var changed = BooleanArray(0)
    private var fresh = BooleanArray(0)     // changed after being still for [STILL_MS]
    private var changedAt = LongArray(0)
    var tileChanged = BooleanArray(0); private set
    private var hasBaseline = false
    private var rowBytes = ByteArray(0)

    fun reset(frameWidth: Int, frameHeight: Int) {
        cols = frameWidth / STEP
        rows = frameHeight / STEP
        tileCols = (cols + TILE - 1) / TILE
        tileRows = (rows + TILE - 1) / TILE
        luma = IntArray(cols * rows)
        changed = BooleanArray(cols * rows)
        fresh = BooleanArray(cols * rows)
        changedAt = LongArray(cols * rows) { Long.MIN_VALUE / 2 }
        tileChanged = BooleanArray(tileCols * tileRows)
        rowBytes = ByteArray(frameWidth * 4)
        hasBaseline = false
    }

    /** Samples an RGBA_8888 plane. Returns the number of changed samples (0 on the first frame). */
    fun update(buf: ByteBuffer, rowStride: Int, now: Long): Int {
        var count = 0
        var max = 0
        val first = !hasBaseline
        val row = rowBytes
        val len = minOf(row.size, cols * STEP * 4)
        for (y in 0 until rows) {
            // One bulk copy per sampled row is far cheaper than a get() per byte.
            buf.position((y * STEP + STEP / 2) * rowStride)
            buf.get(row, 0, len)
            val i0 = y * cols
            for (x in 0 until cols) {
                val p = (x * STEP + STEP / 2) * 4
                val r = row[p].toInt() and 0xFF
                val g = row[p + 1].toInt() and 0xFF
                val b = row[p + 2].toInt() and 0xFF
                val l = (r * 77 + g * 150 + b * 29) shr 8
                if (l > max) max = l
                val i = i0 + x
                val c = !first && abs(l - luma[i]) > THRESHOLD
                changed[i] = c
                fresh[i] = c && now - changedAt[i] > STILL_MS
                if (c) { count++; changedAt[i] = now }
                luma[i] = l
            }
        }
        buf.rewind()
        hasBaseline = true
        maxLuma = max
        return count
    }

    /** Forget changes inside [r] (frame pixels): our own overlay drew there. Returns how many. */
    fun clear(r: Rect): Int {
        var n = 0
        forSamples(r, 0) { i -> if (changed[i]) { changed[i] = false; fresh[i] = false; n++ } }
        return n
    }

    /**
     * Did anything under [r] (grown by [marginSamples]) change that means the page itself changed?
     * In tiles where [animatedTile] is true (video behind a menu) only samples that had been still
     * for [STILL_MS] count, and it takes [MIN_FRESH] of them: the art moves all the time (and
     * dark parts flicker now and then), the menu's text and buttons don't.
     */
    inline fun anyStale(r: Rect, marginSamples: Int, animatedTile: (Int) -> Boolean): Boolean {
        val x0 = (r.left / STEP - marginSamples).coerceAtLeast(0)
        val x1 = (r.right / STEP + marginSamples).coerceAtMost(cols - 1)
        val y0 = (r.top / STEP - marginSamples).coerceAtLeast(0)
        val y1 = (r.bottom / STEP + marginSamples).coerceAtMost(rows - 1)
        var fresh = 0
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * cols + x
            if (!changedAt(i)) continue
            if (!animatedTile((y / TILE) * tileCols + x / TILE)) return true
            if (freshAt(i) && ++fresh >= MIN_FRESH) return true
        }
        return false
    }

    /**
     * Did most of the ring of samples around [r] (outside it, [ring] samples deep) change since
     * [since] and then hold still for [SETTLED_MS]? That is a panel or page landing on top. Video
     * keeps moving, so it rarely holds still all around a box at once.
     */
    fun covered(r: Rect, ring: Int, since: Long, now: Long): Boolean {
        val ix0 = r.left / STEP; val ix1 = r.right / STEP
        val iy0 = r.top / STEP; val iy1 = r.bottom / STEP
        var n = 0
        var hit = 0
        forSamples(r, ring) { i ->
            val x = i % cols; val y = i / cols
            if (x in ix0..ix1 && y in iy0..iy1) return@forSamples // our own box is drawn there
            n++
            val at = changedAt[i]
            if (at > since && now - at in SETTLED_MS..SWEEP_MS) hit++
        }
        return n > 0 && hit >= n * SWEEP_FRACTION
    }

    fun changedAt(i: Int) = changed[i]
    fun freshAt(i: Int) = fresh[i]

    /** Fills [tileChanged]; returns how many tiles changed. */
    fun computeTiles(): Int {
        tileChanged.fill(false)
        var n = 0
        for (y in 0 until rows) for (x in 0 until cols) {
            if (!changed[y * cols + x]) continue
            val t = (y / TILE) * tileCols + x / TILE
            if (!tileChanged[t]) { tileChanged[t] = true; n++ }
        }
        return n
    }

    /** Frame-pixel rectangle of tile [t], clamped to the frame. */
    fun tileRect(t: Int, out: Rect, frameWidth: Int, frameHeight: Int): Rect {
        val tx = t % tileCols
        val ty = t / tileCols
        val size = TILE * STEP
        out.set(tx * size, ty * size, minOf((tx + 1) * size, frameWidth), minOf((ty + 1) * size, frameHeight))
        return out
    }

    private inline fun forSamples(r: Rect, margin: Int, block: (Int) -> Unit) {
        val x0 = (r.left / STEP - margin).coerceAtLeast(0)
        val x1 = (r.right / STEP + margin).coerceAtMost(cols - 1)
        val y0 = (r.top / STEP - margin).coerceAtLeast(0)
        val y1 = (r.bottom / STEP + margin).coerceAtMost(rows - 1)
        for (y in y0..y1) for (x in x0..x1) block(y * cols + x)
    }

    companion object {
        const val STEP = 8          // frame pixels between samples
        const val TILE = 16         // samples per tile side (= 128 real px)
        const val THRESHOLD = 24    // luma change that counts
        const val STILL_MS = 4000L  // unchanged this long counts as still
        const val MIN_FRESH = 2
        const val SETTLED_MS = 250L
        const val SWEEP_MS = 2000L
        const val SWEEP_FRACTION = 0.7f
    }
}
