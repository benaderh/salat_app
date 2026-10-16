package com.salat.times.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.salat.times.data.RingerModeStore

class SilentModeEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_SILENT_END) return

        // Silence encore "en attente" (l'adhan a joue jusqu'a la fin de la periode) :
        // il n'a jamais demarre, donc rien a restaurer.
        // (on verifie que le silence en attente correspond bien a CETTE fin, pas a un vieux reste)
        val pending = RingerModeStore(context).takePendingSilentUntil()
        if (pending > 0 && kotlin.math.abs(System.currentTimeMillis() - pending) < 2 * 60_000L) {
            AlarmLog.add(context, "SILENCE annule : l'adhan a couvert toute la periode")
            return
        }
        SilentMode.end(context)
    }
}
