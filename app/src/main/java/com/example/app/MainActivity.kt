package com.example.app

import android.animation.ValueAnimator
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import kotlin.math.hypot

enum class Direction { LEFT, RIGHT, TOP, BOTTOM }
enum class Objects {HUMAN, VEHICLE, MOTORCYCLE, BIKE}

class MainActivity : AppCompatActivity() {

    private val pulseAnimators = mutableMapOf<Direction, ValueAnimator>()
    private val arrowAnimators = mutableMapOf<Direction, ValueAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.decorView.postDelayed({
            startPulse(Direction.RIGHT, 2)
            startArrowBlink(Direction.RIGHT)
            showObject(Direction.RIGHT, Objects.BIKE)

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

    fun startArrowBlink(direction: Direction) {
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

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
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
            Objects.VEHICLE     -> R.drawable.pedestrian
            Objects.MOTORCYCLE  -> R.drawable.pedestrian
            Objects.BIKE        -> R.drawable.cyclist
            Objects.HUMAN       -> R.drawable.pedestrian
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
