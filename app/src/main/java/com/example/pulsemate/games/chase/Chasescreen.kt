package com.example.pulsemate.games.chase

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
import androidx.compose.ui.draw.drawBehind
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val PREFS_NAME = "pulsemate_seedchase"
private const val BEST_KEY = "best"

private enum class ChasePhase { START, PLAY, OVER }

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
fun ChaseScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { ChaseHaptics(loveSpouse) }
    val world = remember { ChaseWorld() }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(ChasePhase.START) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var livesUi by remember { mutableIntStateOf(ChaseWorld.LIVES) }
    var levelUi by remember { mutableIntStateOf(1) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var bestAtStart by remember { mutableIntStateOf(best) }
    var newBest by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }

    fun startGame() {
        world.start()
        haptics.reset()
        bestAtStart = best
        newBest = false
        scoreUi = 0
        livesUi = world.lives
        levelUi = 1
        phase = ChasePhase.PLAY
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
        var proxTimer = 0f
        var seedCount = 0

        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now

            if (phase == ChasePhase.PLAY && !paused) {
                world.update(seconds)

                for (e in world.events) {
                    when (e) {
                        ChaseEvent.SEED -> {
                            seedCount++
                            if (seedCount % 3 == 0) haptics.pulse(scope, 2, 50L)
                        }

                        ChaseEvent.POWER -> haptics.pulse(scope, 7, 300L)
                        ChaseEvent.EAT_CHASER -> haptics.pulse(scope, 9, 350L)
                        ChaseEvent.DIED -> haptics.pulse(scope, 9, 900L)
                        ChaseEvent.CLEARED -> haptics.pulse(scope, 5, 500L)

                        ChaseEvent.GAME_OVER -> {
                            newBest = best > bestAtStart
                            phase = ChasePhase.OVER
                        }
                    }
                }

                world.events.clear()

                scoreUi = world.score
                livesUi = world.lives.coerceAtLeast(0)
                levelUi = world.level

                if (world.score > best) {
                    best = world.score
                    saveBest(context, best)
                }

                banner = when {
                    world.readyLeft > 0f -> "READY"
                    world.clearLeft > 0f -> "CLEARED"
                    world.dyingLeft > 0f -> "OUCH"
                    else -> ""
                }

                proxTimer += seconds

                if (proxTimer >= 0.32f) {
                    proxTimer = 0f

                    val level = world.proximityLevel()

                    if (level > 0 && world.readyLeft <= 0f && world.dyingLeft <= 0f && world.clearLeft <= 0f) {
                        haptics.pulse(scope, level, 140L)
                    }
                }
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ChaseBgTop, ChaseBgBottom)))
            .drawBehind { drawRetroBackdrop() }
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CHASE",
                color = ChaseDim,
                fontSize = 14.sp,
                letterSpacing = 5.sp,
                fontFamily = ChaseFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "$scoreUi",
                color = ChaseInk,
                fontSize = 38.sp,
                fontFamily = ChaseFont,
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
                    color = ChaseDim,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                    fontFamily = ChaseFont
                )

                Text(
                    text = "●".repeat(livesUi) + "○".repeat((ChaseWorld.LIVES - livesUi).coerceAtLeast(0)),
                    color = ChaseBeetle,
                    fontSize = 18.sp,
                    letterSpacing = 4.sp
                )

                Text(
                    text = "BEST $best",
                    color = ChaseDim,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                    fontFamily = ChaseFont
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ChaseWorld.COLS.toFloat() / ChaseWorld.ROWS.toFloat())
                            .clip(RoundedCornerShape(18.dp))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    var last = down.position
                                    val threshold = 22.dp.toPx()

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break

                                        if (!change.pressed) break

                                        val dx = change.position.x - last.x
                                        val dy = change.position.y - last.y

                                        if (abs(dx) > threshold || abs(dy) > threshold) {
                                            if (abs(dx) > abs(dy)) {
                                                world.setWant(if (dx > 0f) 1 else -1, 0)
                                            } else {
                                                world.setWant(0, if (dy > 0f) 1 else -1)
                                            }

                                            last = change.position
                                        }

                                        change.consume()
                                    }
                                }
                            }
                    ) {
                        val tick = frame

                        if (tick >= 0) drawBoard(world, tick)
                    }

                    if (banner.isNotEmpty() && phase == ChasePhase.PLAY) {
                        Text(
                            text = banner,
                            color = ChasePower,
                            fontSize = 34.sp,
                            letterSpacing = 6.sp,
                            fontFamily = ChaseFont
                        )
                    }
                }
            }

            Text(
                text = "SWIPE TO STEER",
                color = ChaseDim,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                fontFamily = ChaseFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            ChaseButton(
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
            ChasePhase.START -> PopupCard {
                Text(
                    text = "CHASE",
                    color = ChasePower,
                    fontSize = 32.sp,
                    letterSpacing = 4.sp,
                    fontFamily = ChaseFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Swipe to steer your beetle and eat every seed. Three spiky chasers hunt you, so keep moving. The big amber seeds scare them for a few seconds and let you eat them. The buzz grows as a chaser gets close, but you can always see them, so it plays fine without it.",
                    color = ChaseInk.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = ChaseFont
                )

                Spacer(Modifier.height(20.dp))

                ChaseButton(
                    text = "Play",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                ChaseButton(
                    text = "Back",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            ChasePhase.OVER -> PopupCard {
                Text(
                    text = "GAME OVER",
                    color = ChaseRed,
                    fontSize = 30.sp,
                    letterSpacing = 4.sp,
                    fontFamily = ChaseFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Score ${world.score}",
                    color = ChaseInk,
                    fontSize = 24.sp,
                    fontFamily = ChaseFont
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Reached level ${world.level}",
                    color = ChaseDim,
                    fontSize = 18.sp,
                    fontFamily = ChaseFont
                )

                Text(
                    text = if (newBest) "New best!" else "Best $best",
                    color = if (newBest) ChasePower else ChaseDim,
                    fontSize = 18.sp,
                    fontFamily = ChaseFont
                )

                Spacer(Modifier.height(20.dp))

                ChaseButton(
                    text = "Play again",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                ChaseButton(
                    text = "Menu",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            ChasePhase.PLAY -> Unit
        }
    }
}

private fun DrawScope.drawRetroBackdrop() {
    val w = size.width
    val h = size.height
    val unit = w * 0.1f

    fun triangle(cx: Float, cy: Float, color: Color) {
        val path = Path().apply {
            moveTo(cx, cy - unit * 0.8f)
            lineTo(cx + unit * 0.8f, cy + unit * 0.6f)
            lineTo(cx - unit * 0.8f, cy + unit * 0.6f)
            close()
        }

        drawPath(path, color)
    }

    fun zigzag(x: Float, y: Float, color: Color) {
        val path = Path().apply {
            moveTo(x, y)

            for (i in 1..6) {
                lineTo(x + i * unit * 0.4f, y + if (i % 2 == 1) -unit * 0.35f else 0f)
            }
        }

        drawPath(path, color, style = Stroke(width = unit * 0.14f, cap = StrokeCap.Round))
    }

    triangle(w * 0.1f, h * 0.06f, Color(0xFFFFD23F).copy(alpha = 0.55f))
    triangle(w * 0.9f, h * 0.9f, Color(0xFF2EC4B6).copy(alpha = 0.6f))
    triangle(w * 0.06f, h * 0.7f, Color(0xFFFF6FB5).copy(alpha = 0.5f))
    drawCircle(Color(0xFFFF9F1C).copy(alpha = 0.55f), unit * 0.6f, Offset(w * 0.92f, h * 0.08f), style = Stroke(width = unit * 0.2f))
    drawCircle(Color(0xFFB6E03A).copy(alpha = 0.55f), unit * 0.5f, Offset(w * 0.08f, h * 0.93f), style = Stroke(width = unit * 0.18f))
    zigzag(w * 0.62f, h * 0.035f, Color(0xFF2EC4B6).copy(alpha = 0.7f))
    zigzag(w * 0.05f, h * 0.45f, Color(0xFFFFD23F).copy(alpha = 0.6f))
    zigzag(w * 0.55f, h * 0.975f, Color(0xFFFF6FB5).copy(alpha = 0.7f))
}

private fun DrawScope.drawEyes(cx: Float, cy: Float, r: Float, fx: Float, fy: Float, scared: Boolean) {
    val px = -fy
    val py = fx

    for (side in listOf(-1f, 1f)) {
        val ex = cx + fx * r * 0.22f + px * side * r * 0.36f
        val ey = cy + fy * r * 0.22f + py * side * r * 0.36f

        drawCircle(Color.White, r * 0.27f, Offset(ex, ey))

        if (scared) {
            drawCircle(Color(0xFF2B2140), r * 0.1f, Offset(ex, ey))
        } else {
            drawCircle(Color(0xFF2B2140), r * 0.14f, Offset(ex + fx * r * 0.1f, ey + fy * r * 0.1f))
        }
    }
}

private fun DrawScope.drawBoard(world: ChaseWorld, tick: Int) {
    val cell = size.width / ChaseWorld.COLS
    val wallRadius = CornerRadius(cell * 0.22f, cell * 0.22f)

    drawRect(ChaseBoard)

    for (r in 0 until ChaseWorld.ROWS) {
        for (c in 0 until ChaseWorld.COLS) {
            val ch = world.grid[r][c]
            val x = c * cell
            val y = r * cell

            if (ch == '#') {
                val inset = cell * 0.06f
                val side = cell - inset * 2f
                val corner = CornerRadius(cell * 0.14f, cell * 0.14f)

                drawRoundRect(ChaseOutline.copy(alpha = 0.55f), Offset(x + inset + 3f, y + inset + 4f), Size(side, side), corner)
                drawRoundRect(ChaseWall, Offset(x + inset, y + inset), Size(side, side), corner)
                drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(x + cell * 0.16f, y + cell * 0.16f), Size(side * 0.55f, cell * 0.1f), CornerRadius(cell * 0.05f, cell * 0.05f))
                drawRoundRect(ChaseOutline, Offset(x + inset, y + inset), Size(side, side), corner, style = Stroke(width = 3f))
            } else if (ch == '.') {
                drawCircle(ChaseSeed, cell * 0.075f, Offset(x + cell * 0.5f, y + cell * 0.5f))
            } else if (ch == 'o') {
                val pulse = 0.85f + 0.15f * sin(tick * 0.12f)

                drawCircle(ChasePower.copy(alpha = 0.25f), cell * 0.3f * pulse, Offset(x + cell * 0.5f, y + cell * 0.5f))
                drawCircle(ChasePower, cell * 0.18f * pulse, Offset(x + cell * 0.5f, y + cell * 0.5f))
                drawCircle(ChaseOutline, cell * 0.18f * pulse, Offset(x + cell * 0.5f, y + cell * 0.5f), style = Stroke(width = 2.5f))
            }
        }
    }

    val pr = cell * 0.36f
    val pcx = (world.player.x + 0.5f) * cell
    val pcy = (world.player.y + 0.5f) * cell
    val fx = if (world.pdx == 0 && world.pdy == 0) 0f else world.pdx.toFloat()
    val fy = if (world.pdx == 0 && world.pdy == 0) 1f else world.pdy.toFloat()
    val dying = world.dyingLeft > 0f
    val shrink = if (dying) (world.dyingLeft / 1.3f).coerceIn(0f, 1f) else 1f

    drawCircle(Color.Black.copy(alpha = 0.25f), pr * shrink, Offset(pcx + 2f, pcy + 3f))
    drawCircle(ChaseBeetle, pr * shrink, Offset(pcx, pcy))
    drawCircle(ChaseOutline, pr * shrink, Offset(pcx, pcy), style = Stroke(width = 3f))
    drawCircle(Color.White.copy(alpha = 0.25f), pr * 0.22f * shrink, Offset(pcx - pr * 0.35f, pcy - pr * 0.4f))

    if (!dying) {
        for (side in listOf(-1f, 1f)) {
            drawCircle(
                ChaseOutline,
                pr * 0.12f,
                Offset(pcx - fx * pr * 0.4f + (-fy) * side * pr * 0.32f, pcy - fy * pr * 0.4f + fx * side * pr * 0.32f)
            )
        }

        drawEyes(pcx, pcy, pr, fx, fy, false)
    }

    for (ch in world.chasers) {
        val m = ch.mover
        val cx = (m.x + 0.5f) * cell
        val cy = (m.y + 0.5f) * cell
        val r = cell * 0.31f
        val blink = ch.scared && world.scaredLeft < 2f && (tick / 6) % 2 == 0
        val body = when {
            blink -> Color(0xFFE8E2F0)
            ch.scared -> ChaseScared
            else -> ChaseChaserColors[ch.index % ChaseChaserColors.size]
        }

        val idle = ch.waiting > 0f

        for (k in 0 until 8) {
            val a = k * Math.PI.toFloat() / 4f + tick * 0.01f

            drawLine(
                color = body,
                start = Offset(cx + cos(a) * r * 0.8f, cy + sin(a) * r * 0.8f),
                end = Offset(cx + cos(a) * r * 1.22f, cy + sin(a) * r * 1.22f),
                strokeWidth = cell * 0.08f,
                cap = StrokeCap.Round
            )
        }

        drawCircle(body.copy(alpha = if (idle) 0.6f else 1f), r, Offset(cx, cy))
        drawCircle(ChaseOutline, r, Offset(cx, cy), style = Stroke(width = 3f))

        val dxf = if (ch.dx == 0 && ch.dy == 0) 0f else ch.dx.toFloat()
        val dyf = if (ch.dx == 0 && ch.dy == 0) 1f else ch.dy.toFloat()

        drawEyes(cx, cy, r, dxf, dyf, ch.scared)

        if (ch.scared) {
            drawLine(
                color = Color(0xFF2B2140),
                start = Offset(cx - r * 0.35f, cy + r * 0.5f),
                end = Offset(cx + r * 0.35f, cy + r * 0.5f),
                strokeWidth = cell * 0.05f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
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
                .background(ChasePanel)
                .border(4.dp, ChaseWall, shape)
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun ChaseButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (filled) ChasePower else Color.Transparent)
            .border(3.dp, if (filled) ChaseOutline else ChaseWall, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = if (filled) ChaseOutline else ChaseInk,
            fontSize = 17.sp,
            letterSpacing = 3.sp,
            maxLines = 1,
            fontFamily = ChaseFont
        )
    }
}