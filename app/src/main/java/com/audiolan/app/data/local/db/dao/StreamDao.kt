package com.audiolan.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.audiolan.app.data.local.db.entity.StreamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamDao {
    // Services collect this Flow to diff and reconcile running coroutines.
    @Query("SELECT * FROM streams WHERE service_type = :type ORDER BY name ASC")
    fun getStreamsByType(type: String): Flow<List<StreamEntity>>

    @Query("SELECT * FROM streams WHERE is_enabled = 1 AND service_type = :type")
    suspend fun getEnabledStreamsByType(type: String): List<StreamEntity>

    @Query("SELECT * FROM streams WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): StreamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stream: StreamEntity): Long

    @Delete
    suspend fun delete(stream: StreamEntity)

    @Query("UPDATE streams SET is_enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE streams SET volume = :volume WHERE id = :id")
    suspend fun setVolume(id: Long, volume: Float)
}
