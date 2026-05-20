package com.audiolan.app.ui

import android.content.Context
import android.os.Build

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.audiolan.app.data.repository.StreamRepository
import com.audiolan.app.data.repository.SettingsRepository
import com.audiolan.app.domain.model.AccentColor
import com.audiolan.app.domain.model.CastSettings
import com.audiolan.app.domain.model.MicSettings
import com.audiolan.app.domain.model.ReceiverSettings
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.service.ServiceManager
import com.audiolan.app.ui.home.HomeScreen
import com.audiolan.app.ui.home.HomeViewModel
import com.audiolan.app.ui.navigation.BottomNavBar
import com.audiolan.app.ui.navigation.Screen
import com.audiolan.app.ui.settings.SettingsScreen
import com.audiolan.app.ui.settings.SettingsViewModel
import com.audiolan.app.ui.streams.StreamDetailScreen
import com.audiolan.app.ui.streams.StreamDetailViewModel
import com.audiolan.app.ui.streams.StreamListScreen
import com.audiolan.app.ui.streams.StreamListViewModel
import com.audiolan.app.ui.theme.AppBackground
import com.audiolan.app.ui.theme.AudioLANTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assume

fun assumeComposeHostCanStayResumed() {
    Assume.assumeFalse(
        "Connected device stops the Compose test host activity immediately",
        Build.MANUFACTURER.equals("motorola", ignoreCase = true) &&
            Build.MODEL.contains("edge 40 neo", ignoreCase = true),
    )
}

class InMemoryStreamRepository(
    initialStreams: List<Stream> = emptyList(),
) : StreamRepository {
    private val streams = MutableStateFlow(initialStreams)
    private var nextId = (initialStreams.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun getStreamsByType(type: ServiceType): Flow<List<Stream>> =
        streams.map { list -> list.filter { it.serviceType == type }.sortedBy { it.name } }

    override suspend fun getEnabledStreamsByType(type: ServiceType): List<Stream> =
        streams.value.filter { it.serviceType == type && it.isEnabled }.sortedBy { it.name }

    override suspend fun getById(id: Long): Stream? =
        streams.value.firstOrNull { it.id == id }

    override suspend fun insertOrUpdate(stream: Stream): Long {
        val id = if (stream.id == 0L) nextId++ else stream.id
        val stored = stream.copy(id = id)
        streams.value = streams.value.filterNot { it.id == id } + stored
        return id
    }

    override suspend fun delete(stream: Stream) {
        streams.value = streams.value.filterNot { it.id == stream.id }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        streams.value = streams.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
    }

    override suspend fun setVolume(id: Long, volume: Float) {
        streams.value = streams.value.map {
            if (it.id == id) it.copy(volume = volume) else it
        }
    }
}

class InMemorySettingsRepository : SettingsRepository {
    private val accentColor = MutableStateFlow(AccentColor.LAVENDER)
    private val micSettings = MutableStateFlow(MicSettings())
    private val castSettings = MutableStateFlow(CastSettings())
    private val receiverSettings = MutableStateFlow(ReceiverSettings())

    override fun getAccentColor(): Flow<AccentColor> = accentColor

    override fun getMicSettings(): Flow<MicSettings> = micSettings

    override fun getCastSettings(): Flow<CastSettings> = castSettings

    override fun getReceiverSettings(): Flow<ReceiverSettings> = receiverSettings

    override suspend fun saveMicSettings(settings: MicSettings) {
        micSettings.value = settings
    }

    override suspend fun saveCastSettings(settings: CastSettings) {
        castSettings.value = settings
    }

    override suspend fun saveReceiverSettings(settings: ReceiverSettings) {
        receiverSettings.value = settings
    }

    override suspend fun saveAccentColor(color: AccentColor) {
        accentColor.value = color
    }
}

@Composable
fun TestHomeApp(context: Context) {
    AudioLANTheme {
        HomeScreen(
            navController = rememberNavController(),
            viewModel = remember { HomeViewModel(ServiceManager(context.applicationContext)) },
        )
    }
}

@Composable
fun TestNavigationApp(repository: InMemoryStreamRepository = InMemoryStreamRepository()) {
    AudioLANTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val bottomBarRoutes = setOf(
            Screen.Home.route,
            Screen.Mic.route,
            Screen.Cast.route,
            Screen.Receiver.route,
            Screen.Settings.route,
        )
        val showBottomBar = currentRoute in bottomBarRoutes

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    BottomNavBar(navController = navController, currentRoute = currentRoute)
                }
            },
            containerColor = AppBackground,
        ) { innerPadding ->
            TestNavGraph(
                navController = navController,
                repository = repository,
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            )
        }
    }
}

@Composable
fun TestStreamApp(repository: InMemoryStreamRepository = InMemoryStreamRepository()) {
    AudioLANTheme {
        val navController = rememberNavController()
        TestNavGraph(navController = navController, repository = repository)
    }
}

@Composable
private fun TestNavGraph(
    navController: NavHostController,
    repository: InMemoryStreamRepository,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            Text("home")
        }
        composable(Screen.Mic.route) {
            StreamListScreen(
                navController = navController,
                serviceTypeName = ServiceType.MIC.name,
                viewModel = remember { StreamListViewModel(repository) },
            )
        }
        composable(Screen.Cast.route) {
            StreamListScreen(
                navController = navController,
                serviceTypeName = ServiceType.CAST.name,
                viewModel = remember { StreamListViewModel(repository) },
            )
        }
        composable(Screen.Receiver.route) {
            StreamListScreen(
                navController = navController,
                serviceTypeName = ServiceType.RECEIVER.name,
                viewModel = remember { StreamListViewModel(repository) },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                viewModel = remember { SettingsViewModel(InMemorySettingsRepository()) },
            )
        }
        composable(Screen.MicSettings.route) {
            Text("microphone settings")
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
            StreamDetailScreen(
                navController = navController,
                streamId = backStackEntry.arguments?.getLong(Screen.StreamDetail.ARG_STREAM_ID) ?: -1L,
                prefilledHost = backStackEntry.arguments
                    ?.getString(Screen.StreamDetail.ARG_HOST)
                    .orEmpty(),
                prefilledStreamName = backStackEntry.arguments
                    ?.getString(Screen.StreamDetail.ARG_STREAM_NAME)
                    .orEmpty(),
                prefilledTransportMode = backStackEntry.arguments
                    ?.getString(Screen.StreamDetail.ARG_TRANSPORT_MODE)
                    .orEmpty(),
                prefilledLowLatency = backStackEntry.arguments
                    ?.getBoolean(Screen.StreamDetail.ARG_LOW_LATENCY) ?: false,
                viewModel = remember { StreamDetailViewModel(repository) },
            )
        }
    }
}
