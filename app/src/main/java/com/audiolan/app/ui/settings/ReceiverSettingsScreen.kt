package com.audiolan.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.audiolan.app.ui.streams.components.VolumeSliderRow
import com.audiolan.app.ui.theme.Dimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverSettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val settings by viewModel.receiverSettings.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "receiver settings",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "navigate back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .navigationBarsPadding()
                .padding(horizontal = Dimensions.ScreenHorizontalPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Dimensions.ScreenVerticalPadding),
        ) {
            Spacer(Modifier.height(Dimensions.SpaceM))
            ReceiverVolumeCard(
                volume = settings.globalVolume,
                onVolumeChange = viewModel::setReceiverGlobalVolume,
                onReset = viewModel::resetReceiverGlobalVolume,
            )
            Spacer(Modifier.height(Dimensions.SpaceM))
        }
    }
}

@Composable
private fun ReceiverVolumeCard(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Dimensions.RowHorizontalPadding,
                vertical = Dimensions.RowVerticalPadding,
            ),
        ) {
            Text(
                text = "global volume",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(Dimensions.SpaceXS))
            VolumeSliderRow(
                volume = volume,
                onVolumeChange = onVolumeChange,
                onReset = onReset,
                contentDescription = "global receiver volume, current value ${
                    String.format(java.util.Locale.US, "%.0f", volume * 100)
                } percent",
            )
        }
    }
}
