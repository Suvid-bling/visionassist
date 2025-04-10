package com.example.visionassist.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream

/**
 * Utility class for image processing operations required for object detection
 */
object ImageUtils {
    private const val TAG = "ImageUtils"

    /**
     * Creates an InputImage from an ImageProxy for ML Kit
     */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun imageProxyToInputImage(imageProxy: ImageProxy): InputImage? {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            Log.e(TAG, "Failed to get media image from ImageProxy")
            return null
        }

        return InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
    }

    /**
     * Converts an ImageProxy to a Bitmap
     * Useful for custom processing or visualization
     */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null

        try {
            // Get the YUV data
            val planes = image.planes
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            // U and V are swapped
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                image.width,
                image.height,
                null
            )

            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, image.width, image.height),
                100,
                out
            )

            val jpegBytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

            // Rotate the bitmap if needed based on rotation degrees
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                return Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
            }

            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap: ${e.message}")
            return null
        }
    }

    /**
     * Calculates the normalized position description of an object in the frame
     * (e.g., "upper left", "center right")
     */
    fun getPositionDescription(centerX: Float, centerY: Float, frameWidth: Int, frameHeight: Int): String {
        val xNormalized = centerX / frameWidth
        val yNormalized = centerY / frameHeight

        val horizontalPosition = when {
            xNormalized < 0.33f -> "left"
            xNormalized > 0.66f -> "right"
            else -> "center"
        }

        val verticalPosition = when {
            yNormalized < 0.33f -> "upper"
            yNormalized > 0.66f -> "lower"
            else -> ""
        }

        return if (verticalPosition.isEmpty()) horizontalPosition else "$verticalPosition $horizontalPosition"
    }

    /**
     * Estimates the relative distance of an object based on its size in the frame
     */
    fun estimateDistance(objectWidth: Float, objectHeight: Float,
                         frameWidth: Int, frameHeight: Int): String {
        // Calculate the object's area as a percentage of the frame
        val objectArea = objectWidth * objectHeight
        val frameArea = frameWidth * frameHeight
        val areaPercentage = objectArea / frameArea

        return when {
            areaPercentage > 0.4f -> "very close"
            areaPercentage > 0.2f -> "close"
            areaPercentage > 0.05f -> "medium distance"
            else -> "far away"
        }
    }
}