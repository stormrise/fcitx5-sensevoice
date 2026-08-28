package com.fcitx5sensevoice

interface AudioRecorder {
    val state: AudioRecorderState

    fun start(onAudio: (ShortArray) -> Unit)

    fun stop()

    fun release()
}

enum class AudioRecorderState {
    IDLE,
    STARTING,
    RECORDING,
    STOPPING,
    ERROR,
}
