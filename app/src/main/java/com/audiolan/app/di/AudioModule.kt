package com.audiolan.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    // AudioRecord and AudioTrack are created per stream inside services because
    // sample rate, channel config, and buffer sizes are only known at runtime.
    // This module is reserved for future audio-session-level bindings.
}
