package com.example.visionassist.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.UUID

class TextToSpeechManager(private val context: Context) {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    // Queue for sequential speech
    private val speechQueue: Queue<SpeechItem> = LinkedList()
    private var isSpeaking = false

    // Speech parameters
    private var speechRate = 1.0f
    private var speechPitch = 1.0f
    private var volume = 1.0f

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported")
                } else {
                    isInitialized = true
                    setupTtsListener()
                    Log.d(TAG, "TextToSpeech initialized successfully")
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed")
            }
        }
    }

    private fun setupTtsListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Speech started
            }

            override fun onDone(utteranceId: String?) {
                // Process next item in queue when current speech is done
                processSpeechQueue()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                // Handle errors and move to next item
                Log.e(TAG, "Speech error for utterance: $utteranceId")
                processSpeechQueue()
            }
        })
    }

    fun speak(text: String, priority: SpeechPriority = SpeechPriority.NORMAL) {
        if (!isInitialized) {
            Log.w(TAG, "TextToSpeech not initialized yet")
            return
        }

        val speechItem = SpeechItem(text, priority)

        // Handle by priority
        when (priority) {
            SpeechPriority.CRITICAL -> {
                // Interrupt current speech for critical information
                speechQueue.clear()
                textToSpeech?.stop()
                enqueueSpeech(speechItem)
            }
            SpeechPriority.HIGH -> {
                // Add to front of queue
                (speechQueue as LinkedList).addFirst(speechItem)
                if (!isSpeaking) {
                    processSpeechQueue()
                }
            }
            else -> {
                // Normal priority - add to end of queue
                enqueueSpeech(speechItem)
            }
        }
    }

    private fun enqueueSpeech(speechItem: SpeechItem) {
        speechQueue.add(speechItem)

        if (!isSpeaking) {
            processSpeechQueue()
        }
    }

    private fun processSpeechQueue() {
        if (speechQueue.isEmpty()) {
            isSpeaking = false
            return
        }

        isSpeaking = true
        val speechItem = speechQueue.poll() ?: return

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        textToSpeech?.setSpeechRate(speechRate)
        textToSpeech?.setPitch(speechPitch)
        textToSpeech?.speak(speechItem.text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        textToSpeech?.stop()
        speechQueue.clear()
        isSpeaking = false
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.1f, 2.0f)
    }

    fun setSpeechPitch(pitch: Float) {
        speechPitch = pitch.coerceIn(0.1f, 2.0f)
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1.0f)
    }

    fun shutdown() {
        speechQueue.clear()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private data class SpeechItem(val text: String, val priority: SpeechPriority)

    enum class SpeechPriority {
        LOW, NORMAL, HIGH, CRITICAL
    }

    companion object {
        private const val TAG = "TextToSpeechManager"
    }
}