package com.example.pulsemate.games.reel

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
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import kotlinx.coroutines.launch
import kotlin.math.sin

private const val PREFS_NAME = "pulsemate_reelin"
private const val BEST_KEY = "best"

private enum class ReelPhase { START, PLAY, OVER }

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
fun ReelScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { ReelHaptics(loveSpouse) }
    val world = remember { ReelWorld() }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(ReelPhase.START) }
    var paused by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf("") }
    var zoneUi by remember { mutableIntStateOf(1) }
    var holdingUi by remember { mutableStateOf(false) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var livesUi by remember { mutableIntStateOf(ReelWorld.LIVES) }
    var levelUi by remember { mutableIntStateOf(1) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var bestAtStart by remember { mutableIntStateOf(best) }
    var newBest by remember { mutableStateOf(false) }

    fun sync() {
        scoreUi = world.score
        livesUi = world.lives.coerceAtLeast(0)
        levelUi = world.level

        if (world.score > best) {
            best = world.score
            saveBest(context, best)
        }
    }

    fun startGame() {
        world.start()
        haptics.reset()
        bestAtStart = best
        newBest = false
        banner = ""
        sync()
        phase = ReelPhase.PLAY
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
                world.reeling = false
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
        var buzzTimer = 0f

        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now

            if (phase == ReelPhase.PLAY && !paused) {
                world.update(seconds)

                for (e in world.events) {
                    when (e) {
                        ReelEvent.TUG -> haptics.pulse(scope, 6, 90L)

                        ReelEvent.CATCH -> {
                            haptics.pulse(scope, 8, 500L)
                            banner = "CAUGHT!"
                            sync()

                            scope.launch {
                                delay(1500L)
                                world.nextFish()
                                banner = ""
                            }
                        }

                        ReelEvent.SNAP -> {
                            haptics.pulse(scope, 9, 700L)
                            banner = "LINE SNAPPED"
                            sync()

                            scope.launch {
                                delay(1600L)

                                if (world.lives <= 0) {
                                    newBest = best > bestAtStart
                                    phase = ReelPhase.OVER
                                } else {
                                    world.newFish()
                                    banner = ""
                                }
                            }
                        }

                        ReelEvent.ESCAPE -> {
                            haptics.pulse(scope, 4, 500L)
                            banner = "IT GOT AWAY"
                            sync()

                            scope.launch {
                                delay(1600L)

                                if (world.lives <= 0) {
                                    newBest = best > bestAtStart
                                    phase = ReelPhase.OVER
                                } else {
                                    world.newFish()
                                    banner = ""
                                }
                            }
                        }
                    }
                }

                world.events.clear()

                zoneUi = world.zone()
                holdingUi = world.reeling

                buzzTimer += seconds

                if (buzzTimer >= 0.18f) {
                    buzzTimer = 0f

                    val level = world.hapticLevel()

                    if (level > 0 && world.state == ReelState.FIGHTING) haptics.pulse(scope, level, 100L)
                }
            }

            frame++
        }
    }

    val statusText = when {
        banner.isNotEmpty() -> banner
        phase != ReelPhase.PLAY -> ""
        zoneUi == 0 -> "SLACK  REEL"
        zoneUi == 1 -> "GOOD"
        zoneUi == 2 -> "EASE OFF"
        else -> "LET GO"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ReelBgTop, ReelBgBottom)))
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "REEL IN",
                color = ReelDim,
                fontSize = 15.sp,
                letterSpacing = 5.sp,
                fontFamily = ReelFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = "$scoreUi",
                color = ReelInk,
                fontSize = 38.sp,
                fontFamily = ReelFont,
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
                    text = "FISH $levelUi",
                    color = ReelDim,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    fontFamily = ReelFont
                )

                Text(
                    text = "●".repeat(livesUi) + "○".repeat((ReelWorld.LIVES - livesUi).coerceAtLeast(0)),
                    color = ReelAccent,
                    fontSize = 18.sp,
                    letterSpacing = 4.sp
                )

                Text(
                    text = "BEST $best",
                    color = ReelDim,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    fontFamily = ReelFont
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
                        .aspectRatio(0.8f)
                        .clip(RoundedCornerShape(20.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()

                                if (phase == ReelPhase.PLAY && !paused) world.reeling = true

                                waitForUpOrCancellation()

                                world.reeling = false
                            }
                        }
                ) {
                    val tick = frame

                    if (tick >= 0) drawScene(world)
                }
            }

            Text(
                text = statusText,
                color = when (zoneUi) {
                    2 -> ReelAccent
                    3 -> ReelRed
                    else -> ReelInk
                },
                fontSize = 22.sp,
                letterSpacing = 4.sp,
                fontFamily = ReelFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = if (holdingUi) "REELING" else "HOLD THE SEA TO REEL",
                color = ReelDim,
                fontSize = 12.sp,
                letterSpacing = 3.sp,
                fontFamily = ReelFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            ReelButton(
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
            ReelPhase.START -> PopupCard {
                Text(
                    text = "REEL IN",
                    color = ReelAccent,
                    fontSize = 32.sp,
                    letterSpacing = 4.sp,
                    fontFamily = ReelFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Press and hold on the sea to reel the fish in, and let go to ease the line. Keep the pointer on the green part of the gauge: too tight and the line snaps, too loose and the fish slips away. The fish tugs in bursts, so be ready to let go. The buzz follows the line tension, but the gauge shows the same thing, so you can play without it.",
                    color = ReelInk,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = ReelFont
                )

                Spacer(Modifier.height(20.dp))

                ReelButton(
                    text = "Play",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                ReelButton(
                    text = "Back",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            ReelPhase.OVER -> PopupCard {
                Text(
                    text = "GAME OVER",
                    color = ReelRed,
                    fontSize = 30.sp,
                    letterSpacing = 3.sp,
                    fontFamily = ReelFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Score ${world.score}",
                    color = ReelInk,
                    fontSize = 26.sp,
                    fontFamily = ReelFont
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Fish caught ${(world.level - 1).coerceAtLeast(0)}",
                    color = ReelDim,
                    fontSize = 18.sp,
                    fontFamily = ReelFont
                )

                Text(
                    text = if (newBest) "New best!" else "Best $best",
                    color = if (newBest) ReelAccent else ReelDim,
                    fontSize = 18.sp,
                    fontFamily = ReelFont
                )

                Spacer(Modifier.height(20.dp))

                ReelButton(
                    text = "Play again",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                ReelButton(
                    text = "Menu",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            ReelPhase.PLAY -> Unit
        }
    }
}

private fun DrawScope.drawScene(world: ReelWorld) {
    val w = size.width
    val h = size.height
    val waterTop = h * 0.2f

    drawRect(Brush.verticalGradient(listOf(ReelSkyTop, ReelSkyBottom), startY = 0f, endY = waterTop))
    drawCircle(Color(0xFFFFF3B0), w * 0.07f, Offset(w * 0.8f, h * 0.08f))
    drawRect(Brush.verticalGradient(listOf(ReelWaterTop, ReelWaterBottom), startY = waterTop, endY = h), Offset(0f, waterTop), Size(w, h - waterTop))

    for (k in 0 until 3) {
        val path = Path().apply {
            moveTo(0f, waterTop + k * h * 0.02f)

            for (i in 1..30) {
                lineTo(w * i / 30f, waterTop + k * h * 0.02f + sin(i * 0.9f + world.time * 2f + k) * 4f)
            }
        }

        drawPath(path, Color.White.copy(alpha = 0.35f - k * 0.08f), style = Stroke(width = 3f, cap = StrokeCap.Round))
    }

    for (k in 0 until 6) {
        val bx = w * (0.15f + k * 0.14f)
        val by = h - ((world.time * (30f + k * 9f) + k * 97f) % (h - waterTop))

        drawCircle(Color.White.copy(alpha = 0.25f), 4f + k % 3 * 2f, Offset(bx, by))
    }

    val hull = Path().apply {
        moveTo(w * 0.16f, waterTop - h * 0.05f)
        lineTo(w * 0.5f, waterTop - h * 0.05f)
        lineTo(w * 0.44f, waterTop + h * 0.015f)
        lineTo(w * 0.22f, waterTop + h * 0.015f)
        close()
    }

    drawPath(hull, ReelBoat)
    drawPath(hull, ReelInk, style = Stroke(width = 3f))
    drawRoundRect(Color(0xFFFFF3E0), Offset(w * 0.2f, waterTop - h * 0.11f), Size(w * 0.12f, h * 0.06f), CornerRadius(8f, 8f))
    drawRoundRect(ReelInk, Offset(w * 0.2f, waterTop - h * 0.11f), Size(w * 0.12f, h * 0.06f), CornerRadius(8f, 8f), style = Stroke(width = 3f))

    val tip = Offset(w * 0.56f, waterTop - h * 0.14f)

    drawLine(ReelInk, Offset(w * 0.42f, waterTop - h * 0.05f), tip, 5f, StrokeCap.Round)

    val thrash = if (world.pull > 0.05f) 0.14f else 0.04f
    val fx = w * 0.55f + sin(world.time * 9f) * w * thrash
    val fy = waterTop + h * 0.1f + world.depth * h * 0.62f
    val fishColor = ReelFishColors[(world.level - 1) % ReelFishColors.size]

    val lineColor = when (world.zone()) {
        0 -> Color.White.copy(alpha = 0.7f)
        1 -> Color.White
        2 -> ReelWarn
        else -> ReelDanger
    }

    drawLine(lineColor, tip, Offset(fx - w * 0.07f, fy), if (world.zone() >= 2) 5f else 3f, StrokeCap.Round)

    val body = w * (0.15f + 0.01f * (world.level % 5))
    val bodyH = body * 0.55f
    val tail = Path().apply {
        moveTo(fx + body * 0.4f, fy)
        lineTo(fx + body * 0.8f, fy - bodyH * 0.55f)
        lineTo(fx + body * 0.8f, fy + bodyH * 0.55f)
        close()
    }

    drawPath(tail, fishColor)
    drawPath(tail, ReelInk, style = Stroke(width = 3f))
    drawOval(fishColor, Offset(fx - body / 2f, fy - bodyH / 2f), Size(body, bodyH))
    drawOval(ReelInk, Offset(fx - body / 2f, fy - bodyH / 2f), Size(body, bodyH), style = Stroke(width = 3f))
    drawOval(Color.White.copy(alpha = 0.3f), Offset(fx - body * 0.3f, fy - bodyH * 0.3f), Size(body * 0.5f, bodyH * 0.25f))
    drawCircle(Color.White, bodyH * 0.13f, Offset(fx - body * 0.3f, fy - bodyH * 0.1f))
    drawCircle(ReelInk, bodyH * 0.06f, Offset(fx - body * 0.32f, fy - bodyH * 0.1f))

    val gx = w * 0.9f
    val gTop = h * 0.3f
    val gBottom = h * 0.88f
    val gH = gBottom - gTop
    val gW = w * 0.05f

    fun segment(from: Float, to: Float, color: Color) {
        drawRect(color, Offset(gx - gW / 2f, gBottom - gH * to), Size(gW, gH * (to - from)))
    }

    segment(0f, ReelWorld.SAFE_LOW, ReelLow)
    segment(ReelWorld.SAFE_LOW, ReelWorld.SAFE_HIGH, ReelSafe)
    segment(ReelWorld.SAFE_HIGH, ReelWorld.WARN_HIGH, ReelWarn)
    segment(ReelWorld.WARN_HIGH, 1f, ReelDanger)

    drawRect(ReelInk, Offset(gx - gW / 2f, gTop), Size(gW, gH), style = Stroke(width = 3f))

    val my = gBottom - gH * world.tension
    val marker = Path().apply {
        moveTo(gx - gW / 2f - 4f, my)
        lineTo(gx - gW / 2f - w * 0.035f, my - 9f)
        lineTo(gx - gW / 2f - w * 0.035f, my + 9f)
        close()
    }

    drawPath(marker, Color.White)
    drawPath(marker, ReelInk, style = Stroke(width = 2f))
    drawLine(Color.White, Offset(gx - gW / 2f, my), Offset(gx + gW / 2f, my), 4f)

    val px = w * 0.07f

    drawRect(Color.White.copy(alpha = 0.3f), Offset(px - gW / 2f, gTop), Size(gW, gH))
    drawRect(ReelAccent, Offset(px - gW / 2f, gBottom - gH * (1f - world.depth)), Size(gW, gH * (1f - world.depth)))
    drawRect(ReelInk, Offset(px - gW / 2f, gTop), Size(gW, gH), style = Stroke(width = 3f))
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
                .background(ReelPanel)
                .border(3.dp, ReelInk, shape)
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun ReelButton(
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
            .background(if (filled) ReelAccent else Color.Transparent)
            .border(2.5.dp, ReelInk, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = if (filled) Color.White else ReelInk,
            fontSize = 17.sp,
            letterSpacing = 3.sp,
            maxLines = 1,
            fontFamily = ReelFont
        )
    }
}