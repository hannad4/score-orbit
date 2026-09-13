package com.simplescoring.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
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
    // Always dark — the board is a night-table game surface. On Android 12+
    // the menus follow Material You dynamic color; the board itself keeps
    // its fixed game colors so player dots always read true.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicDarkColorScheme(context)
        else -> DarkColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Safe cast: under layoutlib (screenshots/previews) the context
            // isn't an Activity, and there's no window to paint anyway.
            (view.context as? Activity)?.let { activity ->
                val window = activity.window
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
