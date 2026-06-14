package com.audiolan.app.domain.model

data class Stream(
    val id: Long,
    val serviceType: ServiceType,
    val name: String,
    val host: String,
    val port: Int,
    val netQuality: NetQuality,
    val transportMode: TransportMode = TransportMode.WIFI,
    val lowLatency: Boolean = false,
    val sourceType: SourceType = SourceType.MIC,
    val broadcastMode: Boolean = false,
    val volume: Float,
    val isEnabled: Boolean,
)
