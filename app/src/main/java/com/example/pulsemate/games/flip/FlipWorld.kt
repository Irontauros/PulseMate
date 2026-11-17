package com.example.pulsemate.games.flip

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class FlipEvent { FLIP, LAND, GEM, DEAD }

enum class ObstacleKind { SPIKE_FLOOR, SPIKE_CEIL, BLOCK_FLOOR, BLOCK_CEIL, MINE }

class Obstacle(val kind: ObstacleKind, var x: Float, val w: Float, val y: Float)

class Gem(var x: Float, val y: Float) {
    var taken = false
}

class FlipWorld {
    companion object {
        const val W = 11f
        const val H = 12f
        const val BAND = 1.2f
        const val PX = 3f
        const val HALF = 0.4f
        const val HIT = 0.34f
        const val GRAVITY = 120f
        const val MAX_V = 32f
        const val BLOCK_H = 2.2f
        const val SPIKE_H = 1.0f
    }

    val obstacles = ArrayList<Obstacle>()
    val gems = ArrayList<Gem>()
    val events = ArrayList<FlipEvent>()

    var cy = H - HALF
    var vy = 0f
    var gravDir = 1
    var grounded = true
    var time = 0f
    var speed = 6f
    var distance = 0f
    var scroll = 0f
    var gemCount = 0
    var dead = false

    private var untilSpawn = 14f
    private val random = Random(System.nanoTime())

    fun start() {
        obstacles.clear()
        gems.clear()
        events.clear()
        cy = H - HALF
        vy = 0f
        gravDir = 1
        grounded = true
        time = 0f
        speed = 6f
        distance = 0f
        scroll = 0f
        gemCount = 0
        dead = false
        untilSpawn = 14f
    }

    fun score(): Int = (distance / 2f).toInt() + gemCount * 25

    fun flip() {
        if (dead) return

        gravDir = -gravDir
        grounded = false
        events.add(FlipEvent.FLIP)
    }

    private fun spawn() {
        val kind = when (random.nextInt(100)) {
            in 0 until 25 -> ObstacleKind.SPIKE_FLOOR
            in 25 until 50 -> ObstacleKind.SPIKE_CEIL
            in 50 until 65 -> ObstacleKind.BLOCK_FLOOR
            in 65 until 80 -> ObstacleKind.BLOCK_CEIL
            else -> ObstacleKind.MINE
        }

        val w = when (kind) {
            ObstacleKind.SPIKE_FLOOR, ObstacleKind.SPIKE_CEIL -> 1.4f
            ObstacleKind.BLOCK_FLOOR, ObstacleKind.BLOCK_CEIL -> 1.3f
            ObstacleKind.MINE -> 1.0f
        }

        val y = if (kind == ObstacleKind.MINE) 2f + random.nextFloat() * 8f else 0f
        val x = W + 1f

        obstacles.add(Obstacle(kind, x, w, y))

        val gap = speed * (1.0f + random.nextFloat() * 0.7f)

        untilSpawn = gap + w

        if (random.nextInt(100) < 55) {
            val gy = if (random.nextBoolean()) 1.0f else H - 1.0f

            gems.add(Gem(x + w + gap / 2f, gy))
        }
    }

    private fun hitsBox(x0: Float, x1: Float, y0: Float, y1: Float): Boolean {
        return PX + HIT > x0 && PX - HIT < x1 && cy + HIT > y0 && cy - HIT < y1
    }

    private fun collides(o: Obstacle): Boolean {
        return when (o.kind) {
            ObstacleKind.SPIKE_FLOOR -> hitsBox(o.x + 0.25f, o.x + o.w - 0.25f, H - 0.75f, H)
            ObstacleKind.SPIKE_CEIL -> hitsBox(o.x + 0.25f, o.x + o.w - 0.25f, 0f, 0.75f)
            ObstacleKind.BLOCK_FLOOR -> hitsBox(o.x, o.x + o.w, H - BLOCK_H, H)
            ObstacleKind.BLOCK_CEIL -> hitsBox(o.x, o.x + o.w, 0f, BLOCK_H)
            ObstacleKind.MINE -> {
                val mx = o.x + o.w / 2f
                val px = mx.coerceIn(PX - HIT, PX + HIT)
                val py = o.y.coerceIn(cy - HIT, cy + HIT)

                hypot(px - mx, py - o.y) < 0.45f
            }
        }
    }

    fun update(dt: Float) {
        if (dead) return

        time += dt
        speed = min(10.5f, 6f + time * 0.12f)

        val travel = speed * dt

        distance += travel
        scroll += travel

        vy += GRAVITY * gravDir * dt
        vy = vy.coerceIn(-MAX_V, MAX_V)
        cy += vy * dt

        val floor = H - HALF
        val ceil = HALF

        if (gravDir > 0 && cy >= floor) {
            cy = floor
            vy = 0f

            if (!grounded) {
                grounded = true
                events.add(FlipEvent.LAND)
            }
        } else if (gravDir < 0 && cy <= ceil) {
            cy = ceil
            vy = 0f

            if (!grounded) {
                grounded = true
                events.add(FlipEvent.LAND)
            }
        } else {
            grounded = false
        }

        cy = cy.coerceIn(ceil, floor)

        for (o in obstacles) o.x -= travel
        for (g in gems) g.x -= travel

        obstacles.removeAll { it.x + it.w < -2f }
        gems.removeAll { it.x < -2f }

        untilSpawn -= travel

        if (untilSpawn <= 0f) spawn()

        for (g in gems) {
            if (!g.taken && hypot(g.x - PX, g.y - cy) < 0.8f) {
                g.taken = true
                gemCount++
                events.add(FlipEvent.GEM)
            }
        }

        gems.removeAll { it.taken }

        for (o in obstacles) {
            if (collides(o)) {
                dead = true
                events.add(FlipEvent.DEAD)
                break
            }
        }
    }

    fun secondsToNext(): Float {
        var best = 99f

        for (o in obstacles) {
            if (o.x + o.w < PX - 0.3f) continue

            best = min(best, max(0f, (o.x - PX) / speed))
        }

        return best
    }

    fun warnLevel(): Int {
        val t = secondsToNext()

        if (t > 1.4f) return 0

        return (2 + ((1.4f - t) / 1.4f * 6f).toInt()).coerceIn(2, 8)
    }
}