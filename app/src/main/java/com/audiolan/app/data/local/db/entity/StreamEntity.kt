package com.audiolan.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.audiolan.app.domain.model.NetQuality
import com.audiolan.app.domain.model.NetworkSelection
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.SourceType
import com.audiolan.app.domain.model.Stream

@Entity(
    tableName = "streams",
    indices = [Index(value = ["service_type"])],
)
data class StreamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "service_type") val serviceType: String,
    val name: String,
    val host: String,
    val port: Int = 6980,
    @ColumnInfo(name = "net_quality") val netQuality: String,
    @ColumnInfo(name = "network_interface") val networkInterface: String = NetworkSelection.WIFI_INTERFACE_ANY,
    @ColumnInfo(name = "network_ssid") val networkSsid: String? = NetworkSelection.SSID_ANY,
    @ColumnInfo(name = "low_latency") val lowLatency: Boolean = false,
    @ColumnInfo(name = "source_type", defaultValue = "'MIC'") val sourceType: String = SourceType.MIC.name,
    @ColumnInfo(name = "broadcast_mode", defaultValue = "0") val broadcastMode: Boolean = false,
    val volume: Float = 1.0f,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true,
)

fun StreamEntity.toDomain(): Stream =
    Stream(
        id = id,
        serviceType = runCatching { ServiceType.valueOf(serviceType) }.getOrDefault(ServiceType.TRANSMITTER),
        name = name,
        host = host,
        port = port,
        netQuality = NetQuality.valueOf(netQuality),
        networkSelection = NetworkSelection(
            interfaceName = networkInterface,
            ssid = networkSsid,
        ),
        lowLatency = lowLatency,
        sourceType = runCatching { SourceType.valueOf(sourceType) }.getOrDefault(SourceType.MIC),
        broadcastMode = broadcastMode,
        volume = volume,
        isEnabled = isEnabled,
    )

fun Stream.toEntity(): StreamEntity =
    StreamEntity(
        id = id,
        serviceType = serviceType.name,
        name = name,
        host = host,
        port = port,
        netQuality = netQuality.name,
        networkInterface = networkSelection.interfaceName,
        networkSsid = networkSelection.ssid,
        lowLatency = lowLatency,
        sourceType = sourceType.name,
        broadcastMode = broadcastMode,
        volume = volume,
        isEnabled = isEnabled,
    )
