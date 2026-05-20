package com.audiolan.app.di

import com.audiolan.app.data.repository.SettingsRepository
import com.audiolan.app.data.repository.SettingsRepositoryImpl
import com.audiolan.app.data.repository.StreamRepository
import com.audiolan.app.data.repository.StreamRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindStreamRepository(
        impl: StreamRepositoryImpl,
    ): StreamRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl,
    ): SettingsRepository
}
