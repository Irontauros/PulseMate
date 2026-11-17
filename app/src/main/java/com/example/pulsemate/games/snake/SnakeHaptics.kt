package com.example.pulsemate.games.snake

import com.example.pulsemate.haptics.HapticController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SnakeHaptics(
    private val haptics: HapticController
) {
    private var idleLevel = 0
    private var sentLevel = 0
    private var overrideJob: Job? = null

    fun idle(level: Int) {
        idleLevel = level
        if (overrideJob?.isActive == true) return
        apply(level)
    }

    fun eat(scope: CoroutineScope, level: Int) {
        overrideJob?.cancel()
        overrideJob = scope.launch {
            haptics.setLevel(level.coerceIn(1, 9))
            sentLevel = level.coerceIn(1, 9)
            delay(EAT_MS)
            apply(idleLevel)
        }
    }

    fun die(scope: CoroutineScope) {
        overrideJob?.cancel()
        overrideJob = scope.launch {
            haptics.setLevel(9)
            sentLevel = 9
            delay(DIE_MS)
            haptics.stop()
            sentLevel = 0
        }
    }

    fun stop() {
        overrideJob?.cancel()
        overrideJob = null
        idleLevel = 0
        sentLevel = 0
        haptics.stop()
    }

    fun reset() {
        overrideJob?.cancel()
        overrideJob = null
        idleLevel = 0
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
        const val EAT_MS = 450L
        const val DIE_MS = 900L
    }
}