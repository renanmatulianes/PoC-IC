// ARQUIVO ATUALIZADO: MainActivity.kt

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
import androidx.lifecycle.lifecycleScope
import com.example.app.model.BsmNotification
import com.example.app.model.CombinedNotification
import com.example.app.model.Notification
import com.example.app.model.PsmNotification
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.net.Socket
import java.time.Instant
import java.time.Duration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.location.Location
import java.io.IOException
import java.lang.StringBuilder

enum class Direction { LEFT, RIGHT, TOP, BOTTOM, NULL}
enum class Objects {HUMAN, VEHICLE, MOTORCYCLE, BIKE, NULL}

class MainActivity : AppCompatActivity() {

    private val pulseAnimators = mutableMapOf<Direction, ValueAnimator>()
    private val arrowAnimators = mutableMapOf<Direction, ValueAnimator>()

    private var mostRecentDirection : Direction = Direction.NULL
    private var mostRecentNotification : Notification? = null

    private val handler = Handler(Looper.getMainLooper())

    private val activeNotifications = mutableMapOf<String, Notification>()
    private val cleanupRunnables = mutableMapOf<String, Runnable>()

    private var tcpSocket: Socket? = null
    private var connectionJob: Job? = null
    private var shouldReconnect = true
    private val reconnectDelayMs = 15000L

    private val moshi  = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val combinedNotificationAdapter = moshi.adapter(CombinedNotification::class.java)

    private val serverIp = "10.0.2.2" // 192.168.0.53
    private val serverPort = 3001 // 8080


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectTcpSocket()

