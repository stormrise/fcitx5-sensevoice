package com.fcitx5sensevoice

import android.content.Context
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

internal data class SenseVoiceModelAssets(
    val model: String,
    val tokens: String,
    val vad: String,
)

internal class ModelManager(private val context: Context) {
    private val modelDirectory = File(context.filesDir, MODEL_DIRECTORY)

    fun isInstalled(): Boolean = modelDirectory.isDirectory && verify()

    fun installFromAssets(): SenseVoiceModelAssets {
        if (isInstalled()) return modelAssets()

        val stagingDirectory = File(context.filesDir, "$MODEL_DIRECTORY.installing")
        stagingDirectory.deleteRecursively()
        check(stagingDirectory.mkdirs()) { "Cannot create SenseVoice staging directory" }
        try {
            copyAsset(MODEL_ASSET, File(stagingDirectory, MODEL_FILE))
            copyAsset(TOKENS_ASSET, File(stagingDirectory, TOKENS_FILE))
            copyAsset(VAD_ASSET, File(stagingDirectory, VAD_FILE))
            check(verify(stagingDirectory)) { "SenseVoice model SHA-256 verification failed" }

            modelDirectory.deleteRecursively()
            modelDirectory.parentFile?.mkdirs()
            check(stagingDirectory.renameTo(modelDirectory)) { "Cannot install SenseVoice model" }
            modelDirectory.walkTopDown().filter(File::isFile).forEach { it.setReadOnly() }
        } catch (error: Throwable) {
            stagingDirectory.deleteRecursively()
            throw error
        }
        return modelAssets()
    }

    fun getSenseVoiceModel(): SenseVoiceModelAssets {
        check(isInstalled()) { "SenseVoice model is not installed or failed verification" }
        return modelAssets()
    }

    private fun modelAssets(): SenseVoiceModelAssets =
        SenseVoiceModelAssets(
            model = File(modelDirectory, MODEL_FILE).absolutePath,
            tokens = File(modelDirectory, TOKENS_FILE).absolutePath,
            vad = File(modelDirectory, VAD_FILE).absolutePath,
        )

    fun verify(): Boolean = verify(modelDirectory)

    fun delete() {
        if (modelDirectory.exists() && !modelDirectory.deleteRecursively()) {
            throw IllegalStateException("Cannot delete SenseVoice model")
        }
    }

    private fun verify(directory: File): Boolean =
        ModelChecksum.matches(File(directory, MODEL_FILE), MODEL_SHA256) &&
                ModelChecksum.matches(File(directory, TOKENS_FILE), TOKENS_SHA256) &&
                ModelChecksum.matches(File(directory, VAD_FILE), VAD_SHA256)

    private fun copyAsset(assetPath: String, destination: File) {
        destination.parentFile?.mkdirs()
        try {
            context.assets.open(assetPath).use { input ->
                destination.outputStream().buffered().use(input::copyTo)
            }
        } catch (error: Exception) {
            throw FileNotFoundException("SenseVoice asset is missing: $assetPath").apply {
                initCause(error)
            }
        }
    }

    private companion object {
        const val MODEL_DIRECTORY = "models/sensevoice"
        const val MODEL_FILE = "model.int8.onnx"
        const val TOKENS_FILE = "tokens.txt"
        const val VAD_FILE = "silero_vad.onnx"
        const val MODEL_ASSET = "$MODEL_DIRECTORY/$MODEL_FILE"
        const val TOKENS_ASSET = "$MODEL_DIRECTORY/$TOKENS_FILE"
        const val VAD_ASSET = "$MODEL_DIRECTORY/$VAD_FILE"
        const val MODEL_SHA256 = "12ca1a2ae7ecf3e0019ef2822307ee0b5cadc9196569e379b4c4026f8205276d"
        const val TOKENS_SHA256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"
        const val VAD_SHA256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
    }
}

internal object ModelChecksum {
    fun matches(file: File, expectedSha256: String): Boolean = sha256(file) == expectedSha256

    private fun sha256(file: File): String? {
        if (!file.isFile) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(CHECKSUM_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private const val CHECKSUM_BUFFER_SIZE = 1024 * 1024
}
