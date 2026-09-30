package com.fcitx5sensevoice

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ensures bundled legal assets required for public release stay present.
 *
 * @author stormrise
 * @date 2026/09/29
 */
class LegalAssetsTest {
    @Test
    fun requiredLegalAssetsExist() {
        val root = projectRoot()
        REQUIRED_ASSETS.forEach { relativePath ->
            assertTrue(
                "Missing legal asset: $relativePath",
                Files.isRegularFile(root.resolve(relativePath)),
            )
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

    private companion object {
        val REQUIRED_ASSETS = listOf(
            "app/src/main/assets/legal/privacy_policy.txt",
            "app/src/main/assets/legal/third_party_notices.txt",
            "app/src/main/assets/legal/model_attribution.txt",
            "app/src/main/assets/legal/onnxruntime_third_party_notices.txt",
        )
    }
}
