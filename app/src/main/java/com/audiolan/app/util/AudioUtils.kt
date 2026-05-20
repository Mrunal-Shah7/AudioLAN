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
}
