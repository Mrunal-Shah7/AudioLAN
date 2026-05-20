package com.audiolan.app.service

import android.content.Context
import android.content.Intent
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.service.cast.CastStreamingService
import com.audiolan.app.service.mic.MicStreamingService
import com.audiolan.app.service.receiver.ReceiverService
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
    private val _micState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    private val _castState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    private val _receiverState = MutableStateFlow<ServiceState>(ServiceState.Idle)

    val micState: StateFlow<ServiceState> = _micState.asStateFlow()
    val castState: StateFlow<ServiceState> = _castState.asStateFlow()
    val receiverState: StateFlow<ServiceState> = _receiverState.asStateFlow()

    fun updateMicState(state: ServiceState) {
        _micState.value = state
    }

    fun updateCastState(state: ServiceState) {
        _castState.value = state
    }

    fun updateReceiverState(state: ServiceState) {
        _receiverState.value = state
    }

    fun startMicService() {
        _micState.value = ServiceState.Starting
        context.startForegroundService(Intent(context, MicStreamingService::class.java))
    }

    fun stopMicService() {
        _micState.value = ServiceState.Stopping
        context.stopService(Intent(context, MicStreamingService::class.java))
    }

    fun startCastService(resultCode: Int, data: Intent) {
        val intent = Intent(context, CastStreamingService::class.java).apply {
            putExtra(EXTRA_RESULT_CODE, resultCode)
            putExtra(EXTRA_RESULT_DATA, data)
        }
        context.startForegroundService(intent)
    }

    fun stopCastService() {
        _castState.value = ServiceState.Stopping
        context.stopService(Intent(context, CastStreamingService::class.java))
    }

    fun startReceiverService() {
        _receiverState.value = ServiceState.Starting
        context.startForegroundService(Intent(context, ReceiverService::class.java))
    }

    fun stopReceiverService() {
        _receiverState.value = ServiceState.Stopping
        context.stopService(Intent(context, ReceiverService::class.java))
    }

    companion object {
        const val MIC_CHANNEL_ID = "mic_service"
        const val CAST_CHANNEL_ID = "cast_service"
        const val RECEIVER_CHANNEL_ID = "receiver_service"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "com.audiolan.app.action.STOP_SERVICE"
    }
}
