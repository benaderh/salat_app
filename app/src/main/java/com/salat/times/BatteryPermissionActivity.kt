package com.salat.times

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.salat.times.data.PrefsManager

/**
 * Demande l'exemption d'optimisation de la batterie : indispensable pour que
 * AlarmManager.setExactAndAllowWhileIdle() declenche les alarmes a l'heure exacte
 * meme quand le telephone est en veille profonde (Doze).
 */
class BatteryPermissionActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery_permission)
        prefs = PrefsManager(this)

        findViewById<android.widget.Button>(R.id.btnGrant).setOnClickListener {
            requestIgnoreBatteryOptimizations()
        }
        findViewById<android.widget.Button>(R.id.btnSkip).setOnClickListener {
            prefs.batteryPromptShown = true
            finish()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Certains OEM (Xiaomi, Huawei...) bloquent cet intent : rediriger vers les
                    // parametres generaux de batterie de l'app en secours.
                    try {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    } catch (_: Exception) {
                    }
                }
            }
        }
        prefs.batteryPromptShown = true
        finish()
    }
}
