package com.audiolan.app.data.repository

import com.audiolan.app.data.local.preferences.SettingsDataStore
import com.audiolan.app.domain.model.AccentColor
import com.audiolan.app.domain.model.CastSettings
import com.audiolan.app.domain.model.MicSettings
import com.audiolan.app.domain.model.ReceiverSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getAccentColor(): Flow<AccentColor>
    fun getAmoledMode(): Flow<Boolean>
    fun getMicSettings(): Flow<MicSettings>
    fun getCastSettings(): Flow<CastSettings>
    fun getReceiverSettings(): Flow<ReceiverSettings>
    suspend fun saveMicSettings(settings: MicSettings)
    suspend fun saveCastSettings(settings: CastSettings)
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

    override fun getMicSettings(): Flow<MicSettings> = settingsDataStore.micSettings

    override fun getCastSettings(): Flow<CastSettings> = settingsDataStore.castSettings

    override fun getReceiverSettings(): Flow<ReceiverSettings> = settingsDataStore.receiverSettings

    override suspend fun saveMicSettings(settings: MicSettings) {
        settingsDataStore.saveMicSettings(settings)
    }

    override suspend fun saveCastSettings(settings: CastSettings) {
        settingsDataStore.saveCastSettings(settings)
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
