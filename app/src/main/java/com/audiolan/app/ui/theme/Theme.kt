package com.audiolan.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.audiolan.app.domain.model.AccentColor

val LocalToggleThumbColor = staticCompositionLocalOf { LavenderThumb }

@Composable
@Suppress("DEPRECATION")
fun AudioLANTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.LAVENDER,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemAccent = if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    val primary = if (accentColor == AccentColor.SYSTEM) systemAccent.primary else accentColor.primary
    val onPrimary = if (accentColor == AccentColor.SYSTEM) systemAccent.onPrimary else accentColor.onPrimary
    val toggleThumb = if (accentColor == AccentColor.SYSTEM) systemAccent.primaryContainer else accentColor.toggleThumb
    val background = if (amoledMode) Color.Black else AppBackground

    val darkColors = darkColorScheme(
        background = background,
        surface = Surface,
        primary = primary,
        onPrimary = onPrimary,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        error = StatusError,
        onError = TextPrimary,
        surfaceVariant = NeutralButton,
        onSurfaceVariant = TextSecondary,
        outline = CardBorder,
    )
    val lightColors = lightColorScheme(
        background = background,
        surface = Surface,
        primary = primary,
        onPrimary = onPrimary,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        error = StatusError,
        onError = TextPrimary,
        surfaceVariant = NeutralButton,
        onSurfaceVariant = TextSecondary,
        outline = CardBorder,
    )
    val colorScheme = if (darkTheme) darkColors else lightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalToggleThumbColor provides toggleThumb) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AudioLANTypography,
            shapes = AudioLANShapes,
            content = content,
        )
    }
}
