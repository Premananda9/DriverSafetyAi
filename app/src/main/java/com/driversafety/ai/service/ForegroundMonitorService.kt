package com.driversafety.ai.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.driversafety.ai.MainActivity
import com.driversafety.ai.R
import com.driversafety.ai.alert.TTSManager
import com.driversafety.ai.connectivity.BluetoothConnectionManager
import com.driversafety.ai.connectivity.WiFiConnectionManager
import com.driversafety.ai.utils.AppPreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Persistent Foreground Service that:
 *   1. Maintains BT/WiFi connection to ESP32
 *   2. Parses incoming drowsiness values
 *   3. Broadcasts alerts to Activities
 *   4. Shows persistent notification
 */
class ForegroundMonitorService : Service() {

    companion object {
        private const val TAG = "MonitorService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "driver_safety_monitor"

        // Global state for newly created fragments to query instantly
        var isCurrentlyConnected = false
        var currentDeviceName = ""

        // Intent action for broadcasts
        const val ACTION_DROWSINESS_DATA = "com.driversafety.ai.DROWSINESS_DATA"
        const val ACTION_CONNECTION_CHANGE = "com.driversafety.ai.CONNECTION_CHANGE"
        const val ACTION_TRIGGER_ALERT_SCREEN = "com.driversafety.ai.TRIGGER_ALERT_SCREEN" // Rate-limited
        const val EXTRA_VALUE = "extra_value"
        const val EXTRA_LEVEL = "extra_level"
        const val EXTRA_CONNECTED = "extra_connected"
        const val EXTRA_DEVICE_NAME = "extra_device_name"

        // Alert levels
        const val LEVEL_SAFE = 0
        const val LEVEL_MODERATE = 1
        const val LEVEL_CRITICAL = 2

        // Service commands
        const val CMD_START_BT = "cmd_start_bt"
        const val CMD_START_WIFI = "cmd_start_wifi"
        const val CMD_START_WS = "cmd_start_ws"
        const val CMD_STOP = "cmd_stop"
        const val EXTRA_BT_ADDRESS = "extra_bt_address"
        const val EXTRA_WIFI_IP = "extra_wifi_ip"
        const val EXTRA_WIFI_PORT = "extra_wifi_port"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: AppPreferenceManager
    private lateinit var btManager: BluetoothConnectionManager
    private lateinit var wifiManager: WiFiConnectionManager
    private lateinit var broadcaster: LocalBroadcastManager
    private lateinit var tts: TTSManager

    private var lastAlertLevel = LEVEL_SAFE
    private var alertCooldownMs = 30_000L // 30 seconds between same-level alerts
    private var lastAlertTime = 0L

    override fun onCreate() {
        super.onCreate()
        // CRITICAL: startForeground must be called IMMEDIATELY (within 5s of startForegroundService)
        // Do this before any other initialization that might be slow
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= 34) { // Android 14+
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification("Monitoring…"),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification("Monitoring…"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground: ${e.message}")
            // Still try without type flags as a fallback
            try { startForeground(NOTIFICATION_ID, buildNotification("Monitoring…")) } catch (_: Exception) {}
        }

        // Now safe to initialize the rest
        prefs = AppPreferenceManager(this)
        btManager = BluetoothConnectionManager(this)
        wifiManager = WiFiConnectionManager()
        broadcaster = LocalBroadcastManager.getInstance(this)
        tts = TTSManager(this)
        Log.d(TAG, "ForegroundMonitorService created")

        // Observe BT data
        scope.launch {
            btManager.incomingData.collect { raw ->
                processRawData(raw)
            }
        }

        // Observe BT state
        scope.launch {
            btManager.state.collectLatest { state ->
                val connected = state == BluetoothConnectionManager.State.CONNECTED
                broadcastConnectionChange(connected, btManager.connectedDeviceName.value)
                updateNotification(if (connected) "Connected via Bluetooth" else "Disconnected")
            }
        }

        // Observe WiFi data
        scope.launch {
            wifiManager.incomingData.collect { raw ->
                raw?.let { processRawData(it) }
            }
        }

        // Observe WiFi state
        scope.launch {
            wifiManager.state.collectLatest { state ->
                val connected = state == WiFiConnectionManager.State.CONNECTED
                broadcastConnectionChange(connected, "ESP32 (WiFi)")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CMD_START_BT -> {
                val address = intent.getStringExtra(EXTRA_BT_ADDRESS) ?: return START_STICKY
                val paired = btManager.getPairedDevices()
                val device = paired.find { it.address == address }
                device?.let {
                    btManager.connect(it, prefs.autoReconnect)
                }
            }
            CMD_START_WIFI -> {
                val ip = intent.getStringExtra(EXTRA_WIFI_IP) ?: return START_STICKY
                val port = intent.getStringExtra(EXTRA_WIFI_PORT) ?: "8080"
                wifiManager.connectHttp(ip, port)
            }
            CMD_START_WS -> {
                val ip = intent.getStringExtra(EXTRA_WIFI_IP) ?: return START_STICKY
                val port = intent.getStringExtra(EXTRA_WIFI_PORT) ?: "8080"
                wifiManager.connectWebSocket(ip, port)
            }
            CMD_STOP -> {
                btManager.disconnect()
                wifiManager.disconnect()
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ========== DATA PROCESSING ==========

    private fun processRawData(raw: String) {
        val value = parseValue(raw)
        if (value == null) {
            Log.w(TAG, "Could not parse value from: $raw")
            return
        }

        val moderateThreshold = prefs.moderateThreshold
        val criticalThreshold = prefs.criticalThreshold

        val level = when {
            value >= criticalThreshold -> LEVEL_CRITICAL
            value >= moderateThreshold -> LEVEL_MODERATE
            else -> LEVEL_SAFE
        }

        Log.d(TAG, "Drowsiness value: $value → Level $level")

        // Broadcast raw data to dashboard
        broadcaster.sendBroadcast(Intent(ACTION_DROWSINESS_DATA).apply {
            putExtra(EXTRA_VALUE, value)
            putExtra(EXTRA_LEVEL, level)
        })

        // Trigger alert if level changed or cooldown expired
        val now = System.currentTimeMillis()
        if (level > LEVEL_SAFE && (level != lastAlertLevel || now - lastAlertTime > alertCooldownMs)) {
            lastAlertLevel = level
            lastAlertTime = now
            triggerAlert(level, value)
        } else if (level == LEVEL_SAFE) {
            lastAlertLevel = LEVEL_SAFE
        }
    }

    private fun parseValue(raw: String): Int? {
        val trimmed = raw.trim()
        // Try plain int
        trimmed.toIntOrNull()?.let { return it }
        // Try JSON {"value":1234}
        try {
            val json = org.json.JSONObject(trimmed)
            return json.optInt("value", -1).takeIf { it >= 0 }
        } catch (e: Exception) {}
        // Try extracting first number in string
        return Regex("\\d+").find(trimmed)?.value?.toIntOrNull()
    }

    private fun triggerAlert(level: Int, value: Int) {
        when (level) {
            LEVEL_MODERATE -> {
                // Level 1 — TTS voice warning ONLY, no screen popup
                tts.speakDrowsy()
                updateNotification("⚠ Drowsy detected — Eyes closed for ${value}s")
                showNotificationAlert("Drowsiness Warning", "You are feeling drowsy. Eyes closed for ${value}s")
            }
            LEVEL_CRITICAL -> {
                // Level 2 — broadcast to launch Emergency Screen
                broadcaster.sendBroadcast(Intent(ACTION_TRIGGER_ALERT_SCREEN).apply {
                    putExtra(EXTRA_VALUE, value)
                    putExtra(EXTRA_LEVEL, level)
                })
                updateNotification("🚨 CRITICAL — Stop driving immediately!")
                showNotificationAlert("CRITICAL ALERT", "Stop driving NOW! Eyes closed for ${value}s")
            }
        }
    }

    // ========== BROADCASTS ==========

    private fun broadcastConnectionChange(connected: Boolean, deviceName: String) {
        isCurrentlyConnected = connected
        currentDeviceName = deviceName
        broadcaster.sendBroadcast(Intent(ACTION_CONNECTION_CHANGE).apply {
            putExtra(EXTRA_CONNECTED, connected)
            putExtra(EXTRA_DEVICE_NAME, deviceName)
        })
    }

    // ========== NOTIFICATIONS ==========

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Driver Safety Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background drowsiness monitoring"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Driver Safety AI")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield_car)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun showNotificationAlert(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                "driver_safety_alerts",
                "Drowsiness Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                enableLights(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(alertChannel)

            val pendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, "driver_safety_alerts")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            manager.notify(1002, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        btManager.release()
        wifiManager.release()
        tts.release()
        Log.d(TAG, "ForegroundMonitorService destroyed")
    }
}
