package com.audiolan.app.ui.streams.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.ServiceType
import com.audiolan.app.domain.model.SourceType
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.ui.components.AnimatedStatusIcon
import com.audiolan.app.ui.components.StreamStatus
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.util.AnimationUtils

@Composable
fun StreamCard(
    stream: Stream,
    serviceState: ServiceState = ServiceState.Idle,
    status: StreamStatus? = null,
    onToggle: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onResetVolume: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animationsEnabled = AnimationUtils.isSystemAnimationEnabled(context) && !LocalInspectionMode.current
    val isServiceRunning = serviceState is ServiceState.Running
    var visibleVolume by remember(stream.id) { mutableFloatStateOf(stream.volume) }
    LaunchedEffect(stream.volume) {
        visibleVolume = stream.volume
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(
                    animationSpec = if (animationsEnabled) {
                        spring(dampingRatio = 0.8f, stiffness = 300f)
                    } else {
                        spring(stiffness = Spring.StiffnessHigh)
                    },
                )
                .padding(Dimensions.CardPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stream.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (stream.broadcastMode && stream.serviceType == ServiceType.TRANSMITTER) {
                            "all devices on network:${stream.port}"
                        } else {
                            "${stream.host}:${stream.port}"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (stream.serviceType == ServiceType.TRANSMITTER) {
                        Text(
                            text = buildTransmitterLabels(stream),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = stream.networkSelection.displayName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AnimatedContent(
                    targetState = isServiceRunning,
                    transitionSpec = {
                        if (animationsEnabled) {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(100))
                        } else {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        }
                    },
                    label = "stream_card_status_area",
                ) { running ->
                    if (running) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            status?.let { AnimatedStatusIcon(status = it) }
                            Switch(
                                checked = stream.isEnabled,
                                onCheckedChange = onToggle,
                                modifier = Modifier
                                    .alpha(0.4f)
                                    .semantics {
                                        contentDescription = if (stream.isEnabled) {
                                            "disable ${stream.name}"
                                        } else {
                                            "enable ${stream.name}"
                                        }
                                    },
                                colors = switchColors(),
                            )
                        }
                    } else {
                        Switch(
                            checked = stream.isEnabled,
                            onCheckedChange = onToggle,
                            modifier = Modifier.semantics {
                                contentDescription = if (stream.isEnabled) {
                                    "disable ${stream.name}"
                                } else {
                                    "enable ${stream.name}"
                                }
                            },
                            colors = switchColors(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimensions.SpaceS))
            VolumeSliderRow(
                volume = visibleVolume,
                onVolumeChange = {
                    visibleVolume = it
                    onVolumeChange(it)
                },
                onReset = {
                    visibleVolume = 1.0f
                    onResetVolume()
                },
                contentDescription = "volume for ${stream.name}, current value ${
                    String.format(java.util.Locale.US, "%.0f", visibleVolume * 100)
                } percent",
            )
            Spacer(Modifier.height(Dimensions.SpaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceXS)) {
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(
                        horizontal = Dimensions.SmallButtonHorizPadding,
                        vertical = Dimensions.SmallButtonVerticalPadding,
                    ),
                    modifier = Modifier
                        .heightIn(min = Dimensions.SmallButtonMinHeight)
                        .semantics { contentDescription = "edit ${stream.name}" },
                ) {
                    Text("edit", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(
                        horizontal = Dimensions.SmallButtonHorizPadding,
                        vertical = Dimensions.SmallButtonVerticalPadding,
                    ),
                    modifier = Modifier.heightIn(min = Dimensions.SmallButtonMinHeight),
                ) {
                    Text("delete", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

private fun buildTransmitterLabels(stream: Stream): String =
    buildList {
        add(
            when (stream.sourceType) {
                SourceType.MIC -> "mic"
                SourceType.INTERNAL_AUDIO -> "internal audio"
            },
        )
        if (stream.broadcastMode) add("broadcast")
    }.joinToString(" | ")
