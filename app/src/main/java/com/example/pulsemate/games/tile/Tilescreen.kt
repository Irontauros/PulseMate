package com.example.pulsemate.games.tile.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pulsemate.games.tile.TileAmber
import com.example.pulsemate.games.tile.TileBase
import com.example.pulsemate.games.tile.TileBgBottom
import com.example.pulsemate.games.tile.TileBgTop
import com.example.pulsemate.games.tile.TileDim
import com.example.pulsemate.games.tile.TileFont
import com.example.pulsemate.games.tile.TileHaptics
import com.example.pulsemate.games.tile.TileInk
import com.example.pulsemate.games.tile.TileLine
import com.example.pulsemate.games.tile.TileMint
import com.example.pulsemate.games.tile.TilePanel
import com.example.pulsemate.games.tile.TileRed
import com.example.pulsemate.games.tile.TileRowColors
import com.example.pulsemate.games.tile.TileTap
import com.example.pulsemate.games.tile.TileWorld
import com.example.pulsemate.haptics.LoveSpouseController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

private const val PREFS_NAME = "pulsemate_tileecho"
private const val BEST_KEY = "best"

private enum class TilePhase { START, SHOWING, INPUT, OVER }

private fun loadBest(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(BEST_KEY, 0)
}

private fun saveBest(context: Context, value: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(BEST_KEY, value)
        .apply()
}

private fun Context.findActivity(): Activity? {
    var current: Context = this

    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }

    return null
}

