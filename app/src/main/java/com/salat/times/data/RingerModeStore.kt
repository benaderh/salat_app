package com.salat.times.data

import android.content.Context
import android.media.AudioManager

class RingerModeStore(context: Context) {
    private val prefs = context.getSharedPreferences("ringer_mode_store", Context.MODE_PRIVATE)

    fun savePreviousMode(mode: Int) {
        // Ne pas ecraser si deja en silencieux suite a une autre priere proche dans le temps
        if (prefs.getInt(KEY_MODE, -1) == -1) {
            prefs.edit().putInt(KEY_MODE, mode).apply()
        }
    }

    fun getPreviousMode(): Int {
        val mode = prefs.getInt(KEY_MODE, AudioManager.RINGER_MODE_NORMAL)
        prefs.edit().remove(KEY_MODE).apply()
        return mode
    }

    companion object {
        private const val KEY_MODE = "previous_ringer_mode"
    }
}
