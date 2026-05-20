package com.audiolan.app.ui.streams

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.TransportMode
import com.audiolan.app.ui.navigation.Screen
import com.audiolan.app.ui.settings.components.OptionPickerDialog
import com.audiolan.app.ui.streams.components.DetailRow
import com.audiolan.app.ui.streams.components.NetQualityPickerSheet
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamDetailScreen(
    navController: NavController,
    streamId: Long,
    prefilledHost: String = "",
    prefilledStreamName: String = "",
    prefilledTransportMode: String = "",
    prefilledLowLatency: Boolean = false,
    viewModel: StreamDetailViewModel = hiltViewModel(),
) {
    val serviceTypeName = navController.previousBackStackEntry
        ?.destination
        ?.route
        ?.let { route ->
            when {
                route.startsWith(Screen.Mic.route) -> ServiceType.MIC.name
                route.startsWith(Screen.Cast.route) -> ServiceType.CAST.name
                route.startsWith(Screen.Receiver.route) -> ServiceType.RECEIVER.name
                route.startsWith(Screen.Discovery.route) -> ServiceType.RECEIVER.name
                else -> ServiceType.MIC.name
            }
        } ?: ServiceType.MIC.name
    val serviceType = remember(serviceTypeName) { ServiceType.valueOf(serviceTypeName) }

    LaunchedEffect(streamId, prefilledHost, prefilledStreamName, prefilledTransportMode, prefilledLowLatency) {
        if (streamId != -1L) {
            viewModel.loadStream(streamId)
        } else {
            viewModel.applyPrefill(
                hostValue = prefilledHost,
                streamNameValue = prefilledStreamName,
                transportModeValue = prefilledTransportMode,
                lowLatencyValue = prefilledLowLatency,
            )
        }
    }

    var showNameDialog by remember { mutableStateOf(false) }
    var showHostDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showTransportModeDialog by remember { mutableStateOf(false) }
    var showNetQualitySheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (streamId == -1L) "new stream" else "stream settings",
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
            DetailRow(
                label = "name",
                value = viewModel.name.ifBlank { "tap to set" },
                onClick = { showNameDialog = true },
                errorMessage = viewModel.nameError,
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            DetailRow(
                label = "hostname",
                value = viewModel.host.ifBlank { "tap to set" },
                onClick = { showHostDialog = true },
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            DetailRow(
                label = "port",
                value = viewModel.port,
                onClick = { showPortDialog = true },
                errorMessage = viewModel.portError,
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            DetailRow(
                label = "net quality",
                value = viewModel.netQuality.name.lowercase(Locale.US).replace('_', ' '),
                onClick = { showNetQualitySheet = true },
            )
            Spacer(Modifier.height(Dimensions.RowSpacing))
            DetailRow(
                label = "transport",
                value = viewModel.transportMode.displayName,
                onClick = { showTransportModeDialog = true },
            )
            Spacer(Modifier.height(Dimensions.SpaceXXL))
            Button(
                onClick = { viewModel.save(serviceType) { navController.popBackStack() } },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.SaveButtonMinHeight)
                    .testTag("stream_detail_save"),
            ) {
                Text("save", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showNameDialog) {
        TextInputDialog(
            title = "name",
            initialValue = viewModel.name,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onDismiss = { showNameDialog = false },
            onSave = {
                viewModel.name = it
                viewModel.validate()
                showNameDialog = false
            },
        )
    }
    if (showHostDialog) {
        TextInputDialog(
            title = "hostname",
            initialValue = viewModel.host,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onDismiss = { showHostDialog = false },
            onSave = {
                viewModel.host = it
                showHostDialog = false
            },
        )
    }
    if (showPortDialog) {
        TextInputDialog(
            title = "port",
            initialValue = viewModel.port,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            onDismiss = { showPortDialog = false },
            onSave = {
                viewModel.port = it
                viewModel.validate()
                showPortDialog = false
            },
        )
    }
    if (showNetQualitySheet) {
        NetQualityPickerSheet(
            selected = viewModel.netQuality,
            onSelect = {
                viewModel.netQuality = it
            },
            onDismiss = { showNetQualitySheet = false },
        )
    }
    if (showTransportModeDialog) {
        OptionPickerDialog(
            title = "transport",
            options = TransportMode.entries,
            selected = viewModel.transportMode,
            labelFor = { it.displayName },
            onSelect = { viewModel.transportMode = it },
            onDismiss = { showTransportModeDialog = false },
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    keyboardOptions: KeyboardOptions,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
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
                keyboardOptions = keyboardOptions,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value) },
                modifier = Modifier.testTag("${title}_dialog_save"),
            ) {
                Text(
                    text = "save",
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
