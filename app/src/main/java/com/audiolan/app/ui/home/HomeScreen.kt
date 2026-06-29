package com.audiolan.app.ui.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.service.discovery.PongResponder
import com.audiolan.app.ui.components.StreamStatus
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.util.AnimationUtils
import java.util.Locale
import kotlin.math.log10
import kotlin.math.max
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pongScope = rememberCoroutineScope()
    val actionScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val responderJob = PongResponder(context, pongScope).start()
        onDispose {
            responderJob.cancel()
        }
    }

    val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    var pendingTransmitterStartConfig by remember { mutableStateOf<TransmitterStartConfig?>(null) }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        val startConfig = pendingTransmitterStartConfig
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            viewModel.startTransmitter(
                resultCode = result.resultCode,
                data = data,
                hasMicStreams = startConfig?.hasMicStreams ?: false,
                hasInternalAudioStreams = true,
            )
        } else {
            viewModel.onTransmitterConsentDenied()
        }
        pendingTransmitterStartConfig = null
    }

    fun continueTransmitterStart() {
        actionScope.launch {
            val startConfig = viewModel.getTransmitterStartConfig()
            if (startConfig.enabledStreamCount == 0) {
                pendingTransmitterStartConfig = null
                viewModel.onNoTransmitterStreamsConfigured()
                return@launch
            }
            if (startConfig.duplicateNames.isNotEmpty()) {
                pendingTransmitterStartConfig = null
                viewModel.onDuplicateTransmitterStreamNames(startConfig.duplicateNames)
                return@launch
            }
            pendingTransmitterStartConfig = startConfig
            if (startConfig.hasInternalAudioStreams) {
                mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
            } else {
                viewModel.startTransmitter(
                    hasMicStreams = startConfig.hasMicStreams,
                    hasInternalAudioStreams = false,
                )
                pendingTransmitterStartConfig = null
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onMicPermissionResult(true)
            continueTransmitterStart()
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    val transmitterState by viewModel.transmitterState.collectAsStateWithLifecycle()
    val receiverState by viewModel.receiverState.collectAsStateWithLifecycle()
    val transmitterStreams by viewModel.transmitterStreams.collectAsStateWithLifecycle()
    val receiverStreams by viewModel.receiverStreams.collectAsStateWithLifecycle()
    val transmitterStatuses by viewModel.transmitterStreamStatuses.collectAsStateWithLifecycle()
    val receiverStatuses by viewModel.receiverStreamStatuses.collectAsStateWithLifecycle()
    val transmitterPanelError by viewModel.transmitterPanelError.collectAsStateWithLifecycle()
    val receiverPanelError by viewModel.receiverPanelError.collectAsStateWithLifecycle()
    val transmitterLevel by viewModel.transmitterLevelState.collectAsStateWithLifecycle()
    val receiverLevel by viewModel.receiverLevelState.collectAsStateWithLifecycle()
    val networkInterfaces by viewModel.networkInterfaces.collectAsStateWithLifecycle()
    val usbTetherStatus by viewModel.usbTetherStatus.collectAsStateWithLifecycle()
    val allNetworkInterfaces = (networkInterfaces + usbTetherStatus.interfaces).distinctBy { it.interfaceName to it.ip }

    Scaffold(
        snackbarHost = { InsetAwareSnackbarHost(hostState = viewModel.snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = Dimensions.ScreenHorizontalPadding),
        ) {
            HomeTopBar()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = Dimensions.SpaceS),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS),
            ) {
                ServicePanel(
                    label = "Transmit",
                    icon = Icons.Default.SettingsVoice,
                    serviceState = transmitterState,
                    streams = transmitterStreams,
                    streamStatuses = transmitterStatuses,
                    panelError = transmitterPanelError,
                    level = transmitterLevel,
                    onStartService = {
                        if (transmitterState.isActionable()) {
                            val permission = Manifest.permission.RECORD_AUDIO
                            if (
                                ContextCompat.checkSelfPermission(context, permission) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.onMicPermissionResult(true)
                                continueTransmitterStart()
                            } else {
                                micPermissionLauncher.launch(permission)
                            }
                        }
                    },
                    onStopService = {
                        if (transmitterState is ServiceState.Running) {
                            viewModel.stopTransmitter()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = Dimensions.CardBorderWidth,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                ServicePanel(
                    label = "Receive",
                    icon = Icons.Default.Headphones,
                    serviceState = receiverState,
                    streams = receiverStreams,
                    streamStatuses = receiverStatuses,
                    panelError = receiverPanelError,
                    level = receiverLevel,
                    onStartService = {
                        if (receiverState.isActionable()) {
                            viewModel.startReceiver()
                        }
                    },
                    onStopService = {
                        if (receiverState is ServiceState.Running) {
                            viewModel.stopReceiver()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = Dimensions.SpaceM),
                thickness = Dimensions.CardBorderWidth,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            AvailableNetworksSection(
                interfaces = allNetworkInterfaces,
                onRefresh = viewModel::refreshNetworkInterfaces,
            )
        }
    }
}

@Composable
private fun HomeTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimensions.SpaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.CellTower,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(Dimensions.SpaceS))
        Text(
            text = "AudioLAN",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InsetAwareSnackbarHost(hostState: SnackbarHostState) {
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimensions.ScreenHorizontalPadding)
            .padding(bottom = Dimensions.BottomNavHeight + navBottomPadding + Dimensions.SpaceM),
        contentAlignment = Alignment.BottomCenter,
    ) {
        SnackbarHost(
            hostState = hostState,
            modifier = Modifier.fillMaxWidth(),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                )
            },
        )
    }
}

@Composable
private fun ServicePanel(
    label: String,
    icon: ImageVector,
    serviceState: ServiceState,
    streams: List<Stream>,
    streamStatuses: Map<Long, StreamStatus>,
    panelError: String?,
    level: Pair<Float, Float>,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = serviceState is ServiceState.Running
    val enabledStreams = streams.filter { it.isEnabled }
    val activeCount = enabledStreams.count { streamStatuses[it.id] is StreamStatus.Active }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimensions.SpaceS),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(Dimensions.SpaceXS)
                                .size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(Dimensions.SpaceS))
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(Dimensions.SpaceXS))
                StatusChip(
                    state = serviceState,
                    activeCount = activeCount,
                    totalCount = enabledStreams.size,
                    hasInlineError = panelError != null,
                )
                panelError?.let { message ->
                    Spacer(Modifier.height(Dimensions.SpaceXXS))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    HomeAudioLevelMeter(
                        level = level,
                        isRunning = isRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(252.dp),
                    )
                    Text(
                        text = dbReadout(level = level, isRunning = isRunning),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimensions.SpaceXS),
                    )
                }
            }

            Button(
                onClick = {
                    if (isRunning) onStopService() else onStartService()
                },
                enabled = serviceState.isActionable(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.SmallButtonMinHeight),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(horizontal = Dimensions.SpaceS, vertical = Dimensions.SpaceS),
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Dimensions.SpaceXS))
                Text(
                    text = if (isRunning) "Stop" else "Start",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun HomeAudioLevelMeter(
    level: Pair<Float, Float>,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val barHeight = 188.dp
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DbScale(
            modifier = Modifier
                .height(barHeight)
                .width(28.dp),
        )
        Spacer(Modifier.width(Dimensions.SpaceXXS))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                HomeMeterBar(
                    level = level.first,
                    isRunning = isRunning,
                    height = barHeight,
                    modifier = Modifier.width(20.dp),
                )
                HomeMeterBar(
                    level = level.second,
                    isRunning = isRunning,
                    height = barHeight,
                    modifier = Modifier.width(20.dp),
                )
            }
            Spacer(Modifier.height(Dimensions.SpaceXXS))
            Row(
                modifier = Modifier.width(52.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "L",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "R",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DbScale(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        listOf(
            "0" to 0f,
            "-6" to 0.25f,
            "-12" to 0.5f,
            "-18" to 0.75f,
            "-24" to 1f,
        ).forEach { (label, fraction) ->
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopEnd)
                    .padding(top = (188.dp - 12.dp) * fraction),
            )
        }
    }
}

