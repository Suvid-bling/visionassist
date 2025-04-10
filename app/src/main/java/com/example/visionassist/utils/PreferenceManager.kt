package com.example.visionassist.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Type-safe wrapper for SharedPreferences
 */
class PreferenceManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    // Cache for frequently accessed settings
    private var speechRateCache: Float? = null
    private var speechPitchCache: Float? = null
    private var detectionThresholdCache: Float? = null

    companion object {
        // Preference keys
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_SPEECH_PITCH = "speech_pitch"
        const val KEY_SPEECH_VOLUME = "speech_volume"
        const val KEY_DETECTION_THRESHOLD = "detection_threshold"
        const val KEY_DETECTION_FREQUENCY = "detection_frequency"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val KEY_ENABLE_HAPTIC = "enable_haptic"
        const val KEY_FIRST_RUN = "first_run"

        // Default values
        const val DEFAULT_SPEECH_RATE = 1.0f
        const val DEFAULT_SPEECH_PITCH = 1.0f
        const val DEFAULT_SPEECH_VOLUME = 1.0f
        const val DEFAULT_DETECTION_THRESHOLD = 0.5f
        const val DEFAULT_DETECTION_FREQUENCY = 500 // ms
        const val DEFAULT_DARK_MODE = true
        const val DEFAULT_HIGH_CONTRAST = true
        const val DEFAULT_ENABLE_HAPTIC = true
        const val DEFAULT_FIRST_RUN = true
    }

    /**
     * Save a string preference
     */
    suspend fun setString(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }

    /**
     * Get a string preference with default value
     */
    fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    /**
     * Save a boolean preference
     */
    suspend fun setBoolean(key: String, value: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(key, value).apply()
    }

    /**
     * Get a boolean preference with default value
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    /**
     * Save an integer preference
     */
    suspend fun setInt(key: String, value: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(key, value).apply()
    }

    /**
     * Get an integer preference with default value
     */
    fun getInt(key: String, defaultValue: Int = 0): Int {
        return prefs.getInt(key, defaultValue)
    }

    /**
     * Save a float preference
     */
    suspend fun setFloat(key: String, value: Float) = withContext(Dispatchers.IO) {
        // Update cache for frequently accessed settings
        when (key) {
            KEY_SPEECH_RATE -> speechRateCache = value
            KEY_SPEECH_PITCH -> speechPitchCache = value
            KEY_DETECTION_THRESHOLD -> detectionThresholdCache = value
        }

        prefs.edit().putFloat(key, value).apply()
    }

    /**
     * Get a float preference with default value
     */
    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        // Check cache first for frequently accessed settings
        return when (key) {
            KEY_SPEECH_RATE -> speechRateCache ?: prefs.getFloat(key, defaultValue).also { speechRateCache = it }
            KEY_SPEECH_PITCH -> speechPitchCache ?: prefs.getFloat(key, defaultValue).also { speechPitchCache = it }
            KEY_DETECTION_THRESHOLD -> detectionThresholdCache ?: prefs.getFloat(key, defaultValue).also { detectionThresholdCache = it }
            else -> prefs.getFloat(key, defaultValue)
        }
    }

    /**
     * Save preferences in a batch operation
     */
    suspend fun savePreferences(preferences: Map<String, Any>) = withContext(Dispatchers.IO) {
        val editor = prefs.edit()

        for ((key, value) in preferences) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Long -> editor.putLong(key, value)
            }
        }

        editor.apply()
    }

    /**
     * Reset all preferences to default values
     */
    suspend fun resetToDefaults() = withContext(Dispatchers.IO) {
        val editor = prefs.edit()

        editor.putFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE)
        editor.putFloat(KEY_SPEECH_PITCH, DEFAULT_SPEECH_PITCH)
        editor.putFloat(KEY_SPEECH_VOLUME, DEFAULT_SPEECH_VOLUME)
        editor.putFloat(KEY_DETECTION_THRESHOLD, DEFAULT_DETECTION_THRESHOLD)
        editor.putInt(KEY_DETECTION_FREQUENCY, DEFAULT_DETECTION_FREQUENCY)
        editor.putBoolean(KEY_DARK_MODE, DEFAULT_DARK_MODE)
        editor.putBoolean(KEY_HIGH_CONTRAST, DEFAULT_HIGH_CONTRAST)
        editor.putBoolean(KEY_ENABLE_HAPTIC, DEFAULT_ENABLE_HAPTIC)

        editor.apply()

        // Clear caches
        speechRateCache = DEFAULT_SPEECH_RATE
        speechPitchCache = DEFAULT_SPEECH_PITCH
        detectionThresholdCache = DEFAULT_DETECTION_THRESHOLD
    }

    /**
     * Register a preference change listener
     */
    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Unregister a preference change listener
     */
    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}