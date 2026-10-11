package dev.dracu.taptl.tile

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import dev.dracu.taptl.TAG
import dev.dracu.taptl.capture.CaptureService
import dev.dracu.taptl.capture.CaptureState
import dev.dracu.taptl.capture.ConsentActivity

class TranslateTileService : TileService() {

    // Lets the setup screen tick off "Add the tile", however it was added.
    override fun onTileAdded() {
        getSharedPreferences("taptl", MODE_PRIVATE).edit().putBoolean("tile_added", true).apply()
    }

    override fun onTileRemoved() {
        getSharedPreferences("taptl", MODE_PRIVATE).edit().putBoolean("tile_added", false).apply()
    }

    override fun onStartListening() {
        // Listening at all means the tile is in Quick Settings (also covers tiles added before this existed).
        getSharedPreferences("taptl", MODE_PRIVATE).edit().putBoolean("tile_added", true).apply()
        val tile = qsTile ?: return
        val state = CaptureService.state
        tile.state = if (state == CaptureState.ON) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when (state) {
            CaptureState.OFF -> "Off"
            CaptureState.REQUESTING -> "Starting…"
            CaptureState.ON -> "On"
        }
        tile.updateTile()
    }

    override fun onClick() {
        Log.i(TAG, "tile click in state ${CaptureService.state}")
        when (CaptureService.state) {
            CaptureState.ON -> CaptureService.stop(this)
            // REQUESTING re-opens consent too, so a lost dialog never leaves the tile stuck.
            CaptureState.OFF, CaptureState.REQUESTING ->
                if (isLocked) unlockAndRun { requestConsent() } else requestConsent()
        }
    }

    private fun requestConsent() {
        CaptureService.setState(this, CaptureState.REQUESTING)
        val intent = Intent(this, ConsentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val options = if (Build.VERSION.SDK_INT >= 36) {
                ActivityOptions.makeBasic()
                    .setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                    )
                    .toBundle()
            } else null
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                options,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
