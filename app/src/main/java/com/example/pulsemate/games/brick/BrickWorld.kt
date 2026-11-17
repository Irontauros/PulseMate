package com.example.pulsemate.games.brick

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class PowerKind(val emoji: String) {
    WIDE("🍄"),
    SLOW("🐢"),
    MULTI("🎈"),
    LIFE("💖")
}

class Brick(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val row: Int,
    val max: Int
) {
    var left: Int = max
}

class BrickBall(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float
)

class FallingPower(
    val kind: PowerKind,
    var x: Float,
    var y: Float
)

enum class BrickEventType { HIT, BREAK, ROW, POWER, LIFE_LOST, LEVEL_CLEAR, GAME_OVER }

class BrickEvent(
    val type: BrickEventType,
    val value: Int = 0
)

class BrickWorld(private val random: Random = Random.Default) {

    companion object {
        const val WIDTH = 100f
        const val HEIGHT = 140f
        const val COLS = 8
        const val PADDLE_Y = 129f
        const val PADDLE_H = 3.6f
        const val BASE_PADDLE = 18f
        const val WIDE_PADDLE = 28f
        const val BALL_R = 2.3f
        const val BRICK_TOP = 12f
        const val BRICK_H = 5f
        const val BRICK_GAP = 1f
        const val SIDE = 4f

        private const val STEP = 1f / 240f
        private const val POWER_FALL = 38f
        private const val WIDE_SECONDS = 10f
        private const val SLOW_SECONDS = 8f
        private const val MAX_BALLS = 5

        fun baseSpeed(level: Int): Float = min(62f + (level - 1) * 7f, 115f)
    }

    val bricks = ArrayList<Brick>()
    val balls = ArrayList<BrickBall>()
    val powers = ArrayList<FallingPower>()

    var paddleX: Float = WIDTH / 2f
        private set

    var paddleW: Float = BASE_PADDLE
        private set

    var level: Int = 1
        private set

    var lives: Int = 3
        private set

    var score: Int = 0
        private set

    var waiting: Boolean = true
        private set

    var gameOver: Boolean = false
        private set

    var cleared: Boolean = false
        private set

    var ballEmojiIndex: Int = 0
        private set

    var wideLeft: Float = 0f
        private set

    var slowLeft: Float = 0f
        private set

    private var rowLeft = IntArray(0)
    private var remaining = 0
    private var accumulator = 0f

    init {
        reset()
    }

    fun reset() {
        level = 1
        lives = 3
        score = 0
        gameOver = false
        paddleX = WIDTH / 2f
        buildLevel()
    }

    fun nextLevel() {
        level++
        buildLevel()
    }

    fun launch() {
        if (!waiting || gameOver || cleared) return

        val ball = balls.firstOrNull() ?: return
        val angle = (random.nextFloat() - 0.5f) * 0.8f
        val speed = targetSpeed()

        ball.vx = speed * sin(angle)
        ball.vy = -speed * cos(angle)
        waiting = false
    }

    fun movePaddle(dx: Float) {
        if (gameOver || cleared) return

        paddleX = (paddleX + dx).coerceIn(paddleW / 2f, WIDTH - paddleW / 2f)

        if (waiting) {
            balls.firstOrNull()?.x = paddleX
        }
    }

    fun update(seconds: Float): List<BrickEvent> {
        val events = ArrayList<BrickEvent>()

        if (gameOver || cleared) return events

        val frame = seconds.coerceIn(0f, 0.05f)

        if (wideLeft > 0f) {
            wideLeft -= frame

            if (wideLeft <= 0f) {
                wideLeft = 0f
                paddleW = BASE_PADDLE
                paddleX = paddleX.coerceIn(paddleW / 2f, WIDTH - paddleW / 2f)
                if (waiting) balls.firstOrNull()?.x = paddleX
            }
        }

        if (slowLeft > 0f) {
            slowLeft -= frame

            if (slowLeft <= 0f) {
                slowLeft = 0f
                normalizeSpeeds()
            }
        }

        if (waiting) return events

        accumulator += frame

        while (accumulator >= STEP) {
            accumulator -= STEP
            stepPhysics(STEP, events)
            if (gameOver || cleared || waiting) break
        }

        return events
    }

    private fun targetSpeed(): Float {
        return baseSpeed(level) * if (slowLeft > 0f) 0.72f else 1f
    }

