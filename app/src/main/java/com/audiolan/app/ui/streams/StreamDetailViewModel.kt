package com.audiolan.app.ui.streams

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiolan.app.data.repository.StreamRepository
import com.audiolan.app.domain.model.NetQuality
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.domain.model.TransportMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class StreamDetailViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
) : ViewModel() {
    var name by mutableStateOf("")
    var host by mutableStateOf("")
    var port by mutableStateOf("6980")
    var netQuality by mutableStateOf(NetQuality.OPTIMAL)
    var transportMode by mutableStateOf(TransportMode.WIFI)

    var nameError by mutableStateOf<String?>(null)
    var portError by mutableStateOf<String?>(null)

    private var editingStreamId: Long? = null
    private var existingVolume: Float = 1.0f
    private var existingEnabled: Boolean = true
    private var loadedStreamId: Long? = null
    private var appliedPrefillKey: String? = null

    val isEditMode: Boolean get() = editingStreamId != null

    fun applyPrefill(hostValue: String, streamNameValue: String, transportModeValue: String, lowLatencyValue: Boolean) {
        val key = "$hostValue|$streamNameValue|$transportModeValue|$lowLatencyValue"
        if (appliedPrefillKey == key || editingStreamId != null) return
        appliedPrefillKey = key
        if (hostValue.isNotBlank() && host.isBlank()) {
            host = hostValue
        }
        if (streamNameValue.isNotBlank() && name.isBlank()) {
            name = streamNameValue
        }
        if (transportModeValue.isNotBlank()) {
            transportMode = runCatching { TransportMode.valueOf(transportModeValue) }.getOrDefault(TransportMode.WIFI)
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
            transportMode = stream.transportMode
            existingVolume = stream.volume
            existingEnabled = stream.isEnabled
            validate()
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
        val stream = Stream(
            id = editingStreamId ?: 0L,
            serviceType = serviceType,
            name = name.trim(),
            host = host.trim(),
            port = port.toInt(),
            netQuality = netQuality,
            transportMode = transportMode,
            lowLatency = false,
            volume = if (isEditMode) existingVolume else 1.0f,
            isEnabled = if (isEditMode) existingEnabled else true,
        )
        viewModelScope.launch {
            streamRepository.insertOrUpdate(stream)
            onSuccess()
        }
    }
}
