package com.salat.times

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.salat.times.alarm.AlarmScheduler
import com.salat.times.data.PrefsManager
import com.salat.times.data.SalatDataRepository

class SettingsVilleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_ville)

        val repo = SalatDataRepository.get(this)
        val prefs = PrefsManager(this)
        val listView = findViewById<ListView>(R.id.listVilles)

        val villes = repo.villes
        val names = villes.map { it.nom }
        val currentIndex = villes.indexOfFirst { it.id == prefs.villeId }

        val adapter = object : ArrayAdapter<String>(
            this, R.layout.item_ville, R.id.tvVilleName, names
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val tv = view.findViewById<android.widget.TextView>(R.id.tvVilleName)
                if (position == currentIndex) {
                    tv.setTextColor(getColor(R.color.accent_gold))
                    tv.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    tv.setTextColor(getColor(R.color.text_primary))
                    tv.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
                return view
            }
        }
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = villes[position]
            prefs.villeId = selected.id
            AlarmScheduler.rescheduleAll(this)
            finish()
        }
    }
}
