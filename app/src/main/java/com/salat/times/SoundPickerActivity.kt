package com.salat.times

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Parcourt le dossier /storage/emulated/0/SalatAthan/ et liste les fichiers mp3
 * disponibles pour que l'utilisateur en choisisse un comme son d'alarme.
 */
class SoundPickerActivity : AppCompatActivity() {

    private val soundDir = File(Environment.getExternalStorageDirectory(), "SalatAthan")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sound_picker)
        ensureStoragePermission()
    }

    private fun ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Sur Android 11+, l'acces complet aux fichiers (necessaire pour lire un .json
            // arbitraire hors des dossiers media standards) passe par MANAGE_EXTERNAL_STORAGE,
            // qui doit etre accordee via un ecran systeme dedie (pas une simple popup runtime).
            if (!Environment.isExternalStorageManager()) {
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
                return
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_STORAGE
                )
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.READ_MEDIA_AUDIO), REQ_STORAGE
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSounds()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) loadSounds()
    }

    private fun loadSounds() {
        val listView = findViewById<ListView>(R.id.listSounds)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        val files: List<File> = if (soundDir.exists() && soundDir.isDirectory) {
            soundDir.listFiles { f -> f.isFile && f.extension.lowercase() == "mp3" }
                ?.sortedBy { it.name } ?: emptyList()
        } else {
            emptyList()
        }

        if (files.isEmpty()) {
            tvEmpty.visibility = android.view.View.VISIBLE
            listView.visibility = android.view.View.GONE
            return
        }

        tvEmpty.visibility = android.view.View.GONE
        listView.visibility = android.view.View.VISIBLE

        val names = files.map { it.name }
        listView.adapter = ArrayAdapter(this, R.layout.item_ville, R.id.tvVilleName, names)

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = files[position]
            val result = Intent().putExtra(EXTRA_SELECTED_PATH, selected.absolutePath)
            setResult(RESULT_OK, result)
            finish()
        }
    }

    companion object {
        const val EXTRA_SELECTED_PATH = "extra_selected_path"
        private const val REQ_STORAGE = 501
    }
}
