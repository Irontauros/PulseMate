package com.example.pulsemate.games.battle

import kotlin.random.Random

enum class BattleEventType { MISS, HIT, SUNK, WIN, LOSE }

class BattleEvent(
    val type: BattleEventType,
    val shooter: Int,
    val x: Int = 0,
    val y: Int = 0,
    val len: Int = 0
)

class Ship(val id: Int, val len: Int, var x: Int, var y: Int, var horizontal: Boolean) {
    fun cells(): List<Pair<Int, Int>> {
        return (0 until len).map { if (horizontal) Pair(x + it, y) else Pair(x, y + it) }
    }
}

class Fleet {
    val ships = ArrayList<Ship>()
    val shots = Array(BattleWorld.N) { IntArray(BattleWorld.N) }

    fun clearShots() {
        for (row in shots) row.fill(0)
    }

    fun shipAt(x: Int, y: Int): Ship? {
        return ships.firstOrNull { s -> s.cells().any { it.first == x && it.second == y } }
    }

    fun isSunk(s: Ship): Boolean = s.cells().all { shots[it.second][it.first] == 2 }

    fun isSunkCell(x: Int, y: Int): Boolean {
        val s = shipAt(x, y) ?: return false

        return isSunk(s)
    }

    fun allSunk(): Boolean = ships.isNotEmpty() && ships.all { isSunk(it) }

    fun shipsLeft(): Int = ships.count { !isSunk(it) }
}

class BattleWorld {
    companion object {
        const val N = 10
        const val PLAYER = 0
        const val ENEMY = 1
        val LENGTHS = intArrayOf(5, 4, 3, 3, 2)
    }

    val player = Fleet()
    val enemy = Fleet()

    var difficulty = 2
    var turn = PLAYER
    var over = false
    var winner = -1
    var battle = false
    var gameId = 0

    private val random = Random(System.nanoTime())

    fun newGame(level: Int) {
        difficulty = level
        gameId++
        over = false
        winner = -1
        turn = PLAYER
        battle = false

        build(player)
        build(enemy)
    }

    fun shuffle() {
        randomize(player)
    }

    fun begin() {
        battle = true
        turn = PLAYER
    }

    fun canPlace(s: Ship, x: Int, y: Int, horizontal: Boolean): Boolean {
        val probe = Ship(s.id, s.len, x, y, horizontal)

        for ((cx, cy) in probe.cells()) {
            if (cx < 0 || cy < 0 || cx >= N || cy >= N) return false

            for (other in player.ships) {
                if (other.id == s.id) continue
                if (other.cells().any { it.first == cx && it.second == cy }) return false
            }
        }

        return true
    }

    fun moveShip(id: Int, x: Int, y: Int): Boolean {
        val s = player.ships[id]

        if (!canPlace(s, x, y, s.horizontal)) return false

        s.x = x
        s.y = y

        return true
    }

    fun rotateShip(id: Int): Boolean {
        val s = player.ships[id]
        val h2 = !s.horizontal

        for (k in 0 until s.len) {
            val nx = if (h2) s.x - k else s.x
            val ny = if (h2) s.y else s.y - k

            if (canPlace(s, nx, ny, h2)) {
                s.x = nx
                s.y = ny
                s.horizontal = h2

                return true
            }
        }

        return false
    }

    fun fire(x: Int, y: Int): List<BattleEvent> {
        if (!battle || over || turn != PLAYER) return emptyList()
        if (x !in 0 until N || y !in 0 until N || enemy.shots[y][x] != 0) return emptyList()

        return shoot(enemy, x, y, PLAYER)
    }

    fun aiFire(x: Int, y: Int): List<BattleEvent> {
        if (!battle || over || turn != ENEMY) return emptyList()
        if (x !in 0 until N || y !in 0 until N || player.shots[y][x] != 0) return emptyList()

        return shoot(player, x, y, ENEMY)
    }

    private fun shoot(fleet: Fleet, x: Int, y: Int, shooter: Int): List<BattleEvent> {
        val events = ArrayList<BattleEvent>()
        val ship = fleet.shipAt(x, y)

        if (ship == null) {
            fleet.shots[y][x] = 1
            events.add(BattleEvent(BattleEventType.MISS, shooter, x, y))
        } else {
            fleet.shots[y][x] = 2

            if (fleet.isSunk(ship)) {
                events.add(BattleEvent(BattleEventType.SUNK, shooter, x, y, ship.len))
            } else {
                events.add(BattleEvent(BattleEventType.HIT, shooter, x, y))
            }
        }

        if (fleet.allSunk()) {
            over = true
            winner = shooter
            events.add(
                BattleEvent(
                    if (shooter == PLAYER) BattleEventType.WIN else BattleEventType.LOSE,
                    shooter
                )
            )
        } else {
            turn = if (shooter == PLAYER) ENEMY else PLAYER
        }

        return events
    }

    private fun build(f: Fleet) {
        f.ships.clear()
        f.clearShots()

        for ((i, len) in LENGTHS.withIndex()) f.ships.add(Ship(i, len, 0, 0, true))

        randomize(f)
    }

    private fun randomize(f: Fleet) {
        while (true) {
            val occupied = Array(N) { BooleanArray(N) }
            var allPlaced = true

            for (s in f.ships) {
                var placed = false

                for (attempt in 0 until 200) {
                    val h = random.nextBoolean()
                    val maxX = if (h) N - s.len else N - 1
                    val maxY = if (h) N - 1 else N - s.len

                    s.x = random.nextInt(maxX + 1)
                    s.y = random.nextInt(maxY + 1)
                    s.horizontal = h

                    if (s.cells().none { occupied[it.second][it.first] }) {
                        for ((cx, cy) in s.cells()) occupied[cy][cx] = true

                        placed = true
                        break
                    }
                }

                if (!placed) {
                    allPlaced = false
                    break
                }
            }

            if (allPlaced) return
        }
    }
}