    private fun normalizeSpeeds() {
        val target = targetSpeed()

        for (b in balls) {
            val s = sqrt(b.vx * b.vx + b.vy * b.vy)

            if (s > 0.001f) {
                b.vx = b.vx / s * target
                b.vy = b.vy / s * target
            }
        }
    }

    private fun buildLevel() {
        bricks.clear()
        powers.clear()
        balls.clear()
        cleared = false
        gameOver = false
        wideLeft = 0f
        slowLeft = 0f
        paddleW = BASE_PADDLE
        accumulator = 0f

        val rows = min(4 + level, 9)
        val bw = (WIDTH - 2f * SIDE - (COLS - 1) * BRICK_GAP) / COLS
        val p4 = if (level >= 4) min(0.03f * (level - 3), 0.14f) else 0f
        val p3 = min(0.10f + 0.04f * level, 0.32f)
        val p2 = min(0.22f + 0.03f * level, 0.34f)

        rowLeft = IntArray(rows)
        remaining = 0

        for (r in 0 until rows) {
            for (c in 0 until COLS) {
                if (r > 0 && random.nextFloat() < 0.12f) continue

                val roll = random.nextFloat()
                val max = when {
                    roll < p4 -> 4
                    roll < p4 + p3 -> 3
                    roll < p4 + p3 + p2 -> 2
                    else -> 1
                }

                bricks.add(
                    Brick(
                        SIDE + c * (bw + BRICK_GAP),
                        BRICK_TOP + r * (BRICK_H + BRICK_GAP),
                        bw,
                        BRICK_H,
                        r,
                        max
                    )
                )
                rowLeft[r]++
                remaining++
            }
        }

        ballEmojiIndex = random.nextInt(BallEmojis.size)
        resetBall()
    }

    private fun resetBall() {
        balls.clear()
        balls.add(BrickBall(paddleX, PADDLE_Y - PADDLE_H / 2f - BALL_R, 0f, 0f))
        waiting = true
    }

    private fun stepPhysics(dt: Float, events: MutableList<BrickEvent>) {
        var i = 0

        while (i < balls.size) {
            val b = balls[i]

            b.x += b.vx * dt
            b.y += b.vy * dt

            if (b.x - BALL_R < 0f) {
                b.x = BALL_R
                b.vx = abs(b.vx)
            } else if (b.x + BALL_R > WIDTH) {
                b.x = WIDTH - BALL_R
                b.vx = -abs(b.vx)
            }

            if (b.y - BALL_R < 0f) {
                b.y = BALL_R
                b.vy = abs(b.vy)
            }

            if (b.vy > 0f) {
                val top = PADDLE_Y - PADDLE_H / 2f
                val reach = paddleW / 2f + BALL_R * 0.6f

                if (b.y + BALL_R >= top &&
                    b.y - BALL_R <= PADDLE_Y + PADDLE_H &&
                    abs(b.x - paddleX) <= reach
                ) {
                    b.y = top - BALL_R

                    val offset = ((b.x - paddleX) / reach).coerceIn(-1f, 1f)
                    val angle = offset * 1.05f
                    val speed = targetSpeed()

                    b.vx = speed * sin(angle)
                    b.vy = -speed * cos(angle)
                }
            }

            hitBricks(b, events)
            keepAngle(b)

            if (cleared) return

            if (b.y - BALL_R > HEIGHT) {
                balls.removeAt(i)
                continue
            }

            i++
        }

        var k = 0

        while (k < powers.size) {
            val p = powers[k]

            p.y += POWER_FALL * dt

            val caught = p.y + 3f >= PADDLE_Y - PADDLE_H / 2f &&
                    p.y - 3f <= PADDLE_Y + PADDLE_H / 2f &&
                    abs(p.x - paddleX) <= paddleW / 2f + 3f

            if (caught) {
                applyPower(p.kind)
                events.add(BrickEvent(BrickEventType.POWER, p.kind.ordinal))
                powers.removeAt(k)
                continue
            }

            if (p.y > HEIGHT + 4f) {
                powers.removeAt(k)
                continue
            }

            k++
        }

        if (balls.isEmpty()) {
            lives--
            powers.clear()
            wideLeft = 0f
            slowLeft = 0f
            paddleW = BASE_PADDLE
            paddleX = paddleX.coerceIn(paddleW / 2f, WIDTH - paddleW / 2f)

            if (lives <= 0) {
                gameOver = true
                events.add(BrickEvent(BrickEventType.GAME_OVER))
            } else {
                resetBall()
                events.add(BrickEvent(BrickEventType.LIFE_LOST))
            }
        }
    }

