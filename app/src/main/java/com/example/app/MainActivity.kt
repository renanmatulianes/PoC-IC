package com.example.app

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app.model.CombinedNotification
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.net.Socket
import android.util.Log
import java.io.IOException
import java.lang.StringBuilder
import com.example.app.databinding.ActivityMainBinding
import com.example.app.databinding.NotificationChildZoneBinding
import com.example.app.model.TimNotification
import com.example.app.rules.effects.NotificationUI
import com.example.app.rules.Orchestrator
import com.example.app.rules.Rule
import com.example.app.rules.effects.*
import com.example.app.rules.filters.*
import com.example.app.rules.context.NotificationContext
import com.example.app.ui.VisualAlertManager
import com.example.app.processing.combinedToAppNotification

enum class Direction { LEFT, RIGHT, TOP, BOTTOM, NULL}
enum class Objects {HUMAN, VEHICLE, MOTORCYCLE, BIKE, NULL}
enum class ZonaTipo { CRIANCA, CICLISTA }

class MainActivity : AppCompatActivity(), NotificationUI {

    private lateinit var orchestrator: Orchestrator

    private var tcpSocket: Socket? = null
    private var connectionJob: Job? = null
    private var shouldReconnect = true
    private val reconnectDelayMs = 15000L

    private var zoneAlertSocket: Socket? = null
    private var zoneAlertConnectionJob: Job? = null

    private lateinit var binding: ActivityMainBinding
    private lateinit var visualAlertManager: VisualAlertManager

    private val moshi  = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val combinedNotificationAdapter = moshi.adapter(CombinedNotification::class.java)
    private val timNotificationAdapter = moshi.adapter(TimNotification::class.java)

    private val obuServerIp = "10.0.2.2" // 192.168.0.53
    private val obuServerPort = 3002 // 8080

    private val zoneAlertServerIp = "10.0.2.2"
    private val zoneAlertServerPort = 3003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val childZoneBinding = NotificationChildZoneBinding.bind(binding.root.findViewById(R.id.child_zone_notification_layout))

        visualAlertManager = VisualAlertManager(this, binding, childZoneBinding)

        orchestrator = Orchestrator(this)
        setupRules()

        connectToObuServer()
        connectToZoneAlertServer()

