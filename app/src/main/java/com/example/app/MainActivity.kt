package com.example.app

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app.model.CombinedNotification
import com.example.app.model.Notification
import com.example.app.pattern.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStreamReader
import java.net.Socket
import java.time.Instant

enum class Direction { LEFT, RIGHT, TOP, BOTTOM, NULL }
enum class Objects { HUMAN, VEHICLE, MOTORCYCLE, BIKE, NULL }

class MainActivity : AppCompatActivity() {

    // --- Estado do App (gerenciado pela Activity, operado pelos Effects) ---
    internal var activeNotification: Notification? = null
    internal var activeCleanupRunnable: Runnable? = null
    internal var activeDirection: Direction = Direction.NULL

    // --- Dependências para os Effects ---
    private val pulseAnimators = mutableMapOf<Direction, ValueAnimator>()
    private val arrowAnimators = mutableMapOf<Direction, ValueAnimator>()
    private val handler = Handler(Looper.getMainLooper())

    // --- Conexão TCP ---
    private var tcpSocket: Socket? = null
    private var connectionJob: Job? = null
    private var shouldReconnect = true
    private val reconnectDelayMs = 15000L

    // --- Utilitários ---
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val combinedNotificationAdapter = moshi.adapter(CombinedNotification::class.java)
    private val orchestrator = NotificationOrchestrator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupNotificationRules()
        connectTcpSocket()

