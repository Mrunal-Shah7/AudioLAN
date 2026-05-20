package com.audiolan.app.domain.vban

import java.util.concurrent.atomic.AtomicInteger

object VbanPacketizer {
    fun packetize(
        rawPcm: ByteArray,
        samplesPerPacket: Int,
        numChannels: Int,
        bitsPerSample: Int,
        streamName: String,
        sampleRateHz: Int,
        frameCounterRef: AtomicInteger,
    ): List<ByteArray> {
        val bytesPerFrame = numChannels * (bitsPerSample / 8)
        val bytesPerPacket = samplesPerPacket * bytesPerFrame
        val packets = mutableListOf<ByteArray>()
        var offset = 0

        while (offset < rawPcm.size) {
            val remaining = rawPcm.size - offset
            val chunkBytes = minOf(remaining, bytesPerPacket)
            val actualSamples = chunkBytes / bytesPerFrame
            if (actualSamples == 0) {
                break
            }

            val pcmChunkSize = actualSamples * bytesPerFrame
            val pcmChunk = rawPcm.sliceArray(offset until offset + pcmChunkSize)
            val counter = frameCounterRef.getAndIncrement()
            packets += VbanEncoder.buildPacket(
                streamName = streamName,
                pcmData = pcmChunk,
                numSamples = actualSamples,
                numChannels = numChannels,
                sampleRateHz = sampleRateHz,
                frameCounter = counter,
            )
            offset += pcmChunkSize
        }

        return packets
    }
}
