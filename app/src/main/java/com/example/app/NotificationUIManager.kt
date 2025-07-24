package com.example.app

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.app.model.Notification

class NotificationUIManager(private val activity: Activity) {

    private val pulseAnimators = mutableMapOf<Direction, ValueAnimator>()
    private val arrowAnimators = mutableMapOf<Direction, ValueAnimator>()
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    // Referências para as Views para evitar chamadas repetidas de findViewById
    private val carImg: ImageView by lazy { activity.findViewById(R.id.carImg) }
    private val settingsIcon: ImageView by lazy { activity.findViewById(R.id.settingsIcon) }

    // Mapeamento de direções para Views para simplificar o código
    private val pulseViews by lazy {
        mapOf(
            Direction.LEFT to activity.findViewById<View>(R.id.leftPulse),
            Direction.RIGHT to activity.findViewById<View>(R.id.rightPulse),
            Direction.TOP to activity.findViewById<View>(R.id.topPulse),
            Direction.BOTTOM to activity.findViewById<View>(R.id.bottomPulse)
        )
    }
    private val arrowViews by lazy {
        mapOf(
            Direction.LEFT to activity.findViewById<ImageView>(R.id.leftArrow),
            Direction.RIGHT to activity.findViewById<ImageView>(R.id.rightArrow),
            Direction.TOP to activity.findViewById<ImageView>(R.id.topArrow),
            Direction.BOTTOM to activity.findViewById<ImageView>(R.id.bottomArrow)
        )
    }
    private val objectViews by lazy {
        mapOf(
            Direction.LEFT to activity.findViewById<ImageView>(R.id.leftObject),
            Direction.RIGHT to activity.findViewById<ImageView>(R.id.rightObject),
            Direction.TOP to activity.findViewById<ImageView>(R.id.topObject),
            Direction.BOTTOM to activity.findViewById<ImageView>(R.id.bottomObject)
        )
    }

    /**
     * Exibe uma notificação visual e sonora completa.
     */
    fun showNotification(notification: Notification, ttcSeconds: Double?) {
        val block = notification.driver_data ?: return

        // 1. Mapeia dados da notificação para parâmetros da UI
        val direction = when (block.object_direction.lowercase()) {
            "left" -> Direction.LEFT
            "right" -> Direction.RIGHT
            "front" -> Direction.TOP
            "rear" -> Direction.BOTTOM
            else -> Direction.TOP
        }
        val intensity = when (block.risk_level.lowercase()) {
            "low" -> 0
            "medium" -> 1
            else -> 2
        }
        val obj = when (block.object_type.lowercase()) {
            "vehicle" -> Objects.VEHICLE
            "motorcycle" -> Objects.MOTORCYCLE
            "human" -> Objects.HUMAN
            "bike" -> Objects.BIKE
            else -> Objects.VEHICLE
        }

        // 2. Para qualquer notificação anterior
        stopCurrentNotification()

        // 3. Verifica as preferências e exibe a nova notificação
        val prefs = activity.getSharedPreferences("driverPref", Activity.MODE_PRIVATE)
        val riskKey = when(intensity) {
            0 -> "baixo"
            1 -> "medio"
            else -> "alto"
        }

        if (prefs.getBoolean("notif_visual-$riskKey", true)) {
            notifyVisual(direction, intensity, obj)
        }
        if (prefs.getBoolean("notif_sonora-$riskKey", true)) {
            SoundManager.playSound(activity, direction, obj, intensity)
        }

        // 4. Agenda a parada automática da notificação
        scheduleStop(direction, ttcSeconds)
    }

    /**
     * Para a notificação visual e sonora que está ativa.
     */
    fun stopCurrentNotification() {
        // Para o timer agendado
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null

        // Para o som
        SoundManager.stop()

        // Para a animação de cada direção possível
        pulseAnimators.keys.forEach { dir ->
            stopVisualNotification(dir)
        }
        pulseAnimators.clear()
        arrowAnimators.clear()
    }

    private fun scheduleStop(direction: Direction, ttcSeconds: Double?) {
        val delayMs = ttcSeconds?.let { ((it + 2.0) * 1000).toLong().coerceAtLeast(5000L) } ?: 5000L

        val runnable = Runnable {
            stopVisualNotification(direction)
            SoundManager.stop()
        }
        handler.postDelayed(runnable, delayMs)
        stopRunnable = runnable
    }

