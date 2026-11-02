package com.salat.times

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.salat.times.data.PrefsManager

/**
 * Demande explicitement la permission MANAGE_EXTERNAL_STORAGE (Android 11+), indispensable
 * pour lire /storage/emulated/0/SalatAthan/salat_data.json (fichier hors des dossiers
 * media standards). La simple declaration dans le Manifest ne suffit pas : il faut
 * rediriger l'utilisateur vers l'ecran systeme dedie pour qu'il l'active manuellement.
 */
class StoragePermissionActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sur Android < 11, ou si deja accordee, on ne montre rien
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            finish()
            return
        }

        setContentView(R.layout.activity_storage_permission)
        prefs = PrefsManager(this)

        findViewById<android.widget.Button>(R.id.btnGrantStorage).setOnClickListener {
            requestManageStorage()
        }
        findViewById<android.widget.Button>(R.id.btnSkipStorage).setOnClickListener {
            prefs.storagePromptShown = true
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Si l'utilisateur revient de l'ecran systeme avec la permission accordee, on ferme directement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            prefs.storagePromptShown = true
            finish()
        }
    }

    private fun requestManageStorage() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
            }
        }
        prefs.storagePromptShown = true
    }
}
