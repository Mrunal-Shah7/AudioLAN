package com.audiolan.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // DatagramSocket instances are created per stream inside services because
    // destination host and port are stream-specific runtime values.
    // This module is reserved for future network-level singleton bindings.
}
