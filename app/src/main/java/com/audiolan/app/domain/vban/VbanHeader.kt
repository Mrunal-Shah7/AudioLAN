package com.audiolan.app.domain.vban

data class VbanHeader(
    val sampleRateHz: Int,
    val numSamples: Int,
    val numChannels: Int,
    val bitsPerSample: Int,
    val streamName: String,
    val frameCounter: Int,
)
