package com.simplescoring.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ScoreAnythingColors.Accent,
    onPrimary = ScoreAnythingColors.OnBackground,
    primaryContainer = ScoreAnythingColors.Accent.copy(alpha = 0.3f),
    secondary = ScoreAnythingColors.WinnerGold,
    onSecondary = ScoreAnythingColors.BackgroundDark,
    tertiary = ScoreAnythingColors.Accent,
    background = ScoreAnythingColors.BackgroundDark,
    onBackground = ScoreAnythingColors.OnBackground,
    surface = ScoreAnythingColors.SurfaceDark,
    onSurface = ScoreAnythingColors.OnSurface,
    surfaceVariant = ScoreAnythingColors.SurfaceDark,
    onSurfaceVariant = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f),
)

@Composable
fun ScoreAnythingTheme(
    darkTheme: Boolean = true, // Always dark — matches iOS app
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
