package com.audiolan.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiolan.app.data.repository.SettingsRepository
import com.audiolan.app.domain.model.AccentColor
import com.audiolan.app.domain.model.ReceiverSettings
import com.audiolan.app.domain.model.TransmitterSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val accentColor: StateFlow<AccentColor> = settingsRepository.getAccentColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccentColor.LAVENDER)

    val amoledMode: StateFlow<Boolean> = settingsRepository.getAmoledMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val transmitterSettings: StateFlow<TransmitterSettings> = settingsRepository.getTransmitterSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransmitterSettings())

    val receiverSettings: StateFlow<ReceiverSettings> = settingsRepository.getReceiverSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiverSettings())

    fun setTransmitterAudioSource(value: String) {
        viewModelScope.launch {
            settingsRepository.saveTransmitterSettings(transmitterSettings.value.copy(audioSource = value))
        }
    }

    fun setTransmitterInputChannel(value: String) {
        viewModelScope.launch {
            settingsRepository.saveTransmitterSettings(transmitterSettings.value.copy(inputChannel = value))
        }
    }

    fun setTransmitterSampleRate(value: Int) {
        viewModelScope.launch {
            settingsRepository.saveTransmitterSettings(transmitterSettings.value.copy(sampleRate = value))
        }
    }

    fun setTransmitterBufferSize(value: Int) {
        viewModelScope.launch {
            settingsRepository.saveTransmitterSettings(transmitterSettings.value.copy(bufferSize = value))
        }
    }

    fun setTransmitterGlobalVolume(value: Float) {
        viewModelScope.launch {
            settingsRepository.saveTransmitterSettings(transmitterSettings.value.copy(globalVolume = value))
        }
    }

    fun resetTransmitterGlobalVolume() {
        setTransmitterGlobalVolume(1.0f)
    }

    fun setReceiverGlobalVolume(value: Float) {
        viewModelScope.launch {
            settingsRepository.saveReceiverSettings(receiverSettings.value.copy(globalVolume = value))
        }
    }

    fun resetReceiverGlobalVolume() {
        setReceiverGlobalVolume(1.0f)
    }

    fun setAccentColor(color: AccentColor) {
        viewModelScope.launch {
            settingsRepository.saveAccentColor(color)
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveAmoledMode(enabled)
        }
    }
}
