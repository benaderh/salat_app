package com.salat.times.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import com.salat.times.data.RingerModeStore
import java.io.File

/** Active / desactive le mode silencieux du telephone (avec petit son d'information). */
object SilentMode {

    fun start(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val store = RingerModeStore(context)
        store.takePendingSilentUntil() // efface un eventuel silence en attente

        // Le petit son d'information passe AVANT le passage en silencieux
        playInfoSound(context, "silent.mp3")

        store.savePreviousMode(am.ringerMode)
        if (hasDndAccess(context)) {
            try {
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
            } catch (_: SecurityException) {
                // acces "Ne pas deranger" non accorde
            }
        }
        AlarmLog.add(context, "SILENCE active (dnd=${hasDndAccess(context)})")
    }

    fun end(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val previous = RingerModeStore(context).getPreviousMode()
        try {
            am.ringerMode = previous
        } catch (_: SecurityException) {
            // pas d'acces DND, ignorer
        }
        playInfoSound(context, "normal.mp3")
        AlarmLog.add(context, "SILENCE termine -> mode $previous")
    }

    private fun hasDndAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    /**
     * Joue un court mp3 depuis /storage/emulated/0/SalatAthan/ (silent.mp3 / normal.mp3).
     * Usage ALARM : audible meme quand le telephone est en mode silencieux/vibreur.
     */
    private fun playInfoSound(context: Context, fileName: String) {
        try {
            val file = File(File(Environment.getExternalStorageDirectory(), "SalatAthan"), fileName)
            if (!file.exists()) return
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { it.release() }
            mp.setOnErrorListener { p, _, _ -> p.release(); true }
            mp.prepare()
            mp.start()
        } catch (_: Exception) {
            // fichier absent ou illisible : ignorer
        }
    }
}
