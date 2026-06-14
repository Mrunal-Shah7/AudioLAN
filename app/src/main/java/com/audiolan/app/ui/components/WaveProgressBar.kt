package com.audiolan.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.util.AnimationUtils
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.isActive

@Composable
fun WaveProgressBar(
    progress: Float,
    isAnimating: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animationsEnabled = AnimationUtils.isSystemAnimationEnabled(context) && !LocalInspectionMode.current
    val density = LocalDensity.current
    val phase = remember { Animatable(0f) }

    LaunchedEffect(isAnimating, animationsEnabled) {
        if (!isAnimating || !animationsEnabled) {
            phase.stop()
            if (!animationsEnabled) phase.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            phase.snapTo(0f)
            phase.animateTo(
                1f,
                animationSpec = tween(durationMillis = 1_500, easing = LinearEasing),
            )
        }
    }

    Canvas(modifier = modifier.height(4.dp)) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val progressX = size.width * clampedProgress
        val centerY = size.height / 2f
        val amplitude = with(density) { 3.dp.toPx() }
        val wavelength = with(density) { 40.dp.toPx() }
        val step = 4f
        val strokeWidth = with(density) { 2.dp.toPx() }

        if (progressX > 0f) {
            val path = Path()
            var x = 0f
            path.moveTo(0f, centerY)
            while (x <= progressX) {
                val radians = ((x / wavelength) + phase.value) * 2f * PI.toFloat()
                val y = if (isAnimating && animationsEnabled) centerY + sin(radians) * amplitude else centerY
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(progressX, centerY)
            drawPath(path, color = color, style = Stroke(width = strokeWidth))
        }

        if (progressX < size.width) {
            drawLine(
                color = CardBorder,
                start = Offset(progressX, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = strokeWidth,
            )
        }
    }
}
