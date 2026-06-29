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
import com.audiolan.app.domain.model.StreamRuntimeStatus
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

    val transmitterStreams: StateFlow<List<Stream>> = streamRepository.getStreamsByType(ServiceType.TRANSMITTER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val receiverStreams: StateFlow<List<Stream>> = streamRepository.getStreamsByType(ServiceType.RECEIVER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transmitterStreamStatuses: StateFlow<Map<Long, StreamStatus>> =
        combine(
            transmitterStreams,
            transmitterState,
            serviceManager.transmitterStreamStatuses,
        ) { streams, state, runtimeStatuses ->
            buildStreamStatuses(streams, state, runtimeStatuses)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val receiverStreamStatuses: StateFlow<Map<Long, StreamStatus>> =
        combine(
            receiverStreams,
            receiverState,
            serviceManager.receiverStreamStatuses,
        ) { streams, state, runtimeStatuses ->
            buildStreamStatuses(streams, state, runtimeStatuses)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _networkInterfaces = MutableStateFlow<List<NetworkInfo>>(emptyList())
    val networkInterfaces: StateFlow<List<NetworkInfo>> = _networkInterfaces.asStateFlow()

    private val _usbTetherStatus = MutableStateFlow(UsbTetherStatus(false, emptyList(), emptyList()))
    val usbTetherStatus: StateFlow<UsbTetherStatus> = _usbTetherStatus.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(false)
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    private val _transmitterPanelError = MutableStateFlow<String?>(null)
    val transmitterPanelError: StateFlow<String?> = _transmitterPanelError.asStateFlow()

    private val _receiverPanelError = MutableStateFlow<String?>(null)
    val receiverPanelError: StateFlow<String?> = _receiverPanelError.asStateFlow()

    val snackbarHostState = SnackbarHostState()

    init {
        refreshNetworkInterfaces()
        viewModelScope.launch {
            transmitterState.collect { state -> handleServiceState(ServiceType.TRANSMITTER, state) }
        }
        viewModelScope.launch {
            receiverState.collect { state -> handleServiceState(ServiceType.RECEIVER, state) }
        }
        viewModelScope.launch {
            transmitterStreams.collect { streams -> clearResolvedPanelError(ServiceType.TRANSMITTER, streams) }
        }
        viewModelScope.launch {
            receiverStreams.collect { streams -> clearResolvedPanelError(ServiceType.RECEIVER, streams) }
        }
        viewModelScope.launch {
            serviceManager.transmitterStreamStatuses.collect { statuses ->
                handleRuntimeStatusErrors(ServiceType.TRANSMITTER, statuses)
            }
        }
        viewModelScope.launch {
            serviceManager.receiverStreamStatuses.collect { statuses ->
                handleRuntimeStatusErrors(ServiceType.RECEIVER, statuses)
            }
        }
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
        setPanelError(ServiceType.TRANSMITTER, "Microphone permission denied")
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
            setNoEnabledStreamsError(ServiceType.TRANSMITTER)
            return
        }
        clearPanelError(ServiceType.TRANSMITTER)
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
            val enabledStreams = withContext(Dispatchers.IO) {
                streamRepository.getEnabledStreamsByType(ServiceType.RECEIVER)
            }
            if (enabledStreams.isEmpty()) {
                setNoEnabledStreamsError(ServiceType.RECEIVER)
                return@launch
            }
            val duplicateNames = duplicateStreamNames(enabledStreams)
            if (duplicateNames.isNotEmpty()) {
                setDuplicateStreamNamesError(ServiceType.RECEIVER, duplicateNames)
                return@launch
            }
            clearPanelError(ServiceType.RECEIVER)
            serviceManager.startReceiverService()
        }
    }

    fun stopReceiver() = serviceManager.stopReceiverService()

    fun onTransmitterConsentDenied() {
        setPanelError(ServiceType.TRANSMITTER, "Screen capture permission denied")
        Timber.d("Transmitter MediaProjection consent denied by user")
    }

    fun onNoTransmitterStreamsConfigured() {
        setNoEnabledStreamsError(ServiceType.TRANSMITTER)
    }

    fun onDuplicateTransmitterStreamNames(names: List<String>) {
        setDuplicateStreamNamesError(ServiceType.TRANSMITTER, names)
    }

    fun toggleTransmitterAccordion() {
        _isTransmitterAccordionExpanded.value = !_isTransmitterAccordionExpanded.value
    }

    fun toggleReceiverAccordion() {
        _isReceiverAccordionExpanded.value = !_isReceiverAccordionExpanded.value
    }

    private fun buildStreamStatuses(
        streams: List<Stream>,
        serviceState: ServiceState,
        runtimeStatuses: Map<Long, StreamRuntimeStatus>,
    ): Map<Long, StreamStatus> {
        if (serviceState is ServiceState.Error) {
            val runtimeErrors = runtimeStatuses
                .filterValues { it is StreamRuntimeStatus.Error }
                .mapValues { (_, status) -> (status as StreamRuntimeStatus.Error).toUiStatus() }
            if (runtimeErrors.isNotEmpty()) return runtimeErrors
            return streams
                .filter { it.isEnabled }
                .associate { it.id to StreamStatus.Error(serviceState.message) }
        }
        if (serviceState !is ServiceState.Running) return emptyMap()
        return streams
            .filter { it.isEnabled }
            .mapNotNull { stream ->
                val runtimeStatus = runtimeStatuses[stream.id] ?: return@mapNotNull null
                stream.id to runtimeStatus.toUiStatus()
            }
            .toMap()
    }

    private fun StreamRuntimeStatus.toUiStatus(): StreamStatus =
        when (this) {
            StreamRuntimeStatus.Connecting -> StreamStatus.Connecting
            StreamRuntimeStatus.Active -> StreamStatus.Active
            is StreamRuntimeStatus.Error -> StreamStatus.Error(message)
        }

    private fun handleServiceState(serviceType: ServiceType, state: ServiceState) {
        when (state) {
            is ServiceState.Error -> {
                if (isPersistentPanelError(state.message)) {
                    setPanelError(serviceType, state.message)
                } else {
                    clearPanelError(serviceType)
                    showTransientError(state.message)
                }
            }
            ServiceState.Starting,
            ServiceState.Running,
                -> clearPanelError(serviceType)
            ServiceState.Idle,
            ServiceState.Stopping,
                -> Unit
        }
    }

    private fun showTransientError(message: String) {
        viewModelScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long,
            )
        }
    }

    private fun setNoEnabledStreamsError(serviceType: ServiceType) {
        setPanelError(serviceType, "No enabled ${serviceType.name.lowercase()} streams configured")
    }

    private fun setDuplicateStreamNamesError(serviceType: ServiceType, names: List<String>) {
        setPanelError(serviceType, "Duplicate ${serviceType.name.lowercase()} stream name: ${names.joinToString()}")
    }

    private fun setPanelError(serviceType: ServiceType, message: String) {
        when (serviceType) {
            ServiceType.TRANSMITTER -> _transmitterPanelError.value = message
            ServiceType.RECEIVER -> _receiverPanelError.value = message
        }
    }

    private fun clearPanelError(serviceType: ServiceType) {
        when (serviceType) {
            ServiceType.TRANSMITTER -> _transmitterPanelError.value = null
            ServiceType.RECEIVER -> _receiverPanelError.value = null
        }
    }

    private fun getPanelError(serviceType: ServiceType): String? =
        when (serviceType) {
            ServiceType.TRANSMITTER -> _transmitterPanelError.value
            ServiceType.RECEIVER -> _receiverPanelError.value
        }

    private fun clearResolvedPanelError(serviceType: ServiceType, streams: List<Stream>) {
        val current = when (serviceType) {
            ServiceType.TRANSMITTER -> _transmitterPanelError.value
            ServiceType.RECEIVER -> _receiverPanelError.value
        } ?: return
        val enabledStreams = streams.filter { it.isEnabled }
        val shouldClear = when {
            current.startsWith("No enabled", ignoreCase = true) -> enabledStreams.isNotEmpty()
            current.startsWith("Duplicate", ignoreCase = true) -> duplicateStreamNames(enabledStreams).isEmpty()
            else -> false
        }
        if (shouldClear) {
            clearPanelError(serviceType)
        }
    }

    private fun isPersistentPanelError(message: String): Boolean {
        val normalized = message.lowercase()
        return listOf(
            "no enabled",
            "duplicate",
            "mediaprojection",
            "screen capture",
            "permission",
            "port",
            "in use",
            "init failed",
            "initialise",
            "initialize",
            "audiorecord",
            "capture failed",
            "revoked",
            "unsupported",
            "usb tethered",
            "selected network",
        ).any { token -> token in normalized }
    }

    private fun handleRuntimeStatusErrors(
        serviceType: ServiceType,
        statuses: Map<Long, StreamRuntimeStatus>,
    ) {
        val message = statuses.values
            .filterIsInstance<StreamRuntimeStatus.Error>()
            .firstOrNull()
            ?.message
        val current = getPanelError(serviceType)
        if (message != null && isPersistentPanelError(message)) {
            setPanelError(serviceType, message)
        } else if (current?.startsWith("Selected network", ignoreCase = true) == true) {
            clearPanelError(serviceType)
        }
    }

    private fun duplicateStreamNames(streams: List<Stream>): List<String> =
        streams
            .groupBy { it.name.trim().lowercase() }
            .filterKeys { it.isNotBlank() }
            .filterValues { it.size > 1 }
            .values
            .mapNotNull { duplicates -> duplicates.firstOrNull()?.name }

}
