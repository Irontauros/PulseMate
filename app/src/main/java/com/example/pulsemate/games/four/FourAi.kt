package com.example.pulsemate.games.four

import kotlin.random.Random

object FourAiEngine {
    private const val ROWS = 6
    private const val COLS = 7
    private const val WIN = 100000
    private const val INF = 1000000

    private val order = intArrayOf(3, 2, 4, 1, 5, 0, 6)
    private val windows: Array<IntArray> = buildWindows()

    private fun buildWindows(): Array<IntArray> {
        val list = ArrayList<IntArray>()

        for (r in 0 until ROWS) {
            for (c in 0..COLS - 4) {
                list.add(IntArray(4) { r * COLS + c + it })
            }
        }

        for (r in 0..ROWS - 4) {
            for (c in 0 until COLS) {
                list.add(IntArray(4) { (r + it) * COLS + c })
            }
        }

        for (r in 0..ROWS - 4) {
            for (c in 0..COLS - 4) {
                list.add(IntArray(4) { (r + it) * COLS + c + it })
                list.add(IntArray(4) { (r + it) * COLS + c + 3 - it })
            }
        }

        return list.toTypedArray()
    }

    fun choose(board: Array<IntArray>, difficulty: Int, random: Random): Int {
        val b = IntArray(ROWS * COLS)
        val heights = IntArray(COLS) { -1 }

        for (r in 0 until ROWS) for (c in 0 until COLS) b[r * COLS + c] = board[r][c]

        for (c in 0 until COLS) {
            for (r in ROWS - 1 downTo 0) {
                if (b[r * COLS + c] == 0) {
                    heights[c] = r
                    break
                }
            }
        }

        val legal = (0 until COLS).filter { heights[it] >= 0 }

        if (legal.size <= 1) return legal.firstOrNull() ?: 0

        val randomChance = when (difficulty) {
            1 -> 0.65f
            2 -> 0.35f
            3 -> 0.12f
            else -> 0f
        }

        if (random.nextFloat() < randomChance) return legal[random.nextInt(legal.size)]

        val depth = when (difficulty) {
            1 -> 1
            2 -> 2
            3 -> 4
            else -> 6
        }

        var bestScore = -INF
        val best = ArrayList<Int>()

        for (c in order) {
            if (heights[c] < 0) continue

            val r = heights[c]

            b[r * COLS + c] = 2
            heights[c]--

            val score = if (winsAt(b, r, c, 2)) {
                WIN + depth
            } else {
                -negamax(b, heights, 1, depth - 1, -INF, INF)
            }

            b[r * COLS + c] = 0
            heights[c]++

            if (score > bestScore) {
                bestScore = score
                best.clear()
                best.add(c)
            } else if (score == bestScore) {
                best.add(c)
            }
        }

        return best[random.nextInt(best.size)]
    }

    private fun negamax(
        b: IntArray,
        heights: IntArray,
        player: Int,
        depth: Int,
        alphaStart: Int,
        beta: Int
    ): Int {
        if (depth == 0) return evaluate(b, player)

        var alpha = alphaStart
        var best = -INF
        var any = false

        for (c in order) {
            if (heights[c] < 0) continue

            any = true

            val r = heights[c]

            b[r * COLS + c] = player
            heights[c]--

            val score = if (winsAt(b, r, c, player)) {
                WIN + depth
            } else {
                -negamax(b, heights, 3 - player, depth - 1, -beta, -alpha)
            }

            b[r * COLS + c] = 0
            heights[c]++

            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta) break
        }

        return if (any) best else 0
    }

    private fun evaluate(b: IntArray, player: Int): Int {
        val other = 3 - player
        var s = 0

        for (w in windows) {
            var p = 0
            var o = 0

            for (i in w) {
                val v = b[i]

                if (v == player) p++ else if (v == other) o++
            }

            if (p > 0 && o > 0) continue

            if (p == 3) s += 6 else if (p == 2) s += 2
            if (o == 3) s -= 7 else if (o == 2) s -= 2
        }

        for (r in 0 until ROWS) {
            val v = b[r * COLS + 3]

            if (v == player) s += 3 else if (v == other) s -= 3
        }

        return s
    }

    private fun winsAt(b: IntArray, r: Int, c: Int, p: Int): Boolean {
        val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))

        for (d in dirs) {
            var count = 1

            var rr = r + d[0]
            var cc = c + d[1]

            while (rr in 0 until ROWS && cc in 0 until COLS && b[rr * COLS + cc] == p) {
                count++
                rr += d[0]
                cc += d[1]
            }

            rr = r - d[0]
            cc = c - d[1]

            while (rr in 0 until ROWS && cc in 0 until COLS && b[rr * COLS + cc] == p) {
                count++
                rr -= d[0]
                cc -= d[1]
            }

            if (count >= 4) return true
        }

        return false
    }
}