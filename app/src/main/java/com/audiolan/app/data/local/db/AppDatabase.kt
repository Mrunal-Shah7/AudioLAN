package com.audiolan.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.audiolan.app.data.local.db.dao.StreamDao
import com.audiolan.app.data.local.db.entity.StreamEntity

@Database(
    entities = [StreamEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun streamDao(): StreamDao
}