        val settingsButton = findViewById<ImageView>(R.id.settingsIcon)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

//        alerta_zona(true, ZonaTipo.CICLISTA, "Atenção: Área escolar próxima")
    }

    override fun onDestroy() {
        super.onDestroy()
        shouldReconnect = false
        connectionJob?.cancel()
        zoneAlertConnectionJob?.cancel()
        try {
            tcpSocket?.close()
        } catch (e: IOException) {
            Log.e("MainActivity", "Erro ao fechar o socket", e)
        }

        visualAlertManager.destroy()
    }

    private fun setupRules() {

        val PRIORITY_HIGH = 30
        val PRIORITY_MEDIUM = 20
        val PRIORITY_LOW = 10
        val PRIORITY_ZONE = 30

        // --- REGRA 1: ALERTA DE COLISÃO DE ALTO RISCO ---
        val highRiskRule = Rule(
            name = "High Risk Collision Alert",
            priority = PRIORITY_HIGH,
            rootFilter = RiskLevelFilter("high"),
            effects = listOf(
                StopPreviousAlertsEffect(), // Limpa alertas antigos primeiro
                VisualNotificationEffect(),
                SoundNotificationEffect()
                // Poderíamos adicionar um HapticFeedbackEffect aqui no futuro
            )
        )

        // --- REGRA 2: ALERTA DE COLISÃO DE MÉDIO RISCO ---
        val mediumRiskRule = Rule(
            name = "Medium Risk Collision Alert",
            priority = PRIORITY_MEDIUM,
            rootFilter = RiskLevelFilter("medium"),
            effects = listOf(
                StopPreviousAlertsEffect(),
                VisualNotificationEffect(),
                SoundNotificationEffect()
            )
        )

        // --- REGRA 3: ALERTA DE COLISÃO DE BAIXO RISCO ---
        val lowRiskRule = Rule(
            name = "Low Risk Collision Alert",
            priority = PRIORITY_LOW,
            rootFilter = RiskLevelFilter("low"),
            effects = listOf(
                StopPreviousAlertsEffect(),
                VisualNotificationEffect(),
                SoundNotificationEffect()
                // Poderíamos ter efeitos diferentes aqui, ex: só visual
            )
        )

        // --- REGRA 4: ALERTA DE ZONA ESCOLAR (TIM) ---
        val childZoneRule = Rule(
            name = "Child Zone Alert",
            priority = PRIORITY_ZONE,
            rootFilter = ZoneTypeFilter(ZonaTipo.CRIANCA),
            effects = listOf(
                StopPreviousAlertsEffect(), // Para o alerta de colisão, se houver
                ExpiringZoneAlertEffect(ZonaTipo.CRIANCA, durationMs = 10000L)
            )
        )

        // --- REGRA 5: ALERTA DE CICLISTA (TIM) ---
        val cyclistZoneRule = Rule(
            name = "Cyclist Zone Alert",
            priority = PRIORITY_ZONE,
            rootFilter = ZoneTypeFilter(ZonaTipo.CICLISTA),
            effects = listOf(
                StopPreviousAlertsEffect(),
                ExpiringZoneAlertEffect(ZonaTipo.CICLISTA, durationMs = 10000L)
            )
        )

        orchestrator.addRule(highRiskRule)
        orchestrator.addRule(mediumRiskRule)
        orchestrator.addRule(lowRiskRule)
        orchestrator.addRule(childZoneRule)
        orchestrator.addRule(cyclistZoneRule)

    }

    private fun connectToObuServer() {
        connectionJob = lifecycleScope.launch(Dispatchers.IO) {

            while (shouldReconnect) {
                try {
                    Log.d("TCP", "Tentando conectar a $obuServerIp:$obuServerPort...")
                    tcpSocket = Socket(obuServerIp, obuServerPort)

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
                                processObuMessage(completeJson)

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

    private suspend fun processObuMessage(jsonString: String) {
        val combinedNotification = try {
            combinedNotificationAdapter.fromJson(jsonString)
        } catch (e: Exception) {
            Log.e("JSON", "Erro ao fazer o parsing do JSON (OBU): $jsonString", e)
            return // Sai se o JSON for inválido
        }

        combinedNotification?.let { notifData ->
            val appNotification = combinedToAppNotification(notifData)

            val context = NotificationContext(psmBsmNotification = appNotification)

            withContext(Dispatchers.Main) {
                orchestrator.processContext(context)
            }
        }
    }

    private fun connectToZoneAlertServer() {
        zoneAlertConnectionJob = lifecycleScope.launch(Dispatchers.IO) {
            while (shouldReconnect) {
                try {
                    Log.d("TCP_Zone", "Tentando conectar a $zoneAlertServerIp:$zoneAlertServerPort...")
                    zoneAlertSocket = Socket(zoneAlertServerIp, zoneAlertServerPort)

                    withContext(Dispatchers.Main) {
                        toast("Conectado ao servidor de Alertas de Zona!")
                    }
                    Log.d("TCP_Zone", "Conexão de Alertas de Zona estabelecida.")

                    val reader = InputStreamReader(zoneAlertSocket!!.getInputStream())
                    val buffer = CharArray(4096)
                    val jsonBuffer = StringBuilder()
                    var charsRead: Int = 0

                    while (zoneAlertSocket!!.isConnected && reader.read(buffer).also { charsRead = it } != -1) {
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

                                Log.d("TCP", "JSON completo extraído: $completeJson")
                                processZoneAlertMessage(completeJson)

                            } else {
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!shouldReconnect) break
                    Log.e("TCP_Zone", "Erro de conexão com servidor de Alertas: ${e.message}")
                    withContext(Dispatchers.Main) {
                        toast("Erro nos Alertas de Zona. Reconectando...")
                    }
                    delay(reconnectDelayMs)
                } finally {
                    try {
                        zoneAlertSocket?.close()
                    } catch (e: IOException) {
                        // Log do erro
                    }
                }
            }
        }
    }

    private suspend fun processZoneAlertMessage(jsonString: String) {
        val timNotification = try {
            timNotificationAdapter.fromJson(jsonString)
        } catch (e: Exception) {
            Log.e("JSON_TIM", "Erro ao fazer o parsing do JSON de Alerta de Zona: $jsonString", e)
            return // Sai se o JSON for inválido
        }

        timNotification?.let {
            // 1. Cria o contexto com a notificação TIM.
            val context = NotificationContext(timNotification = it)

            // 2. Entrega ao orquestrador.
            withContext(Dispatchers.Main) {
                orchestrator.processContext(context)
            }

        }
    }

    override fun showVisualAlert(direction: Direction, intensity: Int, obj: Objects) {
        visualAlertManager.showVisualAlert(direction, intensity, obj)
    }

    override fun playSoundAlert(direction: Direction, intensity: Int, obj: Objects) {
        SoundManager.playSound(this, direction, obj, intensity)
    }

    override fun showZoneAlert(activate: Boolean, zoneType: ZonaTipo, message: String?) {
        visualAlertManager.displayZoneAlert(activate, zoneType, message)
    }

    override fun playZoneSound(zoneType: ZonaTipo) {
        SoundManager.playZoneSound(this, zoneType)
    }

    override fun stopAllAlerts() {
        visualAlertManager.stopAllVisuals()
        SoundManager.stop()
    }


    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }


