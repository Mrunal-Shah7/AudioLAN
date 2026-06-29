package com.audiolan.app.domain.vban

object VbanSampleRate {
    private val indexToHz: Map<Int, Int> = listOf(
        0 to 6000,
        1 to 12000,
        2 to 24000,
        3 to 48000,
        4 to 96000,
        5 to 192000,
        6 to 384000,
        7 to 8000,
        8 to 16000,
        9 to 32000,
        10 to 64000,
        11 to 128000,
        12 to 256000,
        13 to 512000,
        14 to 11025,
        15 to 22050,
        16 to 44100,
        17 to 88200,
        18 to 176400,
        19 to 352800,
        20 to 705600,
    ).toMap()

    private val hzToIndex: Map<Int, Int> = indexToHz.entries.associate { (index, hz) -> hz to index }

    fun toIndex(hz: Int): Int =
        hzToIndex[hz] ?: throw IllegalArgumentException("Unsupported sample rate: $hz")

    fun fromIndex(index: Int): Int =
        indexToHz[index] ?: throw IllegalArgumentException("Unknown sample rate index: $index")
}
