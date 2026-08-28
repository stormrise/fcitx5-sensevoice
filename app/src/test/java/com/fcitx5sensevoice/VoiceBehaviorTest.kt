package com.fcitx5sensevoice

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceBehaviorTest {
    @Test
    fun onlyActiveRecordingStatesCanBeCancelled() {
        assertTrue(VoiceState.STARTING.canCancelRecording)
        assertTrue(VoiceState.RECORDING.canCancelRecording)
        VoiceState.entries
            .filterNot { it == VoiceState.STARTING || it == VoiceState.RECORDING }
            .forEach { assertFalse(it.canCancelRecording) }
    }

    @Test
    fun transcriptCleaningTrimsOuterWhitespaceOnly() {
        assertEquals("你好 world", " \n你好 world\t".cleanTranscript())
        assertEquals("", " \n\t".cleanTranscript())
    }

    @Test
    fun audioLevelsTrackRealPcmVolume() {
        val silence = pcmAudioLevels(ShortArray(300))
        val quiet = pcmAudioLevels(alternatingSamples(amplitude = 200))
        val loud = pcmAudioLevels(alternatingSamples(amplitude = 4_000))

        silence.forEach { assertEquals(0f, it, 0.0001f) }
        quiet.zip(loud).forEach { (quietLevel, loudLevel) ->
            assertTrue(quietLevel in 0f..1f)
            assertTrue(loudLevel in 0f..1f)
            assertTrue(loudLevel > quietLevel)
        }
    }

    @Test
    fun modelChecksumRejectsMissingOrModifiedFiles() {
        val directory = Files.createTempDirectory("sensevoice-model-test").toFile()
        try {
            val file = directory.resolve("model.onnx")
            file.writeText("SenseVoice")
            assertTrue(ModelChecksum.matches(file, "eeb8c2f4b81d6a7f6e91d5c71fa52e2cb93cb026fec51607f1dbac585ec57d41"))
            file.appendText(" modified")
            assertFalse(ModelChecksum.matches(file, "eeb8c2f4b81d6a7f6e91d5c71fa52e2cb93cb026fec51607f1dbac585ec57d41"))
            assertFalse(ModelChecksum.matches(directory.resolve("missing"), "unused"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun fullRecordingFallbackRunsOnlyWhenVadProducedNoSegments() {
        assertTrue(shouldDecodeFullRecording(vadSegmentCount = 0, bufferedSampleCount = 16_000))
        assertFalse(shouldDecodeFullRecording(vadSegmentCount = 1, bufferedSampleCount = 16_000))
        assertFalse(shouldDecodeFullRecording(vadSegmentCount = 0, bufferedSampleCount = 0))
    }

    @Test
    fun storedAsrSettingsAcceptSupportedValuesAndRejectInvalidOnes() {
        val configured = AsrSettings.fromStoredValues(
            languageCode = "auto",
            useInverseTextNormalization = false,
            vadThreshold = 0.4f,
            vadMinSilenceSeconds = 1.5f,
        )
        assertEquals(RecognitionLanguage.AUTO, configured.language)
        assertFalse(configured.useInverseTextNormalization)
        assertEquals(0.4f, configured.vadThreshold)
        assertEquals(1.5f, configured.vadMinSilenceSeconds)

        val invalid = AsrSettings.fromStoredValues(
            languageCode = "unsupported",
            useInverseTextNormalization = true,
            vadThreshold = 2f,
            vadMinSilenceSeconds = -1f,
        )
        assertEquals(AsrSettings.DEFAULT, invalid)
    }

    @Test
    fun sileroProbabilitiesProduceSpeechSegmentAfterTrailingSilence() {
        val probabilities = ArrayDeque(
            buildList {
                repeat(7) { add(0.01f) }
                repeat(8) { add(0.9f) }
                repeat(22) { add(0.01f) }
            },
        )
        val segmenter = testSegmenter { probabilities.removeFirst() }

        val results = segmenter.accept(ShortArray(37 * 512) { 100 })

        assertEquals(1, results.size)
        assertEquals(15 * 512, results.single().size)
    }

    @Test
    fun shortNoiseDoesNotProduceSpeechSegment() {
        val probabilities = ArrayDeque(
            buildList {
                repeat(7) { add(0.01f) }
                repeat(3) { add(0.9f) }
                repeat(22) { add(0.01f) }
            },
        )
        val segmenter = testSegmenter { probabilities.removeFirst() }

        assertTrue(segmenter.accept(ShortArray(32 * 512) { 100 }).isEmpty())
        assertTrue(segmenter.flush().isEmpty())
    }

    private fun alternatingSamples(amplitude: Int): ShortArray =
        ShortArray(300) { index -> if (index % 2 == 0) amplitude.toShort() else (-amplitude).toShort() }

    private fun testSegmenter(probability: (FloatArray) -> Float) =
        SileroVadSegmenter(
            sampleRate = 16_000,
            frameSize = 512,
            threshold = 0.25f,
            minSpeechSeconds = 0.25f,
            minSilenceSeconds = 0.7f,
            preRollSeconds = 0.2f,
            maxSpeechSeconds = 15f,
            probability = probability,
        )
}
