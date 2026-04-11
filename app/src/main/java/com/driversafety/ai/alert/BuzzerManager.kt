package com.driversafety.ai.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.util.Log
import com.driversafety.ai.R

/**
 * Controls the continuous loud alarm for critical drowsiness.
 * Loops audio at max volume and vibrates rhythmically.
 */
class BuzzerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var isAlarmActive = false

    companion object {
        private const val TAG = "BuzzerManager"
        // Vibration pattern: wait 0ms, vibrate 500ms, pause 300ms, vibrate 500ms, pause 300ms...
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 300, 500, 300)
    }

    /**
     * Start continuous looping alarm at maximum volume.
     */
    fun startAlarm(customMusicUri: Uri? = null) {
        if (isAlarmActive) return
        isAlarmActive = true

        // Force audio volume to maximum
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )

                if (customMusicUri != null) {
                    try {
                        setDataSource(context, customMusicUri)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot load custom music URI, using default alarm")
                        setDataSource(context, getDefaultAlarmUri())
                    }
                } else {
                    setDataSource(context, getDefaultAlarmUri())
                }

                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer error: ${e.message}")
            startFallbackTone()
        }

        // Start vibration
        startVibration()
    }

    /**
     * Start a high-pitched system tone as a fail-safe.
     * No permissions required for ToneGenerator.
     */
    private fun startFallbackTone() {
        if (toneGenerator == null) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 5000) // 5s burst
                Log.d(TAG, "Fallback ToneGenerator started")
            } catch (e: Exception) {
                Log.e(TAG, "ToneGenerator failed: ${e.message}")
            }
        }
    }

    /**
     * Play music once (for moderate alert - not looping).
     */
    fun playMusicOnce(customMusicUri: Uri? = null) {
        stopAlarm()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val uri = customMusicUri ?: getDefaultAlarmUri()
                setDataSource(context, uri)
                isLooping = false
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Music playback error: ${e.message}")
        }
    }

    fun stopAlarm() {
        isAlarmActive = false
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop error: ${e.message}")
        }
        mediaPlayer = null

        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (e: Exception) {}
        toneGenerator = null

        stopVibration()
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun isAlarmOn(): Boolean = isAlarmActive

    private fun startVibration() {
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, 0) // repeat from index 0
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(VIBRATION_PATTERN, 0)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun getDefaultAlarmUri(): Uri =
        android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI

    fun release() {
        stopAlarm()
    }
}
