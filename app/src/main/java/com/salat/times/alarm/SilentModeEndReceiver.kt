package com.salat.times.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Environment
import com.salat.times.data.RingerModeStore
import java.io.File

class SilentModeEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_SILENT_END) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val store = RingerModeStore(context)
        val previous = store.getPreviousMode()
        try {
            am.ringerMode = previous
        } catch (e: SecurityException) {
            // pas d'acces DND, ignorer
        }

        // Joue normal.mp3 pour informer l'utilisateur que le mode normal est retabli
        playNotificationSound(context, "normal.mp3")
    }

    /**
     * Joue un court fichier mp3 depuis le dossier /storage/emulated/0/SalatAthan/
     * pour notifier l'utilisateur que le mode sonore normal est retabli.
     */
    private fun playNotificationSound(context: Context, fileName: String) {
        try {
            val soundDir = File(Environment.getExternalStorageDirectory(), "SalatAthan")
            val file = File(soundDir, fileName)
            if (!file.exists()) return

            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { it.release() }
            mp.setOnErrorListener { p, _, _ -> p.release(); true }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            // Fichier absent ou erreur de lecture, ignorer
        }
    }
}
