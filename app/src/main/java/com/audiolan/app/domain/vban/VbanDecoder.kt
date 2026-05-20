package com.audiolan.app.domain.vban

import java.nio.charset.StandardCharsets

object VbanDecoder {
    private const val HEADER_SIZE = 28

    fun decode(data: ByteArray): Pair<VbanHeader, ByteArray>? =
        runCatching {
            if (data.size < HEADER_SIZE) {
                return null
            }

            if (
                data[0] != 0x56.toByte() ||
                data[1] != 0x42.toByte() ||
                data[2] != 0x41.toByte() ||
                data[3] != 0x4E.toByte()
            ) {
                return null
            }

            val sampleRateAndSubProtocol = data[4].toInt() and 0xFF
            val subProtocol = sampleRateAndSubProtocol shr 5
            if (subProtocol != 0) {
                return null
            }

            val sampleRateIndex = sampleRateAndSubProtocol and 0x1F
            val sampleRateHz = VbanSampleRate.fromIndex(sampleRateIndex)
            val numSamples = (data[5].toInt() and 0xFF) + 1
            val numChannels = (data[6].toInt() and 0xFF) + 1

            val bitResolutionCode = (data[7].toInt() and 0xFF) and 0x07
            val bitsPerSample = when (bitResolutionCode) {
                0 -> 8
                1 -> 16
                2 -> 24
                3 -> 32
                else -> return null
            }

            val streamName = data
                .sliceArray(8..23)
                .toString(StandardCharsets.UTF_8)
                .trimEnd('\u0000')

            val frameCounter =
                (data[24].toInt() and 0xFF) or
                    ((data[25].toInt() and 0xFF) shl 8) or
                    ((data[26].toInt() and 0xFF) shl 16) or
                    ((data[27].toInt() and 0xFF) shl 24)

            val expectedPcmSize = numSamples * numChannels * (bitsPerSample / 8)
            if (data.size < HEADER_SIZE + expectedPcmSize) {
                return null
            }

            val pcmData = data.sliceArray(HEADER_SIZE until HEADER_SIZE + expectedPcmSize)
            VbanHeader(
                sampleRateHz = sampleRateHz,
                numSamples = numSamples,
                numChannels = numChannels,
                bitsPerSample = bitsPerSample,
                streamName = streamName,
                frameCounter = frameCounter,
            ) to pcmData
        }.getOrNull()
}
