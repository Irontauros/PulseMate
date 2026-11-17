package com.example.pulsemate.games.memory

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

val MemoryFont: FontFamily = FontFamily(Typeface.create("serif", Typeface.BOLD_ITALIC))

val FeltLight = Color(0xFF14664A)
val FeltDark = Color(0xFF062A1F)
val FeltPanel = Color(0xFF0F4533)
val Gold = Color(0xFFE2B84A)
val Cream = Color(0xFFF8EEDA)
val CreamMatched = Color(0xFFFFF1B8)
val CardBackTop = Color(0xFF8A2433)
val CardBackBottom = Color(0xFF4A0F1A)
val InkGreen = Color(0xFF0B2A1F)

private val pool = listOf(
    "🍑", "🍌", "🍒", "🍆", "🔥", "😈",
    "🍩", "🍦", "🥂", "🌹", "🍭", "🍯",
    "🍫", "🍷", "💋", "🎀", "💎", "👑",
    "🌶️", "🍉", "🍍", "🍿", "🎩", "🌙"
)

private val lookalikeTiers: List<List<Pair<String, String>>> = listOf(
    listOf(
        "🍒" to "🍓",
        "🍑" to "🍎",
        "🔥" to "🌶️",
        "🍫" to "🍪",
        "🍷" to "🥂",
        "🌹" to "🌷"
    ),
    listOf(
        "😈" to "👿",
        "🍦" to "🍨",
        "🍩" to "🍪",
        "💋" to "👄",
        "🍉" to "🍈",
        "🍓" to "🍅"
    ),
    listOf(
        "😀" to "😃",
        "😃" to "😄",
        "😊" to "🙂",
        "🍎" to "🍅"
    )
)

private val allFaces: List<String> =
    (pool + lookalikeTiers.flatten().flatMap { listOf(it.first, it.second) }).distinct()

val MemoryFaceCount: Int = pool.size

val MemoryTierCount: Int = lookalikeTiers.size

fun memoryFace(index: Int): String {
    return allFaces[index.coerceIn(0, allFaces.lastIndex)]
}

fun memoryLookalikeCount(tier: Int): Int {
    return lookalikeTiers[tier.coerceIn(0, lookalikeTiers.lastIndex)].size
}

fun memoryLookalike(tier: Int, pick: Int): Pair<Int, Int> {
    val list = lookalikeTiers[tier.coerceIn(0, lookalikeTiers.lastIndex)]
    val pair = list[pick.coerceIn(0, list.lastIndex)]
    return allFaces.indexOf(pair.first) to allFaces.indexOf(pair.second)
}