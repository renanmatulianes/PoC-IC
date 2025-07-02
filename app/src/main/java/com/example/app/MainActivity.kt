package com.example.app

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.app.model.Notification
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import android.location.Location
import java.time.Instant
import java.time.Duration
import android.os.Handler
import android.os.Looper

enum class Direction { LEFT, RIGHT, TOP, BOTTOM, NULL}
enum class Objects {HUMAN, VEHICLE, MOTORCYCLE, BIKE, NULL}

class MainActivity : AppCompatActivity() {

    private val pulseAnimators = mutableMapOf<Direction, ValueAnimator>()
    private val arrowAnimators = mutableMapOf<Direction, ValueAnimator>()

    private var mostRecentDirection : Direction = Direction.NULL
    private var mostRecentNotification : Notification? = null

    // Handler na main thread para agendar paradas
    private val handler = Handler(Looper.getMainLooper())
    // Guarda o Runnable agendado da notificação atual
    private var stopRunnable: Runnable? = null

    private lateinit var socket: WebSocket
    private var shouldReconnect = true
    // tempo de espera antes de reconectar (ms)
    private val reconnectDelayMs = 15000L

    private val moshi  = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val notifAdapter = moshi.adapter(Notification::class.java)

    private val serverIp = "10.0.2.2" //192.168.15.37, emulador = 10.0.2.2
    private val WSENDPOINT = "wss://poc-conecta.onrender.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("driverPref", MODE_PRIVATE)

        val userId = prefs.getInt("userId", -1)

        if (userId != -1) connectSocket(userId)

