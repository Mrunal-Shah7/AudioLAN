package com.audiolan.app.ui.streams

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.audiolan.app.domain.model.NetworkSelection
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.ui.navigation.Screen
import com.audiolan.app.ui.streams.components.DeleteConfirmDialog
import com.audiolan.app.ui.streams.components.StreamCard
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.util.AnimationUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamListScreen(
    navController: NavController,
    serviceTypeName: String,
    viewModel: StreamListViewModel = hiltViewModel(),
) {
    val serviceType = remember(serviceTypeName) { ServiceType.valueOf(serviceTypeName) }
    val context = LocalContext.current
    val animationsEnabled = AnimationUtils.isSystemAnimationEnabled(context) && !LocalInspectionMode.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    LaunchedEffect(serviceType) {
        viewModel.setServiceType(serviceType)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val streamStatuses by viewModel.streamStatuses.collectAsStateWithLifecycle()
    val groupByNetwork by viewModel.groupByNetwork.collectAsStateWithLifecycle()
    val networkGroups by viewModel.networkGroups.collectAsStateWithLifecycle()
    var streamToDelete by remember { mutableStateOf<Stream?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let(viewModel::showError)
    }

    Scaffold(
        snackbarHost = { InsetAwareSnackbarHost(hostState = viewModel.snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(serviceType.name.lowercase(Locale.US))
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                actions = {
                    IconButton(
                        onClick = viewModel::toggleGroupByNetwork,
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = if (groupByNetwork) {
                                "group by network on"
                            } else {
                                "group by network off"
                            },
                            tint = if (groupByNetwork) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(bottom = Dimensions.BottomNavHeight),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                uiState.streams.isEmpty() -> {
                    if (serviceType == ServiceType.RECEIVER) {
                        ReceiverScanButton(
                            onClick = { navController.navigate(Screen.Discovery.route) },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(
                                    horizontal = Dimensions.ScreenHorizontalPadding,
                                    vertical = Dimensions.ScreenVerticalPadding,
                                ),
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "no streams configured.\ntap + to add one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.CardSpacing),
                        contentPadding = PaddingValues(
                            start = Dimensions.ScreenHorizontalPadding,
                            end = Dimensions.ScreenHorizontalPadding,
                            top = Dimensions.ScreenVerticalPadding,
                            bottom = Dimensions.SpaceHuge + Dimensions.FabOffset + Dimensions.ScreenVerticalPadding,
                        ),
                    ) {
                        if (serviceType == ServiceType.RECEIVER) {
                            item(key = "scan") {
                                ReceiverScanButton(
                                    onClick = { navController.navigate(Screen.Discovery.route) },
                                    modifier = Modifier.animateItem(
                                        placementSpec = spring(
                                            dampingRatio = if (animationsEnabled) 0.75f else 1f,
                                            stiffness = if (animationsEnabled) 200f else Spring.StiffnessHigh,
                                        ),
                                    ),
                                )
                                Spacer(Modifier.height(Dimensions.CardSpacing))
                            }
                        }
                        if (groupByNetwork) {
                            networkGroups.forEach { group ->
                                item(key = "network-header-${group.networkSelection.interfaceName}-${group.networkSelection.ssid}") {
                                    NetworkSectionHeader(
                                        networkSelection = group.networkSelection,
                                        modifier = Modifier.animateItem(
                                            placementSpec = spring(
                                                dampingRatio = if (animationsEnabled) 0.75f else 1f,
                                                stiffness = if (animationsEnabled) 200f else Spring.StiffnessHigh,
                                            ),
                                        ),
                                    )
                                }
                                streamCardItems(
                                    streams = group.streams,
                                    serviceState = serviceState,
                                    streamStatuses = streamStatuses,
                                    animationsEnabled = animationsEnabled,
                                    onToggle = viewModel::setEnabled,
                                    onVolumeChange = viewModel::setVolume,
                                    onResetVolume = { streamId -> viewModel.setVolume(streamId, 1.0f) },
                                    onOpen = { stream ->
                                        navController.navigate(Screen.StreamDetail.createRoute(stream.id))
                                    },
                                    onDelete = { stream -> streamToDelete = stream },
                                )
                            }
                        } else {
                            streamCardItems(
                                streams = uiState.streams,
                                serviceState = serviceState,
                                streamStatuses = streamStatuses,
                                animationsEnabled = animationsEnabled,
                                onToggle = viewModel::setEnabled,
                                onVolumeChange = viewModel::setVolume,
                                onResetVolume = { streamId -> viewModel.setVolume(streamId, 1.0f) },
                                onOpen = { stream ->
                                    navController.navigate(Screen.StreamDetail.createRoute(stream.id))
                                },
                                onDelete = { stream -> streamToDelete = stream },
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { navController.navigate(Screen.StreamDetail.createRoute()) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(Dimensions.FabElevation),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Dimensions.FabOffset),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "add new stream",
                )
            }

            streamToDelete?.let { stream ->
                DeleteConfirmDialog(
                    streamName = stream.name,
                    onConfirm = {
                        viewModel.delete(stream)
                        streamToDelete = null
                    },
                    onDismiss = { streamToDelete = null },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.streamCardItems(
    streams: List<Stream>,
    serviceState: ServiceState,
    streamStatuses: Map<Long, com.audiolan.app.ui.components.StreamStatus>,
    animationsEnabled: Boolean,
    onToggle: (Long, Boolean) -> Unit,
    onVolumeChange: (Long, Float) -> Unit,
    onResetVolume: (Long) -> Unit,
    onOpen: (Stream) -> Unit,
    onDelete: (Stream) -> Unit,
) {
    items(
        items = streams,
        key = { it.id },
    ) { stream ->
        AnimatedVisibility(
            visible = true,
            enter = if (animationsEnabled) {
                fadeIn(tween(200)) + expandVertically()
            } else {
                fadeIn(tween(0))
            },
            exit = if (animationsEnabled) {
                fadeOut(tween(150)) + shrinkVertically()
            } else {
                fadeOut(tween(0))
            },
            modifier = Modifier.animateItem(
                placementSpec = spring(
                    dampingRatio = if (animationsEnabled) 0.75f else 1f,
                    stiffness = if (animationsEnabled) 200f else Spring.StiffnessHigh,
                ),
            ),
        ) {
            StreamCard(
                stream = stream,
                serviceState = serviceState,
                status = streamStatuses[stream.id],
                onToggle = { onToggle(stream.id, it) },
                onVolumeChange = { onVolumeChange(stream.id, it) },
                onResetVolume = { onResetVolume(stream.id) },
                onOpen = { onOpen(stream) },
                onDelete = { onDelete(stream) },
            )
        }
    }
}

@Composable
private fun NetworkSectionHeader(
    networkSelection: NetworkSelection,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Dimensions.SpaceXS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceXS),
    ) {
        Icon(
            imageVector = networkIcon(networkSelection),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = networkSelection.displayName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun networkIcon(networkSelection: NetworkSelection) =
    if (
        networkSelection.isAnyWifi ||
        networkSelection.interfaceName.startsWith("wlan") ||
        networkSelection.interfaceName.startsWith("wifi")
    ) {
        Icons.Default.Wifi
    } else {
        Icons.Default.SettingsEthernet
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
private fun ReceiverScanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text("scan network", style = MaterialTheme.typography.labelLarge)
    }
}
