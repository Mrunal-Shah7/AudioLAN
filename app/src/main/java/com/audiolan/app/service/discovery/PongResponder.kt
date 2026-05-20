package com.audiolan.app.service.discovery

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class PongResponder(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    fun start(): Job =
        scope.launch(Dispatchers.IO) {
            val deviceName = getDeviceName()
            val pongPayload = "$HERE_PREFIX$deviceName".toByteArray(Charsets.UTF_8)
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(PING_PONG_PORT).apply {
                    soTimeout = SOCKET_TIMEOUT_MS
                }
                val buffer = ByteArray(DISCOVER_BUFFER_SIZE)
                val deadline = System.currentTimeMillis() + RESPONDER_DURATION_MS

                while (isActive && System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (message == DISCOVER_PAYLOAD) {
                            val response = DatagramPacket(pongPayload, pongPayload.size, packet.address, packet.port)
                            socket.send(response)
                            Timber.d("PongResponder: replied to ${packet.address.hostAddress}")
                        }
                    } catch (_: SocketTimeoutException) {
                        // Normal timeout; check deadline and coroutine cancellation.
                    }
                }
            } catch (e: BindException) {
                Timber.w(e, "PongResponder: port $PING_PONG_PORT already in use - responder not started")
            } finally {
                socket?.close()
                Timber.d("PongResponder: stopped")
            }
        }

    private fun getDeviceName(): String =
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                adapter?.name?.takeIf { it.isNotBlank() } ?: Build.MODEL
            } else {
                Build.MODEL
            }
        } catch (e: Exception) {
            Timber.w(e, "PongResponder: could not get Bluetooth device name - using model: ${Build.MODEL}")
            Build.MODEL
        }

    private companion object {
        const val PING_PONG_PORT = 6_981
        const val SOCKET_TIMEOUT_MS = 1_000
        const val RESPONDER_DURATION_MS = 30_000L
        const val DISCOVER_BUFFER_SIZE = 64
        const val DISCOVER_PAYLOAD = "AUDIOLAN_DISCOVER"
        const val HERE_PREFIX = "AUDIOLAN_HERE|"
    }
}
