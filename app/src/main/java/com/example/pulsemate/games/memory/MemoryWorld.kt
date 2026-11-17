package com.example.pulsemate.games.memory

import kotlin.random.Random

enum class CardState { HIDDEN, UP, MATCHED, WRONG }

enum class FlipResult { IGNORED, FIRST, MATCH, MISS }

enum class MemoryMode(val title: String, val hint: String) {
    CHALLENGE("CHALLENGE", "Limited misses. Clear every board."),
    FREE_PLAY("FREE PLAY", "No losses. Just play."),
    TIMED("TIMED", "Beat the clock. Matches add time, misses cost it."),
    BLIND("BLIND", "Memorize the board, then it flips down."),
    ODD_ONE_OUT("ODD ONE OUT", "Find the card that's different. 3 lives.")
}

class MemoryCard(val face: Int) {
    var state: CardState = CardState.HIDDEN
}

class MemoryWorld(private val random: Random = Random.Default) {

    companion object {
        const val COLS = 4
        const val ODD_LIVES = 3
        const val TIMED_MATCH_BONUS = 1.5f
        const val TIMED_MISS_PENALTY = 2f
        const val TIMED_SECONDS_PER_PAIR = 5f
        const val TIMED_NEW_BOARD_PER_PAIR = 3f

        fun rowsFor(round: Int): Int = when {
            round <= 1 -> 4
            round == 2 -> 5
            else -> 6
        }

        fun pairsFor(round: Int): Int = COLS * rowsFor(round) / 2

        fun oddRowsFor(round: Int): Int = (3 + (round - 1) / 2).coerceAtMost(6)

        fun oddSeconds(round: Int): Float = (8f - (round - 1) * 0.5f).coerceAtLeast(2.5f)
    }

    private var cardList = ArrayList<MemoryCard>()
    private var first = -1
    private var second = -1
    private var matchedPairs = 0

    val cards: List<MemoryCard> get() = cardList

    var mode: MemoryMode = MemoryMode.CHALLENGE
        private set

    var round: Int = 1
        private set

    var boardId: Int = 0
        private set

    var score: Int = 0
        private set

    var moves: Int = 0
        private set

    var misses: Int = 0
        private set

    var streak: Int = 0
        private set

    var gameOver: Boolean = false
        private set

    var boardCleared: Boolean = false
        private set

    var locked: Boolean = false
        private set

    var previewing: Boolean = false
        private set

    var timeLeft: Float = 0f
        private set

    var timeMax: Float = 1f
        private set

    private var oddIndex = -1

    val freePlay: Boolean get() = mode == MemoryMode.FREE_PLAY

    val hasClock: Boolean get() = mode == MemoryMode.TIMED || mode == MemoryMode.ODD_ONE_OUT

    val rows: Int
        get() = if (mode == MemoryMode.ODD_ONE_OUT) oddRowsFor(round) else rowsFor(round)

    val pairs: Int get() = pairsFor(round)

    val livesLeft: Int
        get() {
            val allowed = if (mode == MemoryMode.ODD_ONE_OUT) ODD_LIVES else pairs
            return (allowed - misses).coerceAtLeast(0)
        }

    val previewMs: Long get() = ((2f + pairs * 0.25f) * 1000f).toLong()

    fun start(mode: MemoryMode) {
        this.mode = mode
        round = 1
        score = 0
        moves = 0
        misses = 0
        gameOver = false
        timeLeft = if (mode == MemoryMode.TIMED) pairsFor(1) * TIMED_SECONDS_PER_PAIR else 0f
        newBoard()
    }

    fun nextRound() {
        round++

        if (mode == MemoryMode.TIMED) {
            timeLeft += pairs * TIMED_NEW_BOARD_PER_PAIR
        }

        newBoard()
    }

    fun nextOddBoard(advance: Boolean) {
        if (advance) round++
        newBoard()
    }

    fun beginPreview() {
        previewing = true
        locked = true
        cardList.forEach { it.state = CardState.UP }
    }

    fun endPreview() {
        previewing = false
        locked = false
        cardList.forEach {
            if (it.state == CardState.UP) it.state = CardState.HIDDEN
        }
    }

