package com.fcitx5sensevoice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioBufferTest {
    @Test
    fun bufferKeepsLatestSamplesAndClearsWhenTaken() {
        val buffer = AudioBuffer(3)

        buffer.append(shortArrayOf(1, 2))
        buffer.append(shortArrayOf(3, 4))
        assertArrayEquals(shortArrayOf(2, 3, 4), buffer.take())
        assertArrayEquals(shortArrayOf(), buffer.take())
        buffer.append(shortArrayOf(5, 6, 7, 8, 9))
        assertArrayEquals(shortArrayOf(7, 8, 9), buffer.take())
    }

    @Test
    fun pcm16SamplesAreNormalizedToSherpaRange() {
        val normalized = shortArrayOf(Short.MIN_VALUE, 0, Short.MAX_VALUE).toNormalizedFloatArray()

        assertArrayEquals(floatArrayOf(-1.0f, 0.0f, 32767.0f / 32768.0f), normalized, 0.0f)
    }
}
