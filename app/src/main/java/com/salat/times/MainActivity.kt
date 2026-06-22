package com.salat.times

import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.salat.times.alarm.AlarmScheduler
import com.salat.times.data.PrefsManager
import com.salat.times.data.SalatDataRepository
import com.salat.times.model.ComputedDay
import com.salat.times.model.PrayerKey
import com.salat.times.util.ArabicNames
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var repo: SalatDataRepository
    private lateinit var prefs: PrefsManager

    private lateinit var tvDateGreg: TextView
    private lateinit var tvDateHijri: TextView
    private lateinit var tvClock: TextView
    private lateinit var btnMenu: View
    private lateinit var btnPrev: TextView
    private lateinit var btnNow: TextView
    private lateinit var btnNext: TextView

    private lateinit var rowFajr: android.widget.LinearLayout
    private lateinit var rowDhohr: android.widget.LinearLayout
    private lateinit var rowAsr: android.widget.LinearLayout
    private lateinit var rowMaghreb: android.widget.LinearLayout
    private lateinit var rowIsha: android.widget.LinearLayout

    private var displayedDate: LocalDate = LocalDate.now()
    private var isToday: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateClockAndProgress()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyImmersiveFullscreen()

        repo = SalatDataRepository.get(this)
        prefs = PrefsManager(this)

        tvDateGreg = findViewById(R.id.tvDateGreg)
        tvDateHijri = findViewById(R.id.tvDateHijri)
        tvClock = findViewById(R.id.tvClock)
        btnMenu = findViewById(R.id.btnMenu)
        btnPrev = findViewById(R.id.btnPrev)
        btnNow = findViewById(R.id.btnNow)
        btnNext = findViewById(R.id.btnNext)

        rowFajr = findViewById(R.id.rowFajr)
        rowDhohr = findViewById(R.id.rowDhohr)
        rowAsr = findViewById(R.id.rowAsr)
        rowMaghreb = findViewById(R.id.rowMaghreb)
        rowIsha = findViewById(R.id.rowIsha)

        inflatePrayerRows()

        tvDateHijri.setOnClickListener { showChouroukToast() }

        btnMenu.setOnClickListener { showMenu(it) }
        btnPrev.setOnClickListener {
            displayedDate = displayedDate.minusDays(1)
            updateDayState()
        }
        btnNext.setOnClickListener {
            displayedDate = displayedDate.plusDays(1)
            updateDayState()
        }
        btnNow.setOnClickListener {
            displayedDate = LocalDate.now()
            updateDayState()
        }

        maybeRequestBatteryExemption()
        maybeRequestStoragePermission()
        ensureExactAlarmPermission()
        AlarmScheduler.rescheduleAll(this)

        updateDayState()
    }

    override fun onResume() {
        super.onResume()
        // Reflechir les eventuels changements (ville/alarmes modifies dans les ecrans de reglages)
        prefs = PrefsManager(this)
        renderDay()
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }

    private fun applyImmersiveFullscreen() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private data class RowHolder(
        val root: View,
        val name: TextView,
        val time: TextView,
        val elapsed: TextView,
        val remaining: TextView,
        val label: String
    )

    private lateinit var holderFajr: RowHolder
    private lateinit var holderDhohr: RowHolder
    private lateinit var holderAsr: RowHolder
    private lateinit var holderMaghreb: RowHolder
    private lateinit var holderIsha: RowHolder

    private fun inflatePrayerRows() {
        val inflater = LayoutInflater.from(this)
        holderFajr = inflateInto(rowFajr, inflater, getString(R.string.fajr))
        holderDhohr = inflateInto(rowDhohr, inflater, getString(R.string.dhohr))
        holderAsr = inflateInto(rowAsr, inflater, getString(R.string.asr))
        holderMaghreb = inflateInto(rowMaghreb, inflater, getString(R.string.maghreb))
        holderIsha = inflateInto(rowIsha, inflater, getString(R.string.isha))
    }

    private fun inflateInto(parent: android.widget.LinearLayout, inflater: LayoutInflater, label: String): RowHolder {
        val view = inflater.inflate(R.layout.item_prayer_row, parent, false)
        parent.addView(view)
        val nameTv = view.findViewById<TextView>(R.id.tvPrayerName)
        val timeTv = view.findViewById<TextView>(R.id.tvPrayerTime)
        val elapsedTv = view.findViewById<TextView>(R.id.tvElapsed)
        val remainingTv = view.findViewById<TextView>(R.id.tvRemaining)
        nameTv.text = label
        val holder = RowHolder(parent, nameTv, timeTv, elapsedTv, remainingTv, label)
        view.setOnClickListener { Toast.makeText(this, label, Toast.LENGTH_SHORT).show() }
        return holder
    }

    /** Toast affichant l'heure du Chourouk (lever du soleil), traite comme un message de nom de priere. */
    private fun showChouroukToast() {
        val day = repo.computeDay(displayedDate, prefs.villeId)
        val text = getString(R.string.chourouk_label) + (day.chourouk ?: "--:--")
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.menu_select_city))
        popup.menu.add(0, 2, 1, getString(R.string.menu_alarms))
        popup.menu.add(0, 3, 2, getString(R.string.menu_import_data))
        if (prefs.useImportedData) {
            popup.menu.add(0, 4, 3, getString(R.string.cancel_import))
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, SettingsVilleActivity::class.java))
                2 -> startActivity(Intent(this, SettingsAlarmesActivity::class.java))
                3 -> confirmImportData()
                4 -> confirmCancelImport()
            }
            true
        }
        popup.show()
    }

    private fun confirmImportData() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
            startActivity(Intent(this, StoragePermissionActivity::class.java))
            return
        }
        val file = SalatDataRepository.importFile()
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.import_not_found), Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_confirm_title))
            .setMessage(getString(R.string.import_confirm_body))
            .setPositiveButton(getString(R.string.save)) { _, _ -> doImport(file) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun doImport(file: File) {
        try {
            val text = file.readText(Charsets.UTF_8)
            SalatDataRepository.validateJsonText(text)
            prefs.useImportedData = true
            repo = SalatDataRepository.reload(this)
            AlarmScheduler.rescheduleAll(this)
            Toast.makeText(this, getString(R.string.import_success), Toast.LENGTH_SHORT).show()
            renderDay()
        } catch (e: Exception) {
            val detail = e.message ?: e.toString()
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_error))
                .setMessage(detail)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun confirmCancelImport() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_cancel_title))
            .setMessage(getString(R.string.import_cancel_body))
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                prefs.useImportedData = false
                repo = SalatDataRepository.reload(this)
                AlarmScheduler.rescheduleAll(this)
                renderDay()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /** Met a jour l'etat jour-affiche + bascule visibilite du bouton 'اليوم'/'الآن', puis redessine. */
    private fun updateDayState() {
        isToday = displayedDate == LocalDate.now()
        // Point 9 : bouton du milieu masque par defaut (sur aujourd'hui), visible des qu'on
        // navigue sur un autre jour (et devient alors un raccourci "اليوم" pour revenir).
        btnNow.visibility = if (isToday) View.INVISIBLE else View.VISIBLE
        renderDay()
    }

    private fun renderDay() {
        val villeId = prefs.villeId
        val day = repo.computeDay(displayedDate, villeId)
        renderDateLines(day)
        renderPrayerRows(day)
        updateClockAndProgress()
    }

    private fun renderDateLines(day: ComputedDay) {
        val weekDay = ArabicNames.weekDayName(day.gregorianDate)
        // Point 3, ligne 1 : format yyyy/mm/dd
        val gregStr = "%04d/%02d/%02d".format(
            day.gregorianDate.year, day.gregorianDate.monthValue, day.gregorianDate.dayOfMonth
        )
        tvDateGreg.text = "$weekDay \u2066$gregStr\u2069"

        if (day.hijriMonth != null && day.hijriDay != null && day.hijriYear != null) {
            val hijriStr = "${day.hijriDay} " + ArabicNames.hijriMonthName(day.hijriMonth) + " ${day.hijriYear}"
            tvDateHijri.text = hijriStr
            tvDateHijri.visibility = View.VISIBLE
        } else {
            // Hors plage hijri connue (fallback ref_hor) : pas de mois hijri disponible
            tvDateHijri.text = ""
            tvDateHijri.visibility = View.GONE
        }
    }

    private fun renderPrayerRows(day: ComputedDay) {
        setRow(holderFajr, day.fajr)
        setRow(holderDhohr, day.dhohr)
        setRow(holderAsr, day.asr)
        setRow(holderMaghreb, day.maghreb)
        setRow(holderIsha, day.isha)
    }

    private fun setRow(holder: RowHolder, time: String?) {
        holder.time.text = time ?: "--:--"
        holder.time.setTextColor(getColor(R.color.text_secondary))
        holder.time.setTypeface(null, android.graphics.Typeface.BOLD)
        holder.elapsed.text = ""
        holder.remaining.text = ""
    }

    /** Met a jour l'horloge en direct (hh:mm:ss) + determine prochaine priere + affiche ecoule/restant. */
    private fun updateClockAndProgress() {
        val now = LocalDateTime.now()
        // Point 3, ligne 3 : horloge hh:mm:ss mise a jour chaque seconde
        tvClock.text = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        if (!isToday) {
            // Sur un autre jour que aujourd'hui, pas de calcul ecoule/restant ni surbrillance
            return
        }

        val day = repo.computeDay(displayedDate, prefs.villeId)
        val prayers = listOf(
            Triple(PrayerKey.FAJR, day.fajr, holderFajr),
            Triple(PrayerKey.DHOHR, day.dhohr, holderDhohr),
            Triple(PrayerKey.ASR, day.asr, holderAsr),
            Triple(PrayerKey.MAGHREB, day.maghreb, holderMaghreb),
            Triple(PrayerKey.ISHA, day.isha, holderIsha)
        ).mapNotNull { (k, t, h) -> if (t != null) Triple(k, LocalTime.parse(t), h) else null }

        if (prayers.isEmpty()) return

        val nowTime = now.toLocalTime()
        // Trouve la priere precedente (la derniere dont l'heure est <= maintenant) et la suivante
        var prevIndex = -1
        for (i in prayers.indices) {
            if (!prayers[i].second.isAfter(nowTime)) prevIndex = i else break
        }
        val nextIndex = if (prevIndex + 1 < prayers.size) prevIndex + 1 else -1

        // Reinitialiser les styles temps/couleurs
        prayers.forEach { (_, _, h) ->
            h.elapsed.text = ""
            h.remaining.text = ""
            h.time.setTextColor(getColor(R.color.text_secondary))
            h.time.setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // Priere precedente : temps ecoule depuis son heure, a gauche, rouge
        if (prevIndex >= 0) {
            val (_, t, h) = prayers[prevIndex]
            val elapsed = Duration.between(t, nowTime)
            applyDurationText(h.elapsed, elapsed)
        } else {
            // Avant le premier Fajr du jour : la priere "precedente" est l'Isha d'hier
            val yesterday = repo.computeDay(displayedDate.minusDays(1), prefs.villeId)
            yesterday.isha?.let { ishaStr ->
                val ishaDateTime = LocalDateTime.of(displayedDate.minusDays(1), LocalTime.parse(ishaStr))
                val elapsed = Duration.between(ishaDateTime, now)
                applyDurationText(holderIsha.elapsed, elapsed)
            }
        }

        // Priere suivante : couleur differente + gras + temps restant a droite, vert
        if (nextIndex >= 0) {
            val (_, t, h) = prayers[nextIndex]
            val remaining = Duration.between(nowTime, t)
            h.time.setTextColor(getColor(R.color.next_prayer_color))
            h.time.setTypeface(null, android.graphics.Typeface.BOLD)
            applyDurationText(h.remaining, remaining)
        } else if (prevIndex == prayers.size - 1) {
            // Apres Isha (et avant minuit) : la "suivante" est le Fajr du lendemain
            val tomorrow = repo.computeDay(displayedDate.plusDays(1), prefs.villeId)
            tomorrow.fajr?.let { fajrStr ->
                val fajrDateTime = LocalDateTime.of(displayedDate.plusDays(1), LocalTime.parse(fajrStr))
                val remaining = Duration.between(now, fajrDateTime)
                holderFajr.time.setTextColor(getColor(R.color.next_prayer_color))
                holderFajr.time.setTypeface(null, android.graphics.Typeface.BOLD)
                applyDurationText(holderFajr.remaining, remaining)
            }
        }
    }

    /**
     * Point 3 : formatage adaptatif de la duree, avec taille de police agrandie quand
     * la chaine est plus courte (donc plus de place disponible pour l'agrandir) :
     *  - moins de 10 minutes -> un seul chiffre "m"            (le plus grand)
     *  - moins d'1 heure (>= 10 min) -> "mm"                   (grand)
     *  - moins de 10 heures -> "h:mm"                          (moyen)
     *  - 10 heures ou plus -> "hh:mm"                          (normal, le plus petit des 4)
     */
    private fun applyDurationText(tv: TextView, d: Duration) {
        val totalSeconds = d.seconds.coerceAtLeast(0)
        val totalMinutes = totalSeconds / 60
        val h = totalMinutes / 60
        val m = totalMinutes % 60

        val text: String
        val sizeSp: Float
        when {
            h == 0L && m < 10 -> { text = "$m"; sizeSp = 34f }
            h == 0L -> { text = "%02d".format(m); sizeSp = 30f }
            h < 10 -> { text = "$h:%02d".format(m); sizeSp = 22f }
            else -> { text = "%02d:%02d".format(h, m); sizeSp = 18f }
        }
        tv.text = text
        tv.textSize = sizeSp
    }

    private fun maybeRequestBatteryExemption() {
        if (!prefs.batteryPromptShown) {
            startActivity(Intent(this, BatteryPermissionActivity::class.java))
        }
    }

    private fun maybeRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val granted = android.os.Environment.isExternalStorageManager()
            if (!granted && !prefs.storagePromptShown) {
                startActivity(Intent(this, StoragePermissionActivity::class.java))
            }
        }
    }

    private fun ensureExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                } catch (e: Exception) {
                    // certains OEM n'ont pas cet ecran, ignorer
                }
            }
        }
    }
}
