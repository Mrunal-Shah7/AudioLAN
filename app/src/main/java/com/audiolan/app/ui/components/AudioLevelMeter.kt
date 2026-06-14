package com.audiolan.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.StatusError
import com.audiolan.app.ui.theme.StatusSuccess
import com.audiolan.app.ui.theme.StatusWarning
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary
import com.audiolan.app.util.AnimationUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun AudioLevelMeter(
    levelProvider: () -> Pair<Float, Float>,
    label: String,
    modifier: Modifier = Modifier,
    isRunning: Boolean = true,
    barHeight: Dp = 96.dp,
) {
    val latestProvider by rememberUpdatedState(levelProvider)
    val context = LocalContext.current
    val animationsEnabled = AnimationUtils.isSystemAnimationEnabled(context) && !LocalInspectionMode.current
    val initial = latestProvider()
    val leftLevel = remember { Animatable(initial.first.coerceIn(0f, 1f)) }
    val rightLevel = remember { Animatable(initial.second.coerceIn(0f, 1f)) }
    val leftPeak = remember { Animatable(initial.first.coerceIn(0f, 1f)) }
    val rightPeak = remember { Animatable(initial.second.coerceIn(0f, 1f)) }
    val rawLevel = latestProvider()

    LaunchedEffect(animationsEnabled, isRunning) {
        while (isActive) {
            val (leftTarget, rightTarget) = latestProvider()
            updateMeterLevel(leftLevel, leftPeak, leftTarget.coerceIn(0f, 1f), animationsEnabled)
            updateMeterLevel(rightLevel, rightPeak, rightTarget.coerceIn(0f, 1f), animationsEnabled)
            delay(LEVEL_POLL_MS)
        }
    }

    Column(
        modifier = modifier.semantics {
            contentDescription = "$label level: left ${
                (rawLevel.first.coerceIn(0f, 1f) * 100).roundToInt()
            } percent, right ${(rawLevel.second.coerceIn(0f, 1f) * 100).roundToInt()} percent"
        },
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(Dimensions.SpaceXS))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeterBar(
                channelLabel = "L",
                levelProvider = { leftLevel.value },
                peakProvider = { leftPeak.value },
                isIdlePulseEnabled = !isRunning && rawLevel.first == 0f && rawLevel.second == 0f,
                animationsEnabled = animationsEnabled,
                barHeight = barHeight,
                modifier = Modifier
                    .weight(1f),
            )
            Spacer(Modifier.width(Dimensions.SpaceS))
            MeterBar(
                channelLabel = "R",
                levelProvider = { rightLevel.value },
                peakProvider = { rightPeak.value },
                isIdlePulseEnabled = !isRunning && rawLevel.first == 0f && rawLevel.second == 0f,
                animationsEnabled = animationsEnabled,
                barHeight = barHeight,
                modifier = Modifier
                    .weight(1f),
            )
        }
    }
}

@Composable
fun CompactAudioLevelMeter(
    levelProvider: () -> Pair<Float, Float>,
    label: String,
    modifier: Modifier = Modifier,
) {
    val level = levelProvider().first.coerceIn(0f, 1f)
    Row(
        modifier = modifier.semantics {
            contentDescription = "$label level ${(level * 100).roundToInt()} percent"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(Dimensions.SpaceXS))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
        ) {
            drawRoundRect(
                color = TextSecondary.copy(alpha = 0.18f),
                cornerRadius = CornerRadius(size.height / 2, size.height / 2),
            )
            clipRect(right = size.width * level) {
                drawRoundRect(
                    color = when {
                        level >= 0.8f -> StatusError
                        level >= 0.6f -> StatusWarning
                        else -> StatusSuccess
                    },
                    cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                )
            }
        }
    }
}

@Composable
private fun MeterBar(
    channelLabel: String,
    levelProvider: () -> Float,
    peakProvider: () -> Float,
    isIdlePulseEnabled: Boolean,
    animationsEnabled: Boolean,
    barHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(isIdlePulseEnabled, animationsEnabled) {
        if (!isIdlePulseEnabled || !animationsEnabled) {
            pulse.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            pulse.animateTo(0.03f, animationSpec = spring(stiffness = Spring.StiffnessLow))
            pulse.animateTo(0.0f, animationSpec = spring(stiffness = Spring.StiffnessLow))
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
        ) {
            val level = maxOf(levelProvider(), pulse.value).coerceIn(0f, 1f)
            drawMeterTrack(level = level, peak = peakProvider().coerceIn(0f, 1f))
        }
        Spacer(Modifier.height(Dimensions.SpaceXXS))
        Text(
            text = channelLabel,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private suspend fun updateMeterLevel(
    level: Animatable<Float, *>,
    peak: Animatable<Float, *>,
    target: Float,
    animationsEnabled: Boolean,
) {
    if (animationsEnabled) {
        level.animateTo(
            target,
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessHigh,
            ),
        )
    } else {
        level.snapTo(target)
    }

    if (target >= peak.value) {
        peak.snapTo(target)
    } else if (animationsEnabled) {
        peak.snapTo((peak.value - PEAK_DECAY_PER_POLL).coerceAtLeast(target))
    } else {
        peak.snapTo(target)
    }
}

private fun DrawScope.drawMeterTrack(level: Float, peak: Float) {
    val zoneGap = 2.dp.toPx()
    val width = size.width
    val zones = listOf(
        Zone(0f, 0.6f, StatusSuccess),
        Zone(0.6f, 0.8f, StatusWarning),
        Zone(0.8f, 1f, StatusError),
    )
    zones.forEach { zone ->
        val top = size.height * (1f - zone.end)
        val bottom = size.height * (1f - zone.start)
        val zoneHeight = (bottom - top - zoneGap).coerceAtLeast(0f)
        drawRoundRect(
            color = zone.color.copy(alpha = 0.22f),
            topLeft = Offset(0f, top),
            size = Size(width, zoneHeight),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )
        val fillTop = size.height * (1f - level.coerceAtMost(zone.end))
        val fillBottom = size.height * (1f - zone.start)
        if (level > zone.start) {
            drawRoundRect(
                color = zone.color,
                topLeft = Offset(0f, fillTop),
                size = Size(width, (fillBottom - fillTop - zoneGap).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
        }
    }
    val peakY = size.height * (1f - peak)
    drawLine(
        color = TextPrimary,
        start = Offset(0f, peakY),
        end = Offset(width, peakY),
        strokeWidth = 1.dp.toPx(),
    )
}

private data class Zone(
    val start: Float,
    val end: Float,
    val color: Color,
)

private const val LEVEL_POLL_MS = 50L
private const val PEAK_DECAY_PER_POLL = 0.025f
