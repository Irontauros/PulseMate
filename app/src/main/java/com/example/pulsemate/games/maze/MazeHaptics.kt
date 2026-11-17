package com.example.pulsemate.games.maze

import com.example.pulsemate.haptics.HapticController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MazeHaptics(
    private val haptics: HapticController
) {
    private var guideLevel = 0
    private var sentLevel = 0
    private var overrideJob: Job? = null
    private var lastBump = 0L

    fun guide(level: Int) {
        guideLevel = level
        if (overrideJob?.isActive == true) return
        apply(level)
    }

    fun bump(scope: CoroutineScope, impact: Float) {
        val now = System.currentTimeMillis()
        if (now - lastBump < BUMP_GAP_MS) return
        lastBump = now

        val strength = (3f + impact * 1.2f).toInt().coerceIn(3, 9)

        overrideJob?.cancel()
        overrideJob = scope.launch {
            haptics.setLevel(strength)
            sentLevel = strength
            delay(BUMP_MS)
            apply(guideLevel)
        }
    }

    fun win(scope: CoroutineScope) {
        overrideJob?.cancel()
        overrideJob = scope.launch {
            haptics.setLevel(9)
            sentLevel = 9
            delay(WIN_MS)
            haptics.stop()
            sentLevel = 0
        }
    }

    fun stop() {
        overrideJob?.cancel()
        overrideJob = null
        guideLevel = 0
        sentLevel = 0
        haptics.stop()
    }

    fun reset() {
        overrideJob?.cancel()
        overrideJob = null
        guideLevel = 0
        sentLevel = 0
        haptics.reset()
    }

    private fun apply(level: Int) {
        if (level == sentLevel) return

        sentLevel = level

        if (level <= 0) {
            haptics.stop()
        } else {
            haptics.setLevel(level)
        }
    }

    companion object {
        const val BUMP_MS = 220L
        const val BUMP_GAP_MS = 0L
        const val WIN_MS = 1200L
    }
}