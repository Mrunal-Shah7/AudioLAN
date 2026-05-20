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
    fun allSampleRateIndicesDecode() {
        for (index in 0..15) {
            val sampleRateHz = VbanSampleRate.fromIndex(index)
            val packet = VbanEncoder.buildPacket("Rate", ByteArray(2), 1, 1, sampleRateHz, index)

            val decoded = VbanDecoder.decode(packet)

            assertNotNull("index $index should decode", decoded)
            assertEquals(sampleRateHz, decoded!!.first.sampleRateHz)
        }
    }

    @Test
    fun unsupportedDataFormatReturnsNull() {
        val packet = validPacket().copyOf()
        packet[7] = 0x07

        assertNull(VbanDecoder.decode(packet))
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

    private fun validPacket(): ByteArray =
        VbanEncoder.buildPacket(
            streamName = "AudioLAN",
            pcmData = ByteArray(512),
            numSamples = 128,
            numChannels = 2,
            sampleRateHz = 48000,
            frameCounter = 1,
        )
}
