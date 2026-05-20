package com.audiolan.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.audiolan.app.domain.model.AccentColor
import com.audiolan.app.domain.model.CastSettings
import com.audiolan.app.domain.model.MicSettings
import com.audiolan.app.domain.model.ReceiverSettings
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

    val micSettings: Flow<MicSettings> = dataStore.data.map { preferences ->
        val defaults = MicSettings()
        MicSettings(
            audioSource = preferences[SettingsKeys.MIC_AUDIO_SOURCE] ?: defaults.audioSource,
            inputChannel = preferences[SettingsKeys.MIC_INPUT_CHANNEL] ?: defaults.inputChannel,
            sampleRate = preferences[SettingsKeys.MIC_SAMPLE_RATE] ?: defaults.sampleRate,
            encoding = preferences[SettingsKeys.MIC_ENCODING] ?: defaults.encoding,
            bufferSize = preferences[SettingsKeys.MIC_BUFFER_SIZE] ?: defaults.bufferSize,
            globalVolume = preferences[SettingsKeys.MIC_GLOBAL_VOLUME] ?: defaults.globalVolume,
        )
    }

    val castSettings: Flow<CastSettings> = dataStore.data.map { preferences ->
        val defaults = CastSettings()
        CastSettings(
            channelOut = preferences[SettingsKeys.CAST_CHANNEL_OUT] ?: defaults.channelOut,
            sampleRate = preferences[SettingsKeys.CAST_SAMPLE_RATE] ?: defaults.sampleRate,
            encoding = preferences[SettingsKeys.CAST_ENCODING] ?: defaults.encoding,
            bufferSize = preferences[SettingsKeys.CAST_BUFFER_SIZE] ?: defaults.bufferSize,
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

    suspend fun saveMicSettings(settings: MicSettings) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.MIC_AUDIO_SOURCE] = settings.audioSource
            preferences[SettingsKeys.MIC_INPUT_CHANNEL] = settings.inputChannel
            preferences[SettingsKeys.MIC_SAMPLE_RATE] = settings.sampleRate
            preferences[SettingsKeys.MIC_ENCODING] = settings.encoding
            preferences[SettingsKeys.MIC_BUFFER_SIZE] = settings.bufferSize
            preferences[SettingsKeys.MIC_GLOBAL_VOLUME] = settings.globalVolume
        }
    }

    suspend fun saveCastSettings(settings: CastSettings) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.CAST_CHANNEL_OUT] = settings.channelOut
            preferences[SettingsKeys.CAST_SAMPLE_RATE] = settings.sampleRate
            preferences[SettingsKeys.CAST_ENCODING] = settings.encoding
            preferences[SettingsKeys.CAST_BUFFER_SIZE] = settings.bufferSize
        }
    }

    suspend fun saveReceiverSettings(settings: ReceiverSettings) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.RECEIVER_GLOBAL_VOLUME] = settings.globalVolume
        }
    }
}
