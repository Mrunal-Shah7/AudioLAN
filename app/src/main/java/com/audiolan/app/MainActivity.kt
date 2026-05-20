package com.audiolan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.audiolan.app.domain.model.AccentColor
import com.audiolan.app.service.ServiceEntryPoint
import com.audiolan.app.ui.navigation.AppNavGraph
import com.audiolan.app.ui.navigation.BottomNavBar
import com.audiolan.app.ui.navigation.Screen
import com.audiolan.app.ui.theme.AudioLANTheme
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsRepository by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, ServiceEntryPoint::class.java)
            .settingsRepository()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val accentColor by settingsRepository
                .getAccentColor()
                .collectAsStateWithLifecycle(initialValue = AccentColor.LAVENDER)
            val amoledMode by settingsRepository
                .getAmoledMode()
                .collectAsStateWithLifecycle(initialValue = false)

            AudioLANTheme(accentColor = accentColor, amoledMode = amoledMode) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val bottomBarHiddenRoutes = setOf(
                    Screen.StreamDetail.route,
                    Screen.Discovery.route,
                    Screen.MicSettings.route,
                    Screen.CastSettings.route,
                    Screen.ReceiverSettings.route,
                )
                val bottomBarRoutes = setOf(
                    Screen.Home.route,
                    Screen.Mic.route,
                    Screen.Cast.route,
                    Screen.Receiver.route,
                    Screen.Settings.route,
                )
                val showBottomBar =
                    bottomBarHiddenRoutes.none {
                        currentRoute?.startsWith(it.substringBefore("?")) == true
                    } && currentRoute in bottomBarRoutes

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(
                                navController = navController,
                                currentRoute = currentRoute,
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    AppNavGraph(
                        navController = navController,
                        modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                    )
                }
            }
        }
    }
}
