package com.salat.times.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.salat.times.data.PrefsManager
import com.salat.times.data.SalatDataRepository
import com.salat.times.model.ComputedDay
import com.salat.times.model.PrayerKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Planifie les alarmes exactes (avant + a l'heure) pour les 5 prieres, sur les
 * prochains jours (aujourd'hui + demain, pour couvrir le cas ou l'heure est deja passee).
 *
 * Utilise setExactAndAllowWhileIdle pour garantir le declenchement meme en Doze.
 * Sur Android 12+, necessite la permission SCHEDULE_EXACT_ALARM (verifiee en amont).
 */
object AlarmScheduler {

    private const val DAYS_AHEAD = 2

    // request code scheme: prayerOrdinal*10 + type(0=before,1=attime,2=silentEnd) + dayOffset*100
    private fun requestCode(prayer: PrayerKey, type: Int, dayOffset: Int): Int =
        dayOffset * 100 + prayer.ordinal * 10 + type

    fun rescheduleAll(context: Context) {
        val repo = SalatDataRepository.get(context)
        val prefs = PrefsManager(context)
        val villeId = prefs.villeId
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        // Annule d'abord tout ce qui est programme pour repartir propre
        cancelAll(context)

        for (dayOffset in 0 until DAYS_AHEAD) {
            val date = today.plusDays(dayOffset.toLong())
            val day = repo.computeDay(date, villeId)
            scheduleForDay(context, day, dayOffset, prefs)
        }
    }

    private fun scheduleForDay(context: Context, day: ComputedDay, dayOffset: Int, prefs: PrefsManager) {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)

        val prayers = listOf(
            Triple(PrayerKey.FAJR, day.fajr, "الفجر"),
            Triple(PrayerKey.DHOHR, day.dhohr, "الظهر"),
            Triple(PrayerKey.ASR, day.asr, "العصر"),
            Triple(PrayerKey.MAGHREB, day.maghreb, "المغرب"),
            Triple(PrayerKey.ISHA, day.isha, "العشاء")
        )

        for ((key, timeStr, label) in prayers) {
            if (timeStr == null) continue
            val cfg = prefs.getAlarmConfig(key)
            val time = LocalTime.parse(timeStr)
            val prayerDateTime = LocalDateTime.of(day.gregorianDate, time)

            if (cfg.beforeEnabled && cfg.beforeMinutes > 0) {
                val beforeDt = prayerDateTime.minusMinutes(cfg.beforeMinutes.toLong())
                if (beforeDt.isAfter(now)) {
                    scheduleAlarm(
                        context, beforeDt, key, dayOffset, type = TYPE_BEFORE,
                        label = label, soundPath = cfg.beforeSoundPath, isBefore = true
                    )
                }
            }
            if (cfg.atTimeEnabled) {
                if (prayerDateTime.isAfter(now)) {
                    scheduleAlarm(
                        context, prayerDateTime, key, dayOffset, type = TYPE_AT_TIME,
                        label = label, soundPath = cfg.atTimeSoundPath, isBefore = false
                    )
                }
            }
            if (cfg.silentEnabled) {
                if (prayerDateTime.isAfter(now)) {
                    scheduleSilentStart(context, prayerDateTime, key, dayOffset, cfg.silentDurationMinutes)
                }
            }
        }
    }

    private fun scheduleAlarm(
        context: Context,
        triggerAt: LocalDateTime,
        prayer: PrayerKey,
        dayOffset: Int,
        type: Int,
        label: String,
        soundPath: String?,
        isBefore: Boolean
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PRAYER_KEY, prayer.name)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_SOUND_PATH, soundPath)
            putExtra(EXTRA_IS_BEFORE, isBefore)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode(prayer, type, dayOffset), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExact(am, triggerMillis, pi)
    }

    private fun scheduleSilentStart(
        context: Context,
        triggerAt: LocalDateTime,
        prayer: PrayerKey,
        dayOffset: Int,
        durationMinutes: Int
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val startIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_SILENT_START
            putExtra(EXTRA_PRAYER_KEY, prayer.name)
            putExtra(EXTRA_SILENT_DURATION_MIN, durationMinutes)
        }
        val piStart = PendingIntent.getBroadcast(
            context, requestCode(prayer, TYPE_SILENT_START, dayOffset), startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExact(am, triggerMillis, piStart)

        // Programme aussi la fin du silence
        val endMillis = triggerMillis + durationMinutes * 60_000L
        val endIntent = Intent(context, SilentModeEndReceiver::class.java).apply {
            action = ACTION_SILENT_END
        }
        val piEnd = PendingIntent.getBroadcast(
            context, requestCode(prayer, TYPE_SILENT_END, dayOffset), endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExact(am, endMillis, piEnd)
    }

    private fun setExact(am: AlarmManager, triggerMillis: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                } else {
                    // Permission non accordee : fallback non-exact (l'UI doit demander la permission)
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        }
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (dayOffset in 0 until DAYS_AHEAD) {
            for (prayer in PrayerKey.values()) {
                for (type in listOf(TYPE_BEFORE, TYPE_AT_TIME, TYPE_SILENT_START, TYPE_SILENT_END)) {
                    val intent = if (type == TYPE_SILENT_END)
                        Intent(context, SilentModeEndReceiver::class.java)
                    else
                        Intent(context, PrayerAlarmReceiver::class.java)
                    val pi = PendingIntent.getBroadcast(
                        context, requestCode(prayer, type, dayOffset), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    am.cancel(pi)
                }
            }
        }
    }

    const val ACTION_PRAYER_ALARM = "com.salat.times.ACTION_PRAYER_ALARM"
    const val ACTION_SILENT_START = "com.salat.times.ACTION_SILENT_START"
    const val ACTION_SILENT_END = "com.salat.times.ACTION_SILENT_END"
    const val EXTRA_PRAYER_KEY = "extra_prayer_key"
    const val EXTRA_LABEL = "extra_label"
    const val EXTRA_SOUND_PATH = "extra_sound_path"
    const val EXTRA_IS_BEFORE = "extra_is_before"
    const val EXTRA_SILENT_DURATION_MIN = "extra_silent_duration_min"

    private const val TYPE_BEFORE = 0
    private const val TYPE_AT_TIME = 1
    private const val TYPE_SILENT_START = 2
    private const val TYPE_SILENT_END = 3
}
