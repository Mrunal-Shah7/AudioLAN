package com.audiolan.app.ui.home

import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.service.ServiceManager
import com.audiolan.app.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serviceManager: ServiceManager,
) : ViewModel() {
    val micState: StateFlow<ServiceState> = serviceManager.micState
    val castState: StateFlow<ServiceState> = serviceManager.castState
    val receiverState: StateFlow<ServiceState> = serviceManager.receiverState

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
            merge(micState, castState, receiverState).collect { state ->
                if (state is ServiceState.Error) {
                    snackbarHostState.showSnackbar(
                        message = state.message,
                        duration = SnackbarDuration.Long,
                    )
                }
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
        if (granted) {
            serviceManager.startMicService()
        }
    }

    fun onMicPermissionDenied() {
        _hasMicPermission.value = false
        Timber.w("RECORD_AUDIO permission denied by user")
    }

    fun stopMic() = serviceManager.stopMicService()

    fun startReceiver() = serviceManager.startReceiverService()

    fun stopReceiver() = serviceManager.stopReceiverService()

    fun startCast(resultCode: Int, data: Intent) {
        serviceManager.startCastService(resultCode, data)
    }

    fun onCastConsentDenied() {
        Timber.d("Cast MediaProjection consent denied by user")
    }

    fun stopCast() = serviceManager.stopCastService()
}
