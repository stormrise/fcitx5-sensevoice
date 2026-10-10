package com.fcitx5sensevoice

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps translated UI strings aligned with the default Chinese resources.
 *
 * @author stormrise
 * @date 2026/10/09
 */
class LocaleResourcesTest {
    @Test
    fun translatedStringKeysMatchDefault() {
        val root = projectRoot()
        val defaultKeys = stringNames(root.resolve("app/src/main/res/values/strings.xml"))
        LOCALES.forEach { locale ->
            val translated = stringNames(root.resolve("app/src/main/res/values-$locale/strings.xml"))
            assertEquals("string names mismatch in values-$locale", defaultKeys, translated)
        }
    }

    @Test
    fun uiLanguageLabelStaysBilingual() {
        val root = projectRoot()
        val files = listOf("values") + LOCALES.map { "values-$it" }
        files.forEach { folder ->
            val text = readText(root.resolve("app/src/main/res/$folder/strings.xml"))
            val label = Regex("""<string name="ui_language">([^<]+)</string>""").find(text)?.groupValues?.get(1)
            assertEquals("$folder ui_language should stay bilingual", "界面语言 / Language", label)
            assertEquals(
                "$folder ui language options should stay flagged",
                UI_LANGUAGE_LABELS,
                arrayItems(root.resolve("app/src/main/res/$folder/arrays.xml"), "ui_language_labels"),
            )
        }
    }

    @Test
    fun recognitionLanguageLabelsKeepFlags() {
        val root = projectRoot()
        val files = listOf("values") + LOCALES.map { "values-$it" }
        files.forEach { folder ->
            val labels = arrayItems(root.resolve("app/src/main/res/$folder/arrays.xml"), "recognition_language_labels")
            assertEquals("$folder recognition flag count", RECOGNITION_FLAGS.size, labels.size)
            labels.forEachIndexed { index, label ->
                assertTrue(
                    "$folder recognition label $index should start with ${RECOGNITION_FLAGS[index]}: $label",
                    label.startsWith(RECOGNITION_FLAGS[index] + " "),
                )
            }
        }
    }

    @Test
    fun translatedArrayKeysMatchDefault() {
        val root = projectRoot()
        val defaultKeys = arrayNames(root.resolve("app/src/main/res/values/arrays.xml"))
        LOCALES.forEach { locale ->
            val translated = arrayNames(root.resolve("app/src/main/res/values-$locale/arrays.xml"))
            assertEquals("array names mismatch in values-$locale", defaultKeys, translated)
        }
    }

    private fun stringNames(file: Path): List<String> =
        Regex("""<string name="([^"]+)"""").findAll(readText(file)).map { it.groupValues[1] }.toList()

    private fun arrayNames(file: Path): List<String> =
        Regex("""<string-array name="([^"]+)"""").findAll(readText(file)).map { it.groupValues[1] }.toList()

    private fun arrayItems(file: Path, name: String): List<String> {
        val block = Regex("""<string-array name="$name">([\s\S]*?)</string-array>""")
            .find(readText(file))
            ?.groupValues
            ?.get(1)
            ?: error("missing $name in $file")
        return Regex("""<item>([^<]+)</item>""").findAll(block).map { it.groupValues[1] }.toList()
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

    private companion object {
        val LOCALES = listOf("en", "ja", "ko")
        val UI_LANGUAGE_LABELS = listOf(
            "🌐 跟随系统 / System",
            "🇨🇳 中文",
            "🇺🇸 English",
            "🇯🇵 日本語",
            "🇰🇷 한국어",
        )
        val RECOGNITION_FLAGS = listOf("🌐", "🇨🇳", "🇭🇰", "🇺🇸", "🇯🇵", "🇰🇷")
    }
}
