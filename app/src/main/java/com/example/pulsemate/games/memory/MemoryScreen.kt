package com.example.pulsemate.games.memory

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pulsemate.haptics.LoveSpouseController
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS_NAME = "pulsemate_memory"
private const val BEST_KEY = "best"
private const val MISS_DELAY_MS = 800L
private const val CLEAR_DELAY_MS = 1700L
private const val ODD_NEXT_DELAY_MS = 700L
private const val CARD_RATIO = 1.3f

private enum class MemoryPhase { START, PLAYING, OVER }

private class CardView(val face: Int, val state: CardState)

private class Snapshot(
    val cards: List<CardView>,
    val rows: Int,
    val round: Int,
    val boardId: Int,
    val score: Int,
    val moves: Int,
    val lives: Int,
    val mode: MemoryMode,
    val cleared: Boolean,
    val previewing: Boolean,
    val hasClock: Boolean,
    val timeMax: Float
)

private fun bestKey(mode: MemoryMode): String {
    return if (mode == MemoryMode.CHALLENGE) BEST_KEY else "${BEST_KEY}_${mode.name}"
}

private fun loadBest(context: Context, mode: MemoryMode): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(bestKey(mode), 0)
}

private fun saveBest(context: Context, mode: MemoryMode, value: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(bestKey(mode), value)
        .apply()
}

