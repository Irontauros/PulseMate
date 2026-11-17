package com.example.pulsemate.games.fit

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class FitEventType { PLACE, INVALID, LIFT, LEVEL_CLEAR }

class FitEvent(val type: FitEventType, val value: Int = 0)

class Piece(
    val id: Int,
    val color: Int,
    val base: List<Pair<Int, Int>>,
    val homeX: Int,
    val homeY: Int
) {
    var cells: List<Pair<Int, Int>> = base
    var placed = false
    var px = 0
    var py = 0

    val width: Int get() = cells.maxOf { it.first } + 1
    val height: Int get() = cells.maxOf { it.second } + 1
}

class FitWorld {
    companion object {
        const val EMPTY = -1
        const val VOID = -2
        const val MAX_SPAN = 4
    }

    private class Shape(val name: String, val rows: List<String>)

    private val shapes = listOf(
        Shape("Diamond", listOf("..#..", ".###.", "#####", ".###.", "..#..")),
        Shape("Heart", listOf("##.##", "#####", ".###.", "..#..")),
        Shape("Arrow", listOf("..#..", ".###.", "#####", "..#..", "..#..", "..#..")),
        Shape("Ring", listOf(".###.", "#####", "##.##", "#####", ".###.")),
        Shape("Gem", listOf(".#####.", "#######", ".#####.", "..###..", "...#...")),
        Shape("Cross", listOf("..##..", "..##..", "######", "######", "..##..", "..##..")),
        Shape("House", listOf("..##..", ".####.", "######", "######", "######", "######")),
        Shape("Hexagon", listOf("..####..", ".######.", "########", "########", ".######.", "..####..")),
        Shape("Castle", listOf("#.#.#.#", "#######", ".#####.", ".#####.", ".#####.", "#######"))
    )

    var level = 1
    var shapeName = ""
    var gw = 0
    var gh = 0
    var grid: Array<IntArray> = emptyArray()
    val pieces = ArrayList<Piece>()
    var placedCount = 0
    var solved = false

    private val random = Random(System.nanoTime())

    fun startLevel(n: Int) {
        level = max(1, n)

        val shape = shapes[(level - 1) % shapes.size]

        shapeName = shape.name
        gh = shape.rows.size
        gw = shape.rows.maxOf { it.length }
        grid = Array(gh) { y ->
            IntArray(gw) { x ->
                if (x < shape.rows[y].length && shape.rows[y][x] == '#') EMPTY else VOID
            }
        }

        buildPieces()
        placedCount = 0
        solved = false
    }

    fun nextLevel() {
        startLevel(level + 1)
    }

    fun restart() {
        for (p in pieces) lift(p.id)
    }

    fun canPlace(p: Piece, ox: Int, oy: Int): Boolean {
        for ((cx, cy) in p.cells) {
            val x = ox + cx
            val y = oy + cy

            if (x < 0 || y < 0 || x >= gw || y >= gh) return false
            if (grid[y][x] != EMPTY) return false
        }

        return true
    }

    fun place(id: Int, ox: Int, oy: Int): List<FitEvent> {
        val p = pieces[id]

        if (p.placed || !canPlace(p, ox, oy)) return listOf(FitEvent(FitEventType.INVALID))

        for ((cx, cy) in p.cells) grid[oy + cy][ox + cx] = id

        p.placed = true
        p.px = ox
        p.py = oy
        placedCount++

        val events = arrayListOf(FitEvent(FitEventType.PLACE, placedCount))

        if (placedCount == pieces.size) {
            solved = true
            events.add(FitEvent(FitEventType.LEVEL_CLEAR))
        }

        return events
    }

    fun lift(id: Int): List<FitEvent> {
        val p = pieces[id]

        if (!p.placed) return emptyList()

        for ((cx, cy) in p.cells) grid[p.py + cy][p.px + cx] = EMPTY

        p.placed = false
        placedCount--
        solved = false

        return listOf(FitEvent(FitEventType.LIFT))
    }

