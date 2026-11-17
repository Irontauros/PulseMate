package com.example.pulsemate.games.chase

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class ChaseEvent { SEED, POWER, EAT_CHASER, DIED, CLEARED, GAME_OVER }

class Mover(var c: Int, var r: Int) {
    var tc = c
    var tr = r
    var prog = 0f

    val x: Float get() = c + (tc - c) * prog
    val y: Float get() = r + (tr - r) * prog
    val atTile: Boolean get() = c == tc && r == tr

    fun place(nc: Int, nr: Int) {
        c = nc
        r = nr
        tc = nc
        tr = nr
        prog = 0f
    }
}

class Chaser(
    val index: Int,
    val homeC: Int,
    val homeR: Int,
    val releaseDelay: Float
) {
    val mover = Mover(homeC, homeR)
    var dx = 0
    var dy = 0
    var waiting = releaseDelay
    var scared = false
}

class ChaseWorld {
    companion object {
        const val COLS = 11
        const val ROWS = 13
        const val LIVES = 3
        const val START_C = 5
        const val START_R = 9

        private val TOP = listOf(
            "###########",
            "#o...#...o#",
            "#.##.#.##.#",
            "#.........#",
            "#.##.#.##.#",
            "#....#....#",
            "###.. ..###"
        )

        private val DIRS = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)

