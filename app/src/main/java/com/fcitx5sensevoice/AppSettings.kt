package com.fcitx5sensevoice

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
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

internal fun persistUiLanguage(context: Context, language: UiLanguage) {
    AppSettings(context).saveUiLanguage(language)
    if (Build.VERSION.SDK_INT >= 33) {
        val locales = language.localeOrNull()?.let { LocaleList(it) } ?: LocaleList.getEmptyLocaleList()
        val appContext = context.applicationContext ?: context
        appContext.getSystemService(LocaleManager::class.java).applicationLocales = locales
    }
}

internal enum class UiTheme(val code: String, val nightMode: Int?) {
    SYSTEM("system", null),
    LIGHT("light", Configuration.UI_MODE_NIGHT_NO),
    DARK("dark", Configuration.UI_MODE_NIGHT_YES);

    companion object {
        fun fromCode(code: String?): UiTheme = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

// Applies both UI language and forced light/dark on top of the current configuration.
// Activities and the IME resolve all resources through this context, so one override covers both.
internal fun wrapUiLocale(context: Context): Context {
    val settings = AppSettings(context)
    val locale = settings.loadUiLanguage().localeOrNull()
    val nightMode = settings.loadUiTheme().nightMode
    if (locale == null && nightMode == null) return context
    val config = Configuration(context.resources.configuration)
    if (locale != null) {
        Locale.setDefault(locale)
        config.setLocales(LocaleList(locale))
        config.setLayoutDirection(locale)
    }
    if (nightMode != null) {
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
    }
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

    fun loadUiLanguage(): UiLanguage = UiLanguage.fromCode(preferences.getString(KEY_UI_LANGUAGE, UiLanguage.SYSTEM.code))

    fun saveUiLanguage(language: UiLanguage) {
        preferences.edit().putString(KEY_UI_LANGUAGE, language.code).commit()
    }

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
        const val KEY_UI_LANGUAGE = "ui_language"
        const val KEY_UI_THEME = "ui_theme"
        const val KEY_LANGUAGE = "recognition_language"
        const val KEY_USE_ITN = "use_inverse_text_normalization"
        const val KEY_VAD_THRESHOLD = "vad_threshold"
        const val KEY_VAD_MIN_SILENCE_SECONDS = "vad_min_silence_seconds"
    }
}
