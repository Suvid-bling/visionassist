package com.example.visionassist.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.visionassist.utils.ImageUtils
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ObjectDetector(private val context: Context) {
    private val TAG = "ObjectDetector"
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    // Fallback to ML Kit if TF Lite fails
    private val mlKitDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
    )

    init {
        try {
            Log.d(TAG, "Initializing TensorFlow model")
            loadModelAndLabels()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TensorFlow: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadModelAndLabels() {
        try {
            // Load model
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }

            val modelFile = context.assets.openFd("detect.tflite")
            val fileChannel = FileInputStream(modelFile.fileDescriptor).channel
            val mappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                modelFile.startOffset,
                modelFile.declaredLength
            )

            interpreter = Interpreter(mappedByteBuffer, options)

            // Load labels
            context.assets.open("labelmap.txt").bufferedReader().useLines { lines ->
                labels = lines.filter { it.isNotBlank() }.map { it.trim() }.toList()
            }

            Log.d(TAG, "Model and labels loaded successfully. Labels: $labels")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model or labels: ${e.message}")
            throw e
        }
    }

    @OptIn(ExperimentalGetImage::class)
    fun detect(imageProxy: ImageProxy, onResult: (List<DetectionResult>) -> Unit) {
        try {
            if (interpreter == null) {
                // Fall back to ML Kit if TF Lite initialization failed
                useMLKitDetection(imageProxy, onResult)
                return
            }

            val bitmap = ImageUtils.imageProxyToBitmap(imageProxy)
            if (bitmap == null) {
                imageProxy.close()
                onResult(emptyList())
                return
            }

            // Prepare the input - this is the critical part that was causing the crash
            // Based on the error, we need a 300x300 RGB image (270,000 bytes)
            val inputSize = 300 // SSD MobileNet models typically use 300x300 input

            // Resize bitmap to the required input size
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

            // Allocate a ByteBuffer - use BYTE format (not FLOAT32)
            val inputBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3) // 3 bytes for RGB
            inputBuffer.order(ByteOrder.nativeOrder())

            // Fill the buffer with the bitmap data - no normalization
            val pixels = IntArray(inputSize * inputSize)
            resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

            for (pixel in pixels) {
                // Extract RGB values - note we're using bytes (0-255), not normalized floats
                inputBuffer.put((pixel shr 16 and 0xFF).toByte()) // R
                inputBuffer.put((pixel shr 8 and 0xFF).toByte())  // G
                inputBuffer.put((pixel and 0xFF).toByte())        // B
            }

            // Reset position to start reading from the beginning
            inputBuffer.rewind()

            // Prepare output tensors based on SSD MobileNet output format
            val outputLocations = Array(1) { Array(10) { FloatArray(4) } } // 10 detections, 4 coords
            val outputClasses = Array(1) { FloatArray(10) } // Class IDs for 10 detections
            val outputScores = Array(1) { FloatArray(10) }  // Confidence scores
            val numDetections = FloatArray(1)                // Number of detections

            // Create output map
            val outputMap = mapOf(
                0 to outputLocations,
                1 to outputClasses,
                2 to outputScores,
                3 to numDetections
            )

            // Run inference
            Log.d(TAG, "Running TensorFlow inference")
            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

            // Process outputs
            val results = mutableListOf<DetectionResult>()
            val numDetectionsOutput = numDetections[0].toInt()

            for (i in 0 until numDetectionsOutput) {
                val score = outputScores[0][i]
                if (score >= 0.5f) {
                    val labelIndex = outputClasses[0][i].toInt()
                    val label = if (labelIndex < labels.size) labels[labelIndex] else "Unknown"

                    // Get coordinates
                    val top = outputLocations[0][i][0] * bitmap.height
                    val left = outputLocations[0][i][1] * bitmap.width
                    val bottom = outputLocations[0][i][2] * bitmap.height
                    val right = outputLocations[0][i][3] * bitmap.width
                    val rectF = RectF(left, top, right, bottom)

                    // Create the detection result
                    val position = ImageUtils.getPositionDescription(
                        rectF.centerX(), rectF.centerY(), bitmap.width, bitmap.height
                    )
                    val distance = ImageUtils.estimateDistance(
                        rectF.width(), rectF.height(), bitmap.width, bitmap.height
                    )

                    results.add(
                        DetectionResult(
                            id = i,
                            label = label,
                            confidence = score,
                            location = rectF,
                            locationDescription = position,
                            estimatedDistance = distance
                        )
                    )
                }
            }

            onResult(results)
            imageProxy.close()

        } catch (e: Exception) {
            Log.e(TAG, "Error in TensorFlow detection: ${e.message}")
            e.printStackTrace()

            // Fall back to ML Kit if TF Lite fails
            useMLKitDetection(imageProxy, onResult)
        }
    }

    private fun useMLKitDetection(imageProxy: ImageProxy, onResult: (List<DetectionResult>) -> Unit) {
        Log.d(TAG, "Falling back to ML Kit detection")
        val inputImage = ImageUtils.imageProxyToInputImage(imageProxy)

        if (inputImage == null) {
            imageProxy.close()
            onResult(emptyList())
            return
        }

        mlKitDetector.process(inputImage)
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

                    val centerX = rectF.centerX()
                    val centerY = rectF.centerY()
                    val position = ImageUtils.getPositionDescription(centerX, centerY, frameWidth, frameHeight)
                    val distance = ImageUtils.estimateDistance(rectF.width(), rectF.height(), frameWidth, frameHeight)

                    DetectionResult(
                        id = index,
                        label = label,
                        confidence = confidence,
                        location = rectF,
                        locationDescription = position,
                        estimatedDistance = distance
                    )
                }

                onResult(results)
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit detection failed: ${e.message}")
                onResult(emptyList())
                imageProxy.close()
            }
    }

    fun shutdown() {
        interpreter?.close()
        executor.shutdown()
    }
}
