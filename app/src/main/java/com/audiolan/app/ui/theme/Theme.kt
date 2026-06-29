package com.audiolan.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.audiolan.app.domain.model.AccentColor

@Composable
@Suppress("DEPRECATION")
fun AudioLANTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.LAVENDER,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseScheme = when {
        accentColor == AccentColor.SYSTEM && darkTheme -> dynamicDarkColorScheme(context)
        accentColor == AccentColor.SYSTEM -> dynamicLightColorScheme(context)
        darkTheme -> darkAudioLanScheme(accentColor.seed)
        else -> lightAudioLanScheme(accentColor.seed)
    }
    val colorScheme = if (amoledMode && darkTheme) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
        )
    } else {
        baseScheme
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AudioLANTypography,
        shapes = AudioLANShapes,
        content = content,
    )
}

private fun darkAudioLanScheme(seed: Color): ColorScheme {
    val primary = seed
    val primaryContainer = lerp(seed, M3DarkSurfaceContainerHighest, 0.62f)
    val secondary = lerp(seed, M3DarkOnSurfaceVariant, 0.45f)
    val secondaryContainer = lerp(seed, M3DarkSurfaceContainerHigh, 0.72f)
    val tertiary = lerp(seed, M3DarkError, 0.32f)
    val tertiaryContainer = lerp(seed, M3DarkSurfaceContainerHighest, 0.55f)

    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF1B1B1F),
        primaryContainer = primaryContainer,
        onPrimaryContainer = lerp(seed, Color.White, 0.18f),
        inversePrimary = lerp(seed, Color.Black, 0.36f),
        secondary = secondary,
        onSecondary = Color(0xFF1D1B20),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = lerp(seed, Color.White, 0.22f),
        tertiary = tertiary,
        onTertiary = Color(0xFF1D1B20),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = lerp(seed, Color.White, 0.18f),
        background = M3DarkBackground,
        onBackground = M3DarkOnBackground,
        surface = M3DarkSurface,
        onSurface = M3DarkOnSurface,
        surfaceVariant = M3DarkSurfaceVariant,
        onSurfaceVariant = M3DarkOnSurfaceVariant,
        surfaceDim = M3DarkSurfaceDim,
        surfaceBright = M3DarkSurfaceBright,
        surfaceContainerLowest = M3DarkSurfaceContainerLowest,
        surfaceContainerLow = M3DarkSurfaceContainerLow,
        surfaceContainer = M3DarkSurfaceContainer,
        surfaceContainerHigh = M3DarkSurfaceContainerHigh,
        surfaceContainerHighest = M3DarkSurfaceContainerHighest,
        outline = M3DarkOutline,
        outlineVariant = M3DarkOutlineVariant,
        error = M3DarkError,
        onError = M3DarkOnError,
        errorContainer = M3DarkErrorContainer,
        onErrorContainer = M3DarkOnErrorContainer,
        inverseSurface = M3DarkInverseSurface,
        inverseOnSurface = M3DarkInverseOnSurface,
        scrim = M3Scrim,
    )
}

private fun lightAudioLanScheme(seed: Color): ColorScheme {
    val primary = lerp(seed, Color.Black, 0.46f)
    val primaryContainer = lerp(seed, Color.White, 0.52f)
    val secondary = lerp(seed, M3LightOnSurfaceVariant, 0.58f)
    val secondaryContainer = lerp(seed, Color.White, 0.68f)
    val tertiary = lerp(seed, M3LightError, 0.28f)
    val tertiaryContainer = lerp(seed, Color.White, 0.60f)

    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = lerp(seed, Color.Black, 0.56f),
        inversePrimary = seed,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = lerp(seed, Color.Black, 0.58f),
        tertiary = tertiary,
        onTertiary = Color.White,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = lerp(seed, Color.Black, 0.56f),
        background = M3LightBackground,
        onBackground = M3LightOnBackground,
        surface = M3LightSurface,
        onSurface = M3LightOnSurface,
        surfaceVariant = M3LightSurfaceVariant,
        onSurfaceVariant = M3LightOnSurfaceVariant,
        surfaceDim = M3LightSurfaceDim,
        surfaceBright = M3LightSurfaceBright,
        surfaceContainerLowest = M3LightSurfaceContainerLowest,
        surfaceContainerLow = M3LightSurfaceContainerLow,
        surfaceContainer = M3LightSurfaceContainer,
        surfaceContainerHigh = M3LightSurfaceContainerHigh,
        surfaceContainerHighest = M3LightSurfaceContainerHighest,
        outline = M3LightOutline,
        outlineVariant = M3LightOutlineVariant,
        error = M3LightError,
        onError = M3LightOnError,
        errorContainer = M3LightErrorContainer,
        onErrorContainer = M3LightOnErrorContainer,
        inverseSurface = M3LightInverseSurface,
        inverseOnSurface = M3LightInverseOnSurface,
        scrim = M3Scrim,
    )
}
