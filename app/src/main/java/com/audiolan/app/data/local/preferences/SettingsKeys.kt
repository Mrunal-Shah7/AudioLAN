package com.audiolan.app.data.local.preferences

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey

object SettingsKeys {
    val ACCENT_COLOR = stringPreferencesKey("accent_color")
    val AMOLED_MODE = booleanPreferencesKey("amoled_mode")

    val TRANSMITTER_AUDIO_SOURCE = stringPreferencesKey("transmitter_audio_source")
    val TRANSMITTER_INPUT_CHANNEL = stringPreferencesKey("transmitter_input_channel")
    val TRANSMITTER_SAMPLE_RATE = intPreferencesKey("transmitter_sample_rate")
    val TRANSMITTER_ENCODING = intPreferencesKey("transmitter_encoding")
    val TRANSMITTER_BUFFER_SIZE = intPreferencesKey("transmitter_buffer_size")
    val TRANSMITTER_GLOBAL_VOLUME = floatPreferencesKey("transmitter_global_volume")

    // Legacy keys retained so transmitter settings can initialize from prior mic settings.
    val MIC_AUDIO_SOURCE = stringPreferencesKey("mic_audio_source")
    val MIC_INPUT_CHANNEL = stringPreferencesKey("mic_input_channel")
    val MIC_SAMPLE_RATE = intPreferencesKey("mic_sample_rate")
    val MIC_ENCODING = intPreferencesKey("mic_encoding")
    val MIC_BUFFER_SIZE = intPreferencesKey("mic_buffer_size")
    val MIC_GLOBAL_VOLUME = floatPreferencesKey("mic_global_volume")

    // Legacy cast keys retained for existing installs.
    val CAST_CHANNEL_OUT = stringPreferencesKey("cast_channel_out")
    val CAST_SAMPLE_RATE = intPreferencesKey("cast_sample_rate")
    val CAST_ENCODING = intPreferencesKey("cast_encoding")
    val CAST_BUFFER_SIZE = intPreferencesKey("cast_buffer_size")

    val RECEIVER_GLOBAL_VOLUME = floatPreferencesKey("receiver_global_volume")
}
