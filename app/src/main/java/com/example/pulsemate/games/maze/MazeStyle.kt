package com.example.pulsemate.games.maze

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily

val MazeFont: FontFamily = FontFamily(Typeface.create("casual", Typeface.BOLD))

val MeadowTop = Color(0xFFFFF0B3)
val MeadowBottom = Color(0xFFB8E6A0)
val GrassA = Color(0xFFA9E386)
val GrassB = Color(0xFF9AD977)
val HedgeDark = Color(0xFF1F7A33)
val HedgeMid = Color(0xFF38AA4C)
val HedgeLight = Color(0xFF74D281)
val Cocoa = Color(0xFF5A3A22)
val CreamPanel = Color(0xFFFFF6DC)
val CreamEdge = Color(0xFFC99B5B)
val BtnOrange = Color(0xFFFF8F3D)
val BtnOrangeDark = Color(0xFFD1661A)
val BtnCream = Color(0xFFFFF1CC)
val BtnCreamDark = Color(0xFFE0C98E)
val StarYellow = Color(0xFFFFC93C)

val MazeHeatStops = listOf(
    Color(0xFF8EC5FF),
    Color(0xFF7FE0C3),
    Color(0xFFB8E86B),
    Color(0xFFFFD84D),
    Color(0xFFFFA24D),
    Color(0xFFFF6B6B),
    Color(0xFFFF5FA8)
)

fun mazeHeat(warmth: Float): Color {
    val t = warmth.coerceIn(0f, 1f) * (MazeHeatStops.size - 1)
    val i = t.toInt().coerceAtMost(MazeHeatStops.size - 2)
    return lerp(MazeHeatStops[i], MazeHeatStops[i + 1], t - i)
}