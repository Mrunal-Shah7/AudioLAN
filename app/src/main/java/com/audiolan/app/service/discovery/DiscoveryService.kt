package com.audiolan.app.service.discovery

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import com.audiolan.app.domain.model.DiscoveredDevice
import com.audiolan.app.domain.model.DiscoverySource
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.vban.VbanDecoder
import com.audiolan.app.service.ServiceEntryPoint
import com.audiolan.app.util.NetworkUtils
import dagger.hilt.android.EntryPointAccessors
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class DiscoveryService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, ServiceEntryPoint::class.java)
    }
    private val serviceManager by lazy { entryPoint.serviceManager() }
    private val discoveredDevicesRepository by lazy { entryPoint.discoveredDevicesRepository() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            try {
                runScan()
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runScan() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
        val wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
        val snifferSocket = createSnifferSocket()
        val pingPongSocket = createPingPongSocket()

        try {
            emitUsbTetherCandidates()
            pingPongSocket?.let(::sendBroadcastPings)

            withTimeout(SCAN_WINDOW_MS) {
                val snifferJob = launch {
                    snifferSocket?.let { runPassiveSniffer(it) }
                }
                val listenerJob = launch {
                    pingPongSocket?.let { runPingPongListener(it) }
                }
                joinAll(snifferJob, listenerJob)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.d("Discovery: 5-second scan window complete")
        } finally {
            snifferSocket?.close()
            pingPongSocket?.close()
            if (multicastLock.isHeld) {
                multicastLock.release()
            }
            if (wifiLock.isHeld) {
                wifiLock.release()
            }
        }
    }

    private fun createSnifferSocket(): DatagramSocket? {
        if (serviceManager.receiverState.value is ServiceState.Running) {
            Timber.d("Discovery: ReceiverService is running; using receiver passive sniff results")
            return null
        }
        return try {
            DatagramSocket(VBAN_PORT).apply { soTimeout = SOCKET_TIMEOUT_MS }
        } catch (e: BindException) {
            Timber.w(e, "Discovery: could not bind port $VBAN_PORT - passive sniffing disabled")
            null
        }
    }

    private fun createPingPongSocket(): DatagramSocket? =
        try {
            DatagramSocket(PING_PONG_PORT).apply {
                soTimeout = SOCKET_TIMEOUT_MS
                broadcast = true
            }
        } catch (e: BindException) {
            Timber.w(e, "Discovery: could not bind port $PING_PONG_PORT - ping/pong disabled")
            null
        }

    private fun emitUsbTetherCandidates() {
        NetworkUtils.getUsbTetherPeerCandidates().forEach { ip ->
            discoveredDevicesRepository.emit(
                DiscoveredDevice(
                    ip = ip,
                    deviceName = "USB tether peer",
                    streamName = null,
                    source = DiscoverySource.USB_TETHER,
                ),
            )
            Timber.d("Discovery: USB tether peer candidate @ $ip")
        }
    }

    private fun sendBroadcastPings(socket: DatagramSocket) {
        val ping = DISCOVER_PAYLOAD.toByteArray(Charsets.UTF_8)
        val targets = buildBroadcastTargets()
        targets.forEach { broadcast ->
            runCatching {
                socket.send(DatagramPacket(ping, ping.size, broadcast, PING_PONG_PORT))
                Timber.d("Discovery: sent broadcast ping to ${broadcast.hostAddress}")
            }.onFailure { error ->
                Timber.w(error, "Discovery: failed broadcast ping to ${broadcast.hostAddress}")
            }
        }
    }

    private fun buildBroadcastTargets(): List<InetAddress> {
        val interfaceBroadcasts = NetworkUtils.getActiveIpv4Interfaces()
            .map { it.first }
            .distinct()
            .flatMap(::broadcastsForInterface)
        return (listOf(InetAddress.getByName(BROADCAST_ADDRESS)) + interfaceBroadcasts).distinct()
    }

    private fun broadcastsForInterface(interfaceName: String): List<InetAddress> {
        val networkInterface = java.net.NetworkInterface.getByName(interfaceName) ?: return emptyList()
        return networkInterface.interfaceAddresses
            .mapNotNull(InterfaceAddress::getBroadcast)
    }

    private suspend fun runPassiveSniffer(socket: DatagramSocket) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(MAX_UDP_DATAGRAM_SIZE)
        while (isActive) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val data = packet.data.copyOf(packet.length)
                val result = VbanDecoder.decode(data) ?: continue
                val ip = packet.address.hostAddress ?: continue
                discoveredDevicesRepository.emit(
                    DiscoveredDevice(
                        ip = ip,
                        deviceName = null,
                        streamName = result.first.streamName,
                        source = DiscoverySource.VBAN_SNIFF,
                    ),
                )
                Timber.d("Discovery: VBAN sniff - ${result.first.streamName} @ $ip")
            } catch (_: SocketTimeoutException) {
                // Normal timeout; loop again so coroutine cancellation can be observed.
            } catch (e: SocketException) {
                if (!isActive) break
                Timber.w(e, "Discovery: passive sniffer socket stopped")
                break
            }
        }
    }

    private suspend fun runPingPongListener(socket: DatagramSocket) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(PING_PONG_BUFFER_SIZE)
        while (isActive) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (message.startsWith(HERE_PREFIX)) {
                    val deviceName = message.removePrefix(HERE_PREFIX).trim()
                    val ip = packet.address.hostAddress ?: continue
                    discoveredDevicesRepository.emit(
                        DiscoveredDevice(
                            ip = ip,
                            deviceName = deviceName,
                            streamName = null,
                            source = DiscoverySource.PING_PONG,
                        ),
                    )
                    Timber.d("Discovery: pong from $deviceName @ $ip")
                }
            } catch (_: SocketTimeoutException) {
                // Normal timeout; loop again so coroutine cancellation can be observed.
            } catch (e: SocketException) {
                if (!isActive) break
                Timber.w(e, "Discovery: ping/pong listener socket stopped")
                break
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val VBAN_PORT = 6_980
        const val PING_PONG_PORT = 6_981
        const val MAX_UDP_DATAGRAM_SIZE = 65_535
        const val PING_PONG_BUFFER_SIZE = 256
        const val SOCKET_TIMEOUT_MS = 100
        const val SCAN_WINDOW_MS = 5_000L
        const val MULTICAST_LOCK_TAG = "AudioLAN.multicast"
        const val WIFI_LOCK_TAG = "AudioLAN.wifi"
        const val DISCOVER_PAYLOAD = "AUDIOLAN_DISCOVER"
        const val HERE_PREFIX = "AUDIOLAN_HERE|"
        const val BROADCAST_ADDRESS = "255.255.255.255"
    }
}
