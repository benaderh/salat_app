package com.salat.times

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.salat.times.data.PrefsManager

/**
 * Ecran de demande des permissions critiques au premier lancement, en 2 etapes :
 *  1) Exemption d'optimisation batterie -> indispensable pour que
 *     AlarmManager.setExactAndAllowWhileIdle() declenche les alarmes a l'heure exacte
 *     meme quand le telephone est en veille profonde (Doze).
 *  2) Acces "gestion de tous les fichiers" (MANAGE_EXTERNAL_STORAGE) -> indispensable
 *     pour lire les mp3 et le salat_data.json dans /storage/emulated/0/SalatAthan/.
 *     Sur Android 11+ cette permission ne peut PAS etre accordee par une simple boite
 *     de dialogue : elle necessite cet ecran systeme dedie.
 */
class BatteryPermissionActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private var step = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery_permission)
        prefs = PrefsManager(this)
        showStep1()
    }

    override fun onResume() {
        super.onResume()
        // Si on revient de l'ecran systeme de permission stockage, on verifie l'etat
        if (step == 2 && hasStoragePermission()) {
            prefs.batteryPromptShown = true
            finish()
        }
    }

    private fun showStep1() {
        step = 1
        findViewById<TextView>(R.id.tvTitle).text = getString(R.string.battery_perm_title)
        findViewById<TextView>(R.id.tvBody).text = getString(R.string.battery_perm_body)
        val btnGrant = findViewById<Button>(R.id.btnGrant)
        val btnSkip = findViewById<Button>(R.id.btnSkip)
        btnGrant.text = getString(R.string.battery_perm_grant)
        btnGrant.setOnClickListener { requestIgnoreBatteryOptimizations() }
        btnSkip.setOnClickListener { showStep2() }
    }

    private fun showStep2() {
        step = 2
        if (hasStoragePermission()) {
            prefs.batteryPromptShown = true
            finish()
            return
        }
        findViewById<TextView>(R.id.tvTitle).text = getString(R.string.storage_perm_title)
        findViewById<TextView>(R.id.tvBody).text = getString(R.string.storage_perm_body)
        val btnGrant = findViewById<Button>(R.id.btnGrant)
        val btnSkip = findViewById<Button>(R.id.btnSkip)
        btnGrant.text = getString(R.string.battery_perm_grant)
        btnGrant.setOnClickListener { requestStoragePermission() }
        btnSkip.setOnClickListener {
            prefs.batteryPromptShown = true
            finish()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // permissions classiques deja gerees via le manifest sur les versions plus anciennes
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
        showStep2()
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    prefs.batteryPromptShown = true
                    finish()
                }
            }
        } else {
            prefs.batteryPromptShown = true
            finish()
        }
        // Le retour de cet ecran declenche onResume() qui verifie et termine si accorde
    }
}