        val settingsButton = findViewById<ImageView>(R.id.settingsIcon)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shouldReconnect = false
        socket.close(1000, "Activity destroyed")
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

//        val settingsIconImg = findViewById<ImageView>(R.id.settingsIcon)
//        settingsIconImg.apply {
//            visibility = View.GONE
//        }
    }

    fun stopVisualNotification(directionArg: Direction = mostRecentDirection) {

        if (directionArg == Direction.NULL) return

        stopArrowBlink(directionArg)
        stopPulse(directionArg)
        removeObjectImg(directionArg)

        val carImg = findViewById<ImageView>(R.id.carImg)
        carImg.translationY = 0f

        when (directionArg) {
            Direction.TOP    -> findViewById<ImageView>(R.id.topArrow).translationY    = 0f
            Direction.BOTTOM -> findViewById<ImageView>(R.id.bottomArrow).translationY = 0f
            else -> {}
        }

        findViewById<ImageView>(R.id.settingsIcon).visibility = View.VISIBLE
    }

    fun startPulse(direction: Direction, intensity : Int) {
        val view = when (direction) {
            Direction.LEFT   -> findViewById<View>(R.id.leftPulse)
            Direction.RIGHT  -> findViewById<View>(R.id.rightPulse)
            Direction.TOP    -> findViewById<View>(R.id.topPulse)
            Direction.BOTTOM -> findViewById<View>(R.id.bottomPulse)
            Direction.NULL -> findViewById<View>(R.id.bottomPulse)
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
                else -> setGradientCenter(0.5f, 1f)
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
                1 -> animTime = 500L
                2 -> animTime = 300L
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
            else -> findViewById<View>(R.id.bottomPulse)
        }
        view.visibility = View.GONE
    }

    fun startArrowBlink(direction: Direction, intensity: Int) {
        val arrowView = when (direction) {
            Direction.LEFT   -> findViewById<ImageView>(R.id.leftArrow)
            Direction.RIGHT  -> findViewById<ImageView>(R.id.rightArrow)
            Direction.TOP    -> findViewById<ImageView>(R.id.topArrow)
            Direction.BOTTOM -> findViewById<ImageView>(R.id.bottomArrow)
            else -> findViewById<ImageView>(R.id.bottomArrow)
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
                else -> {}
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
            else -> findViewById<ImageView>(R.id.bottomArrow)
        }
        arrowView.visibility = View.GONE
    }

    fun showObject(direction: Direction, incomingObject : Objects){

        val objectView = when (direction) {
            Direction.LEFT   -> findViewById<ImageView>(R.id.leftObject)
            Direction.RIGHT  -> findViewById<ImageView>(R.id.rightObject)
            Direction.TOP    -> findViewById<ImageView>(R.id.topObject)
            Direction.BOTTOM -> findViewById<ImageView>(R.id.bottomObject)
            else -> findViewById<ImageView>(R.id.bottomObject)
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

    fun removeObjectImg(direction: Direction){
        val objectView = when (direction) {
            Direction.LEFT   -> findViewById<ImageView>(R.id.leftObject)
            Direction.RIGHT  -> findViewById<ImageView>(R.id.rightObject)
            Direction.TOP    -> findViewById<ImageView>(R.id.topObject)
            Direction.BOTTOM -> findViewById<ImageView>(R.id.bottomObject)
            else -> findViewById<ImageView>(R.id.bottomObject)
        }

        objectView.apply {
            visibility = View.GONE
        }
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0].toDouble()
    }

    fun timeToCollision(notification: Notification?): Double? {
        // 1. Valida se temos todos os dados necessários
        val userLocation = notification?.location ?: return null
        val driverData = notification.driver_data ?: return null
        val objectCoords = driverData.object_coordinates
        val objectSpeed = objectCoords.speed ?: return null // Velocidade do objeto em m/s
        val userSpeed = notification.driver_speed.toDouble() // Sua velocidade em m/s
        val direction = driverData.object_direction.lowercase()

        // 2. Calcula a distância entre os dois
        val dist = distanceMeters(
            userLocation.latitude, userLocation.longitude,
            objectCoords.latitude, objectCoords.longitude
        )

        // 3. Calcula a velocidade relativa (Vrel) com base na direção
        val vRel = when (direction) {
            "front", "left", "right" -> {
                // Aproximação frontal ou lateral (pior caso): velocidades se somam
                userSpeed + objectSpeed
            }
            "rear" -> {
                // Aproximação traseira: Vrel é a diferença.
                // Só há risco de colisão se o objeto for mais rápido.
                if (objectSpeed > userSpeed) {
                    objectSpeed - userSpeed
                } else {
                    -1.0 // Indica que estão se afastando, sem risco de colisão
                }
            }
            else -> -1.0 // Direção desconhecida, assume sem risco
        }

        // 4. Calcula o TTC se a velocidade relativa for positiva (estão se aproximando)
        return if (vRel > 0.0) dist / vRel else null
    }

    fun isNotificationMoreImportant(oldN: Notification?, newN: Notification): Boolean {
        // Se não havia notificação anterior, nova sempre vence
        if (oldN == null) return true

        // 1) Calcula TTC original de cada notificação (em segundos)
        val ttcOld = timeToCollision(oldN)
        val ttcNew = timeToCollision(newN)

        // 2) Quanto tempo se passou entre a chegada das notificações (em ms → seg)
        val tsOld = Instant.parse(oldN.timestamp)
        val tsNew = Instant.parse(newN.timestamp)
        val elapsedMs = Duration.between(tsOld, tsNew).toMillis().coerceAtLeast(0L)
        val elapsedSec = elapsedMs / 1000.0

        // 3) Calcula quanto tempo ainda resta para a antiga colidir
        val remOld = ttcOld?.minus(elapsedSec)

        return when {
            // Se remOld <= 0 → conteúdo antigo já "expirou" → nova é mais importante
            remOld != null && remOld <= 0.0                   -> true

            // Antiga não tem TTC, mas nova tem → prioriza nova
            remOld == null && ttcNew != null                  -> true

            // Antiga tem TTC remanescente, nova não tem → mantém antiga
            remOld != null && ttcNew == null                  -> false

            // Ambas têm TTC → compara o TTC da nova com o TTC remanescente da antiga
            remOld != null && ttcNew != null                  -> ttcNew < remOld

            // Nem antiga nem nova têm TTC → mantém antiga
            else                                               -> false
        }
    }

    private fun connectSocket(userId: Int) {

        val request = Request.Builder()
            //.url("ws://$serverIp:3001?user_id=$userId")
            .url("$WSENDPOINT?user_id=$userId")
            .build()

        val client = OkHttpClient()

        socket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, resp: Response) {
                runOnUiThread { toast("Conectado ao servidor!") }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                // converte JSON → Notification
                val notif = notifAdapter.fromJson(text) ?: return
                val isMoreImportant = isNotificationMoreImportant(mostRecentNotification, notif)

                if (!isMoreImportant)
                    return

                val block = notif.driver_data ?: return

                // Mapeia texto → enums
                val dir = when (block.object_direction.lowercase()) {
                    "left"   -> Direction.LEFT
                    "right"  -> Direction.RIGHT
                    "front"    -> Direction.TOP
                    "rear" -> Direction.BOTTOM
                    else     -> Direction.TOP
                }
                val intensity = when (block.risk_level.lowercase()) {
                    "low"    -> 0
                    "medium" -> 1
                    else     -> 2
                }

                val obj = when (block.object_type.lowercase()) {
                    "vehicle" -> Objects.VEHICLE
                    "motorcycle" -> Objects.MOTORCYCLE
                    "human" -> Objects.HUMAN
                    "bike" -> Objects.BIKE
                    else -> Objects.VEHICLE
                }

                //val objCoordinates = block.object_coordinates

                val prefs = getSharedPreferences("driverPref", MODE_PRIVATE)

                runOnUiThread {
                    stopRunnable?.let { handler.removeCallbacks(it) }

                    if (mostRecentDirection != Direction.NULL){
                        stopVisualNotification(mostRecentDirection)
                        SoundManager.stop()
                    }

                    if (intensity == 0) {
                        if (prefs.getBoolean("notif_visual-baixo", true))
                            notifyVisual(dir, intensity, obj)

                        if (prefs.getBoolean("notif_sonora-baixo", true))
                            SoundManager.playSound(this@MainActivity, dir, obj, intensity)
                    }
                    else if (intensity == 1){
                        if (prefs.getBoolean("notif_visual-medio", true))
                            notifyVisual(dir, intensity, obj)

                        if (prefs.getBoolean("notif_sonora-medio", true))
                            SoundManager.playSound(this@MainActivity, dir, obj, intensity)
                    }
                    else {
                        if (prefs.getBoolean("notif_visual-alto", true))
                            notifyVisual(dir, intensity, obj)

                        if (prefs.getBoolean("notif_sonora-alto", true))
                            SoundManager.playSound(this@MainActivity, dir, obj, intensity)
                    }

                    mostRecentDirection = dir
                    mostRecentNotification = notif


                    val ttcSeconds = timeToCollision(notif) ?: 0.0

                    var delayMillis = ((ttcSeconds + 2.0) * 1000).toLong()
                    if (delayMillis < 5000.0) delayMillis = (5000.0).toLong()

                    val runnable = Runnable {
                        stopVisualNotification(dir)
                        SoundManager.stop()
                    }
                    handler.postDelayed(runnable, delayMillis)
                    stopRunnable = runnable
                }

            }

            override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
                runOnUiThread { toast("Erro conexão. Reconectando em ${reconnectDelayMs/1000}s…") }
                if (shouldReconnect) {
                    handler.postDelayed({ connectSocket(userId) }, reconnectDelayMs)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                runOnUiThread { toast("Erro conexão. Reconectando em ${reconnectDelayMs/1000}s…") }
                if (shouldReconnect) {
                    handler.postDelayed({ connectSocket(userId) }, reconnectDelayMs)
                }
            }
        })
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
