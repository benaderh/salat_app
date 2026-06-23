package com.salat.times.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.salat.times.AlarmActivity
import com.salat.times.data.RingerModeStore
import java.io.File

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_PRAYER_ALARM -> {
                val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: ""
                val soundPath = intent.getStringExtra(AlarmScheduler.EXTRA_SOUND_PATH)
                val isBefore = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_BEFORE, false)
                val volume = intent.getFloatExtra(AlarmScheduler.EXTRA_VOLUME, 1.0f)

                // IMPORTANT : Lancer AlarmActivity DIRECTEMENT depuis le receiver.
                // Un BroadcastReceiver declenche par une alarme exacte a le privilege
                // systeme de demarrer des Activities, meme sur Android 10+.
                // Le service foreground n'a PAS ce privilege (bloque par le systeme).
                try {
                    val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NO_USER_ACTION
                        putExtra(AlarmActivity.EXTRA_LABEL, label)
                        putExtra(AlarmActivity.EXTRA_IS_BEFORE, isBefore)
                    }
                    context.startActivity(alarmActivityIntent)
                } catch (_: Exception) {
                    // Fallback : le fullScreenIntent du service prendra le relais
                }

                // Demarrer le service foreground pour jouer le son + notification
                val serviceIntent = Intent(context, AthanPlaybackService::class.java).apply {
                    putExtra(AthanPlaybackService.EXTRA_LABEL, label)
                    putExtra(AthanPlaybackService.EXTRA_SOUND_PATH, soundPath)
                    putExtra(AthanPlaybackService.EXTRA_IS_BEFORE, isBefore)
                    putExtra(AthanPlaybackService.EXTRA_VOLUME, volume)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }

            AlarmScheduler.ACTION_SILENT_START -> {
                val durationMin = intent.getIntExtra(AlarmScheduler.EXTRA_SILENT_DURATION_MIN, 30)
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                // Joue silent.mp3 pour informer l'utilisateur que le mode silencieux s'active
                playSilentNotificationSound(context, "silent.mp3")

                // Sauvegarde le mode sonore actuel pour pouvoir le restaurer apres la duree
                val store = RingerModeStore(context)
                store.savePreviousMode(am.ringerMode)

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || hasDndAccess(context)) {
                    try {
                        am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    } catch (e: SecurityException) {
                        // Permission DND non accordee, ignorer silencieusement
                    }
                }
                // La fin du silence est deja programmee separement par AlarmScheduler
                // (SilentModeEndReceiver), donc rien d'autre a faire ici.
            }
        }
    }

    private fun hasDndAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    /**
     * Joue un court fichier mp3 depuis le dossier /storage/emulated/0/SalatAthan/
     * pour notifier l'utilisateur (silent.mp3 au debut, normal.mp3 a la fin du mode silencieux).
     */
    private fun playSilentNotificationSound(context: Context, fileName: String) {
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
            // Fichier absent ou erreur de lecture, ignorer silencieusement
        }
    }
}
