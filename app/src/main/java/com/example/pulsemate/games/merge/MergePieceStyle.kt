package com.example.pulsemate.games.merge

import androidx.compose.ui.graphics.Color

const val MERGE_SHOW_ICONS = true

private val pieceColors = listOf(
    Color(0xFFE53950),
    Color(0xFFFF7A9A),
    Color(0xFFFFA07A),
    Color(0xFFFFD84D),
    Color(0xFF8E4FBF),
    Color(0xFFB5171E),
    Color(0xFFFF4FA3),
    Color(0xFFFF8A1F),
    Color(0xFF5B3FD6),
    Color(0xFFFFC21A),
    Color(0xFF3FD0F2)
)

private val pieceIcons = listOf(
    "🍒",
    "🍓",
    "🍑",
    "🍌",
    "🍆",
    "🌶️",
    "💋",
    "🔥",
    "😈",
    "👑",
    "💎"
)

fun mergePieceColor(stage: Int): Color {
    return pieceColors[stage.coerceIn(0, pieceColors.lastIndex)]
}

fun mergePieceIcon(stage: Int): String {
    return pieceIcons[stage.coerceIn(0, pieceIcons.lastIndex)]
}