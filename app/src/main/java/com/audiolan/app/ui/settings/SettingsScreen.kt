package com.audiolan.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.audiolan.app.ui.navigation.Screen
import com.audiolan.app.ui.settings.components.AccentColorPickerSheet
import com.audiolan.app.ui.settings.components.SettingsRow
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.TextPrimary

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var showAccentColorSheet by remember { mutableStateOf(false) }
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val amoledMode by viewModel.amoledMode.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(bottom = Dimensions.BottomNavHeight)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                )
                .padding(bottom = Dimensions.ScreenVerticalPadding),
        ) {
            Text(
                text = "settings",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = Dimensions.SpaceM),
            )
            SettingsRow(
                label = "microphone settings",
                value = "audio source, channels, sample rate, buffer",
                onClick = { navController.navigate(Screen.MicSettings.route) },
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            SettingsRow(
                label = "cast settings",
                value = "channels, sample rate, buffer",
                onClick = { navController.navigate(Screen.CastSettings.route) },
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            SettingsRow(
                label = "receiver settings",
                value = "global volume",
                onClick = { navController.navigate(Screen.ReceiverSettings.route) },
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            SettingsRow(
                label = "accent color",
                value = accentColor.displayName,
                onClick = { showAccentColorSheet = true },
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            SettingsRow(
                label = "amoled mode",
                value = if (amoledMode) "enabled" else "disabled",
                onClick = { viewModel.setAmoledMode(!amoledMode) },
            )
        }
    }

    if (showAccentColorSheet) {
        AccentColorPickerSheet(
            selected = accentColor,
            onSelect = viewModel::setAccentColor,
            onDismiss = { showAccentColorSheet = false },
        )
    }
}
