package com.audiolan.app.ui.discovery

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiolan.app.data.repository.DiscoveredDevicesRepository
import com.audiolan.app.data.repository.StreamRepository
import com.audiolan.app.domain.model.DiscoverySource
import com.audiolan.app.domain.model.DiscoveredDevice
import com.audiolan.app.domain.model.NetworkSelection
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.toRouteHint
import com.audiolan.app.service.discovery.DiscoveryService
import com.audiolan.app.ui.navigation.Screen
import com.audiolan.app.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class MergedDevice(
    val ip: String,
    val port: Int,
    val deviceName: String?,
    val streamName: String?,
    val sources: Set<DiscoverySource>,
    val originNetworks: Set<NetworkSelection>,
    val isSelfOriginated: Boolean,
)

const val SELF_ORIGINATED_STREAM_LABEL = "Cannot configure transmitter of the same device"

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val discoveredDevicesRepository: DiscoveredDevicesRepository,
    private val streamRepository: StreamRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _devices = MutableStateFlow<List<MergedDevice>>(emptyList())
    val devices: StateFlow<List<MergedDevice>> = _devices.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _duplicateMessage = MutableStateFlow<String?>(null)
    val duplicateMessage: StateFlow<String?> = _duplicateMessage.asStateFlow()

    private var collectionJob: Job? = null
    private var scanTimerJob: Job? = null

    fun startScan() {
        if (_isScanning.value) return

        _isScanning.value = true
        _devices.value = emptyList()
        _error.value = null
        _duplicateMessage.value = null
        collectionJob?.cancel()
        scanTimerJob?.cancel()
        discoveredDevicesRepository.clear()

        collectionJob = viewModelScope.launch {
            discoveredDevicesRepository.devices.collect { device ->
                mergeDevice(device)
            }
        }

        runCatching {
            context.startService(Intent(context, DiscoveryService::class.java))
        }.onFailure { error ->
            _error.value = error.message
            _isScanning.value = false
        }

        scanTimerJob = viewModelScope.launch {
            delay(SCAN_COMPLETE_DELAY_MS)
            _isScanning.value = false
        }
    }

    fun buildPrefilledRoute(device: MergedDevice): String =
        Screen.StreamDetail.createRoute(
            host = device.ip,
            streamName = device.streamName.orEmpty(),
            networkHint = primaryOriginNetwork(device)?.toRouteHint()
                ?: if (device.sources.contains(DiscoverySource.USB_TETHER)) {
                    LEGACY_USB_TETHER_TRANSPORT
                } else {
                    ""
                },
            lowLatency = false,
        )

    fun onDeviceSelected(device: MergedDevice, onNavigate: (String) -> Unit) {
        viewModelScope.launch {
            if (device.isSelfOriginated) {
                _duplicateMessage.value = SELF_ORIGINATED_STREAM_LABEL
                return@launch
            }
            val streamName = device.streamName
            if (!streamName.isNullOrBlank() && hasConfiguredReceiver(streamName, device.ip, device.port)) {
                _duplicateMessage.value =
                    "Receiver for $streamName at ${device.ip}:${device.port} is already configured."
            } else {
                onNavigate(buildPrefilledRoute(device))
            }
        }
    }

    fun dismissDuplicateMessage() {
        _duplicateMessage.value = null
    }

    private suspend fun hasConfiguredReceiver(streamName: String, ip: String, port: Int): Boolean =
        streamRepository
            .getStreamsByType(ServiceType.RECEIVER)
            .first()
            .any { stream ->
                stream.name == streamName &&
                    stream.host == ip &&
                    stream.port == port
            }

    private fun mergeDevice(device: DiscoveredDevice) {
        val current = _devices.value.toMutableList()
        val isSelfOriginated = NetworkUtils.isOwnActiveInterfaceAddress(device.ip)
        val existingIndex = current.indexOfFirst { existing ->
            existing.ip == device.ip &&
                (
                    device.streamName.isNullOrBlank() ||
                        existing.streamName.isNullOrBlank() ||
                        existing.streamName == device.streamName
                    )
        }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            current[existingIndex] = existing.copy(
                deviceName = device.deviceName ?: existing.deviceName,
                streamName = device.streamName ?: existing.streamName,
                sources = existing.sources + device.source,
                originNetworks = existing.originNetworks + listOfNotNull(device.originNetwork),
                isSelfOriginated = existing.isSelfOriginated || isSelfOriginated,
            )
        } else {
            current += MergedDevice(
                ip = device.ip,
                port = VBAN_PORT,
                deviceName = device.deviceName,
                streamName = device.streamName,
                sources = setOf(device.source),
                originNetworks = setOfNotNull(device.originNetwork),
                isSelfOriginated = isSelfOriginated,
            )
        }
        _devices.value = current.sortedBy { it.ip }
    }

    private fun primaryOriginNetwork(device: MergedDevice): NetworkSelection? =
        device.originNetworks.minByOrNull { it.sortKey }

    private companion object {
        const val SCAN_COMPLETE_DELAY_MS = 5_500L
        const val VBAN_PORT = 6_980
        const val LEGACY_USB_TETHER_TRANSPORT = "USB_TETHER"
    }
}
