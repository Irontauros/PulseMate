package com.example.pulsemate.games.lock

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class LockResult { SET, JAMMED, OPENED, FAILED }

class LockWorld {
    companion object {
        const val TRIES = 3
        const val RANGE = 100f
        const val MAX_PINS = 6
    }

    var level = 1
    var pins = 2
    var pinsSet = 0
    var tries = TRIES
    var tolerance = 14f
    var target = 0f
    var angle = 0f
    var score = 0

    private val random = Random(System.nanoTime())

    fun newGame() {
        level = 1
        score = 0
        startLevel()
    }

    fun nextLevel() {
        level++
        startLevel()
    }

    private fun startLevel() {
        pins = min(1 + (level + 1) / 2, MAX_PINS).coerceAtLeast(2)
        tolerance = max(4.5f, 15f - level * 1.1f)
        tries = TRIES
        pinsSet = 0
        pickTarget()
    }

    private fun pickTarget() {
        var next: Float

        do {
            next = random.nextFloat() * 360f
        } while (diff(next, target) < 50f || diff(next, angle) < 40f)

        target = next
    }

    fun diff(a: Float, b: Float): Float {
        return abs(((a - b + 540f) % 360f + 360f) % 360f - 180f)
    }

    fun turnTo(value: Float) {
        angle = ((value % 360f) + 360f) % 360f
    }

    fun closeness(): Float {
        val d = diff(angle, target)

        if (d <= tolerance) return 1f

        return (1f - (d - tolerance) / RANGE).coerceIn(0f, 1f)
    }

    fun inWindow(): Boolean = diff(angle, target) <= tolerance

    fun hapticLevel(): Int {
        if (inWindow()) return 9

        val c = closeness()

        if (c < 0.12f) return 0

        return (1 + c * 7f).toInt().coerceIn(1, 8)
    }

    fun setPin(): LockResult {
        if (inWindow()) {
            pinsSet++
            score += 10 * level

            if (pinsSet >= pins) {
                score += 25 * level + tries * 10
                return LockResult.OPENED
            }

            pickTarget()
            return LockResult.SET
        }

        tries--

        return if (tries <= 0) LockResult.FAILED else LockResult.JAMMED
    }
}