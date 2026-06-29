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
                "&networkHint={networkHint}&lowLatency={lowLatency}",
        ) {
        const val ARG_STREAM_ID = "streamId"
        const val ARG_HOST = "host"
        const val ARG_STREAM_NAME = "streamName"
        const val ARG_NETWORK_HINT = "networkHint"
        const val ARG_LOW_LATENCY = "lowLatency"

        fun createRoute(
            streamId: Long = -1L,
            host: String = "",
            streamName: String = "",
            networkHint: String = "",
            lowLatency: Boolean = false,
        ): String =
            "stream_detail?streamId=$streamId&host=${Uri.encode(host)}&streamName=${Uri.encode(streamName)}" +
                "&networkHint=${Uri.encode(networkHint)}&lowLatency=$lowLatency"

        fun createRoute(): String = createRoute(streamId = -1L)
    }

    data object TransmitterSettings : Screen("settings/transmitter")
    data object ReceiverSettings : Screen("settings/receiver")
}