@Composable
fun MemoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { MemoryHaptics(loveSpouse) }
    val world = remember { MemoryWorld() }

    var tick by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(MemoryPhase.START) }
    var modeChoice by remember { mutableStateOf(MemoryMode.CHALLENGE) }
    var best by remember { mutableIntStateOf(loadBest(context, MemoryMode.CHALLENGE)) }
    var timeUi by remember { mutableFloatStateOf(0f) }
    var newRecord by remember { mutableStateOf(false) }
    var showQuit by remember { mutableStateOf(false) }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    val snap = remember(tick) {
        Snapshot(
            cards = world.cards.map { CardView(it.face, it.state) },
            rows = world.rows,
            round = world.round,
            boardId = world.boardId,
            score = world.score,
            moves = world.moves,
            lives = world.livesLeft,
            mode = world.mode,
            cleared = world.boardCleared,
            previewing = world.previewing,
            hasClock = world.hasClock,
            timeMax = world.timeMax
        )
    }

    fun commitScore() {
        if (!world.freePlay && world.score > loadBest(context, world.mode)) {
            saveBest(context, world.mode, world.score)
        }
    }

    fun finishRun() {
        if (phase != MemoryPhase.PLAYING) return

        phase = MemoryPhase.OVER
        haptics.stop()

        if (!world.freePlay && world.score > loadBest(context, world.mode)) {
            saveBest(context, world.mode, world.score)
            best = world.score
            newRecord = true
        } else {
            best = loadBest(context, world.mode)
        }
    }

    fun startPreview() {
        scope.launch {
            world.beginPreview()
            tick++
            delay(world.previewMs)
            world.endPreview()
            tick++
        }
    }

    fun startRun(mode: MemoryMode) {
        world.start(mode)
        haptics.reset()
        haptics.newBoard()
        newRecord = false
        showQuit = false
        timeUi = world.timeLeft
        phase = MemoryPhase.PLAYING
        tick++

        if (mode == MemoryMode.BLIND) startPreview()
    }

    fun onCardClick(index: Int) {
        if (phase != MemoryPhase.PLAYING || showQuit) return

        val result = world.flip(index)

        if (result == FlipResult.IGNORED) return

        tick++

        if (world.mode == MemoryMode.ODD_ONE_OUT) {
            if (result == FlipResult.MATCH) {
                haptics.match(scope)

                scope.launch {
                    delay(ODD_NEXT_DELAY_MS)
                    world.nextOddBoard(true)
                    timeUi = world.timeLeft
                    tick++
                }
            } else {
                haptics.miss(scope)

                scope.launch {
                    delay(MISS_DELAY_MS)

                    if (world.gameOver) {
                        finishRun()
                    } else {
                        world.nextOddBoard(false)
                        timeUi = world.timeLeft
                        tick++
                    }
                }
            }

            return
        }

        when (result) {
            FlipResult.MATCH -> {
                if (world.boardCleared) {
                    haptics.cleared(scope)

                    scope.launch {
                        delay(CLEAR_DELAY_MS)
                        world.nextRound()
                        haptics.newBoard()
                        timeUi = world.timeLeft
                        tick++

                        if (world.mode == MemoryMode.BLIND) startPreview()
                    }
                } else {
                    haptics.match(scope)
                }
            }

            FlipResult.MISS -> {
                haptics.miss(scope)

                scope.launch {
                    delay(MISS_DELAY_MS)
                    world.hideMiss()
                    tick++

                    if (world.gameOver) {
                        finishRun()
                    }
                }
            }

            else -> Unit
        }
    }

    LaunchedEffect(haptics) {
        haptics.reset()
    }

    DisposableEffect(haptics) {
        onDispose {
            haptics.reset()
            commitScore()
        }
    }

    DisposableEffect(lifecycleOwner, haptics) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                haptics.stop()
                commitScore()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(loveSpouse) {
        while (true) {
            deviceProblem = loveSpouse.deviceProblem()
            delay(1_000)
        }
    }

    LaunchedEffect(world) {
        var last = withFrameNanos { it }

        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now

            if (phase == MemoryPhase.PLAYING && !showQuit && world.hasClock) {
                val timedOut = world.update(seconds)
                timeUi = world.timeLeft

                if (timedOut) {
                    tick++
                    haptics.miss(scope)

                    launch {
                        delay(MISS_DELAY_MS)

                        if (world.gameOver) {
                            finishRun()
                        } else {
                            world.nextOddBoard(false)
                            timeUi = world.timeLeft
                            tick++
                        }
                    }
                } else if (world.mode == MemoryMode.TIMED && world.gameOver) {
                    tick++
                    finishRun()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FeltLight, FeltDark)))
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.75f
                    )
                )
            }
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (snap.mode) {
                        MemoryMode.FREE_PLAY -> "FREE PLAY"
                        MemoryMode.TIMED -> "${ceil(timeUi).toInt()}s"
                        else -> "♥ ${snap.lives}"
                    },
                    color = when (snap.mode) {
                        MemoryMode.FREE_PLAY, MemoryMode.TIMED -> Gold
                        else -> Color(0xFFFF7A8A)
                    },
                    fontSize = 22.sp,
                    fontFamily = MemoryFont
                )

                Text(
                    text = if (snap.previewing) "MEMORIZE" else "ROUND ${snap.round}",
                    color = if (snap.previewing) Gold else Color.White.copy(alpha = 0.75f),
                    fontSize = 16.sp,
                    fontFamily = MemoryFont,
                    letterSpacing = 2.sp
                )

                Text(
                    text = if (snap.mode == MemoryMode.FREE_PLAY) "${snap.moves} moves" else "${snap.score}",
                    color = Gold,
                    fontSize = 22.sp,
                    fontFamily = MemoryFont
                )
            }

            if (snap.hasClock && phase == MemoryPhase.PLAYING) {
                val fraction = (timeUi / snap.timeMax).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(if (fraction <= 0.25f) Color(0xFFFF5A5F) else Gold)
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (snap.cards.isNotEmpty()) {
                    val cols = MemoryWorld.COLS
                    val rows = snap.rows
                    val gap = 10.dp

                    val widthFit = (maxWidth - gap * (cols - 1)) / cols
                    val heightFit = (maxHeight - gap * (rows - 1)) / rows / CARD_RATIO
                    val cardWidth = if (widthFit < heightFit) widthFit else heightFit
                    val cardHeight = cardWidth * CARD_RATIO

                    key(snap.boardId) {
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            for (r in 0 until rows) {
                                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                    for (c in 0 until cols) {
                                        val index = r * cols + c
                                        val card = snap.cards[index]

                                        PlayingCard(
                                            face = memoryFace(card.face),
                                            state = card.state,
                                            width = cardWidth,
                                            height = cardHeight,
                                            onClick = { onCardClick(index) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            deviceProblem?.let { problem ->
                Text(
                    text = problem,
                    color = Color(0xFFFFC857),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp, top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                GoldButton(
                    text = "QUIT",
                    filled = false,
                    modifier = Modifier.width(150.dp),
                    onClick = {
                        if (phase == MemoryPhase.PLAYING) showQuit = true
                    }
                )
            }
        }

        if (snap.cleared && phase == MemoryPhase.PLAYING && snap.mode != MemoryMode.ODD_ONE_OUT) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BOARD CLEARED",
                    color = Gold,
                    fontSize = 34.sp,
                    fontFamily = MemoryFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                )
            }
        }

        if (phase == MemoryPhase.START) {
            PopupCard {
                Text(
                    text = "MEMORY",
                    color = Gold,
                    fontSize = 38.sp,
                    fontFamily = MemoryFont,
                    letterSpacing = 3.sp
                )

                if (best > 0) {
                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "BEST  $best",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        fontFamily = MemoryFont,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(Modifier.height(22.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeChip(
                        mode = MemoryMode.CHALLENGE,
                        selected = modeChoice == MemoryMode.CHALLENGE,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            modeChoice = MemoryMode.CHALLENGE
                            best = loadBest(context, MemoryMode.CHALLENGE)
                        }
                    )

                    ModeChip(
                        mode = MemoryMode.FREE_PLAY,
                        selected = modeChoice == MemoryMode.FREE_PLAY,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            modeChoice = MemoryMode.FREE_PLAY
                            best = 0
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeChip(
                        mode = MemoryMode.TIMED,
                        selected = modeChoice == MemoryMode.TIMED,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            modeChoice = MemoryMode.TIMED
                            best = loadBest(context, MemoryMode.TIMED)
                        }
                    )

                    ModeChip(
                        mode = MemoryMode.BLIND,
                        selected = modeChoice == MemoryMode.BLIND,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            modeChoice = MemoryMode.BLIND
                            best = loadBest(context, MemoryMode.BLIND)
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))

                ModeChip(
                    mode = MemoryMode.ODD_ONE_OUT,
                    selected = modeChoice == MemoryMode.ODD_ONE_OUT,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        modeChoice = MemoryMode.ODD_ONE_OUT
                        best = loadBest(context, MemoryMode.ODD_ONE_OUT)
                    }
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = modeChoice.hint,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(40.dp)
                )

                Spacer(Modifier.height(10.dp))

                GoldButton(
                    text = "PLAY",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startRun(modeChoice) }
                )

                Spacer(Modifier.height(10.dp))

                GoldButton(
                    text = "BACK",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }
        }

        if (showQuit) {
            PopupCard {
                Text(
                    text = "QUIT GAME?",
                    color = Gold,
                    fontSize = 28.sp,
                    fontFamily = MemoryFont,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "A new best score is kept.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(22.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoldButton(
                        text = "NO",
                        filled = false,
                        modifier = Modifier.weight(1f),
                        onClick = { showQuit = false }
                    )

                    GoldButton(
                        text = "YES",
                        filled = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            commitScore()
                            haptics.stop()
                            onBack()
                        }
                    )
                }
            }
        }

        if (phase == MemoryPhase.OVER) {
            PopupCard {
                Text(
                    text = if (world.mode == MemoryMode.TIMED) "TIME'S UP" else "GAME OVER",
                    color = Gold,
                    fontSize = 32.sp,
                    fontFamily = MemoryFont,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "${snap.score}",
                    color = Color.White,
                    fontSize = 52.sp,
                    fontFamily = MemoryFont
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = if (newRecord) "NEW BEST!" else "BEST  $best",
                    color = if (newRecord) Gold else Color.White.copy(alpha = 0.75f),
                    fontSize = 16.sp,
                    fontFamily = MemoryFont,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(22.dp))

                GoldButton(
                    text = "PLAY AGAIN",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startRun(world.mode) }
                )

                Spacer(Modifier.height(10.dp))

                GoldButton(
                    text = "EXIT",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }
        }
    }
}

