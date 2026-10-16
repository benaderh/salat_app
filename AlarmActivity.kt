package com.salat.times

import android.app.KeyguardManager
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
 * Strategie d'affichage sur ecran verrouille :
 *  1) setShowWhenLocked(true)     -> affiche l'activity PAR-DESSUS le lock screen
 *  2) setTurnScreenOn(true)       -> allume l'ecran si eteint
 *  3) requestDismissKeyguard()    -> demande au systeme de DEVERROUILLER le telephone
 *     pour que l'activity reste visible apres le deverrouillage (au lieu de disparaitre)
 *  4) FLAG_KEEP_SCREEN_ON         -> garde l'ecran allume tant que l'activity est visible
 *  5) FLAG_DISMISS_KEYGUARD       -> fallback pour API < 26
 *
 * Note : on N'utilise PAS le mode immersif sticky (SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
 * qui sur certains appareils bloque le tactile apres un reveil d'ecran verrouille.
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

        // Bouton Stop dedié, bien visible au centre de l'ecran
        findViewById<View>(R.id.btnStopAlarm).setOnClickListener { stopAndClose() }

        // Demander au systeme de deverrouiller le telephone pour que l'activity reste
        // visible apres le deverrouillage. Sans cela, l'activity s'affiche par-dessus
        // le lock screen mais disparait quand l'utilisateur swipe pour deverrouiller.
        dismissKeyguard()
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
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Demande au systeme de deverrouiller le telephone.
     * Sur API 26+ : utilise KeyguardManager.requestDismissKeyguard() qui affiche
     * le prompt de deverrouillage (PIN/pattern/fingerprint) si necessaire, puis
     * garde l'activity visible une fois deverrouille.
     * Sur API < 26 : FLAG_DISMISS_KEYGUARD (ajoute dans showOverLockScreen) suffit.
     */
    private fun dismissKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    // Keyguard deverrouille avec succes, l'activity reste visible
                }
                override fun onDismissError() {
                    // Erreur de deverrouillage, ignorer
                }
                override fun onDismissCancelled() {
                    // L'utilisateur a annule le deverrouillage, ignorer
                }
            })
        }
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
