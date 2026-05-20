package com.audiolan.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    // ServiceManager is provided automatically as a @Singleton @Inject constructor class.
    // This module is reserved for future service-layer bindings (Sprint 9+).
}
