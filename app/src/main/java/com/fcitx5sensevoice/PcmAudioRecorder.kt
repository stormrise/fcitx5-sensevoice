package com.fcitx5sensevoice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max

class PcmAudioRecorder(
    private val onStateChanged: (AudioRecorderState) -> Unit,
    private val onError: (Throwable) -> Unit,
) : AudioRecorder {
    @Volatile
    override var state: AudioRecorderState = AudioRecorderState.IDLE
        private set

    private val stopRequested = AtomicBoolean(false)
    private var worker: Thread? = null

    @Volatile
    private var activeRecorder: AudioRecord? = null

    @Synchronized
    override fun start(onAudio: (ShortArray) -> Unit) {
        if (state !in setOf(AudioRecorderState.IDLE, AudioRecorderState.ERROR)) return
        stopRequested.set(false)
        updateState(AudioRecorderState.STARTING)
        worker = thread(name = "fcitx5-sensevoice-audio") {
            record(onAudio)
        }
    }

    @Synchronized
    override fun stop() {
        if (state !in setOf(AudioRecorderState.STARTING, AudioRecorderState.RECORDING)) return
        updateState(AudioRecorderState.STOPPING)
        stopRequested.set(true)
        runCatching { activeRecorder?.stop() }
    }

    override fun release() {
        stopRequested.set(true)
        runCatching { activeRecorder?.stop() }
    }

    @SuppressLint("MissingPermission")
    private fun record(onAudio: (ShortArray) -> Unit) {
        var recorder: AudioRecord? = null
        try {
            val minBufferBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBufferBytes <= 0) {
                throw IOException("AudioRecord configuration failed: ${diagnostics(minBufferBytes = minBufferBytes)}")
            }

            val bufferSamples = max(minBufferBytes / Short.SIZE_BYTES, SAMPLES_PER_CHUNK)
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSamples * Short.SIZE_BYTES,
            )
            activeRecorder = recorder
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw IOException("AudioRecord initialization failed: ${diagnostics(recorder, minBufferBytes)}")
            }

            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IOException("AudioRecord did not start: ${diagnostics(recorder, minBufferBytes)}")
            }
            updateState(AudioRecorderState.RECORDING)

            val buffer = ShortArray(bufferSamples)
            var capturedSamples = 0
            while (!stopRequested.get() && capturedSamples < MAX_RECORDING_SAMPLES) {
                val remainingSamples = MAX_RECORDING_SAMPLES - capturedSamples
                val read = recorder.read(
                    buffer,
                    0,
                    minOf(buffer.size, remainingSamples),
                    AudioRecord.READ_BLOCKING,
                )
                when {
                    read > 0 -> {
                        onAudio(buffer.copyOf(read))
                        capturedSamples += read
                    }

                    read < 0 && !stopRequested.get() -> {
                        throw IOException("AudioRecord.read failed: code=$read ${diagnostics(recorder, minBufferBytes)}")
                    }
                }
            }
        } catch (error: Throwable) {
            if (!stopRequested.get()) {
                updateState(AudioRecorderState.ERROR)
                onError(error)
                return
            }
        } finally {
            activeRecorder = null
            runCatching {
                if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            }
            recorder?.release()
            worker = null
        }
        updateState(AudioRecorderState.IDLE)
    }

    private fun updateState(newState: AudioRecorderState) {
        state = newState
        onStateChanged(newState)
    }

    private fun diagnostics(recorder: AudioRecord? = null, minBufferBytes: Int? = null): String =
        "sampleRate=$SAMPLE_RATE channel=mono encoding=pcm16 " +
            "minBufferBytes=${minBufferBytes ?: "unknown"} " +
            "audioRecordState=${recorder?.state ?: "unavailable"} " +
            "recordingState=${recorder?.recordingState ?: "unavailable"}"

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val SAMPLES_PER_CHUNK = SAMPLE_RATE / 10
        const val MAX_RECORDING_MS = 30_000
        const val MAX_RECORDING_SAMPLES = SAMPLE_RATE * MAX_RECORDING_MS / 1_000
    }
}