        private fun layout(): List<String> {
            val rows = ArrayList<String>(TOP)

            for (k in 5 downTo 0) rows.add(TOP[k])

            return rows
        }
    }

    val grid = Array(ROWS) { CharArray(COLS) }
    val player = Mover(START_C, START_R)
    val chasers = listOf(
        Chaser(0, 4, 6, 0f),
        Chaser(1, 5, 6, 2.5f),
        Chaser(2, 6, 6, 5f)
    )
    val events = ArrayList<ChaseEvent>()

    var pdx = 0
    var pdy = 0
    var wantX = 0
    var wantY = 0
    var seedsLeft = 0
    var score = 0
    var lives = LIVES
    var level = 1
    var scaredLeft = 0f
    var readyLeft = 0f
    var dyingLeft = 0f
    var clearLeft = 0f
    var over = false

    private val random = Random(System.nanoTime())

    private fun loadGrid() {
        val rows = layout()

        seedsLeft = 0

        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val ch = rows[r][c]

                grid[r][c] = ch

                if (ch == '.' || ch == 'o') seedsLeft++
            }
        }

        if (grid[START_R][START_C] == '.') {
            grid[START_R][START_C] = ' '
            seedsLeft--
        }
    }

    private fun resetPositions() {
        player.place(START_C, START_R)
        pdx = 0
        pdy = 0
        wantX = 0
        wantY = 0
        scaredLeft = 0f
        readyLeft = 1.8f

        for (ch in chasers) {
            ch.mover.place(ch.homeC, ch.homeR)
            ch.dx = 0
            ch.dy = 0
            ch.waiting = ch.releaseDelay
            ch.scared = false
        }
    }

    fun start() {
        level = 1
        score = 0
        lives = LIVES
        over = false
        dyingLeft = 0f
        clearLeft = 0f
        events.clear()
        loadGrid()
        resetPositions()
    }

    private fun nextLevel() {
        level++
        loadGrid()
        resetPositions()
    }

    fun isOpen(c: Int, r: Int): Boolean {
        return c in 0 until COLS && r in 0 until ROWS && grid[r][c] != '#'
    }

    fun setWant(dx: Int, dy: Int) {
        wantX = dx
        wantY = dy

        if (!player.atTile && dx == -pdx && dy == -pdy && (dx != 0 || dy != 0)) {
            val oc = player.c
            val orr = player.r

            player.c = player.tc
            player.r = player.tr
            player.tc = oc
            player.tr = orr
            player.prog = 1f - player.prog
            pdx = dx
            pdy = dy
        }
    }

    fun playerSpeed(): Float = 4.6f

    fun chaserSpeed(scared: Boolean): Float {
        return if (scared) 2.3f else min(4.7f, 3.3f + level * 0.25f)
    }

    private fun advance(m: Mover, dist: Float, choose: () -> Unit, arrive: () -> Unit) {
        var left = dist
        var guard = 0

        while (left > 0f && guard < 8) {
            guard++

            if (m.atTile) {
                choose()

                if (m.atTile) return
            }

            val need = 1f - m.prog

            if (left >= need) {
                left -= need
                m.c = m.tc
                m.r = m.tr
                m.prog = 0f
                arrive()
            } else {
                m.prog += left
                left = 0f
            }
        }
    }

    private fun choosePlayer() {
        val m = player

        if ((wantX != 0 || wantY != 0) && isOpen(m.c + wantX, m.r + wantY)) {
            pdx = wantX
            pdy = wantY
        }

        if ((pdx == 0 && pdy == 0) || !isOpen(m.c + pdx, m.r + pdy)) {
            m.tc = m.c
            m.tr = m.r
        } else {
            m.tc = m.c + pdx
            m.tr = m.r + pdy
        }
    }

    private fun arrivePlayer() {
        val cell = grid[player.r][player.c]

        if (cell == '.') {
            grid[player.r][player.c] = ' '
            seedsLeft--
            score += 10
            events.add(ChaseEvent.SEED)
        } else if (cell == 'o') {
            grid[player.r][player.c] = ' '
            seedsLeft--
            score += 50
            scaredLeft = max(3f, 7f - level * 0.8f)

            for (ch in chasers) ch.scared = true

            events.add(ChaseEvent.POWER)
        }

        if (seedsLeft <= 0 && clearLeft <= 0f) {
            clearLeft = 1.4f
            events.add(ChaseEvent.CLEARED)
        }
    }

    private fun dist2(ac: Int, ar: Int, bc: Int, br: Int): Int {
        return (ac - bc) * (ac - bc) + (ar - br) * (ar - br)
    }

    private fun chooseChaser(ch: Chaser) {
        val m = ch.mover
        val open = DIRS.filter { isOpen(m.c + it.first, m.r + it.second) }
        val forward = open.filter { !(it.first == -ch.dx && it.second == -ch.dy) }
        val list = if (forward.isEmpty()) open else forward

        var targetC = player.c
        var targetR = player.r

        if (ch.index == 1) {
            targetC = (player.c + pdx * 3).coerceIn(0, COLS - 1)
            targetR = (player.r + pdy * 3).coerceIn(0, ROWS - 1)
        }

        val pick = when {
            list.size == 1 -> list[0]
            ch.scared && random.nextFloat() < 0.8f ->
                list.maxByOrNull { dist2(m.c + it.first, m.r + it.second, player.c, player.r) } ?: list[0]
            ch.scared -> list[random.nextInt(list.size)]
            random.nextFloat() < (if (ch.index == 2) 0.4f else 0.1f) -> list[random.nextInt(list.size)]
            else -> list.minByOrNull { dist2(m.c + it.first, m.r + it.second, targetC, targetR) } ?: list[0]
        }

        ch.dx = pick.first
        ch.dy = pick.second
        m.tc = m.c + ch.dx
        m.tr = m.r + ch.dy
    }

    fun update(dt: Float) {
        if (over) return

        if (readyLeft > 0f) {
            readyLeft -= dt
            return
        }

        if (dyingLeft > 0f) {
            dyingLeft -= dt

            if (dyingLeft <= 0f) {
                if (lives <= 0) {
                    over = true
                    events.add(ChaseEvent.GAME_OVER)
                } else {
                    resetPositions()
                }
            }

            return
        }

        if (clearLeft > 0f) {
            clearLeft -= dt

            if (clearLeft <= 0f) nextLevel()

            return
        }

        if (scaredLeft > 0f) {
            scaredLeft -= dt

            if (scaredLeft <= 0f) {
                scaredLeft = 0f

                for (ch in chasers) ch.scared = false
            }
        }

        advance(player, playerSpeed() * dt, { choosePlayer() }, { arrivePlayer() })

        for (ch in chasers) {
            if (ch.waiting > 0f) {
                ch.waiting -= dt
                continue
            }

            advance(ch.mover, chaserSpeed(ch.scared) * dt, { chooseChaser(ch) }, { })
        }

        for (ch in chasers) {
            if (ch.waiting > 0f) continue

            val d = hypot(player.x - ch.mover.x, player.y - ch.mover.y)

            if (d < 0.6f) {
                if (ch.scared) {
                    score += 200
                    ch.mover.place(ch.homeC, ch.homeR)
                    ch.dx = 0
                    ch.dy = 0
                    ch.waiting = 2.5f
                    ch.scared = false
                    events.add(ChaseEvent.EAT_CHASER)
                } else {
                    lives--
                    dyingLeft = 1.3f
                    events.add(ChaseEvent.DIED)
                    break
                }
            }
        }
    }

    fun nearestDanger(): Float {
        var best = 99f

        for (ch in chasers) {
            if (ch.scared || ch.waiting > 0f) continue

            best = min(best, hypot(player.x - ch.mover.x, player.y - ch.mover.y))
        }

        return best
    }

    fun proximityLevel(): Int {
        val d = nearestDanger()

        return when {
            d < 1.8f -> 8
            d < 3f -> 6
            d < 4.5f -> 4
            d < 6f -> 2
            else -> 0
        }
    }
}