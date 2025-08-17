package com.example.app.pattern

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.app.Direction
import com.example.app.MainActivity
import com.example.app.Objects
import com.example.app.R
import com.example.app.SoundManager

// --- EFEITO CENTRAL DE GERENCIAMENTO DE ESTADO ---

/**
 * Efeito principal que para o alerta antigo, atualiza o estado da MainActivity
 * e agenda a limpeza do novo alerta.
 */
class SetNewActiveNotificationEffect : Effect<NotificationContext> {
    override fun apply(context: NotificationContext) {
        val mainActivity = context.mainActivity

        // 1. Para a notificação visual e sonora anterior
        if (mainActivity.activeDirection != Direction.NULL) {
            StopAllVisualsForDirectionEffect(mainActivity.activeDirection).apply(context)
        }
        SoundManager.stop()

        // 2. Atualiza o estado da Activity com os dados da NOVA notificação
        mainActivity.activeNotification = context.newNotification
        mainActivity.activeDirection = context.direction

        // 3. Cria e agenda o novo timer de limpeza
        val delayMillis = 5000L
        val runnable = Runnable {
            // A lógica que será executada após o delay
            // Verifica se a notificação que agendou esta limpeza ainda é a ativa
            if (mainActivity.activeNotification == context.newNotification) {
                StopAllVisualsForDirectionEffect(context.direction).apply(context)
                SoundManager.stop()
                mainActivity.activeNotification = null
                mainActivity.activeDirection = Direction.NULL
                mainActivity.activeCleanupRunnable = null
            }
        }
        context.handler.postDelayed(runnable, delayMillis)
        mainActivity.activeCleanupRunnable = runnable // Armazena o novo runnable
    }
}

/** Apenas reagenda o timer de uma notificação existente. */
class RefreshTimeoutEffect : Effect<NotificationContext> {
    override fun apply(context: NotificationContext) {
        val mainActivity = context.mainActivity
        val existingRunnable = mainActivity.activeCleanupRunnable
        if (existingRunnable != null) {
            Log.d("Orchestrator", "Refreshing timeout for object ID: ${context.objectId}")
            context.handler.removeCallbacks(existingRunnable)
            context.handler.postDelayed(existingRunnable, 5000L)
        }
    }
}


// --- Efeitos Visuais e Sonoros (Lógica movida da MainActivity) ---

class TranslateCarViewEffect : Effect<NotificationContext> {
    override fun apply(context: NotificationContext) {
        val activity = context.mainActivity
        val carImg = activity.findViewById<ImageView>(R.id.carImg)
        when (context.direction) {
            Direction.TOP -> {
                carImg.translationY = 150f
                activity.findViewById<ImageView>(R.id.topArrow).translationY = 150f
            }
            Direction.BOTTOM -> {
                carImg.translationY = -150f
                activity.findViewById<ImageView>(R.id.bottomArrow).translationY = -150f
            }
            else -> {}
        }
    }
}

class StartPulseEffect : Effect<NotificationContext> {
    override fun apply(context: NotificationContext) {
        val activity = context.mainActivity
        val direction = context.direction
        val intensity = context.intensity

        val view = when (direction) {
            Direction.LEFT -> activity.findViewById<View>(R.id.leftPulse)
            Direction.RIGHT -> activity.findViewById<View>(R.id.rightPulse)
            Direction.TOP -> activity.findViewById<View>(R.id.topPulse)
            Direction.BOTTOM -> activity.findViewById<View>(R.id.bottomPulse)
            Direction.NULL -> return
        }

        val startColor = when (intensity) {
            0 -> ContextCompat.getColor(activity, R.color.alert_gray)
            1 -> ContextCompat.getColor(activity, R.color.alert_yellow)
            else -> ContextCompat.getColor(activity, R.color.alert_red)
        }
        val endColor = ContextCompat.getColor(activity, android.R.color.transparent)

        val gd = (ContextCompat.getDrawable(activity, R.drawable.gradient)!!.mutate() as GradientDrawable).apply {
            colors = intArrayOf(startColor, endColor)
            when (direction) {
                Direction.LEFT -> setGradientCenter(0f, 0.5f)
                Direction.RIGHT -> setGradientCenter(1f, 0.5f)
                Direction.TOP -> setGradientCenter(0.5f, 0f)
                Direction.BOTTOM -> setGradientCenter(0.5f, 1f)
                else -> {}
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
                addUpdateListener { va ->
                    gd.gradientRadius = va.animatedValue as Float
                    view.invalidate()
                }
                start()
            }
            context.pulseAnimators[direction] = anim
        }
    }
}

