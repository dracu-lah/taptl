package dev.dracu.taptl.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import dev.dracu.taptl.TAG

/**
 * The single overlay window: full screen, never touchable or focusable, window alpha 0.8
 * (Android 12+ lets touches through only at <= 0.8). Safe to call from any thread.
 */
class OverlayController(context: Context, private val onStatusBar: (Int) -> Unit) {

    private val main = Handler(Looper.getMainLooper())
    private val windowContext: Context = if (Build.VERSION.SDK_INT >= 30) {
        // A service context has no display; bind one first.
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        context.createDisplayContext(display).createWindowContext(LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    } else {
        context
    }
    private val wm = windowContext.getSystemService(WindowManager::class.java)
    private var view: OverlayView? = null

    fun show() = main.post {
        if (view != null) return@post
        if (!Settings.canDrawOverlays(windowContext)) {
            Log.w(TAG, "overlay permission missing; nothing will be drawn")
            return@post
        }
        val v = OverlayView(windowContext, onStatusBar)
        val lp = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_TOUCHABLE or LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            alpha = MAX_ALPHA
            windowAnimations = 0
            title = "TapTL"
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
                LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        try {
            wm.addView(v, lp)
            view = v
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
        }
    }

    /** Boxes are in frame coordinates; [scale] maps them to screen pixels. */
    fun setBoxes(boxes: List<Box>, scale: Float) = main.post {
        view?.setBoxes(boxes, scale)
    }

    fun remove() = main.post {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private companion object {
        const val MAX_ALPHA = 0.8f
    }
}
