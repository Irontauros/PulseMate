package com.example.pulsemate.games.tile

import kotlin.math.max
import kotlin.random.Random

enum class TileTap { CORRECT, ROUND_DONE, WRONG }

class TileWorld {
    companion object {
        const val SIZE = 4
        const val COUNT = 16
        const val LIVES = 3
    }

    var round = 1
    var lives = LIVES
    var score = 0
    var inputIndex = 0
    var inputLeft = 0f
    var inputTotal = 1f
    val seq = ArrayList<Int>()

    private val random = Random(System.nanoTime())

    fun start() {
        round = 1
        lives = LIVES
        score = 0
        seq.clear()

        repeat(3) { seq.add(random.nextInt(COUNT)) }
    }

    fun nextRound() {
        round++
        seq.add(random.nextInt(COUNT))
    }

    fun showMillis(): Long = max(260L, 620L - round * 28L)

    fun gapMillis(): Long = max(120L, 260L - round * 10L)

    fun levelFor(tile: Int): Int = 3 + (tile / SIZE) * 2

    fun beginInput() {
        inputIndex = 0
        inputTotal = 2.5f + seq.size * max(0.7f, 1.2f - round * 0.03f)
        inputLeft = inputTotal
    }

    fun tap(tile: Int): TileTap {
        if (seq[inputIndex] != tile) {
            lives--
            return TileTap.WRONG
        }

        inputIndex++

        if (inputIndex >= seq.size) {
            score += seq.size * 10
            return TileTap.ROUND_DONE
        }

        return TileTap.CORRECT
    }

    fun timeout() {
        lives--
    }
}