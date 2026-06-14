package com.audiolan.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.audiolan.app.data.local.db.AppDatabase
import com.audiolan.app.domain.model.NetQuality
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.SourceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.domain.model.TransportMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StreamRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: StreamRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StreamRepositoryImpl(database.streamDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveReturnsInsertedStream() = runTest {
        val stream = stream(serviceType = ServiceType.TRANSMITTER, name = "Mic One")

        val id = repository.insertOrUpdate(stream)
        val result = repository.getStreamsByType(ServiceType.TRANSMITTER).first().single()

        assertEquals(id, result.id)
        assertEquals(ServiceType.TRANSMITTER, result.serviceType)
        assertEquals("Mic One", result.name)
        assertEquals("192.168.1.10", result.host)
        assertEquals(6980, result.port)
        assertEquals(NetQuality.OPTIMAL, result.netQuality)
        assertEquals(TransportMode.WIFI, result.transportMode)
        assertEquals(false, result.lowLatency)
        assertEquals(SourceType.MIC, result.sourceType)
        assertEquals(false, result.broadcastMode)
        assertEquals(1.0f, result.volume, 0.0001f)
        assertEquals(true, result.isEnabled)
    }

    @Test
    fun serviceTypeFilterOnlyReturnsMatchingStreams() = runTest {
        repository.insertOrUpdate(stream(serviceType = ServiceType.TRANSMITTER, name = "Mic"))
        repository.insertOrUpdate(stream(serviceType = ServiceType.RECEIVER, name = "Receiver"))

        val result = repository.getStreamsByType(ServiceType.TRANSMITTER).first()

        assertEquals(listOf("Mic"), result.map { it.name })
    }

    @Test
    fun getByIdReturnsMatchingStream() = runTest {
        val firstId = repository.insertOrUpdate(stream(name = "First"))
        repository.insertOrUpdate(stream(name = "Second"))

        val result = repository.getById(firstId)

        assertEquals("First", result?.name)
        assertEquals(firstId, result?.id)
    }

    @Test
    fun getByIdReturnsNullForNonExistentId() = runTest {
        val result = repository.getById(999_999L)

        assertEquals(null, result)
    }

    @Test
    fun insertOrUpdateReplacesExistingStreamWithSameId() = runTest {
        val id = repository.insertOrUpdate(stream(name = "Original", host = "192.168.1.10"))

        repository.insertOrUpdate(stream(id = id, name = "Original", host = "192.168.1.20"))
        val result = repository.getById(id)

        assertEquals("Original", result?.name)
        assertEquals("192.168.1.20", result?.host)
    }

    @Test
    fun setEnabledUpdatesStream() = runTest {
        val id = repository.insertOrUpdate(stream(isEnabled = true))

        repository.setEnabled(id, false)
        val result = repository.getStreamsByType(ServiceType.TRANSMITTER).first().single()

        assertFalse(result.isEnabled)
    }

    @Test
    fun setVolumeUpdatesStream() = runTest {
        val id = repository.insertOrUpdate(stream(volume = 1.0f))

        repository.setVolume(id, 1.5f)
        val result = repository.getStreamsByType(ServiceType.TRANSMITTER).first().single()

        assertEquals(1.5f, result.volume, 0.0001f)
    }

    @Test
    fun deleteRemovesOnlyRequestedStream() = runTest {
        val firstId = repository.insertOrUpdate(stream(name = "First"))
        repository.insertOrUpdate(stream(name = "Second"))

        repository.delete(stream(id = firstId, name = "First"))
        val result = repository.getStreamsByType(ServiceType.TRANSMITTER).first()

        assertEquals(listOf("Second"), result.map { it.name })
    }

    @Test
    fun streamsAreOrderedByName() = runTest {
        repository.insertOrUpdate(stream(name = "Zeta"))
        repository.insertOrUpdate(stream(name = "Alpha"))
        repository.insertOrUpdate(stream(name = "Mu"))

        val result = repository.getStreamsByType(ServiceType.TRANSMITTER).first()

        assertEquals(listOf("Alpha", "Mu", "Zeta"), result.map { it.name })
    }

    @Test
    fun flowEmitsAgainWhenStreamChanges() = runBlocking {
        val emissions = Channel<List<Stream>>(Channel.UNLIMITED)
        val job = launch {
            repository.getStreamsByType(ServiceType.TRANSMITTER).collect {
                emissions.send(it)
            }
        }

        val id = repository.insertOrUpdate(stream(isEnabled = true))
        val inserted = receiveUntil(emissions) { it.singleOrNull()?.isEnabled == true }
        repository.setEnabled(id, false)
        val updated = receiveUntil(emissions) { it.singleOrNull()?.isEnabled == false }
        job.cancel()

        assertEquals(true, inserted.single().isEnabled)
        assertEquals(false, updated.single().isEnabled)
    }

    @Test
    fun flowEmitsAgainWhenVolumeChanges() = runBlocking {
        val emissions = Channel<List<Stream>>(Channel.UNLIMITED)
        val job = launch {
            repository.getStreamsByType(ServiceType.TRANSMITTER).collect {
                emissions.send(it)
            }
        }

        val id = repository.insertOrUpdate(stream(volume = 1.0f))
        val inserted = receiveUntil(emissions) { it.singleOrNull()?.volume == 1.0f }
        repository.setVolume(id, 1.75f)
        val updated = receiveUntil(emissions) { it.singleOrNull()?.volume == 1.75f }
        job.cancel()

        assertEquals(1.0f, inserted.single().volume, 0.0001f)
        assertEquals(1.75f, updated.single().volume, 0.0001f)
    }

    private fun stream(
        id: Long = 0,
        serviceType: ServiceType = ServiceType.TRANSMITTER,
        name: String = "Stream",
        host: String = "192.168.1.10",
        port: Int = 6980,
        netQuality: NetQuality = NetQuality.OPTIMAL,
        transportMode: TransportMode = TransportMode.WIFI,
        lowLatency: Boolean = false,
        sourceType: SourceType = SourceType.MIC,
        broadcastMode: Boolean = false,
        volume: Float = 1.0f,
        isEnabled: Boolean = true,
    ): Stream =
        Stream(
            id = id,
            serviceType = serviceType,
            name = name,
            host = host,
            port = port,
            netQuality = netQuality,
            transportMode = transportMode,
            lowLatency = lowLatency,
            sourceType = sourceType,
            broadcastMode = broadcastMode,
            volume = volume,
            isEnabled = isEnabled,
        )

    private suspend fun receiveUntil(
        channel: Channel<List<Stream>>,
        predicate: (List<Stream>) -> Boolean,
    ): List<Stream> =
        withTimeout(5_000) {
            while (true) {
                val value = channel.receive()
                if (predicate(value)) {
                    return@withTimeout value
                }
            }
            error("unreachable")
        }
}
