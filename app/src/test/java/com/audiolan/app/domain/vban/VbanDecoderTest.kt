package com.audiolan.app.domain.vban

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VbanDecoderTest {
    @Test
    fun roundTripPreservesHeaderFieldsAndPcm() {
        val pcmData = ByteArray(256) { it.toByte() }
        val packet = VbanEncoder.buildPacket("MyStream", pcmData, 128, 1, 48000, 42)

        val decoded = VbanDecoder.decode(packet)

        assertNotNull(decoded)
        val (header, decodedPcm) = decoded!!
        assertEquals(48000, header.sampleRateHz)
        assertEquals(128, header.numSamples)
        assertEquals(1, header.numChannels)
        assertEquals(16, header.bitsPerSample)
        assertEquals("MyStream", header.streamName)
        assertEquals(42, header.frameCounter)
        assertArrayEquals(pcmData, decodedPcm)
    }

    @Test
    fun tooShortReturnsNull() {
        assertNull(VbanDecoder.decode(ByteArray(27)))
    }

    @Test
    fun badMagicReturnsNull() {
        assertNull(VbanDecoder.decode(ByteArray(28)))
    }

    @Test
    fun badSubProtocolReturnsNull() {
        val packet = validPacket().copyOf()
        packet[4] = 0x20

        assertNull(VbanDecoder.decode(packet))
    }

    @Test
    fun allSupportedSampleRateIndicesDecode() {
        for (index in 0..20) {
            val sampleRateHz = VbanSampleRate.fromIndex(index)
            val packet = VbanEncoder.buildPacket("Rate", ByteArray(2), 1, 1, sampleRateHz, index)

            val decoded = VbanDecoder.decode(packet)

            assertNotNull("index $index should decode", decoded)
            assertEquals(sampleRateHz, decoded!!.first.sampleRateHz)
        }
    }

    @Test
    fun official44100IndexDecodes() {
        val packet = validPacket().copyOf()
        packet[4] = 0x10

        assertEquals(44100, VbanDecoder.decode(packet)!!.first.sampleRateHz)
    }

    @Test
    fun unsupportedDataFormatReturnsNull() {
        val packet = validPacket().copyOf()
        packet[7] = 0x07

        assertNull(VbanDecoder.decode(packet))
    }

    @Test
    fun nonPcmCodecReturnsNull() {
        val packet = validPacket().copyOf()
        packet[7] = 0x11

        assertNull(VbanDecoder.decode(packet))
    }

    @Test
    fun supportedPcmFormatsDecodeWithExplicitSampleFormat() {
        val formats = listOf(
            0x00 to (VbanSampleFormat.PCM_8_INT to 8),
            0x01 to (VbanSampleFormat.PCM_16_INT to 16),
            0x02 to (VbanSampleFormat.PCM_24_INT to 24),
            0x03 to (VbanSampleFormat.PCM_32_INT to 32),
            0x04 to (VbanSampleFormat.PCM_32_FLOAT to 32),
            0x05 to (VbanSampleFormat.PCM_64_FLOAT to 64),
        )

        formats.forEach { (formatByte, expected) ->
            val (expectedFormat, expectedBits) = expected
            val packet = validPacket(
                pcmData = ByteArray(expectedBits / 8),
                numSamples = 1,
                numChannels = 1,
            ).copyOf()
            packet[7] = formatByte.toByte()

            val decoded = VbanDecoder.decode(packet)

            assertNotNull("format byte $formatByte should decode", decoded)
            assertEquals(expectedFormat, decoded!!.first.sampleFormat)
            assertEquals(expectedBits, decoded.first.bitsPerSample)
            assertEquals(expectedBits / 8, decoded.second.size)
        }
    }

    @Test
    fun pcmDataTooShortReturnsNull() {
        val packet = VbanEncoder.buildPacket("Short", ByteArray(400), 100, 2, 48000, 1).copyOf(38)

        assertNull(VbanDecoder.decode(packet))
    }

    @Test
    fun streamNameTrailingNullsAreStripped() {
        val packet = validPacket().copyOf()
        packet.fill(0, 8, 24)
        "Hi".encodeToByteArray().copyInto(packet, destinationOffset = 8)

        assertEquals("Hi", VbanDecoder.decode(packet)!!.first.streamName)
    }

    @Test
    fun numSamplesPlusOneIsApplied() {
        val packet = validPacket().copyOf()
        packet[5] = 0x7F

        assertEquals(128, VbanDecoder.decode(packet)!!.first.numSamples)
    }

    @Test
    fun numChannelsPlusOneIsApplied() {
        val packet = validPacket().copyOf()
        packet[6] = 0x00

        assertEquals(1, VbanDecoder.decode(packet)!!.first.numChannels)
    }

    @Test
    fun frameCounterLittleEndianIsDecoded() {
        val packet = validPacket().copyOf()
        packet[24] = 0x04
        packet[25] = 0x03
        packet[26] = 0x02
        packet[27] = 0x01

        assertEquals(0x01020304, VbanDecoder.decode(packet)!!.first.frameCounter)
    }

    private fun validPacket(
        pcmData: ByteArray = ByteArray(512),
        numSamples: Int = 128,
        numChannels: Int = 2,
    ): ByteArray =
        VbanEncoder.buildPacket(
            streamName = "AudioLAN",
            pcmData = pcmData,
            numSamples = numSamples,
            numChannels = numChannels,
            sampleRateHz = 48000,
            frameCounter = 1,
        )
}
