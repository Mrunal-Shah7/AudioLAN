package com.audiolan.app.data.repository

import com.audiolan.app.data.local.db.dao.StreamDao
import com.audiolan.app.data.local.db.entity.toDomain
import com.audiolan.app.data.local.db.entity.toEntity
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface StreamRepository {
    fun getStreamsByType(type: ServiceType): Flow<List<Stream>>
    suspend fun getEnabledStreamsByType(type: ServiceType): List<Stream>
    suspend fun getById(id: Long): Stream?
    suspend fun insertOrUpdate(stream: Stream): Long
    suspend fun delete(stream: Stream)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun setVolume(id: Long, volume: Float)
}

@Singleton
class StreamRepositoryImpl @Inject constructor(
    private val dao: StreamDao,
) : StreamRepository {
    override fun getStreamsByType(type: ServiceType): Flow<List<Stream>> =
        dao.getStreamsByType(type.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getEnabledStreamsByType(type: ServiceType): List<Stream> =
        dao.getEnabledStreamsByType(type.name).map { it.toDomain() }

    override suspend fun getById(id: Long): Stream? =
        dao.getById(id)?.toDomain()

    override suspend fun insertOrUpdate(stream: Stream): Long =
        dao.insertOrUpdate(stream.toEntity())

    override suspend fun delete(stream: Stream) {
        dao.delete(stream.toEntity())
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }

    override suspend fun setVolume(id: Long, volume: Float) {
        dao.setVolume(id, volume)
    }
}
