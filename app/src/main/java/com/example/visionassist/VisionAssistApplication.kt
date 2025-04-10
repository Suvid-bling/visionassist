package com.example.visionassist

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.Executors

class VisionAssistApplication : Application() {

    companion object {
        private const val TAG = "VisionAssistApp"
        private var instance: VisionAssistApplication? = null

        fun getInstance(): VisionAssistApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    // TensorFlow interpreter for model inference
    private var tfliteInterpreter: Interpreter? = null

    // TextToSpeech engine
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false

    // Background executor for heavy operations
    val backgroundExecutor = Executors.newFixedThreadPool(2)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize TTS in background
        backgroundExecutor.execute {
            initTextToSpeech()
        }

        // Initialize TensorFlow in background
        backgroundExecutor.execute {
            try {
                initTensorFlow()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize TensorFlow: ${e.message}")
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Clean up resources
        tfliteInterpreter?.close()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        backgroundExecutor.shutdown()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported by TTS")
                } else {
                    ttsInitialized = true
                    Log.d(TAG, "TextToSpeech initialized successfully")
                }
            } else {
                Log.e(TAG, "Failed to initialize TextToSpeech")
            }
        }
    }

    private fun initTensorFlow() {
        try {
            val modelFile = "detect.tflite" // Default model name, should be in assets
            tfliteInterpreter = Interpreter(loadModelFile(modelFile))
            Log.d(TAG, "TensorFlow Lite initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TensorFlow Lite: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun getTextToSpeech(): TextToSpeech? {
        return if (ttsInitialized) textToSpeech else null
    }

    fun getTfliteInterpreter(): Interpreter? {
        return tfliteInterpreter
    }
}