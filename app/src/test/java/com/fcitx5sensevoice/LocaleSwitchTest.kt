package com.fcitx5sensevoice

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Language switching cannot be exercised in JVM unit tests against a real Activity.
 * These checks fail on the two regressions that survived Play uploads: skipping
 * wrap/recreate on API 33+, and labeling the launcher with the settings title.
 *
 * @author stormrise
 * @date 2026/10/10
 */
class LocaleSwitchTest {
    @Test
    fun uiLanguageTagsMatchLocaleConfig() {
        val config = readText(projectRoot().resolve("app/src/main/res/xml/locales_config.xml"))
        val tags = UiLanguage.entries.mapNotNull { it.localeOrNull()?.toLanguageTag() }
        assertEquals(listOf("zh-CN", "en", "ja", "ko"), tags)
        tags.forEach { tag ->
            assertTrue("locales_config missing $tag", config.contains("android:name=\"$tag\""))
        }
    }

    @Test
    fun wrapUiLocaleAppliesOnEveryApi() {
        val body = kotlinFunction("app/src/main/java/com/fcitx5sensevoice/AppSettings.kt", "wrapUiLocale")
        assertTrue(body.contains("createConfigurationContext"))
        assertFalse(
            "wrapUiLocale must not skip API 33+; LocaleManager does not refresh a framework Activity",
            "SDK_INT" in body,
        )
    }

    @Test
    fun applyUiPreferencesAlwaysRecreates() {
        val body = kotlinFunction("app/src/main/java/com/fcitx5sensevoice/SettingsActivity.kt", "applyUiPreferences")
        assertTrue(body.contains("persistUiLanguage"))
        assertTrue(body.contains("recreate()"))
        assertFalse(
            "recreate() must not be gated on SDK_INT; API 33+ still needs it",
            "SDK_INT" in body,
        )
    }

    @Test
    fun activitiesWrapAttachBaseContext() {
        listOf("SettingsActivity.kt", "LegalDocumentActivity.kt").forEach { file ->
            val source = readText(projectRoot().resolve("app/src/main/java/com/fcitx5sensevoice/$file"))
            assertTrue(
                "$file must wrap attachBaseContext with wrapUiLocale",
                source.contains("super.attachBaseContext(wrapUiLocale(newBase))"),
            )
        }
    }

    @Test
    fun imeDoesNotReadFrozenServiceResources() {
        // The IME service keeps the resources it was created with, so UI text must come from uiContext().
        val source = readText(projectRoot().resolve("app/src/main/java/com/fcitx5sensevoice/VoiceInputMethodService.kt"))
        assertFalse("IME must not call bare getString()", Regex("""(?<![\w.])getString\(""").containsMatchIn(source))
        assertFalse("IME must not inflate with frozen layoutInflater", "layoutInflater.inflate" in source)
        assertTrue(source.contains("LayoutInflater.from(uiContext())"))
    }

    @Test
    fun launcherUsesAppNameNotSettingsTitle() {
        val manifest = readText(projectRoot().resolve("app/src/main/AndroidManifest.xml"))
        assertTrue(manifest.contains("android:label=\"@string/app_name\""))
        val launcher = Regex(
            """<activity\s+android:name="\.SettingsActivity"[^>]*>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(manifest)?.value ?: error("SettingsActivity missing from manifest")
        assertFalse(
            "LAUNCHER activity label becomes the home-screen name",
            launcher.contains("android:label"),
        )
    }

    private fun kotlinFunction(relativePath: String, name: String): String {
        val text = readText(projectRoot().resolve(relativePath))
        val start = text.indexOf("fun $name")
        require(start >= 0) { "fun $name not found in $relativePath" }
        val brace = text.indexOf('{', start)
        var depth = 0
        for (index in brace until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(brace, index + 1)
                }
            }
        }
        error("unclosed fun $name in $relativePath")
    }

    private fun readText(file: Path): String = String(Files.readAllBytes(file), Charsets.UTF_8)

    private fun projectRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        while (current != null) {
            if (Files.isRegularFile(current.resolve("app/build.gradle.kts"))) return current
            current = current.parent
        }
        error("Could not locate project root from ${Paths.get("").toAbsolutePath()}")
    }
}
