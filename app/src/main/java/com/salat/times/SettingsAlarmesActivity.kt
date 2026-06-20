package com.salat.times

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
        val switchBefore: Switch,
        val etBeforeMinutes: EditText,
        val btnBeforeSound: Button,
        val switchAtTime: Switch,
        val btnAtTimeSound: Button,
        val switchSilent: Switch,
        val etSilentDuration: EditText,
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

            val tvLabel = cardView.findViewById<TextView>(R.id.tvPrayerLabel)
            val switchBefore = cardView.findViewById<Switch>(R.id.switchBefore)
            val etBeforeMinutes = cardView.findViewById<EditText>(R.id.etBeforeMinutes)
            val btnBeforeSound = cardView.findViewById<Button>(R.id.btnBeforeSound)
            val switchAtTime = cardView.findViewById<Switch>(R.id.switchAtTime)
            val btnAtTimeSound = cardView.findViewById<Button>(R.id.btnAtTimeSound)
            val switchSilent = cardView.findViewById<Switch>(R.id.switchSilent)
            val etSilentDuration = cardView.findViewById<EditText>(R.id.etSilentDuration)

            tvLabel.text = label

            val cfg = prefs.getAlarmConfig(key)
            switchBefore.isChecked = cfg.beforeEnabled
            etBeforeMinutes.setText(cfg.beforeMinutes.toString())
            switchAtTime.isChecked = cfg.atTimeEnabled
            switchSilent.isChecked = cfg.silentEnabled
            etSilentDuration.setText(cfg.silentDurationMinutes.toString())

            val holder = CardHolder(
                key = key,
                switchBefore = switchBefore,
                etBeforeMinutes = etBeforeMinutes,
                btnBeforeSound = btnBeforeSound,
                switchAtTime = switchAtTime,
                btnAtTimeSound = btnAtTimeSound,
                switchSilent = switchSilent,
                etSilentDuration = etSilentDuration,
                beforeSoundPath = cfg.beforeSoundPath,
                atTimeSoundPath = cfg.atTimeSoundPath
            )
            cards.add(holder)

            btnBeforeSound.text = shortName(cfg.beforeSoundPath)
            btnAtTimeSound.text = shortName(cfg.atTimeSoundPath)

            switchSilent.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) ensureDndAccess()
            }

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
            File(path).name
        } catch (e: Exception) {
            getString(R.string.select_mp3)
        }
    }

    private fun saveAll() {
        for (card in cards) {
            val beforeMinutes = card.etBeforeMinutes.text.toString().toIntOrNull() ?: 10
            val silentDuration = card.etSilentDuration.text.toString().toIntOrNull() ?: 15
            val config = PrayerAlarmConfig(
                beforeEnabled = card.switchBefore.isChecked,
                beforeMinutes = beforeMinutes.coerceIn(1, 180),
                beforeSoundPath = card.beforeSoundPath,
                atTimeEnabled = card.switchAtTime.isChecked,
                atTimeSoundPath = card.atTimeSoundPath,
                silentEnabled = card.switchSilent.isChecked,
                silentDurationMinutes = silentDuration.coerceIn(1, 180)
            )
            prefs.setAlarmConfig(card.key, config)
        }
        AlarmScheduler.rescheduleAll(this)
        Toast.makeText(this, getString(R.string.save), Toast.LENGTH_SHORT).show()
        finish()
    }
}
