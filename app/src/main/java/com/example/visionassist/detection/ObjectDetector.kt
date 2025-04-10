package com.example.visionassist.detection

import android.content.Context
import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.visionassist.utils.ImageUtils
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ObjectDetector(private val context: Context) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification()
        .build()

    private val detector = ObjectDetection.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    fun detect(imageProxy: ImageProxy, onResult: (List<DetectionResult>) -> Unit) {
        val inputImage = ImageUtils.imageProxyToInputImage(imageProxy)

        if (inputImage == null) {
            imageProxy.close()
            onResult(emptyList())
            return
        }

        detector.process(inputImage)
            .addOnSuccessListener { objects ->
                val frameWidth = imageProxy.width
                val frameHeight = imageProxy.height

                val results = objects.mapIndexed { index, detectedObject ->
                    val label = detectedObject.labels.firstOrNull()?.text ?: "Unknown"
                    val confidence = detectedObject.labels.firstOrNull()?.confidence ?: 0f

                    val boundingBox = detectedObject.boundingBox
                    val rectF = RectF(
                        boundingBox.left.toFloat(),
                        boundingBox.top.toFloat(),
                        boundingBox.right.toFloat(),
                        boundingBox.bottom.toFloat()
                    )

                    // Use ImageUtils for enhanced position and distance description
                    val centerX = rectF.centerX()
                    val centerY = rectF.centerY()
                    val position = ImageUtils.getPositionDescription(centerX, centerY, frameWidth, frameHeight)
                    val distance = ImageUtils.estimateDistance(rectF.width(), rectF.height(), frameWidth, frameHeight)

                    DetectionResult(
                        id = index,
                        label = label,
                        confidence = confidence,
                        location = rectF,
                        locationDescription = position,    // Changed property name
                        estimatedDistance = distance       // Changed property name
                    )
                }

                onResult(results)
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Detection failed: ${e.message}")
                onResult(emptyList())
                imageProxy.close()
            }
    }

    fun shutdown() {
        executor.shutdown()
    }

    companion object {
        private const val TAG = "ObjectDetector"
    }
}