package com.example.pulsemate.games.water

import kotlin.math.min
import kotlin.random.Random

enum class WaterEventType { POUR, INVALID, TUBE_DONE, LEVEL_CLEAR, UNDO }

class WaterEvent(
    val type: WaterEventType,
    val value: Int = 0
)

class Pour(
    val from: Int,
    val to: Int,
    val count: Int,
    val color: Int,
    var progress: Float = 0f
)

class WaterMove(
    val from: Int,
    val to: Int,
    val count: Int
)

class WaterWorld(private val random: Random = Random.Default) {

    companion object {
        const val CAP = 4
        const val EMPTY_TUBES = 2
        const val MAX_COLORS = 10
        private const val POUR_SECONDS = 0.4f

        fun colorCount(level: Int): Int = min(2 + level, MAX_COLORS)
    }

    val tubes = ArrayList<ArrayList<Int>>()

    var level: Int = 1
        private set

    var moves: Int = 0
        private set

    var selected: Int = -1
        private set

    var solved: Boolean = false
        private set

    var pour: Pour? = null
        private set

    private val initial = ArrayList<ArrayList<Int>>()
    private val history = ArrayList<WaterMove>()

    init {
        startLevel(1)
    }

    fun startLevel(newLevel: Int) {
        level = newLevel

        val colors = colorCount(level)
        val cells = ArrayList<Int>()

        for (c in 0 until colors) {
            repeat(CAP) { cells.add(c) }
        }

        cells.shuffle(random)

        tubes.clear()

        for (t in 0 until colors) {
            val tube = ArrayList<Int>()

            for (k in 0 until CAP) tube.add(cells[t * CAP + k])

            tubes.add(tube)
        }

        repeat(EMPTY_TUBES) { tubes.add(ArrayList()) }

        if (isSolvedState()) {
            startLevel(newLevel)
            return
        }

        initial.clear()

        for (t in tubes) initial.add(ArrayList(t))

        resetState()
    }

    fun nextLevel() {
        startLevel(level + 1)
    }

    fun restart() {
        tubes.clear()

        for (t in initial) tubes.add(ArrayList(t))

        resetState()
    }

    private fun resetState() {
        moves = 0
        selected = -1
        solved = false
        pour = null
        history.clear()
    }

    fun tap(index: Int): List<WaterEvent> {
        val events = ArrayList<WaterEvent>()

        if (pour != null || solved || index !in tubes.indices) return events

        if (selected == -1) {
            if (tubes[index].isNotEmpty()) selected = index
            return events
        }

        if (selected == index) {
            selected = -1
            return events
        }

        val from = selected
        val count = pourCount(from, index)

        if (count > 0) {
            pour = Pour(from, index, count, tubes[from].last())
            selected = -1
            events.add(WaterEvent(WaterEventType.POUR, count))
            return events
        }

        events.add(WaterEvent(WaterEventType.INVALID))
        selected = if (tubes[index].isNotEmpty()) index else -1
        return events
    }

    fun undo(): List<WaterEvent> {
        val events = ArrayList<WaterEvent>()

        if (pour != null || solved || history.isEmpty()) return events

        val move = history.removeAt(history.size - 1)

        repeat(move.count) {
            tubes[move.from].add(tubes[move.to].removeAt(tubes[move.to].size - 1))
        }

        moves = (moves - 1).coerceAtLeast(0)
        selected = -1
        events.add(WaterEvent(WaterEventType.UNDO))
        return events
    }

    fun update(seconds: Float): List<WaterEvent> {
        val events = ArrayList<WaterEvent>()
        val p = pour ?: return events

        p.progress += seconds.coerceIn(0f, 0.05f) / POUR_SECONDS

        if (p.progress < 1f) return events

        repeat(p.count) {
            tubes[p.to].add(tubes[p.from].removeAt(tubes[p.from].size - 1))
        }

        history.add(WaterMove(p.from, p.to, p.count))
        moves++
        pour = null

        val dest = tubes[p.to]

        if (isComplete(dest)) {
            events.add(WaterEvent(WaterEventType.TUBE_DONE))
        }

        if (isSolvedState()) {
            solved = true
            events.add(WaterEvent(WaterEventType.LEVEL_CLEAR))
        }

        return events
    }

    private fun pourCount(from: Int, to: Int): Int {
        val a = tubes[from]
        val b = tubes[to]

        if (a.isEmpty() || b.size >= CAP) return 0

        val color = a.last()

        if (b.isNotEmpty() && b.last() != color) return 0

        var run = 0

        for (k in a.indices.reversed()) {
            if (a[k] == color) run++ else break
        }

        if (b.isEmpty() && run == a.size) return 0

        return min(run, CAP - b.size)
    }

    private fun isComplete(tube: List<Int>): Boolean {
        return tube.size == CAP && tube.all { it == tube[0] }
    }

    private fun isSolvedState(): Boolean {
        return tubes.all { it.isEmpty() || isComplete(it) }
    }
}