package com.audiolan.app.service.transmitter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.audiolan.app.R
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.SourceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.domain.model.StreamError
import com.audiolan.app.domain.model.StreamRuntimeStatus
import com.audiolan.app.domain.model.TransmitterSettings
import com.audiolan.app.domain.vban.VbanNetQuality
import com.audiolan.app.domain.vban.VbanPacketizer
import com.audiolan.app.service.ServiceManager
import com.audiolan.app.service.base.BaseStreamingService
import com.audiolan.app.util.AudioUtils
import com.audiolan.app.util.NetworkUtils
import com.audiolan.app.util.RetryUtils
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import timber.log.Timber

class TransmitterService : BaseStreamingService() {
    private var micAudioRecord: AudioRecord? = null
    private var internalAudioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var ignoreMediaProjectionStopCallback = false
    private var numChannels: Int = 2
    private var sampleRate: Int = 48_000
    private var bufferSize: Int = 960
    private var streamManagementStarted = false
    private var transmitterManagementJob: Job? = null
    private var transmitterSettingsJob: Job? = null
    private var networkMonitorJob: Job? = null
    private val sourceReaderJobs = mutableMapOf<SourceType, Job>()
    private val streamConsumers = mutableMapOf<Long, TransmitterConsumer>()
    private val transmitterStreamVolumes = ConcurrentHashMap<Long, Float>()
    private val fanOutLock = Any()
    @Volatile
    private var transmitterGlobalVolume: Float = 1.0f

