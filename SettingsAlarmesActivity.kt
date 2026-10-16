package com.salat.times

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.salat.times.alarm.AlarmScheduler
import com.salat.times.data.PrayerAlarmConfig
import com.salat.times.data.PrefsManager
import com.salat.times.model.PrayerKey
import java.io.File

class SettingsAlarmesActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    private data class CardHolder(
        val key: PrayerKey,
        // Section 1 : avant l'adhan
        val switchBefore: Switch,
        val sectionBeforeDetails: View,
        val tvBeforeMinutes: TextView,
        val btnBeforeMinus: View,
        val btnBeforePlus: View,
        val btnBeforeSound: Button,
        val seekBarBeforeVolume: SeekBar,
        // Section 2 : adhan
        val switchAtTime: Switch,
        val sectionAtTimeDetails: View,
        val btnAtTimeSound: Button,
        val seekBarAtTimeVolume: SeekBar,
        // Section 3 : mode silencieux
        val switchSilent: Switch,
        val sectionSilentDetails: View,
        val tvSilentDelay: TextView,
        val btnSilentDelayMinus: View,
        val btnSilentDelayPlus: View,
        val tvSilentDuration: TextView,
        val btnSilentDurationMinus: View,
        val btnSilentDurationPlus: View,
        // Donnees
        var beforeMinutes: Int,
        var silentDelay: Int,
        var silentDuration: Int,
        var beforeSoundPath: String?,
        var atTimeSoundPath: String?
    )

    private val cards = mutableListOf<CardHolder>()
    private var pendingSoundTargetIsBefore = true
    private var pendingSoundTargetKey: PrayerKey? = null

    private val soundPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra(SoundPickerActivity.EXTRA_SELECTED_PATH)
            val key = pendingSoundTargetKey ?: return@registerForActivityResult
            val card = cards.find { it.key == key } ?: return@registerForActivityResult
            if (pendingSoundTargetIsBefore) {
                card.beforeSoundPath = path
                card.btnBeforeSound.text = shortName(path)
            } else {
                card.atTimeSoundPath = path
                card.btnAtTimeSound.text = shortName(path)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_alarmes)
        prefs = PrefsManager(this)

        val container = findViewById<LinearLayout>(R.id.containerCards)
        val inflater = layoutInflater

        val prayerDefs = listOf(
            PrayerKey.FAJR to getString(R.string.fajr),
            PrayerKey.DHOHR to getString(R.string.dhohr),
            PrayerKey.ASR to getString(R.string.asr),
            PrayerKey.MAGHREB to getString(R.string.maghreb),
            PrayerKey.ISHA to getString(R.string.isha)
        )

        for ((key, label) in prayerDefs) {
            val cardView = inflater.inflate(R.layout.item_prayer_alarm_card, container, false)
            container.addView(cardView)

            val cfg = prefs.getAlarmConfig(key)

            val tvLabel = cardView.findViewById<TextView>(R.id.tvPrayerLabel)
            tvLabel.text = label

            // Section 1 widgets
            val switchBefore = cardView.findViewById<Switch>(R.id.switchBefore)
            val sectionBeforeDetails = cardView.findViewById<View>(R.id.sectionBeforeDetails)
            val tvBeforeMinutes = cardView.findViewById<TextView>(R.id.tvBeforeMinutes)
            val btnBeforeMinus = cardView.findViewById<View>(R.id.btnBeforeMinusMinus)
            val btnBeforePlus = cardView.findViewById<View>(R.id.btnBeforePlus)
            val btnBeforeSound = cardView.findViewById<Button>(R.id.btnBeforeSound)
            val seekBarBeforeVolume = cardView.findViewById<SeekBar>(R.id.seekBarBeforeVolume)

            // Section 2 widgets
            val switchAtTime = cardView.findViewById<Switch>(R.id.switchAtTime)
            val sectionAtTimeDetails = cardView.findViewById<View>(R.id.sectionAtTimeDetails)
            val btnAtTimeSound = cardView.findViewById<Button>(R.id.btnAtTimeSound)
            val seekBarAtTimeVolume = cardView.findViewById<SeekBar>(R.id.seekBarAtTimeVolume)

            // Section 3 widgets
            val switchSilent = cardView.findViewById<Switch>(R.id.switchSilent)
            val sectionSilentDetails = cardView.findViewById<View>(R.id.sectionSilentDetails)
            val tvSilentDelay = cardView.findViewById<TextView>(R.id.tvSilentDelay)
            val btnSilentDelayMinus = cardView.findViewById<View>(R.id.btnSilentDelayMinus)
            val btnSilentDelayPlus = cardView.findViewById<View>(R.id.btnSilentDelayPlus)
            val tvSilentDuration = cardView.findViewById<TextView>(R.id.tvSilentDuration)
            val btnSilentDurationMinus = cardView.findViewById<View>(R.id.btnSilentDurationMinus)
            val btnSilentDurationPlus = cardView.findViewById<View>(R.id.btnSilentDurationPlus)

            // Valeurs initiales
            val beforeMin = cfg.beforeMinutes.coerceIn(5, 60)
            val silentDelay = cfg.silentDelayMinutes.coerceIn(1, 15)
            val silentDur = cfg.silentDurationMinutes.coerceIn(16, 120)

            switchBefore.isChecked = cfg.beforeEnabled
            tvBeforeMinutes.text = beforeMin.toString()
            btnBeforeSound.text = shortName(cfg.beforeSoundPath)
            seekBarBeforeVolume.progress = (cfg.beforeVolume * 100).toInt()

            switchAtTime.isChecked = cfg.atTimeEnabled
            btnAtTimeSound.text = shortName(cfg.atTimeSoundPath)
            seekBarAtTimeVolume.progress = (cfg.atTimeVolume * 100).toInt()

            switchSilent.isChecked = cfg.silentEnabled
            tvSilentDelay.text = silentDelay.toString()
            tvSilentDuration.text = silentDur.toString()

            // Visibilite initiale
            sectionBeforeDetails.visibility = if (cfg.beforeEnabled) View.VISIBLE else View.GONE
            sectionAtTimeDetails.visibility = if (cfg.atTimeEnabled) View.VISIBLE else View.GONE
            sectionSilentDetails.visibility = if (cfg.silentEnabled) View.VISIBLE else View.GONE

            val holder = CardHolder(
                key = key,
                switchBefore = switchBefore,
                sectionBeforeDetails = sectionBeforeDetails,
                tvBeforeMinutes = tvBeforeMinutes,
                btnBeforeMinus = btnBeforeMinus,
                btnBeforePlus = btnBeforePlus,
                btnBeforeSound = btnBeforeSound,
                seekBarBeforeVolume = seekBarBeforeVolume,
                switchAtTime = switchAtTime,
                sectionAtTimeDetails = sectionAtTimeDetails,
                btnAtTimeSound = btnAtTimeSound,
                seekBarAtTimeVolume = seekBarAtTimeVolume,
                switchSilent = switchSilent,
                sectionSilentDetails = sectionSilentDetails,
                tvSilentDelay = tvSilentDelay,
                btnSilentDelayMinus = btnSilentDelayMinus,
                btnSilentDelayPlus = btnSilentDelayPlus,
                tvSilentDuration = tvSilentDuration,
                btnSilentDurationMinus = btnSilentDurationMinus,
                btnSilentDurationPlus = btnSilentDurationPlus,
                beforeMinutes = beforeMin,
                silentDelay = silentDelay,
                silentDuration = silentDur,
                beforeSoundPath = cfg.beforeSoundPath,
                atTimeSoundPath = cfg.atTimeSoundPath
            )
            cards.add(holder)

            // ── Listeners de visibilite ──

            switchBefore.setOnCheckedChangeListener { _, isChecked ->
                sectionBeforeDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
            switchAtTime.setOnCheckedChangeListener { _, isChecked ->
                sectionAtTimeDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
            switchSilent.setOnCheckedChangeListener { _, isChecked ->
                sectionSilentDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
                if (isChecked) ensureDndAccess()
            }

            // ── Boutons +/- pour المدة (avant l'adhan, 5–60) ──

            btnBeforeMinus.setOnClickListener {
                if (holder.beforeMinutes > 5) {
                    holder.beforeMinutes--
                    tvBeforeMinutes.text = holder.beforeMinutes.toString()
                }
            }
            btnBeforePlus.setOnClickListener {
                if (holder.beforeMinutes < 60) {
                    holder.beforeMinutes++
                    tvBeforeMinutes.text = holder.beforeMinutes.toString()
                }
            }

            // ── Boutons +/- pour بدأ بعد الأذان بـ (1–15) ──

            btnSilentDelayMinus.setOnClickListener {
                if (holder.silentDelay > 1) {
                    holder.silentDelay--
                    tvSilentDelay.text = holder.silentDelay.toString()
                }
            }
            btnSilentDelayPlus.setOnClickListener {
                if (holder.silentDelay < 15) {
                    holder.silentDelay++
                    tvSilentDelay.text = holder.silentDelay.toString()
                }
            }

            // ── Boutons +/- pour مدة الوضع الصامت (16–120) ──

            btnSilentDurationMinus.setOnClickListener {
                if (holder.silentDuration > 16) {
                    holder.silentDuration--
                    tvSilentDuration.text = holder.silentDuration.toString()
                }
            }
            btnSilentDurationPlus.setOnClickListener {
                if (holder.silentDuration < 120) {
                    holder.silentDuration++
                    tvSilentDuration.text = holder.silentDuration.toString()
                }
            }

            // ── Selection audio ──

            btnBeforeSound.setOnClickListener {
                pendingSoundTargetIsBefore = true
                pendingSoundTargetKey = key
                soundPickerLauncher.launch(Intent(this, SoundPickerActivity::class.java))
            }
            btnAtTimeSound.setOnClickListener {
                pendingSoundTargetIsBefore = false
                pendingSoundTargetKey = key
                soundPickerLauncher.launch(Intent(this, SoundPickerActivity::class.java))
            }
        }

        findViewById<Button>(R.id.btnSaveAll).setOnClickListener { saveAll() }
    }

    private fun ensureDndAccess() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                Toast.makeText(
                    this,
                    getString(R.string.silent_mode) + " : " + getString(R.string.battery_perm_grant),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                // ecran non disponible sur cet OEM, ignorer
            }
        }
    }

    private fun shortName(path: String?): String {
        if (path.isNullOrBlank()) return getString(R.string.select_mp3)
        return try {
            File(path).nameWithoutExtension
        } catch (e: Exception) {
            getString(R.string.select_mp3)
        }
    }

    private fun saveAll() {
        for (card in cards) {
            val config = PrayerAlarmConfig(
                beforeEnabled = card.switchBefore.isChecked,
                beforeMinutes = card.beforeMinutes.coerceIn(5, 60),
                beforeSoundPath = card.beforeSoundPath,
                beforeVolume = card.seekBarBeforeVolume.progress / 100f,
                atTimeEnabled = card.switchAtTime.isChecked,
                atTimeSoundPath = card.atTimeSoundPath,
                atTimeVolume = card.seekBarAtTimeVolume.progress / 100f,
                silentEnabled = card.switchSilent.isChecked,
                silentDelayMinutes = card.silentDelay.coerceIn(1, 15),
                silentDurationMinutes = card.silentDuration.coerceIn(16, 120)
            )
            prefs.setAlarmConfig(card.key, config)
        }
        AlarmScheduler.rescheduleAll(this)
        Toast.makeText(this, getString(R.string.save), Toast.LENGTH_SHORT).show()
        finish()
    }
}
