package com.fcitx5sensevoice

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * @author stormrise
 * @date 2026/09/30
 */
class SystemUiTest {
    @Test
    fun paddingValuesStoresInsets() {
        val padding = PaddingValues(12, 24, 12, 40)
        assertEquals(12, padding.left)
        assertEquals(24, padding.top)
        assertEquals(40, padding.bottom)
    }

    @Test
    fun edgeToEdgeDoesNotSetDeprecatedBarColors() {
        val root = projectRoot()
        val sources = listOf(
            "app/src/main/java/com/fcitx5sensevoice/SystemUi.kt",
            "app/src/main/res/values/themes.xml",
        )
        sources.forEach { relative ->
            val text = String(Files.readAllBytes(root.resolve(relative)), Charsets.UTF_8)
            assertFalse("$relative still sets statusBarColor", "statusBarColor" in text)
            assertFalse("$relative still sets navigationBarColor", "navigationBarColor" in text)
        }
    }

    private fun projectRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        while (current != null) {
            if (Files.isRegularFile(current.resolve("app/build.gradle.kts"))) return current
            current = current.parent
        }
        error("Could not locate project root from ${Paths.get("").toAbsolutePath()}")
    }
}
