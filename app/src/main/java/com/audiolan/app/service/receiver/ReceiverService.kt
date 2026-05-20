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
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.domain.model.TransportMode
import com.audiolan.app.domain.vban.VbanDecoder
import com.audiolan.app.service.ServiceManager
import com.audiolan.app.service.base.BaseStreamingService
import com.audiolan.app.util.AudioUtils
import com.audiolan.app.util.NetworkUtils
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
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
    private var streamManagementStarted = false

    private val jitterBuffers = ConcurrentHashMap<Long, JitterBuffer>()
    private val audioTracks = ConcurrentHashMap<Long, AudioTrack>()
    private val enabledStreamsByName = ConcurrentHashMap<String, Long>()

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
            enabledStreamsByName.clear()
            streams
                .filter { it.isEnabled }
                .forEach { stream -> enabledStreamsByName[stream.name] = stream.id }
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
                val matchedStreamId = findMatchingStreamId(header.streamName)
                if (matchedStreamId != null) {
                    jitterBuffers[matchedStreamId]?.offer(ReceiverPacket(header, pcm))
                } else {
                    forwardToDiscovery(senderIp, header.streamName)
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

    private fun findMatchingStreamId(streamName: String): Long? =
        enabledStreamsByName[streamName]

    private fun forwardToDiscovery(ip: String, streamName: String) {
        discoveredDevicesRepository.emit(
            DiscoveredDevice(
                ip = ip,
                deviceName = null,
                streamName = streamName,
                source = DiscoverySource.VBAN_SNIFF,
            ),
        )
    }

    override suspend fun streamLoop(stream: Stream) = withContext(Dispatchers.IO) {
        if (stream.transportMode == TransportMode.USB_TETHER && !NetworkUtils.hasUsbTetherInterface()) {
            throw SocketException("No USB tethered device connected")
        }
        val jitterBuffer = JitterBuffer(
            capacityPackets = JITTER_CAPACITY_PACKETS,
            prebufferPackets = JITTER_PREBUFFER_PACKETS,
        )
        jitterBuffers[stream.id] = jitterBuffer

        var localAudioTrack: AudioTrack? = null
        var configuredSampleRate: Int? = null
        var configuredChannels: Int? = null
        val settings = settingsRepository.getReceiverSettings().first()

        Timber.d("ReceiverService: playback loop started for stream ${stream.name}")
        try {
            while (isActive) {
                val packet = jitterBuffer.poll()
                if (packet == null) {
                    delay(1)
                    continue
                }

                if (packet.header.bitsPerSample != 16) {
                    Timber.w("Unsupported VBAN bit depth for ${stream.name}: ${packet.header.bitsPerSample}")
                    continue
                }

                if (localAudioTrack == null) {
                    localAudioTrack = initialiseAudioTrack(stream, packet).also { track ->
                        audioTracks[stream.id] = track
                        configuredSampleRate = packet.header.sampleRateHz
                        configuredChannels = packet.header.numChannels
                    }
                }

                if (
                    packet.header.sampleRateHz != configuredSampleRate ||
                    packet.header.numChannels != configuredChannels
                ) {
                    Timber.w("Format mismatch for ${stream.name} - ignoring packet with different parameters")
                    continue
                }

                val playbackPcm = preparePcmForPlayback(packet)
                val combinedVolume = stream.volume * settings.globalVolume
                val pcm = if (combinedVolume != 1.0f) {
                    playbackPcm.copyOf().also { AudioUtils.applyVolume(it, combinedVolume) }
                } else {
                    playbackPcm
                }
                localAudioTrack?.let { writeFully(it, pcm) }
            }
        } finally {
            audioTracks.remove(stream.id)?.let(::releaseAudioTrack)
            jitterBuffers.remove(stream.id)?.clear()
            Timber.d("ReceiverService: playback loop ended for ${stream.name}")
        }
    }

    private fun initialiseAudioTrack(stream: Stream, packet: ReceiverPacket): AudioTrack {
        val playbackChannels = packet.header.numChannels.coerceAtMost(MAX_PLAYBACK_CHANNELS)
        val track = AudioTrackFactory.create(packet.header.sampleRateHz, playbackChannels)
        track.play()
        Timber.d(
            "AudioTrack initialised for ${stream.name}: " +
                "${packet.header.sampleRateHz}Hz, ${packet.header.numChannels}ch input, " +
                "${playbackChannels}ch playback",
        )
        return track
    }

    private fun preparePcmForPlayback(packet: ReceiverPacket): ByteArray =
        if (packet.header.numChannels <= MAX_PLAYBACK_CHANNELS) {
            packet.pcm
        } else {
            downmixToStereo16Bit(packet.pcm, packet.header.numChannels)
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

    override fun onServiceDestroyed() {
        udpSocket?.close()
        udpSocket = null
        receiveJob = null
        streamNameCacheJob = null
        streamManagementStarted = false
        enabledStreamsByName.clear()

        audioTracks.values.forEach(::releaseAudioTrack)
        audioTracks.clear()
        jitterBuffers.values.forEach { it.clear() }
        jitterBuffers.clear()

        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceManager.updateReceiverState(ServiceState.Idle)
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
        const val NOTIFICATION_ID = 3
        const val PORT = 6_980
        const val MAX_UDP_DATAGRAM_SIZE = 65_535
        const val JITTER_CAPACITY_PACKETS = 96
        const val JITTER_PREBUFFER_PACKETS = 16
        const val MAX_PLAYBACK_CHANNELS = 2
        const val BYTES_PER_SAMPLE_16BIT = 2
    }
}
