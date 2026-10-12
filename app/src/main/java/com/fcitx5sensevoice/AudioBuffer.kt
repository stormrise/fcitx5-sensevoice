package com.fcitx5sensevoice

// Keeps only the most recent `capacity` samples. Recording itself is unbounded; the buffer only feeds
// the no-segment fallback, so older audio is dropped instead of stopping the recording.
internal class AudioBuffer(capacity: Int) {
    private val data = ShortArray(capacity)
    private var size = 0

    init {
        require(capacity > 0) { "Audio buffer capacity must be positive" }
    }

    @Synchronized
    fun append(samples: ShortArray) {
        val incoming = if (samples.size > data.size) samples.copyOfRange(samples.size - data.size, samples.size) else samples
        val overflow = size + incoming.size - data.size
        if (overflow > 0) {
            data.copyInto(data, destinationOffset = 0, startIndex = overflow, endIndex = size)
            size -= overflow
        }
        incoming.copyInto(data, destinationOffset = size)
        size += incoming.size
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
