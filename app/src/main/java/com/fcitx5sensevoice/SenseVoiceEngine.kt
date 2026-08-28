package com.fcitx5sensevoice

import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

internal class SenseVoiceEngine(
    private val model: SenseVoiceModelAssets,
    private val settings: AsrSettings,
) : AsrEngine {
    private val audio = AudioBuffer(MAX_AUDIO_SAMPLES)
    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var segmenter: SileroVadSegmenter? = null
    private var vadSegmentCount = 0

    @Synchronized
    override fun init() {
        if (recognizer != null) return
        Log.i(TAG, "MODEL_INIT_START")
        val newVad = Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = model.vad,
                    threshold = settings.vadThreshold,
                    minSilenceDuration = settings.vadMinSilenceSeconds,
                    minSpeechDuration = VAD_MIN_SPEECH_SECONDS,
                    windowSize = VAD_WINDOW_SAMPLES,
                    maxSpeechDuration = VAD_MAX_SPEECH_SECONDS,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
            ),
        )
        try {
            newVad.acceptWaveform(FloatArray(VAD_WINDOW_SAMPLES))
            newVad.reset()
            recognizer = OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        senseVoice = OfflineSenseVoiceModelConfig(
                            model = model.model,
                            language = settings.language.code,
                            useInverseTextNormalization = settings.useInverseTextNormalization,
                        ),
                        tokens = model.tokens,
                        numThreads = 1,
                        provider = "cpu",
                    ),
                    decodingMethod = "greedy_search",
                ),
            )
            vad = newVad
            segmenter = createSegmenter(newVad)
        } catch (error: Throwable) {
            newVad.release()
            throw error
        }
        Log.i(
            TAG,
            "MODEL_INIT_SUCCESS language=${settings.language.code} itn=${settings.useInverseTextNormalization} " +
                    "vadThreshold=${settings.vadThreshold} endpointSilence=${settings.vadMinSilenceSeconds}",
        )
    }

    @Synchronized
    override fun reset() {
        audio.reset()
        vadSegmentCount = 0
        vad?.reset()
        segmenter?.reset()
    }

    @Synchronized
    override fun acceptAudio(samples: ShortArray): List<String> {
        val accepted = audio.append(samples)
        if (accepted != samples.size) {
            Log.w(TAG, "ASR_BUFFER_FULL droppedSamples=${samples.size - accepted}")
        }
        return decodeSegments(checkNotNull(segmenter) { "Silero VAD segmenter is not initialized" }.accept(samples))
    }

    @Synchronized
    override fun finish(): List<String> {
        val activeVad = checkNotNull(vad) { "Silero VAD is not initialized" }
        val activeSegmenter = checkNotNull(segmenter) { "Silero VAD segmenter is not initialized" }
        return try {
            val results = decodeSegments(activeSegmenter.flush())
            val samples = audio.take()
            val stats = activeSegmenter.stats()
            Log.i(
                TAG,
                "VAD_STATS frames=${stats.frameCount} speechFrames=${stats.speechFrameCount} " +
                        "maxProbability=${stats.maxProbability}",
            )
            if (shouldDecodeFullRecording(vadSegmentCount, samples.size)) {
                Log.w(TAG, "VAD_FALLBACK samples=${samples.size}")
                decode(samples.toNormalizedFloatArray())?.let(::listOf).orEmpty()
            } else {
                results
            }
        } finally {
            audio.reset()
            vadSegmentCount = 0
            activeVad.reset()
            activeSegmenter.reset()
        }
    }

    private fun decodeSegments(segments: List<ShortArray>): List<String> = buildList {
        segments.forEach { samples ->
            vadSegmentCount++
            Log.i(TAG, "VAD_SEGMENT samples=${samples.size}")
            decode(samples.toNormalizedFloatArray())?.let(::add)
        }
    }

    private fun createSegmenter(activeVad: Vad): SileroVadSegmenter =
        SileroVadSegmenter(
            sampleRate = SAMPLE_RATE,
            frameSize = VAD_WINDOW_SAMPLES,
            threshold = settings.vadThreshold,
            minSpeechSeconds = VAD_MIN_SPEECH_SECONDS,
            minSilenceSeconds = settings.vadMinSilenceSeconds,
            preRollSeconds = VAD_PRE_ROLL_SECONDS,
            maxSpeechSeconds = VAD_MAX_SPEECH_SECONDS,
            probability = activeVad::compute,
        )

    private fun decode(samples: FloatArray): String? {
        val activeRecognizer = checkNotNull(recognizer) { "SenseVoice is not initialized" }
        val startedAtMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "ASR_START samples=${samples.size} sampleRate=$SAMPLE_RATE")
        val stream = activeRecognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            activeRecognizer.decode(stream)
            val result = activeRecognizer.getResult(stream)
            val text = result.text.cleanTranscript()
            val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
            if (text.isEmpty()) {
                Log.i(TAG, "ASR_EMPTY samples=${samples.size} elapsedMs=$elapsedMs")
            } else {
                Log.i(
                    TAG,
                    "ASR_SUCCESS samples=${samples.size} elapsedMs=$elapsedMs textLength=${text.length} lang=${result.lang}",
                )
            }
            text.ifEmpty { null }
        } finally {
            stream.release()
        }
    }

    @Synchronized
    override fun release() {
        audio.reset()
        vadSegmentCount = 0
        segmenter = null
        vad?.release()
        vad = null
        recognizer?.release()
        recognizer = null
    }

    private companion object {
        const val TAG = "FCITX5-SENSEVOICE"
        const val SAMPLE_RATE = 16_000
        const val VAD_MIN_SPEECH_SECONDS = 0.25f
        const val VAD_PRE_ROLL_SECONDS = 0.2f
        const val VAD_MAX_SPEECH_SECONDS = 15.0f
        const val VAD_WINDOW_SAMPLES = 512
        const val MAX_RECORDING_SECONDS = 30
        const val MAX_AUDIO_SAMPLES = SAMPLE_RATE * MAX_RECORDING_SECONDS
    }
}

internal fun shouldDecodeFullRecording(vadSegmentCount: Int, bufferedSampleCount: Int): Boolean =
    vadSegmentCount == 0 && bufferedSampleCount > 0
