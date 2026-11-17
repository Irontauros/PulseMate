package com.example.pulsemate.games.four

enum class FourEventType { LAND, BLOCK, THREAT, WIN, LOSE, DRAW }

class FourEvent(val type: FourEventType, val player: Int, val value: Int = 0)

class Drop(val col: Int, val row: Int, val player: Int) {
    var pos = -1f
    var vel = 0f
}

class FourWorld {
    companion object {
        const val ROWS = 6
        const val COLS = 7
        const val EMPTY = 0
        const val HUMAN = 1
        const val AI = 2
        const val GRAVITY = 42f
    }

    val board = Array(ROWS) { IntArray(COLS) }

    var turn = HUMAN
    var over = false
    var winner = 0
    var winCells: List<Pair<Int, Int>> = emptyList()
    var drop: Drop? = null
    var difficulty = 2
    var gameId = 0

    fun start(level: Int, humanFirst: Boolean) {
        for (row in board) row.fill(EMPTY)

        difficulty = level
        turn = if (humanFirst) HUMAN else AI
        over = false
        winner = 0
        winCells = emptyList()
        drop = null
        gameId++
    }

    fun lowestRow(col: Int): Int {
        for (r in ROWS - 1 downTo 0) if (board[r][col] == EMPTY) return r

        return -1
    }

    fun canPlay(): Boolean = !over && drop == null

    fun wantsAi(): Boolean = canPlay() && turn == AI

    fun humanDrop(col: Int): Boolean {
        if (!canPlay() || turn != HUMAN || col !in 0 until COLS) return false

        val row = lowestRow(col)

        if (row < 0) return false

        drop = Drop(col, row, HUMAN)

        return true
    }

    fun aiDrop(col: Int) {
        if (!wantsAi()) return

        var target = col

        if (target !in 0 until COLS || lowestRow(target) < 0) {
            target = (0 until COLS).firstOrNull { lowestRow(it) >= 0 } ?: return
        }

        drop = Drop(target, lowestRow(target), AI)
    }

    fun snapshot(): Array<IntArray> = Array(ROWS) { board[it].copyOf() }

    fun update(seconds: Float): List<FourEvent> {
        val d = drop ?: return emptyList()

        d.vel += GRAVITY * seconds
        d.pos += d.vel * seconds

        if (d.pos < d.row) return emptyList()

        return land(d)
    }

    private fun land(d: Drop): List<FourEvent> {
        val events = ArrayList<FourEvent>()
        val other = if (d.player == HUMAN) AI else HUMAN
        val blocked = wouldWin(other, d.col)

        board[d.row][d.col] = d.player
        drop = null

        events.add(FourEvent(FourEventType.LAND, d.player, d.row + 1))

        val line = findLine(d.row, d.col, d.player)

        if (line.isNotEmpty()) {
            over = true
            winner = d.player
            winCells = line
            events.add(
                FourEvent(if (d.player == HUMAN) FourEventType.WIN else FourEventType.LOSE, d.player)
            )

            return events
        }

        if (board[0].all { it != EMPTY }) {
            over = true
            events.add(FourEvent(FourEventType.DRAW, 0))

            return events
        }

        if (blocked) events.add(FourEvent(FourEventType.BLOCK, d.player))

        var threats = 0

        for (c in 0 until COLS) if (wouldWin(d.player, c)) threats++

        if (threats > 0) events.add(FourEvent(FourEventType.THREAT, d.player, threats))

        turn = other

        return events
    }

    private fun wouldWin(player: Int, col: Int): Boolean {
        val r = lowestRow(col)

        if (r < 0) return false

        board[r][col] = player

        val result = findLine(r, col, player).isNotEmpty()

        board[r][col] = EMPTY

        return result
    }

    private fun findLine(r: Int, c: Int, p: Int): List<Pair<Int, Int>> {
        val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))

        for (d in dirs) {
            val cells = arrayListOf(Pair(r, c))

            var rr = r + d[0]
            var cc = c + d[1]

            while (rr in 0 until ROWS && cc in 0 until COLS && board[rr][cc] == p) {
                cells.add(Pair(rr, cc))
                rr += d[0]
                cc += d[1]
            }

            rr = r - d[0]
            cc = c - d[1]

            while (rr in 0 until ROWS && cc in 0 until COLS && board[rr][cc] == p) {
                cells.add(Pair(rr, cc))
                rr -= d[0]
                cc -= d[1]
            }

            if (cells.size >= 4) return cells
        }

        return emptyList()
    }
}