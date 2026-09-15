package com.scoreorbit.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ScoreOrbitColors.Accent,
    onPrimary = ScoreOrbitColors.OnBackground,
    primaryContainer = ScoreOrbitColors.Accent.copy(alpha = 0.3f),
    secondary = ScoreOrbitColors.WinnerGold,
    onSecondary = ScoreOrbitColors.BackgroundDark,
    tertiary = ScoreOrbitColors.Accent,
    background = ScoreOrbitColors.BackgroundDark,
    onBackground = ScoreOrbitColors.OnBackground,
    surface = ScoreOrbitColors.SurfaceDark,
    onSurface = ScoreOrbitColors.OnSurface,
    surfaceVariant = ScoreOrbitColors.SurfaceDark,
    onSurfaceVariant = ScoreOrbitColors.OnSurface.copy(alpha = 0.7f),
)

/**
 * Static light scheme mirroring the dark roles (used below Android 12 or
 * with dynamic color off). Newer phones get the wallpaper-tinted Material
 * You palettes instead.
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2B6CB0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF001D35),
    secondary = Color(0xFF7A5900),
    onSecondary = Color.White,
    tertiary = Color(0xFF2B6CB0),
)

/**
 * M3 Expressive shape family: rounder and more playful than baseline —
 * cards, dialogs and sheets pick these up automatically.
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Shared expressive motion: springy, bouncy, playful. Screen transitions
 * and list entrances all draw from here so the whole app moves as one.
 */
object ExpressiveMotion {
    /** Emphasized ease for 300-500ms content transitions. */
    val EmphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    fun contentTween(durationMillis: Int = 350) =
        tween<Float>(durationMillis, easing = EmphasizedEasing)

    fun <T> contentSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

@Composable
fun ScoreOrbitTheme(
    // Follows the OS theme by default (see MainActivity); the board keeps
    // fixed game colors so player dots always read true. On Android 12+
    // the menus use Material You dynamic color.
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
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
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ExpressiveShapes,
        content = content
    )
}
