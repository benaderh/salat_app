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
 */
class AlarmActivity : AppCompatActivity() {

    private val playbackStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // L'audio s'est termine tout seul (ou a ete arrete ailleurs) : fermer cet ecran
            if (!isFinishing) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        applyImmersiveFullscreen()
        setContentView(R.layout.activity_alarm)

        val isBefore = intent.getBooleanExtra(EXTRA_IS_BEFORE, false)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: ""

        findViewById<TextView>(R.id.tvAlarmSubtitle).text =
            if (isBefore) getString(R.string.alarm_subtitle_before) else getString(R.string.alarm_subtitle_at_time)
        findViewById<TextView>(R.id.tvAlarmPrayerName).text = label

        findViewById<View>(android.R.id.content).setOnClickListener { stopAndClose() }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(AthanPlaybackService.ACTION_PLAYBACK_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackStoppedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(playbackStoppedReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(playbackStoppedReceiver)
        } catch (_: Exception) {
        }
    }

    private fun showOverLockScreen() {
        // Garantit l'affichage par-dessus l'ecran verrouille ET par-dessus toute autre app
        // au premier plan (utilisation normale du telephone au moment de l'alarme).
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
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        km?.requestDismissKeyguard(this, null)
    }

    private fun applyImmersiveFullscreen() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private fun stopAndClose() {
        startService(Intent(this, AthanPlaybackService::class.java).apply {
            action = AthanPlaybackService.ACTION_STOP
        })
        finish()
    }

    override fun onBackPressed() {
        // Empeche de fermer l'ecran d'alarme sans arreter le son via le bouton retour
        stopAndClose()
    }

    companion object {
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_IS_BEFORE = "extra_is_before"
    }
}
