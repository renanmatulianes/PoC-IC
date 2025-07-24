// ARQUIVO MODIFICADO: MainActivity.kt
package com.example.app

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app.model.Notification
import okhttp3.internal.http2.Http2Reader.Handler

enum class Direction { LEFT, RIGHT, TOP, BOTTOM, NULL }
enum class Objects { HUMAN, VEHICLE, MOTORCYCLE, BIKE, NULL }

class MainActivity : AppCompatActivity(),
    WebSocketManager.WebSocketEventCallback,
    NotificationProcessor.NotificationActionCallback {

    private lateinit var notificationUIManager: NotificationUIManager
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var notificationProcessor: NotificationProcessor

    private val serverIp = "10.0.2.2" // Ou o IP que você precisar
    private val serverUrl = "ws://$serverIp:3001"
    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        notificationUIManager = NotificationUIManager(this)
        notificationProcessor = NotificationProcessor(this)
        webSocketManager = WebSocketManager(serverUrl, this)

        val prefs = getSharedPreferences("driverPref", MODE_PRIVATE)
        userId = prefs.getInt("userId", -1)

        webSocketManager.connect(userId)

        findViewById<ImageView>(R.id.settingsIcon).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager.close()
        notificationUIManager.stopCurrentNotification()
    }

    override fun onConnected() {
        toast("Conectado ao servidor!")
    }

    override fun onNewMessage(text: String) {
        notificationProcessor.processMessage(text)
    }

    override fun onConnectionError(reason: String) {
        toast(reason)
    }

    override fun onShowNotification(notification: Notification, ttc: Double?) {
        notificationUIManager.showNotification(notification, ttc)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}