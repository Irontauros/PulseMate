package com.example.pulsemate.games.merge

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class MergePiece(
    val id: Int,
    val stage: Int,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f
) {
    var landed: Boolean = false
    var merged: Boolean = false
}

class MergeEvent(
    val stage: Int,
    val x: Float,
    val y: Float,
    val vanish: Boolean
)

class MergeWorld(private val random: Random = Random.Default) {

    companion object {
        const val WIDTH = 100f
        const val HEIGHT = 150f
        const val LINE_Y = 26f
        const val STAGES = 11
        const val MAX_STAGE = STAGES - 1
        const val GAME_OVER_SECONDS = 2f
        const val DROP_COOLDOWN = 0.45f

        private const val STEP = 1f / 120f
        private const val MAX_FRAME = 0.05f
        private const val ITERATIONS = 6
        private const val GRAVITY = 260f
        private const val AIR_DRAG = 0.15f
        private const val FLOOR_DRAG = 1.5f
        private const val WALL_BOUNCE = 0.2f
        private const val PIECE_BOUNCE = 0.15f
        private const val BIG_PIECE_BOUNCE = 0.3f
        private const val BOUNCE_MIN_SPEED = 12f
        private const val FRICTION = 0.02f
        private const val SLOP = 0.02f
        private const val CORRECTION = 0.8f
        private const val MERGE_SLOP = 0.3f
        private const val REST_SPEED = 14f

        private val radii = floatArrayOf(
            4.5f, 6f, 7.5f, 9.5f, 11.5f, 14f, 16.5f, 19f, 22f, 25f, 28f
        )

        fun radius(stage: Int): Float = radii[stage.coerceIn(0, MAX_STAGE)]

        fun hapticLevelFor(event: MergeEvent): Int {
            return if (event.vanish) 9 else event.stage.coerceIn(1, 9)
        }

        fun pointsFor(stage: Int): Int = (stage + 1) * (stage + 1)
    }

    private val pieceList = ArrayList<MergePiece>()
    private var nextId = 1
    private var accumulator = 0f
    private var cooldown = 0f
    private var lineTimer = 0f

    val pieces: List<MergePiece> get() = pieceList

    var heldStage: Int = rollStage()
        private set

    var score: Int = 0
        private set

    var gameOver: Boolean = false
        private set

    val lineDanger: Float
        get() = (lineTimer / GAME_OVER_SECONDS).coerceIn(0f, 1f)

    val canDrop: Boolean
        get() = !gameOver && cooldown <= 0f

    fun clampX(x: Float, stage: Int): Float {
        val r = radius(stage)
        return x.coerceIn(r, WIDTH - r)
    }

    fun spawnY(stage: Int): Float = radius(stage) + 2f

    fun drop(x: Float): Boolean {
        if (!canDrop) return false

        val stage = heldStage
        pieceList.add(MergePiece(nextId++, stage, clampX(x, stage), spawnY(stage)))
        heldStage = rollStage()
        cooldown = DROP_COOLDOWN
        return true
    }

    fun reset() {
        pieceList.clear()
        nextId = 1
        accumulator = 0f
        cooldown = 0f
        lineTimer = 0f
        score = 0
        gameOver = false
        heldStage = rollStage()
    }

    fun update(seconds: Float): List<MergeEvent> {
        if (gameOver) return emptyList()

        val events = ArrayList<MergeEvent>()
        val frame = min(max(seconds, 0f), MAX_FRAME)

        cooldown = max(0f, cooldown - frame)
        accumulator += frame

        while (accumulator >= STEP) {
            accumulator -= STEP
            simulate(STEP, events)
            updateLine(STEP)
            if (gameOver) break
        }

        return events
    }

    private fun rollStage(): Int {
        val roll = random.nextInt(100)
        return when {
            roll < 35 -> 0
            roll < 70 -> 1
            roll < 92 -> 2
            else -> 3
        }
    }