    private fun keepAngle(b: BrickBall) {
        val s = sqrt(b.vx * b.vx + b.vy * b.vy)

        if (s < 0.001f) return

        val minVertical = 0.22f * s

        if (abs(b.vy) < minVertical) {
            val vy = if (b.vy < 0f) -minVertical else minVertical
            val vx = sqrt(s * s - vy * vy)

            b.vy = vy
            b.vx = if (b.vx < 0f) -vx else vx
        }
    }

    private fun hitBricks(b: BrickBall, events: MutableList<BrickEvent>) {
        for (brick in bricks) {
            if (brick.left <= 0) continue

            val cx = b.x.coerceIn(brick.x, brick.x + brick.w)
            val cy = b.y.coerceIn(brick.y, brick.y + brick.h)
            val dx = b.x - cx
            val dy = b.y - cy
            val d2 = dx * dx + dy * dy

            if (d2 >= BALL_R * BALL_R) continue

            var nx: Float
            var ny: Float
            val push: Float

            if (d2 > 0.000001f) {
                val d = sqrt(d2)

                nx = dx / d
                ny = dy / d
                push = BALL_R - d
            } else {
                val toLeft = b.x - brick.x
                val toRight = brick.x + brick.w - b.x
                val toTop = b.y - brick.y
                val toBottom = brick.y + brick.h - b.y
                val smallest = min(min(toLeft, toRight), min(toTop, toBottom))

                nx = 0f
                ny = 0f

                when (smallest) {
                    toLeft -> nx = -1f
                    toRight -> nx = 1f
                    toTop -> ny = -1f
                    else -> ny = 1f
                }

                push = smallest + BALL_R
            }

            b.x += nx * push
            b.y += ny * push

            val vn = b.vx * nx + b.vy * ny

            if (vn < 0f) {
                b.vx -= 2f * vn * nx
                b.vy -= 2f * vn * ny
            }

            damage(brick, events)
            return
        }
    }

    private fun damage(brick: Brick, events: MutableList<BrickEvent>) {
        brick.left--
        score += 10

        if (brick.left > 0) {
            events.add(BrickEvent(BrickEventType.HIT, brick.max))
            return
        }

        remaining--
        rowLeft[brick.row]--
        score += 10 * brick.max
        events.add(BrickEvent(BrickEventType.BREAK, brick.max))

        if (random.nextFloat() < 0.13f) {
            val roll = random.nextFloat()
            val kind = when {
                roll < 0.35f -> PowerKind.WIDE
                roll < 0.6f -> PowerKind.SLOW
                roll < 0.9f -> PowerKind.MULTI
                else -> PowerKind.LIFE
            }

            powers.add(FallingPower(kind, brick.x + brick.w / 2f, brick.y + brick.h / 2f))
        }

        if (rowLeft[brick.row] == 0) {
            score += 50
            events.add(BrickEvent(BrickEventType.ROW))
        }

        if (remaining <= 0) {
            cleared = true
            events.add(BrickEvent(BrickEventType.LEVEL_CLEAR))
        }
    }

    private fun applyPower(kind: PowerKind) {
        score += 25

        when (kind) {
            PowerKind.WIDE -> {
                paddleW = WIDE_PADDLE
                wideLeft = WIDE_SECONDS
                paddleX = paddleX.coerceIn(paddleW / 2f, WIDTH - paddleW / 2f)
            }

            PowerKind.SLOW -> {
                slowLeft = SLOW_SECONDS
                normalizeSpeeds()
            }

            PowerKind.MULTI -> {
                val source = ArrayList(balls)

                for (b in source) {
                    for (turn in listOf(-0.45f, 0.45f)) {
                        if (balls.size >= MAX_BALLS) break

                        val c = cos(turn)
                        val s = sin(turn)

                        balls.add(BrickBall(b.x, b.y, b.vx * c - b.vy * s, b.vx * s + b.vy * c))
                    }
                }
            }

            PowerKind.LIFE -> {
                lives = min(lives + 1, 5)
            }
        }
    }
}