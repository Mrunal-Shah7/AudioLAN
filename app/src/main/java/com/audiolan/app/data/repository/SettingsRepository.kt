package com.audiolan.app.data.repository

import com.audiolan.app.data.local.preferences.SettingsDataStore
import com.audiolan.app.domain.model.AccentColor
import com.audiolan.app.domain.model.ReceiverSettings
import com.audiolan.app.domain.model.TransmitterSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getAccentColor(): Flow<AccentColor>
    fun getAmoledMode(): Flow<Boolean>
    fun getTransmitterSettings(): Flow<TransmitterSettings>
    fun getReceiverSettings(): Flow<ReceiverSettings>
    suspend fun saveTransmitterSettings(settings: TransmitterSettings)
    suspend fun saveReceiverSettings(settings: ReceiverSettings)
    suspend fun saveAccentColor(color: AccentColor)
    suspend fun saveAmoledMode(enabled: Boolean)
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : SettingsRepository {
    override fun getAccentColor(): Flow<AccentColor> = settingsDataStore.accentColor

    override fun getAmoledMode(): Flow<Boolean> = settingsDataStore.amoledMode

    override fun getTransmitterSettings(): Flow<TransmitterSettings> = settingsDataStore.transmitterSettings

    override fun getReceiverSettings(): Flow<ReceiverSettings> = settingsDataStore.receiverSettings

    override suspend fun saveTransmitterSettings(settings: TransmitterSettings) {
        settingsDataStore.saveTransmitterSettings(settings)
    }

    override suspend fun saveReceiverSettings(settings: ReceiverSettings) {
        settingsDataStore.saveReceiverSettings(settings)
    }

    override suspend fun saveAccentColor(color: AccentColor) {
        settingsDataStore.saveAccentColor(color)
    }

    override suspend fun saveAmoledMode(enabled: Boolean) {
        settingsDataStore.saveAmoledMode(enabled)
    }
}
