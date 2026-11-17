package com.example.pulsemate.games.dig

import kotlin.math.hypot
import kotlin.random.Random

class DigWorld {
    companion object {
        const val COLS = 7
        const val ROWS = 9
        const val COUNT = COLS * ROWS
        const val FOUND = 5
    }

    val dug = BooleanArray(COUNT)
    val band = IntArray(COUNT) { -1 }

    var treasure = 0
    var level = 1
    var score = 0
    var digsLeft = 9
    var found = false
    var failed = false
    var lastDug = -1

    private val random = Random(System.nanoTime())

    fun digsFor(value: Int): Int = (9 - (value - 1) / 3).coerceAtLeast(6)

    fun start() {
        level = 1
        score = 0
        newRound()
    }

    fun newRound() {
        for (i in 0 until COUNT) {
            dug[i] = false
            band[i] = -1
        }

        treasure = random.nextInt(COUNT)
        digsLeft = digsFor(level)
        found = false
        failed = false
        lastDug = -1
    }

    fun nextLevel() {
        level++
        newRound()
    }

    private fun bandFor(i: Int): Int {
        val dx = (i % COLS - treasure % COLS).toFloat()
        val dy = (i / COLS - treasure / COLS).toFloat()
        val d = hypot(dx, dy)

        return when {
            d <= 1.5f -> 4
            d <= 2.5f -> 3
            d <= 3.6f -> 2
            d <= 5f -> 1
            else -> 0
        }
    }

    fun dig(i: Int): Int {
        if (i !in 0 until COUNT || dug[i] || found || failed) return -1

        dug[i] = true
        digsLeft--
        lastDug = i

        if (i == treasure) {
            found = true
            band[i] = FOUND
            score += 100 + digsLeft * 50

            return FOUND
        }

        val b = bandFor(i)

        band[i] = b

        if (digsLeft <= 0) failed = true

        return b
    }
}