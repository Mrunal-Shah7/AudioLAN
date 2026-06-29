package com.audiolan.app.service.receiver

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioTrack
import androidx.core.app.NotificationCompat
import com.audiolan.app.R
import com.audiolan.app.domain.model.DiscoveredDevice
import com.audiolan.app.domain.model.DiscoverySource
import com.audiolan.app.domain.model.NetworkSelection
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.domain.model.StreamRuntimeStatus
import com.audiolan.app.domain.vban.VbanDecoder
import com.audiolan.app.domain.vban.VbanSampleFormat
import com.audiolan.app.service.ServiceManager
import com.audiolan.app.service.base.BaseStreamingService
import com.audiolan.app.util.AudioUtils
import com.audiolan.app.util.NetworkUtils
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ReceiverService : BaseStreamingService() {
    private var udpSocket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var streamNameCacheJob: Job? = null
    private var receiverSettingsJob: Job? = null
    private var streamManagementStarted = false

    private val jitterBuffers = ConcurrentHashMap<Long, JitterBuffer>()
    private val audioTracks = ConcurrentHashMap<Long, AudioTrack>()
    private val receiverStreamVolumes = ConcurrentHashMap<Long, Float>()
    private val receiveRestartStates = ConcurrentHashMap<Long, ReceiveRestartState>()
    private val restartGenerations = ConcurrentHashMap<Long, Int>()
    @Volatile
    private var receiverTargets: List<ReceiverStreamTarget> = emptyList()
    @Volatile
    private var receiverGlobalVolume: Float = 1.0f
    @Volatile
    private var receiveNetworkSnapshot = ReceiveNetworkSnapshot.EMPTY

    private val discoveredDevicesRepository by lazy {
        entryPoint.discoveredDevicesRepository()
    }

    override fun serviceType(): ServiceType = ServiceType.RECEIVER

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ServiceManager.ACTION_STOP) {
            serviceManager.stopReceiverService()
            return START_NOT_STICKY
        }

        serviceManager.updateReceiverState(ServiceState.Starting)
        startForeground(NOTIFICATION_ID, buildNotification("starting..."))
        acquireWakeLock("AudioLAN::ReceiverService")

        serviceScope.launch {
            try {
                val enabledStreams = streamRepository.getStreamsByType(ServiceType.RECEIVER).first()
                    .filter { it.isEnabled }
                val duplicateNames = duplicateStreamNames(enabledStreams)
                if (duplicateNames.isNotEmpty()) {
                    Timber.w("ReceiverService duplicate stream names: ${duplicateNames.joinToString()}")
                    serviceManager.updateReceiverState(
                        ServiceState.Error("Duplicate receiver stream name: ${duplicateNames.joinToString()}"),
                    )
                    stopSelf()
                    return@launch
                }
                if (udpSocket == null) {
                    udpSocket = DatagramSocket(PORT)
                }
                serviceManager.updateReceiverState(ServiceState.Running)
                updateNotification("listening on port $PORT")

                if (receiveJob == null) {
                    receiveJob = launch { receiveLoop() }
                }
                if (streamNameCacheJob == null) {
                    streamNameCacheJob = launch { collectEnabledStreamNames() }
                }
                if (receiverSettingsJob == null) {
                    receiverGlobalVolume = settingsRepository.getReceiverSettings().first().globalVolume
                    receiverSettingsJob = launch {
                        settingsRepository.getReceiverSettings().collect { settings ->
                            receiverGlobalVolume = settings.globalVolume
                        }
                    }
                }
                if (!streamManagementStarted) {
                    streamManagementStarted = true
                    startStreamManagement()
                }
            } catch (e: BindException) {
                Timber.e(e, "Failed to bind UDP port $PORT - already in use")
                serviceManager.updateReceiverState(ServiceState.Error("Port $PORT in use"))
                stopSelf()
            } catch (e: Exception) {
                Timber.e(e, "ReceiverService failed to start")
                serviceManager.updateReceiverState(ServiceState.Error("Receiver init failed: ${e.message}"))
                stopSelf()
            }
        }
        return START_STICKY
    }

    private suspend fun collectEnabledStreamNames() {
        streamRepository.getStreamsByType(ServiceType.RECEIVER).collect { streams ->
            val enabledStreams = streams.filter { it.isEnabled }
            val enabledIds = enabledStreams.map { it.id }.toSet()
            receiverTargets = enabledStreams.map { stream ->
                ReceiverStreamTarget(
                    id = stream.id,
                    name = stream.name,
                    host = stream.host,
                )
            }
            enabledStreams.forEach { stream ->
                receiverStreamVolumes[stream.id] = stream.volume
            }
            receiverStreamVolumes.keys.filter { it !in enabledIds }.forEach { receiverStreamVolumes.remove(it) }
            receiveRestartStates.keys.filter { it !in enabledIds }.forEach { receiveRestartStates.remove(it) }
            restartGenerations.keys.filter { it !in enabledIds }.forEach { restartGenerations.remove(it) }
        }
    }

    private suspend fun receiveLoop() = withContext(Dispatchers.IO) {
        val socket = udpSocket ?: return@withContext
        val buffer = ByteArray(MAX_UDP_DATAGRAM_SIZE)

        Timber.d("ReceiverService: receive loop started on port $PORT")
        while (isActive) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val data = packet.data.copyOf(packet.length)
                val result = VbanDecoder.decode(data)
                if (result == null) {
                    Timber.v("Received non-VBAN UDP datagram from ${packet.address.hostAddress}")
                    continue
                }

                val (header, pcm) = result
                val senderIp = packet.address.hostAddress ?: continue
                val networkSnapshot = currentReceiveNetworkSnapshot(System.currentTimeMillis())
                val originNetwork = networkSnapshot.originFor(packet.address)?.selection
                if (networkSnapshot.isOwnAddress(packet.address)) {
                    forwardToDiscovery(senderIp, header.streamName, originNetwork)
                    continue
                }

                val matchedStreamId = findMatchingStreamId(header.streamName, senderIp)
                if (matchedStreamId != null) {
                    handleIncomingPacket(
                        streamId = matchedStreamId,
                        packet = ReceiverPacket(header, pcm, originNetwork),
                    )
                    serviceManager.updateStreamRuntimeStatus(
                        serviceType = ServiceType.RECEIVER,
                        streamId = matchedStreamId,
                        status = StreamRuntimeStatus.Active,
                    )
                } else {
                    forwardToDiscovery(senderIp, header.streamName, originNetwork)
                }
            } catch (e: SocketException) {
                if (!isActive) break
                Timber.e(e, "Socket error in receive loop")
                break
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error in receive loop")
            }
        }
        Timber.d("ReceiverService: receive loop ended")
    }

    private fun findMatchingStreamId(streamName: String, senderIp: String): Long? {
        val matchingNameTargets = receiverTargets.filter { it.name == streamName }
        return matchingNameTargets.firstOrNull { it.host == senderIp }?.id
            ?: matchingNameTargets.firstOrNull { it.host.isWildcardHost() }?.id
    }

    private fun handleIncomingPacket(
        streamId: Long,
        packet: ReceiverPacket,
    ) {
        val nowMs = System.currentTimeMillis()
        val state = receiveRestartStates.computeIfAbsent(streamId) { ReceiveRestartState() }
        var restartReason: String? = null

        synchronized(state) {
            val lastCounter = state.lastFrameCounter
            val lastPacketAt = state.lastPacketAtMs
            val backwardJump = lastCounter != null &&
                lastCounter.toLong() - packet.header.frameCounter.toLong() > RESTART_BACKWARD_JUMP_THRESHOLD_PACKETS
            val resumedAfterGap = lastPacketAt > 0L &&
                nowMs - lastPacketAt > RESTART_SILENCE_TIMEOUT_MS

            restartReason = when {
                backwardJump -> "frame counter restarted ${lastCounter} -> ${packet.header.frameCounter}"
                resumedAfterGap -> "packet gap ${nowMs - lastPacketAt}ms"
                else -> null
            }

            state.lastFrameCounter = packet.header.frameCounter
            state.lastPacketAtMs = nowMs
        }

        restartReason?.let { reason ->
            resetReceiveState(streamId, reason)
        }
        jitterBuffers[streamId]?.offer(packet)
    }

    private fun resetReceiveState(streamId: Long, reason: String) {
        Timber.i("ReceiverService: resetting receive state for stream id=$streamId ($reason)")
        jitterBuffers[streamId]?.clear()
        restartGenerations.merge(streamId, 1) { current, increment -> current + increment }
        serviceManager.updateStreamRuntimeStatus(
            serviceType = ServiceType.RECEIVER,
            streamId = streamId,
            status = StreamRuntimeStatus.Connecting,
        )
    }

    private fun currentReceiveNetworkSnapshot(nowMs: Long): ReceiveNetworkSnapshot {
        val current = receiveNetworkSnapshot
        if (nowMs - current.createdAtMs < RECEIVE_NETWORK_SNAPSHOT_TTL_MS) {
            return current
        }

        val refreshed = ReceiveNetworkSnapshot(
            resolvedNetworks = NetworkUtils.getResolvedNetworkSelections(applicationContext),
            createdAtMs = nowMs,
        )
        receiveNetworkSnapshot = refreshed
        return refreshed
    }

    private fun forwardToDiscovery(
        ip: String,
        streamName: String,
        originNetwork: NetworkSelection?,
    ) {
        discoveredDevicesRepository.emit(
            DiscoveredDevice(
                ip = ip,
                deviceName = null,
                streamName = streamName,
                source = DiscoverySource.VBAN_SNIFF,
                originNetwork = originNetwork,
            ),
        )
    }

    override suspend fun streamLoop(stream: Stream) = withContext(Dispatchers.IO) {
        val jitterBuffer = JitterBuffer(
            capacityPackets = JITTER_CAPACITY_PACKETS,
            prebufferPackets = JITTER_PREBUFFER_PACKETS,
        )
        jitterBuffers[stream.id] = jitterBuffer

        var localAudioTrack: AudioTrack? = null
        var configuredSampleRate: Int? = null
        var configuredChannels: Int? = null
        receiverStreamVolumes[stream.id] = stream.volume
        var observedRestartGeneration = restartGenerations[stream.id] ?: 0
        var lastLevelEmissionMs = 0L
        var levelEmissionCount = 0
        var levelLogWindowMs = System.currentTimeMillis()

        Timber.d("ReceiverService: playback loop started for stream ${stream.name}")
        try {
            while (isActive) {
                val currentRestartGeneration = restartGenerations[stream.id] ?: 0
                if (currentRestartGeneration != observedRestartGeneration) {
                    Timber.d(
                        "ReceiverService: reinitialising playback for ${stream.name} " +
                            "generation $observedRestartGeneration -> $currentRestartGeneration",
                    )
                    localAudioTrack?.let(::releaseAudioTrack)
                    audioTracks.remove(stream.id)
                    localAudioTrack = null
                    configuredSampleRate = null
                    configuredChannels = null
                    observedRestartGeneration = currentRestartGeneration
                }

                val packet = jitterBuffer.poll()
                if (packet == null) {
                    delay(1)
                    continue
                }

                if (
                    configuredSampleRate != null &&
                    (
                        packet.header.sampleRateHz != configuredSampleRate ||
                            packet.header.numChannels != configuredChannels
                        )
                ) {
                    Timber.w("Format mismatch for ${stream.name} - ignoring packet with different parameters")
                    continue
                }

                val playbackPcm = preparePcmForPlayback(packet)
                if (playbackPcm.isEmpty()) {
                    Timber.w("Unsupported VBAN playback format for ${stream.name}: ${packet.header.sampleFormat}")
                    continue
                }
                if (localAudioTrack == null) {
                    localAudioTrack = initialiseAudioTrack(stream, packet).also { track ->
                        audioTracks[stream.id] = track
                        configuredSampleRate = packet.header.sampleRateHz
                        configuredChannels = packet.header.numChannels
                    }
                }

                val playbackVolume = currentReceiverPlaybackVolume(stream.id, stream.volume)
                val pcm = if (playbackVolume != 1.0f) {
                    playbackPcm.copyOf().also { AudioUtils.applyVolume(it, playbackVolume) }
                } else {
                    playbackPcm
                }
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastLevelEmissionMs >= LEVEL_EMISSION_INTERVAL_MS) {
                    val peakChannels = packet.header.numChannels.coerceAtMost(MAX_PLAYBACK_CHANNELS)
                    val peak = AudioUtils.computePeakLevel(pcm, peakChannels)
                    serviceManager.updateReceiverLevel(peak.first, peak.second)
                    lastLevelEmissionMs = nowMs
                    levelEmissionCount++
                    if (nowMs - levelLogWindowMs >= LEVEL_LOG_WINDOW_MS) {
                        Timber.v("Receiver level emissions for ${stream.name}: $levelEmissionCount/sec")
                        levelEmissionCount = 0
                        levelLogWindowMs = nowMs
                    }
                }
                localAudioTrack?.let { track ->
                    writeFully(track, pcm)
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                }
            }
        } finally {
            audioTracks.remove(stream.id)?.let(::releaseAudioTrack)
            jitterBuffers.remove(stream.id)?.clear()
            receiverStreamVolumes.remove(stream.id)
            receiveRestartStates.remove(stream.id)
            restartGenerations.remove(stream.id)
            Timber.d("ReceiverService: playback loop ended for ${stream.name}")
        }
    }

    private fun currentReceiverStreamVolume(streamId: Long, fallback: Float): Float =
        receiverStreamVolumes[streamId] ?: fallback

    private fun currentReceiverPlaybackVolume(streamId: Long, fallback: Float): Float =
        (currentReceiverStreamVolume(streamId, fallback) * receiverGlobalVolume)
            .coerceIn(MIN_PLAYBACK_VOLUME, MAX_UNITY_PLAYBACK_VOLUME)

    private fun initialiseAudioTrack(stream: Stream, packet: ReceiverPacket): AudioTrack {
        val playbackChannels = packet.header.numChannels.coerceAtMost(MAX_PLAYBACK_CHANNELS)
        val track = AudioTrackFactory.create(packet.header.sampleRateHz, playbackChannels)
        Timber.d(
            "AudioTrack initialised for ${stream.name}: " +
                "${packet.header.sampleRateHz}Hz, ${packet.header.numChannels}ch input, " +
                "${playbackChannels}ch playback",
        )
        return track
    }

    private fun preparePcmForPlayback(packet: ReceiverPacket): ByteArray {
        val pcm16 = convertPcmTo16Bit(packet.pcm, packet.header.sampleFormat)
            ?: return ByteArray(0)
        return if (packet.header.numChannels <= MAX_PLAYBACK_CHANNELS) {
            pcm16
        } else {
            downmixToStereo16Bit(pcm16, packet.header.numChannels)
        }
    }

    private fun convertPcmTo16Bit(pcm: ByteArray, sampleFormat: VbanSampleFormat): ByteArray? =
        when (sampleFormat) {
            VbanSampleFormat.PCM_8_INT -> convert8BitPcmTo16Bit(pcm)
            VbanSampleFormat.PCM_16_INT -> pcm
            VbanSampleFormat.PCM_24_INT -> convert24BitIntegerPcmTo16Bit(pcm)
            VbanSampleFormat.PCM_32_INT -> convert32BitIntegerPcmTo16Bit(pcm)
            VbanSampleFormat.PCM_32_FLOAT -> convert32BitFloatPcmTo16Bit(pcm)
            VbanSampleFormat.PCM_64_FLOAT -> convert64BitFloatPcmTo16Bit(pcm)
        }

    private fun convert8BitPcmTo16Bit(pcm: ByteArray): ByteArray {
        val output = ByteArray(pcm.size * BYTES_PER_SAMPLE_16BIT)
        var outputOffset = 0
        pcm.forEach { byte ->
            val sample = ((byte.toInt() and 0xFF) - PCM_8BIT_UNSIGNED_ZERO) shl 8
            writeLittleEndianPcm16(output, outputOffset, sample)
            outputOffset += BYTES_PER_SAMPLE_16BIT
        }
        return output
    }

    private fun convert24BitIntegerPcmTo16Bit(pcm: ByteArray): ByteArray {
        val sampleCount = pcm.size / BYTES_PER_SAMPLE_24BIT
        val output = ByteArray(sampleCount * BYTES_PER_SAMPLE_16BIT)
        var inputOffset = 0
        var outputOffset = 0
        repeat(sampleCount) {
            var sample = (pcm[inputOffset].toInt() and 0xFF) or
                ((pcm[inputOffset + 1].toInt() and 0xFF) shl 8) or
                ((pcm[inputOffset + 2].toInt() and 0xFF) shl 16)
            if ((sample and PCM_24_SIGN_BIT) != 0) {
                sample = sample or PCM_24_SIGN_EXTENSION
            }
            writeLittleEndianPcm16(output, outputOffset, sample shr 8)
            inputOffset += BYTES_PER_SAMPLE_24BIT
            outputOffset += BYTES_PER_SAMPLE_16BIT
        }
        return output
    }

    private fun convert32BitFloatPcmTo16Bit(pcm: ByteArray): ByteArray {
        val sampleCount = pcm.size / BYTES_PER_SAMPLE_32BIT
        val output = ByteArray(sampleCount * BYTES_PER_SAMPLE_16BIT)
        var inputOffset = 0
        var outputOffset = 0
        repeat(sampleCount) {
            val bits = (pcm[inputOffset].toInt() and 0xFF) or
                ((pcm[inputOffset + 1].toInt() and 0xFF) shl 8) or
                ((pcm[inputOffset + 2].toInt() and 0xFF) shl 16) or
                (pcm[inputOffset + 3].toInt() shl 24)
            writeFloatSampleAsPcm16(output, outputOffset, java.lang.Float.intBitsToFloat(bits))
            inputOffset += BYTES_PER_SAMPLE_32BIT
            outputOffset += BYTES_PER_SAMPLE_16BIT
        }
        return output
    }

    private fun convert64BitFloatPcmTo16Bit(pcm: ByteArray): ByteArray {
        val sampleCount = pcm.size / BYTES_PER_SAMPLE_64BIT
        val output = ByteArray(sampleCount * BYTES_PER_SAMPLE_16BIT)
        var inputOffset = 0
        var outputOffset = 0
        repeat(sampleCount) {
            val bits = (pcm[inputOffset].toLong() and 0xFFL) or
                ((pcm[inputOffset + 1].toLong() and 0xFFL) shl 8) or
                ((pcm[inputOffset + 2].toLong() and 0xFFL) shl 16) or
                ((pcm[inputOffset + 3].toLong() and 0xFFL) shl 24) or
                ((pcm[inputOffset + 4].toLong() and 0xFFL) shl 32) or
                ((pcm[inputOffset + 5].toLong() and 0xFFL) shl 40) or
                ((pcm[inputOffset + 6].toLong() and 0xFFL) shl 48) or
                ((pcm[inputOffset + 7].toLong() and 0xFFL) shl 56)
            writeFloatSampleAsPcm16(output, outputOffset, java.lang.Double.longBitsToDouble(bits).toFloat())
            inputOffset += BYTES_PER_SAMPLE_64BIT
            outputOffset += BYTES_PER_SAMPLE_16BIT
        }
        return output
    }

    private fun convert32BitIntegerPcmTo16Bit(pcm: ByteArray): ByteArray {
        val sampleCount = pcm.size / BYTES_PER_SAMPLE_32BIT
        val output = ByteArray(sampleCount * BYTES_PER_SAMPLE_16BIT)
        var inputOffset = 0
        var outputOffset = 0
        repeat(sampleCount) {
            val sample = (pcm[inputOffset].toInt() and 0xFF) or
                ((pcm[inputOffset + 1].toInt() and 0xFF) shl 8) or
                ((pcm[inputOffset + 2].toInt() and 0xFF) shl 16) or
                (pcm[inputOffset + 3].toInt() shl 24)
            writeLittleEndianPcm16(output, outputOffset, sample shr 16)
            inputOffset += BYTES_PER_SAMPLE_32BIT
            outputOffset += BYTES_PER_SAMPLE_16BIT
        }
        return output
    }

    private fun downmixToStereo16Bit(pcm: ByteArray, inputChannels: Int): ByteArray {
        val inputFrameSize = inputChannels * BYTES_PER_SAMPLE_16BIT
        val frameCount = pcm.size / inputFrameSize
        val output = ByteArray(frameCount * MAX_PLAYBACK_CHANNELS * BYTES_PER_SAMPLE_16BIT)
        var inputOffset = 0
        var outputOffset = 0

        repeat(frameCount) {
            var leftSum = 0
            var rightSum = 0
            var leftCount = 0
            var rightCount = 0

            for (channel in 0 until inputChannels) {
                val sampleOffset = inputOffset + channel * BYTES_PER_SAMPLE_16BIT
                val sample = readLittleEndianPcm16(pcm, sampleOffset)
                if (channel % 2 == 0) {
                    leftSum += sample
                    leftCount++
                } else {
                    rightSum += sample
                    rightCount++
                }
            }

            writeLittleEndianPcm16(output, outputOffset, leftSum / leftCount.coerceAtLeast(1))
            writeLittleEndianPcm16(
                output,
                outputOffset + BYTES_PER_SAMPLE_16BIT,
                rightSum / rightCount.coerceAtLeast(1),
            )

            inputOffset += inputFrameSize
            outputOffset += MAX_PLAYBACK_CHANNELS * BYTES_PER_SAMPLE_16BIT
        }
        return output
    }

    private fun readLittleEndianPcm16(pcm: ByteArray, offset: Int): Int =
        ((pcm[offset + 1].toInt() shl 8) or (pcm[offset].toInt() and 0xFF))
            .toShort()
            .toInt()

    private fun writeLittleEndianPcm16(pcm: ByteArray, offset: Int, sample: Int) {
        val clipped = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        pcm[offset] = (clipped and 0xFF).toByte()
        pcm[offset + 1] = ((clipped shr 8) and 0xFF).toByte()
    }

    private fun writeFloatSampleAsPcm16(pcm: ByteArray, offset: Int, sample: Float) {
        val finiteSample = if (sample.isFinite()) sample else 0f
        val clipped = finiteSample.coerceIn(FLOAT_PCM_MIN, FLOAT_PCM_MAX)
        writeLittleEndianPcm16(pcm, offset, (clipped * Short.MAX_VALUE).toInt())
    }

    private fun writeFully(audioTrack: AudioTrack, pcm: ByteArray) {
        var offset = 0
        while (offset < pcm.size) {
            val written = audioTrack.write(
                pcm,
                offset,
                pcm.size - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            if (written < 0) {
                throw IllegalStateException("AudioTrack write failed: $written")
            }
            if (written == 0) {
                Thread.yield()
            } else {
                offset += written
            }
        }
    }

    private fun duplicateStreamNames(streams: List<Stream>): List<String> =
        streams
            .groupBy { it.name.trim().lowercase() }
            .filterKeys { it.isNotBlank() }
            .filterValues { it.size > 1 }
            .values
            .mapNotNull { duplicates -> duplicates.firstOrNull()?.name }

    override fun onServiceDestroyed() {
        udpSocket?.close()
        udpSocket = null
        receiveJob = null
        streamNameCacheJob = null
        receiverSettingsJob?.cancel()
        receiverSettingsJob = null
        streamManagementStarted = false
        receiverTargets = emptyList()
        receiverStreamVolumes.clear()
        receiveRestartStates.clear()
        restartGenerations.clear()
        receiveNetworkSnapshot = ReceiveNetworkSnapshot.EMPTY

        audioTracks.values.forEach(::releaseAudioTrack)
        audioTracks.clear()
        jitterBuffers.values.forEach { it.clear() }
        jitterBuffers.clear()

        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceManager.updateReceiverState(ServiceState.Idle)
        serviceManager.clearReceiverLevel()
        serviceManager.clearStreamRuntimeStatuses(ServiceType.RECEIVER)
        Timber.d("ReceiverService destroyed")
    }

    override fun onUpdateNotification(activeStreamCount: Int) {
        val status = if (activeStreamCount == 0) {
            "no active streams"
        } else {
            "playing $activeStreamCount stream(s)"
        }
        updateNotification(status)
    }

    private fun releaseAudioTrack(audioTrack: AudioTrack?) {
        audioTrack ?: return
        runCatching {
            if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.stop()
            }
        }.onFailure { Timber.w(it, "Failed to stop AudioTrack") }
        audioTrack.release()
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, ServiceManager.RECEIVER_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_receiver_title))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_receiver)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_receiver,
                getString(R.string.notif_action_stop),
                buildStopPendingIntent(),
            )
            .build()

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildStopPendingIntent(): PendingIntent {
        val intent = Intent(this, ReceiverService::class.java).apply {
            action = ServiceManager.ACTION_STOP
        }
        return PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val RECEIVE_NETWORK_SNAPSHOT_TTL_MS = 1_000L
        const val NOTIFICATION_ID = 3
        const val PORT = 6_980
        const val MAX_UDP_DATAGRAM_SIZE = 65_535
        const val JITTER_CAPACITY_PACKETS = 192
        const val JITTER_PREBUFFER_PACKETS = 32
        const val MAX_PLAYBACK_CHANNELS = 2
        const val BYTES_PER_SAMPLE_16BIT = 2
        const val BYTES_PER_SAMPLE_24BIT = 3
        const val BYTES_PER_SAMPLE_32BIT = 4
        const val BYTES_PER_SAMPLE_64BIT = 8
        const val PCM_8BIT_UNSIGNED_ZERO = 128
        const val PCM_24_SIGN_BIT = 0x00800000
        const val PCM_24_SIGN_EXTENSION = -0x01000000
        const val FLOAT_PCM_MIN = -1.0f
        const val FLOAT_PCM_MAX = 1.0f
        const val LEVEL_EMISSION_INTERVAL_MS = 50L
        const val LEVEL_LOG_WINDOW_MS = 1_000L
        const val RESTART_BACKWARD_JUMP_THRESHOLD_PACKETS = 64
        const val RESTART_SILENCE_TIMEOUT_MS = 1_500L
        const val MIN_PLAYBACK_VOLUME = 0.0f
        const val MAX_UNITY_PLAYBACK_VOLUME = 1.0f
    }
}

