package com.audiolan.app.domain.vban

import java.nio.charset.StandardCharsets

object VbanEncoder {
    private const val HEADER_SIZE = 28
    private const val STREAM_NAME_SIZE = 16

    fun buildPacket(
        streamName: String,
        pcmData: ByteArray,
        numSamples: Int,
        numChannels: Int,
        sampleRateHz: Int,
        frameCounter: Int,
    ): ByteArray {
        require(numSamples in 1..256) { "numSamples must be in 1..256: $numSamples" }
        require(numChannels in 1..256) { "numChannels must be in 1..256: $numChannels" }

        val header = ByteArray(HEADER_SIZE)
        header[0] = 0x56
        header[1] = 0x42
        header[2] = 0x41
        header[3] = 0x4E
        header[4] = (VbanSampleRate.toIndex(sampleRateHz) and 0x1F).toByte()
        header[5] = (numSamples - 1).toByte()
        header[6] = (numChannels - 1).toByte()
        header[7] = 0x01

        val streamNameBytes = streamName.toByteArray(StandardCharsets.UTF_8)
        streamNameBytes.copyInto(
            destination = header,
            destinationOffset = 8,
            startIndex = 0,
            endIndex = minOf(streamNameBytes.size, STREAM_NAME_SIZE),
        )

        header[24] = (frameCounter and 0xFF).toByte()
        header[25] = ((frameCounter shr 8) and 0xFF).toByte()
        header[26] = ((frameCounter shr 16) and 0xFF).toByte()
        header[27] = ((frameCounter shr 24) and 0xFF).toByte()

        return header + pcmData
    }
}
