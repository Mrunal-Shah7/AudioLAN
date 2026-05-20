package com.audiolan.app.data.repository

import com.audiolan.app.domain.model.DiscoveredDevice
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class DiscoveredDevicesRepository @Inject constructor() {
    @Volatile
    private var mutableDevices = newDeviceFlow()

    val devices: SharedFlow<DiscoveredDevice>
        get() = mutableDevices.asSharedFlow()

    fun emit(device: DiscoveredDevice) {
        mutableDevices.tryEmit(device)
    }

    fun clear() {
        mutableDevices = newDeviceFlow()
    }

    private fun newDeviceFlow(): MutableSharedFlow<DiscoveredDevice> =
        MutableSharedFlow(replay = 20, extraBufferCapacity = 50)
}
