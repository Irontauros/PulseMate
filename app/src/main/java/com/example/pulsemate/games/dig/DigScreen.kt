package com.example.pulsemate.games.dig

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
import androidx.compose.foundation.layout.size
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

private const val PREFS_NAME = "pulsemate_treasuredig"
private const val BEST_KEY = "best"

private enum class DigPhase { START, PLAY, OVER }

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
fun DigScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { DigHaptics(loveSpouse) }
    val world = remember { DigWorld() }
    val puff = remember { FloatArray(DigWorld.COUNT) }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(DigPhase.START) }
    var busy by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var statusBand by remember { mutableIntStateOf(-1) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var levelUi by remember { mutableIntStateOf(1) }
    var digsUi by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var bestAtStart by remember { mutableIntStateOf(best) }
    var newBest by remember { mutableStateOf(false) }

    fun sync() {
        scoreUi = world.score
        levelUi = world.level
        digsUi = world.digsLeft

        if (world.score > best) {
            best = world.score
            saveBest(context, best)
        }
    }

    fun tapCell(i: Int) {
        if (phase != DigPhase.PLAY || busy) return

        val result = world.dig(i)

        if (result < 0) return

        puff[i] = 1f
        sync()

        when {
            result == DigWorld.FOUND -> {
                busy = true
                status = "Treasure!"
                statusBand = 4
                haptics.pulse(scope, 9, 700L)

                scope.launch {
                    delay(1500L)
                    world.nextLevel()
                    sync()
                    status = "Dig somewhere"
                    statusBand = -1
                    busy = false
                }
            }

            world.failed -> {
                busy = true
                status = "Out of digs"
                statusBand = result
                haptics.pulse(scope, 4, 500L)

                scope.launch {
                    delay(1800L)
                    newBest = best > bestAtStart
                    phase = DigPhase.OVER
                }
            }

            else -> {
                status = DigBandNames[result]
                statusBand = result

                val level = intArrayOf(1, 3, 5, 7, 9)[result]
                val millis = longArrayOf(110L, 220L, 300L, 380L, 480L)[result]

                haptics.pulse(scope, level, millis)
            }
        }
    }

    fun startGame() {
        world.start()
        haptics.reset()
        bestAtStart = best
        newBest = false
        sync()
        status = "Dig somewhere"
        statusBand = -1
        busy = false
        phase = DigPhase.PLAY
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

        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now

            for (i in 0 until DigWorld.COUNT) {
                if (puff[i] > 0f) puff[i] = (puff[i] - seconds * 2.2f).coerceAtLeast(0f)
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DigBgTop, DigBgBottom)))
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TREASURE DIG",
                color = DigDim,
                fontSize = 15.sp,
                letterSpacing = 4.sp,
                fontFamily = DigFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = "$scoreUi",
                color = DigInk,
                fontSize = 38.sp,
                fontFamily = DigFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "LEVEL $levelUi",
                    color = DigDim,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    fontFamily = DigFont
                )

                Text(
                    text = "DIGS $digsUi",
                    color = DigInk,
                    fontSize = 17.sp,
                    letterSpacing = 2.sp,
                    fontFamily = DigFont
                )

                Text(
                    text = "BEST $best",
                    color = DigDim,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    fontFamily = DigFont
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(DigWorld.COLS.toFloat() / DigWorld.ROWS.toFloat())
                        .clip(RoundedCornerShape(18.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()

                                down.consume()

                                val cell = size.width / DigWorld.COLS.toFloat()
                                val col = (down.position.x / cell).toInt()
                                val row = (down.position.y / cell).toInt()

                                if (col in 0 until DigWorld.COLS && row in 0 until DigWorld.ROWS) {
                                    tapCell(row * DigWorld.COLS + col)
                                }
                            }
                        }
                ) {
                    val tick = frame

                    if (tick >= 0) drawField(world, puff)
                }
            }

            Text(
                text = status.uppercase(),
                color = if (statusBand >= 0) DigInk else DigDim,
                fontSize = 22.sp,
                letterSpacing = 4.sp,
                fontFamily = DigFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (b in 0 until 5) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(DigBandColors[b])
                                .border(1.5.dp, DigInk.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        )

                        Text(
                            text = " ${DigBandNames[b]}",
                            color = DigInk,
                            fontSize = 10.sp,
                            fontFamily = DigFont
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            DigButton(
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
            DigPhase.START -> PopupCard {
                Text(
                    text = "TREASURE DIG",
                    color = DigRed,
                    fontSize = 30.sp,
                    letterSpacing = 3.sp,
                    fontFamily = DigFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "A chest is buried somewhere in the sand. Tap a square to dig. Each hole shows how close the chest is by color and dots, from cold to burning. The buzz gets stronger the closer you are, but the colors tell you the same thing, so you can play without it. Find the chest before you run out of digs.",
                    color = DigInk,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = DigFont
                )

                Spacer(Modifier.height(20.dp))

                DigButton(
                    text = "Play",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                DigButton(
                    text = "Back",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            DigPhase.OVER -> PopupCard {
                Text(
                    text = "GAME OVER",
                    color = DigRed,
                    fontSize = 30.sp,
                    letterSpacing = 3.sp,
                    fontFamily = DigFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Score ${world.score}",
                    color = DigInk,
                    fontSize = 26.sp,
                    fontFamily = DigFont
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Reached level ${world.level}",
                    color = DigDim,
                    fontSize = 18.sp,
                    fontFamily = DigFont
                )

                Text(
                    text = if (newBest) "New best!" else "Best $best",
                    color = if (newBest) DigRed else DigDim,
                    fontSize = 18.sp,
                    fontFamily = DigFont
                )

                Spacer(Modifier.height(20.dp))

                DigButton(
                    text = "Play again",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                DigButton(
                    text = "Menu",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            DigPhase.PLAY -> Unit
        }
    }
}

private fun DrawScope.drawChest(cx: Float, cy: Float, w: Float, alpha: Float) {
    val h = w * 0.7f
    val left = cx - w / 2f
    val top = cy - h / 2f

    drawRoundRect(DigChest.copy(alpha = alpha), Offset(left, top + h * 0.3f), Size(w, h * 0.7f), CornerRadius(w * 0.1f, w * 0.1f))
    drawRoundRect(Color(0xFFA9703A).copy(alpha = alpha), Offset(left, top), Size(w, h * 0.45f), CornerRadius(w * 0.25f, w * 0.25f))
    drawRect(DigGold.copy(alpha = alpha), Offset(left, top + h * 0.38f), Size(w, h * 0.12f))
    drawCircle(DigGold.copy(alpha = alpha), w * 0.1f, Offset(cx, top + h * 0.5f))
    drawRoundRect(DigInk.copy(alpha = alpha), Offset(left, top), Size(w, h), CornerRadius(w * 0.2f, w * 0.2f), style = Stroke(width = 3f))
}

private fun DrawScope.drawField(world: DigWorld, puff: FloatArray) {
    val cell = size.width / DigWorld.COLS
    val corner = CornerRadius(cell * 0.18f, cell * 0.18f)

    drawRect(Color(0xFFC9975B))

    for (i in 0 until DigWorld.COUNT) {
        val col = i % DigWorld.COLS
        val row = i / DigWorld.COLS
        val x = col * cell
        val y = row * cell
        val cx = x + cell / 2f
        val cy = y + cell / 2f
        val inset = cell * 0.05f
        val side = cell - inset * 2f

        if (!world.dug[i]) {
            drawRoundRect(DigSandEdge, Offset(x + inset, y + inset + 3f), Size(side, side), corner)
            drawRoundRect(DigSand, Offset(x + inset, y + inset), Size(side, side), corner)

            val seed = (i * 7919) % 97

            for (k in 0 until 3) {
                val dx = ((seed * (k + 3)) % 60) / 60f
                val dy = ((seed * (k + 5)) % 50) / 50f

                drawCircle(DigSandDot, cell * 0.04f, Offset(x + cell * (0.22f + 0.56f * dx), y + cell * (0.22f + 0.5f * dy)))
            }

            if (world.failed && !world.found && i == world.treasure) {
                drawChest(cx, cy, cell * 0.6f, 0.9f)
            }
        } else {
            val b = world.band[i]

            drawRoundRect(DigHole, Offset(x + inset, y + inset), Size(side, side), corner)

            if (b == DigWorld.FOUND) {
                drawRoundRect(DigGold.copy(alpha = 0.55f), Offset(x + inset, y + inset), Size(side, side), corner)
                drawChest(cx, cy, cell * 0.66f, 1f)
            } else {
                drawRoundRect(DigBandColors[b], Offset(x + inset, y + inset), Size(side, side), corner)

                val pips = b + 1
                val gap = cell * 0.13f
                val start = cx - (pips - 1) * gap / 2f

                for (k in 0 until pips) {
                    drawCircle(DigInk.copy(alpha = 0.85f), cell * 0.04f, Offset(start + k * gap, cy))
                }
            }

            drawRoundRect(DigInk.copy(alpha = 0.55f), Offset(x + inset, y + inset), Size(side, side), corner, style = Stroke(width = 2.5f))

            if (i == world.lastDug) {
                drawRoundRect(Color.White, Offset(x + inset, y + inset), Size(side, side), corner, style = Stroke(width = 4f))
            }
        }

        if (puff[i] > 0f) {
            val grow = 1f - puff[i]

            drawCircle(
                color = Color.White.copy(alpha = puff[i] * 0.8f),
                radius = cell * (0.25f + 0.55f * grow),
                center = Offset(cx, cy),
                style = Stroke(width = 4f)
            )
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)

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
                .background(DigPanel)
                .border(3.dp, DigInk, shape)
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun DigButton(
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
            .background(if (filled) DigInk else Color.Transparent)
            .border(2.5.dp, DigInk, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = if (filled) DigPanel else DigInk,
            fontSize = 17.sp,
            letterSpacing = 3.sp,
            maxLines = 1,
            fontFamily = DigFont
        )
    }
}