package com.audiolan.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.TextSecondary
import androidx.compose.ui.graphics.Color

@Stable
private data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, Icons.Default.Home, "home"),
    BottomNavItem(Screen.Transmitter.route, Icons.Default.SettingsVoice, "transmitter"),
    BottomNavItem(Screen.Receiver.route, Icons.Default.CellTower, "receiver"),
    BottomNavItem(Screen.Settings.route, Icons.Default.Settings, "settings"),
)

@Composable
fun BottomNavBar(
    navController: NavController,
    currentRoute: String?,
) {
    Column {
        HorizontalDivider(
            thickness = Dimensions.BottomNavBorderWidth,
            color = CardBorder,
        )
        NavigationBar(
            modifier = Modifier
                .navigationBarsPadding()
                .height(Dimensions.BottomNavHeight)
                .testTag("bottom_navigation"),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                val itemColor = if (selected) MaterialTheme.colorScheme.primary else TextSecondary
                NavigationBarItem(
                    modifier = Modifier.testTag("bottom_nav_${item.label}"),
                    selected = selected,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = itemColor,
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            color = itemColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                    ),
                )
            }
        }
    }
}
