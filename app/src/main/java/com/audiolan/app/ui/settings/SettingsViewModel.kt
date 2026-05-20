package com.audiolan.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiolan.app.data.repository.SettingsRepository
import com.audiolan.app.domain.model.AccentColor
import com.audiolan.app.domain.model.CastSettings
import com.audiolan.app.domain.model.MicSettings
import com.audiolan.app.domain.model.ReceiverSettings
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

    val micSettings: StateFlow<MicSettings> = settingsRepository.getMicSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MicSettings())

    val castSettings: StateFlow<CastSettings> = settingsRepository.getCastSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CastSettings())

    val receiverSettings: StateFlow<ReceiverSettings> = settingsRepository.getReceiverSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiverSettings())

    fun setMicAudioSource(value: String) {
        viewModelScope.launch {
            settingsRepository.saveMicSettings(micSettings.value.copy(audioSource = value))
        }
    }

    fun setMicInputChannel(value: String) {
        viewModelScope.launch {
            settingsRepository.saveMicSettings(micSettings.value.copy(inputChannel = value))
        }
    }

    fun setMicSampleRate(value: Int) {
        viewModelScope.launch {
            settingsRepository.saveMicSettings(micSettings.value.copy(sampleRate = value))
        }
    }

    fun setMicBufferSize(value: Int) {
        viewModelScope.launch {
            settingsRepository.saveMicSettings(micSettings.value.copy(bufferSize = value))
        }
    }

    fun setMicGlobalVolume(value: Float) {
        viewModelScope.launch {
            settingsRepository.saveMicSettings(micSettings.value.copy(globalVolume = value))
        }
    }

    fun resetMicGlobalVolume() {
        setMicGlobalVolume(1.0f)
    }

    fun setCastChannelOut(value: String) {
        viewModelScope.launch {
            settingsRepository.saveCastSettings(castSettings.value.copy(channelOut = value))
        }
    }

    fun setCastSampleRate(value: Int) {
        viewModelScope.launch {
            settingsRepository.saveCastSettings(castSettings.value.copy(sampleRate = value))
        }
    }

    fun setCastBufferSize(value: Int) {
        viewModelScope.launch {
            settingsRepository.saveCastSettings(castSettings.value.copy(bufferSize = value))
        }
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
