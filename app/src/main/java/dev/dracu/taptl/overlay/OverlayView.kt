package dev.dracu.taptl.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.os.Build
import android.view.View
import android.view.WindowInsets

/** One translated line, in capture-frame coordinates. */
class Box(val rect: Rect, val text: String, val bg: Int, val fg: Int, val shownAt: Long = 0L)

/**
 * Full-screen, non-touchable canvas. Each box covers the Chinese with a flat colour sampled
 * from the real background and draws the English on top: shrink to fit, then 2 lines, then "…".
 */
class OverlayView(
    context: Context,
    /** Height in screen px of the visible status bar (0 when hidden, e.g. in a full-screen game). */
    private val onStatusBar: (Int) -> Unit,
) : View(context) {

    private val bgPaint = Paint()
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = false }
    private val minTextPx = 9f * resources.displayMetrics.scaledDensity
    private val floorTextPx = 7f * resources.displayMetrics.scaledDensity
    private val location = IntArray(2)
    private val screenRect = RectF()

    private var items: List<Item> = emptyList()

    private class Item(val box: Box, val rect: RectF, val layout: StaticLayout)

    /** [scale] maps frame pixels to screen pixels. */
    fun setBoxes(boxes: List<Box>, scale: Float) {
        items = boxes.map { b ->
            val r = RectF(b.rect.left * scale, b.rect.top * scale, b.rect.right * scale, b.rect.bottom * scale)
            fit(b, r)
        }
        invalidate()
    }

    /**
     * The box already spans the free space around the label (the pipeline widens it). Fit the
     * English inside without ever cutting a word: one line shrinking to [minTextPx], then two lines
     * if the box is tall enough, then one line down to [floorTextPx]. "…" only as a last resort.
     */
    private fun fit(box: Box, r: RectF): Item {
        textPaint.color = box.fg
        val h = r.height()
        val w = r.width().coerceAtLeast(1f)
        // One line: start near the Chinese glyph size and shrink until it fits.
        var size = h * 0.72f
        textPaint.textSize = size
        while (size > minTextPx && textPaint.measureText(box.text) > w) {
            size *= 0.92f
            textPaint.textSize = size
        }
        if (textPaint.measureText(box.text) <= w) return Item(box, r, layout(box.text, w, 1))
        // Two lines, if they fit the height at a readable size.
        size = h / 2.3f
        while (size >= floorTextPx) {
            textPaint.textSize = size
            val l = layout(box.text, w, 2)
            if (l.lineCount <= 2 && l.getEllipsisCount(l.lineCount - 1) == 0 && l.height <= h) return Item(box, r, l)
            size *= 0.92f
        }
        // One small line.
        size = minTextPx
        textPaint.textSize = size
        while (size > floorTextPx && textPaint.measureText(box.text) > w) {
            size *= 0.92f
            textPaint.textSize = size
        }
        // Never draw outside the box: the pipeline masks exactly this rect out of OCR.
        return Item(box, r, layout(box.text, w, 1))
    }

    private fun layout(text: String, width: Float, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, TextPaint(textPaint), width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val top = if (Build.VERSION.SDK_INT >= 30) {
            insets.getInsets(WindowInsets.Type.statusBars()).top
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetTop
        }
        onStatusBar(top)
        return super.onApplyWindowInsets(insets)
    }

    override fun onDraw(canvas: Canvas) {
        if (items.isEmpty()) return
        // Boxes are in screen coordinates; undo wherever the window actually landed.
        getLocationOnScreen(location)
        canvas.save()
        canvas.translate(-location[0].toFloat(), -location[1].toFloat())
        for (it in items) {
            bgPaint.color = it.box.bg
            screenRect.set(it.rect)
            canvas.drawRect(screenRect, bgPaint)
            canvas.save()
            canvas.clipRect(it.rect)
            val dy = ((it.rect.height() - it.layout.height) / 2f).coerceAtLeast(0f)
            canvas.translate(it.rect.left, it.rect.top + dy)
            it.layout.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
    }
}
