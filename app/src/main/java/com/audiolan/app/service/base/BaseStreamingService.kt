package com.audiolan.app.service.base

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.os.PowerManager
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.domain.model.StreamError
import com.audiolan.app.domain.model.TransportMode
import com.audiolan.app.service.ServiceEntryPoint
import com.audiolan.app.util.NetworkUtils
import com.audiolan.app.util.RetryUtils
import dagger.hilt.android.EntryPointAccessors
import java.net.DatagramSocket
import java.net.SocketException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class BaseStreamingService : Service() {
    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    protected val runningJobs = mutableMapOf<Long, Job>()
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile
    protected var currentWifiNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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
                    runningJobs[id] = launchStreamJob(stream)
                    changed = true
                }

                if (changed) {
                    updateStreamCountNotification()
                }
            }
        }
    }

    protected fun registerWifiNetworkCallback() {
        if (networkCallback != null) return

        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                currentWifiNetwork = network
                Timber.d("${this@BaseStreamingService::class.java.simpleName}: Wi-Fi network available, rebinding sockets")
                onWifiNetworkAvailable(network)
            }

            override fun onLost(network: Network) {
                if (currentWifiNetwork == network) {
                    currentWifiNetwork = null
                    Timber.w("${this@BaseStreamingService::class.java.simpleName}: Wi-Fi connection lost")
                    onWifiNetworkLost()
                }
            }
        }

        runCatching {
            connectivityManager.requestNetwork(request, callback)
            networkCallback = callback
        }.onFailure { throwable ->
            Timber.w(throwable, "Unable to request Wi-Fi network callback")
        }

        NetworkUtils.getWifiNetwork(applicationContext)?.let { currentWifiNetwork = it }
    }

    protected open fun onWifiNetworkAvailable(network: Network) {
        rebindSocketsToNetwork(network)
    }

    protected open fun onWifiNetworkLost() {
        serviceScope.launch {
            runningJobs.values.forEach { it.cancel() }
            runningJobs.clear()
            updateServiceState(ServiceState.Error("Wi-Fi connection lost"))
            updateStreamCountNotification()
            stopSelf()
        }
    }

    protected fun rebindSocketsToNetwork(network: Network) {
        serviceScope.launch {
            val activeIds = runningJobs.keys.toSet()
            if (activeIds.isEmpty()) return@launch

            val streams = streamRepository.getStreamsByType(serviceType()).first()
                .filter { it.isEnabled && it.id in activeIds }

            runningJobs.values.forEach { it.cancel() }
            runningJobs.clear()

            streams.forEach { stream ->
                Timber.d(
                    "${this@BaseStreamingService::class.java.simpleName}: restarting stream ${stream.name} on Wi-Fi network $network",
                )
                runningJobs[stream.id] = launchStreamJob(stream)
            }
            updateStreamCountNotification()
        }
    }

    protected fun bindSocketToWifiNetwork(socket: DatagramSocket, stream: Stream? = null) {
        val network = currentWifiNetwork ?: NetworkUtils.getWifiNetwork(applicationContext)
        if (network == null) {
            Timber.w("No Wi-Fi network available for socket bind${stream?.let { " (${it.name})" } ?: ""}")
            return
        }

        runCatching {
            network.bindSocket(socket)
            currentWifiNetwork = network
            Timber.d("Bound UDP socket to Wi-Fi network $network${stream?.let { " for ${it.name}" } ?: ""}")
        }.onFailure { throwable ->
            Timber.w(throwable, "Failed to bind UDP socket to Wi-Fi network")
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

    private fun launchStreamJob(stream: Stream): Job =
        serviceScope.launch { retryLoop(stream) }

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
        updateServiceState(ServiceState.Error(message))
    }

    protected fun updateServiceState(state: ServiceState) {
        when (serviceType()) {
            ServiceType.TRANSMITTER -> serviceManager.updateTransmitterState(state)
            ServiceType.RECEIVER -> serviceManager.updateReceiverState(state)
        }
    }

    private fun unregisterWifiNetworkCallback() {
        val callback = networkCallback ?: return
        runCatching {
            getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(callback)
        }.onFailure { throwable ->
            Timber.w(throwable, "Failed to unregister Wi-Fi network callback")
        }
        networkCallback = null
    }

    override fun onDestroy() {
        unregisterWifiNetworkCallback()
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
