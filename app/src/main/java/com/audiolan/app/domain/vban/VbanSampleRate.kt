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
        9 to 22050,
        10 to 44100,
        11 to 11025,
        12 to 32000,
        13 to 88200,
        14 to 176400,
        15 to 352800,
    ).toMap()

    private val hzToIndex: Map<Int, Int> = indexToHz.entries.associate { (index, hz) -> hz to index }

    fun toIndex(hz: Int): Int =
        hzToIndex[hz] ?: throw IllegalArgumentException("Unsupported sample rate: $hz")

    fun fromIndex(index: Int): Int =
        indexToHz[index] ?: throw IllegalArgumentException("Unknown sample rate index: $index")
}
