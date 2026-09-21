package com.airplay.cast

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AirPlayApp : Application() {
    companion object {
        const val CHANNEL_CAST = "airplay_cast"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_CAST, "AirPlay Cast", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Active AirPlay streaming session" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
