package com.audiolan.app.ui.streams

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiolan.app.data.repository.StreamRepository
import com.audiolan.app.domain.model.NetQuality
import com.audiolan.app.domain.model.NetworkSelection
import com.audiolan.app.domain.model.NetworkSelectionOption
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.SourceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class StreamDetailViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    var name by mutableStateOf("")
    var host by mutableStateOf("")
    var port by mutableStateOf("6980")
    var netQuality by mutableStateOf(NetQuality.OPTIMAL)
    var networkSelection by mutableStateOf(NetworkSelection.anyWifi())
    var networkOptions by mutableStateOf<List<NetworkSelectionOption>>(emptyList())
    var sourceType by mutableStateOf(SourceType.MIC)
    var broadcastMode by mutableStateOf(false)

    var nameError by mutableStateOf<String?>(null)
    var portError by mutableStateOf<String?>(null)

    private var editingStreamId: Long? = null
    private var existingVolume: Float = 1.0f
    private var existingEnabled: Boolean = true
    private var loadedStreamId: Long? = null
    private var appliedPrefillKey: String? = null

    val isEditMode: Boolean get() = editingStreamId != null

    init {
        refreshNetworkOptions()
    }

    fun applyPrefill(hostValue: String, streamNameValue: String, networkHintValue: String, lowLatencyValue: Boolean) {
        val key = "$hostValue|$streamNameValue|$networkHintValue|$lowLatencyValue"
        if (appliedPrefillKey == key || editingStreamId != null) return
        appliedPrefillKey = key
        if (hostValue.isNotBlank() && host.isBlank()) {
            host = hostValue
        }
        if (streamNameValue.isNotBlank() && name.isBlank()) {
            name = streamNameValue
        }
        val hintedNetworkSelection = NetworkSelection.fromRouteHint(networkHintValue)
        when {
            hintedNetworkSelection != null -> {
                networkSelection = hintedNetworkSelection
                refreshNetworkOptions()
            }
            networkHintValue == LEGACY_USB_TETHER_TRANSPORT -> {
                networkSelection = NetworkSelection.anyUsbTether()
                refreshNetworkOptions()
            }
        }
        validate()
    }

    fun loadStream(streamId: Long) {
        if (loadedStreamId == streamId) return
        loadedStreamId = streamId
        editingStreamId = streamId
        viewModelScope.launch {
            val stream = streamRepository.getById(streamId) ?: return@launch
            name = stream.name
            host = stream.host
            port = stream.port.toString()
            netQuality = stream.netQuality
            networkSelection = stream.networkSelection
            sourceType = stream.sourceType
            broadcastMode = stream.broadcastMode
            existingVolume = stream.volume
            existingEnabled = stream.isEnabled
            refreshNetworkOptions()
            validate()
        }
    }

    fun refreshNetworkOptions() {
        viewModelScope.launch {
            val liveOptions = withContext(Dispatchers.IO) {
                NetworkUtils.getNetworkSelectionOptions(context)
            }
            if (!isEditMode && networkSelection.isAnyWifi && liveOptions.isNotEmpty()) {
                networkSelection = liveOptions.first().selection
            } else if (!isEditMode && networkSelection.isAnyUsbTether) {
                liveOptions.firstOrNull {
                    NetworkUtils.isLikelyUsbTetherInterface(it.selection.interfaceName) ||
                        NetworkUtils.isLikelyEthernetInterface(it.selection.interfaceName)
                }?.let { networkSelection = it.selection }
            }

            val updatedSelectedOption = liveOptions.firstOrNull { it.selection == networkSelection }
            networkOptions = if (updatedSelectedOption == null) {
                liveOptions + NetworkSelectionOption(
                    selection = networkSelection,
                    label = "${networkSelection.displayName} (unavailable)",
                    address = null,
                    isAvailable = false,
                )
            } else {
                liveOptions
            }
        }
    }

    fun validate(): Boolean {
        var valid = true
        val trimmedName = name.trim()
        val nameBytes = trimmedName.toByteArray(Charsets.UTF_8)
        nameError = when {
            trimmedName.isBlank() -> "name cannot be empty"
            nameBytes.size > 16 -> "name must be <= 16 bytes (${nameBytes.size} bytes entered)"
            else -> null
        }
        if (nameError != null) valid = false

        val portInt = port.toIntOrNull()
        portError = when {
            portInt == null -> "port must be a number"
            portInt !in 1..65535 -> "port must be 1-65535"
            else -> null
        }
        if (portError != null) valid = false

        return valid
    }

    fun save(serviceType: ServiceType, onSuccess: () -> Unit) {
        if (!validate()) return
        viewModelScope.launch {
            val trimmedName = name.trim()
            val duplicateName = streamRepository.getStreamsByType(serviceType)
                .first()
                .any { stream ->
                    stream.id != editingStreamId &&
                        stream.name.equals(trimmedName, ignoreCase = true)
                }
            if (duplicateName) {
                nameError = "name already exists in this ${serviceType.name.lowercase()} service"
                return@launch
            }

            val stream = Stream(
                id = editingStreamId ?: 0L,
                serviceType = serviceType,
                name = trimmedName,
                host = host.trim(),
                port = port.toInt(),
                netQuality = netQuality,
                networkSelection = networkSelection,
                lowLatency = false,
                sourceType = sourceType,
                broadcastMode = broadcastMode,
                volume = if (isEditMode) existingVolume else 1.0f,
                isEnabled = if (isEditMode) existingEnabled else true,
            )
            streamRepository.insertOrUpdate(stream)
            onSuccess()
        }
    }

    private companion object {
        const val LEGACY_USB_TETHER_TRANSPORT = "USB_TETHER"
    }
}
