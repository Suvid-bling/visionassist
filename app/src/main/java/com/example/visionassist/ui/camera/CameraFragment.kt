package com.example.visionassist.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.visionassist.R
import com.example.visionassist.VisionAssistApplication
import com.example.visionassist.detection.ObjectDetector
import com.example.visionassist.ui.settings.SettingsFragment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewModel: CameraViewModel
    private lateinit var objectDetector: ObjectDetector

    // UI elements
    private lateinit var viewFinder: PreviewView
    private lateinit var detectButton: Button
    private lateinit var settingsButton: Button
    private lateinit var statusText: TextView

    private var analysisUseCase: ImageAnalysis? = null
    private var isDetecting = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize view model
        viewModel = ViewModelProvider(this)[CameraViewModel::class.java]

        // Initialize UI elements
        viewFinder = view.findViewById(R.id.viewFinder)
        detectButton = view.findViewById(R.id.detectButton)
        settingsButton = view.findViewById(R.id.settingsButton)
        statusText = view.findViewById(R.id.statusText)

        // Set up button click listeners
        detectButton.setOnClickListener {
            handleDetectButtonClick()
        }

        settingsButton.setOnClickListener {
            handleSettingsButtonClick()
        }

        // Initialize the object detector
        try {
            objectDetector = ObjectDetector(requireContext())
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing object detector: ${e.message}")
            Toast.makeText(context, "Error initializing detector", Toast.LENGTH_SHORT).show()
        }

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Start camera when view is created
        startCamera()

        // Set up observers for detection results
        viewModel.detectionResults.observe(viewLifecycleOwner) { results ->
            // Process detection results
            results?.let {
                if (it.isNotEmpty()) {
                    val topResult = it[0]
                    // Update UI
                    statusText.text = "Detected: ${topResult.label} (${(topResult.confidence * 100).toInt()}%)"
                    // Speak result
                    viewModel.speakDetectionResult(topResult)
                } else {
                    statusText.text = getString(R.string.ready_to_detect)
                }
            }
        }
    }

    private fun handleDetectButtonClick() {
        if (isDetecting) {
            // Stop detection
            isDetecting = false
            detectButton.text = getString(R.string.detect)
            statusText.text = getString(R.string.ready_to_detect)
            analysisUseCase?.clearAnalyzer()
        } else {
            // Start detection
            isDetecting = true
            detectButton.text = "STOP"
            statusText.text = "Detecting..."

            // Make sure camera is set up
            if (analysisUseCase == null) {
                startCamera()
            } else {
                analysisUseCase?.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }
            }
        }

        // Provide haptic feedback to confirm button press
        detectButton.isPressed = true
        detectButton.postDelayed({ detectButton.isPressed = false }, 100)
    }

    private fun handleSettingsButtonClick() {
        // Provide haptic feedback to confirm button press
        settingsButton.isPressed = true
        settingsButton.postDelayed({ settingsButton.isPressed = false }, 100)

        // Navigate to settings fragment







        val settingsFragment = SettingsFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, settingsFragment)
            .addToBackStack(null)  // This allows back navigation
            .commit()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview use case
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Image analysis use case
            analysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Only set analyzer if detection is enabled
            if (isDetecting) {
                analysisUseCase?.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }
            }

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    analysisUseCase
                )

                statusText.text = getString(R.string.ready_to_detect)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                statusText.text = "Camera error: ${e.message}"
                Toast.makeText(context, "Camera initialization failed", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isDetecting) {
            viewModel.processImageDetection(imageProxy, objectDetector)
        } else {
            imageProxy.close()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraFragment"
    }
}