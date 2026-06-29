package com.audiolan.app.domain.model

data class NetworkSelection(
    val interfaceName: String = WIFI_INTERFACE_ANY,
    val ssid: String? = SSID_ANY,
) {
    val displayName: String
        get() = when {
            isAnyWifi -> "Wi-Fi"
            isAnyUsbTether -> "USB tethering"
            !ssid.isNullOrBlank() && ssid != SSID_ANY -> "$ssid ($interfaceName)"
            else -> interfaceName
        }

    val isAnyWifi: Boolean
        get() = interfaceName == WIFI_INTERFACE_ANY

    val isAnyUsbTether: Boolean
        get() = interfaceName == USB_INTERFACE_ANY

    val sortKey: String
        get() = "${networkSortBucket()}|${displayName.lowercase()}"

    private fun networkSortBucket(): Int =
        interfaceName.lowercase().let { lowerInterfaceName ->
            when {
                isAnyWifi ||
                    lowerInterfaceName.startsWith("wlan") ||
                    lowerInterfaceName.startsWith("wifi") -> 0
                isAnyUsbTether ||
                    lowerInterfaceName.startsWith("rndis") ||
                    lowerInterfaceName.startsWith("usb") -> 1
                lowerInterfaceName.startsWith("eth") || lowerInterfaceName.startsWith("en") -> 2
                else -> 3
            }
        }

    companion object {
        private const val ROUTE_HINT_PREFIX = "network:"
        private const val ROUTE_HINT_SEPARATOR = "|"

        const val WIFI_INTERFACE_ANY = "__WIFI__"
        const val USB_INTERFACE_ANY = "__USB_TETHER__"
        const val SSID_ANY = "__ANY_SSID__"

        fun anyWifi(): NetworkSelection =
            NetworkSelection(interfaceName = WIFI_INTERFACE_ANY, ssid = SSID_ANY)

        fun anyUsbTether(): NetworkSelection =
            NetworkSelection(interfaceName = USB_INTERFACE_ANY, ssid = null)

        fun fromRouteHint(value: String): NetworkSelection? {
            if (!value.startsWith(ROUTE_HINT_PREFIX)) return null
            val encodedSelection = value.removePrefix(ROUTE_HINT_PREFIX)
            val parts = encodedSelection.split(ROUTE_HINT_SEPARATOR, limit = 2)
            val interfaceName = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
            val ssid = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            return NetworkSelection(interfaceName = interfaceName, ssid = ssid)
        }
    }
}

fun NetworkSelection.toRouteHint(): String =
    "network:$interfaceName|${ssid.orEmpty()}"

data class NetworkSelectionOption(
    val selection: NetworkSelection,
    val label: String,
    val address: String? = null,
    val isAvailable: Boolean = true,
)
