package com.example.pulsemate.games.brick.screens

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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
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
import com.example.pulsemate.games.brick.BallEmojis
import com.example.pulsemate.games.brick.BrickBase
import com.example.pulsemate.games.brick.BrickBgBottom
import com.example.pulsemate.games.brick.BrickBgTop
import com.example.pulsemate.games.brick.BrickBtnDark
import com.example.pulsemate.games.brick.BrickBtnFace
import com.example.pulsemate.games.brick.BrickBtnSoft
import com.example.pulsemate.games.brick.BrickBtnSoftDark
import com.example.pulsemate.games.brick.BrickEdge
import com.example.pulsemate.games.brick.BrickEventType
import com.example.pulsemate.games.brick.BrickFont
import com.example.pulsemate.games.brick.BrickHaptics
import com.example.pulsemate.games.brick.BrickInk
import com.example.pulsemate.games.brick.BrickPanel
import com.example.pulsemate.games.brick.BrickTitle
import com.example.pulsemate.games.brick.BrickWorld
import com.example.pulsemate.games.brick.PaddleWood
import com.example.pulsemate.games.brick.brickColor
import com.example.pulsemate.haptics.LoveSpouseController
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS_NAME = "pulsemate_brick"
private const val BEST_KEY = "best"
private const val NEXT_LEVEL_DELAY_MS = 1600L

private enum class BrickPhase { START, PLAYING, OVER }

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

private fun DrawScope.drawEmoji(paint: Paint, text: String, cx: Float, cy: Float, textSize: Float) {
    drawIntoCanvas { canvas ->
        paint.textSize = textSize

        val metrics = paint.fontMetrics
        val baseline = cy - (metrics.ascent + metrics.descent) / 2f

        canvas.nativeCanvas.drawText(text, cx, baseline, paint)
    }
}

