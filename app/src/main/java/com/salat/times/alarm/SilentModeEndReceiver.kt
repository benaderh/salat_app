package com.salat.times.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.salat.times.data.RingerModeStore

class SilentModeEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_SILENT_END) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val store = RingerModeStore(context)
        val previous = store.getPreviousMode()
        try {
            am.ringerMode = previous
        } catch (e: SecurityException) {
            // pas d'acces DND, ignorer
        }
    }
}
