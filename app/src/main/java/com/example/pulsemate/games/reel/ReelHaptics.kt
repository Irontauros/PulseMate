package com.example.pulsemate.games.reel

import com.example.pulsemate.haptics.HapticController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReelHaptics(
    private val haptics: HapticController
) {
    private var job: Job? = null
    private var activeLevel = 0
    private var activeUntil = 0L

    fun pulse(scope: CoroutineScope, level: Int, millis: Long) {
        val now = System.currentTimeMillis()

        if (now < activeUntil && level < activeLevel) return

        activeLevel = level
        activeUntil = now + millis

        job?.cancel()
        job = scope.launch {
            haptics.setLevel(level.coerceIn(1, 9))
            delay(millis)
            haptics.stop()
            activeLevel = 0
            activeUntil = 0L
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        activeLevel = 0
        activeUntil = 0L
        haptics.stop()
    }

    fun reset() {
        job?.cancel()
        job = null
        activeLevel = 0
        activeUntil = 0L
        haptics.reset()
    }
}