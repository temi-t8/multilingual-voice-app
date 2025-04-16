package ca.mohawk.temi8

import android.util.Log
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Object managing a WebSocket connection to a backend server for sending/receiving messages.
 */
object WebSocketManager {
    private const val TAG = "WebSocketManager"
    private const val SERVER_URL = "wss://backend-server-lyg7.onrender.com/ws" // Replace with actual IP!

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .pingInterval(10, TimeUnit.SECONDS)
            .build()
    }

    private var webSocket: WebSocket? = null
    private var isConnected = false

    /**
     * Callback invoked when a new message is received from the server.
     */
    var onMessageReceived: ((String) -> Unit)? = null

    /**
     * Establishes a connection to the WebSocket server if not already connected.
     */
    fun connect() {
        if (isConnected) return // Prevent duplicate connections

        val request = Request.Builder().url(SERVER_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnected = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                onMessageReceived?.invoke(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $reason")
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closed: $reason")
                isConnected = false
                this@WebSocketManager.webSocket = null
                reconnectWithDelay()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Error: ${t.message}")
                isConnected = false
                this@WebSocketManager.webSocket = null
                reconnectWithDelay()
            }
        })
    }
    
    /**
     * Sends a message through the WebSocket if connected.
     * @param message The text to be sent.
     */
    fun sendMessage(message: String) {
        if (isConnected) {
            webSocket?.send(message)
        } else {
            Log.w(TAG, "Message not sent: WebSocket is not connected")
        }
    }
    
    /**
     * Closes the WebSocket connection gracefully.
     */
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isConnected = false
    }

    /**
     * Checks if the WebSocket is currently connected.
     * @return True if connected, False otherwise.
     */
    fun isWebSocketConnected(): Boolean = isConnected

    /**
     * Attempts to reconnect to the WebSocket server with a 5-second delay if not connected.
     */
    private fun reconnectWithDelay() {
        Log.d(TAG, "Attempting to reconnect in 5 seconds...")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isConnected) connect()
        }, 5000)
    }
}
