package com.audiolan.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.audiolan.app.domain.model.AccentColor
import com.audiolan.app.domain.model.ReceiverSettings
import com.audiolan.app.domain.model.TransmitterSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val accentColor: Flow<AccentColor> = dataStore.data.map { preferences ->
        val name = preferences[SettingsKeys.ACCENT_COLOR] ?: AccentColor.LAVENDER.name
        runCatching { AccentColor.valueOf(name) }.getOrDefault(AccentColor.LAVENDER)
    }

    val amoledMode: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SettingsKeys.AMOLED_MODE] ?: false
    }

    val transmitterSettings: Flow<TransmitterSettings> = dataStore.data.map { preferences ->
        val defaults = TransmitterSettings()
        TransmitterSettings(
            audioSource = preferences[SettingsKeys.TRANSMITTER_AUDIO_SOURCE]
                ?: preferences[SettingsKeys.MIC_AUDIO_SOURCE]
                ?: defaults.audioSource,
            inputChannel = preferences[SettingsKeys.TRANSMITTER_INPUT_CHANNEL]
                ?: preferences[SettingsKeys.MIC_INPUT_CHANNEL]
                ?: defaults.inputChannel,
            sampleRate = preferences[SettingsKeys.TRANSMITTER_SAMPLE_RATE]
                ?: preferences[SettingsKeys.MIC_SAMPLE_RATE]
                ?: defaults.sampleRate,
            encoding = preferences[SettingsKeys.TRANSMITTER_ENCODING]
                ?: preferences[SettingsKeys.MIC_ENCODING]
                ?: defaults.encoding,
            bufferSize = preferences[SettingsKeys.TRANSMITTER_BUFFER_SIZE]
                ?: preferences[SettingsKeys.MIC_BUFFER_SIZE]
                ?: defaults.bufferSize,
            globalVolume = preferences[SettingsKeys.TRANSMITTER_GLOBAL_VOLUME]
                ?: preferences[SettingsKeys.MIC_GLOBAL_VOLUME]
                ?: defaults.globalVolume,
        )
    }

    val receiverSettings: Flow<ReceiverSettings> = dataStore.data.map { preferences ->
        val defaults = ReceiverSettings()
        ReceiverSettings(
            globalVolume = preferences[SettingsKeys.RECEIVER_GLOBAL_VOLUME] ?: defaults.globalVolume,
        )
    }

    suspend fun saveAccentColor(color: AccentColor) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.ACCENT_COLOR] = color.name
        }
    }

    suspend fun saveAmoledMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.AMOLED_MODE] = enabled
        }
    }

    suspend fun saveTransmitterSettings(settings: TransmitterSettings) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.TRANSMITTER_AUDIO_SOURCE] = settings.audioSource
            preferences[SettingsKeys.TRANSMITTER_INPUT_CHANNEL] = settings.inputChannel
            preferences[SettingsKeys.TRANSMITTER_SAMPLE_RATE] = settings.sampleRate
            preferences[SettingsKeys.TRANSMITTER_ENCODING] = settings.encoding
            preferences[SettingsKeys.TRANSMITTER_BUFFER_SIZE] = settings.bufferSize
            preferences[SettingsKeys.TRANSMITTER_GLOBAL_VOLUME] = settings.globalVolume
        }
    }

    suspend fun saveReceiverSettings(settings: ReceiverSettings) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.RECEIVER_GLOBAL_VOLUME] = settings.globalVolume
        }
    }
}
