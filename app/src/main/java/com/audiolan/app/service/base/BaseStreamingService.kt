package com.audiolan.app.service.base

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.domain.model.StreamError
import com.audiolan.app.domain.model.TransportMode
import com.audiolan.app.service.ServiceEntryPoint
import com.audiolan.app.util.RetryUtils
import dagger.hilt.android.EntryPointAccessors
import java.net.SocketException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class BaseStreamingService : Service() {
    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    protected val runningJobs = mutableMapOf<Long, Job>()
    private var wakeLock: PowerManager.WakeLock? = null

    protected val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, ServiceEntryPoint::class.java)
    }
    protected val serviceManager by lazy { entryPoint.serviceManager() }
    protected val streamRepository by lazy { entryPoint.streamRepository() }
    protected val settingsRepository by lazy { entryPoint.settingsRepository() }

    override fun onBind(intent: Intent?): IBinder? = null

    protected fun startStreamManagement() {
        serviceScope.launch {
            streamRepository.getStreamsByType(serviceType()).collect { streams ->
                val enabledStreams = streams.filter { it.isEnabled }
                val newIds = enabledStreams.map { it.id }.toSet()
                val currentIds = runningJobs.keys.toSet()
                var changed = false

                (currentIds - newIds).forEach { id ->
                    Timber.d("Stopping stream coroutine id=$id")
                    runningJobs.remove(id)?.cancel()
                    changed = true
                }

                (newIds - currentIds).forEach { id ->
                    val stream = enabledStreams.first { it.id == id }
                    Timber.d("Starting stream coroutine id=$id name=${stream.name}")
                    runningJobs[id] = serviceScope.launch { retryLoop(stream) }
                    changed = true
                }

                if (changed) {
                    updateStreamCountNotification()
                }
            }
        }
    }

    protected fun updateStreamCountNotification() {
        onUpdateNotification(runningJobs.size)
    }

    protected fun acquireWakeLock(tag: String) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    protected fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private suspend fun retryLoop(stream: Stream) {
        val maxAttempts = 3
        try {
            RetryUtils.withExponentialRetry(
                maxAttempts = maxAttempts,
                onSocketRetry = { attempts, delayMs, exception ->
                    Timber.w(
                        "Stream ${stream.name} network error (attempt $attempts/$maxAttempts), " +
                            "retrying in ${delayMs}ms: ${exception.message}",
                    )
                },
            ) {
                streamLoop(stream)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketException) {
            Timber.e("Stream ${stream.name} failed after $maxAttempts attempts")
            if (stream.transportMode == TransportMode.USB_TETHER) {
                postStreamError(stream, "No USB tethered device connected")
            } else {
                postStreamError(stream, StreamError.NetworkUnreachable(stream.host, stream.port))
            }
        } catch (e: Exception) {
            Timber.e(e, "Stream ${stream.name} unrecoverable error")
        }
    }

    protected open fun postStreamError(stream: Stream, error: StreamError) {
        postStreamError(stream, error.toUserMessage())
    }

    protected open fun postStreamError(stream: Stream, message: String) {
        Timber.e("Stream error: ${stream.name} - $message")
        when (serviceType()) {
            ServiceType.MIC -> serviceManager.updateMicState(ServiceState.Error(message))
            ServiceType.CAST -> serviceManager.updateCastState(ServiceState.Error(message))
            ServiceType.RECEIVER -> serviceManager.updateReceiverState(ServiceState.Error(message))
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        onServiceDestroyed()
        super.onDestroy()
    }

    protected abstract suspend fun streamLoop(stream: Stream)

    protected abstract fun onUpdateNotification(activeStreamCount: Int)

    abstract fun onServiceDestroyed()

    abstract fun serviceType(): ServiceType

    private companion object {
        const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
