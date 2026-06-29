package com.audiolan.app.domain.vban

data class VbanHeader(
    val sampleRateHz: Int,
    val numSamples: Int,
    val numChannels: Int,
    val bitsPerSample: Int,
    val sampleFormat: VbanSampleFormat = VbanSampleFormat.PCM_16_INT,
    val streamName: String,
    val frameCounter: Int,
)

enum class VbanSampleFormat {
    PCM_8_INT,
    PCM_16_INT,
    PCM_24_INT,
    PCM_32_INT,
    PCM_32_FLOAT,
    PCM_64_FLOAT,
}
