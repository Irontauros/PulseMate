package com.example.pulsemate.games.maze

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

enum class MazeMode(val title: String, val hint: String) {
    CLASSIC("Classic", "See the whole maze. Follow the warmth bar to the star."),
    FOG("Foggy", "Thick fog. Hedges only show when you touch them.")
}

class MazeWorld(private val random: Random = Random.Default) {

    companion object {
        const val WALL = 0.18f
        const val BALL_R = 0.27f

        private const val STEP = 1f / 120f
        private const val MAX_FRAME = 0.05f
        private const val ACCEL = 11f
        private const val FRICTION = 1.6f
        private const val MAX_SPEED = 7f
        private const val RESTITUTION = 0.25f
        private const val WIN_DIST2 = 0.09f

        fun sizeFor(level: Int): Int = (4 + level).coerceAtMost(11)
    }

    var mode: MazeMode = MazeMode.CLASSIC
        private set

    var level: Int = 1
        private set

    var completed: Int = 0
        private set

    var size: Int = 5
        private set

    var hWall: Array<BooleanArray> = emptyArray()
        private set

    var vWall: Array<BooleanArray> = emptyArray()
        private set

    var px: Float = 0.5f
        private set

    var py: Float = 0.5f
        private set

    var exitX: Int = 0
        private set

    var exitY: Int = 0
        private set

    var time: Float = 0f
        private set

    var won: Boolean = false
        private set

    var flash: Float = 0f
        private set

    private var vx = 0f
    private var vy = 0f
    private var accumulator = 0f
    private var rects = FloatArray(0)
    private var rectCount = 0
    private var dist: Array<IntArray> = emptyArray()
    private var maxDist = 1

    val warmth: Float
        get() = 1f - currentDistance().toFloat() / maxDist

    init {
        buildLevel()
    }

    fun start(mode: MazeMode) {
        this.mode = mode
        level = 1
        completed = 0
        buildLevel()
    }

    fun nextLevel() {
        level++
        buildLevel()
    }

    fun guideLevel(): Int {
        return (1f + warmth * 8f).roundToInt().coerceIn(1, 9)
    }

    fun update(seconds: Float, tiltX: Float, tiltY: Float): Float {
        if (won) return 0f

        val frame = min(max(seconds, 0f), MAX_FRAME)
        accumulator += frame

        var impact = 0f

        while (accumulator >= STEP) {
            accumulator -= STEP
            impact = max(impact, step(STEP, tiltX, tiltY))
            if (won) break
        }

        time += frame
        flash = max(0f, flash - frame * 1.2f)
        if (impact > 1f) flash = 1f

        return impact
    }

    private fun currentDistance(): Int {
        if (dist.isEmpty()) return maxDist
        val i = px.toInt().coerceIn(0, size - 1)
        val j = py.toInt().coerceIn(0, size - 1)
        return dist[j][i].coerceAtLeast(0)
    }

    private fun step(dt: Float, tiltX: Float, tiltY: Float): Float {
        vx += tiltX * ACCEL * dt
        vy += tiltY * ACCEL * dt

        val damp = max(0f, 1f - FRICTION * dt)
        vx *= damp
        vy *= damp

        val speed = sqrt(vx * vx + vy * vy)
        if (speed > MAX_SPEED) {
            val scale = MAX_SPEED / speed
            vx *= scale
            vy *= scale
        }

        px += vx * dt
        py += vy * dt

        var impact = 0f

        repeat(2) {
            var k = 0

            while (k < rectCount) {
                val base = k * 4
                impact = max(impact, collide(rects[base], rects[base + 1], rects[base + 2], rects[base + 3]))
                k++
            }
        }

        val dx = px - (exitX + 0.5f)
        val dy = py - (exitY + 0.5f)

        if (dx * dx + dy * dy <= WIN_DIST2) {
            won = true
            completed++
        }

        return impact
    }

    private fun collide(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val cx = px.coerceIn(x0, x1)
        val cy = py.coerceIn(y0, y1)
        val dx = px - cx
        val dy = py - cy
        val d2 = dx * dx + dy * dy

        if (d2 >= BALL_R * BALL_R) return 0f

        val nx: Float
        val ny: Float
        val penetration: Float

        if (d2 > 0.00000001f) {
            val d = sqrt(d2)
            nx = dx / d
            ny = dy / d
            penetration = BALL_R - d
        } else {
            val toLeft = px - x0
            val toRight = x1 - px
            val toTop = py - y0
            val toBottom = y1 - py
            val smallest = min(min(toLeft, toRight), min(toTop, toBottom))

            when (smallest) {
                toLeft -> {
                    nx = -1f
                    ny = 0f
                }

                toRight -> {
                    nx = 1f
                    ny = 0f
                }

                toTop -> {
                    nx = 0f
                    ny = -1f
                }

                else -> {
                    nx = 0f
                    ny = 1f
                }
            }

            penetration = smallest + BALL_R
        }

        px += nx * penetration
        py += ny * penetration

        val vn = vx * nx + vy * ny

        if (vn >= 0f) return 0f

        vx -= (1f + RESTITUTION) * vn * nx
        vy -= (1f + RESTITUTION) * vn * ny

        return -vn
    }

