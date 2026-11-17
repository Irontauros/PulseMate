package com.example.pulsemate.games.fruit

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

val FruitFont: FontFamily = FontFamily(Typeface.create("casual", Typeface.BOLD))

val FruitBgTop = Color(0xFFE9C792)
val FruitBgBottom = Color(0xFFD4A56A)
val FruitInk = Color(0xFF3B2415)
val FruitPanel = Color(0xFFE2B87A)
val FruitEdge = Color(0xFF6B4423)
val FruitBtnFace = Color(0xFFD5DCE5)
val FruitBtnDark = Color(0xFF8A94A3)
val FruitBtnSoft = Color(0xFFD9A066)
val FruitBtnSoftDark = Color(0xFF8F5F32)
val FruitTitle = Color(0xFF8A2F14)
val BoardWood = Color(0xFFB87A45)
val BoardWoodLight = Color(0xFFE6C08A)
val BoardGrain = Color(0xFF8F5F32)

const val KIND_BOMB = -1
const val KIND_BONUS = -2

class FruitDef(
    val name: String,
    val emoji: String,
    val fallback: String,
    val color: Color,
    val level: Int
)

val FruitDefs = listOf(
    FruitDef("Melon", "🍈", "🍈", Color(0xFFBDEBB3), 2),
    FruitDef("Peach", "🍑", "🍑", Color(0xFFFFC9D8), 3),
    FruitDef("Banana", "🍌", "🍌", Color(0xFFFFEE9E), 4),
    FruitDef("Strawberry", "🍓", "🍓", Color(0xFFFF9DB0), 5),
    FruitDef("Cherry", "🍒", "🍒", Color(0xFFFF8C9C), 6),
    FruitDef("Eggplant", "🍆", "🍆", Color(0xFFC7A6EA), 7)
)

const val BOMB_EMOJI = "😈"
const val BONUS_EMOJI = "💎"