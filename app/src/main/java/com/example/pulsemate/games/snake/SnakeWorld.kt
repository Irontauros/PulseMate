package com.example.pulsemate.games.snake

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.random.Random

enum class SnakeEvent { NONE, ATE, DIED }

class SnakeFruitSpot(
    val x: Int,
    val y: Int,
    val type: Int
)

class SnakeWorld(private val random: Random = Random.Default) {

    companion object {
        const val COLS = 12
        const val ROWS = 16
        const val START_LENGTH = 3
        const val FRUIT_COUNT = 2

        private val DX = intArrayOf(0, 1, 0, -1)
        private val DY = intArrayOf(-1, 0, 1, 0)

        fun stepSeconds(length: Int): Float {
            return (0.2f - (length - START_LENGTH) * 0.003f).coerceAtLeast(0.11f)
        }
    }

    val cells = ArrayList<IntArray>()
    val colors = ArrayList<Color>()
    val fruits = ArrayList<SnakeFruitSpot>()

    var prevCells: List<IntArray> = emptyList()
        private set

    var dir: Int = 1
        private set

    var moving: Boolean = false
        private set

    var alive: Boolean = true
        private set

    var score: Int = 0
        private set

    var lastType: Int = 0
        private set

    val dirX: Int get() = DX[dir]
    val dirY: Int get() = DY[dir]

    private val queue = ArrayList<Int>()
    private var snakeColor: Color = SnakeStartColor
    private var targetColor: Color = SnakeStartColor

    init {
        reset()
    }

    fun reset() {
        cells.clear()
        colors.clear()
        fruits.clear()
        queue.clear()

        val cx = COLS / 2
        val cy = ROWS / 2

        for (i in 0 until START_LENGTH) {
            cells.add(intArrayOf(cx - (START_LENGTH - 1) + i, cy))
            colors.add(SnakeStartColor)
        }

        prevCells = cells.map { it.copyOf() }
        dir = 1
        moving = false
        alive = true
        score = 0
        snakeColor = SnakeStartColor
        targetColor = SnakeStartColor

        repeat(FRUIT_COUNT) { spawnFruit() }
    }

    fun turn(newDir: Int) {
        if (!alive) return

        if (!moving && queue.isEmpty() && newDir == dir) {
            moving = true
            return
        }

        val base = if (queue.isEmpty()) dir else queue[queue.size - 1]

        if (newDir == base || (newDir + 2) % 4 == base) return
        if (queue.size >= 2) return

        queue.add(newDir)
        moving = true
    }

    fun step(): SnakeEvent {
        if (!alive || !moving) return SnakeEvent.NONE

        if (queue.isNotEmpty()) {
            dir = queue.removeAt(0)
        }

        val head = cells[cells.size - 1]
        val nx = head[0] + DX[dir]
        val ny = head[1] + DY[dir]

        if (nx < 0 || nx >= COLS || ny < 0 || ny >= ROWS) {
            alive = false
            return SnakeEvent.DIED
        }

        val fruitIndex = fruits.indexOfFirst { it.x == nx && it.y == ny }
        val eating = fruitIndex >= 0
        val first = if (eating) 0 else 1

        for (k in first until cells.size) {
            if (cells[k][0] == nx && cells[k][1] == ny) {
                alive = false
                return SnakeEvent.DIED
            }
        }

        prevCells = cells.map { it.copyOf() }
        snakeColor = lerp(snakeColor, targetColor, 0.45f)
        cells.add(intArrayOf(nx, ny))
        colors.add(snakeColor)

        if (eating) {
            val fruit = fruits.removeAt(fruitIndex)
            lastType = fruit.type
            targetColor = SnakeFruits[fruit.type].color
            snakeColor = lerp(snakeColor, targetColor, 0.6f)
            colors[colors.size - 1] = snakeColor
            score++
            spawnFruit()
            return SnakeEvent.ATE
        }

        cells.removeAt(0)
        colors.removeAt(0)
        return SnakeEvent.NONE
    }

    private fun spawnFruit() {
        val taken = HashSet<Int>()

        for (c in cells) taken.add(c[1] * COLS + c[0])
        for (f in fruits) taken.add(f.y * COLS + f.x)

        if (taken.size >= COLS * ROWS) return

        var index: Int

        do {
            index = random.nextInt(COLS * ROWS)
        } while (index in taken)

        var type = random.nextInt(SnakeFruits.size)

        if (fruits.any { it.type == type }) {
            type = (type + 1 + random.nextInt(SnakeFruits.size - 1)) % SnakeFruits.size
        }

        fruits.add(SnakeFruitSpot(index % COLS, index / COLS, type))
    }
}