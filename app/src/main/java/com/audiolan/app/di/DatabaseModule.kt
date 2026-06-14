package com.audiolan.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.audiolan.app.data.local.db.AppDatabase
import com.audiolan.app.data.local.db.dao.StreamDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "audiolan.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    @Singleton
    fun provideStreamDao(database: AppDatabase): StreamDao =
        database.streamDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings.preferences_pb") },
        )

}

private object MIGRATION_1_2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE streams ADD COLUMN transport_mode TEXT NOT NULL DEFAULT 'WIFI'")
        db.execSQL("ALTER TABLE streams ADD COLUMN low_latency INTEGER NOT NULL DEFAULT 0")
    }
}

object MIGRATION_2_3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE streams ADD COLUMN source_type TEXT NOT NULL DEFAULT 'MIC'")
        db.execSQL("ALTER TABLE streams ADD COLUMN broadcast_mode INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE streams SET service_type = 'TRANSMITTER' " +
                "WHERE service_type = 'MIC' OR service_type = 'CAST'",
        )
    }
}
