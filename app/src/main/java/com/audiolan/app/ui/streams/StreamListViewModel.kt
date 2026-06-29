package com.audiolan.app.ui.streams

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiolan.app.data.repository.StreamRepository
import com.audiolan.app.domain.model.NetworkSelection
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.domain.model.StreamRuntimeStatus
import com.audiolan.app.service.ServiceManager
import com.audiolan.app.ui.components.StreamStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StreamListUiState(
    val streams: List<Stream> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class NetworkStreamGroup(
    val networkSelection: NetworkSelection,
    val streams: List<Stream>,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class StreamListViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val serviceManager: ServiceManager,
) : ViewModel() {
    private val serviceType = MutableStateFlow<ServiceType?>(null)
    val snackbarHostState = SnackbarHostState()
    private val _groupByNetwork = MutableStateFlow(false)
    val groupByNetwork: StateFlow<Boolean> = _groupByNetwork
    private val volumeUpdates = MutableSharedFlow<Pair<Long, Float>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val uiState: StateFlow<StreamListUiState> = serviceType
        .flatMapLatest { type ->
            if (type == null) {
                flowOf(StreamListUiState())
            } else {
                streamRepository.getStreamsByType(type)
                    .map { streams -> StreamListUiState(streams = streams, isLoading = false) }
                    .catch { emit(StreamListUiState(isLoading = false, error = it.message)) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StreamListUiState(isLoading = true),
        )

    val serviceState: StateFlow<ServiceState> = serviceType
        .flatMapLatest { type ->
            when (type) {
                ServiceType.TRANSMITTER -> serviceManager.transmitterState
                ServiceType.RECEIVER -> serviceManager.receiverState
                null -> flowOf(ServiceState.Idle)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceState.Idle)

    private val runtimeStatuses: StateFlow<Map<Long, StreamRuntimeStatus>> = serviceType
        .flatMapLatest { type ->
            when (type) {
                ServiceType.TRANSMITTER -> serviceManager.transmitterStreamStatuses
                ServiceType.RECEIVER -> serviceManager.receiverStreamStatuses
                null -> flowOf(emptyMap())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val streamStatuses: StateFlow<Map<Long, StreamStatus>> =
        combine(uiState, serviceState, runtimeStatuses) { uiState, state, runtimeStatuses ->
            buildStreamStatuses(uiState.streams, state, runtimeStatuses)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val networkGroups: StateFlow<List<NetworkStreamGroup>> =
        combine(uiState, groupByNetwork) { uiState, groupByNetwork ->
            if (!groupByNetwork) {
                emptyList()
            } else {
                buildNetworkGroups(uiState.streams)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            volumeUpdates
                .collect { (id, volume) -> streamRepository.setVolume(id, volume) }
        }
    }

    fun setServiceType(type: ServiceType) {
        if (serviceType.value != type) {
            _groupByNetwork.value = false
            serviceType.value = type
        }
    }

    fun toggleGroupByNetwork() {
        _groupByNetwork.value = !_groupByNetwork.value
    }

    fun setEnabled(streamId: Long, enabled: Boolean) {
        viewModelScope.launch {
            streamRepository.setEnabled(streamId, enabled)
        }
    }

    fun delete(stream: Stream) {
        viewModelScope.launch {
            streamRepository.delete(stream)
        }
    }

    fun setVolume(streamId: Long, volume: Float) {
        volumeUpdates.tryEmit(streamId to volume)
    }

    fun showError(message: String) {
        viewModelScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long,
            )
        }
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

    private fun buildNetworkGroups(streams: List<Stream>): List<NetworkStreamGroup> =
        streams
            .groupBy { it.networkSelection }
            .entries
            .sortedBy { it.key.sortKey }
            .map { (networkSelection, groupedStreams) ->
                NetworkStreamGroup(networkSelection, groupedStreams)
            }
}
