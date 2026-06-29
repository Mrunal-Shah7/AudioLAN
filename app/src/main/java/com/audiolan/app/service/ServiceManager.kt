package com.audiolan.app.service

import android.content.Context
import android.content.Intent
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.StreamRuntimeStatus
import com.audiolan.app.service.receiver.ReceiverService
import com.audiolan.app.service.transmitter.TransmitterService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _transmitterState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    private val _receiverState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    private val _transmitterLevel = MutableStateFlow(0f to 0f)
    private val _receiverLevel = MutableStateFlow(0f to 0f)
    private val _transmitterStreamStatuses =
        MutableStateFlow<Map<Long, StreamRuntimeStatus>>(emptyMap())
    private val _receiverStreamStatuses =
        MutableStateFlow<Map<Long, StreamRuntimeStatus>>(emptyMap())

    val transmitterState: StateFlow<ServiceState> = _transmitterState.asStateFlow()
    val receiverState: StateFlow<ServiceState> = _receiverState.asStateFlow()
    val transmitterLevel: StateFlow<Pair<Float, Float>> = _transmitterLevel.asStateFlow()
    val receiverLevel: StateFlow<Pair<Float, Float>> = _receiverLevel.asStateFlow()
    val transmitterStreamStatuses: StateFlow<Map<Long, StreamRuntimeStatus>> =
        _transmitterStreamStatuses.asStateFlow()
    val receiverStreamStatuses: StateFlow<Map<Long, StreamRuntimeStatus>> =
        _receiverStreamStatuses.asStateFlow()

    fun updateTransmitterState(state: ServiceState) {
        _transmitterState.value = state
    }

    fun updateReceiverState(state: ServiceState) {
        _receiverState.value = state
    }

    fun updateTransmitterLevel(left: Float, right: Float) {
        _transmitterLevel.value = left to right
    }

    fun updateReceiverLevel(left: Float, right: Float) {
        _receiverLevel.value = left to right
    }

    fun clearTransmitterLevel() {
        _transmitterLevel.value = 0f to 0f
    }

    fun clearReceiverLevel() {
        _receiverLevel.value = 0f to 0f
    }

    fun updateStreamRuntimeStatus(
        serviceType: ServiceType,
        streamId: Long,
        status: StreamRuntimeStatus,
    ) {
        val flow = runtimeStatusFlow(serviceType)
        flow.value = flow.value + (streamId to status)
    }

    fun clearStreamRuntimeStatus(serviceType: ServiceType, streamId: Long) {
        val flow = runtimeStatusFlow(serviceType)
        if (streamId in flow.value) {
            flow.value = flow.value - streamId
        }
    }

    fun clearStreamRuntimeStatuses(serviceType: ServiceType) {
        runtimeStatusFlow(serviceType).value = emptyMap()
    }

    fun startTransmitterService(
        resultCode: Int? = null,
        data: Intent? = null,
        hasMicStreams: Boolean = true,
        hasInternalAudioStreams: Boolean = resultCode != null && data != null,
    ) {
        if (!hasMicStreams && !hasInternalAudioStreams) {
            _transmitterState.value = ServiceState.Idle
            return
        }
        _transmitterState.value = ServiceState.Starting
        val intent = Intent(context, TransmitterService::class.java).apply {
            putExtra(EXTRA_HAS_MIC_STREAMS, hasMicStreams)
            putExtra(EXTRA_HAS_INTERNAL_AUDIO_STREAMS, hasInternalAudioStreams)
            if (resultCode != null && data != null) {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
        }
        context.startForegroundService(intent)
    }

    fun stopTransmitterService() {
        _transmitterState.value = ServiceState.Stopping
        context.stopService(Intent(context, TransmitterService::class.java))
    }

    fun startReceiverService() {
        _receiverState.value = ServiceState.Starting
        context.startForegroundService(Intent(context, ReceiverService::class.java))
    }

    fun stopReceiverService() {
        _receiverState.value = ServiceState.Stopping
        context.stopService(Intent(context, ReceiverService::class.java))
    }

    private fun runtimeStatusFlow(serviceType: ServiceType): MutableStateFlow<Map<Long, StreamRuntimeStatus>> =
        when (serviceType) {
            ServiceType.TRANSMITTER -> _transmitterStreamStatuses
            ServiceType.RECEIVER -> _receiverStreamStatuses
        }

    companion object {
        const val TRANSMITTER_CHANNEL_ID = "transmitter_service"
        const val RECEIVER_CHANNEL_ID = "receiver_service"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_HAS_MIC_STREAMS = "has_mic_streams"
        const val EXTRA_HAS_INTERNAL_AUDIO_STREAMS = "has_internal_audio_streams"
        const val ACTION_STOP = "com.audiolan.app.action.STOP_SERVICE"
    }
}
