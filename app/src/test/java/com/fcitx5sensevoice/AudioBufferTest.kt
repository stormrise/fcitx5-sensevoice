package com.fcitx5sensevoice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioBufferTest {
    @Test
    fun bufferIsBoundedAndClearedWhenTaken() {
        val buffer = AudioBuffer(3)

        assertEquals(2, buffer.append(shortArrayOf(1, 2)))
        assertEquals(1, buffer.append(shortArrayOf(3, 4)))
        assertArrayEquals(shortArrayOf(1, 2, 3), buffer.take())
        assertArrayEquals(shortArrayOf(), buffer.take())
    }

    @Test
    fun pcm16SamplesAreNormalizedToSherpaRange() {
        val normalized = shortArrayOf(Short.MIN_VALUE, 0, Short.MAX_VALUE).toNormalizedFloatArray()

        assertArrayEquals(floatArrayOf(-1.0f, 0.0f, 32767.0f / 32768.0f), normalized, 0.0f)
    }
}