@Composable
fun TileScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { TileHaptics(loveSpouse) }
    val world = remember { TileWorld() }

    val goodGlow = remember { FloatArray(TileWorld.COUNT) }
    val badGlow = remember { FloatArray(TileWorld.COUNT) }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(TilePhase.START) }
    var lit by remember { mutableIntStateOf(-1) }
    var roundUi by remember { mutableIntStateOf(1) }
    var livesUi by remember { mutableIntStateOf(TileWorld.LIVES) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var timeFrac by remember { mutableFloatStateOf(1f) }
    var status by remember { mutableStateOf("") }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var bestAtStart by remember { mutableIntStateOf(best) }
    var newBest by remember { mutableStateOf(false) }
    var deviceProblem by remember { mutableStateOf<String?>(null) }
    var showJob by remember { mutableStateOf<Job?>(null) }

    fun sync() {
        roundUi = world.round
        livesUi = world.lives
        scoreUi = world.score
    }

    fun showSequence() {
        showJob?.cancel()
        showJob = scope.launch {
            phase = TilePhase.SHOWING
            status = "Watch"
            timeFrac = 1f
            delay(800L)

            for (t in world.seq) {
                lit = t
                haptics.pulse(scope, world.levelFor(t), max(120L, world.showMillis() - 40L))
                delay(world.showMillis())
                lit = -1
                delay(world.gapMillis())
            }

            world.beginInput()
            status = "Your turn"
            phase = TilePhase.INPUT
        }
    }

    fun failTurn(tile: Int) {
        if (tile >= 0) badGlow[tile] = 1f

        sync()

        if (world.lives <= 0) {
            haptics.pulse(scope, 6, 800L)
            newBest = world.round - 1 > bestAtStart
            status = "Game over"
            phase = TilePhase.OVER
        } else {
            haptics.pulse(scope, 4, 500L)
            status = "Missed. Watch again"
            phase = TilePhase.SHOWING

            showJob?.cancel()
            showJob = scope.launch {
                delay(1000L)
                showSequence()
            }
        }
    }

    fun tapTile(tile: Int) {
        if (phase != TilePhase.INPUT) return

        when (world.tap(tile)) {
            TileTap.CORRECT -> {
                goodGlow[tile] = 1f
                haptics.pulse(scope, world.levelFor(tile), 130L)
            }

            TileTap.ROUND_DONE -> {
                goodGlow[tile] = 1f
                haptics.pulse(scope, 8, 320L)
                sync()

                if (world.round > best) {
                    best = world.round
                    saveBest(context, best)
                }

                status = "Round ${world.round} cleared"
                phase = TilePhase.SHOWING

                showJob?.cancel()
                showJob = scope.launch {
                    delay(900L)
                    world.nextRound()
                    sync()
                    showSequence()
                }
            }

            TileTap.WRONG -> failTurn(tile)
        }
    }

    fun startGame() {
        showJob?.cancel()
        world.start()
        haptics.reset()
        bestAtStart = best
        newBest = false
        lit = -1
        sync()
        showSequence()
    }

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        onDispose {
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(haptics) {
        haptics.reset()
    }

    DisposableEffect(haptics) {
        onDispose {
            haptics.reset()
            showJob?.cancel()
        }
    }

    DisposableEffect(lifecycleOwner, haptics) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) haptics.stop()
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

            for (i in 0 until TileWorld.COUNT) {
                if (goodGlow[i] > 0f) goodGlow[i] = (goodGlow[i] - seconds * 3f).coerceAtLeast(0f)
                if (badGlow[i] > 0f) badGlow[i] = (badGlow[i] - seconds * 2f).coerceAtLeast(0f)
            }

            if (phase == TilePhase.INPUT) {
                world.inputLeft -= seconds
                timeFrac = (world.inputLeft / world.inputTotal).coerceIn(0f, 1f)

                if (world.inputLeft <= 0f) {
                    world.timeout()
                    failTurn(-1)
                }
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TileBgTop, TileBgBottom)))
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TILE ECHO",
                color = TileDim,
                fontSize = 14.sp,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
                fontFamily = TileFont,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "ROUND $roundUi",
                color = TileInk,
                fontSize = 34.sp,
                letterSpacing = 5.sp,
                textAlign = TextAlign.Center,
                fontFamily = TileFont,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "●".repeat(livesUi.coerceAtLeast(0)) + "○".repeat((TileWorld.LIVES - livesUi).coerceAtLeast(0)),
                color = TileAmber,
                fontSize = 18.sp,
                letterSpacing = 5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "SCORE $scoreUi",
                    color = TileDim,
                    fontSize = 14.sp,
                    letterSpacing = 3.sp,
                    fontFamily = TileFont
                )

                Text(
                    text = "BEST $best",
                    color = TileDim,
                    fontSize = 14.sp,
                    letterSpacing = 3.sp,
                    fontFamily = TileFont
                )
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(TileLine.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (phase == TilePhase.INPUT) timeFrac else 0f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (timeFrac > 0.25f) TileAmber else TileRed)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()

                                    val cell = size.width / TileWorld.SIZE.toFloat()
                                    val col = (down.position.x / cell).toInt()
                                    val row = (down.position.y / cell).toInt()

                                    if (col in 0 until TileWorld.SIZE && row in 0 until TileWorld.SIZE) {
                                        tapTile(row * TileWorld.SIZE + col)
                                    }
                                }
                            }
                    ) {
                        val tick = frame

                        if (tick >= 0) {
                            val cell = size.width / TileWorld.SIZE
                            val gap = cell * 0.07f
                            val radius = CornerRadius(cell * 0.2f, cell * 0.2f)

                            for (t in 0 until TileWorld.COUNT) {
                                val col = t % TileWorld.SIZE
                                val row = t / TileWorld.SIZE
                                val x = col * cell + gap
                                val y = row * cell + gap
                                val s = cell - gap * 2f
                                val tone = TileRowColors[row]
                                val on = lit == t

                                drawRoundRect(TileBase, Offset(x, y), Size(s, s), radius)
                                drawRoundRect(tone.copy(alpha = 0.16f), Offset(x, y), Size(s, s), radius)
                                drawRoundRect(tone, Offset(x, y), Size(s, s), radius, style = Stroke(width = 4f))

                                if (on) {
                                    drawRoundRect(tone.copy(alpha = 0.28f), Offset(x - gap, y - gap), Size(s + gap * 2f, s + gap * 2f), CornerRadius(cell * 0.24f, cell * 0.24f))
                                    drawRoundRect(tone, Offset(x, y), Size(s, s), radius)
                                    drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(x + s * 0.1f, y + s * 0.08f), Size(s * 0.8f, s * 0.3f), CornerRadius(cell * 0.12f, cell * 0.12f))
                                }

                                if (goodGlow[t] > 0f) {
                                    drawRoundRect(TileMint.copy(alpha = 0.9f * goodGlow[t]), Offset(x, y), Size(s, s), radius)
                                }

                                if (badGlow[t] > 0f) {
                                    drawRoundRect(TileRed.copy(alpha = 0.9f * badGlow[t]), Offset(x, y), Size(s, s), radius)
                                }

                                val dotsWidth = row * cell * 0.09f
                                for (d in 0..row) {
                                    drawCircle(
                                        color = if (on) Color.White.copy(alpha = 0.85f) else tone,
                                        radius = cell * 0.03f,
                                        center = Offset(x + s * 0.5f - dotsWidth / 2f + d * cell * 0.09f, y + s * 0.84f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = status.uppercase(),
                        color = if (phase == TilePhase.INPUT) TileAmber else TileDim,
                        fontSize = 18.sp,
                        letterSpacing = 5.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = TileFont,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            deviceProblem?.let { problem ->
                Text(
                    text = problem,
                    color = TileAmber,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            TileButton(
                text = "Back",
                filled = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    haptics.stop()
                    onBack()
                }
            )
        }

        when (phase) {
            TilePhase.START -> PopupCard {
                Text(
                    text = "TILE ECHO",
                    color = TileAmber,
                    fontSize = 32.sp,
                    letterSpacing = 6.sp,
                    fontFamily = TileFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Watch the tiles light up, then tap them in the same order before the line runs out. Every round adds one more tile and gets faster. The dots on a tile show how strong it buzzes: higher rows buzz harder, but you can play without it.",
                    color = TileInk.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = TileFont
                )

                Spacer(Modifier.height(20.dp))

                TileButton(
                    text = "Play",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                TileButton(
                    text = "Back",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            TilePhase.OVER -> PopupCard {
                Text(
                    text = "GAME OVER",
                    color = TileRed,
                    fontSize = 30.sp,
                    letterSpacing = 5.sp,
                    fontFamily = TileFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Rounds cleared ${(world.round - 1).coerceAtLeast(0)}",
                    color = TileInk,
                    fontSize = 22.sp,
                    fontFamily = TileFont
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Score ${world.score}",
                    color = TileDim,
                    fontSize = 18.sp,
                    fontFamily = TileFont
                )

                Text(
                    text = if (newBest) "New best!" else "Best $best",
                    color = if (newBest) TileAmber else TileDim,
                    fontSize = 18.sp,
                    fontFamily = TileFont
                )

                Spacer(Modifier.height(20.dp))

                TileButton(
                    text = "Play again",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                TileButton(
                    text = "Menu",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            TilePhase.SHOWING, TilePhase.INPUT -> Unit
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
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
                .clip(shape)
                .background(TilePanel)
                .border(3.dp, TileAmber, shape)
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun TileButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (filled) TileAmber else Color.Transparent)
            .border(2.dp, if (filled) TileAmber else TileAmber.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = if (filled) Color.White else TileInk,
            fontSize = 17.sp,
            letterSpacing = 4.sp,
            maxLines = 1,
            fontFamily = TileFont
        )
    }
}