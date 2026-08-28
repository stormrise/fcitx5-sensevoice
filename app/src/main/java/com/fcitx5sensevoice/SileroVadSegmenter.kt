package com.fcitx5sensevoice

import java.util.ArrayDeque
import kotlin.math.ceil

internal class SileroVadSegmenter(
    sampleRate: Int,
    private val frameSize: Int,
    private val threshold: Float,
    minSpeechSeconds: Float,
    minSilenceSeconds: Float,
    preRollSeconds: Float,
    maxSpeechSeconds: Float,
    private val probability: (FloatArray) -> Float,
) {
    private class Frame(val samples: ShortArray, val size: Int = samples.size)

    data class Stats(
        val frameCount: Int,
        val speechFrameCount: Int,
        val maxProbability: Float,
    )

    private val minSpeechFrames = framesFor(sampleRate, minSpeechSeconds)
    private val minSilenceFrames = framesFor(sampleRate, minSilenceSeconds)
    private val preRollFrameCount = framesFor(sampleRate, preRollSeconds)
    private val maxSpeechSamples = (sampleRate * maxSpeechSeconds).toInt()
    private val negativeThreshold = (threshold - 0.15f).coerceAtLeast(0.01f)
    private val pendingFrame = ShortArray(frameSize)
    private val preRoll = ArrayDeque<Frame>()
    private val segmentFrames = mutableListOf<Frame>()
    private var pendingSize = 0
    private var candidateSpeechFrames = 0
    private var silenceFrames = 0
    private var segmentSamples = 0
    private var active = false
    private var frameCount = 0
    private var speechFrameCount = 0
    private var maxProbability = 0f

    fun accept(samples: ShortArray): List<ShortArray> = buildList {
        var offset = 0
        while (offset < samples.size) {
            val copied = minOf(frameSize - pendingSize, samples.size - offset)
            samples.copyInto(pendingFrame, destinationOffset = pendingSize, startIndex = offset, endIndex = offset + copied)
            pendingSize += copied
            offset += copied
            if (pendingSize == frameSize) {
                process(Frame(pendingFrame.copyOf()), this)
                pendingSize = 0
            }
        }
    }

    fun flush(): List<ShortArray> = buildList {
        if (pendingSize > 0 && (active || candidateSpeechFrames > 0)) {
            segmentFrames += Frame(pendingFrame.copyOf(pendingSize), pendingSize)
            segmentSamples += pendingSize
        }
        if (active) emitSegment(trailingFrames = 0, output = this)
        clearSegmentationState()
        pendingSize = 0
    }

    fun stats(): Stats = Stats(frameCount, speechFrameCount, maxProbability)

    fun reset() {
        clearSegmentationState()
        pendingSize = 0
        frameCount = 0
        speechFrameCount = 0
        maxProbability = 0f
    }

    private fun process(frame: Frame, output: MutableList<ShortArray>) {
        val score = probability(frame.samples.toNormalizedFloatArray())
        frameCount++
        if (score >= threshold) speechFrameCount++
        maxProbability = maxOf(maxProbability, score)

        if (!active) {
            processBeforeSpeech(frame, score)
            return
        }

        segmentFrames += frame
        segmentSamples += frame.size
        silenceFrames = if (score < negativeThreshold) silenceFrames + 1 else 0
        when {
            silenceFrames >= minSilenceFrames -> emitSegment(silenceFrames, output)
            segmentSamples >= maxSpeechSamples -> emitSegment(trailingFrames = 0, output = output)
        }
    }

    private fun processBeforeSpeech(frame: Frame, score: Float) {
        if (score >= threshold) {
            if (candidateSpeechFrames == 0) {
                segmentFrames += preRoll
                segmentSamples = preRoll.sumOf { it.size }
                preRoll.clear()
            }
            segmentFrames += frame
            segmentSamples += frame.size
            candidateSpeechFrames++
            if (candidateSpeechFrames >= minSpeechFrames) active = true
            return
        }

        if (candidateSpeechFrames > 0) {
            segmentFrames.forEach(::addPreRoll)
            segmentFrames.clear()
            segmentSamples = 0
            candidateSpeechFrames = 0
        }
        addPreRoll(frame)
    }

    private fun emitSegment(trailingFrames: Int, output: MutableList<ShortArray>) {
        val contentFrameCount = segmentFrames.size - trailingFrames
        if (contentFrameCount > 0) {
            output += flatten(segmentFrames.subList(0, contentFrameCount))
        }
        if (trailingFrames > 0) {
            segmentFrames.subList(contentFrameCount, segmentFrames.size).forEach(::addPreRoll)
        }
        segmentFrames.clear()
        segmentSamples = 0
        candidateSpeechFrames = 0
        silenceFrames = 0
        active = false
    }

    private fun addPreRoll(frame: Frame) {
        preRoll += frame
        while (preRoll.size > preRollFrameCount) preRoll.removeFirst()
    }

    private fun clearSegmentationState() {
        preRoll.clear()
        segmentFrames.clear()
        segmentSamples = 0
        candidateSpeechFrames = 0
        silenceFrames = 0
        active = false
    }

    private fun flatten(frames: List<Frame>): ShortArray {
        val result = ShortArray(frames.sumOf { it.size })
        var offset = 0
        frames.forEach { frame ->
            frame.samples.copyInto(result, destinationOffset = offset, endIndex = frame.size)
            offset += frame.size
        }
        return result
    }

    private fun framesFor(sampleRate: Int, seconds: Float): Int =
        ceil(sampleRate * seconds / frameSize).toInt().coerceAtLeast(1)
}
