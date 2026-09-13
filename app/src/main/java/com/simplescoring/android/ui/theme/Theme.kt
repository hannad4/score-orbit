package com.simplescoring.android.ui.theme

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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
        shapes = ExpressiveShapes,
        content = content
    )
}
