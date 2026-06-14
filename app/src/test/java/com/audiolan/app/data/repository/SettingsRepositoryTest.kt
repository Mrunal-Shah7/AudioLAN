package com.audiolan.app.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.audiolan.app.data.local.preferences.SettingsDataStore
import com.audiolan.app.domain.model.AccentColor
import com.audiolan.app.domain.model.ReceiverSettings
import com.audiolan.app.domain.model.TransmitterSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: TestScope
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        scope = TestScope(UnconfinedTestDispatcher())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { temporaryFolder.newFile("settings.preferences_pb") },
        )
        repository = SettingsRepositoryImpl(SettingsDataStore(dataStore))
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun defaultValuesAreEmittedBeforeSave() = runTest {
        val transmitterSettings = repository.getTransmitterSettings().first()

        assertEquals(TransmitterSettings(), transmitterSettings)
        assertEquals(AccentColor.LAVENDER, repository.getAccentColor().first())
        assertEquals(false, repository.getAmoledMode().first())
        assertEquals(ReceiverSettings(), repository.getReceiverSettings().first())
    }

    @Test
    fun saveAndRetrieveAccentColor() = runTest {
        repository.saveAccentColor(AccentColor.TEAL)

        assertEquals(AccentColor.TEAL, repository.getAccentColor().first())
    }

    @Test
    fun saveAndRetrieveAmoledMode() = runTest {
        repository.saveAmoledMode(true)

        assertEquals(true, repository.getAmoledMode().first())
    }

    @Test
    fun saveAndRetrieveTransmitterSettings() = runTest {
        val settings = TransmitterSettings(
            audioSource = "VOICE_COMM",
            inputChannel = "MONO",
            sampleRate = 44100,
            encoding = 16,
            bufferSize = 480,
            globalVolume = 0.5f,
        )

        repository.saveTransmitterSettings(settings)

        assertEquals(settings, repository.getTransmitterSettings().first())
    }

    @Test
    fun saveAndRetrieveReceiverSettings() = runTest {
        val settings = ReceiverSettings(globalVolume = 1.8f)

        repository.saveReceiverSettings(settings)

        assertEquals(settings, repository.getReceiverSettings().first())
    }

    @Test
    fun savingCopiedTransmitterSettingsRetainsOtherSavedFields() = runTest {
        val initial = TransmitterSettings(
            audioSource = "VOICE_COMM",
            inputChannel = "MONO",
            sampleRate = 44100,
            encoding = 16,
            bufferSize = 480,
            globalVolume = 0.5f,
        )
        repository.saveTransmitterSettings(initial)

        repository.saveTransmitterSettings(initial.copy(globalVolume = 1.7f))
        val result = repository.getTransmitterSettings().first()

        assertEquals("VOICE_COMM", result.audioSource)
        assertEquals("MONO", result.inputChannel)
        assertEquals(44100, result.sampleRate)
        assertEquals(16, result.encoding)
        assertEquals(480, result.bufferSize)
        assertEquals(1.7f, result.globalVolume, 0.0001f)
    }
}
