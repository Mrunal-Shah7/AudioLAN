package com.audiolan.app.ui.streams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.ui.navigation.Screen
import com.audiolan.app.ui.streams.components.DeleteConfirmDialog
import com.audiolan.app.ui.streams.components.StreamCard
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.TextSecondary

@Composable
fun StreamListScreen(
    navController: NavController,
    serviceTypeName: String,
    viewModel: StreamListViewModel = hiltViewModel(),
) {
    val serviceType = remember(serviceTypeName) { ServiceType.valueOf(serviceTypeName) }
    LaunchedEffect(serviceType) {
        viewModel.setServiceType(serviceType)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var streamToDelete by remember { mutableStateOf<Stream?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let(viewModel::showError)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.CardSpacing),
                        contentPadding = PaddingValues(
                            start = Dimensions.ScreenHorizontalPadding,
                            end = Dimensions.ScreenHorizontalPadding,
                            top = Dimensions.ScreenVerticalPadding,
                            bottom = Dimensions.SpaceHuge + Dimensions.FabOffset + Dimensions.ScreenVerticalPadding,
                        ),
                    ) {
                        if (serviceType == ServiceType.RECEIVER) {
                            item {
                                ReceiverScanButton(
                                    onClick = { navController.navigate(Screen.Discovery.route) },
                                )
                                Spacer(Modifier.height(Dimensions.CardSpacing))
                            }
                        }
                        items(
                            items = uiState.streams,
                            key = { it.id },
                        ) { stream ->
                            StreamCard(
                                stream = stream,
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
