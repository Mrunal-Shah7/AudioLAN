package com.audiolan.app.ui.streams

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiolan.app.data.repository.StreamRepository
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
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
import kotlinx.coroutines.flow.debounce
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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class StreamListViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val serviceManager: ServiceManager,
) : ViewModel() {
    private val serviceType = MutableStateFlow<ServiceType?>(null)
    val snackbarHostState = SnackbarHostState()
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

    val levelState: StateFlow<Pair<Float, Float>> = serviceType
        .flatMapLatest { type ->
            when (type) {
                ServiceType.TRANSMITTER -> serviceManager.transmitterLevel
                ServiceType.RECEIVER -> serviceManager.receiverLevel
                null -> flowOf(0f to 0f)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f to 0f)

    private val activeDelayElapsed = MutableStateFlow(false)

    val streamStatuses: StateFlow<Map<Long, StreamStatus>> =
        combine(uiState, serviceState, activeDelayElapsed) { uiState, state, delayElapsed ->
            when {
                state is ServiceState.Error -> uiState.streams
                    .filter { it.isEnabled }
                    .associate { it.id to StreamStatus.Error(state.message) }
                state is ServiceState.Running -> uiState.streams
                    .filter { it.isEnabled }
                    .associate { stream ->
                        stream.id to if (delayElapsed) StreamStatus.Active else StreamStatus.Connecting
                    }
                else -> emptyMap()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            volumeUpdates
                .debounce(300L)
                .collect { (id, volume) -> streamRepository.setVolume(id, volume) }
        }
        viewModelScope.launch {
            serviceState.collect { state ->
                if (state is ServiceState.Running) {
                    activeDelayElapsed.value = false
                    kotlinx.coroutines.delay(ACTIVE_STATUS_DELAY_MS)
                    if (serviceState.value is ServiceState.Running) {
                        activeDelayElapsed.value = true
                    }
                } else {
                    activeDelayElapsed.value = false
                }
            }
        }
    }

    fun setServiceType(type: ServiceType) {
        serviceType.value = type
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

    private companion object {
        const val ACTIVE_STATUS_DELAY_MS = 2_000L
    }
}
