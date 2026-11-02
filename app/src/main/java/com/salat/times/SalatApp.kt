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
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                // Remarque : IMPORTANCE_HIGH est le maximum effectif pour fullScreenIntent.
                // IMPORTANCE_MAX est l'alias de HIGH dans les versions recentes de l'API.
            }
            nm.createNotificationChannel(athanChannel)
        }
    }

    companion object {
        // Nouveau canal v2 : les proprietes d'un canal Android ne peuvent jamais etre mises
        // a jour apres sa premiere creation. Changer l'ID force la recreation avec
        // les nouvelles proprietes (lockscreenVisibility, etc.) sur les installations existantes.
        const val CHANNEL_ATHAN = "channel_athan_v2"
    }
}
