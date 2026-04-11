package com.driversafety.ai.connectivity

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Manages WiFi communication with ESP32 via:
 *   - HTTP polling (GET endpoint every 500ms)
 *   - WebSocket streaming (real-time push)
 *
 * ESP32 HTTP endpoint: GET http://<ip>:<port>/data → {"value": 1234}
 * ESP32 WebSocket:     ws://<ip>:<port>/ws → sends {"value": 1234} continuously
 */
class WiFiConnectionManager {

    companion object {
        private const val TAG = "WiFiManager"
        private const val POLL_INTERVAL_MS = 500L
        private const val CONNECTION_TIMEOUT_SEC = 5L
    }

    enum class Mode { HTTP, WEBSOCKET }
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state

    private val _incomingData = MutableStateFlow<String?>(null)
    val incomingData: StateFlow<String?> = _incomingData

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECTION_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(CONNECTION_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentIp: String = ""
    private var currentPort: String = "8080"
    private var currentMode: Mode = Mode.HTTP

    // ========== HTTP POLLING ==========

    fun connectHttp(ip: String, port: String) {
        currentIp = ip
        currentPort = port
        currentMode = Mode.HTTP
        _state.value = State.CONNECTING

        pollJob?.cancel()
        pollJob = scope.launch {
            val url = "http://$ip:$port/data"
            Log.d(TAG, "Starting HTTP polling: $url")

            // Test initial connection
            try {
                val response = okHttpClient.newCall(
                    Request.Builder().url(url).build()
                ).execute()
                if (response.isSuccessful) {
                    _state.value = State.CONNECTED
                } else {
                    _state.value = State.ERROR
                    return@launch
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initial HTTP connection failed: ${e.message}")
                _state.value = State.ERROR
                return@launch
            }

            // Polling loop
            while (isActive) {
                try {
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val parsed = parseValue(body)
                            if (parsed != null) {
                                _incomingData.value = parsed.toString()
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "HTTP poll error: ${e.message}")
                    if (_state.value == State.CONNECTED) {
                        _state.value = State.ERROR
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    // ========== WEBSOCKET STREAMING ==========

    fun connectWebSocket(ip: String, port: String) {
        currentIp = ip
        currentPort = port
        currentMode = Mode.WEBSOCKET
        _state.value = State.CONNECTING

        disconnectWebSocket()

        val wsUrl = "ws://$ip:$port/ws"
        Log.d(TAG, "Connecting WebSocket: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _state.value = State.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.v(TAG, "WS received: $text")
                val parsed = parseValue(text)
                if (parsed != null) {
                    _incomingData.value = parsed.toString()
                } else {
                    // Raw value
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty()) _incomingData.value = trimmed
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                _state.value = State.ERROR
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                _state.value = State.DISCONNECTED
            }
        })
    }

    // ========== DISCONNECT ==========

    fun disconnect() {
        pollJob?.cancel()
        disconnectWebSocket()
        _state.value = State.DISCONNECTED
    }

    private fun disconnectWebSocket() {
        try {
            webSocket?.close(1000, "Disconnected by user")
        } catch (e: Exception) {}
        webSocket = null
    }

    fun isConnected(): Boolean = _state.value == State.CONNECTED

    /**
     * Parse {"value": 1234} JSON or plain integer string.
     */
    private fun parseValue(raw: String): Int? {
        return try {
            // Try JSON
            JSONObject(raw).optInt("value", -1).takeIf { it >= 0 }
        } catch (e: Exception) {
            // Try plain number
            raw.trim().toIntOrNull()
        }
    }

    fun release() {
        scope.cancel()
        disconnect()
        okHttpClient.dispatcher.executorService.shutdown()
    }
}
