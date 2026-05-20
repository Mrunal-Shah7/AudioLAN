package com.audiolan.app.domain.vban

import com.audiolan.app.domain.model.NetQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VbanNetQualityTest {
    @Test
    fun optimalMono16BitReturns256() {
        assertEquals(256, samples(NetQuality.OPTIMAL, 1))
    }

    @Test
    fun fastMono16BitReturns256() {
        assertEquals(256, samples(NetQuality.FAST, 1))
    }

    @Test
    fun mediumMono16BitReturns256() {
        assertEquals(256, samples(NetQuality.MEDIUM, 1))
    }

    @Test
    fun slowMono16BitReturns256() {
        assertEquals(256, samples(NetQuality.SLOW, 1))
    }

    @Test
    fun verySlowMono16BitReturns256() {
        assertEquals(256, samples(NetQuality.VERY_SLOW, 1))
    }

    @Test
    fun optimalStereo16BitReturns128() {
        assertEquals(128, samples(NetQuality.OPTIMAL, 2))
    }

    @Test
    fun fastStereo16BitReturns256() {
        assertEquals(256, samples(NetQuality.FAST, 2))
    }

    @Test
    fun mediumStereo16BitReturns256() {
        assertEquals(256, samples(NetQuality.MEDIUM, 2))
    }

    @Test
    fun slowStereo16BitReturns256() {
        assertEquals(256, samples(NetQuality.SLOW, 2))
    }

    @Test
    fun verySlowStereo16BitReturns256() {
        assertEquals(256, samples(NetQuality.VERY_SLOW, 2))
    }

    @Test
    fun resultsAreAlwaysPowerOfTwo() {
        forEach16BitCase { result ->
            assertTrue(result > 0 && result and (result - 1) == 0)
        }
    }

    @Test
    fun resultsAreAlwaysAtMost256() {
        forEach16BitCase { result ->
            assertTrue(result <= 256)
        }
    }

    @Test
    fun resultsAreAlwaysAtLeast1() {
        forEach16BitCase { result ->
            assertTrue(result >= 1)
        }
    }

    private fun samples(netQuality: NetQuality, channels: Int): Int =
        VbanNetQuality.calculateSamplesPerPacket(netQuality, channels, 16)

    private fun forEach16BitCase(assertion: (Int) -> Unit) {
        for (quality in NetQuality.entries) {
            for (channels in listOf(1, 2)) {
                assertion(samples(quality, channels))
            }
        }
    }
}
