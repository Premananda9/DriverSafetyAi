package com.driversafety.ai

import android.animation.ObjectAnimator
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.driversafety.ai.alert.BuzzerManager
import com.driversafety.ai.alert.TTSManager
import com.driversafety.ai.databinding.ActivityModerateAlertBinding
import com.driversafety.ai.location.LocationManager
import com.driversafety.ai.utils.AppPreferenceManager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.driversafety.ai.service.ForegroundMonitorService

class ModerateAlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModerateAlertBinding
    private lateinit var prefs: AppPreferenceManager
    private lateinit var tts: TTSManager
    private lateinit var buzzer: BuzzerManager
    private lateinit var locationManager: LocationManager
    private lateinit var broadcaster: LocalBroadcastManager

    private val criticalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ForegroundMonitorService.ACTION_TRIGGER_ALERT_SCREEN) return
            val level = intent.getIntExtra(ForegroundMonitorService.EXTRA_LEVEL, 0)
            val value = intent.getIntExtra(ForegroundMonitorService.EXTRA_VALUE, 0)
            if (level >= ForegroundMonitorService.LEVEL_CRITICAL) {
                
                // Directly launch Level 2 from the foreground to guarantee it doesn't get blocked!
                val emergencyIntent = Intent(this@ModerateAlertActivity, EmergencyAlertActivity::class.java).apply {
                    putExtra("sensor_value", value)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(emergencyIntent)

                // Instantly kill this Level 1 screen silently
                tts.stop()
                buzzer.stopAlarm()
                finish()
                overridePendingTransition(0, 0) // no transition glitch
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModerateAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferenceManager(this)
        tts = TTSManager(this)
        buzzer = BuzzerManager(this)
        locationManager = LocationManager(this)
        broadcaster = LocalBroadcastManager.getInstance(this)

        broadcaster.registerReceiver(
            criticalReceiver,
            IntentFilter(ForegroundMonitorService.ACTION_TRIGGER_ALERT_SCREEN)
        )

        val sensorValue = intent.getIntExtra("sensor_value", 0)

        setupUI(sensorValue)
        triggerModerateActions()
    }

    private fun setupUI(sensorValue: Int) {
        // Animate warning icon
        animatePulse(binding.ivWarningIcon)

        // Animate card entrance
        binding.cardSuggestion.alpha = 0f
        binding.cardSuggestion.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(400)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Buttons
        binding.btnOpenCoffee.setOnClickListener {
            locationManager.openNearbyCoffee()
        }

        binding.btnPlayMusic.setOnClickListener {
            playMusic()
        }

        binding.btnDismissModerate.setOnClickListener {
            tts.stop()
            buzzer.stopAlarm()
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun triggerModerateActions() {
        // 1. TTS Alert
        tts.speakDrowsy()

        // 2. Play music (moderate - once, not alarming)
        buzzer.playMusicOnce(prefs.getMusicUriOrNull())

        // 3. Get location for maps (pre-fetch)
        locationManager.getLastLocation { /* cached */ }
    }

    private fun playMusic() {
        buzzer.playMusicOnce(prefs.getMusicUriOrNull())
        binding.btnPlayMusic.text = "🎵  Playing…"
        binding.btnPlayMusic.isEnabled = false
    }

    private fun animatePulse(view: android.view.View) {
        ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.15f, 1f).apply {
            duration = 800
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.15f, 1f).apply {
            duration = 800
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    override fun onBackPressed() {
        // Don't allow back dismiss — driver must explicitly confirm
        binding.btnDismissModerate.performClick()
    }

    override fun onDestroy() {
        super.onDestroy()
        broadcaster.unregisterReceiver(criticalReceiver)
        tts.release()
        buzzer.release()
    }
}
