package dev.dracu.taptl.capture

import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.dracu.taptl.TAG

/** Invisible: asks for screen-capture consent, starts [CaptureService], then finishes. */
class ConsentActivity : ComponentActivity() {

    private var answered = false

    private val consent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        answered = true
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            CaptureService.start(this, result.resultCode, data)
        } else {
            Log.i(TAG, "consent declined")
            CaptureService.setState(this, CaptureState.OFF)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val manager = getSystemService(MediaProjectionManager::class.java)
        // Whole screen only: skips the "single app" picker on 14+.
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        consent.launch(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!answered && !isChangingConfigurations && CaptureService.state == CaptureState.REQUESTING) {
            CaptureService.setState(this, CaptureState.OFF)
        }
    }
}
