package com.audiolan.app.data.local.preferences

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey

object SettingsKeys {
    val ACCENT_COLOR = stringPreferencesKey("accent_color")
    val AMOLED_MODE = booleanPreferencesKey("amoled_mode")

    val MIC_AUDIO_SOURCE = stringPreferencesKey("mic_audio_source")
    val MIC_INPUT_CHANNEL = stringPreferencesKey("mic_input_channel")
    val MIC_SAMPLE_RATE = intPreferencesKey("mic_sample_rate")
    val MIC_ENCODING = intPreferencesKey("mic_encoding")
    val MIC_BUFFER_SIZE = intPreferencesKey("mic_buffer_size")
    val MIC_GLOBAL_VOLUME = floatPreferencesKey("mic_global_volume")

    val CAST_CHANNEL_OUT = stringPreferencesKey("cast_channel_out")
    val CAST_SAMPLE_RATE = intPreferencesKey("cast_sample_rate")
    val CAST_ENCODING = intPreferencesKey("cast_encoding")
    val CAST_BUFFER_SIZE = intPreferencesKey("cast_buffer_size")

    val RECEIVER_GLOBAL_VOLUME = floatPreferencesKey("receiver_global_volume")
}
