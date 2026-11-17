package com.example.pulsemate.games.snake

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

val SnakeFont: FontFamily = FontFamily(Typeface.create("sans-serif-smallcaps", Typeface.BOLD))

val SnakeBgTop = Color(0xFFFFE9F2)
val SnakeBgBottom = Color(0xFFE4DCFF)
val SnakeBoardA = Color(0xFFFFFDF8)
val SnakeBoardB = Color(0xFFFFF1F6)
val SnakeBoardEdge = Color(0xFFF7B7CF)
val SnakeInk = Color(0xFF6B4A5E)
val SnakeStartColor = Color(0xFFB5EAD7)
val SnakePanel = Color(0xFFFFF8F2)
val SnakeBtnPink = Color(0xFFFF9DBB)
val SnakeBtnPinkDark = Color(0xFFE0759A)
val SnakeBtnLilac = Color(0xFFEDE4FF)
val SnakeBtnLilacDark = Color(0xFFC9B8F2)
val SnakeTitle = Color(0xFF9B7FD1)

class SnakeFruit(
    val name: String,
    val emoji: String,
    val fallback: String,
    val color: Color,
    val level: Int
)

val SnakeFruits = listOf(
    SnakeFruit("Melon", "🍈", "🍈", Color(0xFFBDEBB3), 2),
    SnakeFruit("Peach", "🍑", "🍑", Color(0xFFFFC9D8), 3),
    SnakeFruit("Banana", "🍌", "🍌", Color(0xFFFFEE9E), 4),
    SnakeFruit("Orange", "🍊", "🍊", Color(0xFFFFCB9A), 5),
    SnakeFruit("Cherry", "🍒", "🍒", Color(0xFFFF8C9C), 6),
    SnakeFruit("Blueberry", "🫐", "🔵", Color(0xFFA8C8FF), 7),
    SnakeFruit("Grape", "🍇", "🍇", Color(0xFFD7B8F7), 8)
)

fun snakeFruitEmoji(fruit: SnakeFruit, paint: Paint): String {
    return if (paint.hasGlyph(fruit.emoji)) fruit.emoji else fruit.fallback
}