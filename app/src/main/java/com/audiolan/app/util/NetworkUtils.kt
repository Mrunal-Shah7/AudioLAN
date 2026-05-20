package com.audiolan.app.util

import java.io.File
import java.net.Inet4Address
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
}