    private fun notifyVisual(direction: Direction, intensity: Int, incomingObject: Objects) {
        if (direction == Direction.TOP && (incomingObject != Objects.NULL || intensity != -1)) {
            carImg.translationY = 150f
            arrowViews[Direction.TOP]?.translationY = 150f
        } else if (direction == Direction.BOTTOM && (incomingObject != Objects.NULL || intensity != -1)) {
            carImg.translationY = -150f
            arrowViews[Direction.BOTTOM]?.translationY = -150f
        }

        startArrowBlink(direction, intensity)
        if (intensity != -1) startPulse(direction, intensity)
        if (incomingObject != Objects.NULL) showObject(direction, incomingObject)

        settingsIcon.visibility = View.GONE
    }

    private fun stopVisualNotification(direction: Direction) {
        if (direction == Direction.NULL) return

        stopArrowBlink(direction)
        stopPulse(direction)
        removeObjectImg(direction)

        carImg.translationY = 0f

        when (direction) {
            Direction.TOP -> arrowViews[direction]?.translationY = 0f
            Direction.BOTTOM -> arrowViews[direction]?.translationY = 0f
            else -> {}
        }

        settingsIcon.visibility = View.VISIBLE
    }

    private fun startPulse(direction: Direction, intensity: Int) {
        val view = pulseViews[direction] ?: return
        pulseAnimators[direction]?.cancel()

        val startColor = when (intensity) {
            0 -> ContextCompat.getColor(activity, R.color.alert_gray)
            1 -> ContextCompat.getColor(activity, R.color.alert_yellow)
            else -> ContextCompat.getColor(activity, R.color.alert_red)
        }
        val endColor = ContextCompat.getColor(activity, android.R.color.transparent)

        val gd = (ContextCompat.getDrawable(activity, R.drawable.gradient)!!
            .mutate() as GradientDrawable).apply {
            colors = intArrayOf(startColor, endColor)

            when (direction) {
                Direction.LEFT   -> setGradientCenter(0f, 0.5f)
                Direction.RIGHT  -> setGradientCenter(1f, 0.5f)
                Direction.TOP    -> setGradientCenter(0.5f, 0f)
                Direction.BOTTOM -> setGradientCenter(0.5f, 1f)
                else             -> setGradientCenter(0.5f, 1f)
            }
        }

        view.background = gd
        view.visibility = View.VISIBLE

        view.post {
            val (minSize, maxSize) = if (direction == Direction.LEFT || direction == Direction.RIGHT) 350f to 400f else 200f to 300f
            val animTime = when (intensity) {
                0 -> 800L
                1 -> 500L
                else -> 300L
            }
            val anim = ValueAnimator.ofFloat(minSize, maxSize).apply {
                duration = animTime
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { gd.gradientRadius = it.animatedValue as Float; view.invalidate() }
                start()
            }
            pulseAnimators[direction] = anim
        }
    }

    private fun stopPulse(direction: Direction) {
        pulseAnimators[direction]?.cancel()
        val view = pulseViews[direction]
        view?.visibility = View.GONE
    }

    private fun startArrowBlink(direction: Direction, intensity: Int) {
        val arrowView = arrowViews[direction] ?: return
        arrowView.apply {
            scaleX = when (direction) {
                Direction.LEFT -> -1f
                else -> 1f
            }
            rotation = when(direction) {
                Direction.TOP -> -90f
                Direction.BOTTOM -> 90f
                else -> 0f
            }
            alpha = 0f
            visibility = View.VISIBLE
        }

        arrowAnimators[direction]?.cancel()

        val animDuration = when (intensity) {
            0 -> 400L
            1 -> 300L
            else -> 200L
        }
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animDuration
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { arrowView.alpha = it.animatedValue as Float }
            start()
        }
        arrowAnimators[direction] = anim
    }

    private fun stopArrowBlink(direction: Direction) {
        arrowAnimators[direction]?.cancel()
        val arrowView = arrowViews[direction]
        arrowView?.visibility = View.GONE
    }

    private fun showObject(direction: Direction, incomingObject: Objects) {
        val objectView = objectViews[direction] ?: return
        val resId = when (incomingObject) {
            Objects.VEHICLE -> R.drawable.vehicle_icon
            Objects.MOTORCYCLE -> R.drawable.motorcycle
            Objects.BIKE -> R.drawable.cyclist
            Objects.HUMAN -> R.drawable.pedestrian
            else -> 0
        }
        if (resId == 0) return

        objectView.apply {
            setImageResource(resId)
            elevation = 6f
            bringToFront()
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(250).start()
            scaleX = if (direction == Direction.RIGHT) -1f else 1f
        }
    }

    private fun removeObjectImg(direction: Direction) {
        val objectView = objectViews[direction]
        objectView?.visibility = View.GONE
    }
}