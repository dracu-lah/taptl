package dev.dracu.taptl.capture

import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import dev.dracu.taptl.TAG

/**
 * Full-resolution screen frames from a VirtualDisplay into an ImageReader. Everything runs on
 * one low-priority thread; frames only arrive when the screen redraws. The pipeline may keep the
 * latest image open (so it never has to copy frames), hence one image more than the usual 2.
 */
class FrameSource(
    private val projection: MediaProjection,
    private val densityDpi: Int,
    private val onFailed: () -> Unit,
    /** Called on the capture thread. Return true to keep the image (the caller closes it later). */
    private val onFrame: (image: Image, capturedWidth: Int) -> Boolean,
) {
    private val thread = HandlerThread("TapTL-capture", Process.THREAD_PRIORITY_BACKGROUND)
        .apply { start() }
    /** The capture thread: frames, diffing and pipeline decisions all run here. */
    val handler = Handler(thread.looper)

    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var capturedWidth = 0

    fun start(screenWidth: Int, screenHeight: Int) = handler.post {
        try {
            val w = screenWidth
            val h = screenHeight
            capturedWidth = screenWidth
            val r = newReader(w, h)
            display = projection.createVirtualDisplay(
                "TapTL", w, h, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface, null, handler,
            )
            Log.i(TAG, "virtual display ${w}x$h")
        } catch (e: Exception) {
            Log.e(TAG, "createVirtualDisplay failed", e)
            onFailed()
        }
    }

    /** Called with the full captured size, e.g. after a rotation. */
    fun resize(screenWidth: Int, screenHeight: Int) = handler.post {
        val vd = display ?: return@post
        val w = screenWidth
        val h = screenHeight
        if (w == width && h == height) return@post
        capturedWidth = screenWidth
        val old = reader
        val r = newReader(w, h)
        vd.resize(w, h, densityDpi)
        vd.setSurface(r.surface)
        old?.close()
        Log.i(TAG, "virtual display resized to ${w}x$h")
    }

    fun release() = handler.post {
        display?.release()
        display = null
        reader?.close()
        reader = null
        thread.quitSafely()
    }

    private fun newReader(w: Int, h: Int): ImageReader {
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, MAX_IMAGES)
        r.setOnImageAvailableListener(::onImageAvailable, handler)
        reader = r
        width = w
        height = h
        return r
    }

    private fun onImageAvailable(r: ImageReader) {
        if (r !== reader) return // a late callback from a reader we already swapped out
        val image = r.acquireLatestImage() ?: return
        var keep = false
        try {
            keep = onFrame(image, capturedWidth)
        } catch (e: Exception) {
            Log.e(TAG, "frame handling failed", e)
        } finally {
            if (!keep) image.close()
        }
    }

    private companion object {
        const val MAX_IMAGES = 3 // one kept by the pipeline, one being read, one spare
    }
}
