package com.audiolan.app.domain.model

data class TransmitterSettings(
    val audioSource: String = "DEFAULT",
    val inputChannel: String = "STEREO",
    val sampleRate: Int = 48_000,
    val encoding: Int = 16,
    val bufferSize: Int = 960,
    val globalVolume: Float = 1.0f,
)