    fun update(seconds: Float): Boolean {
        if (!hasClock || gameOver || boardCleared || previewing) return false
        if (mode == MemoryMode.ODD_ONE_OUT && locked) return false

        timeLeft -= seconds

        if (timeLeft > 0f) return false

        timeLeft = 0f

        if (mode == MemoryMode.TIMED) {
            gameOver = true
            return false
        }

        locked = true
        misses++
        streak = 0

        if (oddIndex in cardList.indices) {
            cardList[oddIndex].state = CardState.MATCHED
        }

        if (misses >= ODD_LIVES) gameOver = true

        return true
    }

    fun flip(index: Int): FlipResult {
        if (gameOver || boardCleared || locked) return FlipResult.IGNORED
        if (index !in cardList.indices) return FlipResult.IGNORED

        if (mode == MemoryMode.ODD_ONE_OUT) return pickOdd(index)

        val card = cardList[index]
        if (card.state != CardState.HIDDEN) return FlipResult.IGNORED

        card.state = CardState.UP

        if (first == -1) {
            first = index
            return FlipResult.FIRST
        }

        second = index
        moves++

        val a = cardList[first]
        val b = cardList[second]

        if (a.face == b.face) {
            a.state = CardState.MATCHED
            b.state = CardState.MATCHED
            first = -1
            second = -1
            streak++
            matchedPairs++
            score += 10 + (streak - 1) * 2

            if (mode == MemoryMode.TIMED) {
                timeLeft += TIMED_MATCH_BONUS
            }

            if (matchedPairs >= pairs) {
                boardCleared = true
                score += 50 + round * 10
            }

            return FlipResult.MATCH
        }

        locked = true
        streak = 0
        misses++

        when (mode) {
            MemoryMode.CHALLENGE, MemoryMode.BLIND -> {
                if (misses >= pairs) gameOver = true
            }

            MemoryMode.TIMED -> {
                timeLeft -= TIMED_MISS_PENALTY
                if (timeLeft <= 0f) {
                    timeLeft = 0f
                    gameOver = true
                }
            }

            else -> Unit
        }

        return FlipResult.MISS
    }

    fun hideMiss() {
        if (first != -1) cardList[first].state = CardState.HIDDEN
        if (second != -1) cardList[second].state = CardState.HIDDEN
        first = -1
        second = -1
        locked = false
    }

    private fun pickOdd(index: Int): FlipResult {
        locked = true
        moves++

        if (index == oddIndex) {
            cardList[index].state = CardState.MATCHED
            streak++
            score += 10 + (timeLeft * 2f).toInt()
            return FlipResult.MATCH
        }

        cardList[index].state = CardState.WRONG
        cardList[oddIndex].state = CardState.MATCHED
        streak = 0
        misses++

        if (misses >= ODD_LIVES) gameOver = true

        return FlipResult.MISS
    }

    private fun newBoard() {
        boardId++
        first = -1
        second = -1
        matchedPairs = 0
        boardCleared = false
        locked = false
        previewing = false

        if (mode == MemoryMode.ODD_ONE_OUT) {
            val count = COLS * rows
            val tier = ((round - 1) / 3).coerceAtMost(MemoryTierCount - 1)
            val pick = random.nextInt(memoryLookalikeCount(tier))
            val look = memoryLookalike(tier, pick)
            val swap = random.nextBoolean()
            val base = if (swap) look.first else look.second
            val odd = if (swap) look.second else look.first

            oddIndex = random.nextInt(count)

            cardList = ArrayList((0 until count).map { i ->
                MemoryCard(if (i == oddIndex) odd else base).also { it.state = CardState.UP }
            })

            timeLeft = oddSeconds(round)
            timeMax = timeLeft
            return
        }

        misses = 0
        streak = 0

        val chosen = (0 until MemoryFaceCount).shuffled(random).take(pairs)
        val deck = (chosen + chosen).shuffled(random)

        cardList = ArrayList(deck.map { MemoryCard(it) })

        timeMax = if (mode == MemoryMode.TIMED) timeLeft else 1f
    }
}