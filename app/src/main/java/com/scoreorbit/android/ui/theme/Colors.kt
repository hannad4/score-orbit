package com.scoreorbit.android.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

object ScoreOrbitColors {
    val BackgroundDark = Color(0xFF121212)
    val SurfaceDark = Color(0xFF1E1E1E)
    val ScoreTileBg = Color(0xFF2D2D2D)
    val ScoreTilePressed = Color(0xFF404040)
    val Accent = Color(0xFF5B9BD5)
    val WinnerGold = Color(0xFFFFD54F)
    val UndoDisabled = Color(0xFF616161)
    val OnBackground = Color.White
    val OnSurface = Color.White

    /**
     * Player dots, in auto-assign order. The first 12 are spaced widely
     * around the hue wheel (neighbors always contrast) so a full 12-player
     * table never lands two look-alike colors; the rest are muted alternates
     * for manual picking.
     */
    val PlayerColors = listOf(
        0xFFE53935.toInt(), // Red
        0xFF43A047.toInt(), // Green
        0xFF1E88E5.toInt(), // Blue
        0xFFFBC02D.toInt(), // Yellow
        0xFFAB47BC.toInt(), // Purple
        0xFF00897B.toInt(), // Teal
        0xFFFD7E14.toInt(), // Orange
        0xFF3949AB.toInt(), // Indigo
        0xFFEC407A.toInt(), // Pink
        0xFF00ACC1.toInt(), // Cyan
        0xFFF5F5F5.toInt(), // White
        0xFF9CCC65.toInt(), // Lime
        0xFFFFCA28.toInt(), // Amber
        0xFF66BB6A.toInt(), // Light green
        0xFFEF5350.toInt(), // Light red
        0xFF29B6F6.toInt(), // Sky
        0xFF26A69A.toInt(), // Seafoam
        0xFF5E35B1.toInt(), // Deep purple
        0xFFE65100.toInt(), // Burnt orange
        0xFF6D4C41.toInt(), // Brown
        0xFF8D6E63.toInt(), // Tan
        0xFF546E7A.toInt(), // Slate
        0xFF78909C.toInt(), // Steel
        0xFF37474F.toInt(), // Charcoal
    )
}
