package com.example.pulsemate.games.fruit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class Flyer(
    val kind: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var angle: Float,
    val spin: Float
)

class Half(
    val kind: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var angle: Float,
    val spin: Float,
    val side: Int
)

class Splash(
    val x: Float,
    val y: Float,
    val kind: Int,
    var age: Float = 0f
)

class Cut(
    val x: Float,
    val y: Float,
    val dx: Float,
    val dy: Float,
    var age: Float = 0f
)

class TrailPoint(
    val x: Float,
    val y: Float,
    val time: Float,
    val start: Boolean
)

enum class FruitEventType { SLICE, COMBO, BOMB, BONUS, MISS, GAME_OVER }

class FruitEvent(
    val type: FruitEventType,
    val value: Int = 0
)

class FruitWorld(private val random: Random = Random.Default) {

    companion object {
        const val RADIUS = 7.5f
        const val CUT_SECONDS = 7f
        const val GRAVITY = 120f
        const val START_LIVES = 3
        private const val COMBO_WINDOW = 0.45f
        private const val TRAIL_SECONDS = 0.25f
        private const val SPLASH_SECONDS = 0.55f
    }

    val flyers = ArrayList<Flyer>()
    val halves = ArrayList<Half>()
    val splashes = ArrayList<Splash>()
    val trail = ArrayList<TrailPoint>()
    val cuts = ArrayList<Cut>()

    var width: Float = 100f
        private set

    var height: Float = 150f
        private set

    var score: Int = 0
        private set

    var lives: Int = START_LIVES
        private set

    var gameOver: Boolean = false
        private set

    var time: Float = 0f
        private set

    var flash: Float = 0f
        private set

    private var spawnTimer = 0.6f
    private var swipeCount = 0
    private var lastSlice = -10f
    private var trailStart = true
    private val pending = ArrayList<FruitEvent>()

    init {
        reset()
    }

    fun reset() {
        flyers.clear()
        halves.clear()
        splashes.clear()
        trail.clear()
        cuts.clear()
        pending.clear()
        score = 0
        lives = START_LIVES
        gameOver = false
        time = 0f
        flash = 0f
        spawnTimer = 0.6f
        swipeCount = 0
        lastSlice = -10f
        trailStart = true
    }

    fun resize(pixelWidth: Float, pixelHeight: Float) {
        if (pixelWidth <= 0f || pixelHeight <= 0f) return

        if (pixelWidth <= pixelHeight) {
            width = 100f
            height = 100f * pixelHeight / pixelWidth
        } else {
            height = 100f
            width = 100f * pixelWidth / pixelHeight
        }
    }

    fun beginSwipe() {
        trailStart = true
    }

    fun addTrail(x: Float, y: Float) {
        trail.add(TrailPoint(x, y, time, trailStart))
        trailStart = false
    }

    fun slice(x1: Float, y1: Float, x2: Float, y2: Float) {
        if (gameOver) return

        var i = 0

        while (i < flyers.size) {
            val f = flyers[i]

            if (segmentDistance(f.x, f.y, x1, y1, x2, y2) <= RADIUS) {
                flyers.removeAt(i)
                onSliced(f, x2 - x1, y2 - y1)
                if (gameOver) return
                continue
            }

            i++
        }
    }

