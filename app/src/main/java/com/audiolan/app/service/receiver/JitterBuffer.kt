package com.audiolan.app.service.receiver

import com.audiolan.app.domain.vban.VbanHeader
import com.audiolan.app.domain.model.NetworkSelection
import java.util.TreeMap
import timber.log.Timber

data class ReceiverPacket(
    val header: VbanHeader,
    val pcm: ByteArray,
    val originNetwork: NetworkSelection? = null,
)

class JitterBuffer(
    private val capacityPackets: Int = DEFAULT_CAPACITY_PACKETS,
    private val prebufferPackets: Int = DEFAULT_PREBUFFER_PACKETS,
) {
    private val packetsByCounter = TreeMap<Int, ReceiverPacket>()
    private var expectedFrameCounter: Int? = null
    private var primed = false

    init {
        require(capacityPackets > 0) { "capacityPackets must be greater than 0" }
        require(prebufferPackets > 0) { "prebufferPackets must be greater than 0" }
        require(prebufferPackets <= capacityPackets) { "prebufferPackets must be <= capacityPackets" }
    }

    @Synchronized
    fun offer(packet: ReceiverPacket) {
        val frameCounter = packet.header.frameCounter
        val expected = expectedFrameCounter
        if (primed && expected != null && frameCounter < expected) {
            Timber.v("JitterBuffer late packet dropped frame=$frameCounter expected=$expected")
            return
        }

        packetsByCounter[frameCounter] = packet
        while (packetsByCounter.size > capacityPackets) {
            packetsByCounter.pollFirstEntry()
            Timber.v("JitterBuffer overflow - dropped oldest packet")
        }
    }

    @Synchronized
    fun poll(): ReceiverPacket? {
        if (packetsByCounter.isEmpty()) return null

        if (!primed) {
            if (packetsByCounter.size < prebufferPackets) return null
            expectedFrameCounter = packetsByCounter.firstKey()
            primed = true
        }

        val expected = expectedFrameCounter ?: packetsByCounter.firstKey()
        packetsByCounter.remove(expected)?.let { packet ->
            expectedFrameCounter = expected + 1
            return packet
        }

        dropLatePackets(expected)
        val nextFrameCounter = firstFrameCounterOrNull() ?: return null
        if (nextFrameCounter > expected) {
            Timber.v("JitterBuffer gap frame=$expected next=$nextFrameCounter")
            expectedFrameCounter = nextFrameCounter
        }
        return null
    }

    @Synchronized
    fun clear() {
        packetsByCounter.clear()
        expectedFrameCounter = null
        primed = false
    }

    @Synchronized
    fun size(): Int = packetsByCounter.size

    private fun dropLatePackets(expected: Int) {
        while (packetsByCounter.isNotEmpty() && packetsByCounter.firstKey() < expected) {
            packetsByCounter.pollFirstEntry()
        }
    }

    private fun firstFrameCounterOrNull(): Int? =
        if (packetsByCounter.isEmpty()) null else packetsByCounter.firstKey()

    private companion object {
        const val DEFAULT_CAPACITY_PACKETS = 64
        const val DEFAULT_PREBUFFER_PACKETS = 12
    }
}
