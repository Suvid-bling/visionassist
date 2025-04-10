package com.example.visionassist.audio

import android.content.Context
import android.util.Log
import com.example.visionassist.detection.DetectionResult
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Executors

/**
 * Manages audio feedback from various sources and coordinates speech and sound effects
 */
class AudioFeedbackManager(private val context: Context) {

    private val TAG = "AudioFeedbackManager"

    // Text-to-speech manager for spoken feedback
    private val textToSpeechManager = TextToSpeechManager(context)

    // Executor for background audio processing
    private val audioExecutor = Executors.newSingleThreadExecutor()

    // Queue for sequential audio events
    private val audioEventQueue: Queue<AudioEvent> = LinkedList()

    // Track if we're currently speaking
    private var isProcessingAudio = false

    // Last spoken detection to avoid repetition
    private var lastSpokenDetection: String? = null
    private var lastSpeakTime = 0L
    private val minRepeatInterval = 3000L // 3 seconds between repeating the same message

    /**
     * Speak detection results based on priority
     */
    fun speakDetectionResults(results: List<DetectionResult>) {
        if (results.isEmpty()) {
            return
        }

        // Get the most important detection
        val topResult = results[0]
        val detectionMessage = buildDetectionMessage(topResult)

        // Check for repetition
        if (detectionMessage == lastSpokenDetection) {
            val now = System.currentTimeMillis()
            if (now - lastSpeakTime < minRepeatInterval) {
                // Too soon to repeat the same message
                return
            }
        }

        // Create audio event for this detection
        val event = AudioEvent(
            text = detectionMessage,
            priority = getPriorityForDetection(topResult),
            type = AudioEventType.DETECTION
        )

        // Add to queue and process
        enqueueAudioEvent(event)
    }

    /**
     * Speak error or status messages
     */
    fun speakStatus(message: String, priority: AudioPriority = AudioPriority.NORMAL) {
        val event = AudioEvent(
            text = message,
            priority = priority,
            type = AudioEventType.STATUS
        )
        enqueueAudioEvent(event)
    }

    /**
     * Queue an audio event and process if not already processing
     */
    private fun enqueueAudioEvent(event: AudioEvent) {
        // Handle critical priority - clear queue
        if (event.priority == AudioPriority.CRITICAL) {
            synchronized(audioEventQueue) {
                audioEventQueue.clear()
                audioEventQueue.add(event)
            }

            // Interrupt current speech
            textToSpeechManager.stop()
            isProcessingAudio = false
            processNextAudioEvent()
            return
        }

        // Handle high priority - add to front of queue
        if (event.priority == AudioPriority.HIGH) {
            synchronized(audioEventQueue) {
                (audioEventQueue as LinkedList).addFirst(event)
            }

            if (!isProcessingAudio) {
                processNextAudioEvent()
            }
            return
        }

        // Normal priority - add to end of queue
        synchronized(audioEventQueue) {
            audioEventQueue.add(event)
        }

        if (!isProcessingAudio) {
            processNextAudioEvent()
        }
    }

    /**
     * Process the next audio event in the queue
     */
    private fun processNextAudioEvent() {
        val event: AudioEvent?

        synchronized(audioEventQueue) {
            if (audioEventQueue.isEmpty()) {
                isProcessingAudio = false
                return
            }

            event = audioEventQueue.poll()
            isProcessingAudio = true
        }

        event?.let {
            // Update tracking for repeated detections
            if (it.type == AudioEventType.DETECTION) {
                lastSpokenDetection = it.text
                lastSpeakTime = System.currentTimeMillis()
            }

            // Speak the event
            audioExecutor.execute {
                textToSpeechManager.speak(it.text, getTextToSpeechPriority(it.priority))

                // Wait for speech to complete
                try {
                    Thread.sleep(calculateSpeechDuration(it.text))
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Speech wait interrupted: ${e.message}")
                }

                // Process next event
                processNextAudioEvent()
            }
        }
    }

    /**
     * Build appropriate message for detection
     */
    private fun buildDetectionMessage(result: DetectionResult): String {
        val confidence = (result.confidence * 100).toInt()

        return when {
            confidence > 90 -> "Detected ${result.label} ${result.locationDescription}, ${result.estimatedDistance}"
            confidence > 70 -> "${result.label} detected with high confidence"
            else -> "Possible ${result.label} detected"
        }
    }

    /**
     * Determine priority based on detection properties
     */
    private fun getPriorityForDetection(result: DetectionResult): AudioPriority {
        // Determine priority based on object type and distance
        return when {
            result.label.equals("person", ignoreCase = true) &&
                    (result.estimatedDistance == "very close" || result.estimatedDistance == "close") -> AudioPriority.HIGH

            result.label.equals("car", ignoreCase = true) &&
                    (result.estimatedDistance == "very close" || result.estimatedDistance == "close") -> AudioPriority.HIGH

            result.label.equals("obstacle", ignoreCase = true) -> AudioPriority.HIGH

            result.confidence > 0.8f -> AudioPriority.NORMAL

            else -> AudioPriority.LOW
        }
    }

    /**
     * Convert our priority to TTS priority
     */
    private fun getTextToSpeechPriority(priority: AudioPriority): TextToSpeechManager.SpeechPriority {
        return when (priority) {
            AudioPriority.CRITICAL -> TextToSpeechManager.SpeechPriority.CRITICAL
            AudioPriority.HIGH -> TextToSpeechManager.SpeechPriority.HIGH
            AudioPriority.NORMAL -> TextToSpeechManager.SpeechPriority.NORMAL
            AudioPriority.LOW -> TextToSpeechManager.SpeechPriority.LOW
        }
    }

    /**
     * Roughly estimate speech duration based on text length
     * This is a simple approximation - speaking rate is about 3 words per second
     */
    private fun calculateSpeechDuration(text: String): Long {
        val words = text.split(" ").size
        val baseTime = 300L // base time in milliseconds
        val timePerWord = 300L // time per word in milliseconds

        return baseTime + (words * timePerWord)
    }

    /**
     * Set speech rate (0.5f to 2.0f)
     */
    fun setSpeechRate(rate: Float) {
        textToSpeechManager.setSpeechRate(rate)
    }

    /**
     * Set speech pitch (0.5f to 2.0f)
     */
    fun setSpeechPitch(pitch: Float) {
        textToSpeechManager.setSpeechPitch(pitch)
    }

    /**
     * Set speech volume (0.0f to 1.0f)
     */
    fun setSpeechVolume(volume: Float) {
        textToSpeechManager.setVolume(volume)
    }

    /**
     * Release all resources
     */
    fun shutdown() {
        textToSpeechManager.shutdown()
        audioExecutor.shutdown()
    }

    /**
     * Audio event data class
     */
    data class AudioEvent(
        val text: String,
        val priority: AudioPriority,
        val type: AudioEventType
    )

    /**
     * Types of audio events
     */
    enum class AudioEventType {
        DETECTION, STATUS, ALERT, TUTORIAL
    }

    /**
     * Priority levels for audio feedback
     */
    enum class AudioPriority {
        LOW, NORMAL, HIGH, CRITICAL
    }
}