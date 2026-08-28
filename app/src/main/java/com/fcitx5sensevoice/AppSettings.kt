package com.fcitx5sensevoice

import android.content.Context

internal enum class RecognitionLanguage(val code: String) {
    AUTO("auto"),
    MANDARIN("zh"),
    CANTONESE("yue"),
    ENGLISH("en"),
    JAPANESE("ja"),
    KOREAN("ko");

    companion object {
        fun fromCode(code: String?): RecognitionLanguage = entries.firstOrNull { it.code == code } ?: MANDARIN
    }
}

internal data class AsrSettings(
    val language: RecognitionLanguage = RecognitionLanguage.MANDARIN,
    val useInverseTextNormalization: Boolean = true,
    val vadThreshold: Float = DEFAULT_VAD_THRESHOLD,
    val vadMinSilenceSeconds: Float = DEFAULT_VAD_MIN_SILENCE_SECONDS,
) {
    companion object {
        const val DEFAULT_VAD_THRESHOLD = 0.25f
        const val DEFAULT_VAD_MIN_SILENCE_SECONDS = 0.7f
        val DEFAULT = AsrSettings()

        fun fromStoredValues(
            languageCode: String?,
            useInverseTextNormalization: Boolean,
            vadThreshold: Float,
            vadMinSilenceSeconds: Float,
        ): AsrSettings = AsrSettings(
            language = RecognitionLanguage.fromCode(languageCode),
            useInverseTextNormalization = useInverseTextNormalization,
            vadThreshold = vadThreshold.takeIf { it in 0.05f..0.95f } ?: DEFAULT_VAD_THRESHOLD,
            vadMinSilenceSeconds = vadMinSilenceSeconds.takeIf { it in 0.3f..3f }
                ?: DEFAULT_VAD_MIN_SILENCE_SECONDS,
        )
    }
}

internal class AppSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AsrSettings = AsrSettings.fromStoredValues(
        languageCode = preferences.getString(KEY_LANGUAGE, RecognitionLanguage.MANDARIN.code),
        useInverseTextNormalization = preferences.getBoolean(KEY_USE_ITN, true),
        vadThreshold = preferences.getFloat(KEY_VAD_THRESHOLD, AsrSettings.DEFAULT_VAD_THRESHOLD),
        vadMinSilenceSeconds = preferences.getFloat(
            KEY_VAD_MIN_SILENCE_SECONDS,
            AsrSettings.DEFAULT_VAD_MIN_SILENCE_SECONDS,
        ),
    )

    fun save(settings: AsrSettings) {
        preferences.edit()
            .putString(KEY_LANGUAGE, settings.language.code)
            .putBoolean(KEY_USE_ITN, settings.useInverseTextNormalization)
            .putFloat(KEY_VAD_THRESHOLD, settings.vadThreshold)
            .putFloat(KEY_VAD_MIN_SILENCE_SECONDS, settings.vadMinSilenceSeconds)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "voice_settings"
        const val KEY_LANGUAGE = "recognition_language"
        const val KEY_USE_ITN = "use_inverse_text_normalization"
        const val KEY_VAD_THRESHOLD = "vad_threshold"
        const val KEY_VAD_MIN_SILENCE_SECONDS = "vad_min_silence_seconds"
    }
}
