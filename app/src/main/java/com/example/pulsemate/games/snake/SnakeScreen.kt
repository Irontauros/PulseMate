package com.example.pulsemate.games.snake.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pulsemate.games.snake.SnakeBgBottom
import com.example.pulsemate.games.snake.SnakeBgTop
import com.example.pulsemate.games.snake.SnakeBoardA
import com.example.pulsemate.games.snake.SnakeBoardB
import com.example.pulsemate.games.snake.SnakeBoardEdge
import com.example.pulsemate.games.snake.SnakeBtnLilac
import com.example.pulsemate.games.snake.SnakeBtnLilacDark
import com.example.pulsemate.games.snake.SnakeBtnPink
import com.example.pulsemate.games.snake.SnakeBtnPinkDark
import com.example.pulsemate.games.snake.SnakeEvent
import com.example.pulsemate.games.snake.SnakeFont
import com.example.pulsemate.games.snake.SnakeFruits
import com.example.pulsemate.games.snake.SnakeHaptics
import com.example.pulsemate.games.snake.SnakeInk
import com.example.pulsemate.games.snake.SnakePanel
import com.example.pulsemate.games.snake.SnakeTitle
import com.example.pulsemate.games.snake.SnakeWorld
import com.example.pulsemate.games.snake.snakeFruitEmoji
import com.example.pulsemate.haptics.LoveSpouseController
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val PREFS_NAME = "pulsemate_snake"
private const val BEST_KEY = "best"
private const val SWIPE_PX = 28f

