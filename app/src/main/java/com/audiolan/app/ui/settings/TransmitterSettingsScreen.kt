package com.audiolan.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.audiolan.app.ui.settings.components.OptionPickerDialog
import com.audiolan.app.ui.settings.components.SettingsRow
import com.audiolan.app.ui.streams.components.VolumeSliderRow
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransmitterSettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val settings by viewModel.transmitterSettings.collectAsStateWithLifecycle()
    var showAudioSourceDialog by remember { mutableStateOf(false) }
    var showInputChannelDialog by remember { mutableStateOf(false) }
    var showSampleRateDialog by remember { mutableStateOf(false) }
    var showBufferSizeDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "transmitter settings",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
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
            SettingsRow(
                label = "audio source",
                value = audioSourceLabel(settings.audioSource),
                requiresRestart = true,
                onClick = { showAudioSourceDialog = true },
            )
            Text(
                text = "applies to microphone streams only",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(
                    start = Dimensions.RowHorizontalPadding,
                    top = Dimensions.SpaceXXS,
                ),
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            SettingsRow(
                label = "input channel",
                value = settings.inputChannel.lowercase(Locale.US),
                requiresRestart = true,
                onClick = { showInputChannelDialog = true },
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
            Spacer(Modifier.height(Dimensions.RowSpacing))
            TransmitterVolumeCard(
                volume = settings.globalVolume,
                onVolumeChange = viewModel::setTransmitterGlobalVolume,
                onReset = viewModel::resetTransmitterGlobalVolume,
            )
            Spacer(Modifier.height(Dimensions.SpaceM))
        }
    }

    if (showAudioSourceDialog) {
        OptionPickerDialog<String>(
            title = "audio source",
            options = listOf("DEFAULT", "VOICE_COMM"),
            selected = settings.audioSource,
            labelFor = ::audioSourceLabel,
            onSelect = viewModel::setTransmitterAudioSource,
            onDismiss = { showAudioSourceDialog = false },
        )
    }
    if (showInputChannelDialog) {
        OptionPickerDialog<String>(
            title = "input channel",
            options = listOf("MONO", "STEREO"),
            selected = settings.inputChannel,
            labelFor = { it.lowercase(Locale.US) },
            onSelect = viewModel::setTransmitterInputChannel,
            onDismiss = { showInputChannelDialog = false },
        )
    }
    if (showSampleRateDialog) {
        OptionPickerDialog<Int>(
            title = "sample rate",
            options = listOf(44_100, 48_000),
            selected = settings.sampleRate,
            labelFor = { "$it Hz" },
            onSelect = viewModel::setTransmitterSampleRate,
            onDismiss = { showSampleRateDialog = false },
        )
    }
    if (showBufferSizeDialog) {
        BufferSizeDialog(
            title = "buffer size",
            initialValue = settings.bufferSize.toString(),
            onDismiss = { showBufferSizeDialog = false },
            onSave = { bufferSize ->
                viewModel.setTransmitterBufferSize(bufferSize)
                showBufferSizeDialog = false
            },
        )
    }
}

@Composable
private fun TransmitterVolumeCard(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AudioLANSurface,
        shape = RoundedCornerShape(Dimensions.RowCornerRadius),
        border = BorderStroke(Dimensions.CardBorderWidth, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Dimensions.RowHorizontalPadding,
                vertical = Dimensions.RowVerticalPadding,
            ),
        ) {
            Text(
                text = "global volume",
                color = TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "(requires restart)",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(Dimensions.SpaceXS))
            VolumeSliderRow(
                volume = volume,
                onVolumeChange = onVolumeChange,
                onReset = onReset,
                contentDescription = "global transmitter volume, current value ${
                    String.format(Locale.US, "%.0f", volume * 100)
                } percent",
            )
        }
    }
}

@Composable
fun BufferSizeDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AudioLANSurface,
        title = {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    value.toIntOrNull()?.let(onSave)
                },
            ) {
                Text(
                    text = "done",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "cancel",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}

private fun audioSourceLabel(value: String): String = when (value) {
    "VOICE_COMM" -> "voice communication"
    else -> "default"
}
