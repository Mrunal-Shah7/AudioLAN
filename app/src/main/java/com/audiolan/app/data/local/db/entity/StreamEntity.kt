package com.audiolan.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.audiolan.app.domain.model.NetQuality
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.domain.model.TransportMode

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
    @ColumnInfo(name = "transport_mode") val transportMode: String = TransportMode.WIFI.name,
    @ColumnInfo(name = "low_latency") val lowLatency: Boolean = false,
    val volume: Float = 1.0f,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true,
)

fun StreamEntity.toDomain(): Stream =
    Stream(
        id = id,
        serviceType = ServiceType.valueOf(serviceType),
        name = name,
        host = host,
        port = port,
        netQuality = NetQuality.valueOf(netQuality),
        transportMode = runCatching { TransportMode.valueOf(transportMode) }.getOrDefault(TransportMode.WIFI),
        lowLatency = lowLatency,
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
        transportMode = transportMode.name,
        lowLatency = lowLatency,
        volume = volume,
        isEnabled = isEnabled,
    )