@Composable
private fun HomeMeterBar(
    level: Float,
    isRunning: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animationsEnabled = AnimationUtils.isSystemAnimationEnabled(context) && !LocalInspectionMode.current
    val segmentColor = MaterialTheme.colorScheme.surface
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val successColor = MaterialTheme.colorScheme.tertiary
    val warningColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    val peakColor = MaterialTheme.colorScheme.onSurface
    val targetLevel = if (isRunning) levelToMeterFraction(level).coerceIn(0f, 1f) else 0f
    val displayedLevel = remember { Animatable(targetLevel) }
    val peakLevel = remember { Animatable(targetLevel) }

    LaunchedEffect(targetLevel, isRunning, animationsEnabled) {
        if (!animationsEnabled) {
            displayedLevel.snapTo(targetLevel)
            peakLevel.snapTo(targetLevel)
            return@LaunchedEffect
        }
        val durationMs = if (targetLevel > displayedLevel.value) {
            HOME_METER_ATTACK_MS
        } else {
            HOME_METER_RELEASE_MS
        }
        displayedLevel.animateTo(
            targetValue = targetLevel,
            animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
        )
    }

    LaunchedEffect(targetLevel, animationsEnabled) {
        if (!animationsEnabled) {
            peakLevel.snapTo(targetLevel)
            return@LaunchedEffect
        }
        if (targetLevel >= peakLevel.value) {
            peakLevel.snapTo(targetLevel)
        } else {
            peakLevel.animateTo(
                targetValue = targetLevel,
                animationSpec = tween(durationMillis = HOME_PEAK_RELEASE_MS, easing = LinearEasing),
            )
        }
    }

    Canvas(
        modifier = modifier.height(height),
    ) {
        val displayLevel = when {
            isRunning -> displayedLevel.value.coerceIn(0.015f, 1f)
            else -> 0.015f
        }
        val corner = CornerRadius(7.dp.toPx(), 7.dp.toPx())
        drawRoundRect(
            color = trackColor,
            cornerRadius = corner,
        )

        val segmentCount = 18
        val segmentGap = 2.dp.toPx()
        val segmentHeight = (size.height - segmentGap * (segmentCount - 1)) / segmentCount
        repeat(segmentCount - 1) { index ->
            val y = (index + 1) * segmentHeight + index * segmentGap
            drawLine(
                color = segmentColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = segmentGap,
            )
        }

        clipRect(top = size.height * (1f - displayLevel)) {
            drawMeterZone(start = 0f, end = 0.6f, color = successColor, corner = corner)
            drawMeterZone(start = 0.6f, end = 0.8f, color = warningColor, corner = corner)
            drawMeterZone(start = 0.8f, end = 1f, color = errorColor, corner = corner)
        }

        repeat(segmentCount - 1) { index ->
            val y = (index + 1) * segmentHeight + index * segmentGap
            drawLine(
                color = segmentColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = segmentGap,
            )
        }

        val peak = if (isRunning) peakLevel.value.coerceIn(0f, 1f) else 0f
        if (peak > 0.04f) {
            val peakY = size.height * (1f - peak)
            drawLine(
                color = peakColor,
                start = Offset(0f, peakY),
                end = Offset(size.width, peakY),
                strokeWidth = Dimensions.CardBorderWidth.toPx(),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMeterZone(
    start: Float,
    end: Float,
    color: androidx.compose.ui.graphics.Color,
    corner: CornerRadius,
) {
    val top = size.height * (1f - end)
    val bottom = size.height * (1f - start)
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, top),
        size = Size(size.width, bottom - top),
        cornerRadius = corner,
    )
}

@Composable
private fun StatusChip(
    state: ServiceState,
    activeCount: Int,
    totalCount: Int,
    hasInlineError: Boolean,
) {
    val errorColor = MaterialTheme.colorScheme.error
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activeColor = MaterialTheme.colorScheme.onSurface
    val (text, color, showDot) = if (hasInlineError) {
        Triple("error", errorColor, true)
    } else {
        when (state) {
            ServiceState.Idle -> Triple("Stopped", inactiveColor, false)
            ServiceState.Starting -> Triple("Starting", inactiveColor, false)
            ServiceState.Running -> Triple("$activeCount / $totalCount active", activeColor, true)
            ServiceState.Stopping -> Triple("Stopping", inactiveColor, false)
            is ServiceState.Error -> Triple("error", errorColor, true)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimensions.SpaceXS, vertical = Dimensions.SpaceXXS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showDot) {
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = CircleShape,
                    color = if (hasInlineError || state is ServiceState.Error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    content = {},
                )
                Spacer(Modifier.width(Dimensions.SpaceXXS))
            }
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AvailableNetworksSection(
    interfaces: List<NetworkInfo>,
    onRefresh: () -> Unit,
) {
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimensions.SpaceS)
            .padding(bottom = Dimensions.BottomNavHeight + navBottomPadding + Dimensions.SpaceM),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "AVAILABLE NETWORKS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = onRefresh,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                contentPadding = PaddingValues(
                    horizontal = Dimensions.SpaceS,
                    vertical = Dimensions.SpaceXXS,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(Dimensions.SpaceXXS))
                Text("Refresh", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(Dimensions.SpaceXS))
        if (interfaces.isEmpty()) {
            NetworkPlaceholder()
        } else {
            interfaces.forEachIndexed { index, info ->
                NetworkInterfaceCard(info)
                if (index != interfaces.lastIndex) {
                    Spacer(Modifier.height(Dimensions.NetworkCardSpacing))
                }
            }
        }
    }
}

@Composable
private fun NetworkPlaceholder() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "no active network interfaces",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(
                horizontal = Dimensions.NetworkCardHorizPadding,
                vertical = Dimensions.NetworkCardVertPadding,
            ),
        )
    }
}

