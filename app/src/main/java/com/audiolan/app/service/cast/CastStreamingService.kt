package com.audiolan.app.service.cast

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.audiolan.app.R
import com.audiolan.app.domain.model.CastSettings
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class CastStreamingService : BaseStreamingService() {
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var numChannels: Int = 2
    private var sampleRate: Int = 48_000
    private var bufferSize: Int = 960
    private var streamManagementStarted = false

    override fun serviceType(): ServiceType = ServiceType.CAST

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ServiceManager.ACTION_STOP) {
            serviceManager.stopCastService()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(ServiceManager.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.resultData()
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Timber.e("CastStreamingService: invalid MediaProjection token - stopping")
            serviceManager.updateCastState(ServiceState.Error("MediaProjection consent missing"))
            stopSelf()
            return START_NOT_STICKY
        }

        serviceManager.updateCastState(ServiceState.Starting)
        startForeground(NOTIFICATION_ID, buildNotification("starting..."))
        acquireWakeLock("AudioLAN::CastService")

        serviceScope.launch {
            try {
                initialiseCastAudio(resultCode, resultData)
                val record = audioRecord ?: throw IllegalStateException("AudioPlaybackCapture AudioRecord not initialised")
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw IllegalStateException("AudioPlaybackCapture AudioRecord failed to start")
                }
                serviceManager.updateCastState(ServiceState.Running)
                updateNotification("streaming")
                if (!streamManagementStarted) {
                    streamManagementStarted = true
                    startStreamManagement()
                }
            } catch (e: Exception) {
                Timber.e(e, "CastStreamingService failed to initialise")
                serviceManager.updateCastState(ServiceState.Error("Cast init failed: ${e.message}"))
                stopSelf()
            }
        }
        return START_STICKY
    }

    private suspend fun initialiseCastAudio(resultCode: Int, resultData: Intent) {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
            ?: throw IllegalStateException("MediaProjection token rejected")
        mediaProjection = projection

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        val settings = settingsRepository.getCastSettings().first()
        val channelMask = when (settings.channelOut) {
            "MONO" -> AudioFormat.CHANNEL_IN_MONO
            else -> AudioFormat.CHANNEL_IN_STEREO
        }
        numChannels = if (settings.channelOut == "MONO") 1 else 2
        sampleRate = settings.sampleRate
        bufferSize = settings.bufferSize

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Unsupported cast capture settings")
        }
        val actualBufferSize = maxOf(bufferSize, minBufferSize)
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

        audioRecord = record
        Timber.d(
            "Cast audio initialised sampleRate=$sampleRate channels=$numChannels bufferSize=$actualBufferSize",
        )
    }

    override suspend fun streamLoop(stream: Stream) = withContext(Dispatchers.IO) {
        val recorder = audioRecord ?: throw IllegalStateException("AudioPlaybackCapture AudioRecord not initialised")
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
        var consecutiveEmptyReads = 0

        Timber.d("CastStream starting: ${stream.name} -> ${stream.host}:${stream.port}")
        try {
            while (isActive) {
                val bytesRead = recorder.read(buffer, 0, buffer.size)
                when {
                    bytesRead > 0 -> consecutiveEmptyReads = 0
                    bytesRead == 0 -> {
                        consecutiveEmptyReads++
                        if (consecutiveEmptyReads == EMPTY_READ_WARNING_THRESHOLD) {
                            Timber.w(
                                "CastStream ${stream.name}: $EMPTY_READ_WARNING_THRESHOLD consecutive empty reads - " +
                                    "apps may be blocking capture",
                            )
                        }
                        continue
                    }
                    bytesRead == AudioRecord.ERROR_DEAD_OBJECT -> {
                        serviceManager.updateCastState(ServiceState.Error("MediaProjection revoked"))
                        Timber.e("AudioRecord ERROR_DEAD_OBJECT in ${stream.name} - terminating stream")
                        throw IOException("AudioRecord dead object")
                    }
                    else -> continue
                }

                val rawPcm = buffer.copyOf(bytesRead)
                if (stream.volume != 1.0f) {
                    AudioUtils.applyVolume(rawPcm, stream.volume)
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
            Timber.d("CastStream stopped: ${stream.name}")
        }
    }

    override fun onServiceDestroyed() {
        audioRecord?.let { record ->
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            }.onFailure { Timber.w(it, "Failed to stop cast AudioRecord") }
            record.release()
        }
        audioRecord = null
        releaseMediaProjection()
        streamManagementStarted = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceManager.updateCastState(ServiceState.Idle)
        Timber.d("CastStreamingService destroyed")
    }

    override fun onUpdateNotification(activeStreamCount: Int) {
        val status = if (activeStreamCount == 0) {
            "no active streams"
        } else {
            "streaming to $activeStreamCount stream(s)"
        }
        updateNotification(status)
    }

    private fun releaseMediaProjection() {
        mediaProjection?.let { projection ->
            runCatching { projection.stop() }
                .onFailure { Timber.w(it, "Failed to stop MediaProjection") }
        }
        mediaProjection = null
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, ServiceManager.CAST_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_cast_title))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_cast)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_cast,
                getString(R.string.notif_action_stop),
                buildStopPendingIntent(),
            )
            .build()

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildStopPendingIntent(): PendingIntent {
        val intent = Intent(this, CastStreamingService::class.java).apply {
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
        const val NOTIFICATION_ID = 2
        const val EMPTY_READ_WARNING_THRESHOLD = 100
    }
}
