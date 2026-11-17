package com.example.pulsemate.games.memory

import com.example.pulsemate.haptics.HapticController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MemoryHaptics(
    private val haptics: HapticController
) {
    private var pulseJob: Job? = null
    private var level = 0

    fun match(scope: CoroutineScope) {
        level = (level + 1).coerceAtMost(9)
        pulse(scope, level, MATCH_MS)
    }

    fun miss(scope: CoroutineScope) {
        level = (level - 1).coerceAtLeast(0)

        if (level == 0) {
            cancelPulse()
            haptics.stop()
        } else {
            pulse(scope, level, MISS_MS)
        }
    }

    fun cleared(scope: CoroutineScope) {
        level = 9
        pulse(scope, 9, CLEAR_MS)
    }

    fun newBoard() {
        level = 0
    }

    fun stop() {
        cancelPulse()
        haptics.stop()
    }

    fun reset() {
        cancelPulse()
        level = 0
        haptics.reset()
    }

    private fun pulse(scope: CoroutineScope, value: Int, durationMs: Long) {
        cancelPulse()

        pulseJob = scope.launch {
            haptics.setLevel(value)
            delay(durationMs)
            haptics.stop()
        }
    }

    private fun cancelPulse() {
        pulseJob?.cancel()
        pulseJob = null
    }

    companion object {
        const val MATCH_MS = 600L
        const val MISS_MS = 300L
        const val CLEAR_MS = 1500L
    }
}