package com.audiolan.app.domain.vban

import java.nio.charset.StandardCharsets

object VbanDecoder {
    private const val HEADER_SIZE = 28
    private const val PCM_CODEC_CODE = 0x00

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

            val dataFormat = data[7].toInt() and 0xFF
            val codecCode = dataFormat and 0xF0
            if (codecCode != PCM_CODEC_CODE) {
                return null
            }

            val bitResolutionCode = dataFormat and 0x07
            val sampleFormat = when (bitResolutionCode) {
                0 -> VbanSampleFormat.PCM_8_INT
                1 -> VbanSampleFormat.PCM_16_INT
                2 -> VbanSampleFormat.PCM_24_INT
                3 -> VbanSampleFormat.PCM_32_INT
                4 -> VbanSampleFormat.PCM_32_FLOAT
                5 -> VbanSampleFormat.PCM_64_FLOAT
                else -> return null
            }
            val bitsPerSample = sampleFormat.bitsPerSample

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
                sampleFormat = sampleFormat,
                streamName = streamName,
                frameCounter = frameCounter,
            ) to pcmData
        }.getOrNull()

    private val VbanSampleFormat.bitsPerSample: Int
        get() = when (this) {
            VbanSampleFormat.PCM_8_INT -> 8
            VbanSampleFormat.PCM_16_INT -> 16
            VbanSampleFormat.PCM_24_INT -> 24
            VbanSampleFormat.PCM_32_INT,
            VbanSampleFormat.PCM_32_FLOAT -> 32
            VbanSampleFormat.PCM_64_FLOAT -> 64
        }
}