    private fun buildLevel() {
        val n = sizeFor(level)
        size = n

        val h = Array(n + 1) { BooleanArray(n) { true } }
        val v = Array(n) { BooleanArray(n + 1) { true } }
        val seen = Array(n) { BooleanArray(n) }
        val stack = ArrayList<Int>()

        seen[0][0] = true
        stack.add(0)

        while (stack.isNotEmpty()) {
            val cur = stack[stack.lastIndex]
            val ci = cur % n
            val cj = cur / n
            val options = ArrayList<Int>()

            if (cj > 0 && !seen[cj - 1][ci]) options.add(0)
            if (ci < n - 1 && !seen[cj][ci + 1]) options.add(1)
            if (cj < n - 1 && !seen[cj + 1][ci]) options.add(2)
            if (ci > 0 && !seen[cj][ci - 1]) options.add(3)

            if (options.isEmpty()) {
                stack.removeAt(stack.lastIndex)
                continue
            }

            when (options[random.nextInt(options.size)]) {
                0 -> {
                    h[cj][ci] = false
                    seen[cj - 1][ci] = true
                    stack.add((cj - 1) * n + ci)
                }

                1 -> {
                    v[cj][ci + 1] = false
                    seen[cj][ci + 1] = true
                    stack.add(cj * n + ci + 1)
                }

                2 -> {
                    h[cj + 1][ci] = false
                    seen[cj + 1][ci] = true
                    stack.add((cj + 1) * n + ci)
                }

                else -> {
                    v[cj][ci] = false
                    seen[cj][ci - 1] = true
                    stack.add(cj * n + ci - 1)
                }
            }
        }

        hWall = h
        vWall = v

        val fromStart = distances(0, 0)
        var bestI = n - 1
        var bestJ = n - 1
        var bestD = -1

        for (j in 0 until n) {
            for (i in 0 until n) {
                if (fromStart[j][i] > bestD) {
                    bestD = fromStart[j][i]
                    bestI = i
                    bestJ = j
                }
            }
        }

        exitX = bestI
        exitY = bestJ
        dist = distances(exitX, exitY)

        var farthest = 1
        for (row in dist) {
            for (d in row) {
                if (d > farthest) farthest = d
            }
        }
        maxDist = farthest

        buildRects()

        px = 0.5f
        py = 0.5f
        vx = 0f
        vy = 0f
        accumulator = 0f
        time = 0f
        flash = 0f
        won = false
    }

    private fun distances(startI: Int, startJ: Int): Array<IntArray> {
        val n = size
        val d = Array(n) { IntArray(n) { -1 } }
        val queue = ArrayDeque<Int>()

        d[startJ][startI] = 0
        queue.add(startJ * n + startI)

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val ci = cur % n
            val cj = cur / n
            val next = d[cj][ci] + 1

            if (cj > 0 && !hWall[cj][ci] && d[cj - 1][ci] < 0) {
                d[cj - 1][ci] = next
                queue.add((cj - 1) * n + ci)
            }

            if (cj < n - 1 && !hWall[cj + 1][ci] && d[cj + 1][ci] < 0) {
                d[cj + 1][ci] = next
                queue.add((cj + 1) * n + ci)
            }

            if (ci < n - 1 && !vWall[cj][ci + 1] && d[cj][ci + 1] < 0) {
                d[cj][ci + 1] = next
                queue.add(cj * n + ci + 1)
            }

            if (ci > 0 && !vWall[cj][ci] && d[cj][ci - 1] < 0) {
                d[cj][ci - 1] = next
                queue.add(cj * n + ci - 1)
            }
        }

        return d
    }

    private fun buildRects() {
        val n = size
        val t = WALL / 2f
        val list = ArrayList<Float>()

        for (j in 0..n) {
            for (i in 0 until n) {
                if (hWall[j][i]) {
                    list.add(i - t)
                    list.add(j - t)
                    list.add(i + 1 + t)
                    list.add(j + t)
                }
            }
        }

        for (j in 0 until n) {
            for (i in 0..n) {
                if (vWall[j][i]) {
                    list.add(i - t)
                    list.add(j - t)
                    list.add(i + t)
                    list.add(j + 1 + t)
                }
            }
        }

        rects = list.toFloatArray()
        rectCount = rects.size / 4
    }
}