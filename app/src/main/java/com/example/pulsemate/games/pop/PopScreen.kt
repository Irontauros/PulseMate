package com.example.pulsemate.games.pop

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pulsemate.haptics.LoveSpouseController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS_NAME = "pulsemate_chainpop"
private const val BEST_KEY = "best"

private enum class PopPhase { START, PLAY, OVER }

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
fun PopScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { PopHaptics(loveSpouse) }
    val world = remember { PopWorld() }
    val fx = remember { FloatArray(PopWorld.COUNT) }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(PopPhase.START) }
    var busy by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var scoreUi by remember { mutableIntStateOf(0) }
    var livesUi by remember { mutableIntStateOf(PopWorld.LIVES) }
    var levelUi by remember { mutableIntStateOf(1) }
    var tapsUi by remember { mutableIntStateOf(PopWorld.TAPS) }
    var poppedUi by remember { mutableIntStateOf(0) }
    var targetUi by remember { mutableIntStateOf(0) }
    var chainUi by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var bestAtStart by remember { mutableIntStateOf(best) }
    var newBest by remember { mutableStateOf(false) }

    fun sync() {
        scoreUi = world.score
        livesUi = world.lives.coerceAtLeast(0)
        levelUi = world.level
        tapsUi = world.tapsLeft
        poppedUi = world.popped
        targetUi = world.target
        chainUi = world.wave

        if (world.score > best) {
            best = world.score
            saveBest(context, best)
        }
    }

    fun finishChain() {
        sync()

        when (world.resolve()) {
            PopResult.CONTINUE -> {
                status = "Pop ${(world.target - world.popped).coerceAtLeast(0)} more"
                busy = false
            }

            PopResult.CLEAR -> {
                haptics.pulse(scope, 8, 400L)
                status = "Level cleared"
                sync()

                scope.launch {
                    delay(1100L)
                    world.advanceLevel()
                    sync()
                    status = "Tap a bubble"
                    busy = false
                }
            }

            PopResult.FAIL -> {
                haptics.pulse(scope, 5, 600L)
                status = "Not enough pops"
                sync()

                scope.launch {
                    delay(1100L)

                    if (world.lives <= 0) {
                        newBest = best > bestAtStart
                        phase = PopPhase.OVER
                    } else {
                        world.retryBoard()
                        sync()
                        status = "Tap a bubble"
                        busy = false
                    }
                }
            }
        }
    }

    fun tapCell(i: Int) {
        if (phase != PopPhase.PLAY || busy) return

        if (!world.tap(i)) return

        busy = true
        haptics.pulse(scope, 3, 60L)
        sync()

        if (!world.chainActive) finishChain()
    }

    fun startGame() {
        world.start()
        haptics.reset()
        bestAtStart = best
        newBest = false
        sync()
        status = "Tap a bubble"
        busy = false
        phase = PopPhase.PLAY
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
            loveSpouse.deviceProblem()
            delay(1_000)
        }
    }

    LaunchedEffect(world) {
        var last = withFrameNanos { it }
        var waveTimer = 0f

        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now

            for (i in 0 until PopWorld.COUNT) {
                if (fx[i] > 0f) fx[i] = (fx[i] - seconds * 3f).coerceAtLeast(0f)
            }

            if (phase == PopPhase.PLAY && busy && world.chainActive) {
                waveTimer += seconds

                if (waveTimer >= 0.16f) {
                    waveTimer = 0f

                    val popList = world.stepWave()

                    for (p in popList) fx[p] = 1f

                    haptics.pulse(scope, (2 + world.wave).coerceAtMost(9), 110L)
                    sync()

                    if (!world.chainActive) finishChain()
                }
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PopBgTop, PopBgBottom)))
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CHAIN POP",
                color = PopDim,
                fontSize = 15.sp,
                letterSpacing = 5.sp,
                fontFamily = PopFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = "$scoreUi",
                color = PopInk,
                fontSize = 38.sp,
                fontFamily = PopFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LEVEL $levelUi",
                    color = PopDim,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    fontFamily = PopFont
                )

                Text(
                    text = "●".repeat(livesUi) + "○".repeat((PopWorld.LIVES - livesUi).coerceAtLeast(0)),
                    color = PopRed,
                    fontSize = 18.sp,
                    letterSpacing = 4.sp
                )

                Text(
                    text = "BEST $best",
                    color = PopDim,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    fontFamily = PopFont
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "POPPED $poppedUi / $targetUi",
                    color = PopAccent,
                    fontSize = 18.sp,
                    letterSpacing = 2.sp,
                    fontFamily = PopFont
                )

                Text(
                    text = "TAPS $tapsUi",
                    color = PopInk,
                    fontSize = 18.sp,
                    letterSpacing = 2.sp,
                    fontFamily = PopFont
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(contentAlignment = Alignment.TopCenter) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(PopWorld.COLS.toFloat() / PopWorld.ROWS.toFloat())
                            .clip(RoundedCornerShape(22.dp))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()

                                    down.consume()

                                    val cell = size.width / PopWorld.COLS.toFloat()
                                    val col = (down.position.x / cell).toInt()
                                    val row = (down.position.y / cell).toInt()

                                    if (col in 0 until PopWorld.COLS && row in 0 until PopWorld.ROWS) {
                                        tapCell(row * PopWorld.COLS + col)
                                    }
                                }
                            }
                    ) {
                        val tick = frame

                        if (tick >= 0) drawBoard(world, fx)
                    }
                }
            }

            Text(
                text = if (chainUi > 1 && busy) "CHAIN x$chainUi" else status.uppercase(),
                color = PopAccent,
                fontSize = 20.sp,
                letterSpacing = 4.sp,
                fontFamily = PopFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            PopButton(
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
            PopPhase.START -> PopupCard {
                Text(
                    text = "CHAIN POP",
                    color = PopAccent,
                    fontSize = 32.sp,
                    letterSpacing = 3.sp,
                    fontFamily = PopFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Tap a bubble to make it grow. A big bubble that grows again bursts and pushes its neighbors one size up, which can burst them too. You only get 3 taps per board, so pop at least the target number. Each wave of the chain is worth more points, and the buzz grows with the wave, but you can see the chain on screen, so you can play without it.",
                    color = PopInk,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = PopFont
                )

                Spacer(Modifier.height(20.dp))

                PopButton(
                    text = "Play",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                PopButton(
                    text = "Back",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            PopPhase.OVER -> PopupCard {
                Text(
                    text = "GAME OVER",
                    color = PopRed,
                    fontSize = 30.sp,
                    letterSpacing = 3.sp,
                    fontFamily = PopFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Score ${world.score}",
                    color = PopInk,
                    fontSize = 26.sp,
                    fontFamily = PopFont
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Reached level ${world.level}",
                    color = PopDim,
                    fontSize = 18.sp,
                    fontFamily = PopFont
                )

                Text(
                    text = if (newBest) "New best!" else "Best $best",
                    color = if (newBest) PopAccent else PopDim,
                    fontSize = 18.sp,
                    fontFamily = PopFont
                )

                Spacer(Modifier.height(20.dp))

                PopButton(
                    text = "Play again",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                PopButton(
                    text = "Menu",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            PopPhase.PLAY -> Unit
        }
    }
}

private fun DrawScope.drawBoard(world: PopWorld, fx: FloatArray) {
    val cell = size.width / PopWorld.COLS

    drawRect(Color.White.copy(alpha = 0.55f))

    for (i in 0 until PopWorld.COUNT) {
        val cx = (i % PopWorld.COLS + 0.5f) * cell
        val cy = (i / PopWorld.COLS + 0.5f) * cell
        val level = world.cells[i]

        if (level <= 0) {
            drawCircle(PopLine, cell * 0.07f, Offset(cx, cy))
        } else {
            val shown = level.coerceAtMost(4)
            val r = cell * (0.17f + 0.09f * shown).coerceAtMost(0.47f)
            val color = PopBubbleColors[shown - 1]

            drawCircle(Color.Black.copy(alpha = 0.1f), r, Offset(cx + 2f, cy + 4f))
            drawCircle(color, r, Offset(cx, cy))
            drawCircle(PopInk.copy(alpha = 0.55f), r, Offset(cx, cy), style = Stroke(width = 3f))
            drawCircle(Color.White.copy(alpha = 0.55f), r * 0.22f, Offset(cx - r * 0.38f, cy - r * 0.42f))

            val pip = cell * 0.045f
            val span = (shown - 1) * pip * 2.6f

            for (k in 0 until shown) {
                drawCircle(
                    Color.White.copy(alpha = 0.9f),
                    pip,
                    Offset(cx - span / 2f + k * pip * 2.6f, cy + r * 0.35f)
                )
            }
        }

        if (fx[i] > 0f) {
            val grow = 1f - fx[i]

            drawCircle(
                color = PopAccent.copy(alpha = fx[i]),
                radius = cell * (0.3f + 0.7f * grow),
                center = Offset(cx, cy),
                style = Stroke(width = 5f)
            )
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
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
                .background(PopPanel)
                .border(3.dp, PopAccent, shape)
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun PopButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (filled) PopAccent else Color.Transparent)
            .border(2.5.dp, if (filled) PopAccent else PopInk.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = if (filled) Color.White else PopInk,
            fontSize = 17.sp,
            letterSpacing = 3.sp,
            maxLines = 1,
            fontFamily = PopFont
        )
    }
}