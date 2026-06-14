package com.audiolan.app.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Transmitter : Screen("transmitter")
    data object Receiver : Screen("receiver")
    data object Settings : Screen("settings")
    data object Discovery : Screen("discovery")

    data object StreamDetail :
        Screen(
            "stream_detail?streamId={streamId}&host={host}&streamName={streamName}" +
                "&transportMode={transportMode}&lowLatency={lowLatency}",
        ) {
        const val ARG_STREAM_ID = "streamId"
        const val ARG_HOST = "host"
        const val ARG_STREAM_NAME = "streamName"
        const val ARG_TRANSPORT_MODE = "transportMode"
        const val ARG_LOW_LATENCY = "lowLatency"

        fun createRoute(
            streamId: Long = -1L,
            host: String = "",
            streamName: String = "",
            transportMode: String = "",
            lowLatency: Boolean = false,
        ): String =
            "stream_detail?streamId=$streamId&host=${Uri.encode(host)}&streamName=${Uri.encode(streamName)}" +
                "&transportMode=${Uri.encode(transportMode)}&lowLatency=$lowLatency"

        fun createRoute(): String = createRoute(streamId = -1L)
    }

    data object TransmitterSettings : Screen("settings/transmitter")
    data object ReceiverSettings : Screen("settings/receiver")
}