        val settingsButton = findViewById<ImageView>(R.id.settingsIcon)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shouldReconnect = false
        connectionJob?.cancel()
        try {
            tcpSocket?.close()
        } catch (e: IOException) {
            Log.e("MainActivity", "Erro ao fechar o socket", e)
        }
    }

    private fun connectTcpSocket() {
        connectionJob = lifecycleScope.launch(Dispatchers.IO) {

            while (shouldReconnect) {
                try {
                    Log.d("TCP", "Tentando conectar a $serverIp:$serverPort...")
                    tcpSocket = Socket(serverIp, serverPort)

                    withContext(Dispatchers.Main) {
                        toast("Conectado ao servidor OBU!")
                    }
                    Log.d("TCP", "Conexão estabelecida.")

                    val reader = InputStreamReader(tcpSocket!!.getInputStream())
                    val buffer = CharArray(4096)
                    val jsonBuffer = StringBuilder()
                    var charsRead: Int = 0

                    while (tcpSocket!!.isConnected && reader.read(buffer).also { charsRead = it } != -1) {

                        jsonBuffer.append(buffer, 0, charsRead)

                        while (true) {
                            val startIdx = jsonBuffer.indexOf('{')

                            if (startIdx == -1) {
                                jsonBuffer.clear()
                                break
                            }

                            var braceCount = 0
                            var endIdx = -1

                            for (i in startIdx until jsonBuffer.length) {
                                when (jsonBuffer[i]) {
                                    '{' -> braceCount++
                                    '}' -> braceCount--
                                }
                                if (braceCount == 0) {
                                    endIdx = i
                                    break
                                }
                            }

                            if (endIdx != -1) {

                                val completeJson = jsonBuffer.substring(startIdx, endIdx + 1)

                                jsonBuffer.delete(0, endIdx + 1)

                                //Log.d("TCP", "JSON completo extraído: $completeJson")
                                processMessage(completeJson)

                            } else {
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!shouldReconnect) break
                    Log.e("TCP", "Erro de conexão: ${e.message}")
                    withContext(Dispatchers.Main) {
                        toast("Erro de conexão. Reconectando em ${reconnectDelayMs / 1000}s…")
                    }
                    delay(reconnectDelayMs)
                } finally {
                    try {
                        tcpSocket?.close()
                    } catch (e: IOException) {
                        Log.e("TCP", "Erro ao fechar socket na tentativa de reconexão", e)
                    }
                }
            }
        }
    }

    private suspend fun processMessage(jsonString: String) {
        val combinedNotification = try {
            combinedNotificationAdapter.fromJson(jsonString)
        } catch (e: Exception) {
            Log.e("JSON", "Erro ao fazer o parsing do JSON: $jsonString", e)
            null
        }

        combinedNotification?.let { notifData ->
            // O ID do objeto (pedestre) vem do PSM e é a nossa chave.
            val objectId = notifData.psm.id
            val newNotif = combinedToAppNotification(notifData)

            val existingNotif = activeNotifications[objectId]

            // --- LÓGICA PRINCIPAL: DECIDIR SE ATUALIZA OU NÃO ---
            if (shouldDisplayNotification(newNotif, existingNotif)) {
                withContext(Dispatchers.Main) {
                    displayOrUpdateNotification(newNotif)
                }
            }
            else if (existingNotif != null) {
                // Caso B: Risco é o mesmo ou menor. Apenas "refresca" a notificação existente.
                withContext(Dispatchers.Main) {
                    refreshNotificationTimeout(objectId)
                }
            }
        }
    }

    private fun displayOrUpdateNotification(notif: Notification) {
        val objectId = notif.driver_data?.object_id ?: return
        val block = notif.driver_data ?: return

        // 1. LIMPEZA DA NOTIFICAÇÃO ANTERIOR (se houver)
        // Cancela o agendamento de limpeza anterior para este ID
        cleanupRunnables[objectId]?.let { handler.removeCallbacks(it) }
        // Para a animação visual e o som da notificação anterior
        // Como estamos focando em um alerta por vez, paramos o "mostRecentDirection"
        if (mostRecentDirection != Direction.NULL) {
            stopVisualNotification(mostRecentDirection)
            SoundManager.stop()
        }

        // 2. EXTRAÇÃO DOS DADOS PARA A UI
        val dir = when (block.object_direction.lowercase()) {
            "left" -> Direction.LEFT
            "right" -> Direction.RIGHT
            "front" -> Direction.TOP
            "rear" -> Direction.BOTTOM
            else -> Direction.TOP
        }
        val intensity = when (block.risk_level.lowercase()) {
            "low" -> 0
            "medium" -> 1
            else -> 2 // high
        }
        val obj = when (block.object_type.lowercase()) {
            "human" -> Objects.HUMAN
            "bike" -> Objects.BIKE
            "vehicle" -> Objects.VEHICLE
            else -> Objects.VEHICLE
        }

        // 3. EXIBIÇÃO DA NOVA NOTIFICAÇÃO
        val prefs = getSharedPreferences("driverPref", MODE_PRIVATE)
        if (intensity == 0) {
            if (prefs.getBoolean("notif_visual-baixo", true)) notifyVisual(dir, intensity, obj)
            if (prefs.getBoolean("notif_sonora-baixo", true)) SoundManager.playSound(this@MainActivity, dir, obj, intensity)
        } else if (intensity == 1) {
            if (prefs.getBoolean("notif_visual-medio", true)) notifyVisual(dir, intensity, obj)
            if (prefs.getBoolean("notif_sonora-medio", true)) SoundManager.playSound(this@MainActivity, dir, obj, intensity)
        } else {
            if (prefs.getBoolean("notif_visual-alto", true)) notifyVisual(dir, intensity, obj)
            if (prefs.getBoolean("notif_sonora-alto", true)) SoundManager.playSound(this@MainActivity, dir, obj, intensity)
        }

        // 4. ATUALIZAÇÃO DE ESTADO
        mostRecentDirection = dir // Mantemos isso para saber qual animação parar
        activeNotifications[objectId] = notif // Adiciona/atualiza a notificação no mapa

        // 5. AGENDAMENTO DA LIMPEZA FUTURA
        val delayMillis = 5000L // Duração da notificação na tela
        val runnable = Runnable {
            // Verifica se a notificação que agendou esta limpeza ainda é a ativa
            if (activeNotifications[objectId] == notif) {
                stopVisualNotification(dir)
                SoundManager.stop()
                activeNotifications.remove(objectId)
                cleanupRunnables.remove(objectId)
                if (mostRecentDirection == dir) {
                    mostRecentDirection = Direction.NULL
                }
            }
        }
        handler.postDelayed(runnable, delayMillis)
        cleanupRunnables[objectId] = runnable // Armazena o runnable para poder cancelá-lo
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
        val userLocation = notification?.location ?: return null
        val driverData = notification.driver_data ?: return null
        val objectCoords = driverData.object_coordinates
        val objectSpeed = objectCoords.speed ?: return null
        val userSpeed = notification.driver_speed.toDouble()
        val direction = driverData.object_direction.lowercase()

        val dist = distanceMeters(
            userLocation.latitude, userLocation.longitude,
            objectCoords.latitude, objectCoords.longitude
        )

        val vRel = when (direction) {
            "front", "left", "right" -> {
                userSpeed + objectSpeed
            }
            "rear" -> {
                if (objectSpeed > userSpeed) {
                    objectSpeed - userSpeed
                } else {
                    -1.0
                }
            }
            else -> -1.0
        }

        return if (vRel > 0.0) dist / vRel else null
    }

    fun isNotificationMoreImportant(oldN: Notification?, newN: Notification): Boolean {
        if (oldN == null) return true

        val ttcOld = timeToCollision(oldN)
        val ttcNew = timeToCollision(newN)

        val tsOld = Instant.parse(oldN.timestamp)
        val tsNew = Instant.parse(newN.timestamp)
        val elapsedMs = Duration.between(tsOld, tsNew).toMillis().coerceAtLeast(0L)
        val elapsedSec = elapsedMs / 1000.0

        val remOld = ttcOld?.minus(elapsedSec)

        return when {
            remOld != null && remOld <= 0.0 -> true
            remOld == null && ttcNew != null -> true
            remOld != null && ttcNew == null -> false
            remOld != null && ttcNew != null -> ttcNew < remOld
            else -> false
        }
    }

    private fun refreshNotificationTimeout(objectId: String) {
        val existingRunnable = cleanupRunnables[objectId]
        val existingNotif = activeNotifications[objectId]

        if (existingRunnable != null && existingNotif != null) {
            Log.d("NotificationLogic", "Refrescando notificação para o ID: $objectId")
            // Cancela o agendamento de limpeza anterior
            handler.removeCallbacks(existingRunnable)
            // Reagenda o mesmo runnable para mais 5 segundos a partir de agora
            handler.postDelayed(existingRunnable, 5000L)
        }
    }

    private fun shouldDisplayNotification(newNotif: Notification, existingNotif: Notification?): Boolean {
        // Se não há notificação existente para este ID, sempre exiba a nova.
        if (existingNotif == null) {
            return true
        }

        val newRiskLevel = newNotif.driver_data?.risk_level ?: "low"
        val existingRiskLevel = existingNotif.driver_data?.risk_level ?: "low"

        val newRiskPriority = getRiskPriority(newRiskLevel)
        val existingRiskPriority = getRiskPriority(existingRiskLevel)

        // Regra 1: Se o risco aumentou, atualize a notificação.
        if (newRiskPriority != existingRiskPriority) {
            return true
        }

        // Se a nova notificação não for mais importante, não a exiba.
        return false
    }

    fun combinedToAppNotification(data: CombinedNotification): Notification {
        val bsm = data.bsm.value.coreData
        val psm = data.psm

        // --- 1. Conversão de Unidades ---
        // Dados do veículo (motorista) do BSM
        val userLat = bsm.lat / 10_000_000.0
        val userLon = bsm.long / 10_000_000.0
        val userSpeed = bsm.speed.toFloat() * 0.02f
        val userHeading = bsm.heading * 0.0125 // Unidades de 0.0125 graus

        // Dados do objeto (pedestre) do PSM
        val objectLat = psm.position.latitude / 10_000_000.0
        val objectLon = psm.position.longitude / 10_000_000.0
        val objectSpeed = psm.speed * 0.02

        // --- 2. Determinar Tipo do Objeto ---
        val objType = when {
            psm.basicType.contains("PEDESTRIAN", ignoreCase = true) -> "HUMAN"
            psm.basicType.contains("CYCLIST", ignoreCase = true) -> "BIKE"
            // Adicionar mais tipos se necessário
            else -> "HUMAN"
        }

        // --- 3. Calcular Direção Relativa (dirString) ---
        // Azimute do veículo para o pedestre
        val bearingToObject = calculateBearing(userLat, userLon, objectLat, objectLon)
        // Ângulo do pedestre em relação à frente do veículo
        val relativeAngle = normalizeAngle(bearingToObject - userHeading)

        // Mapear ângulo para direção (front, rear, left, right)
        val dirString = when {
            relativeAngle >= -45 && relativeAngle < 45 -> "front"
            relativeAngle >= 45 && relativeAngle < 135 -> "right"
            relativeAngle >= 135 || relativeAngle < -135 -> "rear"
            else -> "left" // -135 to -45
        }

        val risk = "low"

        // --- 5. Montar o objeto Notification ---
        val userLocation = Notification.Location(latitude = userLat, longitude = userLon)
        val objectCoords = Notification.Coordinates(latitude = objectLat, longitude = objectLon, speed = objectSpeed)

        val driverData = Notification.Driver(
            object_id = psm.id,
            risk_level = risk,
            object_direction = dirString,
            object_type = objType,
            object_coordinates = objectCoords
        )

        val timestamp = Instant.now().toString()

        val convertedNotif = Notification(
            driver_data = driverData,
            location = userLocation,
            driver_speed = userSpeed,
            timestamp = timestamp
        )

        val ttc = timeToCollision(convertedNotif)

        convertedNotif.driver_data?.risk_level = if (ttc != null) {
            when {
                ttc < 4.0 -> "high"
                ttc <= 8.0 -> "medium"
                else -> "low"
            }
        } else {
            "low"
        }

        return convertedNotif
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * Calcula o azimute (bearing) inicial em graus de um ponto de partida para um ponto de destino.
     * O azimute é o ângulo em relação ao Norte verdadeiro, no sentido horário (0-360).
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        val y = Math.sin(deltaLonRad) * Math.cos(lat2Rad)
        val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLonRad)

        val bearingRad = Math.atan2(y, x)
        return (Math.toDegrees(bearingRad) + 360) % 360 // Normaliza para 0-360
    }

    /**
     * Normaliza um ângulo para o intervalo [-180, 180].
     */
    private fun normalizeAngle(angle: Double): Double {
        var a = angle % 360
        if (a > 180) {
            a -= 360
        }
        if (a <= -180) {
            a += 360
        }
        return a
    }

private fun getRiskPriority(riskLevel: String): Int {
    return when (riskLevel.lowercase()) {
        "high" -> 3
        "medium" -> 2
        "low" -> 1
        else -> 0
    }
}