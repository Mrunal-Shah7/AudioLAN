package com.audiolan.app.util

import java.net.SocketException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

object RetryUtils {
    suspend fun withExponentialRetry(
        maxAttempts: Int = 3,
        onSocketRetry: suspend (
            attempt: Int,
            delayMs: Long,
            exception: SocketException,
        ) -> Unit = { _, _, _ -> },
        block: suspend () -> Unit,
    ) {
        var attempts = 0
        while (attempts < maxAttempts) {
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketException) {
                attempts++
                if (attempts >= maxAttempts) throw e
                val delayMs = 1_000L * (1 shl attempts)
                onSocketRetry(attempts, delayMs, e)
                delay(delayMs)
            }
        }
    }
}
