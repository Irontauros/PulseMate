package com.example.pulsemate.games.quiz

import com.example.pulsemate.haptics.HapticController

class QuizHaptics(
    private val haptics: HapticController
) {
    private var level = 0
    private var correctStreak = 0

    fun correct() {
        correctStreak++
        level = (level + 1).coerceAtMost(9)
        haptics.setLevel(level)
    }

    fun wrong() {
        reduceLevel()
    }

    fun timeout() {
        reduceLevel()
    }

    private fun reduceLevel() {
        correctStreak = 0
        level = (level - 1).coerceAtLeast(0)

        if (level == 0) {
            haptics.stop()
        } else {
            haptics.setLevel(level)
        }
    }

    fun reset() {
        level = 0
        correctStreak = 0
        haptics.reset()
    }

    fun getLevel(): Int = level.coerceAtLeast(1)

    fun getRawLevel(): Int = level

    fun getCorrectStreak(): Int = correctStreak
}