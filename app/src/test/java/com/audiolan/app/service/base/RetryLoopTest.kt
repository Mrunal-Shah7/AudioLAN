package com.audiolan.app.service.base

import com.audiolan.app.util.RetryUtils
import java.io.IOException
import java.net.SocketException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryLoopTest {
    @Test
    fun succeedsOnFirstAttemptWithoutRetry() = runTest {
        var calls = 0

        RetryUtils.withExponentialRetry {
            calls++
        }

        assertEquals(1, calls)
    }

    @Test
    fun succeedsAfterOneSocketRetry() = runTest {
        var calls = 0

        RetryUtils.withExponentialRetry {
            calls++
            if (calls == 1) throw SocketException("temporary")
        }

        assertEquals(2, calls)
    }

    @Test
    fun exhaustsSocketRetriesAndPropagatesException() = runTest {
        var calls = 0

        try {
            RetryUtils.withExponentialRetry(maxAttempts = 3) {
                calls++
                throw SocketException("down")
            }
            fail("Expected SocketException")
        } catch (e: SocketException) {
            assertEquals("down", e.message)
        }

        assertEquals(3, calls)
    }

    @Test
    fun cancellationExceptionIsRethrownImmediately() = runTest {
        var calls = 0

        try {
            RetryUtils.withExponentialRetry {
                calls++
                throw CancellationException("cancelled")
            }
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }

        assertEquals(1, calls)
    }

    @Test
    fun nonSocketExceptionPropagatesWithoutRetry() = runTest {
        var calls = 0

        try {
            RetryUtils.withExponentialRetry {
                calls++
                throw IOException("disk")
            }
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("disk", e.message)
        }

        assertEquals(1, calls)
    }
}
