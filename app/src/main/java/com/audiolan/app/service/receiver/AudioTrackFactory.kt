package com.audiolan.app.service.receiver

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

object AudioTrackFactory {
    fun create(sampleRateHz: Int, numChannels: Int): AudioTrack {
        val channelOut = when (numChannels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            else -> AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRateHz,
            channelOut,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
            throw IllegalArgumentException("Unsupported AudioTrack config: $sampleRateHz Hz, $numChannels ch")
        }

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(channelOut)
                    .build(),
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }
}
