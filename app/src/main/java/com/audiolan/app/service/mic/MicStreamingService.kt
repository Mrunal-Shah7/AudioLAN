package com.audiolan.app.service.mic

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.audiolan.app.R
import com.audiolan.app.domain.model.MicSettings
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MicStreamingService : BaseStreamingService() {
    private var audioRecord: AudioRecord? = null
    private var numChannels: Int = 1
    private var sampleRate: Int = 48_000
    private var bufferSize: Int = 960
    private var streamManagementStarted = false

    override fun serviceType(): ServiceType = ServiceType.MIC

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ServiceManager.ACTION_STOP) {
            serviceManager.stopMicService()
            return START_NOT_STICKY
        }

        serviceManager.updateMicState(ServiceState.Starting)
        startForeground(NOTIFICATION_ID, buildNotification("starting..."))
        acquireWakeLock("AudioLAN::MicService")

        serviceScope.launch {
            try {
                if (ContextCompat.checkSelfPermission(
                        this@MicStreamingService,
                        Manifest.permission.RECORD_AUDIO,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    throw SecurityException("RECORD_AUDIO permission not granted")
                }

                val settings = settingsRepository.getMicSettings().first()
                initialiseMicAudio(settings)
                serviceManager.updateMicState(ServiceState.Running)
                updateNotification("streaming")
                if (!streamManagementStarted) {
                    streamManagementStarted = true
                    startStreamManagement()
                }
            } catch (e: Exception) {
                Timber.e(e, "MicStreamingService failed to initialise")
                serviceManager.updateMicState(ServiceState.Error("Mic init failed: ${e.message}"))
                stopSelf()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun initialiseMicAudio(settings: MicSettings) {
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

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Unsupported microphone settings")
        }
        val actualBufferSize = maxOf(bufferSize, minBufferSize)

        val record = AudioRecord(
            audioSource,
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            actualBufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord failed to initialise")
        }

        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            throw IllegalStateException("AudioRecord failed to start")
        }

        audioRecord = record
        Timber.d(
            "Mic audio initialised sampleRate=$sampleRate channels=$numChannels bufferSize=$actualBufferSize",
        )
    }

    override suspend fun streamLoop(stream: Stream) = withContext(Dispatchers.IO) {
        val recorder = audioRecord ?: throw IllegalStateException("AudioRecord not initialised")
        val settings = settingsRepository.getMicSettings().first()
        if (stream.transportMode == TransportMode.USB_TETHER && !NetworkUtils.hasUsbTetherInterface()) {
            throw SocketException("No USB tethered device connected")
        }
        val samplesPerPacket = VbanNetQuality.calculateSamplesPerPacket(stream.netQuality, numChannels, 16)
        val frameCounter = AtomicInteger(0)
        val socket = DatagramSocket()
        val address = try {
            InetAddress.getByName(stream.host)
        } catch (e: UnknownHostException) {
            socket.close()
            throw SocketException("Unable to resolve host ${stream.host}: ${e.message}")
        }
        val buffer = ByteArray(bufferSize)

        Timber.d("MicStream starting: ${stream.name} -> ${stream.host}:${stream.port}")
        try {
            while (isActive) {
                val bytesRead = recorder.read(buffer, 0, buffer.size)
                when {
                    bytesRead == AudioRecord.ERROR_DEAD_OBJECT -> {
                        Timber.e("AudioRecord ERROR_DEAD_OBJECT in ${stream.name} - terminating stream")
                        throw IOException("AudioRecord dead object")
                    }
                    bytesRead <= 0 -> continue
                }

                val rawPcm = buffer.copyOf(bytesRead)
                val combinedVolume = stream.volume * settings.globalVolume
                if (combinedVolume != 1.0f) {
                    AudioUtils.applyVolume(rawPcm, combinedVolume)
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
            Timber.d("MicStream stopped: ${stream.name}")
        }
    }

    override fun onServiceDestroyed() {
        audioRecord?.let { record ->
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            }.onFailure { Timber.w(it, "Failed to stop AudioRecord") }
            record.release()
        }
        audioRecord = null
        streamManagementStarted = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceManager.updateMicState(ServiceState.Idle)
        Timber.d("MicStreamingService destroyed")
    }

    override fun onUpdateNotification(activeStreamCount: Int) {
        val status = if (activeStreamCount == 0) {
            "no active streams"
        } else {
            "streaming to $activeStreamCount stream(s)"
        }
        updateNotification(status)
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, ServiceManager.MIC_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_mic_title))
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
        val intent = Intent(this, MicStreamingService::class.java).apply {
            action = ServiceManager.ACTION_STOP
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val NOTIFICATION_ID = 1
    }
}
