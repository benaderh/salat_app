package com.salat.times.alarm

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Petit journal des alarmes (prevu vs recu vs son demarre) pour diagnostiquer les retards.
 * Stocke dans le stockage interne de l'app (aucune permission), limite a ~200 lignes.
 * Consultable depuis le menu de l'app ("سجل التنبيهات").
 */
object AlarmLog {
    private const val FILE = "alarm_log.txt"
    private const val MAX_LINES = 200
    private val FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")

    @Synchronized
    fun add(context: Context, message: String) {
        try {
            val f = File(context.filesDir, FILE)
            val line = "${LocalDateTime.now().format(FMT)}  $message"
            val lines = if (f.exists()) f.readLines().takeLast(MAX_LINES - 1) else emptyList()
            f.writeText((lines + line).joinToString("\n") + "\n")
        } catch (_: Exception) {
            // le journal ne doit jamais perturber une alarme
        }
    }

    @Synchronized
    fun read(context: Context, lastLines: Int = 60): String {
        return try {
            val f = File(context.filesDir, FILE)
            if (!f.exists()) "(vide)" else f.readLines().takeLast(lastLines).reversed().joinToString("\n")
        } catch (_: Exception) {
            "(illisible)"
        }
    }

    @Synchronized
    fun clear(context: Context) {
        try { File(context.filesDir, FILE).delete() } catch (_: Exception) { }
    }
}
