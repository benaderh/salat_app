package com.salat.times.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.salat.times.data.RingerModeStore

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_PRAYER_ALARM -> {
                val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: ""
                val soundPath = intent.getStringExtra(AlarmScheduler.EXTRA_SOUND_PATH)
                val isBefore = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_BEFORE, false)

                val serviceIntent = Intent(context, AthanPlaybackService::class.java).apply {
                    putExtra(AthanPlaybackService.EXTRA_LABEL, label)
                    putExtra(AthanPlaybackService.EXTRA_SOUND_PATH, soundPath)
                    putExtra(AthanPlaybackService.EXTRA_IS_BEFORE, isBefore)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }

            AlarmScheduler.ACTION_SILENT_START -> {
                val durationMin = intent.getIntExtra(AlarmScheduler.EXTRA_SILENT_DURATION_MIN, 15)
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
}
