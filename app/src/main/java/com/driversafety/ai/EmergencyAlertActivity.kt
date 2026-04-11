package com.driversafety.ai

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.driversafety.ai.ai.CognitiveGameManager
import com.driversafety.ai.alert.BuzzerManager
import com.driversafety.ai.alert.TTSManager
import com.driversafety.ai.databinding.ActivityEmergencyAlertBinding
import com.driversafety.ai.emergency.EmergencyManager
import com.driversafety.ai.location.LocationManager
import com.driversafety.ai.utils.AppPreferenceManager

class EmergencyAlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmergencyAlertBinding
    private lateinit var prefs: AppPreferenceManager
    private lateinit var tts: TTSManager
    private lateinit var buzzer: BuzzerManager
    private lateinit var locationManager: LocationManager
    private lateinit var emergencyManager: EmergencyManager
    private lateinit var gameManager: CognitiveGameManager
    private var currentChallenge: CognitiveGameManager.GameItem? = null

    private var alarmStopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferenceManager(this)
        tts = TTSManager(this)
        buzzer = BuzzerManager(this)
        locationManager = LocationManager(this)
        emergencyManager = EmergencyManager(this)
        gameManager = CognitiveGameManager()

        setupUI()
        preFetchLocation()
        triggerCriticalActions()
    }

    private fun preFetchLocation() {
        // Immediately fetch location to ensure Maps query has GPS data
        locationManager.getLastLocation { loc ->
            if (loc != null) {
                // Location cached in manager
            }
        }
    }

    private fun setupUI() {
        // Pulsing critical icon
        animatePulseCritical(binding.ivCriticalIcon)

        // Blink background red
        startBackgroundBlink()

        // Find Rest Area
        binding.btnOpenRestArea.setOnClickListener {
            locationManager.openNearbyRestArea()
        }

        // Call emergency contact
        binding.btnCallContact.setOnClickListener {
            val phone = prefs.contactPhone
            if (phone.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_no_contact), Toast.LENGTH_SHORT).show()
            } else {
                emergencyManager.callEmergencyContact(phone)
            }
        }

        // Game submit button
        binding.btnGameSubmit.setOnClickListener {
            handleGameSubmit()
        }

        // Stop alarm (large button)
        binding.btnStopAlarm.setOnClickListener {
            stopAlarm()
        }

        // Close (only enabled after alarm stopped)
        binding.btnCloseCritical.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun triggerCriticalActions() {
        // STEP 1: Speak the initial warning immediately
        // No callback needed for the primary warning to ensure it always starts
        tts.speak("Alert! You are feeling very sleepy. This is extremely dangerous. Please pull over and rest immediately.")

        // STEP 2: Start the loud alarm or fallback tone after 1 second
        android.os.Handler(mainLooper).postDelayed({
            if (!alarmStopped) {
                buzzer.startAlarm(prefs.getMusicUriOrNull())
            }
        }, 1200)

        // STEP 3: Auto-suggest Rest Area after 4 seconds
        // By this point, pre-fetched location should be available
        android.os.Handler(mainLooper).postDelayed({
            if (!alarmStopped) {
                tts.speak("I am opening Google Maps to find the nearest rest area for you. Please pull over.")
                locationManager.openNearbyRestArea()
                animatePulseButton(binding.btnOpenRestArea)
            }
        }, 4500)

        // STEP 4: Show the cognitive game after 9 seconds
        android.os.Handler(mainLooper).postDelayed({
            if (!alarmStopped) {
                showGamePanel()
            }
        }, 9000)

        // Meanwhile in background: Send SMS with last known location
        locationManager.getLastLocation { location ->
            val phone = prefs.contactPhone
            if (phone.isNotEmpty()) {
                val smsSent = emergencyManager.sendEmergencySms(phone, location)
                runOnUiThread {
                    if (smsSent) {
                        binding.tvSmsSent.text = "✅ SMS Sent"
                        binding.tvSmsSent.setTextColor(Color.parseColor("#00D4AA"))
                    } else {
                        binding.tvSmsSent.text = "❌ SMS Failed"
                        binding.tvSmsSent.setTextColor(Color.parseColor("#FF1744"))
                    }
                }

                // Auto-call (delayed) if enabled
                if (prefs.autoCallEnabled) {
                    android.os.Handler(mainLooper).postDelayed({
                        if (!alarmStopped) {
                            emergencyManager.callEmergencyContact(phone)
                        }
                    }, 14000)
                }
            } else {
                runOnUiThread {
                    binding.tvSmsSent.text = "⚠ No Contact Set"
                }
            }
        }
    }

    private fun showGamePanel() {
        if (alarmStopped) return

        binding.cardCognitiveGame.visibility = View.VISIBLE
        binding.cardCognitiveGame.alpha = 0f
        binding.cardCognitiveGame.translationY = 100f // Start lower for pop-up effect
        binding.cardCognitiveGame.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .start()

        loadNewChallenge()

        // Focus input and show keyboard immediately
        binding.etGameAnswer.requestFocus()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.etGameAnswer, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun loadNewChallenge() {
        val challenge = gameManager.getRandomChallenge()
        currentChallenge = challenge
        binding.tvGameQuestion.text = challenge.question
        binding.etGameAnswer.text.clear()

        tts.speak(challenge.answerPrompt)
    }

    private fun handleGameSubmit() {
        val answer = binding.etGameAnswer.text.toString()
        if (answer.isBlank()) return

        val challenge = currentChallenge ?: return
        if (challenge.validator(answer)) {
            tts.speak("Correct! Alarm stopped.")
            binding.etGameAnswer.clearFocus()
            
            // Hide keyboard cleanly
            val inputMethodManager = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(binding.etGameAnswer.windowToken, 0)
            
            stopAlarm()
        } else {
            tts.speak("Incorrect. Stay awake!")
            binding.etGameAnswer.error = "Try again"
        }
    }

    private fun stopAlarm() {
        if (!alarmStopped) {
            alarmStopped = true
            buzzer.stopAlarm()
            tts.stop()
            binding.btnStopAlarm.text = "✅ Alarm Stopped"
            binding.btnStopAlarm.isEnabled = false
            binding.btnCloseCritical.isEnabled = true
            binding.btnCloseCritical.alpha = 1f
        }
    }

    private fun animatePulseButton(view: View) {
        ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f).apply {
            duration = 1000
            repeatCount = 5
            start()
        }
        ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f).apply {
            duration = 1000
            repeatCount = 5
            start()
        }
    }

    private fun animatePulseCritical(view: View) {
        ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.3f, 1f).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.3f, 1f).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun startBackgroundBlink() {
        ObjectAnimator.ofArgb(
            binding.root,
            "backgroundColor",
            Color.parseColor("#1A0000"),
            Color.parseColor("#2D0000"),
            Color.parseColor("#1A0000")
        ).apply {
            duration = 1000
            repeatCount = 10
            interpolator = LinearInterpolator()
            start()
        }
    }

    override fun onBackPressed() {
        // Block back until alarm is stopped
        if (alarmStopped) {
            binding.btnCloseCritical.performClick()
        }
        // else: ignore back press during active emergency
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.release()
        buzzer.release()
    }
}
