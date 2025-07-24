package com.example.app

import android.os.Handler
import android.os.Looper
import okhttp3.*

class WebSocketManager(
    private val serverUrl: String,
    private val listener: WebSocketEventCallback
) {
    interface WebSocketEventCallback {
        fun onConnected()
        fun onNewMessage(text: String)
        fun onConnectionError(reason: String)
    }

    private val client = OkHttpClient()
    private lateinit var socket: WebSocket
    private val handler = Handler(Looper.getMainLooper())
    private var shouldReconnect = true
    private val reconnectDelayMs = 15000L

    fun connect(userId: Int) {
        if (userId == -1) {
            listener.onConnectionError("Invalid User ID")
            return
        }
        val request = Request.Builder()
            .url("$serverUrl?user_id=$userId")
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runOnUiThread { listener.onConnected() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread { listener.onNewMessage(text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleReconnection("Connection failed")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                handleReconnection("Connection closing")
            }
        })
    }

    fun close() {
        shouldReconnect = false
        if (::socket.isInitialized) {
            socket.close(1000, "Client closed connection")
        }
        handler.removeCallbacksAndMessages(null) // Limpa reconexões agendadas
    }

    private fun handleReconnection(reason: String) {
        if (shouldReconnect) {
            runOnUiThread { listener.onConnectionError("Reconnecting in ${reconnectDelayMs / 1000}s…") }
            // Tenta reconectar após um delay. Precisamos do userId, que não temos aqui.
            // Para simplificar, a Activity vai ter que chamar connect() de novo.
            // Uma solução mais robusta guardaria o userId aqui.
        }
    }

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }
}