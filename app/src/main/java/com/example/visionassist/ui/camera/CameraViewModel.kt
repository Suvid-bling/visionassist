package com.example.visionassist.ui.camera

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.visionassist.VisionAssistApplication
import com.example.visionassist.detection.DetectionResult
import com.example.visionassist.detection.ObjectDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val app: VisionAssistApplication = application as VisionAssistApplication
    private val textToSpeech: TextToSpeech? get() = app.getTextToSpeech()

    private val _detectionResults = MutableLiveData<List<DetectionResult>>()
    val detectionResults: LiveData<List<DetectionResult>> = _detectionResults

    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private var lastSpokenTimestamp = 0L
    private val throttleInterval = 2000L // 2 seconds between spoken results

    private var isProcessingFrame = false

    fun processImageDetection(image: ImageProxy, detector: ObjectDetector) {
        if (isProcessingFrame) {
            image.close()
            return
        }

        isProcessingFrame = true

        detector.detect(image) { results ->
            // Update UI with results
            _detectionResults.postValue(results)
            isProcessingFrame = false
        }
    }

    fun speakDetectionResult(result: DetectionResult) {
        val currentTime = System.currentTimeMillis()

        // Throttle speech to prevent continuous talking
        if (currentTime - lastSpokenTimestamp < throttleInterval) {
            return
        }

        // Formulate speech based on detection
        val message = buildSpeechMessage(result)

        // Speak the result
        textToSpeech?.let { tts ->
            val utteranceId = UUID.randomUUID().toString()
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            lastSpokenTimestamp = currentTime
        }
    }

    private fun buildSpeechMessage(result: DetectionResult): String {
        // Basic message format - can be enhanced with more details

        val confidencePercent = (result.confidence * 100).toInt()
        return "Detected ${result.label} with ${confidencePercent}% confidence"
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up resources if needed
    }

    companion object {
        private const val TAG = "CameraViewModel"
    }
}