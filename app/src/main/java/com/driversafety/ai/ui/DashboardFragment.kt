package com.driversafety.ai.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.driversafety.ai.databinding.FragmentDashboardBinding
import com.driversafety.ai.location.LocationManager
import com.driversafety.ai.service.ForegroundMonitorService
import com.driversafety.ai.utils.AppPreferenceManager
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPreferenceManager
    private lateinit var broadcaster: LocalBroadcastManager
    private lateinit var locationManager: LocationManager


    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

    // Weather
    private val weatherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Free weather API - no key needed
    private val WEATHER_API = "https://wttr.in/?format=j1"


    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ForegroundMonitorService.ACTION_DROWSINESS_DATA) {
                val value = intent.getIntExtra(ForegroundMonitorService.EXTRA_VALUE, 0)
                val level = intent.getIntExtra(ForegroundMonitorService.EXTRA_LEVEL, 0)
                updateDrowsinessUI(value, level)
            }
        }
    }

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ForegroundMonitorService.ACTION_CONNECTION_CHANGE) {
                val connected = intent.getBooleanExtra(ForegroundMonitorService.EXTRA_CONNECTED, false)
                val deviceName = intent.getStringExtra(ForegroundMonitorService.EXTRA_DEVICE_NAME) ?: ""
                updateConnectionUI(connected, deviceName)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = AppPreferenceManager(requireContext())
        broadcaster = LocalBroadcastManager.getInstance(requireContext())
        locationManager = LocationManager(requireContext())

        setupButtons()
        updateTimeDisplay()
        fetchWeather()

        // Manual test by tapping gauge
        binding.gaugeView.setOnClickListener {
            showManualInputDialog()
        }
    }

    private fun setupButtons() {
        binding.btnCoffee.setOnClickListener {
            locationManager.openNearbyCoffee()
        }
    }


    // ========== WEATHER ==========

    private fun fetchWeather() {
        weatherScope.launch {
            try {
                // Get location for accurate city-based weather
                val url = URL(WEATHER_API)
                val response = url.readText()
                val json = JSONObject(response)

                val current = json.getJSONArray("weather")
                    .getJSONObject(0)
                    .getJSONArray("hourly")
                    .getJSONObject(0)

                val tempC = current.getInt("tempC")
                val humidity = current.getInt("humidity")
                val weatherCode = current.getInt("weatherCode")
                val desc = current.getJSONArray("weatherDesc")
                    .getJSONObject(0).getString("value")

                // Determine driving risk & icon from weather code
                val (icon, risk, riskColor, riskBg) = getWeatherRisk(weatherCode, tempC)

                withContext(Dispatchers.Main) {
                    _binding ?: return@withContext
                    binding.tvWeatherIcon.text = icon
                    binding.tvWeatherTemp.text = "$tempC°C"
                    binding.tvWeatherDesc.text = desc
                    binding.tvWeatherHumidity.text = "Humidity: $humidity%"
                    binding.tvWeatherRisk.text = risk
                    binding.tvWeatherRisk.setTextColor(Color.WHITE)
                    binding.tvWeatherRisk.setBackgroundColor(Color.parseColor(riskColor))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _binding ?: return@withContext
                    binding.tvWeatherDesc.text = "Weather unavailable"
                    binding.tvWeatherTemp.text = "--°C"
                }
            }
        }
    }

    /**
     * Returns (emoji icon, risk text, risk color hex, badge drawable).
     * Weather codes: https://www.worldweatheronline.com/developer/api/docs/weather-icons.aspx
     */
    private fun getWeatherRisk(code: Int, tempC: Int): Array<String> {
        return when (code) {
            // Sunny / Clear
            113 -> arrayOf("☀️", "Low Risk", "#00D4AA", "")
            // Partly Cloudy / Cloudy
            116, 119, 122 -> arrayOf("⛅", "Low Risk", "#00D4AA", "")
            // Mist / Fog
            143, 248, 260 -> arrayOf("🌫️", "High Risk", "#FF1744", "")
            // Rain / Drizzle
            176, 263, 266, 293, 296, 299, 302, 308, 353, 356 ->
                arrayOf("🌧️", "Moderate Risk", "#FFB300", "")
            // Heavy Rain / Thunderstorm
            305, 359, 386, 389, 392, 395 -> arrayOf("⛈️", "High Risk", "#FF1744", "")
            // Snow
            179, 182, 185, 317, 320, 323, 326, 329, 332, 335, 338 ->
                arrayOf("❄️", "High Risk", "#FF1744", "")
            else -> if (tempC > 38) arrayOf("🥵", "Fatigue Risk", "#FFB300", "")
                    else arrayOf("🌤️", "Low Risk", "#00D4AA", "")
        }
    }

    // ========== DROWSINESS UI ==========

    private fun updateDrowsinessUI(value: Int, level: Int) {
        _binding ?: return
        binding.tvSensorValue.text = "${value}s"

        binding.gaugeView.setValue(value, prefs.criticalThreshold)

        when (level) {
            ForegroundMonitorService.LEVEL_SAFE -> {
                binding.tvDrowsinessLevel.text = "SAFE"
                binding.tvDrowsinessLevel.setBackgroundResource(
                    com.driversafety.ai.R.drawable.bg_badge_safe)
                binding.tvSensorValue.setTextColor(Color.parseColor("#00D4AA"))
            }
            ForegroundMonitorService.LEVEL_MODERATE -> {
                binding.tvDrowsinessLevel.text = "DROWSY - Level 1"
                binding.tvDrowsinessLevel.setBackgroundResource(
                    com.driversafety.ai.R.drawable.bg_badge_warning)
                binding.tvSensorValue.setTextColor(Color.parseColor("#FFB300"))
            }
            ForegroundMonitorService.LEVEL_CRITICAL -> {
                binding.tvDrowsinessLevel.text = "CRITICAL - Level 2"
                binding.tvDrowsinessLevel.setBackgroundResource(
                    com.driversafety.ai.R.drawable.bg_badge_critical)
                binding.tvSensorValue.setTextColor(Color.parseColor("#FF1744"))
            }
        }
    }

    private fun updateConnectionUI(connected: Boolean, deviceName: String) {
        _binding ?: return
        if (connected) {
            binding.tvConnectionStatus.text = "Connected"
            binding.tvConnectionStatus.setTextColor(Color.parseColor("#00D4AA"))
            binding.tvDeviceName.text = deviceName
            binding.viewStatusDot.setBackgroundResource(
                com.driversafety.ai.R.drawable.bg_dot_connected)
        } else {
            binding.tvConnectionStatus.text = "Disconnected"
            binding.tvConnectionStatus.setTextColor(Color.parseColor("#FF1744"))
            binding.tvDeviceName.text = "No device connected"
            binding.viewStatusDot.setBackgroundResource(
                com.driversafety.ai.R.drawable.bg_dot_disconnected)
        }
    }


    // ========== TIME / MISC ==========

    private fun updateTimeDisplay() {
        binding.tvTime.text = timeFormat.format(Date())
        binding.tvDate.text = dateFormat.format(Date())
    }

    private fun showManualInputDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Enter seconds (e.g. 3)"
            setPadding(48, 24, 48, 24)
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Simulate Sensor Data")
            .setMessage("Enter eye-closed seconds to test:")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val str = input.text.toString()
                if (str.isNotEmpty()) {
                    val value = str.toInt()
                    val mod = prefs.moderateThreshold
                    val crit = prefs.criticalThreshold
                    val level = when {
                        value >= crit -> ForegroundMonitorService.LEVEL_CRITICAL
                        value >= mod -> ForegroundMonitorService.LEVEL_MODERATE
                        else -> ForegroundMonitorService.LEVEL_SAFE
                    }
                    broadcaster.sendBroadcast(
                        Intent(ForegroundMonitorService.ACTION_DROWSINESS_DATA).apply {
                            putExtra(ForegroundMonitorService.EXTRA_VALUE, value)
                            putExtra(ForegroundMonitorService.EXTRA_LEVEL, level)
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateTimeDisplay()
        broadcaster.registerReceiver(
            dataReceiver,
            IntentFilter(ForegroundMonitorService.ACTION_DROWSINESS_DATA)
        )
        broadcaster.registerReceiver(
            connectionReceiver,
            IntentFilter(ForegroundMonitorService.ACTION_CONNECTION_CHANGE)
        )
        if (ForegroundMonitorService.isCurrentlyConnected) {
            updateConnectionUI(true, ForegroundMonitorService.currentDeviceName)
        }
    }

    override fun onPause() {
        super.onPause()
        broadcaster.unregisterReceiver(dataReceiver)
        broadcaster.unregisterReceiver(connectionReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        weatherScope.cancel()
        _binding = null
    }
}
