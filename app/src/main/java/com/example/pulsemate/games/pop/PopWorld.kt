package com.example.pulsemate.games.pop

import kotlin.math.min
import kotlin.random.Random

enum class PopResult { CONTINUE, CLEAR, FAIL }

class PopWorld {
    companion object {
        const val COLS = 6
        const val ROWS = 8
        const val COUNT = COLS * ROWS
        const val LIVES = 3
        const val TAPS = 3
    }

    val cells = IntArray(COUNT)

    var level = 1
    var lives = LIVES
    var score = 0
    var tapsLeft = TAPS
    var target = 6
    var popped = 0
    var wave = 0

    private var pending = LinkedHashSet<Int>()
    private val random = Random(System.nanoTime())

    val chainActive: Boolean get() = pending.isNotEmpty()

    fun start() {
        level = 1
        lives = LIVES
        score = 0
        newBoard()
    }

    fun bubblesLeft(): Int = cells.count { it > 0 }

    fun newBoard() {
        tapsLeft = TAPS
        popped = 0
        wave = 0
        pending = LinkedHashSet()

        for (i in 0 until COUNT) cells[i] = 0

        val density = min(0.75f, 0.5f + level * 0.02f)

        for (i in 0 until COUNT) {
            if (random.nextFloat() < density) cells[i] = 1 + random.nextInt(3)
        }

        val wanted = 6 + (level - 1) * 2

        while (bubblesLeft() < wanted + 8) {
            val i = random.nextInt(COUNT)

            if (cells[i] == 0) cells[i] = 1 + random.nextInt(3)
        }

        target = min(wanted, (bubblesLeft() * 0.6f).toInt())
    }

    fun tap(i: Int): Boolean {
        if (chainActive || tapsLeft <= 0 || i !in 0 until COUNT || cells[i] <= 0) return false

        tapsLeft--
        cells[i]++
        wave = 0

        if (cells[i] > 3) pending.add(i)

        return true
    }

    fun stepWave(): List<Int> {
        if (pending.isEmpty()) return emptyList()

        wave++

        val current = pending.toList()
        val next = LinkedHashSet<Int>()

        for (p in current) cells[p] = 0

        for (p in current) {
            popped++
            score += 10 * wave

            val c = p % COLS
            val r = p / COLS

            val neighbors = ArrayList<Int>()

            if (c > 0) neighbors.add(p - 1)
            if (c < COLS - 1) neighbors.add(p + 1)
            if (r > 0) neighbors.add(p - COLS)
            if (r < ROWS - 1) neighbors.add(p + COLS)

            for (n in neighbors) {
                if (cells[n] > 0) {
                    cells[n]++

                    if (cells[n] > 3) next.add(n)
                }
            }
        }

        pending = next

        return current
    }

    fun resolve(): PopResult {
        if (popped >= target) {
            score += tapsLeft * 50

            return PopResult.CLEAR
        }

        if (tapsLeft <= 0 || bubblesLeft() == 0) {
            lives--

            return PopResult.FAIL
        }

        return PopResult.CONTINUE
    }

    fun advanceLevel() {
        level++
        newBoard()
    }

    fun retryBoard() {
        newBoard()
    }
}