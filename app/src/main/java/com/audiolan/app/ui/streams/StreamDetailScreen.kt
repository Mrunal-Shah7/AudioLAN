package com.audiolan.app.ui.streams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.audiolan.app.domain.model.NetQuality
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.SourceType
import com.audiolan.app.domain.model.TransportMode
import com.audiolan.app.ui.navigation.Screen
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.LocalToggleThumbColor
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary

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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val previousRoute = navController.previousBackStackEntry
        ?.destination
        ?.route
    val openedFromDiscovery = previousRoute?.startsWith(Screen.Discovery.route) == true
    val serviceTypeName = previousRoute
        ?.let { route ->
            when {
                route.startsWith(Screen.Transmitter.route) -> ServiceType.TRANSMITTER.name
                route.startsWith(Screen.Receiver.route) -> ServiceType.RECEIVER.name
                route.startsWith(Screen.Discovery.route) -> ServiceType.RECEIVER.name
                else -> ServiceType.TRANSMITTER.name
            }
        } ?: ServiceType.TRANSMITTER.name
    val serviceType = remember(serviceTypeName) { ServiceType.valueOf(serviceTypeName) }
    val serviceLabel = if (serviceType == ServiceType.TRANSMITTER) "transmitter" else "receiver"

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

    val saveEnabled = viewModel.name.isNotBlank() &&
        viewModel.nameError == null &&
        viewModel.portError == null &&
        viewModel.port.toIntOrNull() != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (streamId == -1L) {
                            "New $serviceLabel"
                        } else {
                            "Edit $serviceLabel"
                        },
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
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
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = {
                        viewModel.save(serviceType) {
                            if (openedFromDiscovery && serviceType == ServiceType.RECEIVER) {
                                val returnedToReceiver = navController.popBackStack(
                                    route = Screen.Receiver.route,
                                    inclusive = false,
                                )
                                if (!returnedToReceiver) {
                                    navController.navigate(Screen.Receiver.route) {
                                        launchSingleTop = true
                                    }
                                }
                            } else {
                                navController.popBackStack()
                            }
                        }
                    },
                    enabled = saveEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    ),
                    shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(Dimensions.ScreenHorizontalPadding)
                        .heightIn(min = Dimensions.ButtonMinHeight)
                        .testTag("stream_detail_save"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Dimensions.SpaceXS))
                    Text("Save", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.ScreenHorizontalPadding)
                .padding(bottom = innerPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.RowSpacing),
        ) {
            Spacer(Modifier.height(Dimensions.SpaceXS))
            AudioTextField(
                label = "Name",
                value = viewModel.name,
                onValueChange = {
                    viewModel.name = it
                    viewModel.validate()
                },
                errorMessage = viewModel.nameError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            AudioTextField(
                label = "Hostname",
                value = if (serviceType == ServiceType.TRANSMITTER && viewModel.broadcastMode) {
                    "all devices on network"
                } else {
                    viewModel.host
                },
                onValueChange = {
                    viewModel.host = it
                },
                readOnly = serviceType == ServiceType.TRANSMITTER && viewModel.broadcastMode,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            if (serviceType == ServiceType.TRANSMITTER) {
                SourceDropdown(
                    selected = viewModel.sourceType,
                    onSelect = {
                        viewModel.sourceType = it
                    },
                )
                BroadcastRow(
                    checked = viewModel.broadcastMode,
                    onCheckedChange = {
                        viewModel.broadcastMode = it
                    },
                )
            }
            AudioTextField(
                label = "Port",
                value = viewModel.port,
                onValueChange = {
                    viewModel.port = it
                    viewModel.validate()
                },
                errorMessage = viewModel.portError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
            SegmentedField(label = "Net quality") {
                SegmentedControl(
                    selected = netQualityUiValue(viewModel.netQuality),
                    options = netQualityOptions,
                    onSelect = {
                        viewModel.netQuality = it
                    },
                )
            }
            SegmentedField(label = "Transport") {
                SegmentedControl(
                    selected = viewModel.transportMode,
                    options = transportOptions,
                    onSelect = {
                        viewModel.transportMode = it
                    },
                )
            }
            Spacer(Modifier.height(Dimensions.SpaceL))
        }
    }
}

@Composable
private fun AudioTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        enabled = true,
        singleLine = true,
        maxLines = 1,
        isError = errorMessage != null,
        supportingText = errorMessage?.let {
            {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        label = {
            Text(label)
        },
        keyboardOptions = keyboardOptions,
        colors = audioTextFieldColors(),
        shape = RoundedCornerShape(Dimensions.InputCornerRadius),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceDropdown(
    selected: SourceType,
    onSelect: (SourceType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = sourceTypeLabel(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Source") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = audioTextFieldColors(),
            shape = RoundedCornerShape(Dimensions.InputCornerRadius),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SourceType.entries.forEach { sourceType ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = sourceTypeLabel(sourceType),
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        onSelect(sourceType)
                        expanded = false
                    },
                    trailingIcon = if (sourceType == selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun BroadcastRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        color = AudioLANSurface,
        shape = RoundedCornerShape(Dimensions.InputCornerRadius),
        border = BorderStroke(Dimensions.CardBorderWidth, CardBorder),
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Broadcast",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Send to every client on the network",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = switchColors(),
            )
        }
    }
}

@Composable
private fun SegmentedField(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceXS)) {
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun <T> SegmentedControl(
    selected: T,
    options: List<SegmentedOption<T>>,
    onSelect: (T) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        color = AudioLANSurface,
        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
        border = BorderStroke(Dimensions.CardBorderWidth, CardBorder),
    ) {
        Row(modifier = Modifier.clip(RoundedCornerShape(Dimensions.ButtonCornerRadius))) {
            options.forEachIndexed { index, option ->
                Segment(
                    option = option,
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    modifier = Modifier.weight(1f),
                )
                if (index != options.lastIndex) {
                    VerticalDivider(
                        modifier = Modifier
                            .width(Dimensions.CardBorderWidth)
                            .height(48.dp),
                        color = CardBorder,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> Segment(
    option: SegmentedOption<T>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else TextSecondary
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (selected) MaterialTheme.colorScheme.primary else AudioLANSurface,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val icon = if (selected) Icons.Default.Check else option.icon
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(Dimensions.SpaceXXS))
                }
                Text(
                    text = option.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun audioTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = CardBorder,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = TextSecondary,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = AudioLANSurface,
    unfocusedContainerColor = AudioLANSurface,
    errorContainerColor = AudioLANSurface,
    errorBorderColor = MaterialTheme.colorScheme.error,
    errorLabelColor = MaterialTheme.colorScheme.error,
)

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = LocalToggleThumbColor.current,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = TextSecondary,
    uncheckedTrackColor = CardBorder,
)

private data class SegmentedOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
)

private val netQualityOptions = listOf(
    SegmentedOption(NetQuality.SLOW, "Low"),
    SegmentedOption(NetQuality.OPTIMAL, "Optimal"),
    SegmentedOption(NetQuality.FAST, "High"),
)

private val transportOptions = listOf(
    SegmentedOption(TransportMode.WIFI, "Wi-Fi", Icons.Default.Wifi),
    SegmentedOption(TransportMode.USB_TETHER, "Ethernet", Icons.Default.SettingsEthernet),
)

private fun netQualityUiValue(netQuality: NetQuality): NetQuality =
    when (netQuality) {
        NetQuality.OPTIMAL -> NetQuality.OPTIMAL
        NetQuality.FAST -> NetQuality.FAST
        NetQuality.MEDIUM,
        NetQuality.SLOW,
        NetQuality.VERY_SLOW,
            -> NetQuality.SLOW
    }

private fun sourceTypeLabel(sourceType: SourceType): String =
    when (sourceType) {
        SourceType.MIC -> "Microphone"
        SourceType.INTERNAL_AUDIO -> "Internal audio"
    }
