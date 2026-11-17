package com.example.pulsemate.games.merge

import com.example.pulsemate.haptics.HapticController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MergeHaptics(
    private val haptics: HapticController
) {
    private var pulseJob: Job? = null

    fun pulse(scope: CoroutineScope, level: Int) {
        pulseJob?.cancel()

        pulseJob = scope.launch {
            haptics.setLevel(level)
            delay(PULSE_MS)
            haptics.stop()
        }
    }

    fun stop() {
        pulseJob?.cancel()
        pulseJob = null
        haptics.stop()
    }

    fun reset() {
        pulseJob?.cancel()
        pulseJob = null
        haptics.reset()
    }

    companion object {
        const val PULSE_MS = 350L
    }
}