class StartArrowBlinkEffect : Effect<NotificationContext> {
    override fun apply(context: NotificationContext) {
        val activity = context.mainActivity
        val direction = context.direction
        val intensity = context.intensity

        val arrowView = when (direction) {
            Direction.LEFT -> activity.findViewById<ImageView>(R.id.leftArrow)
            Direction.RIGHT -> activity.findViewById<ImageView>(R.id.rightArrow)
            Direction.TOP -> activity.findViewById<ImageView>(R.id.topArrow)
            Direction.BOTTOM -> activity.findViewById<ImageView>(R.id.bottomArrow)
            else -> return
        }

        arrowView.apply {
            when (direction) {
                Direction.LEFT -> scaleX = -1f
                Direction.RIGHT -> scaleX = 1f
                Direction.TOP -> { scaleX = 1f; rotation = -90f }
                Direction.BOTTOM -> { scaleX = 1f; rotation = 90f }
                else -> {}
            }
            alpha = 0f
            visibility = View.VISIBLE
        }

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
            addUpdateListener { va -> arrowView.alpha = va.animatedValue as Float }
            start()
        }
        context.arrowAnimators[direction] = anim
    }
}

class ShowObjectEffect : Effect<NotificationContext> {
    override fun apply(context: NotificationContext) {
        val activity = context.mainActivity
        val direction = context.direction
        val incomingObject = context.objectType

        val objectView = when (direction) {
            Direction.LEFT -> activity.findViewById<ImageView>(R.id.leftObject)
            Direction.RIGHT -> activity.findViewById<ImageView>(R.id.rightObject)
            Direction.TOP -> activity.findViewById<ImageView>(R.id.topObject)
            Direction.BOTTOM -> activity.findViewById<ImageView>(R.id.bottomObject)
            else -> return
        }

        val resId = when (incomingObject) {
            Objects.VEHICLE -> R.drawable.vehicle_icon
            Objects.MOTORCYCLE -> R.drawable.motorcycle
            Objects.BIKE -> R.drawable.cyclist
            Objects.HUMAN -> R.drawable.pedestrian
            else -> 0
        }

        if (resId != 0) {
            objectView.apply {
                setImageResource(resId)
                elevation = 6f
                bringToFront()
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(250).start()
                if (direction == Direction.RIGHT) scaleX = -1f
            }
        }
    }
}

class PlaySoundEffect : Effect<NotificationContext> {
    override fun apply(context: NotificationContext) {
        SoundManager.playSound(
            context.mainActivity,
            context.direction,
            context.objectType,
            context.intensity
        )
    }
}

class StopAllVisualsForDirectionEffect(private val direction: Direction) : Effect<NotificationContext> {
    override fun apply(context: NotificationContext) {
        if (direction == Direction.NULL) return

        val activity = context.mainActivity

        context.arrowAnimators[direction]?.cancel()
        context.arrowAnimators.remove(direction)
        context.pulseAnimators[direction]?.cancel()
        context.pulseAnimators.remove(direction)

        val arrowView = when (direction) {
            Direction.LEFT -> activity.findViewById<ImageView>(R.id.leftArrow)
            Direction.RIGHT -> activity.findViewById<ImageView>(R.id.rightArrow)
            Direction.TOP -> activity.findViewById<ImageView>(R.id.topArrow)
            Direction.BOTTOM -> activity.findViewById<ImageView>(R.id.bottomArrow)
            else -> null
        }
        arrowView?.visibility = View.GONE

        val pulseView = when (direction) {
            Direction.LEFT -> activity.findViewById<View>(R.id.leftPulse)
            Direction.RIGHT -> activity.findViewById<View>(R.id.rightPulse)
            Direction.TOP -> activity.findViewById<View>(R.id.topPulse)
            Direction.BOTTOM -> activity.findViewById<View>(R.id.bottomPulse)
            else -> null
        }
        pulseView?.visibility = View.GONE

        val objectView = when (direction) {
            Direction.LEFT -> activity.findViewById<ImageView>(R.id.leftObject)
            Direction.RIGHT -> activity.findViewById<ImageView>(R.id.rightObject)
            Direction.TOP -> activity.findViewById<ImageView>(R.id.topObject)
            Direction.BOTTOM -> activity.findViewById<ImageView>(R.id.bottomObject)
            else -> null
        }
        objectView?.visibility = View.GONE

        activity.findViewById<ImageView>(R.id.carImg).translationY = 0f
        when (direction) {
            Direction.TOP -> activity.findViewById<ImageView>(R.id.topArrow).translationY = 0f
            Direction.BOTTOM -> activity.findViewById<ImageView>(R.id.bottomArrow).translationY = 0f
            else -> {}
        }
        activity.findViewById<ImageView>(R.id.settingsIcon).visibility = View.VISIBLE
    }
}