package com.audiolan.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.audiolan.app.domain.model.NetworkSelection
import com.audiolan.app.domain.model.NetworkSelectionOption
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {
    data class ResolvedNetworkSelection(
        val selection: NetworkSelection,
        val label: String,
        val interfaceName: String,
        val ssid: String?,
        val address: Inet4Address,
        val prefixLength: Int,
        val broadcastAddress: InetAddress?,
        val network: Network?,
        val bindingStrategy: NetworkBindingStrategy,
    )

    enum class NetworkBindingStrategy {
        NETWORK,
        INTERFACE_ADDRESS,
    }

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

    fun getNetworkSelectionOptions(context: Context): List<NetworkSelectionOption> =
        getResolvedNetworkSelections(context).map { resolved ->
            NetworkSelectionOption(
                selection = resolved.selection,
                label = resolved.label,
                address = resolved.address.hostAddress,
                isAvailable = true,
            )
        }

    fun getResolvedNetworkSelections(context: Context): List<ResolvedNetworkSelection> {
        val networksByInterface = getNetworksByInterface(context)
        val connectedSsid = getConnectedWifiSsid(context)
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()

        return interfaces
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface ->
                iface.interfaceAddresses
                    .mapNotNull { interfaceAddress ->
                        val address = interfaceAddress.address as? Inet4Address ?: return@mapNotNull null
                        if (address.isLoopbackAddress) return@mapNotNull null
                        val interfaceName = iface.name
                        val ssid = if (isLikelyWifiInterface(interfaceName)) connectedSsid else null
                        val network = networksByInterface[interfaceName]
                        val selection = NetworkSelection(
                            interfaceName = interfaceName,
                            ssid = ssid,
                        )
                        ResolvedNetworkSelection(
                            selection = selection,
                            label = buildNetworkLabel(interfaceName, ssid, address.hostAddress.orEmpty()),
                            interfaceName = interfaceName,
                            ssid = ssid,
                            address = address,
                            prefixLength = interfaceAddress.networkPrefixLength.toInt(),
                            broadcastAddress = interfaceAddress.broadcast,
                            network = network,
                            bindingStrategy = if (network != null) {
                                NetworkBindingStrategy.NETWORK
                            } else {
                                NetworkBindingStrategy.INTERFACE_ADDRESS
                            },
                        )
                    }
            }
            .distinctBy { it.interfaceName to it.address.hostAddress }
            .sortedWith(
                compareBy<ResolvedNetworkSelection> { networkSortBucket(it.interfaceName) }
                    .thenBy { it.label.lowercase() },
            )
    }

    fun resolveNetworkSelection(
        context: Context,
        selection: NetworkSelection,
    ): ResolvedNetworkSelection? {
        val candidates = getResolvedNetworkSelections(context)
        return when {
            selection.isAnyWifi -> candidates.firstOrNull { isLikelyWifiInterface(it.interfaceName) }
            selection.isAnyUsbTether -> candidates.firstOrNull { isLikelyUsbTetherInterface(it.interfaceName) }
                ?: candidates.firstOrNull { isLikelyEthernetInterface(it.interfaceName) }
            else -> candidates.firstOrNull { candidate ->
                candidate.interfaceName == selection.interfaceName &&
                    savedSsidMatches(selection.ssid, candidate.ssid)
            }
        }
    }

    fun isNetworkSelectionAvailable(context: Context, selection: NetworkSelection): Boolean =
        resolveNetworkSelection(context, selection) != null

    fun getBroadcastAddressForSelection(
        context: Context,
        selection: NetworkSelection,
    ): InetAddress? =
        resolveNetworkSelection(context, selection)?.broadcastAddress

    fun resolveArrivalNetwork(
        context: Context,
        sourceAddress: InetAddress,
    ): ResolvedNetworkSelection? {
        val sourceIpv4 = sourceAddress as? Inet4Address ?: return null
        return getResolvedNetworkSelections(context)
            .firstOrNull { resolved ->
                isSameIpv4Subnet(
                    first = sourceIpv4,
                    second = resolved.address,
                    prefixLength = resolved.prefixLength,
                )
            }
    }

    fun isOwnActiveInterfaceAddress(sourceAddress: InetAddress): Boolean {
        val sourceIpv4 = sourceAddress as? Inet4Address ?: return false
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return false
        return interfaces
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .any { it == sourceIpv4 }
    }

    fun isOwnActiveInterfaceAddress(sourceAddress: String): Boolean =
        runCatching {
            isOwnActiveInterfaceAddress(InetAddress.getByName(sourceAddress))
        }.getOrDefault(false)

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
            lower.startsWith("usb")
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
            lower.startsWith("wifi")
    }

    fun isLikelyEthernetInterface(interfaceName: String): Boolean {
        val lower = interfaceName.lowercase()
        return lower.startsWith("eth") ||
            lower.startsWith("en")
    }

    private fun getNetworksByInterface(context: Context): Map<String, Network> {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return emptyMap()
        return connectivityManager.allNetworks.mapNotNull { network ->
            val interfaceName = connectivityManager.getLinkProperties(network)?.interfaceName
                ?: return@mapNotNull null
            interfaceName to network
        }.toMap()
    }

    private fun getConnectedWifiSsid(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return null
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo?.ssid
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeUnless { it.isBlank() || it == "<unknown ssid>" || it.startsWith("0x") }
    }

    private fun buildNetworkLabel(interfaceName: String, ssid: String?, address: String): String =
        when {
            !ssid.isNullOrBlank() -> "$ssid ($interfaceName, $address)"
            isLikelyUsbTetherInterface(interfaceName) -> "USB tethering ($interfaceName, $address)"
            isLikelyEthernetInterface(interfaceName) -> "Ethernet ($interfaceName, $address)"
            else -> "$interfaceName ($address)"
        }

    private fun savedSsidMatches(savedSsid: String?, liveSsid: String?): Boolean =
        savedSsid.isNullOrBlank() ||
            savedSsid == NetworkSelection.SSID_ANY ||
            savedSsid == liveSsid

    private fun isSameIpv4Subnet(
        first: Inet4Address,
        second: Inet4Address,
        prefixLength: Int,
    ): Boolean {
        if (prefixLength !in 0..32) return false
        val mask = if (prefixLength == 0) {
            0
        } else {
            -1 shl (IPV4_BITS - prefixLength)
        }
        return (first.toIpv4Int() and mask) == (second.toIpv4Int() and mask)
    }

    private fun Inet4Address.toIpv4Int(): Int {
        val bytes = address
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    private fun networkSortBucket(interfaceName: String): Int =
        when {
            isLikelyWifiInterface(interfaceName) -> 0
            isLikelyUsbTetherInterface(interfaceName) -> 1
            isLikelyEthernetInterface(interfaceName) -> 2
            else -> 3
        }

    private fun ConnectivityManager.isWifiNetwork(network: Network): Boolean =
        getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

    private const val IPV4_BITS = 32
}
