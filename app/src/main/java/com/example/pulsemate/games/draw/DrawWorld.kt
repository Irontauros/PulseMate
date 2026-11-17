package com.example.pulsemate.games.draw

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class DrawEventType { IMPACT, HAZARD, FELL, WON }

class DrawEvent(val type: DrawEventType, val value: Float = 0f)

class DrawStroke {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    var ink = 0f
}

class DrawLevel(
    val ink: Float,
    val ballX: Float,
    val ballY: Float,
    val goalX1: Float,
    val goalX2: Float,
    val goalY: Float,
    val walls: List<FloatArray>,
    val hazards: List<FloatArray>
)

class DrawWorld {
    companion object {
        const val W = 100f
        const val H = 160f
        const val R = 3.2f
        const val HALF = 1.2f
        const val GRAVITY = 140f
        const val HOLD = 3f
        const val SUBSTEPS = 5

        val LEVELS = listOf(
            DrawLevel(
                220f, 25f, 18f, 62f, 82f, 140f,
                emptyList(),
                emptyList()
            ),
            DrawLevel(
                220f, 50f, 18f, 12f, 32f, 146f,
                listOf(floatArrayOf(30f, 60f, 80f, 72f)),
                emptyList()
            ),
            DrawLevel(
                200f, 80f, 18f, 40f, 60f, 148f,
                listOf(floatArrayOf(60f, 48f, 100f, 58f)),
                emptyList()
            ),
            DrawLevel(
                180f, 20f, 18f, 70f, 90f, 150f,
                listOf(floatArrayOf(0f, 55f, 60f, 65f), floatArrayOf(40f, 95f, 100f, 85f)),
                emptyList()
            ),
            DrawLevel(
                170f, 50f, 18f, 10f, 28f, 150f,
                listOf(floatArrayOf(25f, 70f, 75f, 70f)),
                listOf(floatArrayOf(0f, 110f, 45f, 110f))
            ),
            DrawLevel(
                150f, 85f, 18f, 40f, 60f, 150f,
                listOf(floatArrayOf(100f, 48f, 75f, 58f)),
                listOf(floatArrayOf(20f, 105f, 45f, 105f))
            ),
            DrawLevel(
                140f, 50f, 18f, 80f, 96f, 150f,
                listOf(floatArrayOf(0f, 40f, 45f, 50f), floatArrayOf(100f, 70f, 55f, 80f)),
                listOf(floatArrayOf(60f, 110f, 100f, 110f))
            ),
            DrawLevel(
                130f, 15f, 18f, 40f, 58f, 150f,
                listOf(floatArrayOf(0f, 40f, 40f, 50f), floatArrayOf(60f, 65f, 20f, 75f)),
                listOf(floatArrayOf(60f, 115f, 100f, 115f), floatArrayOf(0f, 115f, 25f, 115f))
            )
        )
    }

    var levelIndex = 0
    var level: DrawLevel = LEVELS[0]
    val strokes = ArrayList<DrawStroke>()
    var inkLeft = 0f
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var rot = 0f
    var released = false
    var hold = 0f
    var touching = false
    var won = false

    private var lost = 0f
    private var solids: List<FloatArray> = emptyList()
    private var impact = 0f

    fun load(index: Int) {
        levelIndex = index.coerceIn(0, LEVELS.size - 1)
        level = LEVELS[levelIndex]
        strokes.clear()
        inkLeft = level.ink

        val list = ArrayList<FloatArray>(level.walls)
        list.add(floatArrayOf(level.goalX1, level.goalY, level.goalX2, level.goalY))
        list.add(floatArrayOf(level.goalX1, level.goalY - 5f, level.goalX1, level.goalY))
        list.add(floatArrayOf(level.goalX2, level.goalY - 5f, level.goalX2, level.goalY))
        solids = list

        resetBall()
    }

    fun resetBall() {
        x = level.ballX
        y = level.ballY
        vx = 0f
        vy = 0f
        rot = 0f
        released = false
        hold = 0f
        lost = 0f
        touching = false
        won = false
    }

    fun release() {
        if (!won) released = true
    }

    fun clearStrokes() {
        strokes.clear()
        inkLeft = level.ink
        resetBall()
    }

