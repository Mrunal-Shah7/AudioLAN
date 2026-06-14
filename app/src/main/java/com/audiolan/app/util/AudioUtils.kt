package com.audiolan.app.util

const val BYTES_PER_SAMPLE_16BIT = 2

object AudioUtils {
    fun applyVolume(pcm: ByteArray, volume: Float) {
        var i = 0
        while (i < pcm.size) {
            if (i + 1 >= pcm.size) break

            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF))
                .toShort()
                .toInt()
            val scaled = (sample * volume).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            pcm[i] = (scaled and 0xFF).toByte()
            pcm[i + 1] = ((scaled shr 8) and 0xFF).toByte()
            i += BYTES_PER_SAMPLE_16BIT
        }
    }

    fun computePeakLevel(pcm: ByteArray, numChannels: Int): Pair<Float, Float> {
        val channels = numChannels.coerceAtLeast(1)
        val bytesPerFrame = channels * BYTES_PER_SAMPLE_16BIT
        var leftPeak = 0
        var rightPeak = 0
        var offset = 0

        while (offset + 1 < pcm.size) {
            var channel = 0
            while (channel < channels && offset + channel * BYTES_PER_SAMPLE_16BIT + 1 < pcm.size) {
                val sampleOffset = offset + channel * BYTES_PER_SAMPLE_16BIT
                val sample = ((pcm[sampleOffset + 1].toInt() shl 8) or (pcm[sampleOffset].toInt() and 0xFF))
                    .toShort()
                    .toInt()
                val amplitude = if (sample == Short.MIN_VALUE.toInt()) {
                    Short.MAX_VALUE.toInt()
                } else {
                    kotlin.math.abs(sample)
                }

                if (channels == 1 || channel % 2 == 0) {
                    if (amplitude > leftPeak) leftPeak = amplitude
                } else {
                    if (amplitude > rightPeak) rightPeak = amplitude
                }
                channel++
            }
            offset += bytesPerFrame
        }

        if (channels == 1) {
            rightPeak = leftPeak
        }

        return Pair(
            (leftPeak / PCM_16BIT_MAX).coerceIn(0f, 1f),
            (rightPeak / PCM_16BIT_MAX).coerceIn(0f, 1f),
        )
    }

    private const val PCM_16BIT_MAX = 32767f
}
