package com.audiolan.app.ui.home

import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiolan.app.data.repository.StreamRepository
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.SourceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.service.ServiceManager
import com.audiolan.app.ui.components.StreamStatus
import com.audiolan.app.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class NetworkInfo(
    val interfaceName: String,
    val ip: String,
)

data class UsbTetherStatus(
    val isAvailable: Boolean,
    val interfaces: List<NetworkInfo>,
    val peers: List<String>,
)

data class TransmitterStartConfig(
    val hasMicStreams: Boolean,
    val hasInternalAudioStreams: Boolean,
    val enabledStreamCount: Int,
    val duplicateNames: List<String>,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serviceManager: ServiceManager,
    private val streamRepository: StreamRepository,
) : ViewModel() {
    val transmitterState: StateFlow<ServiceState> = serviceManager.transmitterState
    val receiverState: StateFlow<ServiceState> = serviceManager.receiverState
    val transmitterLevelState: StateFlow<Pair<Float, Float>> = serviceManager.transmitterLevel
    val receiverLevelState: StateFlow<Pair<Float, Float>> = serviceManager.receiverLevel

    private val _isTransmitterAccordionExpanded = MutableStateFlow(false)
    val isTransmitterAccordionExpanded: StateFlow<Boolean> = _isTransmitterAccordionExpanded.asStateFlow()

    private val _isReceiverAccordionExpanded = MutableStateFlow(false)
    val isReceiverAccordionExpanded: StateFlow<Boolean> = _isReceiverAccordionExpanded.asStateFlow()

    private val _transmitterActiveDelayElapsed = MutableStateFlow(false)
    private val _receiverActiveDelayElapsed = MutableStateFlow(false)

    val transmitterStreams: StateFlow<List<Stream>> = streamRepository.getStreamsByType(ServiceType.TRANSMITTER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val receiverStreams: StateFlow<List<Stream>> = streamRepository.getStreamsByType(ServiceType.RECEIVER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transmitterStreamStatuses: StateFlow<Map<Long, StreamStatus>> =
        combine(transmitterStreams, transmitterState, _transmitterActiveDelayElapsed) { streams, state, activeDelayElapsed ->
            buildStreamStatuses(streams, state, activeDelayElapsed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val receiverStreamStatuses: StateFlow<Map<Long, StreamStatus>> =
        combine(receiverStreams, receiverState, _receiverActiveDelayElapsed) { streams, state, activeDelayElapsed ->
            buildStreamStatuses(streams, state, activeDelayElapsed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _networkInterfaces = MutableStateFlow<List<NetworkInfo>>(emptyList())
    val networkInterfaces: StateFlow<List<NetworkInfo>> = _networkInterfaces.asStateFlow()

    private val _usbTetherStatus = MutableStateFlow(UsbTetherStatus(false, emptyList(), emptyList()))
    val usbTetherStatus: StateFlow<UsbTetherStatus> = _usbTetherStatus.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(false)
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    val snackbarHostState = SnackbarHostState()

    init {
        refreshNetworkInterfaces()
        viewModelScope.launch {
            merge(transmitterState, receiverState).collect { state ->
                if (state is ServiceState.Error) {
                    snackbarHostState.showSnackbar(
                        message = state.message,
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }
        observeServiceDelay(transmitterState, _transmitterActiveDelayElapsed)
        observeServiceDelay(receiverState, _receiverActiveDelayElapsed)
    }

    fun refreshNetworkInterfaces() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _networkInterfaces.value = NetworkUtils.getActiveIpv4Interfaces()
                    .map { (interfaceName, ip) -> NetworkInfo(interfaceName, ip) }
                val usbInterfaces = NetworkUtils.getUsbTetherIpv4Interfaces()
                    .map { (interfaceName, ip) -> NetworkInfo(interfaceName, ip) }
                _usbTetherStatus.value = UsbTetherStatus(
                    isAvailable = usbInterfaces.isNotEmpty(),
                    interfaces = usbInterfaces,
                    peers = NetworkUtils.getUsbTetherPeerCandidates(),
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to enumerate network interfaces")
                _networkInterfaces.value = emptyList()
                _usbTetherStatus.value = UsbTetherStatus(false, emptyList(), emptyList())
            }
        }
    }

    fun onMicPermissionResult(granted: Boolean) {
        _hasMicPermission.value = granted
    }

    fun onMicPermissionDenied() {
        _hasMicPermission.value = false
        Timber.w("RECORD_AUDIO permission denied by user")
    }

    suspend fun getTransmitterStartConfig(): TransmitterStartConfig =
        withContext(Dispatchers.IO) {
            val streams = streamRepository.getEnabledStreamsByType(ServiceType.TRANSMITTER)
            TransmitterStartConfig(
                hasMicStreams = streams.any { it.sourceType == SourceType.MIC },
                hasInternalAudioStreams = streams.any { it.sourceType == SourceType.INTERNAL_AUDIO },
                enabledStreamCount = streams.size,
                duplicateNames = duplicateStreamNames(streams),
            )
        }

    suspend fun transmitterNeedsMediaProjection(): Boolean =
        getTransmitterStartConfig().hasInternalAudioStreams

    fun startTransmitter(
        resultCode: Int? = null,
        data: Intent? = null,
        hasMicStreams: Boolean = true,
        hasInternalAudioStreams: Boolean = resultCode != null && data != null,
    ) {
        if (!hasMicStreams && !hasInternalAudioStreams) {
            showNoTransmitterStreamsMessage()
            return
        }
        serviceManager.startTransmitterService(
            resultCode = resultCode,
            data = data,
            hasMicStreams = hasMicStreams,
            hasInternalAudioStreams = hasInternalAudioStreams,
        )
    }

    fun stopTransmitter() = serviceManager.stopTransmitterService()

    fun startReceiver() {
        viewModelScope.launch {
            val duplicateNames = withContext(Dispatchers.IO) {
                duplicateStreamNames(streamRepository.getEnabledStreamsByType(ServiceType.RECEIVER))
            }
            if (duplicateNames.isNotEmpty()) {
                showDuplicateStreamNamesMessage(ServiceType.RECEIVER, duplicateNames)
                return@launch
            }
            serviceManager.startReceiverService()
        }
    }

    fun stopReceiver() = serviceManager.stopReceiverService()

    fun onTransmitterConsentDenied() {
        Timber.d("Transmitter MediaProjection consent denied by user")
    }

    fun onNoTransmitterStreamsConfigured() {
        showNoTransmitterStreamsMessage()
    }

    fun onDuplicateTransmitterStreamNames(names: List<String>) {
        showDuplicateStreamNamesMessage(ServiceType.TRANSMITTER, names)
    }

    fun toggleTransmitterAccordion() {
        _isTransmitterAccordionExpanded.value = !_isTransmitterAccordionExpanded.value
    }

    fun toggleReceiverAccordion() {
        _isReceiverAccordionExpanded.value = !_isReceiverAccordionExpanded.value
    }

    private fun observeServiceDelay(
        stateFlow: StateFlow<ServiceState>,
        target: MutableStateFlow<Boolean>,
    ) {
        viewModelScope.launch {
            stateFlow.collect { state ->
                if (state is ServiceState.Running) {
                    target.value = false
                    kotlinx.coroutines.delay(ACTIVE_STATUS_DELAY_MS)
                    if (stateFlow.value is ServiceState.Running) {
                        target.value = true
                    }
                } else {
                    target.value = false
                }
            }
        }
    }

    private fun buildStreamStatuses(
        streams: List<Stream>,
        serviceState: ServiceState,
        activeDelayElapsed: Boolean,
    ): Map<Long, StreamStatus> {
        if (serviceState is ServiceState.Error) {
            return streams
                .filter { it.isEnabled }
                .associate { it.id to StreamStatus.Error(serviceState.message) }
        }
        if (serviceState !is ServiceState.Running) return emptyMap()
        return streams
            .filter { it.isEnabled }
            .associate { stream ->
                stream.id to if (activeDelayElapsed) StreamStatus.Active else StreamStatus.Connecting
            }
    }

    private fun showNoTransmitterStreamsMessage() {
        viewModelScope.launch {
            snackbarHostState.showSnackbar(
                message = "No enabled transmitter streams configured",
                duration = SnackbarDuration.Long,
            )
        }
    }

    private fun showDuplicateStreamNamesMessage(serviceType: ServiceType, names: List<String>) {
        viewModelScope.launch {
            snackbarHostState.showSnackbar(
                message = "Duplicate ${serviceType.name.lowercase()} stream name: ${names.joinToString()}",
                duration = SnackbarDuration.Long,
            )
        }
    }

    private fun duplicateStreamNames(streams: List<Stream>): List<String> =
        streams
            .groupBy { it.name.trim().lowercase() }
            .filterKeys { it.isNotBlank() }
            .filterValues { it.size > 1 }
            .values
            .mapNotNull { duplicates -> duplicates.firstOrNull()?.name }

    private companion object {
        const val ACTIVE_STATUS_DELAY_MS = 2_000L
    }
}
