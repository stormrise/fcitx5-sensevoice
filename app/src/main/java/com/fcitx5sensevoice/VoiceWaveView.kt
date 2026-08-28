package com.fcitx5sensevoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.log10
import kotlin.math.sqrt

class VoiceWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = WAVE_STROKE_DP * density
    }
    private val levels = IDLE_LEVELS.copyOf()
    private var recording = false

    fun setWaveColor(color: Int) {
        if (paint.color == color) return
        paint.color = color
        invalidate()
    }

    fun setRecording(recording: Boolean) {
        if (this.recording == recording) return
        this.recording = recording
        if (recording) {
            levels.fill(0f)
        } else {
            IDLE_LEVELS.copyInto(levels)
        }
        invalidate()
    }

    fun setAudioLevels(audioLevels: FloatArray) {
        if (!recording) return
        levels.indices.forEach { index ->
            val next = audioLevels.getOrElse(index) { 0f }.coerceIn(0f, 1f)
            val smoothing = if (next >= levels[index]) ATTACK_WEIGHT else RELEASE_WEIGHT
            levels[index] += (next - levels[index]) * smoothing
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val minimumHeight = MINIMUM_WAVE_HEIGHT_DP * density
        val maximumHeight = height * MAXIMUM_HEIGHT_RATIO
        levels.forEachIndexed { index, level ->
            val lineHeight = minimumHeight + (maximumHeight - minimumHeight) * level
            val leftX = width * LEFT_POSITIONS[index]
            val rightX = width - leftX
            val top = centerY - lineHeight / 2f
            val bottom = centerY + lineHeight / 2f
            canvas.drawLine(leftX, top, leftX, bottom, paint)
            canvas.drawLine(rightX, top, rightX, bottom, paint)
        }
    }

    private companion object {
        const val WAVE_STROKE_DP = 5f
        const val MINIMUM_WAVE_HEIGHT_DP = 6f
        const val MAXIMUM_HEIGHT_RATIO = 0.82f
        const val ATTACK_WEIGHT = 0.75f
        const val RELEASE_WEIGHT = 0.35f
        val LEFT_POSITIONS = floatArrayOf(8f / 144f, 21f / 144f, 34f / 144f)
        val IDLE_LEVELS = floatArrayOf(0.1f, 0.42f, 0.72f)
    }
}

internal fun pcmAudioLevels(samples: ShortArray): FloatArray {
    val levels = FloatArray(3)
    if (samples.isEmpty()) return levels

    levels.indices.forEach { index ->
        val start = index * samples.size / levels.size
        val end = (index + 1) * samples.size / levels.size
        if (end <= start) return@forEach

        var sumOfSquares = 0.0
        for (sampleIndex in start until end) {
            val sample = samples[sampleIndex].toDouble()
            sumOfSquares += sample * sample
        }
        val rms = sqrt(sumOfSquares / (end - start))
        if (rms > 0.0) {
            val dbfs = 20.0 * log10(rms / PCM_FULL_SCALE)
            levels[index] = ((dbfs - NOISE_FLOOR_DB) / (SPEECH_PEAK_DB - NOISE_FLOOR_DB))
                .coerceIn(0.0, 1.0)
                .toFloat()
        }
    }
    return levels
}

private const val PCM_FULL_SCALE = 32_768.0
private const val NOISE_FLOOR_DB = -60.0
private const val SPEECH_PEAK_DB = -18.0
