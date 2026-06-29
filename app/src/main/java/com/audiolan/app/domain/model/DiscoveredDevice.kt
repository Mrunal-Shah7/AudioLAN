package com.audiolan.app.domain.model

enum class DiscoverySource {
    VBAN_SNIFF,
    PING_PONG,
    USB_TETHER,
}

data class DiscoveredDevice(
    val ip: String,
    val deviceName: String?,
    val streamName: String?,
    val source: DiscoverySource,
    val originNetwork: NetworkSelection? = null,
)
