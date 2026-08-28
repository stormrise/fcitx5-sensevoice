package com.fcitx5sensevoice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    private val settingsStore by lazy { AppSettings(this) }
    private lateinit var languageSpinner: Spinner
    private lateinit var itnSwitch: Switch
    private lateinit var vadSensitivitySpinner: Spinner
    private lateinit var endpointSilenceSpinner: Spinner
    private lateinit var permissionStatus: TextView
    private lateinit var permissionAction: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        bindViews()
        populateSpinners()
        showSettings(settingsStore.load())
        findViewById<Button>(R.id.reset_defaults).setOnClickListener { showSettings(AsrSettings.DEFAULT) }
        permissionAction.setOnClickListener { requestMicrophonePermission() }

        if (
            savedInstanceState == null &&
            intent.getBooleanExtra(EXTRA_REQUEST_MICROPHONE_PERMISSION, false) &&
            !hasPermission()
        ) {
            requestMicrophonePermission()
        }
    }

    override fun onResume() {
        super.onResume()
        renderPermissionState()
    }

    override fun onPause() {
        settingsStore.save(readSettings())
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MICROPHONE) return
        renderPermissionState()
    }

    private fun bindViews() {
        languageSpinner = findViewById(R.id.recognition_language)
        itnSwitch = findViewById(R.id.use_itn)
        vadSensitivitySpinner = findViewById(R.id.vad_sensitivity)
        endpointSilenceSpinner = findViewById(R.id.endpoint_silence)
        permissionStatus = findViewById(R.id.permission_status)
        permissionAction = findViewById(R.id.grant_permission)
    }

    private fun populateSpinners() {
        languageSpinner.setEntries(R.array.recognition_language_labels)
        vadSensitivitySpinner.setEntries(R.array.vad_sensitivity_labels)
        endpointSilenceSpinner.setEntries(R.array.endpoint_silence_labels)
    }

    private fun Spinner.setEntries(arrayResource: Int) {
        adapter = ArrayAdapter.createFromResource(
            this@SettingsActivity,
            arrayResource,
            android.R.layout.simple_spinner_item,
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun showSettings(settings: AsrSettings) {
        languageSpinner.setSelection(RecognitionLanguage.entries.indexOf(settings.language))
        itnSwitch.isChecked = settings.useInverseTextNormalization
        vadSensitivitySpinner.setSelection(VAD_THRESHOLDS.nearestIndex(settings.vadThreshold))
        endpointSilenceSpinner.setSelection(ENDPOINT_SILENCE_SECONDS.nearestIndex(settings.vadMinSilenceSeconds))
    }

    private fun readSettings(): AsrSettings = AsrSettings(
        language = RecognitionLanguage.entries[languageSpinner.selectedItemPosition.coerceIn(RecognitionLanguage.entries.indices)],
        useInverseTextNormalization = itnSwitch.isChecked,
        vadThreshold = VAD_THRESHOLDS[vadSensitivitySpinner.selectedItemPosition.coerceIn(VAD_THRESHOLDS.indices)],
        vadMinSilenceSeconds = ENDPOINT_SILENCE_SECONDS[
            endpointSilenceSpinner.selectedItemPosition.coerceIn(ENDPOINT_SILENCE_SECONDS.indices)
        ],
    )

    private fun FloatArray.nearestIndex(value: Float): Int {
        var result = 0
        for (index in 1 until size) {
            if (kotlin.math.abs(this[index] - value) < kotlin.math.abs(this[result] - value)) result = index
        }
        return result
    }

    private fun renderPermissionState() {
        val granted = hasPermission()
        permissionStatus.setText(if (granted) R.string.permission_granted else R.string.permission_explanation)
        permissionAction.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun requestMicrophonePermission() {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
    }

    private fun hasPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val EXTRA_REQUEST_MICROPHONE_PERMISSION = "request_microphone_permission"
        private const val REQUEST_MICROPHONE = 1
        private val VAD_THRESHOLDS = floatArrayOf(0.15f, AsrSettings.DEFAULT_VAD_THRESHOLD, 0.4f)
        private val ENDPOINT_SILENCE_SECONDS =
            floatArrayOf(0.5f, AsrSettings.DEFAULT_VAD_MIN_SILENCE_SECONDS, 1f, 1.5f)
    }
}