    fun update(seconds: Float): List<FruitEvent> {
        val dt = seconds.coerceIn(0f, 0.05f)

        if (!gameOver) {
            time += dt
            spawnTimer -= dt

            if (spawnTimer <= 0f) {
                spawnWave()
                spawnTimer = max(0.7f, 1.5f - time * 0.01f) + random.nextFloat() * 0.4f
            }

            var i = 0

            while (i < flyers.size) {
                val f = flyers[i]

                f.vy += GRAVITY * dt
                f.x += f.vx * dt
                f.y += f.vy * dt
                f.angle += f.spin * dt

                if (f.y > height + RADIUS + 4f && f.vy > 0f) {
                    flyers.removeAt(i)

                    if (f.kind >= 0) {
                        lives--
                        pending.add(FruitEvent(FruitEventType.MISS))

                        if (lives <= 0) {
                            gameOver = true
                            pending.add(FruitEvent(FruitEventType.GAME_OVER))
                            break
                        }
                    }

                    continue
                }

                i++
            }
        }

        var h = 0

        while (h < halves.size) {
            val half = halves[h]

            half.vy += GRAVITY * dt
            half.x += half.vx * dt
            half.y += half.vy * dt
            half.angle += half.spin * dt

            if (half.y > height + RADIUS * 2f) {
                halves.removeAt(h)
                continue
            }

            h++
        }

        var s = 0

        while (s < splashes.size) {
            splashes[s].age += dt

            if (splashes[s].age > SPLASH_SECONDS) {
                splashes.removeAt(s)
                continue
            }

            s++
        }

        var c = 0

        while (c < cuts.size) {
            cuts[c].age += dt

            if (cuts[c].age > CUT_SECONDS) {
                cuts.removeAt(c)
                continue
            }

            c++
        }

        while (trail.isNotEmpty() && time - trail[0].time > TRAIL_SECONDS) {
            trail.removeAt(0)
        }

        flash = max(0f, flash - dt * 2f)

        val out = ArrayList(pending)
        pending.clear()
        return out
    }

    private fun spawnWave() {
        val extra = min(3, 1 + (time / 20f).toInt())
        val count = 1 + random.nextInt(extra + 1)
        val bombChance = min(0.04f + time * 0.003f, 0.2f)

        repeat(count) {
            val roll = random.nextFloat()
            val kind = when {
                roll < bombChance -> KIND_BOMB
                roll < bombChance + 0.04f -> KIND_BONUS
                else -> random.nextInt(FruitDefs.size)
            }

            val x = 14f + random.nextFloat() * (width - 28f)
            val apex = height * (0.62f + random.nextFloat() * 0.26f)
            val vy = -sqrt(2f * GRAVITY * apex)
            val vx = (width / 2f - x) * 0.18f + (random.nextFloat() - 0.5f) * 16f
            val spin = (random.nextFloat() - 0.5f) * 240f

            flyers.add(Flyer(kind, x, height + RADIUS, vx, vy, random.nextFloat() * 360f, spin))
        }
    }

    private fun bump() {
        if (time - lastSlice > COMBO_WINDOW) swipeCount = 0

        swipeCount++
        lastSlice = time
    }

    private fun onSliced(f: Flyer, dx: Float, dy: Float) {
        splashes.add(Splash(f.x, f.y, f.kind))

        when (f.kind) {
            KIND_BOMB -> {
                lives--
                flash = 1f
                pending.add(FruitEvent(FruitEventType.BOMB))

                if (lives <= 0) {
                    gameOver = true
                    pending.add(FruitEvent(FruitEventType.GAME_OVER))
                }
            }

            KIND_BONUS -> {
                score += 50
                bump()
                pending.add(FruitEvent(FruitEventType.BONUS))
            }

            else -> {
                score += 10
                bump()

                val len = sqrt(dx * dx + dy * dy)

                if (len > 0.001f) {
                    cuts.add(Cut(f.x, f.y, dx / len, dy / len))
                } else {
                    cuts.add(Cut(f.x, f.y, 0.94f, -0.34f))
                }

                for (side in listOf(-1, 1)) {
                    halves.add(
                        Half(
                            f.kind,
                            f.x,
                            f.y,
                            f.vx * 0.5f + side * (14f + random.nextFloat() * 10f),
                            f.vy * 0.3f - 12f,
                            f.angle,
                            f.spin + side * 90f,
                            side
                        )
                    )
                }

                pending.add(FruitEvent(FruitEventType.SLICE, f.kind))

                if (swipeCount >= 3) {
                    score += 5 * swipeCount
                    pending.add(FruitEvent(FruitEventType.COMBO, swipeCount))
                }
            }
        }
    }

    private fun segmentDistance(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy

        val t = if (len2 < 0.0001f) 0f else (((px - x1) * dx + (py - y1) * dy) / len2).coerceIn(0f, 1f)
        val cx = x1 + dx * t
        val cy = y1 + dy * t
        val ex = px - cx
        val ey = py - cy

        return sqrt(ex * ex + ey * ey)
    }
}