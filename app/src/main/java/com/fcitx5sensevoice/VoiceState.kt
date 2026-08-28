package com.fcitx5sensevoice

internal enum class VoiceState {
    IDLE,
    STARTING,
    RECORDING,
    STOPPING,
    PROCESSING,
    RESULT,
    ERROR,
}

internal val VoiceState.canCancelRecording: Boolean
    get() = this == VoiceState.STARTING || this == VoiceState.RECORDING