private class ReceiveRestartState {
    var lastFrameCounter: Int? = null
    var lastPacketAtMs: Long = 0L
}

private data class ReceiveNetworkSnapshot(
    val resolvedNetworks: List<NetworkUtils.ResolvedNetworkSelection>,
    val createdAtMs: Long,
) {
    private val ownAddresses: Set<Inet4Address> = resolvedNetworks.map { it.address }.toSet()

    fun isOwnAddress(address: InetAddress): Boolean =
        (address as? Inet4Address)?.let { it in ownAddresses } == true

    fun originFor(address: InetAddress): NetworkUtils.ResolvedNetworkSelection? {
        val sourceIpv4 = address as? Inet4Address ?: return null
        return resolvedNetworks.firstOrNull { resolved ->
            sourceIpv4.isSameSubnet(resolved.address, resolved.prefixLength)
        }
    }

    companion object {
        val EMPTY = ReceiveNetworkSnapshot(emptyList(), 0L)
    }
}

private fun Inet4Address.isSameSubnet(other: Inet4Address, prefixLength: Int): Boolean {
    if (prefixLength !in 0..IPV4_BITS) return false
    val mask = if (prefixLength == 0) {
        0
    } else {
        -1 shl (IPV4_BITS - prefixLength)
    }
    return (toIpv4Int() and mask) == (other.toIpv4Int() and mask)
}

private fun Inet4Address.toIpv4Int(): Int {
    val bytes = address
    return ((bytes[0].toInt() and 0xFF) shl 24) or
        ((bytes[1].toInt() and 0xFF) shl 16) or
        ((bytes[2].toInt() and 0xFF) shl 8) or
        (bytes[3].toInt() and 0xFF)
}

private data class ReceiverStreamTarget(
    val id: Long,
    val name: String,
    val host: String,
)

private fun String.isWildcardHost(): Boolean =
    isBlank() || this == "0.0.0.0" || this == "*"

private const val IPV4_BITS = 32