    fun undo() {
        if (strokes.isEmpty()) return

        val last = strokes.removeAt(strokes.size - 1)
        inkLeft = min(level.ink, inkLeft + last.ink)
    }

    fun beginStroke(px: Float, py: Float): DrawStroke? {
        if (inkLeft <= 1f) return null

        val s = DrawStroke()
        s.xs.add(px)
        s.ys.add(py)
        strokes.add(s)

        return s
    }

    fun extendStroke(s: DrawStroke, px: Float, py: Float) {
        val lx = s.xs[s.xs.size - 1]
        val ly = s.ys[s.ys.size - 1]
        val d = hypot(px - lx, py - ly)

        if (d < 1.6f || d > inkLeft) return

        s.xs.add(px)
        s.ys.add(py)
        s.ink += d
        inkLeft -= d
    }

    fun endStroke(s: DrawStroke) {
        if (s.xs.size < 2) strokes.remove(s)
    }

    fun progress(): Float = (hold / HOLD).coerceIn(0f, 1f)

    fun stars(): Int {
        val f = inkLeft / level.ink

        return when {
            f >= 0.45f -> 3
            f >= 0.2f -> 2
            else -> 1
        }
    }

    private fun collide(x1: Float, y1: Float, x2: Float, y2: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        var t = if (len2 == 0f) 0f else ((x - x1) * dx + (y - y1) * dy) / len2

        t = t.coerceIn(0f, 1f)

        val px = x1 + dx * t
        val py = y1 + dy * t
        val ox = x - px
        val oy = y - py
        val d2 = ox * ox + oy * oy
        val minD = R + HALF

        if (d2 >= minD * minD) return

        val d = sqrt(d2)
        val nx: Float
        val ny: Float

        if (d < 0.0001f) {
            val l = sqrt(len2).coerceAtLeast(0.0001f)
            nx = -dy / l
            ny = dx / l
        } else {
            nx = ox / d
            ny = oy / d
        }

        x += nx * (minD - d)
        y += ny * (minD - d)

        val vn = vx * nx + vy * ny

        if (vn < 0f) {
            val speed = -vn
            val bounce = if (speed > 14f) 0.3f else 0f

            vx -= (1f + bounce) * vn * nx
            vy -= (1f + bounce) * vn * ny

            vx *= 0.985f
            vy *= 0.985f

            impact = max(impact, speed)
        }
    }

    private fun distToSegment(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        var t = if (len2 == 0f) 0f else ((x - x1) * dx + (y - y1) * dy) / len2

        t = t.coerceIn(0f, 1f)

        return hypot(x - (x1 + dx * t), y - (y1 + dy * t))
    }

    fun update(seconds: Float): List<DrawEvent> {
        val events = ArrayList<DrawEvent>()

        if (!released || won) return events

        val dt = seconds / SUBSTEPS
        impact = 0f

        repeat(SUBSTEPS) {
            vy += GRAVITY * dt
            x += vx * dt
            y += vy * dt
            rot += vx * dt / R

            if (x < R) {
                x = R
                if (vx < 0f) vx = -vx * 0.3f
            }

            if (x > W - R) {
                x = W - R
                if (vx > 0f) vx = -vx * 0.3f
            }

            for (s in solids) collide(s[0], s[1], s[2], s[3])

            for (st in strokes) {
                for (i in 0 until st.xs.size - 1) {
                    collide(st.xs[i], st.ys[i], st.xs[i + 1], st.ys[i + 1])
                }
            }
        }

        if (impact > 28f) events.add(DrawEvent(DrawEventType.IMPACT, impact))

        for (h in level.hazards) {
            if (distToSegment(h[0], h[1], h[2], h[3]) <= R + HALF) {
                events.add(DrawEvent(DrawEventType.HAZARD))
                resetBall()
                return events
            }
        }

        if (y > H + 12f) {
            events.add(DrawEvent(DrawEventType.FELL))
            resetBall()
            return events
        }

        touching = distToSegment(level.goalX1, level.goalY, level.goalX2, level.goalY) <= R + HALF + 0.9f

        if (touching) {
            hold += seconds
            lost = 0f
        } else {
            lost += seconds
            if (lost > 0.25f) hold = 0f
        }

        if (hold >= HOLD) {
            hold = HOLD
            won = true
            released = false
            events.add(DrawEvent(DrawEventType.WON))
        }

        return events
    }
}