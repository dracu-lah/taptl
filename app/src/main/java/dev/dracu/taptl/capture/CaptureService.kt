package dev.dracu.taptl.capture

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import dev.dracu.taptl.CHANNEL_CAPTURE
import dev.dracu.taptl.MainActivity
import dev.dracu.taptl.R
import dev.dracu.taptl.TAG
import dev.dracu.taptl.overlay.OverlayController
import dev.dracu.taptl.pipeline.Pipeline
import dev.dracu.taptl.tile.TranslateTileService

enum class CaptureState { OFF, REQUESTING, ON }

/** Foreground service that owns the screen capture for one consent session. */
class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var frameSource: FrameSource? = null
    private var pipeline: Pipeline? = null
    private var overlay: OverlayController? = null
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "projection stopped by the system")
            stopSelf()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            Log.i(TAG, "captured content resized to ${width}x$height")
            frameSource?.resize(width, height)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        if (projection != null || data == null) {
            Log.w(TAG, "ignoring start: running=${projection != null}, data=${data != null}")
            if (projection == null) stopSelf()
            return START_NOT_STICKY
        }

        // Order matters on 14+: consent → startForeground → getMediaProjection.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val mp = manager.getMediaProjection(resultCode, data)
                ?: error("getMediaProjection returned null")
            projection = mp
            // The callback must be registered before createVirtualDisplay.
            mp.registerCallback(callback, Handler(Looper.getMainLooper()))
            val size = screenSize()
            val ov = OverlayController(this, onStatusBar = { pipeline?.setStatusBarHeight(it) })
                .also { overlay = it }
            ov.show()
            val source = FrameSource(
                mp, resources.configuration.densityDpi,
                onFailed = { main.post { stopSelf() } },
                onFrame = { image, capturedWidth -> pipeline?.onFrame(image, capturedWidth) ?: false },
            )
            frameSource = source
            pipeline = Pipeline(this, source.handler, ov, onBlackScreen = { main.post { onBlackScreen() } })
            source.start(size.x, size.y)
            setState(this, CaptureState.ON)
            Log.i(TAG, "capture started, screen ${size.x}x${size.y}")
        } catch (e: Exception) {
            Log.e(TAG, "capture failed to start", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        frameSource?.release()
        frameSource = null
        pipeline?.release()
        pipeline = null
        overlay?.remove()
        overlay = null
        projection?.let {
            it.unregisterCallback(callback)
            it.stop()
        }
        projection = null
        setState(this, CaptureState.OFF)
        Log.i(TAG, "capture service destroyed")
        super.onDestroy()
    }

    /** Frames stay black: the app on screen blocks capture. Tell the user and stop. */
    private fun onBlackScreen() {
        val n = NotificationCompat.Builder(this, CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.notif_black_title))
            .setContentText(getString(R.string.notif_black_text))
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_BLACK_ID, n) }
        stopSelf()
    }

    /** Real screen size in the current rotation. */
    private fun screenSize(): Point {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                .getSystemService(WindowManager::class.java)
                .maximumWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                display.getRealSize(it)
            }
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.notif_capturing))
            .setContentText(getString(R.string.notif_capturing_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_BLACK_ID = 2
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"

        /** Lives in the app process; projection dies with the process, so OFF is right after a restart. */
        @Volatile
        var state = CaptureState.OFF
            private set

        fun setState(context: Context, newState: CaptureState) {
            if (state == newState) return
            Log.i(TAG, "state $state → $newState")
            state = newState
            TileService.requestListeningState(
                context, ComponentName(context, TranslateTileService::class.java)
            )
        }

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, CaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(intent)
        }
    }
}
