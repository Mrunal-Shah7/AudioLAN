package com.audiolan.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.ui.discovery.DiscoveryScreen
import com.audiolan.app.ui.home.HomeScreen
import com.audiolan.app.ui.settings.CastSettingsScreen
import com.audiolan.app.ui.settings.MicSettingsScreen
import com.audiolan.app.ui.settings.ReceiverSettingsScreen
import com.audiolan.app.ui.settings.SettingsScreen
import com.audiolan.app.ui.streams.StreamDetailScreen
import com.audiolan.app.ui.streams.StreamListScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(durationMillis = 120))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 90))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(durationMillis = 120))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 90))
        },
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        composable(Screen.Mic.route) {
            StreamListScreen(navController, serviceTypeName = ServiceType.MIC.name)
        }
        composable(Screen.Cast.route) {
            StreamListScreen(navController, serviceTypeName = ServiceType.CAST.name)
        }
        composable(Screen.Receiver.route) {
            StreamListScreen(navController, serviceTypeName = ServiceType.RECEIVER.name)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController)
        }
        composable(Screen.Discovery.route) {
            DiscoveryScreen(navController)
        }
        composable(Screen.MicSettings.route) {
            MicSettingsScreen(navController)
        }
        composable(Screen.CastSettings.route) {
            CastSettingsScreen(navController)
        }
        composable(Screen.ReceiverSettings.route) {
            ReceiverSettingsScreen(navController)
        }
        composable(
            route = Screen.StreamDetail.route,
            arguments = listOf(
                navArgument(Screen.StreamDetail.ARG_STREAM_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                    nullable = false
                },
                navArgument(Screen.StreamDetail.ARG_HOST) {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = false
                },
                navArgument(Screen.StreamDetail.ARG_STREAM_NAME) {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = false
                },
                navArgument(Screen.StreamDetail.ARG_TRANSPORT_MODE) {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = false
                },
                navArgument(Screen.StreamDetail.ARG_LOW_LATENCY) {
                    type = NavType.BoolType
                    defaultValue = false
                    nullable = false
                },
            ),
        ) { backStackEntry ->
            val streamId = backStackEntry.arguments?.getLong(Screen.StreamDetail.ARG_STREAM_ID) ?: -1L
            val host = backStackEntry.arguments?.getString(Screen.StreamDetail.ARG_HOST).orEmpty()
            val streamName = backStackEntry.arguments?.getString(Screen.StreamDetail.ARG_STREAM_NAME).orEmpty()
            val transportMode = backStackEntry.arguments?.getString(Screen.StreamDetail.ARG_TRANSPORT_MODE).orEmpty()
            val lowLatency = backStackEntry.arguments?.getBoolean(Screen.StreamDetail.ARG_LOW_LATENCY) ?: false
            StreamDetailScreen(
                navController = navController,
                streamId = streamId,
                prefilledHost = host,
                prefilledStreamName = streamName,
                prefilledTransportMode = transportMode,
                prefilledLowLatency = lowLatency,
            )
        }
    }
}
