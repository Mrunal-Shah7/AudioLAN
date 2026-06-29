package com.audiolan.app.domain.vban

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VbanEncoderTest {
    @Test
    fun magicBytesAreVban() {
        val packet = packet()

        assertEquals(0x56.toByte(), packet[0])
        assertEquals(0x42.toByte(), packet[1])
        assertEquals(0x41.toByte(), packet[2])
        assertEquals(0x4E.toByte(), packet[3])
    }

    @Test
    fun sampleRateByteEncodes48000AsIndex3() {
        assertEquals(0x03.toByte(), packet(sampleRateHz = 48000)[4])
    }

    @Test
    fun sampleRateByteEncodes44100AsOfficialIndex16() {
        assertEquals(0x10.toByte(), packet(sampleRateHz = 44100)[4])
    }

    @Test
    fun numSamples128EncodesAs127() {
        assertEquals(127.toByte(), packet(numSamples = 128)[5])
    }

    @Test
    fun numSamples1EncodesAs0() {
        assertEquals(0.toByte(), packet(numSamples = 1, pcmData = ByteArray(2))[5])
    }

    @Test
    fun numSamples256EncodesAs255() {
        assertEquals(255.toByte(), packet(numSamples = 256, pcmData = ByteArray(512))[5])
    }

    @Test
    fun monoEncodesAs0ChannelsByte() {
        assertEquals(0.toByte(), packet(numChannels = 1)[6])
    }

    @Test
    fun stereoEncodesAs1ChannelsByte() {
        assertEquals(1.toByte(), packet(numChannels = 2, pcmData = ByteArray(512))[6])
    }

    @Test
    fun dataFormatByteIs16BitPcm() {
        assertEquals(0x01.toByte(), packet()[7])
    }

    @Test
    fun streamNameIsUtf8AndNullPadded() {
        val packet = packet(streamName = "Test")

        assertArrayEquals(byteArrayOf('T'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 't'.code.toByte()), packet.sliceArray(8..11))
        assertArrayEquals(ByteArray(12), packet.sliceArray(12..23))
    }

    @Test
    fun streamNameIsTruncatedTo16Utf8Bytes() {
        val packet = packet(streamName = "ABCDEFGHIJKLMNOPQ")

        assertArrayEquals("ABCDEFGHIJKLMNOP".encodeToByteArray(), packet.sliceArray(8..23))
    }

    @Test
    fun frameCounterIsLittleEndian() {
        val packet = packet(frameCounter = 0x01020304)

        assertEquals(0x04.toByte(), packet[24])
        assertEquals(0x03.toByte(), packet[25])
        assertEquals(0x02.toByte(), packet[26])
        assertEquals(0x01.toByte(), packet[27])
    }

    @Test
    fun frameCounterZeroWritesZeroBytes() {
        assertArrayEquals(ByteArray(4), packet(frameCounter = 0).sliceArray(24..27))
    }

    @Test
    fun packetLengthIsHeaderPlusPcmDataLength() {
        val pcmData = byteArrayOf(1, 2, 3, 4)

        assertEquals(32, packet(pcmData = pcmData, numSamples = 2).size)
    }

    @Test
    fun pcmDataIsAppendedAtOffset28() {
        val pcmData = byteArrayOf(10, 20, 30, 40)

        assertArrayEquals(pcmData, packet(pcmData = pcmData, numSamples = 2).sliceArray(28..31))
    }

    @Test(expected = IllegalArgumentException::class)
    fun numSamples0Throws() {
        packet(numSamples = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun numSamples257Throws() {
        packet(numSamples = 257, pcmData = ByteArray(514))
    }

    @Test(expected = IllegalArgumentException::class)
    fun numChannels0Throws() {
        packet(numChannels = 0)
    }

    private fun packet(
        streamName: String = "AudioLAN",
        pcmData: ByteArray = ByteArray(256),
        numSamples: Int = 128,
        numChannels: Int = 1,
        sampleRateHz: Int = 48000,
        frameCounter: Int = 42,
    ): ByteArray = VbanEncoder.buildPacket(
        streamName = streamName,
        pcmData = pcmData,
        numSamples = numSamples,
        numChannels = numChannels,
        sampleRateHz = sampleRateHz,
        frameCounter = frameCounter,
    )
}
