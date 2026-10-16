package com.salat.times.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.salat.times.AlarmActivity
import com.salat.times.data.RingerModeStore

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_PRAYER_ALARM -> onPrayerAlarm(context, intent)
            AlarmScheduler.ACTION_SILENT_START -> onSilentStart(context, intent)
        }
    }

    private fun onPrayerAlarm(context: Context, intent: Intent) {
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: ""
        val soundPath = intent.getStringExtra(AlarmScheduler.EXTRA_SOUND_PATH)
        val isBefore = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_BEFORE, false)
        val volume = intent.getFloatExtra(AlarmScheduler.EXTRA_VOLUME, 1.0f)
        val plannedAt = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, 0L)

        // Journal : heure prevue vs heure reelle de reception (le retard est visible dans le menu)
        val delaySec = if (plannedAt > 0) (System.currentTimeMillis() - plannedAt) / 1000 else -1
        AlarmLog.add(
            context,
            "ALARME ${if (isBefore) "avant" else "adhan"} $label : retard systeme = ${delaySec}s"
        )

        // 1) Le SON d'abord : c'est le plus important, rien ne doit le retarder.
        try {
            val serviceIntent = Intent(context, AthanPlaybackService::class.java).apply {
                putExtra(AthanPlaybackService.EXTRA_LABEL, label)
                putExtra(AthanPlaybackService.EXTRA_SOUND_PATH, soundPath)
                putExtra(AthanPlaybackService.EXTRA_IS_BEFORE, isBefore)
                putExtra(AthanPlaybackService.EXTRA_VOLUME, volume)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            AlarmLog.add(context, "ERREUR demarrage service son : ${e.javaClass.simpleName} ${e.message}")
        }

        // 2) L'ecran d'alarme ensuite (un receiver declenche par setAlarmClock peut lancer une Activity).
        try {
            context.startActivity(
                Intent(context, AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    putExtra(AlarmActivity.EXTRA_LABEL, label)
                    putExtra(AlarmActivity.EXTRA_IS_BEFORE, isBefore)
                }
            )
        } catch (_: Exception) {
            // le fullScreenIntent de la notification du service prendra le relais
        }

        // 3) Rearmement en chaine : chaque alarme qui sonne reprogramme les suivantes.
        rearm(context)
    }

    private fun onSilentStart(context: Context, intent: Intent) {
        val endAt = intent.getLongExtra(AlarmScheduler.EXTRA_SILENT_END_AT, 0L)
        val now = System.currentTimeMillis()

        // Periode deja terminee (telephone eteint / alarme tres en retard) : ne rien faire
        if (endAt > 0 && now >= endAt) {
            AlarmLog.add(context, "SILENCE ignore : periode deja terminee")
            rearm(context)
            return
        }

        if (AthanPlaybackService.isPlaying) {
            // L'adhan joue encore : on attend sa fin avant d'activer le silence, sinon le petit
            // son d'information et le changement de mode se melangent a l'adhan (desordre).
            RingerModeStore(context).setPendingSilentUntil(if (endAt > 0) endAt else now + 30 * 60_000L)
            AlarmLog.add(context, "SILENCE en attente : adhan en cours")
        } else {
            SilentMode.start(context)
        }
        // La fin du silence est deja programmee separement (SilentModeEndReceiver).
        rearm(context)
    }

    private fun rearm(context: Context) {
        try {
            AlarmScheduler.rescheduleAll(context)
        } catch (e: Exception) {
            AlarmLog.add(context, "ERREUR reprogrammation : ${e.javaClass.simpleName} ${e.message}")
        }
    }
}
