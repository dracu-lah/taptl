package dev.dracu.taptl

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

const val TAG = "TapTL"
const val CHANNEL_CAPTURE = "capture"

class TapTLApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_CAPTURE,
            getString(R.string.channel_capture),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
