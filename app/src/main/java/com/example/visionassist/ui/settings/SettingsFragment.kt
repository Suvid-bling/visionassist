package com.example.visionassist.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import com.example.visionassist.R
import com.example.visionassist.audio.TextToSpeechManager
import com.example.visionassist.utils.PreferenceManager.Companion.KEY_DETECTION_THRESHOLD
import com.example.visionassist.utils.PreferenceManager.Companion.KEY_SPEECH_PITCH
import com.example.visionassist.utils.PreferenceManager.Companion.KEY_SPEECH_RATE
import com.example.visionassist.utils.PreferenceManager.Companion.KEY_SPEECH_VOLUME

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val TAG = "SettingsFragment"
    private var textToSpeech: TextToSpeechManager? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Initialize TTS for immediate feedback
        textToSpeech = TextToSpeechManager(requireContext())

        // Set up preference change listeners
        setupPreferenceListeners()
    }

    private fun setupPreferenceListeners() {
        // Speech rate
        findPreference<SeekBarPreference>(KEY_SPEECH_RATE)?.let { preference ->
            preference.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as Int
                val speechRate = value / 10f
                textToSpeech?.speak("Speech rate set to $speechRate",
                    TextToSpeechManager.SpeechPriority.HIGH)
                true
            }
        }

        // Speech pitch
        findPreference<SeekBarPreference>(KEY_SPEECH_PITCH)?.let { preference ->
            preference.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as Int
                val speechPitch = value / 10f
                textToSpeech?.speak("Speech pitch set to $speechPitch",
                    TextToSpeechManager.SpeechPriority.HIGH)
                true
            }
        }

        // Detection threshold
        findPreference<SeekBarPreference>(KEY_DETECTION_THRESHOLD)?.let { preference ->
            preference.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as Int
                val threshold = value / 10f
                textToSpeech?.speak("Detection threshold set to $threshold",
                    TextToSpeechManager.SpeechPriority.HIGH)
                true
            }
        }

        // About app
        findPreference<Preference>("about_app")?.let { preference ->
            preference.setOnPreferenceClickListener {
                showAboutDialog()
                true
            }
        }

        // Tutorial
        findPreference<Preference>("tutorial")?.let { preference ->
            preference.setOnPreferenceClickListener {
                startTutorial()
                true
            }
        }
    }

    private fun showAboutDialog() {
        // In a real implementation, show an about dialog
        textToSpeech?.speak("Vision Assist helps visually impaired users " +
                "understand their surroundings through object detection and audio feedback",
            TextToSpeechManager.SpeechPriority.NORMAL)
    }

    private fun startTutorial() {
        // In a real implementation, start the tutorial
        textToSpeech?.speak("Starting tutorial", TextToSpeechManager.SpeechPriority.NORMAL)
        // Navigate to tutorial fragment
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            KEY_SPEECH_RATE -> {
                val value = sharedPreferences?.getInt(key, 10) ?: 10
                textToSpeech?.setSpeechRate(value / 10f)
            }
            KEY_SPEECH_PITCH -> {
                val value = sharedPreferences?.getInt(key, 10) ?: 10
                textToSpeech?.setSpeechPitch(value / 10f)
            }
            KEY_SPEECH_VOLUME -> {
                val value = sharedPreferences?.getInt(key, 10) ?: 10
                textToSpeech?.setVolume(value / 10f)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.shutdown()
    }
}