@Composable
private fun PlayingCard(
    face: String,
    state: CardState,
    width: Dp,
    height: Dp,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val faceUp = state != CardState.HIDDEN

    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 180f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "flip"
    )

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .size(width, height)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density.density
            }
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (rotation <= 90f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(CardBackTop, CardBackBottom)))
                    .border(2.dp, Gold.copy(alpha = 0.55f), shape)
                    .padding(7.dp)
                    .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♦",
                    color = Gold.copy(alpha = 0.75f),
                    fontSize = (width.value * 0.42f).sp
                )
            }
        } else {
            val matched = state == CardState.MATCHED
            val wrong = state == CardState.WRONG

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .background(
                        when {
                            matched -> CreamMatched
                            wrong -> Color(0xFFFFD9D9)
                            else -> Cream
                        }
                    )
                    .border(
                        width = if (matched || wrong) 3.dp else 1.5.dp,
                        color = when {
                            matched -> Gold
                            wrong -> Color(0xFFE5484D)
                            else -> Color(0xFFD8C7A0)
                        },
                        shape = shape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = face,
                    fontSize = (width.value * 0.5f).sp
                )
            }
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(FeltPanel)
                .border(2.dp, Gold.copy(alpha = 0.8f), RoundedCornerShape(26.dp))
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun GoldButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (filled) Gold else Color.Transparent)
            .border(1.5.dp, Gold.copy(alpha = if (filled) 1f else 0.7f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (filled) InkGreen else Gold,
            fontSize = 16.sp,
            fontFamily = MemoryFont,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun ModeChip(
    mode: MemoryMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Gold else Color.White.copy(alpha = 0.08f))
            .border(
                1.5.dp,
                if (selected) Gold else Color.White.copy(alpha = 0.25f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = mode.title,
            color = if (selected) InkGreen else Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontFamily = MemoryFont,
            letterSpacing = 1.sp
        )
    }
}