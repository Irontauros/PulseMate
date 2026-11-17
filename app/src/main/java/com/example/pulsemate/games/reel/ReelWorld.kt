package com.example.pulsemate.games.reel

import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class ReelEvent { TUG, CATCH, SNAP, ESCAPE }

enum class ReelState { FIGHTING, CAUGHT, LOST }

class ReelWorld {
    companion object {
        const val LIVES = 3
        const val SAFE_LOW = 0.35f
        const val SAFE_HIGH = 0.7f
        const val WARN_HIGH = 0.9f
    }

    val events = ArrayList<ReelEvent>()

    var level = 1
    var lives = LIVES
    var score = 0
    var tension = 0.35f
    var depth = 0.65f
    var reeling = false
    var pull = 0f
    var state = ReelState.FIGHTING
    var time = 0f
    var lostByEscape = false

    private var thrashLeft = 0f
    private var thrashStrength = 0f
    private var restLeft = 1.5f
    private val random = Random(System.nanoTime())

    fun start() {
        level = 1
        lives = LIVES
        score = 0
        events.clear()
        newFish()
    }

    fun newFish() {
        tension = 0.35f
        depth = 0.65f
        reeling = false
        pull = 0f
        thrashLeft = 0f
        thrashStrength = 0f
        restLeft = 1.5f
        state = ReelState.FIGHTING
        lostByEscape = false
    }

    fun nextFish() {
        level++
        newFish()
    }

    fun pullMax(): Float = min(1.4f, 0.8f + 0.07f * level)

    fun zone(): Int {
        return when {
            tension < SAFE_LOW -> 0
            tension <= SAFE_HIGH -> 1
            tension <= WARN_HIGH -> 2
            else -> 3
        }
    }

    fun update(dt: Float) {
        if (state != ReelState.FIGHTING) return

        time += dt

        if (thrashLeft > 0f) {
            thrashLeft -= dt
            pull = thrashStrength * (0.65f + 0.35f * sin(time * 14f))
        } else {
            pull = 0f
            restLeft -= dt

            if (restLeft <= 0f) {
                thrashLeft = 0.7f + random.nextFloat() * 0.8f
                thrashStrength = pullMax() * (0.5f + random.nextFloat() * 0.5f)
                restLeft = (2.4f - level * 0.08f - random.nextFloat() * 1.0f).coerceAtLeast(0.8f)
                events.add(ReelEvent.TUG)
            }
        }

        val push = if (reeling) 0.6f else -1.0f

        tension += (push + pull) * dt
        tension = tension.coerceAtLeast(0f)

        if (tension >= 1f) {
            tension = 1f
            lives--
            state = ReelState.LOST
            lostByEscape = false
            events.add(ReelEvent.SNAP)

            return
        }

        val z = zone()

        if (reeling && z == 1) depth -= 0.11f * dt
        if (reeling && z >= 2) depth -= 0.03f * dt
        if (z == 0) depth += 0.05f * dt

        depth += 0.02f * dt

        if (depth <= 0f) {
            depth = 0f
            score += 100 + level * 25
            state = ReelState.CAUGHT
            events.add(ReelEvent.CATCH)
        } else if (depth >= 1f) {
            depth = 1f
            lives--
            state = ReelState.LOST
            lostByEscape = true
            events.add(ReelEvent.ESCAPE)
        }
    }

    fun hapticLevel(): Int {
        return when {
            tension < SAFE_LOW -> 0
            tension <= SAFE_HIGH -> 2 + ((tension - SAFE_LOW) / (SAFE_HIGH - SAFE_LOW) * 2f).toInt()
            tension <= WARN_HIGH -> 6
            else -> 8
        }
    }
}