    private fun simulate(dt: Float, events: MutableList<MergeEvent>) {
        val airFactor = 1f - AIR_DRAG * dt

        for (p in pieceList) {
            p.vy += GRAVITY * dt
            p.vx *= airFactor
            p.vy *= airFactor
            p.x += p.vx * dt
            p.y += p.vy * dt
        }

        val merges = ArrayList<Pair<MergePiece, MergePiece>>()

        repeat(ITERATIONS) {
            for (p in pieceList) {
                solveWalls(p)
            }

            for (i in 0 until pieceList.size) {
                for (j in i + 1 until pieceList.size) {
                    solvePair(pieceList[i], pieceList[j], merges)
                }
            }
        }

        for (p in pieceList) {
            solveWalls(p)
        }

        for (p in pieceList) {
            if (p.y >= HEIGHT - radius(p.stage) - 0.01f) {
                p.vx *= (1f - FLOOR_DRAG * dt)
            }
        }

        for ((a, b) in merges) {
            pieceList.remove(a)
            pieceList.remove(b)

            val mx = (a.x + b.x) / 2f
            val my = (a.y + b.y) / 2f

            if (a.stage >= MAX_STAGE) {
                score += pointsFor(MAX_STAGE) * 2
                events.add(MergeEvent(MAX_STAGE, mx, my, true))
            } else {
                val grown = MergePiece(
                    nextId++,
                    a.stage + 1,
                    mx,
                    my,
                    (a.vx + b.vx) * 0.25f,
                    (a.vy + b.vy) * 0.25f
                )
                val grownRadius = radius(grown.stage)
                grown.x = grown.x.coerceIn(grownRadius, WIDTH - grownRadius)
                grown.y = min(grown.y, HEIGHT - grownRadius)
                grown.landed = true
                pieceList.add(grown)
                score += pointsFor(grown.stage)
                events.add(MergeEvent(grown.stage, mx, my, false))
            }
        }
    }

    private fun solveWalls(p: MergePiece) {
        val r = radius(p.stage)

        if (p.x < r) {
            p.x = r
            if (p.vx < 0f) p.vx = -p.vx * WALL_BOUNCE
        } else if (p.x > WIDTH - r) {
            p.x = WIDTH - r
            if (p.vx > 0f) p.vx = -p.vx * WALL_BOUNCE
        }

        if (p.y > HEIGHT - r) {
            p.y = HEIGHT - r
            if (p.vy > 0f) {
                p.vy = if (p.vy > BOUNCE_MIN_SPEED) -p.vy * WALL_BOUNCE else 0f
            }
            p.landed = true
        }
    }

    private fun solvePair(
        a: MergePiece,
        b: MergePiece,
        merges: MutableList<Pair<MergePiece, MergePiece>>
    ) {
        if (a.merged || b.merged) return

        val ra = radius(a.stage)
        val rb = radius(b.stage)
        val dx = b.x - a.x
        val dy = b.y - a.y
        val minDist = ra + rb
        val dist2 = dx * dx + dy * dy

        if (a.stage == b.stage) {
            val reach = minDist + MERGE_SLOP
            if (dist2 <= reach * reach) {
                a.merged = true
                b.merged = true
                merges.add(Pair(a, b))
            }
            return
        }

        if (dist2 >= minDist * minDist) return

        var dist = sqrt(dist2)
        val nx: Float
        val ny: Float

        if (dist < 0.0001f) {
            nx = 0f
            ny = -1f
            dist = 0f
        } else {
            nx = dx / dist
            ny = dy / dist
        }

        val ia = 1f / (ra * ra)
        val ib = 1f / (rb * rb)
        val invSum = ia + ib

        val overlap = minDist - dist
        val push = max(overlap - SLOP, 0f) * CORRECTION / invSum

        a.x -= nx * push * ia
        a.y -= ny * push * ia
        b.x += nx * push * ib
        b.y += ny * push * ib

        val rvx = b.vx - a.vx
        val rvy = b.vy - a.vy
        val vn = rvx * nx + rvy * ny

        if (vn < 0f) {
            val bigger = max(a.stage, b.stage).toFloat() / MAX_STAGE
            val bounce = if (-vn > BOUNCE_MIN_SPEED) {
                PIECE_BOUNCE + BIG_PIECE_BOUNCE * bigger * bigger
            } else {
                0f
            }
            val jn = -(1f + bounce) * vn / invSum

            a.vx -= jn * ia * nx
            a.vy -= jn * ia * ny
            b.vx += jn * ib * nx
            b.vy += jn * ib * ny

            val tx = -ny
            val ty = nx
            val vt = rvx * tx + rvy * ty
            val limit = FRICTION * jn
            val jt = (-vt / invSum).coerceIn(-limit, limit)

            a.vx -= jt * ia * tx
            a.vy -= jt * ia * ty
            b.vx += jt * ib * tx
            b.vy += jt * ib * ty
        }

        a.landed = true
        b.landed = true
    }

    private fun updateLine(dt: Float) {
        var over = false

        for (p in pieceList) {
            if (!p.landed) continue
            if (p.y - radius(p.stage) >= LINE_Y) continue
            if (p.vx * p.vx + p.vy * p.vy > REST_SPEED * REST_SPEED) continue
            over = true
            break
        }

        if (over) {
            lineTimer += dt
            if (lineTimer >= GAME_OVER_SECONDS) {
                gameOver = true
            }
        } else {
            lineTimer = 0f
        }
    }
}