package com.audiolan.app.service.receiver

import com.audiolan.app.domain.vban.VbanHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JitterBufferTest {
    @Test
    fun pollReturnsNullWhenEmpty() {
        val buffer = JitterBuffer()

        assertNull(buffer.poll())
    }

    @Test
    fun offerDropsOldestPacketOnOverflow() {
        val buffer = JitterBuffer(capacityPackets = 2, prebufferPackets = 2)

        buffer.offer(packet(frameCounter = 1))
        buffer.offer(packet(frameCounter = 2))
        buffer.offer(packet(frameCounter = 3))

        assertEquals(2, buffer.size())
        assertEquals(2, buffer.poll()?.header?.frameCounter)
        assertEquals(3, buffer.poll()?.header?.frameCounter)
        assertNull(buffer.poll())
    }

    @Test
    fun pollWaitsForPrebufferBeforeReturningPackets() {
        val buffer = JitterBuffer(capacityPackets = 4, prebufferPackets = 2)

        buffer.offer(packet(frameCounter = 1))

        assertNull(buffer.poll())

        buffer.offer(packet(frameCounter = 2))

        assertEquals(1, buffer.poll()?.header?.frameCounter)
        assertEquals(2, buffer.poll()?.header?.frameCounter)
    }

    @Test
    fun pollReturnsPacketsInFrameCounterOrder() {
        val buffer = JitterBuffer(capacityPackets = 4, prebufferPackets = 3)

        buffer.offer(packet(frameCounter = 3))
        buffer.offer(packet(frameCounter = 1))
        buffer.offer(packet(frameCounter = 2))

        assertEquals(1, buffer.poll()?.header?.frameCounter)
        assertEquals(2, buffer.poll()?.header?.frameCounter)
        assertEquals(3, buffer.poll()?.header?.frameCounter)
    }

    @Test
    fun missingFrameDoesNotBlockLaterPacketsForever() {
        val buffer = JitterBuffer(capacityPackets = 4, prebufferPackets = 2)

        buffer.offer(packet(frameCounter = 1))
        buffer.offer(packet(frameCounter = 3))

        assertEquals(1, buffer.poll()?.header?.frameCounter)
        assertNull(buffer.poll())
        assertEquals(3, buffer.poll()?.header?.frameCounter)
    }

    @Test
    fun clearEmptiesBuffer() {
        val buffer = JitterBuffer()

        buffer.offer(packet(frameCounter = 1))
        buffer.clear()

        assertEquals(0, buffer.size())
        assertNull(buffer.poll())
    }

    private fun packet(frameCounter: Int): ReceiverPacket =
        ReceiverPacket(
            header = VbanHeader(
                sampleRateHz = 48_000,
                numSamples = 128,
                numChannels = 2,
                bitsPerSample = 16,
                streamName = "stream",
                frameCounter = frameCounter,
            ),
            pcm = byteArrayOf(frameCounter.toByte()),
        )
}
