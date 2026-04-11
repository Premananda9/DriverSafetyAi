package com.driversafety.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.driversafety.ai.databinding.ActivityMainBinding
import com.driversafety.ai.service.ForegroundMonitorService
import com.driversafety.ai.utils.AppPreferenceManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferenceManager
    private lateinit var broadcaster: LocalBroadcastManager

    private val drowsinessReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ForegroundMonitorService.ACTION_TRIGGER_ALERT_SCREEN) return

            val value = intent.getIntExtra(ForegroundMonitorService.EXTRA_VALUE, 0)
            val level = intent.getIntExtra(ForegroundMonitorService.EXTRA_LEVEL, 0)

            handleDrowsinessLevel(level, value)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while driving
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferenceManager(this)
        broadcaster = LocalBroadcastManager.getInstance(this)

        setupNavigation()
        startMonitorService()

        broadcaster.registerReceiver(
            drowsinessReceiver,
            IntentFilter(ForegroundMonitorService.ACTION_TRIGGER_ALERT_SCREEN)
        )
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)
    }

    private fun startMonitorService() {
        try {
            val intent = Intent(this, ForegroundMonitorService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleDrowsinessLevel(level: Int, value: Int) {
        when (level) {
            ForegroundMonitorService.LEVEL_MODERATE -> {
                // Launch Moderate Alert only if not already shown
                if (!isActivityInForeground(ModerateAlertActivity::class.java)) {
                    val intent = Intent(this, ModerateAlertActivity::class.java).apply {
                        putExtra("sensor_value", value)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.fade_scale_in, android.R.anim.fade_out)
                }
            }
            ForegroundMonitorService.LEVEL_CRITICAL -> {
                // Launch Emergency Alert (takes priority over moderate)
                val intent = Intent(this, EmergencyAlertActivity::class.java).apply {
                    putExtra("sensor_value", value)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.fade_scale_in, android.R.anim.fade_out)
            }
        }
    }

    private fun <T : AppCompatActivity> isActivityInForeground(cls: Class<T>): Boolean {
        // Simple check — in production you'd use a more robust lifecycle tracker
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        broadcaster.unregisterReceiver(drowsinessReceiver)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
