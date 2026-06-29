package com.audiolan.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.audiolan.app.util.AnimationUtils

sealed interface StreamStatus {
    data object Connecting : StreamStatus
    data object Active : StreamStatus
    data class Error(val message: String) : StreamStatus
}

@Composable
fun AnimatedStatusIcon(
    status: StreamStatus,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animationsEnabled = AnimationUtils.isSystemAnimationEnabled(context) && !LocalInspectionMode.current
    AnimatedContent(
        targetState = status,
        contentKey = { it::class },
        transitionSpec = {
            if (animationsEnabled) {
                fadeIn(tween(200)) togetherWith fadeOut(tween(100)) using SizeTransform(clip = false)
            } else {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0)) using SizeTransform(clip = false)
            }
        },
        modifier = modifier,
    ) { target ->
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (target) {
                StreamStatus.Connecting -> {
                    if (animationsEnabled) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        StaticDot()
                    }
                }
                StreamStatus.Active -> CheckmarkIcon(animationsEnabled = animationsEnabled)
                is StreamStatus.Error -> Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = target.message,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun StaticDot() {
    val color = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = Modifier.size(10.dp)) {
        drawCircle(color = color)
    }
}

@Composable
private fun CheckmarkIcon(animationsEnabled: Boolean) {
    val color = MaterialTheme.colorScheme.tertiary
    val progress = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    LaunchedEffect(animationsEnabled) {
        if (animationsEnabled) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(400, easing = LinearEasing))
        } else {
            progress.snapTo(1f)
        }
    }
    Canvas(modifier = Modifier.size(18.dp)) {
        val start = Offset(size.width * 0.18f, size.height * 0.54f)
        val mid = Offset(size.width * 0.42f, size.height * 0.76f)
        val end = Offset(size.width * 0.84f, size.height * 0.26f)
        val p = progress.value.coerceIn(0f, 1f)
        val firstProgress = (p / 0.45f).coerceIn(0f, 1f)
        val secondProgress = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)

        drawLine(
            color = color,
            start = start,
            end = Offset(
                x = start.x + (mid.x - start.x) * firstProgress,
                y = start.y + (mid.y - start.y) * firstProgress,
            ),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        if (p > 0.45f) {
            drawLine(
                color = color,
                start = mid,
                end = Offset(
                    x = mid.x + (end.x - mid.x) * secondProgress,
                    y = mid.y + (end.y - mid.y) * secondProgress,
                ),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
