package com.driversafety.ai.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.driversafety.ai.alert.TTSManager
import kotlinx.coroutines.*

/**
 * AI voice interaction module.
 * Asks driver questions and listens for YES/NO voice responses.
 * Uses Android on-device SpeechRecognizer.
 */
class VoiceInteractionManager(
    private val context: Context,
    private val ttsManager: TTSManager
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: VoiceResponseListener? = null
    private var currentQuestionIndex = 0
    private var isListening = false

    companion object {
        private const val TAG = "VoiceInteraction"

        val QUESTIONS = listOf(
            "Are you okay? Say YES if you are fine or NO if you need help.",
            "Should I guide you to a rest area? Say YES to get directions.",
            "Please confirm you are stopping the vehicle. Say YES to confirm."
        )
    }

    interface VoiceResponseListener {
        fun onQuestionAsked(question: String)
        fun onResponseReceived(response: String, isPositive: Boolean)
        fun onListeningStarted()
        fun onListeningStopped()
        fun onError(error: String)
    }

    fun setListener(listener: VoiceResponseListener) {
        this.listener = listener
    }

    /**
     * Start the voice interaction sequence.
     * Asks questions in order and listens for YES/NO.
     */
    fun startInteraction() {
        currentQuestionIndex = 0
        askCurrentQuestion()
    }

    fun askCurrentQuestion() {
        val question = QUESTIONS.getOrNull(currentQuestionIndex) ?: return
        listener?.onQuestionAsked(question)
        ttsManager.speakWithCallback(question) {
            // After TTS finishes speaking, start listening
            CoroutineScope(Dispatchers.Main).launch {
                delay(500)
                startListening()
            }
        }
    }

    fun askCustomQuestion(question: String) {
        listener?.onQuestionAsked(question)
        ttsManager.speakWithCallback(question) {
            CoroutineScope(Dispatchers.Main).launch {
                delay(500)
                startListening()
            }
        }
    }

    /**
     * Manually trigger speech recognition (called from mic button).
     */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener?.onError("Speech recognition not available on this device")
            return
        }

        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    listener?.onListeningStarted()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    listener?.onListeningStopped()

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val topResult = matches?.firstOrNull()?.lowercase()?.trim() ?: ""

                    Log.d(TAG, "Voice result: $topResult")

                    val isPositive = isPositiveResponse(topResult)
                    val displayResult = matches?.firstOrNull() ?: "Not understood"
                    listener?.onResponseReceived(displayResult, isPositive)

                    handleResponse(isPositive)
                }

                override fun onError(error: Int) {
                    isListening = false
                    listener?.onListeningStopped()
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Could not understand response"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        else -> "Recognition error ($error)"
                    }
                    listener?.onError(msg)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say YES or NO")
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening error: ${e.message}")
            listener?.onError("Could not start listening: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "stopListening error: ${e.message}")
        }
        speechRecognizer = null
        isListening = false
    }

    private fun handleResponse(isPositive: Boolean) {
        if (isPositive) {
            // Driver confirmed → move to next question
            currentQuestionIndex++
            if (currentQuestionIndex < QUESTIONS.size) {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(1000)
                    askCurrentQuestion()
                }
            }
        } else {
            // Negative response → escalate or repeat
            ttsManager.speak("Please pull over safely and take a rest.")
        }
    }

    private fun isPositiveResponse(text: String): Boolean {
        val positiveKeywords = listOf("yes", "yeah", "yep", "sure", "ok", "okay",
            "fine", "correct", "right", "affirmative", "good")
        return positiveKeywords.any { text.contains(it) }
    }

    fun isCurrentlyListening(): Boolean = isListening

    fun release() {
        stopListening()
        ttsManager.stop()
    }
}
