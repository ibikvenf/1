package com.aegis.av.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.aegis.av.BuildConfig
import com.aegis.av.R
import com.aegis.av.data.Prefs
import com.aegis.av.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.switchHeuristics.setOnCheckedChangeListener(null)
        b.switchHeuristics.isChecked = Prefs.heuristicsEnabled
        b.switchHeuristics.setOnCheckedChangeListener { _, c -> Prefs.heuristicsEnabled = c }

        b.switchSystemApps.setOnCheckedChangeListener(null)
        b.switchSystemApps.isChecked = Prefs.scanSystemApps
        b.switchSystemApps.setOnCheckedChangeListener { _, c -> Prefs.scanSystemApps = c }

        b.switchAutoUpdate.setOnCheckedChangeListener(null)
        b.switchAutoUpdate.isChecked = Prefs.autoUpdateDb
        b.switchAutoUpdate.setOnCheckedChangeListener { _, c -> Prefs.autoUpdateDb = c }

        b.sliderMaxFile.value = Prefs.maxFileMb.coerceIn(16, 2048).toFloat()
        b.tvMaxFileValue.text = getString(R.string.settings_max_file_value, Prefs.maxFileMb)
        b.sliderMaxFile.addOnChangeListener { _, value, _ ->
            Prefs.maxFileMb = value.toInt()
            b.tvMaxFileValue.text = getString(R.string.settings_max_file_value, value.toInt())
        }

        b.btnSystemSettings.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }

        b.tvVersion.text = getString(R.string.settings_version_fmt, BuildConfig.VERSION_NAME)
    }
}
