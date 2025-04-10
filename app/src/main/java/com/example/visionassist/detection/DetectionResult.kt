package com.example.visionassist.detection

import android.graphics.RectF

data class DetectionResult(
    val id: Int,
    val label: String,
    val confidence: Float,
    val location: RectF,
    val locationDescription: String = "", // Changed from positionDescription
    val estimatedDistance: String = ""    // Changed from distanceDescription
) {
    // Helper methods to compute additional properties
    val centerX: Float get() = location.centerX()
    val centerY: Float get() = location.centerY()
    val width: Float get() = location.width()
    val height: Float get() = location.height()

    // Get a human-readable description
    fun getDescription(): String {
        val confidencePercent = (confidence * 100).toInt()
        return "$label ($confidencePercent% confidence) $locationDescription, $estimatedDistance"
    }
}