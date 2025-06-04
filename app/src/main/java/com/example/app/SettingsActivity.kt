package com.example.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var switchVisual: SwitchMaterial
    private lateinit var switchVibracao: SwitchMaterial
    private lateinit var switchSonora: SwitchMaterial

    private lateinit var lowRiskButton : MaterialButton
    private lateinit var midRiskButton : MaterialButton
    private lateinit var highRiskButton : MaterialButton

    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("driverPref", MODE_PRIVATE)

        setContentView(R.layout.activity_settings)

        switchVisual    = findViewById(R.id.switchVisual)
        switchVibracao  = findViewById(R.id.switchVibracao)
        switchSonora    = findViewById(R.id.switchSonora)

        lowRiskButton = findViewById(R.id.btnLevelBaixo)
        midRiskButton = findViewById(R.id.btnLevelMedio)
        highRiskButton = findViewById(R.id.btnLevelAlto)

        saveButton      = findViewById(R.id.btnSaveSettings)

        loadPreferences()

        saveButton.setOnClickListener {
            prefs.edit {
                putBoolean("notif_visual", switchVisual.isChecked)
                putBoolean("notif_vibracao", switchVibracao.isChecked)
                putBoolean("notif_sonora", switchSonora.isChecked)
                putBoolean("ignore-low-risk" ,lowRiskButton.isChecked)
                putBoolean("ignore-mid-risk" ,midRiskButton.isChecked)
                putBoolean("ignore-high-risk" ,highRiskButton.isChecked)
            }

            finish()
        }
    }

    private fun loadPreferences() {
        val visualOn   = prefs.getBoolean("notif_visual", true)
        val vibracaoOn = prefs.getBoolean("notif_vibracao", true)
        val sonoraOn   = prefs.getBoolean("notif_sonora", true)
        val ignoreLow = prefs.getBoolean("ignore-low-risk", false)
        val ignoreMid = prefs.getBoolean("ignore-mid-risk", false)
        val ignoreHigh = prefs.getBoolean("ignore-high-risk", false)

        switchVisual.isChecked   = visualOn
        switchVibracao.isChecked = vibracaoOn
        switchSonora.isChecked   = sonoraOn
        lowRiskButton.isChecked = ignoreLow
        midRiskButton.isChecked = ignoreMid
        highRiskButton.isChecked = ignoreHigh
    }
}
