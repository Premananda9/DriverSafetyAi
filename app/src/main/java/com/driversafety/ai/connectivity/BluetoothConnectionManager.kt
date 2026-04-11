package com.driversafety.ai.connectivity

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Classic Bluetooth (SPP) connection to ESP32.
 * - Persistent connection that NEVER drops unless user explicitly disconnects
 * - Unlimited auto-reconnect with exponential backoff
 * - Streams incoming data line-by-line as a SharedFlow
 */
class BluetoothConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "BTManager"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val READ_BUFFER_SIZE = 1024
        private const val RECONNECT_DELAY_MS = 3000L  // 3s between retries
        private const val MAX_RECONNECT_DELAY_MS = 30000L // max 30s wait
    }

    enum class State {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state

    private val _incomingData = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingData: SharedFlow<String> = _incomingData

    private val _connectedDeviceName = MutableStateFlow("")
    val connectedDeviceName: StateFlow<String> = _connectedDeviceName

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var connectionJob: Job? = null

    // The scope uses SupervisorJob so one child failure doesn't cancel others
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track the last device the user explicitly wanted to connect to
    private var targetDevice: BluetoothDevice? = null
    
    // Use AtomicBoolean for thread-safe disconnection tracking
    private val isUserDisconnected = AtomicBoolean(false)

    // ========== PUBLIC API ==========

    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            emptyList()
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /**
     * Connect to a device. Will keep retrying forever until connected or disconnect() is called.
     */
    fun connect(device: BluetoothDevice, autoReconnectEnabled: Boolean = true) {
        isUserDisconnected.set(false)
        targetDevice = device
 
        // Cancel any existing job first
        connectionJob?.cancel()
 
        connectionJob = scope.launch {
            persistentConnect(device)
        }
    }

    /**
     * Explicitly disconnect — this is the ONLY way the connection should drop.
     */
    fun disconnect() {
        isUserDisconnected.set(true)
        targetDevice = null
        connectionJob?.cancel()
        closeSocket()
        _state.value = State.DISCONNECTED
        _connectedDeviceName.value = ""
        Log.d(TAG, "User explicitly disconnected.")
    }

    fun isConnected(): Boolean = _state.value == State.CONNECTED

    // ========== CORE CONNECTION LOOP ==========

    /**
     * The main persistent connection loop.
     * It will NEVER exit until userDisconnected is true.
     */
    private suspend fun persistentConnect(device: BluetoothDevice) {
        val deviceName = getDeviceName(device)
        var attempt = 0
 
        while (!isUserDisconnected.get()) {
            attempt++
            Log.d(TAG, "Connection attempt #$attempt line to $deviceName")
 
            if (attempt == 1) {
                _state.value = State.CONNECTING
            } else {
                _state.value = State.RECONNECTING
            }
 
            val connected = tryConnect(device)
 
            if (connected) {
                attempt = 0  // Reset counter on successful connection
                _state.value = State.CONNECTED
                _connectedDeviceName.value = deviceName
                Log.i(TAG, "STABLE CONNECTION: Connected to $deviceName")
 
                // Block here reading data until the connection drops
                readLoop()
 
                // readLoop exited = disconnected. If user asked to disconnect, stop.
                if (isUserDisconnected.get()) break
 
                Log.w(TAG, "LOST CONNECTION to $deviceName. Reconnecting automatically...")
                closeSocket()
                // Give the BT stack a moment to breathe before retrying
                delay(1000L)
            }
 
            if (!isUserDisconnected.get()) {
                // Wait before next attempt, with a cap
                val delayTime = minOf(RECONNECT_DELAY_MS * attempt, MAX_RECONNECT_DELAY_MS)
                Log.d(TAG, "Waiting ${delayTime}ms before retry #${attempt + 1}")
                delay(delayTime)
            }
        }
 
        _state.value = State.DISCONNECTED
        _connectedDeviceName.value = ""
        Log.d(TAG, "persistentConnect loop exited cleanly.")
    }

    private suspend fun tryConnect(device: BluetoothDevice): Boolean {
        return try {
            if (hasBluetoothPermission()) {
                try { bluetoothAdapter?.cancelDiscovery() } catch (e: SecurityException) {}
            }
            closeSocket()
            val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            newSocket.connect()
            socket = newSocket
            inputStream = newSocket.inputStream
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission error connecting: ${e.message}")
            false
        } catch (e: IOException) {
            Log.e(TAG, "IOException connecting: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error connecting: ${e.message}")
            false
        }
    }

    /**
     * Blocking read loop. Returns when the connection drops.
     */
    private suspend fun readLoop() {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        val lineBuffer = StringBuilder()
 
        while (!isUserDisconnected.get()) {
            try {
                val stream = inputStream ?: break
 
                // This blocks until data arrives — no busy-looping
                val bytes = stream.read(buffer)

                if (bytes == -1) {
                    // Stream ended cleanly
                    Log.w(TAG, "Stream returned -1 (remote closed connection)")
                    break
                }

                if (bytes > 0) {
                    val chunk = String(buffer, 0, bytes)
                    lineBuffer.append(chunk)

                    // Parse complete newline-terminated lines
                    var newlineIdx: Int
                    while (lineBuffer.indexOf('\n').also { newlineIdx = it } != -1) {
                        val line = lineBuffer.substring(0, newlineIdx).trim()
                        lineBuffer.delete(0, newlineIdx + 1)
                        if (line.isNotEmpty()) {
                            Log.v(TAG, "Rx: $line")
                            _incomingData.tryEmit(line)
                        }
                    }
                }
 
            } catch (e: IOException) {
                if (!isUserDisconnected.get()) {
                    Log.e(TAG, "Read IOException (connection lost): ${e.message}")
                }
                break  // Exit readLoop — persistentConnect will retry
            }
        }
    }

    // ========== HELPERS ==========

    private fun closeSocket() {
        try { inputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        inputStream = null
    }

    private fun getDeviceName(device: BluetoothDevice): String {
        return if (hasBluetoothPermission()) {
            try { device.name ?: device.address } catch (e: SecurityException) { device.address }
        } else device.address
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun release() {
        disconnect()
        scope.cancel()
    }
}
