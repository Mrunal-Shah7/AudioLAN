package com.audiolan.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {
    fun getActiveIpv4Interfaces(): List<Pair<String, String>> {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        return interfaces
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface ->
                iface.inetAddresses
                    .toList()
                    .filterIsInstance<Inet4Address>()
                    .filterNot { it.isLoopbackAddress }
                    .mapNotNull { address ->
                        val hostAddress = address.hostAddress ?: return@mapNotNull null
                        iface.name to hostAddress
                    }
            }
    }

    fun getUsbTetherIpv4Interfaces(): List<Pair<String, String>> =
        getActiveIpv4Interfaces().filter { (interfaceName, ip) ->
            isLikelyUsbTetherInterface(interfaceName) && ip.isNotBlank()
        }

    fun hasUsbTetherInterface(): Boolean =
        getUsbTetherIpv4Interfaces().isNotEmpty()

    fun getUsbTetherPeerCandidates(): List<String> {
        val usbInterfaces = getUsbTetherIpv4Interfaces().map { it.first }.toSet()
        if (usbInterfaces.isEmpty()) return emptyList()

        val arpFile = File("/proc/net/arp")
        if (!arpFile.canRead()) return emptyList()

        return arpFile.readLines()
            .drop(1)
            .mapNotNull { line ->
                val columns = line.trim().split(Regex("\\s+"))
                if (columns.size < 6) return@mapNotNull null
                val ip = columns[0]
                val mac = columns[3]
                val device = columns[5]
                if (device in usbInterfaces && mac != "00:00:00:00:00:00") ip else null
            }
            .distinct()
    }

    fun isLikelyUsbTetherInterface(interfaceName: String): Boolean {
        val lower = interfaceName.lowercase()
        return lower.startsWith("rndis") ||
            lower.startsWith("usb") ||
            lower.startsWith("eth")
    }

    fun getWifiBroadcastAddress(): InetAddress? {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
        return interfaces
            .asSequence()
            .filter { it.isUp && !it.isLoopback && isLikelyWifiInterface(it.name) }
            .flatMap { it.interfaceAddresses.asSequence() }
            .mapNotNull { it.broadcast }
            .firstOrNull()
    }

    fun getWifiNetwork(context: Context): Network? {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null && connectivityManager.isWifiNetwork(activeNetwork)) {
            return activeNetwork
        }
        return connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.isWifiNetwork(network)
        }
    }

    fun isLikelyWifiInterface(interfaceName: String): Boolean {
        val lower = interfaceName.lowercase()
        return lower.startsWith("wlan") ||
            lower.startsWith("wifi") ||
            lower.startsWith("eth")
    }

    private fun ConnectivityManager.isWifiNetwork(network: Network): Boolean =
        getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
}
