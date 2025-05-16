package com.example.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var leftSensor: ImageView
    private lateinit var rightSensor: ImageView
    private lateinit var topSensor: ImageView
    private lateinit var bottomSensor: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        leftSensor   = findViewById(R.id.leftSensorImg)
        rightSensor  = findViewById(R.id.rightSensorImg)
        topSensor    = findViewById(R.id.topSensorImg)
        bottomSensor = findViewById(R.id.bottomSensorImg)

        rightSensor.visibility = View.INVISIBLE
        leftSensor.visibility = View.VISIBLE
        topSensor.visibility = View.INVISIBLE
        bottomSensor.visibility = View.INVISIBLE

        // Exemplo de ativação inicial (pode vir de evento real)
        onBlindSpotEvent(left = true, right = false, top = false, bottom = false)
    }

    private fun startBlinking(view: View) {
        ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f).apply {
            duration = 500L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun setSensorActive(sensor: ImageView, active: Boolean) {
        sensor.clearAnimation()
        if (active) {
            sensor.visibility = View.VISIBLE
            sensor.setColorFilter(
                ContextCompat.getColor(this, R.color.sensor_on),
                PorterDuff.Mode.SRC_IN
            )
            startBlinking(sensor)
        } else {
            sensor.visibility = View.INVISIBLE
            sensor.alpha = 1f
            sensor.setColorFilter(
                ContextCompat.getColor(this, R.color.sensor_off),
                PorterDuff.Mode.SRC_IN
            )
        }
    }

    private fun onBlindSpotEvent(
        left: Boolean,
        right: Boolean,
        top: Boolean,
        bottom: Boolean
    ) {
        setSensorActive(leftSensor, left)
        setSensorActive(rightSensor, right)
        setSensorActive(topSensor, top)
        setSensorActive(bottomSensor, bottom)
    }
}