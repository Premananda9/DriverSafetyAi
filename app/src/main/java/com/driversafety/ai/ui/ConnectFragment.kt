package com.driversafety.ai.ui

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.driversafety.ai.R
import com.driversafety.ai.connectivity.BluetoothConnectionManager
import com.driversafety.ai.connectivity.WiFiConnectionManager
import com.driversafety.ai.databinding.FragmentConnectBinding
import com.driversafety.ai.service.ForegroundMonitorService
import com.driversafety.ai.utils.AppPreferenceManager
import com.driversafety.ai.utils.PermissionManager

class ConnectFragment : Fragment() {

    private var _binding: FragmentConnectBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPreferenceManager
    private lateinit var btManager: BluetoothConnectionManager
    private lateinit var wifiManager: WiFiConnectionManager
    private lateinit var broadcaster: LocalBroadcastManager
    private lateinit var btAdapter: BluetoothDeviceAdapter

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ForegroundMonitorService.ACTION_CONNECTION_CHANGE) {
                val connected = intent.getBooleanExtra(ForegroundMonitorService.EXTRA_CONNECTED, false)
                val deviceName = intent.getStringExtra(ForegroundMonitorService.EXTRA_DEVICE_NAME) ?: ""
                
                if (connected) {
                    Toast.makeText(requireContext(), "Connected to ESP32! Switching to Dashboard...", Toast.LENGTH_LONG).show()
                    try {
                        findNavController().navigate(R.id.dashboardFragment)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = AppPreferenceManager(requireContext())
        btManager = BluetoothConnectionManager(requireContext())
        wifiManager = WiFiConnectionManager()
        broadcaster = LocalBroadcastManager.getInstance(requireContext())

        setupBluetoothSection()
        setupWifiSection()
        loadSavedPrefs()
    }

    private fun setupBluetoothSection() {
        btAdapter = BluetoothDeviceAdapter(emptyList()) { device ->
            connectToBluetoothDevice(device)
        }
        binding.rvBluetoothDevices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = btAdapter
        }

        binding.btnScanBluetooth.setOnClickListener {
            scanForDevices()
        }

        // Initial scan
        scanForDevices()
    }

    private fun scanForDevices() {
        if (!PermissionManager.hasBluetoothPermissions(requireContext())) {
            PermissionManager.requestBluetooth(requireActivity())
            return
        }

        if (!btManager.isBluetoothEnabled()) {
            Toast.makeText(context, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnScanBluetooth.text = "Scanning…"
        binding.btnScanBluetooth.isEnabled = false

        val devices = btManager.getPairedDevices()

        binding.btnScanBluetooth.text = "Scan"
        binding.btnScanBluetooth.isEnabled = true

        if (devices.isEmpty()) {
            binding.rvBluetoothDevices.visibility = View.GONE
            binding.tvNoBtDevices.visibility = View.VISIBLE
        } else {
            binding.rvBluetoothDevices.visibility = View.VISIBLE
            binding.tvNoBtDevices.visibility = View.GONE
            btAdapter.updateDevices(devices)
        }
    }

    private fun connectToBluetoothDevice(device: BluetoothDevice) {
        val deviceName = try { device.name ?: device.address } catch (e: SecurityException) { device.address }

        // Save as last connected
        prefs.lastBtAddress = device.address
        prefs.lastBtName = deviceName
        prefs.connectionMode = AppPreferenceManager.MODE_BLUETOOTH

        // Send command to foreground service
        val intent = Intent(requireContext(), ForegroundMonitorService::class.java).apply {
            action = ForegroundMonitorService.CMD_START_BT
            putExtra(ForegroundMonitorService.EXTRA_BT_ADDRESS, device.address)
        }
        requireContext().startForegroundService(intent)
    }



    private fun setupWifiSection() {
        binding.etWifiIp.setText(prefs.wifiIp)
        binding.etWifiPort.setText(prefs.wifiPort)

        binding.btnConnectWifi.setOnClickListener {
            val ip = binding.etWifiIp.text.toString().trim()
            val port = binding.etWifiPort.text.toString().trim().ifEmpty { "8080" }

            if (ip.isEmpty()) {
                binding.etWifiIp.error = "Enter IP address"
                return@setOnClickListener
            }

            prefs.wifiIp = ip
            prefs.wifiPort = port

            val mode = binding.btnConnectWifi.tag?.toString() ?: "http"
            val action = if (mode == "ws")
                ForegroundMonitorService.CMD_START_WS else ForegroundMonitorService.CMD_START_WIFI

            prefs.connectionMode = if (mode == "ws")
                AppPreferenceManager.MODE_WEBSOCKET else AppPreferenceManager.MODE_WIFI

            val intent = Intent(requireContext(), ForegroundMonitorService::class.java).apply {
                this.action = action
                putExtra(ForegroundMonitorService.EXTRA_WIFI_IP, ip)
                putExtra(ForegroundMonitorService.EXTRA_WIFI_PORT, port)
            }
            requireContext().startForegroundService(intent)
        }
    }

    private fun loadSavedPrefs() {
        val savedIp = prefs.wifiIp
        val savedPort = prefs.wifiPort
        if (savedIp.isNotEmpty()) {
            binding.etWifiIp.setText(savedIp)
        }
        if (savedPort.isNotEmpty()) {
            binding.etWifiPort.setText(savedPort)
        }
    }


    override fun onResume() {
        super.onResume()
        broadcaster.registerReceiver(
            connectionReceiver,
            IntentFilter(ForegroundMonitorService.ACTION_CONNECTION_CHANGE)
        )
        
        // Restore visual state immediately
        if (ForegroundMonitorService.isCurrentlyConnected) {
            // Updated UI via connectionReceiver
        }
    }

    override fun onPause() {
        super.onPause()
        broadcaster.unregisterReceiver(connectionReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
