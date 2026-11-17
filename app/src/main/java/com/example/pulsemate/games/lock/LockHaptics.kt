package com.example.pulsemate.games.lock

import com.example.pulsemate.haptics.HapticController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LockHaptics(
    private val haptics: HapticController
) {
    private var overrideJob: Job? = null
    private var sentLevel = 0
    private var lastSent = 0L

    fun follow(level: Int) {
        if (overrideJob?.isActive == true) return

        val now = System.currentTimeMillis()

        if (level == sentLevel) return
        if (level != 0 && now - lastSent < 90L) return

        apply(level)
        lastSent = now
    }

    fun pulse(scope: CoroutineScope, level: Int, millis: Long) {
        overrideJob?.cancel()
        overrideJob = scope.launch {
            haptics.setLevel(level.coerceIn(1, 9))
            sentLevel = level
            delay(millis)
            haptics.stop()
            sentLevel = 0
        }
    }

    fun stop() {
        overrideJob?.cancel()
        overrideJob = null
        sentLevel = 0
        haptics.stop()
    }

    fun reset() {
        overrideJob?.cancel()
        overrideJob = null
        sentLevel = 0
        haptics.reset()
    }

    private fun apply(level: Int) {
        sentLevel = level

        if (level <= 0) {
            haptics.stop()
        } else {
            haptics.setLevel(level)
        }
    }
}