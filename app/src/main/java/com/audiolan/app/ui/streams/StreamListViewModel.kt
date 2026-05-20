package com.audiolan.app.ui.streams

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiolan.app.data.repository.StreamRepository
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
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

    init {
        viewModelScope.launch {
            volumeUpdates
                .debounce(300L)
                .collect { (id, volume) -> streamRepository.setVolume(id, volume) }
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
}
