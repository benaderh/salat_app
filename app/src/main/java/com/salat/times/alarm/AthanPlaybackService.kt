package com.salat.times.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.salat.times.AlarmActivity
import com.salat.times.R
import com.salat.times.SalatApp
import java.io.File

/**
 * Service foreground qui joue le son d'alarme et affiche l'ecran noir via fullScreenIntent.
 *
 * Sur Android 10+ (API 29+), le lancement direct d'Activity depuis un service en arriere-plan
 * est bloque par le systeme. La seule solution garantie est le "fullScreenIntent" attache
 * a la notification foreground : Android affiche alors AlarmActivity immediatement, que le
 * telephone soit :
 *   - en veille (ecran eteint)  -> reveille l'ecran et affiche AlarmActivity par-dessus le lock
 *   - utilise par une autre app -> affiche AlarmActivity en plein ecran par-dessus tout
 *   - app deja ouverte          -> idem
 *
 * Recoit ACTION_STOP (depuis AlarmActivity au clic ou a la fin du son) pour s'arreter.
 */
class AthanPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: android.media.AudioFocusRequest? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        val label = intent?.getStringExtra(EXTRA_LABEL) ?: ""
        val soundPath = intent?.getStringExtra(EXTRA_SOUND_PATH)
        val isBefore = intent?.getBooleanExtra(EXTRA_IS_BEFORE, false) ?: false
        val volume = intent?.getFloatExtra(EXTRA_VOLUME, 1.0f) ?: 1.0f

        acquireWakeLock()

        // S'assurer que le stream ALARM est au volume maximum du systeme
        // pour que notre controle relatif via MediaPlayer.setVolume() soit effectif
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
        } catch (_: Exception) { }

        val notification = buildFullScreenNotification(label, isBefore)
        startForeground(NOTIF_ID, notification)

        // Declenche explicitement le fullScreenIntent via NotificationManager
        // (startForeground seul ne garantit pas le fullscreen sur tous les OEM)
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) { }

        // Le son passe en PREMIER : c'est le plus important, il ne doit jamais attendre
        // l'affichage de l'ecran d'alarme.
        playSound(soundPath, volume)

        // Fallback : lancer AlarmActivity directement si le fullScreenIntent
        // risque de ne pas fonctionner (Android 14+ sans permission, ou OEM restrictif)
        launchAlarmActivityDirect(label, isBefore)
        return START_NOT_STICKY
    }

    /**
     * Construit la notification foreground avec un fullScreenIntent pointant vers AlarmActivity.
     * Le fullScreenIntent est l'API officielle Android pour "afficher un ecran par-dessus tout,
     * meme depuis un service en arriere-plan, meme ecran verrouille".
     */
    private fun buildFullScreenNotification(label: String, isBefore: Boolean): Notification {
        val title = if (isBefore) "اقترب وقت $label" else "حان وقت $label"

        val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_LABEL, label)
            putExtra(AlarmActivity.EXTRA_IS_BEFORE, isBefore)
        }

        // fullScreenIntent : Android l'utilise pour reveiller l'ecran et afficher l'Activity
        val fullScreenPi = PendingIntent.getActivity(
            this,
            NOTIF_ID,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // contentIntent : si l'utilisateur tape sur la notification (au lieu d'interagir
        // avec AlarmActivity), on ouvre quand meme AlarmActivity
        val contentPi = PendingIntent.getActivity(
            this,
            NOTIF_ID + 1,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action "Stop" : permet d'arreter l'alarme directement depuis la notification
        // meme si AlarmActivity ne s'affiche pas (fallback critique)
        val stopIntent = Intent(this, AthanPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this,
            NOTIF_ID + 2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SalatApp.CHANNEL_ATHAN)
            .setContentTitle(title)
            .setContentText(getString(R.string.notif_tap_to_stop))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(contentPi)
            .setFullScreenIntent(fullScreenPi, true) // true = haute priorite, affiche meme si DND
            .addAction(
                android.R.drawable.ic_delete,
                getString(R.string.stop_alarm),
                stopPi
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // visible sur l'ecran verrouille
            .setOngoing(true) // non effacable par swipe pendant la sonnerie
            .setAutoCancel(false)
            .build()
    }

    /**
     * Fallback : lance AlarmActivity directement depuis le service.
     * Sur Android 10-13, le fullScreenIntent devrait suffire, mais certains OEM le bloquent.
     * Sur Android 14+, USE_FULL_SCREEN_INTENT peut ne pas etre accorde.
     * Dans tous les cas, tenter un lancement direct ne fait pas de mal :
     * si l'Activity est deja visible (via fullScreenIntent), FLAG_CLEAR_TOP
     * la reutilisera sans la dupliquer.
     */
    private fun launchAlarmActivityDirect(label: String, isBefore: Boolean) {
        try {
            val directIntent = Intent(this, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(AlarmActivity.EXTRA_LABEL, label)
                putExtra(AlarmActivity.EXTRA_IS_BEFORE, isBefore)
            }
            startActivity(directIntent)
        } catch (_: Exception) {
            // Sur Android 10+ en arriere-plan, le systeme peut bloquer startActivity.
            // Ce n'est pas grave : le fullScreenIntent de la notification prendra le relais.
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or          // reveille l'ecran (affichage)
            PowerManager.ACQUIRE_CAUSES_WAKEUP or   // force le reveil meme si l'ecran est eteint
            PowerManager.ON_AFTER_RELEASE,          // garde l'ecran allume apres liberation du lock
            "SalatTimes:AthanWakeLock"
        ).apply { acquire(5 * 60 * 1000L) } // max 5 min de securite
    }

    /**
     * Joue le son d'alarme avec une cascade de secours :
     *   1) le fichier mp3 choisi par l'utilisateur
     *   2) la sonnerie d'alarme par defaut du telephone
     *   3) la sonnerie de notification, puis la sonnerie d'appel
     * Avant, si le fichier mp3 etait illisible (permission de stockage refusee, fichier
     * deplace...), le service s'arretait en silence : l'alarme "ne sonnait pas".
     */
    private fun playSound(soundPath: String?, volume: Float) {
        requestAudioFocus()

        val sources = mutableListOf<(MediaPlayer) -> Unit>()
        if (!soundPath.isNullOrBlank() && File(soundPath).exists()) {
            sources.add { it.setDataSource(soundPath) }
        }
        for (type in intArrayOf(
            android.media.RingtoneManager.TYPE_ALARM,
            android.media.RingtoneManager.TYPE_NOTIFICATION,
            android.media.RingtoneManager.TYPE_RINGTONE
        )) {
            val uri = try {
                android.media.RingtoneManager.getActualDefaultRingtoneUri(this, type)
            } catch (_: Exception) { null }
            if (uri != null) {
                sources.add { it.setDataSource(this, uri) }
            }
        }

        for (setSource in sources) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setSource(mp)
                mp.isLooping = false
                // Appliquer le volume relatif choisi par l'utilisateur (0.0 – 1.0)
                mp.setVolume(volume, volume)
                mp.setOnCompletionListener { stopSelfSafely() }
                mp.setOnErrorListener { _, _, _ -> stopSelfSafely(); true }
                mp.prepare()
                mp.start()
                mediaPlayer = mp
                return
            } catch (e: Exception) {
                try { mp.release() } catch (_: Exception) { }
            }
        }
        // Aucune source n'a pu etre lue
        stopSelfSafely()
    }

    /** Demande le focus audio (alarme) : met en pause la musique/video en cours. */
    private fun requestAudioFocus() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val req = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { }
                .build()
            am.requestAudioFocus(req)
            focusRequest = req
        } catch (_: Exception) { }
    }

    private fun abandonAudioFocus() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } catch (_: Exception) { }
        focusRequest = null
    }

    private fun stopSelfSafely() {
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) { }
        mediaPlayer = null
        abandonAudioFocus()
        wakeLock?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Annule aussi la notification qui persistait (ongoing=true)
        try { NotificationManagerCompat.from(this).cancel(NOTIF_ID) } catch (_: Exception) { }
        sendBroadcast(Intent(ACTION_PLAYBACK_STOPPED).setPackage(packageName))
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) { }
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.salat.times.ACTION_STOP_ATHAN"
        const val ACTION_PLAYBACK_STOPPED = "com.salat.times.ACTION_PLAYBACK_STOPPED"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_SOUND_PATH = "extra_sound_path"
        const val EXTRA_IS_BEFORE = "extra_is_before"
        const val EXTRA_VOLUME = "extra_volume"
        private const val NOTIF_ID = 7711
    }
}
