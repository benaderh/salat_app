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

    /**
     * Silence "en attente" : l'heure de debut du silence est arrivee alors que l'adhan jouait
     * encore. Le silence sera applique a la fin de l'adhan (et pas avant la fin du son).
     * Stocke l'heure de fin prevue (epoch ms) pour ne jamais activer un silence deja expire.
     */
    fun setPendingSilentUntil(endMillis: Long) {
        prefs.edit().putLong(KEY_PENDING_UNTIL, endMillis).apply()
    }

    /** Retourne l'heure de fin du silence en attente (epoch ms), ou 0 s'il n'y en a pas. */
    fun takePendingSilentUntil(): Long {
        val v = prefs.getLong(KEY_PENDING_UNTIL, 0L)
        prefs.edit().remove(KEY_PENDING_UNTIL).apply()
        return v
    }

    companion object {
        private const val KEY_MODE = "previous_ringer_mode"
        private const val KEY_PENDING_UNTIL = "pending_silent_until"
    }
}