        findViewById<ImageView>(R.id.settingsIcon).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shouldReconnect = false
        connectionJob?.cancel()
        handler.removeCallbacksAndMessages(null)
        try {
            tcpSocket?.close()
        } catch (e: IOException) {
            Log.e("MainActivity", "Erro ao fechar o socket", e)
        }
    }

    private fun setupNotificationRules() {
        // Efeitos para ATUALIZAR COMPLETAMENTE uma notificação visual
        val updateVisualEffects = listOf(
            SetNewActiveNotificationEffect(), // Lida com estado, limpeza e timer
            TranslateCarViewEffect(),
            StartPulseEffect(),
            StartArrowBlinkEffect(),
            ShowObjectEffect()
        )

        val playSoundEffect = listOf(PlaySoundEffect())
        val refreshTimeoutEffect = listOf(RefreshTimeoutEffect())

        // Regra 1: Refrescar uma notificação existente sem mudar o visual
        orchestrator.addRule(NotificationRule(
            name = "Refresh Existing Notification",
            filter = ShouldRefreshOnlyFilter(),
            effects = refreshTimeoutEffect
        ))

        // Regras 2: Exibir/Atualizar Notificações Visuais
        orchestrator.addRule(NotificationRule(
            name = "Display High Risk Visual",
            filter = AndFilter(listOf(ShouldUpdateDisplayFilter(), RiskLevelFilter(2), HighRiskVisualNotifEnabledFilter())),
            effects = updateVisualEffects
        ))
        orchestrator.addRule(NotificationRule(
            name = "Display Mid Risk Visual",
            filter = AndFilter(listOf(ShouldUpdateDisplayFilter(), RiskLevelFilter(1), MidRiskVisualNotifEnabledFilter())),
            effects = updateVisualEffects
        ))
        orchestrator.addRule(NotificationRule(
            name = "Display Low Risk Visual",
            filter = AndFilter(listOf(ShouldUpdateDisplayFilter(), RiskLevelFilter(0), LowRiskVisualNotifEnabledFilter())),
            effects = updateVisualEffects
        ))

        // Regras 3: Tocar Som
        orchestrator.addRule(NotificationRule(
            name = "Play High Risk Sound",
            filter = AndFilter(listOf(ShouldUpdateDisplayFilter(), RiskLevelFilter(2), HighRiskSoundNotifEnabledFilter())),
            effects = playSoundEffect
        ))
        orchestrator.addRule(NotificationRule(
            name = "Play Mid Risk Sound",
            filter = AndFilter(listOf(ShouldUpdateDisplayFilter(), RiskLevelFilter(1), MidRiskSoundNotifEnabledFilter())),
            effects = playSoundEffect
        ))
        orchestrator.addRule(NotificationRule(
            name = "Play Low Risk Sound",
            filter = AndFilter(listOf(ShouldUpdateDisplayFilter(), RiskLevelFilter(0), LowRiskSoundNotifEnabledFilter())),
            effects = playSoundEffect
        ))
    }

    private fun connectTcpSocket() {
        connectionJob = lifecycleScope.launch(Dispatchers.IO) {
            val serverIp = "10.0.2.2"
            val serverPort = 3001
            while (shouldReconnect) {
                try {
                    Log.d("TCP", "Tentando conectar a $serverIp:$serverPort...")
                    tcpSocket = Socket(serverIp, serverPort)
                    withContext(Dispatchers.Main) { toast("Conectado ao servidor OBU!") }
                    val reader = InputStreamReader(tcpSocket!!.getInputStream())
                    val jsonBuffer = StringBuilder()
                    val buffer = CharArray(4096)
                    var charsRead: Int = 0
                    while (tcpSocket!!.isConnected && reader.read(buffer).also { charsRead = it } != -1) {
                        jsonBuffer.append(buffer, 0, charsRead)
                        while (true) {
                            val startIdx = jsonBuffer.indexOf('{')
                            if (startIdx == -1) {
                                jsonBuffer.clear(); break
                            }
                            var braceCount = 0
                            var endIdx = -1
                            for (i in startIdx until jsonBuffer.length) {
                                when (jsonBuffer[i]) {
                                    '{' -> braceCount++
                                    '}' -> braceCount--
                                }
                                if (braceCount == 0) {
                                    endIdx = i; break
                                }
                            }
                            if (endIdx != -1) {
                                val completeJson = jsonBuffer.substring(startIdx, endIdx + 1)
                                jsonBuffer.delete(0, endIdx + 1)
                                processMessage(completeJson)
                            } else {
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!shouldReconnect) break
                    Log.e("TCP", "Erro de conexão: ${e.message}", e)
                    withContext(Dispatchers.Main) { toast("Erro de conexão. Reconectando em ${reconnectDelayMs / 1000}s…") }
                    delay(reconnectDelayMs)
                } finally {
                    try {
                        tcpSocket?.close()
                    } catch (e: IOException) {
                        Log.e("TCP", "Erro ao fechar socket")
                    }
                }
            }
        }
    }

    private suspend fun processMessage(jsonString: String) {
        val combinedNotification = try {
            moshi.adapter(CombinedNotification::class.java).fromJson(jsonString)
        } catch (e: Exception) {
            Log.e("JSON", "Erro ao fazer o parsing do JSON: $jsonString", e)
            null
        }

        combinedNotification?.let { notifData ->
            val newNotif = combinedToAppNotification(notifData)
            val driverData = newNotif.driver_data ?: return@let
            val objectId = driverData.object_id

            if (ShouldUpdateDisplayFilter().isMet(createPreliminaryContext(newNotif, objectId))) {
                withContext(Dispatchers.Main) {
                    activeCleanupRunnable?.let { handler.removeCallbacks(it) }
                    activeCleanupRunnable = null
                }
            }

            // 1. Extrair dados
            val direction = when (driverData.object_direction.lowercase()) {
                "left" -> Direction.LEFT; "right" -> Direction.RIGHT; "front" -> Direction.TOP; "rear" -> Direction.BOTTOM; else -> Direction.TOP
            }
            val intensity = when (driverData.risk_level.lowercase()) {
                "low" -> 0; "medium" -> 1; else -> 2
            }
            val objectType = when (driverData.object_type.lowercase()) {
                "human" -> Objects.HUMAN; "bike" -> Objects.BIKE; "vehicle" -> Objects.VEHICLE; else -> Objects.VEHICLE
            }

            // 2. Criar o Contexto completo
            val context = NotificationContext(
                newNotification = newNotif,
                direction = direction,
                intensity = intensity,
                objectType = objectType,
                objectId = objectId,
                existingNotificationForId = if (activeNotification?.driver_data?.object_id == objectId) activeNotification else null,
                mainActivity = this,
                prefs = getSharedPreferences("driverPref", MODE_PRIVATE),
                handler = this.handler,
                pulseAnimators = this.pulseAnimators,
                arrowAnimators = this.arrowAnimators
            )

            // 3. Processar na Main Thread
            withContext(Dispatchers.Main) {
                orchestrator.process(context)
            }
        }
    }

    // Função auxiliar para permitir a verificação do filtro antes de criar o contexto completo
    private fun createPreliminaryContext(newNotif: Notification, objectId: String): NotificationContext {
        return NotificationContext(
            newNotification = newNotif,
            objectId = objectId,
            existingNotificationForId = if (activeNotification?.driver_data?.object_id == objectId) activeNotification else null,
            direction = Direction.NULL, intensity = 0, objectType = Objects.NULL, mainActivity = this,
            prefs = getSharedPreferences("driverPref", MODE_PRIVATE), handler = handler,
            pulseAnimators = pulseAnimators, arrowAnimators = arrowAnimators
        )
    }

    // --- Funções Utilitárias ---
    private fun combinedToAppNotification(data: CombinedNotification): Notification {
        val bsm = data.bsm.value.coreData
        val psm = data.psm
        val userLat = bsm.lat / 10_000_000.0; val userLon = bsm.long / 10_000_000.0
        val userSpeed = bsm.speed * 0.02f; val userHeading = bsm.heading * 0.0125
        val objectLat = psm.position.latitude / 10_000_000.0; val objectLon = psm.position.longitude / 10_000_000.0
        val objectSpeed = psm.speed * 0.02; val objectId = psm.id
        val objType = if (psm.basicType.contains("PEDESTRIAN", ignoreCase = true)) "HUMAN" else "BIKE"
        val bearingToObject = calculateBearing(userLat, userLon, objectLat, objectLon)
        val relativeAngle = normalizeAngle(bearingToObject - userHeading)
        val dirString = when {
            relativeAngle >= -45 && relativeAngle < 45 -> "front"
            relativeAngle >= 45 && relativeAngle < 135 -> "right"
            relativeAngle >= 135 || relativeAngle < -135 -> "rear"
            else -> "left"
        }
        val objectCoords = Notification.Coordinates(latitude = objectLat, longitude = objectLon, speed = objectSpeed)
        val driverData = Notification.Driver(objectId, "low", dirString, objType, objectCoords)
        val convertedNotif = Notification(driverData, Notification.Location(userLat, userLon), userSpeed, Instant.now().toString())
        val ttc = NotificationUtils.timeToCollision(convertedNotif)
        convertedNotif.driver_data?.risk_level = if (ttc != null) {
            when {
                ttc < 4.0 -> "high"; ttc <= 8.0 -> "medium"; else -> "low"
            }
        } else "low"
        return convertedNotif
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1); val lat2Rad = Math.toRadians(lat2)
        val deltaLonRad = Math.toRadians(lon2 - lon1)
        val y = Math.sin(deltaLonRad) * Math.cos(lat2Rad)
        val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLonRad)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }

    private fun normalizeAngle(angle: Double): Double {
        var a = angle % 360
        if (a > 180) a -= 360
        if (a <= -180) a += 360
        return a
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}