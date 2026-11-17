package com.example.pulsemate.games.whack

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class WhackEventType { HIT, GOLD, BOMB, ESCAPED, MISS, GAME_OVER }

class WhackEvent(val type: WhackEventType, val value: Int = 0)

class Hole {
    var kind = 0
    var age = 0f
    var up = 1f
    var hit = false
    var hitAge = 0f
}

class Pop(val hole: Int, val text: String, val kind: Int) {
    var age = 0f
}

class WhackWorld {
    companion object {
        const val COLS = 3
        const val ROWS = 3
        const val COUNT = 9
        const val KIND_NONE = 0
        const val KIND_MOLE = 1
        const val KIND_GOLD = 2
        const val KIND_BOMB = 3
        const val ROUND_SECONDS = 40f
        const val RISE = 0.16f
        const val FALL = 0.16f
        const val HIT_SECONDS = 0.3f
        const val POP_SECONDS = 0.8f
    }

    val holes = Array(COUNT) { Hole() }
    val pops = ArrayList<Pop>()

    var score = 0
    var combo = 0
    var bestCombo = 0
    var timeLeft = ROUND_SECONDS
    var elapsed = 0f
    var running = false

    private var spawnTimer = 0f
    private val random = Random(System.nanoTime())

    fun start() {
        for (h in holes) {
            h.kind = KIND_NONE
            h.age = 0f
            h.hit = false
            h.hitAge = 0f
        }

        pops.clear()
        score = 0
        combo = 0
        bestCombo = 0
        timeLeft = ROUND_SECONDS
        elapsed = 0f
        spawnTimer = 0.4f
        running = true
    }

    fun height(index: Int): Float {
        val h = holes[index]

        if (h.kind == KIND_NONE) return 0f

        if (h.hit) return (1f - h.hitAge / HIT_SECONDS).coerceIn(0f, 1f)

        return when {
            h.age < RISE -> h.age / RISE
            h.age < RISE + h.up -> 1f
            else -> 1f - (h.age - RISE - h.up) / FALL
        }.coerceIn(0f, 1f)
    }

    fun tap(index: Int): List<WhackEvent> {
        if (!running || index !in 0 until COUNT) return emptyList()

        val h = holes[index]

        if (h.kind == KIND_NONE || h.hit || height(index) < 0.2f) {
            combo = 0
            return listOf(WhackEvent(WhackEventType.MISS))
        }

        h.hit = true
        h.hitAge = 0f

        return when (h.kind) {
            KIND_GOLD -> {
                combo++
                bestCombo = max(bestCombo, combo)
                score += 5
                pops.add(Pop(index, "+5", KIND_GOLD))
                listOf(WhackEvent(WhackEventType.GOLD, combo))
            }

            KIND_BOMB -> {
                combo = 0
                score = max(0, score - 3)
                pops.add(Pop(index, "-3", KIND_BOMB))
                listOf(WhackEvent(WhackEventType.BOMB))
            }

            else -> {
                combo++
                bestCombo = max(bestCombo, combo)
                val points = 1 + combo / 5
                score += points
                pops.add(Pop(index, "+$points", KIND_MOLE))
                listOf(WhackEvent(WhackEventType.HIT, combo))
            }
        }
    }

    fun update(seconds: Float): List<WhackEvent> {
        val events = ArrayList<WhackEvent>()

        val popIterator = pops.iterator()
        while (popIterator.hasNext()) {
            val p = popIterator.next()
            p.age += seconds
            if (p.age >= POP_SECONDS) popIterator.remove()
        }

        if (!running) return events

        elapsed += seconds
        timeLeft -= seconds

        for (h in holes) {
            if (h.kind == KIND_NONE) continue

            if (h.hit) {
                h.hitAge += seconds
                if (h.hitAge >= HIT_SECONDS) h.kind = KIND_NONE
            } else {
                h.age += seconds

                if (h.age >= RISE + h.up + FALL) {
                    if (h.kind != KIND_BOMB) {
                        combo = 0
                        events.add(WhackEvent(WhackEventType.ESCAPED))
                    }

                    h.kind = KIND_NONE
                }
            }
        }

        spawnTimer -= seconds

        if (spawnTimer <= 0f) {
            spawn()
            if (elapsed > 15f && random.nextFloat() < 0.3f) spawn()

            val interval = max(0.35f, 0.95f - elapsed * 0.014f)
            spawnTimer = interval * (0.7f + 0.6f * random.nextFloat())
        }

        if (timeLeft <= 0f) {
            timeLeft = 0f
            running = false

            for (h in holes) h.kind = KIND_NONE

            events.add(WhackEvent(WhackEventType.GAME_OVER, score))
        }

        return events
    }

    private fun spawn() {
        val free = ArrayList<Int>()

        for (i in 0 until COUNT) if (holes[i].kind == KIND_NONE) free.add(i)

        if (free.isEmpty()) return

        val h = holes[free[random.nextInt(free.size)]]
        val bombChance = if (elapsed < 3f) 0f else min(0.08f + elapsed * 0.003f, 0.2f)
        val roll = random.nextFloat()

        h.kind = when {
            roll < bombChance -> KIND_BOMB
            roll < bombChance + 0.07f -> KIND_GOLD
            else -> KIND_MOLE
        }

        h.up = max(0.5f, 1.25f - elapsed * 0.018f)
        h.age = 0f
        h.hit = false
        h.hitAge = 0f
    }
}