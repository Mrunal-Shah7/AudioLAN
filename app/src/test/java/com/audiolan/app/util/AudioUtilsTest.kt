package com.audiolan.app.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioUtilsTest {
    @Test
    fun identityVolumeKeepsBytesBitIdentical() {
        val pcm = byteArrayOf(0xE8.toByte(), 0x03.toByte())
        val original = pcm.copyOf()

        AudioUtils.applyVolume(pcm, 1.0f)

        assertArrayEquals(original, pcm)
    }

    @Test
    fun doubleVolumeScalesSampleToTwoThousand() {
        val pcm = byteArrayOf(0xE8.toByte(), 0x03.toByte())

        AudioUtils.applyVolume(pcm, 2.0f)

        assertArrayEquals(byteArrayOf(0xD0.toByte(), 0x07.toByte()), pcm)
    }

    @Test
    fun halfVolumeScalesSampleToFiveHundred() {
        val pcm = byteArrayOf(0xE8.toByte(), 0x03.toByte())

        AudioUtils.applyVolume(pcm, 0.5f)

        assertArrayEquals(byteArrayOf(0xF4.toByte(), 0x01.toByte()), pcm)
    }

    @Test
    fun zeroVolumeSilencesSamples() {
        val pcm = byteArrayOf(0xE8.toByte(), 0x03.toByte())

        AudioUtils.applyVolume(pcm, 0.0f)

        assertArrayEquals(byteArrayOf(0x00, 0x00), pcm)
    }

    @Test
    fun positiveOverflowClampsToShortMax() {
        val pcm = pcmOf(20_000)

        AudioUtils.applyVolume(pcm, 2.0f)

        assertEquals(Short.MAX_VALUE.toInt(), sampleAt(pcm, 0))
    }

    @Test
    fun negativeOverflowClampsToShortMin() {
        val pcm = pcmOf(-20_000)

        AudioUtils.applyVolume(pcm, 2.0f)

        assertEquals(Short.MIN_VALUE.toInt(), sampleAt(pcm, 0))
    }

    @Test
    fun maxPositiveSampleIsUnchangedAtIdentityVolume() {
        val pcm = pcmOf(Short.MAX_VALUE.toInt())
        val original = pcm.copyOf()

        AudioUtils.applyVolume(pcm, 1.0f)

        assertArrayEquals(original, pcm)
    }

    @Test
    fun minNegativeSampleIsUnchangedAtIdentityVolume() {
        val pcm = pcmOf(Short.MIN_VALUE.toInt())
        val original = pcm.copyOf()

        AudioUtils.applyVolume(pcm, 1.0f)

        assertArrayEquals(original, pcm)
    }

    @Test
    fun stereoInterleavedSamplesAreScaledIndependently() {
        val pcm = pcmOf(1000, 2000)

        AudioUtils.applyVolume(pcm, 0.5f)

        assertEquals(500, sampleAt(pcm, 0))
        assertEquals(1000, sampleAt(pcm, 1))
    }

    @Test
    fun oddTrailingByteIsIgnored() {
        val pcm = byteArrayOf(0xE8.toByte(), 0x03, 0x7F)

        AudioUtils.applyVolume(pcm, 0.5f)

        assertEquals(500, sampleAt(pcm, 0))
        assertEquals(0x7F, pcm[2].toInt() and 0xFF)
    }

    @Test
    fun emptyArrayCompletesWithoutException() {
        val pcm = ByteArray(0)

        AudioUtils.applyVolume(pcm, 1.5f)

        assertEquals(0, pcm.size)
    }

    private fun pcmOf(vararg samples: Int): ByteArray {
        val pcm = ByteArray(samples.size * BYTES_PER_SAMPLE_16BIT)
        samples.forEachIndexed { index, sample ->
            val offset = index * BYTES_PER_SAMPLE_16BIT
            pcm[offset] = (sample and 0xFF).toByte()
            pcm[offset + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun sampleAt(pcm: ByteArray, index: Int): Int {
        val offset = index * BYTES_PER_SAMPLE_16BIT
        return ((pcm[offset + 1].toInt() shl 8) or (pcm[offset].toInt() and 0xFF))
            .toShort()
            .toInt()
    }
}
