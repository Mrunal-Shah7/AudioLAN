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
import android.net.Network
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
import com.audiolan.app.domain.model.TransmitterSettings
import com.audiolan.app.domain.model.TransportMode
import com.audiolan.app.domain.vban.VbanNetQuality
import com.audiolan.app.domain.vban.VbanPacketizer
import com.audiolan.app.service.ServiceManager
import com.audiolan.app.service.base.BaseStreamingService
import com.audiolan.app.util.AudioUtils
import com.audiolan.app.util.NetworkUtils
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val sourceJobs = mutableMapOf<SourceType, Job>()

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

                if (hasMicStreams) {
                    if (!startupContext.hasMicStreams) {
                        throw IllegalStateException("Microphone stream added after transmitter startup")
                    }
                    initialiseMicAudio(settings)
                }
                if (hasInternalAudioStreams) {
                    if (!startupContext.hasInternalAudioStreams || startupContext.mediaProjection == null) {
                        throw IllegalStateException("Internal audio stream missing MediaProjection startup context")
                    }
                    initialiseInternalAudio(settings, startupContext.mediaProjection)
                }

                startRecorder(micAudioRecord, "microphone")
                startRecorder(internalAudioRecord, "internal audio")

                registerWifiNetworkCallback()
                serviceManager.updateTransmitterState(ServiceState.Running)
                updateNotification("streaming")
                if (!streamManagementStarted) {
                    streamManagementStarted = true
                    startTransmitterStreamManagement(settings)
                }
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
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            throw IllegalStateException("$label AudioRecord failed to start")
        }
    }

    private fun startTransmitterStreamManagement(settings: TransmitterSettings) {
        transmitterManagementJob = serviceScope.launch {
            streamRepository.getStreamsByType(ServiceType.TRANSMITTER).collect { streams ->
                val enabledStreams = streams.filter { it.isEnabled }
                restartSourceFanout(enabledStreams, settings)
            }
        }
    }

    private fun restartSourceFanout(streams: List<Stream>, settings: TransmitterSettings) {
        sourceJobs.values.forEach { it.cancel() }
        sourceJobs.clear()
        runningJobs.clear()

        streams
            .groupBy { it.sourceType }
            .forEach { (sourceType, sourceStreams) ->
                val job = serviceScope.launch {
                    try {
                        sourceFanoutLoop(sourceType, sourceStreams, settings)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Transmitter source fan-out failed for $sourceType")
                        serviceManager.updateTransmitterState(
                            ServiceState.Error("Transmitter $sourceType failed: ${e.message}"),
                        )
                        stopSelf()
                    }
                }
                sourceJobs[sourceType] = job
                sourceStreams.forEach { stream ->
                    runningJobs[stream.id] = job
                }
            }
        updateStreamCountNotification()
    }

    override fun onWifiNetworkAvailable(network: Network) {
        serviceScope.launch {
            Timber.d("TransmitterService: Wi-Fi network available, restarting source fan-out")
            val settings = settingsRepository.getTransmitterSettings().first()
            val streams = streamRepository.getStreamsByType(ServiceType.TRANSMITTER).first()
                .filter { it.isEnabled }
            restartSourceFanout(streams, settings)
        }
    }

    private suspend fun sourceFanoutLoop(
        sourceType: SourceType,
        streams: List<Stream>,
        settings: TransmitterSettings,
    ) = withContext(Dispatchers.IO) {
        if (streams.isEmpty()) return@withContext
        val recorder = when (sourceType) {
            SourceType.MIC -> micAudioRecord ?: throw IllegalStateException("Microphone AudioRecord not initialised")
            SourceType.INTERNAL_AUDIO -> internalAudioRecord
                ?: throw IllegalStateException("Internal audio AudioRecord not initialised")
        }
        val targets = streams.map { stream -> openTarget(stream, settings) }
        val buffer = ByteArray(bufferSize)
        var consecutiveEmptyReads = 0
        var lastLevelEmissionMs = 0L
        var levelEmissionCount = 0
        var levelLogWindowMs = System.currentTimeMillis()

        Timber.d(
            "Transmitter source fan-out starting: $sourceType -> " +
                streams.joinToString { "${it.name}@${it.host}:${it.port}" },
        )
        try {
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
                        Timber.e("AudioRecord ERROR_DEAD_OBJECT in $sourceType - terminating source fan-out")
                        throw IOException("AudioRecord dead object")
                    }
                    else -> continue
                }

                val sourcePcm = buffer.copyOf(bytesRead)
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastLevelEmissionMs >= LEVEL_EMISSION_INTERVAL_MS) {
                    val meterPcm = sourcePcm.copyOf()
                    if (settings.globalVolume != 1.0f) {
                        AudioUtils.applyVolume(meterPcm, settings.globalVolume)
                    }
                    val peak = AudioUtils.computePeakLevel(meterPcm, numChannels)
                    serviceManager.updateTransmitterLevel(peak.first, peak.second)
                    lastLevelEmissionMs = nowMs
                    levelEmissionCount++
                    if (nowMs - levelLogWindowMs >= LEVEL_LOG_WINDOW_MS) {
                        Timber.v("Transmitter level emissions for $sourceType: $levelEmissionCount/sec")
                        levelEmissionCount = 0
                        levelLogWindowMs = nowMs
                    }
                }

                targets.forEach { target ->
                    val rawPcm = sourcePcm.copyOf()
                    if (target.combinedVolume != 1.0f) {
                        AudioUtils.applyVolume(rawPcm, target.combinedVolume)
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
                    }
                }
            }
        } finally {
            targets.forEach { target ->
                target.socket.close()
                Timber.d("Transmitter target stopped: ${target.stream.name}")
            }
            Timber.d("Transmitter source fan-out stopped: $sourceType")
        }
    }

    private fun openTarget(stream: Stream, settings: TransmitterSettings): TransmitterTarget {
        if (stream.transportMode == TransportMode.USB_TETHER && !NetworkUtils.hasUsbTetherInterface()) {
            throw SocketException("No USB tethered device connected")
        }
        val socket = DatagramSocket()
        if (stream.broadcastMode) {
            socket.broadcast = true
        }
        if (stream.transportMode == TransportMode.WIFI) {
            bindSocketToWifiNetwork(socket, stream)
        }
        val address = resolveDestinationAddress(stream, socket)
        Timber.d(
            "Transmitter target ready: ${stream.name} (${stream.sourceType}) -> " +
                "${address.hostAddress}:${stream.port}",
        )
        return TransmitterTarget(
            stream = stream,
            socket = socket,
            address = address,
            samplesPerPacket = VbanNetQuality.calculateSamplesPerPacket(stream.netQuality, numChannels, 16),
            frameCounter = AtomicInteger(0),
            combinedVolume = stream.volume * settings.globalVolume,
        )
    }

    override suspend fun streamLoop(stream: Stream) = withContext(Dispatchers.IO) {
        val recorder = when (stream.sourceType) {
            SourceType.MIC -> micAudioRecord ?: throw IllegalStateException("Microphone AudioRecord not initialised")
            SourceType.INTERNAL_AUDIO -> internalAudioRecord
                ?: throw IllegalStateException("Internal audio AudioRecord not initialised")
        }
        val settings = settingsRepository.getTransmitterSettings().first()
        if (stream.transportMode == TransportMode.USB_TETHER && !NetworkUtils.hasUsbTetherInterface()) {
            throw SocketException("No USB tethered device connected")
        }

        val samplesPerPacket = VbanNetQuality.calculateSamplesPerPacket(stream.netQuality, numChannels, 16)
        val frameCounter = AtomicInteger(0)
        val socket = DatagramSocket()
        if (stream.broadcastMode) {
            socket.broadcast = true
        }
        if (stream.transportMode == TransportMode.WIFI) {
            bindSocketToWifiNetwork(socket, stream)
        }
        val address = resolveDestinationAddress(stream, socket)
        val buffer = ByteArray(bufferSize)
        var consecutiveEmptyReads = 0
        var lastLevelEmissionMs = 0L
        var levelEmissionCount = 0
        var levelLogWindowMs = System.currentTimeMillis()

        Timber.d(
            "Transmitter stream starting: ${stream.name} (${stream.sourceType}) -> " +
                "${address.hostAddress}:${stream.port}",
        )
        try {
            while (isActive) {
                val bytesRead = recorder.read(buffer, 0, buffer.size)
                when {
                    bytesRead > 0 -> consecutiveEmptyReads = 0
                    bytesRead == 0 -> {
                        consecutiveEmptyReads++
                        if (
                            stream.sourceType == SourceType.INTERNAL_AUDIO &&
                            consecutiveEmptyReads == EMPTY_READ_WARNING_THRESHOLD
                        ) {
                            Timber.w(
                                "Transmitter ${stream.name}: $EMPTY_READ_WARNING_THRESHOLD consecutive empty reads - " +
                                    "apps may be blocking capture",
                            )
                        }
                        continue
                    }
                    bytesRead == AudioRecord.ERROR_DEAD_OBJECT -> {
                        if (stream.sourceType == SourceType.INTERNAL_AUDIO) {
                            serviceManager.updateTransmitterState(ServiceState.Error("MediaProjection revoked"))
                        }
                        Timber.e("AudioRecord ERROR_DEAD_OBJECT in ${stream.name} - terminating stream")
                        throw IOException("AudioRecord dead object")
                    }
                    else -> continue
                }

                val rawPcm = buffer.copyOf(bytesRead)
                val combinedVolume = stream.volume * settings.globalVolume
                if (combinedVolume != 1.0f) {
                    AudioUtils.applyVolume(rawPcm, combinedVolume)
                }

                val nowMs = System.currentTimeMillis()
                if (nowMs - lastLevelEmissionMs >= LEVEL_EMISSION_INTERVAL_MS) {
                    val peak = AudioUtils.computePeakLevel(rawPcm, numChannels)
                    serviceManager.updateTransmitterLevel(peak.first, peak.second)
                    lastLevelEmissionMs = nowMs
                    levelEmissionCount++
                    if (nowMs - levelLogWindowMs >= LEVEL_LOG_WINDOW_MS) {
                        Timber.v("Transmitter level emissions for ${stream.name}: $levelEmissionCount/sec")
                        levelEmissionCount = 0
                        levelLogWindowMs = nowMs
                    }
                }

                val packets = VbanPacketizer.packetize(
                    rawPcm = rawPcm,
                    samplesPerPacket = samplesPerPacket,
                    numChannels = numChannels,
                    bitsPerSample = 16,
                    streamName = stream.name,
                    sampleRateHz = sampleRate,
                    frameCounterRef = frameCounter,
                )

                packets.forEach { payload ->
                    socket.send(DatagramPacket(payload, payload.size, address, stream.port))
                }
            }
        } finally {
            socket.close()
            Timber.d("Transmitter stream stopped: ${stream.name}")
        }
    }

    private fun resolveDestinationAddress(stream: Stream, socket: DatagramSocket): InetAddress {
        if (stream.broadcastMode) {
            socket.broadcast = true
            return NetworkUtils.getWifiBroadcastAddress()
                ?: InetAddress.getByName(GLOBAL_BROADCAST_ADDRESS).also {
                    Timber.w(
                        "Unable to determine subnet broadcast address; using $GLOBAL_BROADCAST_ADDRESS fallback",
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
        releaseAudioRecord(micAudioRecord, "microphone")
        releaseAudioRecord(internalAudioRecord, "internal audio")
        micAudioRecord = null
        internalAudioRecord = null
        releaseMediaProjection()
        transmitterManagementJob?.cancel()
        transmitterManagementJob = null
        sourceJobs.values.forEach { it.cancel() }
        sourceJobs.clear()
        runningJobs.clear()
        streamManagementStarted = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceManager.updateTransmitterState(ServiceState.Idle)
        serviceManager.clearTransmitterLevel()
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
            val activeIds = runningJobs.keys.toSet()
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
    }
}

private data class ForegroundStartupContext(
    val hasMicStreams: Boolean,
    val hasInternalAudioStreams: Boolean,
    val mediaProjection: MediaProjection?,
)

private data class TransmitterTarget(
    val stream: Stream,
    val socket: DatagramSocket,
    val address: InetAddress,
    val samplesPerPacket: Int,
    val frameCounter: AtomicInteger,
    val combinedVolume: Float,
)
