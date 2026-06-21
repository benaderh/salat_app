package com.salat.times

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.salat.times.alarm.AthanPlaybackService

/**
 * Activite plein ecran noir affichee pendant qu'une alarme de priere sonne.
 * S'affiche meme sur ecran verrouille ou par-dessus une autre app au premier plan.
 * Se ferme dans 2 cas :
 *  - clic n'importe ou sur l'ecran -> arrete le son explicitement, puis ferme
 *  - fin naturelle de l'audio (broadcast ACTION_PLAYBACK_STOPPED depuis le service) -> ferme seule
 *
 * IMPORTANT : cette activite reste volontairement minimaliste. Les anciennes versions
 * combinaient FLAG_DISMISS_KEYGUARD + requestDismissKeyguard() + immersive sticky, ce qui
 * sur certains appareils (notamment au reveil depuis l'ecran verrouille) pouvait rendre
 * la zone tactile insensible jusqu'au redemarrage. On utilise ici uniquement
 * setShowWhenLocked/setTurnScreenOn (l'API recommandee depuis Android 8.1) et on evite
 * tout mode immersif "sticky" qui necessite un swipe avant d'accepter un tap.
 */
class AlarmActivity : AppCompatActivity() {

    private var receiverRegistered = false

    private val playbackStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // L'audio s'est termine tout seul (ou a ete arrete ailleurs) : fermer cet ecran
            if (!isFinishing) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContentView(R.layout.activity_alarm)

        val isBefore = intent.getBooleanExtra(EXTRA_IS_BEFORE, false)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: ""

        findViewById<TextView>(R.id.tvAlarmSubtitle).text =
            if (isBefore) getString(R.string.alarm_subtitle_before) else getString(R.string.alarm_subtitle_at_time)
        findViewById<TextView>(R.id.tvAlarmPrayerName).text = label

        // Le clic est pose sur la racine reelle du layout (pas android.R.id.content, qui est
        // un conteneur systeme intermediaire dont le hit-testing peut etre peu fiable apres
        // un reveil d'ecran verrouille sur certains appareils).
        val root = findViewById<View>(R.id.alarmRoot)
        root.setOnClickListener { stopAndClose() }
        root.isClickable = true
        root.isFocusable = true
    }

    override fun onStart() {
        super.onStart()
        try {
            val filter = IntentFilter(AthanPlaybackService.ACTION_PLAYBACK_STOPPED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(playbackStoppedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(playbackStoppedReceiver, filter)
            }
            receiverRegistered = true
        } catch (e: Exception) {
            receiverRegistered = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (receiverRegistered) {
            try {
                unregisterReceiver(playbackStoppedReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun stopAndClose() {
        try {
            startService(Intent(this, AthanPlaybackService::class.java).apply {
                action = AthanPlaybackService.ACTION_STOP
            })
        } catch (_: Exception) {
        }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Empeche de fermer l'ecran d'alarme sans arreter le son via le bouton retour
        stopAndClose()
    }

    companion object {
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_IS_BEFORE = "extra_is_before"
    }
}
