package com.audiolan.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.audiolan.app.domain.model.ServiceState
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.StatusSuccess
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary
import com.audiolan.app.util.AnimationUtils

@Composable
fun ServiceStatusAccordion(
    serviceLabel: String,
    serviceState: ServiceState,
    streams: List<Stream>,
    streamStatuses: Map<Long, StreamStatus>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animationsEnabled = AnimationUtils.isSystemAnimationEnabled(context) && !LocalInspectionMode.current
    val enabledStreams = streams.filter { it.isEnabled }
    val activeCount = enabledStreams.count { streamStatuses[it.id] is StreamStatus.Active }
    val totalCount = enabledStreams.size
    val isRunning = serviceState is ServiceState.Running
    val progress = if (totalCount == 0) 0f else activeCount.toFloat() / totalCount.toFloat()
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = if (animationsEnabled) {
            spring(dampingRatio = 0.75f, stiffness = 300f)
        } else {
            spring(stiffness = Spring.StiffnessHigh)
        },
        label = "accordion_chevron",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (animationsEnabled) {
                    spring(dampingRatio = 0.8f, stiffness = 300f)
                } else {
                    spring(stiffness = Spring.StiffnessHigh)
                },
            ),
        color = if (isRunning) AudioLANSurface else MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(Dimensions.CardCornerRadius),
        border = BorderStroke(Dimensions.CardBorderWidth, CardBorder),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isRunning) onStopService() else onStartService()
                    }
                    .padding(Dimensions.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = serviceButtonText(serviceLabel, serviceState),
                    color = if (isRunning) StatusSuccess else MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (isRunning) {
                    Text(
                        text = "$activeCount / $totalCount active",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "collapse" else "expand",
                            tint = TextSecondary,
                            modifier = Modifier.rotate(rotation),
                        )
                    }
                }
            }
            WaveProgressBar(
                progress = progress,
                isAnimating = isRunning,
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(
                visible = isRunning && isExpanded,
                enter = if (animationsEnabled) expandVertically() + fadeIn() else fadeIn(),
                exit = if (animationsEnabled) shrinkVertically() + fadeOut() else fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = Dimensions.CardPadding,
                        vertical = Dimensions.SpaceS,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceXS),
                ) {
                    if (enabledStreams.isEmpty()) {
                        Text(
                            text = "no enabled streams",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        enabledStreams.forEach { stream ->
                            StreamStatusRow(
                                stream = stream,
                                status = streamStatuses[stream.id] ?: StreamStatus.Connecting,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamStatusRow(
    stream: Stream,
    status: StreamStatus,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedStatusIcon(status = status)
        Spacer(Modifier.width(Dimensions.SpaceS))
        Text(
            text = stream.name,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Dimensions.SpaceS))
        Text(
            text = "${stream.host}:${stream.port}",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun serviceButtonText(serviceLabel: String, state: ServiceState): String =
    when (state) {
        ServiceState.Idle,
        is ServiceState.Error,
            -> "start $serviceLabel service"
        ServiceState.Starting -> "starting..."
        ServiceState.Running -> "stop $serviceLabel service"
        ServiceState.Stopping -> "stopping..."
    }
