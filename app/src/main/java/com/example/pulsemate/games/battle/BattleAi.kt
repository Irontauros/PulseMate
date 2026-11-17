package com.example.pulsemate.games.battle

import kotlin.random.Random

object BattleAiEngine {
    private val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

    fun choose(fleet: Fleet, difficulty: Int, random: Random): Pair<Int, Int> {
        val n = BattleWorld.N
        val unshot = ArrayList<Pair<Int, Int>>()
        val unresolved = ArrayList<Pair<Int, Int>>()

        for (y in 0 until n) {
            for (x in 0 until n) {
                val s = fleet.shots[y][x]

                if (s == 0) unshot.add(Pair(x, y))
                if (s == 2 && !fleet.isSunkCell(x, y)) unresolved.add(Pair(x, y))
            }
        }

        return when (difficulty) {
            1 -> easy(fleet, unshot, unresolved, random)
            2 -> medium(fleet, unshot, unresolved, random)
            else -> hard(fleet, difficulty, unshot, unresolved, random)
        }
    }

    private fun inBounds(x: Int, y: Int): Boolean {
        return x in 0 until BattleWorld.N && y in 0 until BattleWorld.N
    }

    private fun easy(
        fleet: Fleet,
        unshot: List<Pair<Int, Int>>,
        unresolved: List<Pair<Int, Int>>,
        random: Random
    ): Pair<Int, Int> {
        if (unresolved.isNotEmpty() && random.nextFloat() < 0.35f) {
            val h = unresolved[random.nextInt(unresolved.size)]
            val options = ArrayList<Pair<Int, Int>>()

            for (d in dirs) {
                val nx = h.first + d[0]
                val ny = h.second + d[1]

                if (inBounds(nx, ny) && fleet.shots[ny][nx] == 0) options.add(Pair(nx, ny))
            }

            if (options.isNotEmpty()) return options[random.nextInt(options.size)]
        }

        return unshot[random.nextInt(unshot.size)]
    }

    private fun medium(
        fleet: Fleet,
        unshot: List<Pair<Int, Int>>,
        unresolved: List<Pair<Int, Int>>,
        random: Random
    ): Pair<Int, Int> {
        if (unresolved.isNotEmpty()) {
            val scores = HashMap<Pair<Int, Int>, Int>()

            for (h in unresolved) {
                for (d in dirs) {
                    val nx = h.first + d[0]
                    val ny = h.second + d[1]

                    if (!inBounds(nx, ny) || fleet.shots[ny][nx] != 0) continue

                    var s = 1
                    val bx = h.first - d[0]
                    val by = h.second - d[1]

                    if (inBounds(bx, by) && fleet.shots[by][bx] == 2 && !fleet.isSunkCell(bx, by)) s += 3

                    val key = Pair(nx, ny)

                    scores[key] = (scores[key] ?: 0) + s
                }
            }

            if (scores.isNotEmpty()) {
                val top = scores.values.maxOrNull() ?: 0
                val best = scores.filter { it.value == top }.keys.toList()

                return best[random.nextInt(best.size)]
            }
        }

        val parity = unshot.filter { (it.first + it.second) % 2 == 0 }

        return if (parity.isNotEmpty()) parity[random.nextInt(parity.size)] else unshot[random.nextInt(unshot.size)]
    }

    private fun hard(
        fleet: Fleet,
        difficulty: Int,
        unshot: List<Pair<Int, Int>>,
        unresolved: List<Pair<Int, Int>>,
        random: Random
    ): Pair<Int, Int> {
        val n = BattleWorld.N
        val lens = fleet.ships.filter { !fleet.isSunk(it) }.map { it.len }
        var density = Array(n) { IntArray(n) }

        fun compute(useHits: Boolean): Array<IntArray> {
            val d = Array(n) { IntArray(n) }

            for (len in lens) {
                for (horizontal in listOf(true, false)) {
                    for (y in 0 until n) {
                        for (x in 0 until n) {
                            val ex = if (horizontal) x + len - 1 else x
                            val ey = if (horizontal) y else y + len - 1

                            if (ex >= n || ey >= n) continue

                            var valid = true
                            var covers = 0

                            for (i in 0 until len) {
                                val cx = if (horizontal) x + i else x
                                val cy = if (horizontal) y else y + i
                                val s = fleet.shots[cy][cx]

                                if (s == 1 || (s == 2 && fleet.isSunkCell(cx, cy))) {
                                    valid = false
                                    break
                                }

                                if (s == 2) covers++
                            }

                            if (!valid) continue
                            if (useHits && covers == 0) continue

                            val weight = if (useHits) covers * covers * 50 else 1

                            for (i in 0 until len) {
                                val cx = if (horizontal) x + i else x
                                val cy = if (horizontal) y else y + i

                                if (fleet.shots[cy][cx] == 0) d[cy][cx] += weight
                            }
                        }
                    }
                }
            }

            return d
        }

        density = compute(unresolved.isNotEmpty())

        var max = 0

        for (p in unshot) max = maxOf(max, density[p.second][p.first])

        if (max == 0) {
            density = compute(false)

            for (p in unshot) max = maxOf(max, density[p.second][p.first])
        }

        if (max == 0) return unshot[random.nextInt(unshot.size)]

        var best = unshot.filter { density[it.second][it.first] >= max }

        if (difficulty == 3 && random.nextFloat() < 0.25f) {
            best = unshot.filter { density[it.second][it.first] * 10 >= max * 7 }
        }

        return best[random.nextInt(best.size)]
    }
}