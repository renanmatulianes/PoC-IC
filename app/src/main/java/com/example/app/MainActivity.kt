package com.example.app

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.hypot

enum class Direction { LEFT, RIGHT, TOP, BOTTOM }

class MainActivity : AppCompatActivity() {

    private val pulseAnimators = mutableMapOf<Direction, ValueAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.decorView.postDelayed({
            startPulse(Direction.LEFT, 1)
        }, 1000)
    }

    fun startPulse(direction: Direction, intensity : Int) {
        val view = when (direction) {
            Direction.LEFT   -> findViewById<View>(R.id.leftPulse)
            Direction.RIGHT  -> findViewById<View>(R.id.rightPulse)
            Direction.TOP    -> findViewById<View>(R.id.topPulse)
            Direction.BOTTOM -> findViewById<View>(R.id.bottomPulse)
        }

        pulseAnimators[direction]?.cancel()

        val startColor = when (intensity) {
            0 -> ContextCompat.getColor(this, R.color.alert_gray)
            1 -> ContextCompat.getColor(this, R.color.alert_yellow)
            else -> ContextCompat.getColor(this, R.color.alert_red)
        }
        val endColor = ContextCompat.getColor(this, android.R.color.transparent)

        val gd = (ContextCompat.getDrawable(this, R.drawable.gradient)!!
            .mutate() as GradientDrawable).apply {

            colors = intArrayOf(startColor, endColor)

            when (direction) {
                Direction.LEFT   -> setGradientCenter(0f,   0.5f)
                Direction.RIGHT  -> setGradientCenter(1f,   0.5f)
                Direction.TOP    -> setGradientCenter(0.5f, 0f)
                Direction.BOTTOM -> setGradientCenter(0.5f, 1f)
            }
        }

        view.background = gd
        view.visibility = View.VISIBLE

        view.post {

            val (minSize, maxSize) = if (direction == Direction.LEFT || direction == Direction.RIGHT) {
                350f to 400f
            } else {
                200f to 300f
            }

            val anim = ValueAnimator.ofFloat(minSize, maxSize).apply {
                duration = 800L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    gd.gradientRadius = va.animatedValue as Float
                    view.invalidate()
                }
                start()
            }

            pulseAnimators[direction] = anim
        }
    }

    fun stopPulse(direction: Direction) {
        pulseAnimators[direction]?.cancel()
        pulseAnimators.remove(direction)

        val view = when (direction) {
            Direction.LEFT   -> findViewById<View>(R.id.leftPulse)
            Direction.RIGHT  -> findViewById<View>(R.id.rightPulse)
            Direction.TOP    -> findViewById<View>(R.id.topPulse)
            Direction.BOTTOM -> findViewById<View>(R.id.bottomPulse)
        }
        view.visibility = View.GONE
    }
}