@Composable
fun BrickScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { BrickHaptics(loveSpouse) }
    val world = remember { BrickWorld() }
    val emojiPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    }
    val ballEmojis = remember {
        BallEmojis.map { if (emojiPaint.hasGlyph(it)) it else "⚪" }
    }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(BrickPhase.START) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var livesUi by remember { mutableIntStateOf(3) }
    var levelUi by remember { mutableIntStateOf(1) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var waitingUi by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf(false) }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    fun commitBest() {
        if (world.score > loadBest(context)) saveBest(context, world.score)
        best = loadBest(context)
    }

    fun startRun() {
        world.reset()
        haptics.reset()
        scoreUi = 0
        livesUi = world.lives
        levelUi = world.level
        waitingUi = true
        paused = false
        banner = false
        phase = BrickPhase.PLAYING
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
                if (phase == BrickPhase.PLAYING) paused = true
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

            if (phase == BrickPhase.PLAYING && !paused && !banner) {
                val events = world.update(seconds)

                if (events.isNotEmpty()) {
                    var level = 0
                    var millis = 0L

                    for (e in events) {
                        val pair: Pair<Int, Long> = when (e.type) {
                            BrickEventType.HIT -> Pair(3, 110L)
                            BrickEventType.BREAK -> Pair(4 + e.value.coerceAtMost(3), 150L)
                            BrickEventType.ROW -> Pair(8, 350L)
                            BrickEventType.POWER -> Pair(7, 450L)
                            BrickEventType.LIFE_LOST -> Pair(9, 700L)
                            BrickEventType.LEVEL_CLEAR -> Pair(9, 1000L)
                            BrickEventType.GAME_OVER -> Pair(9, 900L)
                        }

                        if (pair.first > level || (pair.first == level && pair.second > millis)) {
                            level = pair.first
                            millis = pair.second
                        }

                        if (e.type == BrickEventType.LEVEL_CLEAR) {
                            banner = true

                            launch {
                                delay(NEXT_LEVEL_DELAY_MS)
                                world.nextLevel()
                                levelUi = world.level
                                waitingUi = true
                                banner = false
                            }
                        }

                        if (e.type == BrickEventType.GAME_OVER) {
                            phase = BrickPhase.OVER
                            commitBest()
                        }
                    }

                    if (level > 0) haptics.pulse(scope, level, millis)
                }

                if (scoreUi != world.score) scoreUi = world.score
                if (livesUi != world.lives) livesUi = world.lives
                if (waitingUi != world.waiting) waitingUi = world.waiting
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BrickBgTop, BrickBgBottom)))
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val ratio = BrickWorld.WIDTH / BrickWorld.HEIGHT
                val available = maxHeight - 40.dp
                val boardWidth = if (maxWidth / available < ratio) maxWidth else available * ratio

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier
                            .width(boardWidth)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "❤️".repeat(livesUi.coerceAtLeast(0)),
                            fontSize = 18.sp
                        )

                        Text(
                            text = "Level $levelUi",
                            color = BrickInk,
                            fontSize = 20.sp,
                            fontFamily = BrickFont
                        )

                        Text(
                            text = "$scoreUi",
                            color = BrickInk,
                            fontSize = 20.sp,
                            fontFamily = BrickFont
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .width(boardWidth)
                            .height(boardWidth / ratio)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (phase == BrickPhase.PLAYING && !paused && !banner) world.launch()
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, drag ->
                                        change.consume()

                                        if (phase == BrickPhase.PLAYING && !paused && !banner) {
                                            world.movePaddle(drag.x / (size.width / BrickWorld.WIDTH))
                                        }
                                    }
                                )
                            }
                    ) {
                        val tick = frame
                        val s = size.width / BrickWorld.WIDTH

                        if (tick >= 0) {
                            val corner = 3f * s

                            drawRoundRect(
                                color = Color(0xFF0B1450).copy(alpha = 0.5f),
                                cornerRadius = CornerRadius(corner, corner)
                            )

                            for (brick in world.bricks) {
                                if (brick.left <= 0) continue

                                val col = brickColor(brick.max, brick.left)
                                val tl = Offset(brick.x * s, brick.y * s)
                                val sz = Size(brick.w * s, brick.h * s)
                                val rad = CornerRadius(1.1f * s, 1.1f * s)

                                drawRoundRect(
                                    color = lerp(col, Color.Black, 0.16f),
                                    topLeft = Offset(tl.x, tl.y + 0.5f * s),
                                    size = sz,
                                    cornerRadius = rad
                                )

                                drawRoundRect(
                                    color = col,
                                    topLeft = tl,
                                    size = Size(sz.width, sz.height - 0.5f * s),
                                    cornerRadius = rad
                                )

                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.3f),
                                    topLeft = Offset(tl.x + 0.8f * s, tl.y + 0.5f * s),
                                    size = Size(sz.width - 1.6f * s, 0.7f * s),
                                    cornerRadius = CornerRadius(0.35f * s, 0.35f * s)
                                )

                                val gap = 1.8f * s
                                val startX = tl.x + sz.width / 2f - (brick.left - 1) * gap / 2f

                                for (d in 0 until brick.left) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.8f),
                                        radius = 0.5f * s,
                                        center = Offset(startX + d * gap, tl.y + sz.height / 2f + 0.3f * s)
                                    )
                                }
                            }

                            for (p in world.powers) {
                                val c = Offset(p.x * s, p.y * s)

                                drawEmoji(emojiPaint, p.kind.emoji, c.x, c.y, 4.2f * s)
                            }

                            val paddleLeft = (world.paddleX - world.paddleW / 2f) * s
                            val paddleTop = (BrickWorld.PADDLE_Y - BrickWorld.PADDLE_H / 2f) * s
                            val paddleWidth = world.paddleW * s
                            val paddleHeight = BrickWorld.PADDLE_H * s
                            val paddleRadius = CornerRadius(paddleHeight / 2f, paddleHeight / 2f)

                            drawRoundRect(
                                color = Color(0xFFC77D0A),
                                topLeft = Offset(paddleLeft, paddleTop + 0.5f * s),
                                size = Size(paddleWidth, paddleHeight),
                                cornerRadius = paddleRadius
                            )

                            drawRoundRect(
                                color = PaddleWood,
                                topLeft = Offset(paddleLeft, paddleTop),
                                size = Size(paddleWidth, paddleHeight - 0.4f * s),
                                cornerRadius = paddleRadius
                            )

                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.3f),
                                topLeft = Offset(paddleLeft + paddleHeight / 2f, paddleTop + 0.4f * s),
                                size = Size(paddleWidth - paddleHeight, 0.6f * s),
                                cornerRadius = CornerRadius(0.3f * s, 0.3f * s)
                            )

                            val emoji = ballEmojis[world.ballEmojiIndex % ballEmojis.size]

                            for (b in world.balls) {
                                val c = Offset(b.x * s, b.y * s)

                                drawEmoji(emojiPaint, emoji, c.x, c.y, BrickWorld.BALL_R * 2.3f * s)
                            }
                        }
                    }
                }

                if (phase == BrickPhase.PLAYING && waitingUi && !paused && !banner) {
                    Text(
                        text = "Tap to launch",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = BrickFont,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color(0x99000000),
                                offset = Offset(3f, 4f),
                                blurRadius = 6f
                            )
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(BrickBtnFace)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }

                if (banner) {
                    Text(
                        text = "Level clear!",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontFamily = BrickFont,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color(0x99000000),
                                offset = Offset(3f, 4f),
                                blurRadius = 6f
                            )
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(BrickBtnFace)
                            .border(3.dp, BrickBtnDark, RoundedCornerShape(24.dp))
                            .padding(horizontal = 26.dp, vertical = 14.dp)
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
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CuteButton(
                    text = "Pause",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(0.5f),
                    onClick = {
                        if (phase == BrickPhase.PLAYING) {
                            paused = true
                            haptics.stop()
                        }
                    }
                )
            }
        }

        if (phase == BrickPhase.START) {
            PopupCard {
                Text(
                    text = "Brick Pop",
                    color = BrickTitle,
                    fontSize = 36.sp,
                    fontFamily = BrickFont
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((index, color) in BrickBase.withIndex()) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontFamily = BrickFont
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "The color shows how many hits a brick takes. Drag to slide the paddle, tap to launch.",
                    color = BrickInk,
                    fontSize = 15.sp,
                    fontFamily = BrickFont,
                    textAlign = TextAlign.Center
                )

                if (best > 0) {
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Best: $best",
                        color = BrickInk.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontFamily = BrickFont
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

        if (phase == BrickPhase.OVER) {
            PopupCard {
                Text(
                    text = "Game over",
                    color = BrickTitle,
                    fontSize = 34.sp,
                    fontFamily = BrickFont
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Score $scoreUi",
                    color = BrickInk,
                    fontSize = 22.sp,
                    fontFamily = BrickFont
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Best $best",
                    color = BrickInk.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontFamily = BrickFont
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

        if (paused && phase == BrickPhase.PLAYING) {
            PopupCard {
                Text(
                    text = "Paused",
                    color = BrickTitle,
                    fontSize = 32.sp,
                    fontFamily = BrickFont
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
            .background(Color.Black.copy(alpha = 0.6f))
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
                .clip(RoundedCornerShape(6.dp))
                .background(BrickPanel)
                .border(3.dp, BrickEdge, RoundedCornerShape(6.dp))
                .padding(5.dp)
                .border(1.dp, BrickEdge.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                .padding(22.dp),
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
    val face = if (primary) BrickBtnFace else BrickBtnSoft
    val outer = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .clip(outer)
            .background(Color.Black)
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(face)
                .drawBehind {
                    drawRect(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, 4f)
                    )

                    drawRect(
                        color = Color.Black.copy(alpha = 0.32f),
                        topLeft = Offset(0f, size.height - 7f),
                        size = Size(size.width, 7f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                color = if (primary) Color(0xFF1A1400) else BrickInk,
                fontSize = 15.sp,
                letterSpacing = 1.5.sp,
                fontFamily = BrickFont
            )
        }
    }
}