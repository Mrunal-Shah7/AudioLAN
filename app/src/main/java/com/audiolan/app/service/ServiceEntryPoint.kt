package com.audiolan.app.service

import com.audiolan.app.data.repository.DiscoveredDevicesRepository
import com.audiolan.app.data.repository.SettingsRepository
import com.audiolan.app.data.repository.StreamRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceEntryPoint {
    fun serviceManager(): ServiceManager
    fun streamRepository(): StreamRepository
    fun settingsRepository(): SettingsRepository
    fun discoveredDevicesRepository(): DiscoveredDevicesRepository
}
