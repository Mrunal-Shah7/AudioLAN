package com.audiolan.app.domain.model

data class MicSettings(
    val audioSource: String = "DEFAULT",
    val inputChannel: String = "STEREO",
    val sampleRate: Int = 48000,
    val encoding: Int = 16,
    val bufferSize: Int = 960,
    val globalVolume: Float = 1.0f,
)
