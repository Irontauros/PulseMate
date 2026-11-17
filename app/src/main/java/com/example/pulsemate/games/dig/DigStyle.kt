package com.example.pulsemate.games.dig

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

val DigFont: FontFamily = FontFamily(
    Typeface.create("serif", Typeface.ITALIC)
)

val DigBgTop = Color(0xFFFF9A76)
val DigBgBottom = Color(0xFFE85D75)
val DigPanel = Color(0xFFFFF3E0)
val DigInk = Color(0xFF4A2231)
val DigDim = Color(0xFF6B2D3E)
val DigGold = Color(0xFFFFC233)
val DigRed = Color(0xFFD62839)
val DigSand = Color(0xFFF2D49B)
val DigSandEdge = Color(0xFFD9B26F)
val DigSandDot = Color(0xFFE3C07E)
val DigHole = Color(0xFFB58A55)
val DigChest = Color(0xFF8B5A2B)

val DigBandColors = listOf(
    Color(0xFF6EC1E4),
    Color(0xFF8ED1B5),
    Color(0xFFFFD166),
    Color(0xFFFF9F43),
    Color(0xFFE63946)
)

val DigBandNames = listOf("COLD", "COOL", "WARM", "HOT", "BURNING")