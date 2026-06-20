package com.salat.times

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SalatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val athanChannel = NotificationChannel(
                CHANNEL_ATHAN,
                "تنبيهات الأذان",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات أوقات الصلاة"
                setBypassDnd(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(athanChannel)
        }
    }

    companion object {
        const val CHANNEL_ATHAN = "channel_athan"
    }
}
