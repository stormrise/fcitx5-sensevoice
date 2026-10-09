package com.fcitx5sensevoice

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
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
    }
}