    override fun serviceType(): ServiceType = ServiceType.TRANSMITTER

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ServiceManager.ACTION_STOP) {
            serviceManager.stopTransmitterService()
            return START_NOT_STICKY
        }

        serviceManager.updateTransmitterState(ServiceState.Starting)
        val startupContext = try {
            prepareForegroundStartup(intent)
        } catch (e: Exception) {
            Timber.e(e, "TransmitterService foreground startup failed")
            serviceManager.updateTransmitterState(ServiceState.Error("Transmitter start failed: ${e.message}"))
            stopSelf()
            return START_NOT_STICKY
        } ?: return START_NOT_STICKY

        serviceScope.launch {
            try {
                val settings = settingsRepository.getTransmitterSettings().first()
                val streams = streamRepository.getStreamsByType(ServiceType.TRANSMITTER).first()
                    .filter { it.isEnabled }
                if (streams.isEmpty()) {
                    Timber.w("TransmitterService started with no enabled streams")
                    serviceManager.updateTransmitterState(ServiceState.Error("No enabled transmitter streams"))
                    stopSelf()
                    return@launch
                }
                val duplicateNames = duplicateStreamNames(streams)
                if (duplicateNames.isNotEmpty()) {
                    Timber.w("TransmitterService duplicate stream names: ${duplicateNames.joinToString()}")
                    serviceManager.updateTransmitterState(
                        ServiceState.Error("Duplicate transmitter stream name: ${duplicateNames.joinToString()}"),
                    )
                    stopSelf()
                    return@launch
                }

                val hasMicStreams = streams.any { it.sourceType == SourceType.MIC }
                val hasInternalAudioStreams = streams.any { it.sourceType == SourceType.INTERNAL_AUDIO }
                transmitterGlobalVolume = settings.globalVolume

                if (hasMicStreams) {
                    if (!startupContext.hasMicStreams) {
                        throw IllegalStateException("Microphone stream added after transmitter startup")
                    }
                    initialiseMicAudio(settings)
                    startRecorder(micAudioRecord, "microphone")
                }
                if (hasInternalAudioStreams) {
                    if (!startupContext.hasInternalAudioStreams || startupContext.mediaProjection == null) {
                        throw IllegalStateException("Internal audio stream missing MediaProjection startup context")
                    }
                    initialiseInternalAudio(settings, startupContext.mediaProjection)
                    startRecorder(internalAudioRecord, "internal audio")
                }

                serviceManager.updateTransmitterState(ServiceState.Running)
                updateNotification("streaming")
                if (!streamManagementStarted) {
                    streamManagementStarted = true
                    startTransmitterStreamManagement(settings)
                }
                if (transmitterSettingsJob == null) {
                    transmitterSettingsJob = launch {
                        settingsRepository.getTransmitterSettings().collect { latestSettings ->
                            transmitterGlobalVolume = latestSettings.globalVolume
                        }
                    }
                }
                startNetworkMonitor()
            } catch (e: Exception) {
                Timber.e(e, "TransmitterService failed to initialise")
                serviceManager.updateTransmitterState(ServiceState.Error("Transmitter init failed: ${e.message}"))
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun prepareForegroundStartup(intent: Intent?): ForegroundStartupContext? {
        val hasMicStreams = intent?.getBooleanExtra(ServiceManager.EXTRA_HAS_MIC_STREAMS, false) ?: false
        val hasInternalAudioStreams =
            intent?.getBooleanExtra(ServiceManager.EXTRA_HAS_INTERNAL_AUDIO_STREAMS, false) ?: false

        if (!hasMicStreams && !hasInternalAudioStreams) {
            Timber.w("TransmitterService started with no source flags")
            serviceManager.updateTransmitterState(ServiceState.Error("No enabled transmitter streams"))
            stopSelf()
            return null
        }

        val resultCode = intent?.getIntExtra(ServiceManager.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.resultData()

        if (hasMicStreams) {
            assertRecordAudioPermission()
        }

        if (hasInternalAudioStreams && (resultCode != Activity.RESULT_OK || resultData == null)) {
            Timber.e("TransmitterService: MediaProjection consent missing")
            serviceManager.updateTransmitterState(ServiceState.Error("MediaProjection consent missing"))
            stopSelf()
            return null
        }

        promoteToForeground(hasMicStreams, hasInternalAudioStreams)
        acquireWakeLock("AudioLAN::TransmitterService")

        val projection = if (hasInternalAudioStreams) {
            obtainMediaProjection(resultCode, resultData ?: error("MediaProjection resultData unexpectedly null"))
        } else {
            null
        }

        return ForegroundStartupContext(
            hasMicStreams = hasMicStreams,
            hasInternalAudioStreams = hasInternalAudioStreams,
            mediaProjection = projection,
        )
    }

    @SuppressLint("InlinedApi")
    private fun promoteToForeground(hasMicStreams: Boolean, hasInternalAudioStreams: Boolean) {
        var foregroundType = 0
        if (hasMicStreams) {
            foregroundType = foregroundType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (hasInternalAudioStreams) {
            foregroundType = foregroundType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("starting..."),
            foregroundType,
        )
    }

    private fun assertRecordAudioPermission() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }
    }

    @SuppressLint("MissingPermission")
    private fun initialiseMicAudio(settings: TransmitterSettings) {
        val audioSource = when (settings.audioSource) {
            "VOICE_COMM" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            else -> MediaRecorder.AudioSource.DEFAULT
        }
        val channelConfig = when (settings.inputChannel) {
            "MONO" -> AudioFormat.CHANNEL_IN_MONO
            else -> AudioFormat.CHANNEL_IN_STEREO
        }
        numChannels = if (settings.inputChannel == "MONO") 1 else 2
        sampleRate = settings.sampleRate
        bufferSize = settings.bufferSize

        val actualBufferSize = resolveBufferSize(channelConfig, "microphone")
        val record = AudioRecord(
            audioSource,
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            actualBufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Microphone AudioRecord failed to initialise")
        }
        micAudioRecord = record
        Timber.d(
            "Mic audio initialised sampleRate=$sampleRate channels=$numChannels bufferSize=$actualBufferSize",
        )
    }

    private fun obtainMediaProjection(resultCode: Int, resultData: Intent): MediaProjection {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
            ?: throw IllegalStateException("MediaProjection token rejected")
        ignoreMediaProjectionStopCallback = false
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                if (ignoreMediaProjectionStopCallback) {
                    Timber.d("MediaProjection stopped during transmitter teardown")
                    return
                }
                Timber.w("MediaProjection stopped by system")
                serviceManager.updateTransmitterState(ServiceState.Error("MediaProjection stopped"))
                stopSelf()
            }
        }
        projection.registerCallback(callback, Handler(Looper.getMainLooper()))
        mediaProjectionCallback = callback
        mediaProjection = projection
        return projection
    }

    private fun initialiseInternalAudio(settings: TransmitterSettings, projection: MediaProjection) {
        val channelMask = when (settings.inputChannel) {
            "MONO" -> AudioFormat.CHANNEL_IN_MONO
            else -> AudioFormat.CHANNEL_IN_STEREO
        }
        numChannels = if (settings.inputChannel == "MONO") 1 else 2
        sampleRate = settings.sampleRate
        bufferSize = settings.bufferSize

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        val actualBufferSize = resolveBufferSize(channelMask, "internal audio")
        val record = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(actualBufferSize)
            .build()

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            releaseMediaProjection()
            throw IllegalStateException("AudioPlaybackCapture AudioRecord failed to initialise")
        }
        internalAudioRecord = record
        Timber.d(
            "Internal audio initialised sampleRate=$sampleRate channels=$numChannels bufferSize=$actualBufferSize",
        )
    }

    private fun resolveBufferSize(channelConfig: Int, label: String): Int {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Unsupported $label settings")
        }
        return maxOf(bufferSize, minBufferSize)
    }

    private fun startRecorder(record: AudioRecord?, label: String) {
        record ?: return
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) return
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            throw IllegalStateException("$label AudioRecord failed to start")
        }
    }

    private fun startTransmitterStreamManagement(settings: TransmitterSettings) {
        transmitterManagementJob = serviceScope.launch {
            streamRepository.getStreamsByType(ServiceType.TRANSMITTER).collect { streams ->
                reconcileTransmitterStreams(
                    streams = streams.filter { it.isEnabled },
                    settings = settings,
                )
            }
        }
    }

    private fun startNetworkMonitor() {
        if (networkMonitorJob != null) return
        networkMonitorJob = serviceScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(NETWORK_MONITOR_INTERVAL_MS)
                val settings = settingsRepository.getTransmitterSettings().first()
                val streams = streamRepository.getStreamsByType(ServiceType.TRANSMITTER).first()
                    .filter { it.isEnabled }
                reconcileTransmitterStreams(streams, settings)
            }
        }
    }

    private fun reconcileTransmitterStreams(
        streams: List<Stream>,
        settings: TransmitterSettings,
        restartExisting: Boolean = false,
    ) {
        val duplicateNames = duplicateStreamNames(streams)
        if (duplicateNames.isNotEmpty()) {
            Timber.w("TransmitterService duplicate stream names: ${duplicateNames.joinToString()}")
            serviceManager.updateTransmitterState(
                ServiceState.Error("Duplicate transmitter stream name: ${duplicateNames.joinToString()}"),
            )
            stopSelf()
            return
        }

        val streamsById = streams.associateBy { it.id }
        val currentIds = synchronized(fanOutLock) { streamConsumers.keys.toSet() }
        streams.forEach { stream -> transmitterStreamVolumes[stream.id] = stream.volume }
        transmitterStreamVolumes.keys
            .filter { it !in streamsById.keys }
            .forEach { transmitterStreamVolumes.remove(it) }

        (currentIds - streamsById.keys).forEach { id ->
            stopConsumer(id)
        }

        streams.forEach { stream ->
            val existing = synchronized(fanOutLock) { streamConsumers[stream.id] }
            val resolvedNetwork = NetworkUtils.resolveNetworkSelection(applicationContext, stream.networkSelection)
            if (resolvedNetwork == null) {
                if (existing != null) {
                    stopConsumer(stream.id)
                }
                postSelectedNetworkUnavailable(stream)
                return@forEach
            }
            if (existing == null || shouldRestartConsumer(existing.stream, stream) || restartExisting) {
                if (existing != null) {
                    stopConsumer(stream.id)
                }
                startConsumer(stream, settings)
            } else {
                synchronized(fanOutLock) {
                    streamConsumers[stream.id] = existing.copy(stream = stream)
                }
            }
        }

        reconcileSourceReaders(settings)
        updateStreamCountNotification()
    }

    private fun startConsumer(stream: Stream, settings: TransmitterSettings) {
        if (stream.sourceType == SourceType.INTERNAL_AUDIO && mediaProjection == null) {
            postTransmitterStreamError(stream, "Restart transmitter to enable internal audio")
            return
        }

        val frames = Channel<ByteArray>(
            capacity = FRAME_CHANNEL_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        var completedWithError = false
        val job = serviceScope.launch {
            serviceManager.updateStreamRuntimeStatus(
                serviceType = ServiceType.TRANSMITTER,
                streamId = stream.id,
                status = StreamRuntimeStatus.Connecting,
            )
            try {
                runConsumerWithRetry(stream, frames, settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketException) {
                completedWithError = true
                postTransmitterStreamError(stream, StreamError.NetworkUnreachable(stream.host, stream.port))
            } catch (e: SelectedNetworkUnavailableException) {
                completedWithError = true
                postSelectedNetworkUnavailable(stream)
            } catch (e: Exception) {
                completedWithError = true
                Timber.e(e, "Transmitter stream consumer failed: ${stream.name}")
                postTransmitterStreamError(stream, e.message ?: "Stream failed")
            } finally {
                frames.close()
                synchronized(fanOutLock) {
                    if (streamConsumers[stream.id]?.job == coroutineContext[Job]) {
                        streamConsumers.remove(stream.id)
                    }
                    if (runningJobs[stream.id] == coroutineContext[Job]) {
                        runningJobs.remove(stream.id)
                    }
                }
                if (!completedWithError) {
                    serviceManager.clearStreamRuntimeStatus(ServiceType.TRANSMITTER, stream.id)
                }
                updateStreamCountNotification()
                reconcileSourceReaders(settings)
                Timber.d("Transmitter stream consumer stopped: ${stream.name}")
            }
        }
        synchronized(fanOutLock) {
            streamConsumers[stream.id] = TransmitterConsumer(stream, frames, job)
            runningJobs[stream.id] = job
        }
        transmitterStreamVolumes[stream.id] = stream.volume
        Timber.d("Transmitter stream consumer starting: ${stream.name} (${stream.sourceType})")
    }

    private fun stopConsumer(streamId: Long) {
        val consumer = synchronized(fanOutLock) {
            streamConsumers.remove(streamId)
        } ?: return
        serviceManager.clearStreamRuntimeStatus(ServiceType.TRANSMITTER, streamId)
        synchronized(fanOutLock) {
            if (runningJobs[streamId] == consumer.job) {
                runningJobs.remove(streamId)
            }
        }
        transmitterStreamVolumes.remove(streamId)
        consumer.frames.close()
        consumer.job.cancel()
        Timber.d("Transmitter stream consumer cancelling: ${consumer.stream.name}")
    }

    private fun reconcileSourceReaders(settings: TransmitterSettings) {
        SourceType.values().forEach { sourceType ->
            val hasConsumers = synchronized(fanOutLock) {
                streamConsumers.values.any { it.stream.sourceType == sourceType }
            }
            val hasReader = synchronized(fanOutLock) { sourceReaderJobs.containsKey(sourceType) }
            when {
                hasConsumers && !hasReader -> startSourceReader(sourceType, settings)
                !hasConsumers && hasReader -> stopSourceReader(sourceType)
            }
        }
    }

    private fun startSourceReader(sourceType: SourceType, settings: TransmitterSettings) {
        val job = serviceScope.launch {
            try {
                captureReaderLoop(sourceType, settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Transmitter capture reader failed for $sourceType")
                snapshotConsumers(sourceType).forEach { consumer ->
                    postTransmitterStreamError(consumer.stream, "Capture failed: ${e.message}")
                }
                serviceManager.updateTransmitterState(
                    ServiceState.Error("Transmitter $sourceType capture failed: ${e.message}"),
                )
                stopSelf()
            } finally {
                synchronized(fanOutLock) {
                    if (sourceReaderJobs[sourceType] == coroutineContext[Job]) {
                        sourceReaderJobs.remove(sourceType)
                    }
                }
                releaseSourceAudioRecord(sourceType)
                Timber.d("Transmitter capture reader stopped: $sourceType")
            }
        }
        synchronized(fanOutLock) {
            sourceReaderJobs[sourceType] = job
        }
        Timber.d("Transmitter capture reader starting: $sourceType")
    }

    private fun stopSourceReader(sourceType: SourceType) {
        val job = synchronized(fanOutLock) { sourceReaderJobs.remove(sourceType) } ?: return
        job.cancel()
        Timber.d("Transmitter capture reader cancelling: $sourceType")
    }

    private suspend fun captureReaderLoop(
        sourceType: SourceType,
        settings: TransmitterSettings,
    ) = withContext(Dispatchers.IO) {
        val recorder = ensureRecorderStarted(sourceType, settings)
        val buffer = ByteArray(bufferSize)
        var consecutiveEmptyReads = 0
        var lastLevelEmissionMs = 0L
        var levelEmissionCount = 0
        var levelLogWindowMs = System.currentTimeMillis()

        Timber.d("Transmitter capture reader active: $sourceType")
        while (isActive) {
            val bytesRead = recorder.read(buffer, 0, buffer.size)
            when {
                bytesRead > 0 -> consecutiveEmptyReads = 0
                bytesRead == 0 -> {
                    consecutiveEmptyReads++
                    if (
                        sourceType == SourceType.INTERNAL_AUDIO &&
                        consecutiveEmptyReads == EMPTY_READ_WARNING_THRESHOLD
                    ) {
                        Timber.w(
                            "Transmitter $sourceType: $EMPTY_READ_WARNING_THRESHOLD consecutive empty reads - " +
                                "apps may be blocking capture",
                        )
                    }
                    continue
                }
                bytesRead == AudioRecord.ERROR_DEAD_OBJECT -> {
                    if (sourceType == SourceType.INTERNAL_AUDIO) {
                        serviceManager.updateTransmitterState(ServiceState.Error("MediaProjection revoked"))
                    }
                    Timber.e("AudioRecord ERROR_DEAD_OBJECT in $sourceType capture reader")
                    throw IOException("AudioRecord dead object")
                }
                else -> continue
            }

            val frame = buffer.copyOf(bytesRead)
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastLevelEmissionMs >= LEVEL_EMISSION_INTERVAL_MS) {
                val peak = AudioUtils.computePeakLevel(frame, numChannels)
                serviceManager.updateTransmitterLevel(peak.first, peak.second)
                lastLevelEmissionMs = nowMs
                levelEmissionCount++
                if (nowMs - levelLogWindowMs >= LEVEL_LOG_WINDOW_MS) {
                    Timber.v("Transmitter level emissions for $sourceType: $levelEmissionCount/sec")
                    levelEmissionCount = 0
                    levelLogWindowMs = nowMs
                }
            }

            snapshotConsumers(sourceType).forEach { consumer ->
                consumer.frames.trySend(frame.copyOf())
            }
        }
    }

    private suspend fun runConsumerWithRetry(
        stream: Stream,
        frames: ReceiveChannel<ByteArray>,
        settings: TransmitterSettings,
    ) {
        RetryUtils.withExponentialRetry(
            maxAttempts = STREAM_SEND_MAX_ATTEMPTS,
            onSocketRetry = { attempt, delayMs, exception ->
                Timber.w(
                    "Transmitter ${stream.name} network error (attempt $attempt/$STREAM_SEND_MAX_ATTEMPTS), " +
                        "retrying in ${delayMs}ms: ${exception.message}",
                )
            },
        ) {
            streamSendLoop(stream, frames, settings)
        }
    }

    private suspend fun streamSendLoop(
        stream: Stream,
        frames: ReceiveChannel<ByteArray>,
        settings: TransmitterSettings,
    ) = withContext(Dispatchers.IO) {
        val target = openTarget(stream, settings)
        var confirmedActive = false
        try {
            for (sourcePcm in frames) {
                val rawPcm = sourcePcm
                val combinedVolume = currentTransmitterStreamVolume(stream.id, stream.volume) * transmitterGlobalVolume
                if (combinedVolume != 1.0f) {
                    AudioUtils.applyVolume(rawPcm, combinedVolume)
                }
                VbanPacketizer.packetize(
                    rawPcm = rawPcm,
                    samplesPerPacket = target.samplesPerPacket,
                    numChannels = numChannels,
                    bitsPerSample = 16,
                    streamName = target.stream.name,
                    sampleRateHz = sampleRate,
                    frameCounterRef = target.frameCounter,
                ).forEach { payload ->
                    target.socket.send(
                        DatagramPacket(payload, payload.size, target.address, target.stream.port),
                    )
                    if (!confirmedActive) {
                        serviceManager.updateStreamRuntimeStatus(
                            serviceType = ServiceType.TRANSMITTER,
                            streamId = stream.id,
                            status = StreamRuntimeStatus.Active,
                        )
                        confirmedActive = true
                    }
                }
            }
        } finally {
            target.socket.close()
            Timber.d("Transmitter target stopped: ${target.stream.name}")
        }
    }

    private fun openTarget(stream: Stream, settings: TransmitterSettings): TransmitterTarget {
        val resolvedNetwork = NetworkUtils.resolveNetworkSelection(applicationContext, stream.networkSelection)
            ?: throw SelectedNetworkUnavailableException(stream.networkSelection.displayName)
        val socket = createSocketForNetwork(resolvedNetwork, stream)
        if (stream.broadcastMode) {
            socket.broadcast = true
        }
        val address = resolveDestinationAddress(stream, socket, resolvedNetwork)
        Timber.d(
            "Transmitter target ready: ${stream.name} (${stream.sourceType}) -> " +
                "${address.hostAddress}:${stream.port} via ${resolvedNetwork.label}",
        )
        return TransmitterTarget(
            stream = stream,
            socket = socket,
            address = address,
            samplesPerPacket = VbanNetQuality.calculateSamplesPerPacket(stream.netQuality, numChannels, 16),
            frameCounter = AtomicInteger(0),
        )
    }

    private fun currentTransmitterStreamVolume(streamId: Long, fallback: Float): Float =
        transmitterStreamVolumes[streamId] ?: fallback

    private fun shouldRestartConsumer(previous: Stream, next: Stream): Boolean =
        previous.runtimeConfig() != next.runtimeConfig()

    private fun createSocketForNetwork(
        resolvedNetwork: NetworkUtils.ResolvedNetworkSelection,
        stream: Stream,
    ): DatagramSocket {
        val socket = when (resolvedNetwork.bindingStrategy) {
            NetworkUtils.NetworkBindingStrategy.NETWORK -> DatagramSocket()
            NetworkUtils.NetworkBindingStrategy.INTERFACE_ADDRESS ->
                DatagramSocket(InetSocketAddress(resolvedNetwork.address, 0))
        }
        try {
            if (resolvedNetwork.bindingStrategy == NetworkUtils.NetworkBindingStrategy.NETWORK) {
                resolvedNetwork.network?.bindSocket(socket)
                    ?: throw SocketException("Network object missing for ${resolvedNetwork.interfaceName}")
                Timber.d("Bound ${stream.name} to network ${resolvedNetwork.label}")
            } else {
                Timber.d(
                    "Bound ${stream.name} to local interface ${resolvedNetwork.interfaceName} " +
                        "(${resolvedNetwork.address.hostAddress})",
                )
            }
            return socket
        } catch (e: Exception) {
            socket.close()
            if (e is SocketException) throw e
            throw SocketException("Failed to bind ${stream.name} to ${resolvedNetwork.label}: ${e.message}")
        }
    }

    override suspend fun streamLoop(stream: Stream) {
        throw UnsupportedOperationException("TransmitterService uses source fan-out capture readers")
    }

    private fun ensureRecorderStarted(sourceType: SourceType, settings: TransmitterSettings): AudioRecord {
        val record = when (sourceType) {
            SourceType.MIC -> {
                if (micAudioRecord == null) {
                    initialiseMicAudio(settings)
                }
                micAudioRecord ?: throw IllegalStateException("Microphone AudioRecord not initialised")
            }
            SourceType.INTERNAL_AUDIO -> {
                if (internalAudioRecord == null) {
                    val projection = mediaProjection
                        ?: throw IllegalStateException("MediaProjection unavailable for internal audio")
                    initialiseInternalAudio(settings, projection)
                }
                internalAudioRecord ?: throw IllegalStateException("Internal audio AudioRecord not initialised")
            }
        }
        startRecorder(
            record = record,
            label = when (sourceType) {
                SourceType.MIC -> "microphone"
                SourceType.INTERNAL_AUDIO -> "internal audio"
            },
        )
        return record
    }

    private fun releaseSourceAudioRecord(sourceType: SourceType) {
        when (sourceType) {
            SourceType.MIC -> {
                releaseAudioRecord(micAudioRecord, "microphone")
                micAudioRecord = null
            }
            SourceType.INTERNAL_AUDIO -> {
                releaseAudioRecord(internalAudioRecord, "internal audio")
                internalAudioRecord = null
            }
        }
    }

    private fun snapshotConsumers(sourceType: SourceType): List<TransmitterConsumer> =
        synchronized(fanOutLock) {
            streamConsumers.values
                .filter { it.stream.sourceType == sourceType }
                .toList()
        }

    private fun postTransmitterStreamError(stream: Stream, error: StreamError) {
        postTransmitterStreamError(stream, error.toUserMessage())
    }

    private fun postTransmitterStreamError(stream: Stream, message: String) {
        Timber.e("Transmitter stream error: ${stream.name} - $message")
        serviceManager.updateStreamRuntimeStatus(
            serviceType = ServiceType.TRANSMITTER,
            streamId = stream.id,
            status = StreamRuntimeStatus.Error(message),
        )
    }

    private fun postSelectedNetworkUnavailable(stream: Stream) {
        postTransmitterStreamError(
            stream = stream,
            message = "Selected network not available: ${stream.networkSelection.displayName}. Edit the stream and choose an available network.",
        )
    }

    private fun resolveDestinationAddress(
        stream: Stream,
        socket: DatagramSocket,
        resolvedNetwork: NetworkUtils.ResolvedNetworkSelection,
    ): InetAddress {
        if (stream.broadcastMode) {
            socket.broadcast = true
            return resolvedNetwork.broadcastAddress
                ?: InetAddress.getByName(GLOBAL_BROADCAST_ADDRESS).also {
                    Timber.w(
                        "Unable to determine subnet broadcast for ${resolvedNetwork.label}; " +
                            "using $GLOBAL_BROADCAST_ADDRESS fallback",
                    )
                }
        }

        return try {
            InetAddress.getByName(stream.host)
        } catch (e: UnknownHostException) {
            socket.close()
            throw SocketException("Unable to resolve host ${stream.host}: ${e.message}")
        }
    }

    override fun onServiceDestroyed() {
        ignoreMediaProjectionStopCallback = true
        transmitterManagementJob?.cancel()
        transmitterManagementJob = null
        transmitterSettingsJob?.cancel()
        transmitterSettingsJob = null
        networkMonitorJob?.cancel()
        networkMonitorJob = null
        synchronized(fanOutLock) {
            streamConsumers.values.forEach { consumer ->
                consumer.frames.close()
                consumer.job.cancel()
            }
            streamConsumers.clear()
            sourceReaderJobs.values.forEach { it.cancel() }
            sourceReaderJobs.clear()
            runningJobs.clear()
        }
        transmitterStreamVolumes.clear()
        releaseAudioRecord(micAudioRecord, "microphone")
        releaseAudioRecord(internalAudioRecord, "internal audio")
        micAudioRecord = null
        internalAudioRecord = null
        releaseMediaProjection()
        streamManagementStarted = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceManager.updateTransmitterState(ServiceState.Idle)
        serviceManager.clearTransmitterLevel()
        serviceManager.clearStreamRuntimeStatuses(ServiceType.TRANSMITTER)
        Timber.d("TransmitterService destroyed")
    }

    private fun releaseAudioRecord(record: AudioRecord?, label: String) {
        record ?: return
        runCatching {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        }.onFailure { Timber.w(it, "Failed to stop $label AudioRecord") }
        record.release()
    }

    private fun releaseMediaProjection() {
        mediaProjection?.let { projection ->
            ignoreMediaProjectionStopCallback = true
            mediaProjectionCallback?.let { callback ->
                runCatching { projection.unregisterCallback(callback) }
                    .onFailure { Timber.w(it, "Failed to unregister MediaProjection callback") }
            }
            runCatching { projection.stop() }
                .onFailure { Timber.w(it, "Failed to stop MediaProjection") }
        }
        mediaProjectionCallback = null
        mediaProjection = null
    }

    private fun duplicateStreamNames(streams: List<Stream>): List<String> =
        streams
            .groupBy { it.name.trim().lowercase() }
            .filterKeys { it.isNotBlank() }
            .filterValues { it.size > 1 }
            .values
            .mapNotNull { duplicates -> duplicates.firstOrNull()?.name }

    override fun onUpdateNotification(activeStreamCount: Int) {
        if (activeStreamCount == 0) {
            updateNotification("no active streams")
            return
        }

        serviceScope.launch {
            val activeIds = synchronized(fanOutLock) { runningJobs.keys.toSet() }
            val streams = streamRepository.getStreamsByType(ServiceType.TRANSMITTER).first()
                .filter { it.id in activeIds }
            val micCount = streams.count { it.sourceType == SourceType.MIC }
            val internalCount = streams.count { it.sourceType == SourceType.INTERNAL_AUDIO }
            val parts = buildList {
                if (micCount > 0) add("$micCount mic")
                if (internalCount > 0) add("$internalCount internal audio")
            }.joinToString(", ")
            updateNotification("streaming: $parts")
        }
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, ServiceManager.TRANSMITTER_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_transmitter_title))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_mic,
                getString(R.string.notif_action_stop),
                buildStopPendingIntent(),
            )
            .build()

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildStopPendingIntent(): PendingIntent {
        val intent = Intent(this, TransmitterService::class.java).apply {
            action = ServiceManager.ACTION_STOP
        }
        return PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.resultData(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(ServiceManager.EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            getParcelableExtra(ServiceManager.EXTRA_RESULT_DATA)
        }

    private companion object {
        const val NOTIFICATION_ID = 1
        const val EMPTY_READ_WARNING_THRESHOLD = 100
        const val GLOBAL_BROADCAST_ADDRESS = "255.255.255.255"
        const val LEVEL_EMISSION_INTERVAL_MS = 50L
        const val LEVEL_LOG_WINDOW_MS = 1_000L
        const val FRAME_CHANNEL_CAPACITY = 4
        const val STREAM_SEND_MAX_ATTEMPTS = 3
        const val NETWORK_MONITOR_INTERVAL_MS = 2_000L
    }
}

private data class ForegroundStartupContext(
    val hasMicStreams: Boolean,
    val hasInternalAudioStreams: Boolean,
    val mediaProjection: MediaProjection?,
)

private data class TransmitterConsumer(
    val stream: Stream,
    val frames: Channel<ByteArray>,
    val job: Job,
)

private data class TransmitterTarget(
    val stream: Stream,
    val socket: DatagramSocket,
    val address: InetAddress,
    val samplesPerPacket: Int,
    val frameCounter: AtomicInteger,
)

private data class TransmitterRuntimeConfig(
    val name: String,
    val host: String,
    val port: Int,
    val netQuality: com.audiolan.app.domain.model.NetQuality,
    val networkSelection: com.audiolan.app.domain.model.NetworkSelection,
    val sourceType: SourceType,
    val broadcastMode: Boolean,
)

private fun Stream.runtimeConfig(): TransmitterRuntimeConfig =
    TransmitterRuntimeConfig(
        name = name,
        host = host,
        port = port,
        netQuality = netQuality,
        networkSelection = networkSelection,
        sourceType = sourceType,
        broadcastMode = broadcastMode,
    )

private class SelectedNetworkUnavailableException(
    label: String,
) : Exception("Selected network not available: $label")
