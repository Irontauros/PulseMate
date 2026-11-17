
package com.example.pulsemate.haptics

interface HapticController {
    fun setLevel(level: Int)
    fun stop()
    fun reset()
    fun getLevel(): Int
}
