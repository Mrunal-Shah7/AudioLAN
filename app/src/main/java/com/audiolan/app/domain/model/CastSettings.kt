package com.audiolan.app.domain.model

data class CastSettings(
    val channelOut: String = "STEREO",
    val sampleRate: Int = 48000,
    val encoding: Int = 16,
    val bufferSize: Int = 960,
)
