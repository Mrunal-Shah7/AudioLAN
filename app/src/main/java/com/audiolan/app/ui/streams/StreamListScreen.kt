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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.ui.components.AudioLevelMeter
import com.audiolan.app.ui.navigation.Screen
import com.audiolan.app.ui.streams.components.DeleteConfirmDialog
import com.audiolan.app.ui.streams.components.StreamCard
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.TextSecondary
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
    val levelState by viewModel.levelState.collectAsStateWithLifecycle()
    val streamStatuses by viewModel.streamStatuses.collectAsStateWithLifecycle()
    var streamToDelete by remember { mutableStateOf<Stream?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let(viewModel::showError)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(serviceType.name.lowercase(Locale.US))
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
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
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = serviceState is ServiceState.Running,
                            enter = if (animationsEnabled) expandVertically() + fadeIn() else fadeIn(tween(0)),
                            exit = if (animationsEnabled) shrinkVertically() + fadeOut() else fadeOut(tween(0)),
                        ) {
                            Column {
                                AudioLevelMeter(
                                    levelProvider = { levelState },
                                    label = if (serviceType == ServiceType.TRANSMITTER) "output" else "input",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = Dimensions.ScreenHorizontalPadding,
                                            vertical = Dimensions.SpaceS,
                                        ),
                                )
                                HorizontalDivider(color = CardBorder)
                            }
                        }
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
                            items(
                                items = uiState.streams,
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
                                        onToggle = { viewModel.setEnabled(stream.id, it) },
                                        onVolumeChange = { viewModel.setVolume(stream.id, it) },
                                        onResetVolume = { viewModel.setVolume(stream.id, 1.0f) },
                                        onOpen = { navController.navigate(Screen.StreamDetail.createRoute(stream.id)) },
                                        onDelete = { streamToDelete = stream },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { navController.navigate(Screen.StreamDetail.createRoute()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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

@Composable
private fun ReceiverScanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text("scan network", style = MaterialTheme.typography.labelLarge)
    }
}
