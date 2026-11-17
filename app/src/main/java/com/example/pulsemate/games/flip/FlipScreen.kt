package com.example.pulsemate.games.flip

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.cos
import kotlin.math.sin

private const val PREFS_NAME = "pulsemate_gravityflip"
private const val BEST_KEY = "best"

private enum class FlipPhase { START, PLAY, OVER }

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
fun FlipScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { FlipHaptics(loveSpouse) }
    val world = remember { FlipWorld() }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(FlipPhase.START) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var gemsUi by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var bestAtStart by remember { mutableIntStateOf(best) }
    var newBest by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }

    fun startGame() {
        world.start()
        haptics.reset()
        bestAtStart = best
        newBest = false
        scoreUi = 0
        gemsUi = 0
        phase = FlipPhase.PLAY
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
            if (event == Lifecycle.Event.ON_PAUSE) {
                paused = true
                haptics.stop()
            }

            if (event == Lifecycle.Event.ON_RESUME) paused = false
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
        var warnTimer = 0f

        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now

            if (phase == FlipPhase.PLAY && !paused) {
                world.update(seconds)

                for (e in world.events) {
                    when (e) {
                        FlipEvent.FLIP -> haptics.pulse(scope, 3, 60L)
                        FlipEvent.LAND -> haptics.pulse(scope, 4, 70L)
                        FlipEvent.GEM -> haptics.pulse(scope, 6, 120L)

                        FlipEvent.DEAD -> {
                            haptics.pulse(scope, 9, 900L)
                            newBest = world.score() > bestAtStart
                            phase = FlipPhase.OVER
                        }
                    }
                }

                world.events.clear()

                scoreUi = world.score()
                gemsUi = world.gemCount

                if (scoreUi > best) {
                    best = scoreUi
                    saveBest(context, best)
                }

                warnTimer += seconds

                if (warnTimer >= 0.28f) {
                    warnTimer = 0f

                    val level = world.warnLevel()

                    if (level > 0 && !world.dead) haptics.pulse(scope, level, 120L)
                }
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FlipBgTop, FlipBgBottom)))
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GRAVITY FLIP",
                color = FlipDim,
                fontSize = 14.sp,
                letterSpacing = 5.sp,
                fontFamily = FlipFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "$scoreUi",
                color = FlipInk,
                fontSize = 40.sp,
                fontFamily = FlipFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "GEMS $gemsUi",
                    color = FlipDim,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                    fontFamily = FlipFont
                )

                Text(
                    text = "BEST $best",
                    color = FlipDim,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                    fontFamily = FlipFont
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
                        .aspectRatio(FlipWorld.W / (FlipWorld.H + FlipWorld.BAND * 2f))
                        .clip(RoundedCornerShape(18.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()

                                down.consume()

                                if (phase == FlipPhase.PLAY && !paused) world.flip()
                            }
                        }
                ) {
                    val tick = frame

                    if (tick >= 0) drawWorld(world, tick)
                }
            }

            Text(
                text = "TAP TO FLIP GRAVITY",
                color = FlipDim,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                fontFamily = FlipFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            FlipButton(
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
            FlipPhase.START -> PopupCard {
                Text(
                    text = "GRAVITY FLIP",
                    color = FlipCoral,
                    fontSize = 30.sp,
                    letterSpacing = 3.sp,
                    fontFamily = FlipFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "You run through the tunnel. Tap anywhere to flip gravity between the floor and the ceiling. Dodge pink spikes, yellow blocks and red mines, and grab the gems. It gets faster. The buzz grows as something gets close, but you can see everything coming, so you can play without it.",
                    color = FlipInk,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FlipFont
                )

                Spacer(Modifier.height(20.dp))

                FlipButton(
                    text = "Play",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                FlipButton(
                    text = "Back",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            FlipPhase.OVER -> PopupCard {
                Text(
                    text = "CRASH",
                    color = FlipRed,
                    fontSize = 32.sp,
                    letterSpacing = 4.sp,
                    fontFamily = FlipFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Score ${world.score()}",
                    color = FlipInk,
                    fontSize = 26.sp,
                    fontFamily = FlipFont
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Gems ${world.gemCount}",
                    color = FlipDim,
                    fontSize = 18.sp,
                    fontFamily = FlipFont
                )

                Text(
                    text = if (newBest) "New best!" else "Best $best",
                    color = if (newBest) FlipCoral else FlipDim,
                    fontSize = 18.sp,
                    fontFamily = FlipFont
                )

                Spacer(Modifier.height(20.dp))

                FlipButton(
                    text = "Play again",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                FlipButton(
                    text = "Menu",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            FlipPhase.PLAY -> Unit
        }
    }
}

private fun DrawScope.drawWorld(world: FlipWorld, tick: Int) {
    val scale = size.width / FlipWorld.W
    val band = FlipWorld.BAND

    fun sx(x: Float): Float = x * scale
    fun sy(y: Float): Float = (band + y) * scale

    drawRect(Brush.verticalGradient(listOf(FlipSkyTop, FlipSkyBottom)))

    val loop = FlipWorld.W + 4f

    for (i in 0 until 5) {
        val raw = i * 5.3f - world.scroll * 0.25f
        val x = ((raw % loop) + loop) % loop - 2f
        val y = 2f + (i * 37 % 9)

        drawCircle(Color.White.copy(alpha = 0.3f), scale * (0.9f + (i % 3) * 0.3f), Offset(sx(x), sy(y)))
        drawCircle(Color.White.copy(alpha = 0.3f), scale * 0.7f, Offset(sx(x + 1f), sy(y + 0.2f)))
    }

    for (g in world.gems) {
        val cx = sx(g.x)
        val cy = sy(g.y)
        val r = scale * (0.4f + 0.05f * sin(tick * 0.15f))
        val path = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r * 0.8f, cy)
            lineTo(cx, cy + r)
            lineTo(cx - r * 0.8f, cy)
            close()
        }

        drawPath(path, FlipGem)
        drawPath(path, FlipGemEdge, style = Stroke(width = 3f))
    }

    for (o in world.obstacles) {
        val x = sx(o.x)
        val w = o.w * scale

        when (o.kind) {
            ObstacleKind.SPIKE_FLOOR, ObstacleKind.SPIKE_CEIL -> {
                val floor = o.kind == ObstacleKind.SPIKE_FLOOR
                val baseY = if (floor) sy(FlipWorld.H) else sy(0f)
                val tipY = if (floor) sy(FlipWorld.H - FlipWorld.SPIKE_H) else sy(FlipWorld.SPIKE_H)
                val half = w / 2f

                for (k in 0 until 2) {
                    val path = Path().apply {
                        moveTo(x + k * half, baseY)
                        lineTo(x + k * half + half / 2f, tipY)
                        lineTo(x + (k + 1) * half, baseY)
                        close()
                    }

                    drawPath(path, FlipSpike)
                    drawPath(path, FlipInk, style = Stroke(width = 3f))
                }
            }

            ObstacleKind.BLOCK_FLOOR, ObstacleKind.BLOCK_CEIL -> {
                val floor = o.kind == ObstacleKind.BLOCK_FLOOR
                val top = if (floor) sy(FlipWorld.H - FlipWorld.BLOCK_H) else sy(0f)
                val h = FlipWorld.BLOCK_H * scale
                val corner = CornerRadius(scale * 0.2f, scale * 0.2f)

                drawRoundRect(FlipBlock, Offset(x, top), Size(w, h), corner)

                for (k in 0 until 3) {
                    drawLine(
                        color = FlipInk.copy(alpha = 0.35f),
                        start = Offset(x + w * 0.1f, top + h * (0.2f + k * 0.3f)),
                        end = Offset(x + w * 0.9f, top + h * (0.2f + k * 0.3f)),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }

                drawRoundRect(FlipInk, Offset(x, top), Size(w, h), corner, style = Stroke(width = 3f))
            }

            ObstacleKind.MINE -> {
                val cx = x + w / 2f
                val cy = sy(o.y)
                val r = scale * 0.45f

                for (k in 0 until 8) {
                    val a = k * Math.PI.toFloat() / 4f + tick * 0.03f

                    drawLine(
                        color = FlipInk,
                        start = Offset(cx + cos(a) * r * 0.9f, cy + sin(a) * r * 0.9f),
                        end = Offset(cx + cos(a) * r * 1.4f, cy + sin(a) * r * 1.4f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                }

                drawCircle(FlipMine, r, Offset(cx, cy))
                drawCircle(FlipInk, r, Offset(cx, cy), style = Stroke(width = 3f))
                drawCircle(Color.White.copy(alpha = 0.5f), r * 0.25f, Offset(cx - r * 0.3f, cy - r * 0.3f))
            }
        }
    }

    val bandH = band * scale

    drawRect(FlipBand, Offset(0f, 0f), Size(size.width, bandH))
    drawRect(FlipBand, Offset(0f, sy(FlipWorld.H)), Size(size.width, bandH))
    drawRect(FlipBandEdge, Offset(0f, bandH - scale * 0.15f), Size(size.width, scale * 0.15f))
    drawRect(FlipBandEdge, Offset(0f, sy(FlipWorld.H)), Size(size.width, scale * 0.15f))

    val dash = 1.4f
    val offset = world.scroll % dash

    var d = -dash

    while (d < FlipWorld.W + dash) {
        val x = sx(d - offset)

        drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(x, bandH * 0.4f), Size(scale * 0.7f, bandH * 0.18f), CornerRadius(4f, 4f))
        drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(x, sy(FlipWorld.H) + bandH * 0.42f), Size(scale * 0.7f, bandH * 0.18f), CornerRadius(4f, 4f))

        d += dash
    }

    val px = sx(FlipWorld.PX)
    val py = sy(world.cy)
    val half = FlipWorld.HALF * scale
    val up = -world.gravDir.toFloat()

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.18f),
        topLeft = Offset(px - half + 3f, py - half + 4f),
        size = Size(half * 2f, half * 2f),
        cornerRadius = CornerRadius(half * 0.45f, half * 0.45f)
    )
    drawRoundRect(
        color = if (world.dead) FlipRed else FlipCoral,
        topLeft = Offset(px - half, py - half),
        size = Size(half * 2f, half * 2f),
        cornerRadius = CornerRadius(half * 0.45f, half * 0.45f)
    )
    drawRoundRect(
        color = FlipInk,
        topLeft = Offset(px - half, py - half),
        size = Size(half * 2f, half * 2f),
        cornerRadius = CornerRadius(half * 0.45f, half * 0.45f),
        style = Stroke(width = 3f)
    )

    for (side in listOf(-1f, 1f)) {
        val ex = px + half * 0.2f + side * half * 0.38f
        val ey = py + up * half * 0.18f

        drawCircle(Color.White, half * 0.3f, Offset(ex, ey))
        drawCircle(FlipInk, half * 0.15f, Offset(ex + half * 0.1f, ey))
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
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
                .background(FlipPanel)
                .border(3.dp, FlipInk, shape)
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun FlipButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (filled) FlipCoral else Color.Transparent)
            .border(2.5.dp, FlipInk, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = if (filled) Color.White else FlipInk,
            fontSize = 17.sp,
            letterSpacing = 3.sp,
            maxLines = 1,
            fontFamily = FlipFont
        )
    }
}