package com.fcitx5sensevoice

internal interface AsrEngine {
    fun init()

    fun reset()

    fun acceptAudio(samples: ShortArray): List<String>

    fun finish(): List<String>

    fun release()
}
