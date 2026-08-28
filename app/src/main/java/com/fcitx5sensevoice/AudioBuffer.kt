package com.fcitx5sensevoice

internal class AudioBuffer(capacity: Int) {
    private val data = ShortArray(capacity)
    private var size = 0

    init {
        require(capacity > 0) { "Audio buffer capacity must be positive" }
    }

    @Synchronized
    fun append(samples: ShortArray): Int {
        val count = minOf(samples.size, data.size - size)
        samples.copyInto(data, destinationOffset = size, endIndex = count)
        size += count
        return count
    }

    @Synchronized
    fun take(): ShortArray = data.copyOf(size).also { size = 0 }

    @Synchronized
    fun reset() {
        size = 0
    }
}

internal fun ShortArray.toNormalizedFloatArray(): FloatArray =
    FloatArray(size) { index -> this[index].toFloat() / 32768.0f }
