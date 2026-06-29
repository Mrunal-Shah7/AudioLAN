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
import androidx.compose.runtime.withFrameNanos
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
import com.audiolan.app.util.AnimationUtils
import kotlin.math.exp
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
        if (!animationsEnabled) {
            while (isActive) {
                val (leftTarget, rightTarget) = latestProvider()
                updateMeterFrame(
                    level = leftLevel,
                    peak = leftPeak,
                    target = if (isRunning) leftTarget else 0f,
                    animationsEnabled = false,
                    deltaMs = LEVEL_POLL_MS.toFloat(),
                )
                updateMeterFrame(
                    level = rightLevel,
                    peak = rightPeak,
                    target = if (isRunning) rightTarget else 0f,
                    animationsEnabled = false,
                    deltaMs = LEVEL_POLL_MS.toFloat(),
                )
                delay(LEVEL_POLL_MS)
            }
            return@LaunchedEffect
        }

        var previousFrameNanos = withFrameNanos { it }
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            val deltaMs = ((frameNanos - previousFrameNanos) / NANOS_PER_MS).coerceIn(0L, MAX_FRAME_DELTA_MS)
                .toFloat()
            previousFrameNanos = frameNanos
            val (leftTarget, rightTarget) = latestProvider()
            updateMeterFrame(
                level = leftLevel,
                peak = leftPeak,
                target = if (isRunning) leftTarget else 0f,
                animationsEnabled = true,
                deltaMs = deltaMs,
            )
            updateMeterFrame(
                level = rightLevel,
                peak = rightPeak,
                target = if (isRunning) rightTarget else 0f,
                animationsEnabled = true,
                deltaMs = deltaMs,
            )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val latestProvider by rememberUpdatedState(levelProvider)
    val context = LocalContext.current
    val animationsEnabled = AnimationUtils.isSystemAnimationEnabled(context) && !LocalInspectionMode.current
    val initial = latestProvider().first.coerceIn(0f, 1f)
    val displayedLevel = remember { Animatable(initial) }
    val rawLevel = latestProvider().first.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val successColor = MaterialTheme.colorScheme.tertiary
    val warningColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error

    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) {
            while (isActive) {
                displayedLevel.snapTo(latestProvider().first.coerceIn(0f, 1f))
                delay(LEVEL_POLL_MS)
            }
            return@LaunchedEffect
        }
        var previousFrameNanos = withFrameNanos { it }
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            val deltaMs = ((frameNanos - previousFrameNanos) / NANOS_PER_MS).coerceIn(0L, MAX_FRAME_DELTA_MS)
                .toFloat()
            previousFrameNanos = frameNanos
            val target = latestProvider().first.coerceIn(0f, 1f)
            val current = displayedLevel.value
            val timeConstant = if (target > current) ATTACK_TIME_MS else RELEASE_TIME_MS
            displayedLevel.snapTo(current + (target - current) * smoothingAlpha(deltaMs, timeConstant))
        }
    }

    Row(
        modifier = modifier.semantics {
            contentDescription = "$label level ${(rawLevel * 100).roundToInt()} percent"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(Dimensions.SpaceXS))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
        ) {
            val level = displayedLevel.value.coerceIn(0f, 1f)
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(size.height / 2, size.height / 2),
            )
            clipRect(right = size.width * level) {
                drawRoundRect(
                    color = when {
                        level >= 0.8f -> errorColor
                        level >= 0.6f -> warningColor
                        else -> successColor
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
    val zones = listOf(
        Zone(0f, 0.6f, MaterialTheme.colorScheme.tertiary),
        Zone(0.6f, 0.8f, MaterialTheme.colorScheme.secondary),
        Zone(0.8f, 1f, MaterialTheme.colorScheme.error),
    )
    val peakColor = MaterialTheme.colorScheme.onSurface
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
            drawMeterTrack(
                level = level,
                peak = peakProvider().coerceIn(0f, 1f),
                zones = zones,
                peakColor = peakColor,
            )
        }
        Spacer(Modifier.height(Dimensions.SpaceXXS))
        Text(
            text = channelLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private suspend fun updateMeterFrame(
    level: Animatable<Float, *>,
    peak: Animatable<Float, *>,
    target: Float,
    animationsEnabled: Boolean,
    deltaMs: Float,
) {
    val clampedTarget = target.coerceIn(0f, 1f)
    if (!animationsEnabled) {
        level.snapTo(clampedTarget)
        peak.snapTo(clampedTarget)
        return
    }

    val current = level.value
    val timeConstant = if (clampedTarget > current) ATTACK_TIME_MS else RELEASE_TIME_MS
    level.snapTo(current + (clampedTarget - current) * smoothingAlpha(deltaMs, timeConstant))

    if (clampedTarget >= peak.value) {
        peak.snapTo(clampedTarget)
    } else {
        peak.snapTo((peak.value - deltaMs / PEAK_HOLD_RELEASE_MS).coerceAtLeast(clampedTarget))
    }
}

private fun smoothingAlpha(deltaMs: Float, timeConstantMs: Float): Float =
    (1f - exp((-deltaMs / timeConstantMs).toDouble()).toFloat()).coerceIn(0f, 1f)

private fun DrawScope.drawMeterTrack(
    level: Float,
    peak: Float,
    zones: List<Zone>,
    peakColor: Color,
) {
    val zoneGap = 2.dp.toPx()
    val width = size.width
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
        color = peakColor,
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
private const val NANOS_PER_MS = 1_000_000L
private const val MAX_FRAME_DELTA_MS = 100L
private const val ATTACK_TIME_MS = 45f
private const val RELEASE_TIME_MS = 260f
private const val PEAK_HOLD_RELEASE_MS = 2_000f
