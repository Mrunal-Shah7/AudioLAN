package com.audiolan.app.domain.vban

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VbanPacketizerTest {
    @Test
    fun evenSplitProducesTwoPackets() {
        val packets = packetize(ByteArray(1024), samplesPerPacket = 256, numChannels = 1)

        assertEquals(2, packets.size)
        assertHeader(packets[0], numSamples = 256, numChannels = 1, frameCounter = 0)
        assertHeader(packets[1], numSamples = 256, numChannels = 1, frameCounter = 1)
        assertEquals(512, VbanDecoder.decode(packets[0])!!.second.size)
        assertEquals(512, VbanDecoder.decode(packets[1])!!.second.size)
    }

    @Test
    fun partialLastPacketIsSentWithActualSampleCount() {
        val packets = packetize(ByteArray(960), samplesPerPacket = 256, numChannels = 1)

        assertEquals(2, packets.size)
        assertHeader(packets[0], numSamples = 256, numChannels = 1, frameCounter = 0)
        assertHeader(packets[1], numSamples = 224, numChannels = 1, frameCounter = 1)
        assertEquals(223.toByte(), packets[1][5])
        assertEquals(448, VbanDecoder.decode(packets[1])!!.second.size)
    }

    @Test
    fun twoPacketsCanBeProducedFromExactlyTwoSmallerChunks() {
        val packets = packetize(ByteArray(512), samplesPerPacket = 128, numChannels = 1)

        assertEquals(2, packets.size)
        assertHeader(packets[0], numSamples = 128, numChannels = 1, frameCounter = 0)
        assertHeader(packets[1], numSamples = 128, numChannels = 1, frameCounter = 1)
    }

    @Test
    fun stereoPacketUsesFourByteFrames() {
        val packets = packetize(ByteArray(512), samplesPerPacket = 128, numChannels = 2)

        assertEquals(1, packets.size)
        assertHeader(packets[0], numSamples = 128, numChannels = 2, frameCounter = 0)
        assertEquals(512, VbanDecoder.decode(packets[0])!!.second.size)
    }

    @Test
    fun subFrameRemainderIsDiscarded() {
        val packets = packetize(ByteArray(513), samplesPerPacket = 256, numChannels = 1)

        assertEquals(1, packets.size)
        assertHeader(packets[0], numSamples = 256, numChannels = 1, frameCounter = 0)
    }

    @Test
    fun frameCounterContinuesAcrossCalls() {
        val counter = AtomicInteger(0)

        val firstCall = packetize(ByteArray(1536), samplesPerPacket = 256, numChannels = 1, counter = counter)
        val secondCall = packetize(ByteArray(1536), samplesPerPacket = 256, numChannels = 1, counter = counter)

        assertEquals(3, firstCall.size)
        assertEquals(3, secondCall.size)
        assertEquals(6, counter.get())
        assertEquals(0, VbanDecoder.decode(firstCall[0])!!.first.frameCounter)
        assertEquals(1, VbanDecoder.decode(firstCall[1])!!.first.frameCounter)
        assertEquals(2, VbanDecoder.decode(firstCall[2])!!.first.frameCounter)
        assertEquals(3, VbanDecoder.decode(secondCall[0])!!.first.frameCounter)
        assertEquals(4, VbanDecoder.decode(secondCall[1])!!.first.frameCounter)
        assertEquals(5, VbanDecoder.decode(secondCall[2])!!.first.frameCounter)
    }

    @Test
    fun producedPacketsAreDecodableAndPreservePcmBytes() {
        val rawPcm = ByteArray(960) { it.toByte() }
        val packets = packetize(rawPcm, samplesPerPacket = 256, numChannels = 1)

        assertNotNull(VbanDecoder.decode(packets[0]))
        assertNotNull(VbanDecoder.decode(packets[1]))
        assertArrayEquals(rawPcm.sliceArray(0 until 512), VbanDecoder.decode(packets[0])!!.second)
        assertArrayEquals(rawPcm.sliceArray(512 until 960), VbanDecoder.decode(packets[1])!!.second)
    }

    private fun packetize(
        rawPcm: ByteArray,
        samplesPerPacket: Int,
        numChannels: Int,
        bitsPerSample: Int = 16,
        counter: AtomicInteger = AtomicInteger(0),
    ): List<ByteArray> =
        VbanPacketizer.packetize(
            rawPcm = rawPcm,
            samplesPerPacket = samplesPerPacket,
            numChannels = numChannels,
            bitsPerSample = bitsPerSample,
            streamName = "AudioLAN",
            sampleRateHz = 48000,
            frameCounterRef = counter,
        )

    private fun assertHeader(
        packet: ByteArray,
        numSamples: Int,
        numChannels: Int,
        frameCounter: Int,
    ) {
        val decoded = VbanDecoder.decode(packet)
        assertNotNull(decoded)
        val header = decoded!!.first
        assertEquals(numSamples, header.numSamples)
        assertEquals(numChannels, header.numChannels)
        assertEquals(frameCounter, header.frameCounter)
    }
}
