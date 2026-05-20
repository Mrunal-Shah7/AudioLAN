package com.audiolan.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import com.audiolan.app.service.ServiceManager
import com.audiolan.app.util.ReleaseTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class AudioLANApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree(filesDir))
        }

        Timber.d("AudioLAN application started")
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService<NotificationManager>() ?: return
        val channels = listOf(
            NotificationChannel(
                ServiceManager.MIC_CHANNEL_ID,
                getString(R.string.notification_mic_service_title),
                NotificationManager.IMPORTANCE_LOW,
            ),
            NotificationChannel(
                ServiceManager.CAST_CHANNEL_ID,
                getString(R.string.notification_cast_service_title),
                NotificationManager.IMPORTANCE_LOW,
            ),
            NotificationChannel(
                ServiceManager.RECEIVER_CHANNEL_ID,
                getString(R.string.notification_receiver_service_title),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        notificationManager.createNotificationChannels(channels)
    }
}
