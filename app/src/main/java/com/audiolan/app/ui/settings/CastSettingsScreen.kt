package com.audiolan.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.audiolan.app.ui.settings.components.OptionPickerDialog
import com.audiolan.app.ui.settings.components.SettingsRow
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.TextPrimary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastSettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.castSettings.collectAsStateWithLifecycle()
    var showChannelOutDialog by remember { mutableStateOf(false) }
    var showSampleRateDialog by remember { mutableStateOf(false) }
    var showBufferSizeDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "cast settings",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "navigate back",
                            tint = TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .navigationBarsPadding()
                .padding(horizontal = Dimensions.ScreenHorizontalPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Dimensions.ScreenVerticalPadding),
        ) {
            Spacer(Modifier.height(Dimensions.SpaceM))
            SettingsRow(
                label = "channel out",
                value = settings.channelOut.lowercase(Locale.US),
                requiresRestart = true,
                onClick = { showChannelOutDialog = true },
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            SettingsRow(
                label = "sample rate",
                value = "${settings.sampleRate} Hz",
                requiresRestart = true,
                onClick = { showSampleRateDialog = true },
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            SettingsRow(
                label = "encoding",
                value = "16-bit PCM",
                requiresRestart = true,
                readOnly = true,
                onClick = {},
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            SettingsRow(
                label = "buffer size",
                value = "${settings.bufferSize} bytes",
                requiresRestart = true,
                onClick = { showBufferSizeDialog = true },
            )
            Spacer(Modifier.height(Dimensions.SpaceM))
        }
    }

    if (showChannelOutDialog) {
        OptionPickerDialog<String>(
            title = "channel out",
            options = listOf("MONO", "STEREO"),
            selected = settings.channelOut,
            labelFor = { it.lowercase(Locale.US) },
            onSelect = viewModel::setCastChannelOut,
            onDismiss = { showChannelOutDialog = false },
        )
    }
    if (showSampleRateDialog) {
        OptionPickerDialog<Int>(
            title = "sample rate",
            options = listOf(44_100, 48_000),
            selected = settings.sampleRate,
            labelFor = { "$it Hz" },
            onSelect = viewModel::setCastSampleRate,
            onDismiss = { showSampleRateDialog = false },
        )
    }
    if (showBufferSizeDialog) {
        BufferSizeDialog(
            title = "buffer size",
            initialValue = settings.bufferSize.toString(),
            onDismiss = { showBufferSizeDialog = false },
            onSave = { bufferSize ->
                viewModel.setCastBufferSize(bufferSize)
                showBufferSizeDialog = false
            },
        )
    }
}