private enum class SnakePhase { START, PLAYING, OVER }

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
fun SnakeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { SnakeHaptics(loveSpouse) }
    val world = remember { SnakeWorld() }
    val emojiPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    }
    val fruitEmojis = remember { SnakeFruits.map { snakeFruitEmoji(it, emojiPaint) } }
    val timing = remember { FloatArray(3) }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(SnakePhase.START) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var started by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    fun commitBest() {
        if (world.score > loadBest(context)) saveBest(context, world.score)
        best = loadBest(context)
    }

    fun startRun() {
        world.reset()
        haptics.reset()
        timing[0] = 0f
        timing[1] = 1f
        scoreUi = 0
        started = false
        paused = false
        phase = SnakePhase.PLAYING
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
            commitBest()
        }
    }

    DisposableEffect(lifecycleOwner, haptics) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                haptics.stop()
                if (phase == SnakePhase.PLAYING) paused = true
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

            timing[2] += seconds

            if (phase == SnakePhase.PLAYING && !paused) {
                if (world.moving != started) started = world.moving

                if (world.moving && world.alive) {
                    timing[0] += seconds
                    val interval = SnakeWorld.stepSeconds(world.cells.size)

                    while (timing[0] >= interval && world.alive) {
                        timing[0] -= interval

                        when (world.step()) {
                            SnakeEvent.ATE -> {
                                scoreUi = world.score
                                haptics.eat(scope, SnakeFruits[world.lastType].level)
                            }

                            SnakeEvent.DIED -> {
                                phase = SnakePhase.OVER
                                haptics.die(scope)
                                commitBest()
                            }

                            SnakeEvent.NONE -> Unit
                        }
                    }

                    timing[1] = if (world.alive) (timing[0] / interval).coerceIn(0f, 1f) else 1f

                    if (world.alive) {
                        haptics.idle(((world.cells.size - SnakeWorld.START_LENGTH) / 4).coerceIn(0, 2))
                    }
                }
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SnakeBgTop, SnakeBgBottom)))
            .safeDrawingPadding()
            .pointerInput(Unit) {
                var ax = 0f
                var ay = 0f

                detectDragGestures(
                    onDragStart = {
                        ax = 0f
                        ay = 0f
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        ax += drag.x
                        ay += drag.y

                        if (abs(ax) > SWIPE_PX || abs(ay) > SWIPE_PX) {
                            val d = if (abs(ax) > abs(ay)) {
                                if (ax > 0f) 1 else 3
                            } else {
                                if (ay > 0f) 2 else 0
                            }

                            if (phase == SnakePhase.PLAYING && !paused) world.turn(d)

                            ax = 0f
                            ay = 0f
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🍒 $scoreUi",
                    color = SnakeInk,
                    fontSize = 28.sp,
                    fontFamily = SnakeFont
                )

                Text(
                    text = "Best $best",
                    color = SnakeInk.copy(alpha = 0.7f),
                    fontSize = 20.sp,
                    fontFamily = SnakeFont
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                val ratio = SnakeWorld.COLS.toFloat() / SnakeWorld.ROWS
                val boardWidth = if (maxWidth / maxHeight < ratio) maxWidth else maxHeight * ratio

                Canvas(
                    modifier = Modifier
                        .width(boardWidth)
                        .height(boardWidth / ratio)
                ) {
                    val tick = frame
                    val cell = size.width / SnakeWorld.COLS
                    val corner = cell * 0.7f
                    val clock = timing[2]

                    if (tick >= 0) {
                        val clip = Path().apply {
                            addRoundRect(
                                RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner, corner))
                            )
                        }

                        clipPath(clip) {
                            for (j in 0 until SnakeWorld.ROWS) {
                                for (i in 0 until SnakeWorld.COLS) {
                                    drawRect(
                                        color = if ((i + j) % 2 == 0) SnakeBoardA else SnakeBoardB,
                                        topLeft = Offset(i * cell, j * cell),
                                        size = Size(cell, cell)
                                    )
                                }
                            }

                            for (spot in world.fruits) {
                                val fruit = SnakeFruits[spot.type]
                                val cx = (spot.x + 0.5f) * cell
                                val cy = (spot.y + 0.5f) * cell
                                val bob = 1f + 0.08f * sin(clock * 5f + spot.x)

                                drawCircle(
                                    color = fruit.color.copy(alpha = 0.6f),
                                    radius = cell * 0.47f * bob,
                                    center = Offset(cx, cy)
                                )

                                drawIntoCanvas { canvas ->
                                    emojiPaint.textSize = cell * 0.62f * bob

                                    val metrics = emojiPaint.fontMetrics
                                    val baseline = cy - (metrics.ascent + metrics.descent) / 2f

                                    canvas.nativeCanvas.drawText(fruitEmojis[spot.type], cx, baseline, emojiPaint)
                                }
                            }

                            val n = world.cells.size
                            val prev = world.prevCells
                            val pn = prev.size
                            val t = if (world.moving && world.alive) timing[1].coerceIn(0f, 1f) else 1f

                            if (n > 0 && pn > 0) {
                                val xs = FloatArray(n)
                                val ys = FloatArray(n)

                                for (j in 0 until n) {
                                    val c = world.cells[n - 1 - j]
                                    val p = prev[(pn - 1 - j).coerceAtLeast(0)]

                                    xs[j] = ((p[0] + (c[0] - p[0]) * t) + 0.5f) * cell
                                    ys[j] = ((p[1] + (c[1] - p[1]) * t) + 0.5f) * cell
                                }

                                val stroke = cell * 0.8f
                                val shadowShift = cell * 0.07f

                                for (j in n - 1 downTo 1) {
                                    drawLine(
                                        color = Color.Black.copy(alpha = 0.08f),
                                        start = Offset(xs[j], ys[j] + shadowShift),
                                        end = Offset(xs[j - 1], ys[j - 1] + shadowShift),
                                        strokeWidth = stroke,
                                        cap = StrokeCap.Round
                                    )
                                }

                                for (j in n - 1 downTo 1) {
                                    drawLine(
                                        color = world.colors[n - 1 - j],
                                        start = Offset(xs[j], ys[j]),
                                        end = Offset(xs[j - 1], ys[j - 1]),
                                        strokeWidth = stroke,
                                        cap = StrokeCap.Round
                                    )
                                }

                                for (j in n - 1 downTo 1) {
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.3f),
                                        start = Offset(xs[j] - cell * 0.1f, ys[j] - cell * 0.12f),
                                        end = Offset(xs[j - 1] - cell * 0.1f, ys[j - 1] - cell * 0.12f),
                                        strokeWidth = cell * 0.28f,
                                        cap = StrokeCap.Round
                                    )
                                }

                                val headColor = world.colors[n - 1]
                                val head = Offset(xs[0], ys[0])
                                val fx = world.dirX.toFloat()
                                val fy = world.dirY.toFloat()
                                val px = -fy
                                val py = fx

                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.08f),
                                    radius = cell * 0.46f,
                                    center = Offset(head.x, head.y + shadowShift)
                                )

                                drawCircle(
                                    color = headColor,
                                    radius = cell * 0.46f,
                                    center = head
                                )

                                for (side in listOf(-1f, 1f)) {
                                    val blush = Offset(
                                        head.x - fx * cell * 0.12f + px * side * cell * 0.3f,
                                        head.y - fy * cell * 0.12f + py * side * cell * 0.3f
                                    )

                                    drawCircle(
                                        color = Color(0xFFFF8FB0).copy(alpha = 0.5f),
                                        radius = cell * 0.08f,
                                        center = blush
                                    )

                                    val eye = Offset(
                                        head.x + fx * cell * 0.1f + px * side * cell * 0.2f,
                                        head.y + fy * cell * 0.1f + py * side * cell * 0.2f
                                    )

                                    drawCircle(color = Color.White, radius = cell * 0.15f, center = eye)
                                    drawCircle(
                                        color = SnakeInk,
                                        radius = cell * 0.08f,
                                        center = Offset(eye.x + fx * cell * 0.04f, eye.y + fy * cell * 0.04f)
                                    )
                                }
                            }
                        }

                        drawRoundRect(
                            color = SnakeBoardEdge,
                            cornerRadius = CornerRadius(corner, corner),
                            style = Stroke(width = cell * 0.22f)
                        )
                    }
                }

                if (phase == SnakePhase.PLAYING && !started && !paused) {
                    Text(
                        text = "Swipe to start",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontFamily = SnakeFont,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color(0x99000000),
                                offset = Offset(3f, 4f),
                                blurRadius = 6f
                            )
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(SnakeBtnPink)
                            .padding(horizontal = 22.dp, vertical = 12.dp)
                    )
                }
            }

            deviceProblem?.let { problem ->
                Text(
                    text = problem,
                    color = Color(0xFFB45309),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CuteButton(
                    text = "Pause",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(0.5f),
                    onClick = {
                        if (phase == SnakePhase.PLAYING) {
                            paused = true
                            haptics.stop()
                        }
                    }
                )
            }
        }

        if (phase == SnakePhase.START) {
            PopupCard {
                Text(
                    text = "Fruity Snake",
                    color = SnakeTitle,
                    fontSize = 36.sp,
                    fontFamily = SnakeFont
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = fruitEmojis.joinToString(" "),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Swipe to steer. Every fruit tints your snake and buzzes differently.",
                    color = SnakeInk,
                    fontSize = 15.sp,
                    fontFamily = SnakeFont,
                    textAlign = TextAlign.Center
                )

                if (best > 0) {
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Best: $best",
                        color = SnakeInk.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontFamily = SnakeFont
                    )
                }

                Spacer(Modifier.height(18.dp))

                CuteButton(
                    text = "Play",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startRun() }
                )

                Spacer(Modifier.height(10.dp))

                CuteButton(
                    text = "Back",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }
        }

        if (phase == SnakePhase.OVER) {
            PopupCard {
                Text(
                    text = "Yum! All done",
                    color = SnakeTitle,
                    fontSize = 32.sp,
                    fontFamily = SnakeFont,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "You ate $scoreUi fruits",
                    color = SnakeInk,
                    fontSize = 20.sp,
                    fontFamily = SnakeFont
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Best: $best",
                    color = SnakeInk.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontFamily = SnakeFont
                )

                Spacer(Modifier.height(18.dp))

                CuteButton(
                    text = "Play again",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startRun() }
                )

                Spacer(Modifier.height(10.dp))

                CuteButton(
                    text = "Menu",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }
        }

        if (paused && phase == SnakePhase.PLAYING) {
            PopupCard {
                Text(
                    text = "Paused",
                    color = SnakeTitle,
                    fontSize = 32.sp,
                    fontFamily = SnakeFont
                )

                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CuteButton(
                        text = "Quit",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            commitBest()
                            haptics.stop()
                            onBack()
                        }
                    )

                    CuteButton(
                        text = "Resume",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = { paused = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
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
                .clip(RoundedCornerShape(28.dp))
                .background(SnakePanel)
                .border(4.dp, SnakeBoardEdge, RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun CuteButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val face = if (primary) SnakeBtnPink else SnakeBtnLilac
    val edge = if (primary) SnakeBtnPinkDark else SnakeBtnLilacDark
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(edge)
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(shape)
                .background(face),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (primary) Color.White else SnakeInk,
                fontSize = 18.sp,
                fontFamily = SnakeFont
            )
        }
    }
}