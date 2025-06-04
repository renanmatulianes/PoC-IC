package com.example.app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import androidx.core.content.edit

class RegisterActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("driverPref", MODE_PRIVATE)

        if (prefs.contains("plate")) {
            goToRadar()
            return
        }

        setContentView(R.layout.activity_register)

        val etName  = findViewById<TextInputEditText>(R.id.etName)
        val etModel = findViewById<TextInputEditText>(R.id.etModel)
        val etYear  = findViewById<TextInputEditText>(R.id.etYear)
        val etPlate = findViewById<TextInputEditText>(R.id.etPlate)
        val btnReg  = findViewById<Button>(R.id.btnRegister)

        btnReg.setOnClickListener {
            val name  = etName.text?.toString()?.trim().orEmpty()
            val model = etModel.text?.toString()?.trim().orEmpty()
            val year  = etYear.text?.toString()?.trim().orEmpty()
            val plate = etPlate.text?.toString()?.trim().orEmpty()

            if (name.isBlank() || model.isBlank() || year.isBlank() || plate.isBlank()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit {
                putString("name", name)
                putString("model", model)
                putString("year", year)
                putString("plate", plate)
            }

            goToRadar()
        }
    }

    private fun goToRadar() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
