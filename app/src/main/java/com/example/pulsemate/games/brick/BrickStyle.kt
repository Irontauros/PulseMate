package com.example.pulsemate.games.brick

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily

val BrickFont: FontFamily = FontFamily(Typeface.create("sans-serif-black", Typeface.NORMAL))

val BrickBgTop = Color(0xFF2B3FB8)
val BrickBgBottom = Color(0xFF7A3BC4)
val BrickInk = Color(0xFFF3F3F8)
val BrickPanel = Color(0xFF1E1E2A)
val BrickEdge = Color(0xFFFFC72C)
val BrickBtnFace = Color(0xFFFFC72C)
val BrickBtnDark = Color(0xFFB58900)
val BrickBtnSoft = Color(0xFF3A3A4E)
val BrickBtnSoftDark = Color(0xFF22222E)
val BrickTitle = Color(0xFFFFC72C)
val PaddleWood = Color(0xFFFFC72C)

val BrickBase = listOf(
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFF1E88E5),
    Color(0xFF2EAA4F)
)

fun brickColor(max: Int, left: Int): Color {
    val base = BrickBase[(max - 1).coerceIn(0, BrickBase.lastIndex)]
    val progress = (max - left).toFloat() / max.coerceAtLeast(1)

    return lerp(base, Color.White, 0.55f * progress)
}

val BallEmojis = listOf(
    "👑", "🔥", "🌶️", "😈", "💎",
    "⭐", "🌸", "🦄", "🍩", "🍭",
)