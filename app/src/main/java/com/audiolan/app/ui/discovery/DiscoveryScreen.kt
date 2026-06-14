package com.audiolan.app.ui.discovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.audiolan.app.domain.model.DiscoverySource
import com.audiolan.app.service.discovery.PongResponder
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary
import com.audiolan.app.util.AnimationUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    navController: NavController,
    viewModel: DiscoveryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val animationsEnabled = AnimationUtils.isSystemAnimationEnabled(context) && !LocalInspectionMode.current
    val pongScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val responderJob = PongResponder(context, pongScope).start()
        onDispose {
            responderJob.cancel()
        }
    }

    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val duplicateMessage by viewModel.duplicateMessage.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "discovery",
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
                .padding(horizontal = Dimensions.ScreenHorizontalPadding),
        ) {
            Spacer(Modifier.height(Dimensions.SpaceM))
            Button(
                onClick = { viewModel.startScan() },
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.ButtonMinHeight),
            ) {
                Text(
                    text = if (isScanning) "scanning..." else "scan network",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(Dimensions.SpaceS))
            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.SpaceXS),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = CardBorder,
                )
            }
            error?.let { message ->
                Text(
                    text = message,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = Dimensions.SpaceXS),
                )
            }
            Spacer(Modifier.height(Dimensions.SpaceM))
            if (devices.isEmpty() && !isScanning) {
                Text(
                    text = "no devices found. tap scan network to search.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimensions.CardSpacing),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Dimensions.ScreenVerticalPadding),
                ) {
                    items(devices, key = { "${it.ip}:${it.port}:${it.streamName.orEmpty()}" }) { device ->
                        DeviceResultCard(
                            device = device,
                            modifier = Modifier.animateItem(
                                placementSpec = spring(
                                    dampingRatio = if (animationsEnabled) 0.75f else 1f,
                                    stiffness = if (animationsEnabled) 200f else Spring.StiffnessHigh,
                                ),
                            ),
                            onClick = {
                                viewModel.onDeviceSelected(device) { route ->
                                    navController.navigate(route)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    duplicateMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateMessage,
            containerColor = AudioLANSurface,
            title = {
                Text(
                    text = "receiver already configured",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = message,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDuplicateMessage) {
                    Text(
                        text = "ok",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        )
    }
}

@Composable
private fun DeviceResultCard(
    device: MergedDevice,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val hasPingPong = device.sources.contains(DiscoverySource.PING_PONG)
    val hasVban = device.sources.contains(DiscoverySource.VBAN_SNIFF)
    val hasUsb = device.sources.contains(DiscoverySource.USB_TETHER)
    val mainText = when {
        hasUsb -> "usb tether: ${device.deviceName ?: device.ip}"
        hasPingPong && !device.deviceName.isNullOrBlank() -> "${device.deviceName} @ ${device.ip}"
        hasVban && !device.streamName.isNullOrBlank() -> "vban: ${device.streamName} @ ${device.ip}"
        else -> device.ip
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = AudioLANSurface,
        shape = RoundedCornerShape(Dimensions.CardCornerRadius),
        border = BorderStroke(Dimensions.CardBorderWidth, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
        ) {
            Text(
                text = mainText,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            if (hasVban && !device.streamName.isNullOrBlank()) {
                Text(
                    text = "vban: ${device.streamName}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Dimensions.SpaceXXS),
                )
            }
            if (hasPingPong && hasVban) {
                Text(
                    text = "ping/pong + vban",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimensions.SpaceXXS),
                )
            }
            if (hasUsb) {
                Text(
                    text = "usb tethering",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimensions.SpaceXXS),
                )
            }
        }
    }
}
