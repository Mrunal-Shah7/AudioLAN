package com.audiolan.app.ui.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.core.content.ContextCompat
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.service.discovery.PongResponder
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.NeutralButton
import com.audiolan.app.ui.theme.OnNeutralButton
import com.audiolan.app.ui.theme.StatusSuccess
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary
import androidx.compose.ui.text.font.FontWeight

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pongScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val responderJob = PongResponder(context, pongScope).start()
        onDispose {
            responderJob.cancel()
        }
    }
    val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val castLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            viewModel.startCast(result.resultCode, data)
        } else {
            viewModel.onCastConsentDenied()
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onMicPermissionResult(true)
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    val micState by viewModel.micState.collectAsStateWithLifecycle()
    val castState by viewModel.castState.collectAsStateWithLifecycle()
    val receiverState by viewModel.receiverState.collectAsStateWithLifecycle()
    val networkInterfaces by viewModel.networkInterfaces.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(bottom = Dimensions.BottomNavHeight)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                )
                .padding(
                    top = Dimensions.ScreenVerticalPadding,
                    bottom = Dimensions.ScreenVerticalPadding,
                ),
        ) {
            ServiceButton(
                label = serviceButtonLabel(
                    state = micState,
                    startLabel = "start microphone service",
                    stopLabel = "stop microphone service",
                ),
                enabled = micState.isActionable(),
                isRunning = micState is ServiceState.Running,
                onClick = {
                    if (micState is ServiceState.Running) {
                        viewModel.stopMic()
                    } else {
                        val permission = Manifest.permission.RECORD_AUDIO
                        when {
                            ContextCompat.checkSelfPermission(
                                context,
                                permission,
                            ) == PackageManager.PERMISSION_GRANTED -> {
                                viewModel.onMicPermissionResult(true)
                            }
                            else -> {
                                micPermissionLauncher.launch(permission)
                            }
                        }
                    }
                },
            )
            Spacer(Modifier.height(Dimensions.SpaceM))
            ServiceButton(
                label = serviceButtonLabel(
                    state = castState,
                    startLabel = "start cast service",
                    stopLabel = "stop cast service",
                ),
                enabled = castState.isActionable(),
                isRunning = castState is ServiceState.Running,
                onClick = {
                    if (castState is ServiceState.Running) {
                        viewModel.stopCast()
                    } else {
                        castLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    }
                },
            )
            Spacer(Modifier.height(Dimensions.SpaceM))
            ServiceButton(
                label = serviceButtonLabel(
                    state = receiverState,
                    startLabel = "start receiver service",
                    stopLabel = "stop receiver service",
                ),
                enabled = receiverState.isActionable(),
                isRunning = receiverState is ServiceState.Running,
                onClick = {
                    if (receiverState is ServiceState.Running) {
                        viewModel.stopReceiver()
                    } else {
                        viewModel.startReceiver()
                    }
                },
            )
            Spacer(Modifier.height(Dimensions.SpaceM))
            Text(
                text = "available networks",
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(Dimensions.SpaceXS))
            if (networkInterfaces.isEmpty()) {
                Text(
                    text = "no active network interfaces",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                networkInterfaces.forEachIndexed { index, info ->
                    NetworkCard(info)
                    if (index != networkInterfaces.lastIndex) {
                        Spacer(Modifier.height(Dimensions.NetworkCardSpacing))
                    }
                }
            }
            Spacer(Modifier.height(Dimensions.SpaceS))
            Button(
                onClick = { viewModel.refreshNetworkInterfaces() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeutralButton,
                    contentColor = OnNeutralButton,
                ),
            ) {
                Text("refresh")
            }
        }
    }
}

@Composable
private fun ServiceButton(
    label: String,
    enabled: Boolean,
    isRunning: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimensions.ButtonMinHeight),
        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
        colors = serviceButtonColors(isRunning),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(
                horizontal = Dimensions.ButtonHorizontalPadding,
                vertical = Dimensions.ButtonVerticalPadding,
            ),
        )
    }
}

@Composable
private fun serviceButtonColors(isRunning: Boolean): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = if (isRunning) StatusSuccess else MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        disabledContentColor = MaterialTheme.colorScheme.onPrimary,
    )

@Composable
private fun NetworkCard(info: NetworkInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AudioLANSurface,
        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
        border = BorderStroke(Dimensions.CardBorderWidth, CardBorder),
    ) {
        Text(
            text = "${info.interfaceName}: ${info.ip}",
            color = TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(
                horizontal = Dimensions.NetworkCardHorizPadding,
                vertical = Dimensions.NetworkCardVertPadding,
            ),
        )
    }
}

private fun serviceButtonLabel(
    state: ServiceState,
    startLabel: String,
    stopLabel: String,
): String =
    when (state) {
        ServiceState.Idle,
        is ServiceState.Error,
            -> startLabel
        ServiceState.Starting -> "starting..."
        ServiceState.Running -> stopLabel
        ServiceState.Stopping -> "stopping..."
    }

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