    fun hint(): List<FitEvent> {
        val p = pieces.firstOrNull { !it.placed } ?: return emptyList()

        for ((cx, cy) in p.base) {
            val owner = grid[p.homeY + cy][p.homeX + cx]

            if (owner >= 0 && owner != p.id) lift(owner)
        }

        p.cells = p.base

        return place(p.id, p.homeX, p.homeY)
    }

    private fun buildPieces() {
        pieces.clear()

        val groups = partition().shuffled(random)

        for ((i, cells) in groups.withIndex()) {
            val minX = cells.minOf { it % gw }
            val minY = cells.minOf { it / gw }
            val base = cells.map { Pair(it % gw - minX, it / gw - minY) }
            val piece = Piece(i, i % FitColors.size, base, minX, minY)

            pieces.add(piece)
        }
    }

    private fun partition(): List<List<Int>> {
        var area = 0

        for (row in grid) for (v in row) if (v == EMPTY) area++

        val avg = when {
            area <= 14 -> 3
            area <= 24 -> 4
            else -> 5
        }

        var best: List<List<Int>>? = null
        var bestBad = Int.MAX_VALUE

        for (attempt in 0 until 200) {
            val result = attempt(avg)

            if (result.second < bestBad) {
                best = result.first
                bestBad = result.second
            }

            if (bestBad == 0) break
        }

        return best!!
    }

    private fun attempt(avg: Int): Pair<List<List<Int>>, Int> {
        val total = gw * gh
        val owner = IntArray(total) { if (grid[it / gw][it % gw] == EMPTY) -1 else -2 }
        val groups = ArrayList<ArrayList<Int>>()

        while (true) {
            var seed = -1
            var seedFree = 99

            for (i in 0 until total) {
                if (owner[i] != -1) continue

                val f = freeNeighbors(owner, i).size

                if (f < seedFree || (f == seedFree && random.nextInt(3) == 0)) {
                    seed = i
                    seedFree = f
                }
            }

            if (seed < 0) break

            val index = groups.size
            val cells = arrayListOf(seed)
            val target = (avg + random.nextInt(3) - 1).coerceIn(2, 5)

            owner[seed] = index

            while (cells.size < target) {
                var pick = -1
                var pickScore = Int.MAX_VALUE

                for (c in cells) {
                    for (n in freeNeighbors(owner, c)) {
                        if (!spanOk(cells, n)) continue

                        val score = freeNeighbors(owner, n).size * 10 + random.nextInt(15)

                        if (score < pickScore) {
                            pick = n
                            pickScore = score
                        }
                    }
                }

                if (pick < 0) break

                cells.add(pick)
                owner[pick] = index
            }

            groups.add(cells)
        }

        var bad = 0

        for (cells in groups.toList()) {
            if (cells.size != 1) continue

            val c = cells[0]
            var target = -1

            for (n in neighbors(c)) {
                val o = owner[n]

                if (o >= 0 && o != owner[c] && groups[o].size < 6 && spanOk(groups[o], c)) {
                    target = o
                    break
                }
            }

            if (target < 0) {
                bad++
            } else {
                groups[target].add(c)
                owner[c] = target
                cells.clear()
            }
        }

        return Pair(groups.filter { it.isNotEmpty() }, bad)
    }

    private fun neighbors(c: Int): List<Int> {
        val x = c % gw
        val y = c / gw
        val result = ArrayList<Int>()

        if (x > 0) result.add(c - 1)
        if (x < gw - 1) result.add(c + 1)
        if (y > 0) result.add(c - gw)
        if (y < gh - 1) result.add(c + gw)

        return result
    }

    private fun freeNeighbors(owner: IntArray, c: Int): List<Int> {
        return neighbors(c).filter { owner[it] == -1 }
    }

    private fun spanOk(cells: List<Int>, extra: Int): Boolean {
        var minX = extra % gw
        var maxX = minX
        var minY = extra / gw
        var maxY = minY

        for (c in cells) {
            minX = min(minX, c % gw)
            maxX = max(maxX, c % gw)
            minY = min(minY, c / gw)
            maxY = max(maxY, c / gw)
        }

        return maxX - minX + 1 <= MAX_SPAN && maxY - minY + 1 <= MAX_SPAN
    }
}