@Composable
private fun NetworkInterfaceCard(info: NetworkInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Dimensions.NetworkCardHorizPadding,
                vertical = Dimensions.NetworkCardVertPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = info.interfaceName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = info.ip,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun dbReadout(level: Pair<Float, Float>, isRunning: Boolean): String {
    if (!isRunning) return "-- dB"
    val peak = max(level.first, level.second).coerceIn(0f, 1f)
    if (peak <= 0.000_001f) return "-- dB"
    return String.format(Locale.US, "%.1f dB", 20f * log10(peak))
}

private fun levelToMeterFraction(level: Float): Float {
    val peak = level.coerceIn(0f, 1f)
    if (peak <= 0.000_001f) return 0f
    val db = (20f * log10(peak)).coerceIn(HOME_METER_DB_FLOOR, 0f)
    return (db - HOME_METER_DB_FLOOR) / -HOME_METER_DB_FLOOR
}

private const val HOME_METER_DB_FLOOR = -24f
private const val HOME_METER_ATTACK_MS = 45
private const val HOME_METER_RELEASE_MS = 260
private const val HOME_PEAK_RELEASE_MS = 2_000

private fun ServiceState.isActionable(): Boolean =
    when (this) {
        ServiceState.Idle,
        is ServiceState.Error,
        ServiceState.Running,
            -> true
        ServiceState.Starting,
        ServiceState.Stopping,
            -> false
    }
