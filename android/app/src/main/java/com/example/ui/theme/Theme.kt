package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    primaryContainer = NightPrimaryContainer,
    onPrimaryContainer = NightOnPrimaryContainer,
    secondary = NightSecondary,
    onSecondary = NightOnSecondary,
    secondaryContainer = NightSecondaryContainer,
    onSecondaryContainer = NightOnSecondaryContainer,
    tertiary = NightTertiary,
    background = NightBackground,
    onBackground = NightOnBackground,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightOnSurfaceVariant,
    outline = NightOutline,
    error = NorilskError
)

private val LightColorScheme = lightColorScheme(
    primary = ArcticPrimary,
    onPrimary = ArcticOnPrimary,
    primaryContainer = ArcticPrimaryContainer,
    onPrimaryContainer = ArcticOnPrimaryContainer,
    secondary = ArcticSecondary,
    onSecondary = ArcticOnSecondary,
    secondaryContainer = ArcticSecondaryContainer,
    onSecondaryContainer = ArcticOnSecondaryContainer,
    tertiary = ArcticTertiary,
    background = ArcticBackground,
    onBackground = ArcticOnBackground,
    surface = ArcticSurface,
    onSurface = ArcticOnSurface,
    surfaceVariant = ArcticSurfaceVariant,
    onSurfaceVariant = ArcticOnSurfaceVariant,
    outline = ArcticOutline,
    error = NorilskError
)

/**
 * Динамические цвета (Material You) ОТКЛЮЧЕНЫ намеренно: иначе системная палитра
 * перебивала выбор «Светлая/Тёмная» и тема визуально не переключалась.
 * Теперь свет/тьма всегда работают и приложение имеет фирменный арктический стиль.
 */
@Composable
fun NorilskTransitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
