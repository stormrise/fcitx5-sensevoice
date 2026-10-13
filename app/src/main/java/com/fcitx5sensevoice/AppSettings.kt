package com.fcitx5sensevoice

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

internal enum class UiLanguage(val code: String) {
    SYSTEM("system"),
    CHINESE("zh"),
    ENGLISH("en"),
    JAPANESE("ja"),
    KOREAN("ko");

    fun localeOrNull(): Locale? = when (this) {
        SYSTEM -> null
        CHINESE -> Locale.SIMPLIFIED_CHINESE
        ENGLISH -> Locale.ENGLISH
        JAPANESE -> Locale.JAPANESE
        KOREAN -> Locale.KOREAN
    }

    companion object {
        fun fromCode(code: String?): UiLanguage = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

// Per-app language is owned by AppCompat: framework LocaleManager on API 33+, its own storage below.
internal fun UiLanguage.toLocaleList(): LocaleListCompat =
    localeOrNull()?.let { LocaleListCompat.create(it) } ?: LocaleListCompat.getEmptyLocaleList()

internal fun currentUiLanguage(context: Context): UiLanguage {
    val locale = LocaleManagerCompat.getApplicationLocales(context).get(0) ?: return UiLanguage.SYSTEM
    return UiLanguage.entries.firstOrNull { it.localeOrNull()?.language == locale.language } ?: UiLanguage.SYSTEM
}

internal enum class UiTheme(val code: String, val appCompatMode: Int, val nightMode: Int?) {
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, null),
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_YES);

    companion object {
        fun fromCode(code: String?): UiTheme = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

// AppCompat handles forced night mode for activities; the IME has no delegate, so it overrides the config itself.
internal fun wrapUiTheme(context: Context): Context {
    val nightMode = AppSettings(context).loadUiTheme().nightMode ?: return context
    val config = Configuration(context.resources.configuration)
    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
    return context.createConfigurationContext(config)
}

internal enum class RecognitionLanguage(val code: String) {
    AUTO("auto"),
    MANDARIN("zh"),
    CANTONESE("yue"),
    ENGLISH("en"),
    JAPANESE("ja"),
    KOREAN("ko");

    companion object {
        fun fromCode(code: String?): RecognitionLanguage = entries.firstOrNull { it.code == code } ?: AUTO
    }
}

internal data class AsrSettings(
    val language: RecognitionLanguage = RecognitionLanguage.AUTO,
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
    private val preferences =
        (context.applicationContext ?: context).getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadUiTheme(): UiTheme = UiTheme.fromCode(preferences.getString(KEY_UI_THEME, UiTheme.SYSTEM.code))

    fun saveUiTheme(theme: UiTheme) {
        preferences.edit().putString(KEY_UI_THEME, theme.code).commit()
    }

    fun load(): AsrSettings = AsrSettings.fromStoredValues(
        languageCode = preferences.getString(KEY_LANGUAGE, RecognitionLanguage.AUTO.code),
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
        const val KEY_UI_THEME = "ui_theme"
        const val KEY_LANGUAGE = "recognition_language"
        const val KEY_USE_ITN = "use_inverse_text_normalization"
        const val KEY_VAD_THRESHOLD = "vad_threshold"
        const val KEY_VAD_MIN_SILENCE_SECONDS = "vad_min_silence_seconds"
    }
}
