package com.audiolan.app.domain.vban

import com.audiolan.app.domain.model.NetQuality

object VbanNetQuality {
    fun calculateSamplesPerPacket(
        netQuality: NetQuality,
        channels: Int,
        bitsPerSample: Int,
    ): Int {
        require(channels > 0) { "channels must be greater than 0: $channels" }
        require(bitsPerSample > 0) { "bitsPerSample must be greater than 0: $bitsPerSample" }

        val referenceBytes = when (netQuality) {
            NetQuality.OPTIMAL -> 512
            NetQuality.FAST -> 1024
            NetQuality.MEDIUM -> 2048
            NetQuality.SLOW -> 4096
            NetQuality.VERY_SLOW -> 8192
        }
        val bytesPerSampleFrame = channels * (bitsPerSample / 8)
        require(bytesPerSampleFrame > 0) {
            "bytesPerSampleFrame must be greater than 0 for channels=$channels and bitsPerSample=$bitsPerSample"
        }

        val maxSamples = referenceBytes / bytesPerSampleFrame
        var samples = 1
        while (samples * 2 <= maxSamples && samples * 2 <= 256) {
            samples *= 2
        }
        return samples
    }
}
