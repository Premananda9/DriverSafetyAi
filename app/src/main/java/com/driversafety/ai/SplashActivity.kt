package com.driversafety.ai

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.driversafety.ai.databinding.ActivitySplashBinding
import com.driversafety.ai.utils.AppPreferenceManager
import com.driversafety.ai.utils.PermissionManager

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var prefs: AppPreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferenceManager(this)

        animateLogo()
    }

    private fun animateLogo() {
        // Fade + scale in logo container
        binding.logoContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // Show loading indicator
                binding.progressBar.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction {
                        // Request permissions then navigate
                        requestPermissionsAndNavigate()
                    }
                    .start()
            }
            .start()

        // Pulse animation on logo
        ObjectAnimator.ofFloat(binding.ivLogo, "scaleX", 1f, 1.05f, 1f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(binding.ivLogo, "scaleY", 1f, 1.05f, 1f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun requestPermissionsAndNavigate() {
        // Request all critical permissions at startup
        PermissionManager.requestAllCritical(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Navigate to Main regardless (permissions can be re-requested later)
        navigateToMain()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
