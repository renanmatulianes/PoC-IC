package com.example.app

import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

enum class Direction { LEFT, RIGHT, TOP, BOTTOM }
enum class Objects {HUMAN, VEHICLE, MOTORCYCLE, BIKE, NULL}

class MainActivity : AppCompatActivity() {

    private val pulseAnimators = mutableMapOf<Direction, ValueAnimator>()
    private val arrowAnimators = mutableMapOf<Direction, ValueAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("driverPref", MODE_PRIVATE)

        val visualOn   = prefs.getBoolean("notif_visual", true)
        val vibracaoOn = prefs.getBoolean("notif_vibracao", true)
        val sonoraOn   = prefs.getBoolean("notif_sonora", true)
        val ignoreLow = prefs.getBoolean("ignore-low-risk", false)
        val ignoreMid = prefs.getBoolean("ignore-mid-risk", false)
        val ignoreHigh = prefs.getBoolean("ignore-high-risk", false)

        window.decorView.postDelayed({
            if (visualOn)
                notifyVisual(Direction.BOTTOM, 2, Objects.MOTORCYCLE)

            if (sonoraOn)
                SoundManager.playSound(this, Direction.BOTTOM, Objects.MOTORCYCLE, 2)

        }, 1000)

        val settingsButton = findViewById<ImageView>(R.id.settingsIcon)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    fun notifyVisual(direction: Direction, intensity: Int, incomingObject: Objects){
        val carImg = findViewById<ImageView>(R.id.carImg)

        if (direction == Direction.TOP && (incomingObject != Objects.NULL || intensity != -1)){
            val arrowTopImg = findViewById<ImageView>(R.id.topArrow)

            carImg.apply {
                translationY = 150f
            }

            arrowTopImg.apply {
                translationY = 150f
            }
        }
        else if (direction == Direction.BOTTOM && (incomingObject != Objects.NULL || intensity != -1)) {
            val arrowBottomImg = findViewById<ImageView>(R.id.bottomArrow)

            carImg.apply {
                translationY = -150f
            }

            arrowBottomImg.apply {
                translationY = -150f
            }
        }

        startArrowBlink(direction, intensity)

        if (intensity != -1)
            startPulse(direction, intensity)

        if (incomingObject != Objects.NULL)
            showObject(direction, incomingObject)

        val settingsIconImg = findViewById<ImageView>(R.id.settingsIcon)
        settingsIconImg.apply {
            visibility = View.GONE
        }
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

            val animTime : Long

            when (intensity) {
                0 -> animTime = 800L
                1 -> animTime = 600L
                2 -> animTime = 400L
                else -> animTime = 800L
            }

            val anim = ValueAnimator.ofFloat(minSize, maxSize).apply {
                duration = animTime
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

    fun startArrowBlink(direction: Direction, intensity: Int) {
        val arrowView = when (direction) {
            Direction.LEFT   -> findViewById<ImageView>(R.id.leftArrow)
            Direction.RIGHT  -> findViewById<ImageView>(R.id.rightArrow)
            Direction.TOP    -> findViewById<ImageView>(R.id.topArrow)
            Direction.BOTTOM -> findViewById<ImageView>(R.id.bottomArrow)
        }

        arrowView.apply {
            when (direction) {
                Direction.LEFT   -> scaleX = -1f
                Direction.RIGHT  -> scaleX = 1f
                Direction.TOP    -> {
                    scaleX = 1f
                    rotation = -90f
                }
                Direction.BOTTOM -> {
                    scaleX = 1f
                    rotation = 90f
                }
            }
            alpha = 0f
            visibility = View.VISIBLE
        }

        arrowAnimators[direction]?.cancel()

        val animDuration : Long

        when (intensity) {
            0 -> animDuration = 400L
            1 -> animDuration = 300L
            2 -> animDuration = 200L
            else -> animDuration = 400L
        }

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animDuration
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                arrowView.alpha = va.animatedValue as Float
            }
            start()
        }
        arrowAnimators[direction] = anim
    }

    fun stopArrowBlink(direction: Direction) {
        arrowAnimators[direction]?.cancel()
        arrowAnimators.remove(direction)
        val arrowView = when (direction) {
            Direction.LEFT   -> findViewById<ImageView>(R.id.leftArrow)
            Direction.RIGHT  -> findViewById<ImageView>(R.id.rightArrow)
            Direction.TOP    -> findViewById<ImageView>(R.id.topArrow)
            Direction.BOTTOM -> findViewById<ImageView>(R.id.bottomArrow)
        }
        arrowView.visibility = View.GONE
    }

    fun showObject(direction: Direction, incomingObject : Objects){

        val objectView = when (direction) {
            Direction.LEFT   -> findViewById<ImageView>(R.id.leftObject)
            Direction.RIGHT  -> findViewById<ImageView>(R.id.rightObject)
            Direction.TOP    -> findViewById<ImageView>(R.id.topObject)
            Direction.BOTTOM -> findViewById<ImageView>(R.id.bottomObject)
        }

        val resId = when (incomingObject) {
            Objects.VEHICLE     -> R.drawable.vehicle_icon
            Objects.MOTORCYCLE  -> R.drawable.motorcycle
            Objects.BIKE        -> R.drawable.cyclist
            Objects.HUMAN       -> R.drawable.pedestrian
            else                -> 0
        }

        objectView.apply {
            setImageResource(resId)
            elevation = 6f
            bringToFront()
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(250).start()

            when (direction) {
                Direction.RIGHT  -> scaleX = -1f
                else -> {}
            }
        }
    }
}
