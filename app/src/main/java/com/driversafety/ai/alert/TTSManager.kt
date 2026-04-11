package com.driversafety.ai.alert

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * Wrapper around Android TextToSpeech engine.
 * Provides a clean API with queue management and priority overrides.
 */
class TTSManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingQueue = mutableListOf<String>()

    companion object {
        private const val TAG = "TTSManager"
        const val TTS_DROWSY  = "Warning! You are feeling drowsy. Please stay alert and keep your eyes open."
        const val TTS_SLEEPY  = "Alert! You are feeling very sleepy. This is dangerous. Please pull over immediately and rest."
    }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e(TAG, "TTS language not supported, trying default")
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.0f)
                isReady = true

                // Drain pending queue
                pendingQueue.forEach { speak(it) }
                pendingQueue.clear()
            } else {
                Log.e(TAG, "TTS init failed with status: $status")
            }
        }
    }

    /**
     * Speak text. Interrupts current speech.
     */
    fun speak(text: String, flush: Boolean = true) {
        if (!isReady) {
            pendingQueue.add(text)
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queueMode, null, utteranceId)
    }

    /**
     * Speak and then execute callback when done.
     */
    fun speakWithCallback(text: String, onDone: () -> Unit) {
        if (!isReady) {
            pendingQueue.add(text)
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) onDone()
            }
            override fun onError(id: String?) {}
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun speakDrowsy()  = speak(TTS_DROWSY)
    fun speakSleepy()  = speak(TTS_SLEEPY)

    fun stop() {
        tts?.stop()
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        pendingQueue.clear()
    }
}
