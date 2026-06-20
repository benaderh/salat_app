package com.salat.times.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.salat.times.MainActivity
import com.salat.times.R
import com.salat.times.SalatApp
import java.io.File

class AthanPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: ""
        val soundPath = intent?.getStringExtra(EXTRA_SOUND_PATH)
        val isBefore = intent?.getBooleanExtra(EXTRA_IS_BEFORE, false) ?: false

        acquireWakeLock()
        startForeground(NOTIF_ID, buildNotification(label, isBefore))
        playSound(soundPath)

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "SalatTimes:AthanWakeLock"
        ).apply { acquire(5 * 60 * 1000L) } // max 5 min de securite
    }

    private fun buildNotification(label: String, isBefore: Boolean): Notification {
        val title = if (isBefore) "اقترب وقت $label" else "حان وقت $label"
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SalatApp.CHANNEL_ATHAN)
            .setContentTitle(title)
            .setContentText("مواقيت الصلاة")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }

    private fun playSound(soundPath: String?) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                if (!soundPath.isNullOrBlank() && File(soundPath).exists()) {
                    setDataSource(soundPath)
                } else {
                    // Pas de fichier choisi : utiliser la sonnerie d'alarme systeme par defaut
                    val defaultUri = android.media.RingtoneManager.getActualDefaultRingtoneUri(
                        this@AthanPlaybackService, android.media.RingtoneManager.TYPE_ALARM
                    )
                    if (defaultUri != null) {
                        setDataSource(this@AthanPlaybackService, defaultUri)
                    }
                }
                isLooping = false
                setOnCompletionListener { stopSelfSafely() }
                setOnErrorListener { _, _, _ -> stopSelfSafely(); true }
                prepare()
                start()
            }
        } catch (e: Exception) {
            stopSelfSafely()
        }
    }

    private fun stopSelfSafely() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        wakeLock?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_SOUND_PATH = "extra_sound_path"
        const val EXTRA_IS_BEFORE = "extra_is_before"
        private const val NOTIF_ID = 7711
    }
}
