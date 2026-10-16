package com.salat.times.data

import android.content.Context
import com.salat.times.model.PrayerKey
import org.json.JSONObject

/**
 * Reglages d'alarme pour UNE priere.
 * - beforeEnabled / beforeMinutes / beforeSoundPath / beforeVolume : alarme "avant l'adhan"
 * - atTimeEnabled / atTimeSoundPath / atTimeVolume : alarme "a l'heure de l'adhan"
 * - silentEnabled / silentDelayMinutes / silentDurationMinutes : mode silencieux automatique
 */
data class PrayerAlarmConfig(
    val beforeEnabled: Boolean = false,
    val beforeMinutes: Int = 10,
    val beforeSoundPath: String? = null,
    val beforeVolume: Float = 0.8f,
    val atTimeEnabled: Boolean = true,
    val atTimeSoundPath: String? = null,
    val atTimeVolume: Float = 1.0f,
    val silentEnabled: Boolean = false,
    val silentDelayMinutes: Int = 5,
    val silentDurationMinutes: Int = 30
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("beforeEnabled", beforeEnabled)
        put("beforeMinutes", beforeMinutes)
        put("beforeSoundPath", beforeSoundPath ?: JSONObject.NULL)
        put("beforeVolume", beforeVolume.toDouble())
        put("atTimeEnabled", atTimeEnabled)
        put("atTimeSoundPath", atTimeSoundPath ?: JSONObject.NULL)
        put("atTimeVolume", atTimeVolume.toDouble())
        put("silentEnabled", silentEnabled)
        put("silentDelayMinutes", silentDelayMinutes)
        put("silentDurationMinutes", silentDurationMinutes)
    }

    companion object {
        fun fromJson(o: JSONObject?): PrayerAlarmConfig {
            if (o == null) return PrayerAlarmConfig()
            return PrayerAlarmConfig(
                beforeEnabled = o.optBoolean("beforeEnabled", false),
                beforeMinutes = o.optInt("beforeMinutes", 10),
                beforeSoundPath = if (o.isNull("beforeSoundPath")) null else o.optString("beforeSoundPath"),
                beforeVolume = o.optDouble("beforeVolume", 0.8).toFloat(),
                atTimeEnabled = o.optBoolean("atTimeEnabled", true),
                atTimeSoundPath = if (o.isNull("atTimeSoundPath")) null else o.optString("atTimeSoundPath"),
                atTimeVolume = o.optDouble("atTimeVolume", 1.0).toFloat(),
                silentEnabled = o.optBoolean("silentEnabled", false),
                silentDelayMinutes = o.optInt("silentDelayMinutes", 5),
                silentDurationMinutes = o.optInt("silentDurationMinutes", 30)
            )
        }
    }
}

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("salat_prefs", Context.MODE_PRIVATE)

    var villeId: Int
        get() = prefs.getInt(KEY_VILLE, DEFAULT_VILLE_ID)
        set(value) = prefs.edit().putInt(KEY_VILLE, value).apply()

    var batteryPromptShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_PROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_PROMPT_SHOWN, value).apply()

    var storagePromptShown: Boolean
        get() = prefs.getBoolean(KEY_STORAGE_PROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_STORAGE_PROMPT_SHOWN, value).apply()

    /** true si l'app doit utiliser le JSON importe depuis SalatAthan/ au lieu de l'asset embarque. */
    var useImportedData: Boolean
        get() = prefs.getBoolean(KEY_USE_IMPORTED, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_IMPORTED, value).apply()

    fun getAlarmConfig(key: PrayerKey): PrayerAlarmConfig {
        val raw = prefs.getString(alarmPrefKey(key), null) ?: return PrayerAlarmConfig()
        return PrayerAlarmConfig.fromJson(JSONObject(raw))
    }

    fun setAlarmConfig(key: PrayerKey, config: PrayerAlarmConfig) {
        prefs.edit().putString(alarmPrefKey(key), config.toJson().toString()).apply()
    }

    fun getAllAlarmConfigs(): Map<PrayerKey, PrayerAlarmConfig> =
        PrayerKey.values().associateWith { getAlarmConfig(it) }

    private fun alarmPrefKey(key: PrayerKey) = "alarm_cfg_${key.name}"

    companion object {
        private const val KEY_VILLE = "ville_id"
        private const val KEY_BATTERY_PROMPT_SHOWN = "battery_prompt_shown"
        private const val KEY_STORAGE_PROMPT_SHOWN = "storage_prompt_shown"
        private const val KEY_USE_IMPORTED = "use_imported_data"
        const val DEFAULT_VILLE_ID = 100 // Alger
    }
}
