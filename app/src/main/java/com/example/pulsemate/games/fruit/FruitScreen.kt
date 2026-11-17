package com.example.pulsemate.games.fruit.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.example.pulsemate.games.fruit.BOMB_EMOJI
import com.example.pulsemate.games.fruit.BONUS_EMOJI
import com.example.pulsemate.games.fruit.BoardWood
import com.example.pulsemate.games.fruit.BoardGrain
import com.example.pulsemate.games.fruit.BoardWoodLight
import com.example.pulsemate.games.fruit.FruitBgBottom
import com.example.pulsemate.games.fruit.FruitBgTop
import com.example.pulsemate.games.fruit.FruitBtnDark
import com.example.pulsemate.games.fruit.FruitBtnFace
import com.example.pulsemate.games.fruit.FruitBtnSoft
import com.example.pulsemate.games.fruit.FruitBtnSoftDark
import com.example.pulsemate.games.fruit.FruitDefs
import com.example.pulsemate.games.fruit.FruitEdge
import com.example.pulsemate.games.fruit.FruitEventType
import com.example.pulsemate.games.fruit.FruitFont
import com.example.pulsemate.games.fruit.FruitHaptics
import com.example.pulsemate.games.fruit.FruitInk
import com.example.pulsemate.games.fruit.FruitPanel
import com.example.pulsemate.games.fruit.FruitTitle
import com.example.pulsemate.games.fruit.FruitWorld
import com.example.pulsemate.games.fruit.KIND_BOMB
import com.example.pulsemate.games.fruit.KIND_BONUS
import com.example.pulsemate.haptics.LoveSpouseController
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS_NAME = "pulsemate_fruit"
private const val BEST_KEY = "best"

private enum class FruitPhase { START, PLAYING, OVER }

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

private fun DrawScope.drawEmoji(
    paint: Paint,
    text: String,
    cx: Float,
    cy: Float,
    textSize: Float,
    angle: Float = 0f,
    side: Int = 0
) {
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas

        paint.textSize = textSize

        val metrics = paint.fontMetrics
        val baseline = -(metrics.ascent + metrics.descent) / 2f

        nc.save()
        nc.translate(cx, cy)
        nc.rotate(angle)

        if (side < 0) {
            nc.clipRect(-textSize, -textSize, 0f, textSize)
        } else if (side > 0) {
            nc.clipRect(0f, -textSize, textSize, textSize)
        }

        nc.drawText(text, 0f, baseline, paint)
        nc.restore()
    }
}

@Composable
fun FruitScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { FruitHaptics(loveSpouse) }
    val world = remember { FruitWorld() }
    val emojiPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    }
    val fruitEmojis = remember {
        FruitDefs.map { if (emojiPaint.hasGlyph(it.emoji)) it.emoji else it.fallback }
    }
    val stamp = remember { IntArray(1) }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(FruitPhase.START) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var livesUi by remember { mutableIntStateOf(FruitWorld.START_LIVES) }
    var comboUi by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var paused by remember { mutableStateOf(false) }
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
        comboUi = 0
        paused = false
        phase = FruitPhase.PLAYING
    }

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

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
                if (phase == FruitPhase.PLAYING) paused = true
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

            if (phase == FruitPhase.PLAYING && !paused) {
                val events = world.update(seconds)

                if (events.isNotEmpty()) {
                    var level = 0
                    var millis = 0L

                    for (e in events) {
                        val pair: Pair<Int, Long> = when (e.type) {
                            FruitEventType.SLICE -> Pair(FruitDefs[e.value].level, 130L)
                            FruitEventType.COMBO -> Pair((6 + e.value).coerceAtMost(9), 350L)
                            FruitEventType.BOMB -> Pair(9, 700L)
                            FruitEventType.BONUS -> Pair(8, 400L)
                            FruitEventType.MISS -> Pair(4, 250L)
                            FruitEventType.GAME_OVER -> Pair(9, 900L)
                        }

                        if (pair.first > level || (pair.first == level && pair.second > millis)) {
                            level = pair.first
                            millis = pair.second
                        }

                        if (e.type == FruitEventType.COMBO) {
                            comboUi = e.value
                            stamp[0]++

                            val mine = stamp[0]

                            launch {
                                delay(800)
                                if (stamp[0] == mine) comboUi = 0
                            }
                        }

                        if (e.type == FruitEventType.GAME_OVER) {
                            phase = FruitPhase.OVER
                            commitBest()
                        }
                    }

                    if (level > 0) haptics.pulse(scope, level, millis)
                }

                if (scoreUi != world.score) scoreUi = world.score
                if (livesUi != world.lives) livesUi = world.lives
            } else if (phase == FruitPhase.OVER) {
                world.update(seconds)
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FruitBgTop, FruitBgBottom)))
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
                    text = "❤️".repeat(livesUi.coerceAtLeast(0)),
                    fontSize = 20.sp
                )

                Text(
                    text = "$scoreUi",
                    color = FruitInk,
                    fontSize = 26.sp,
                    fontFamily = FruitFont
                )

                Text(
                    text = "Best $best",
                    color = FruitInk.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontFamily = FruitFont
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
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                world.resize(size.width.toFloat(), size.height.toFloat())

                                val sx = world.width / size.width
                                val sy = world.height / size.height

                                var px = down.position.x * sx
                                var py = down.position.y * sy

                                world.beginSwipe()
                                world.addTrail(px, py)

                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    val cx = change.position.x * sx
                                    val cy = change.position.y * sy

                                    if (phase == FruitPhase.PLAYING && !paused) {
                                        world.slice(px, py, cx, cy)
                                        world.addTrail(cx, cy)
                                    }

                                    px = cx
                                    py = cy
                                    change.consume()
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    val tick = frame

                    if (tick >= 0) {
                        world.resize(size.width, size.height)

                        val s = size.width / world.width

                        for (i in 0 until 30) {
                            val y = size.height * (i + 0.5f) / 30f
                            val wave = sin(i * 1.7f) * s * 1.6f

                            drawLine(
                                color = BoardGrain.copy(alpha = 0.10f + 0.06f * ((i * 7) % 5) / 4f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y + wave),
                                strokeWidth = s * (0.25f + 0.2f * ((i * 3) % 4) / 3f)
                            )
                        }

                        val knots = listOf(
                            Offset(size.width * 0.2f, size.height * 0.3f),
                            Offset(size.width * 0.78f, size.height * 0.62f),
                            Offset(size.width * 0.42f, size.height * 0.88f)
                        )

                        for (k in knots) {
                            for (ring in 1..3) {
                                drawOval(
                                    color = BoardGrain.copy(alpha = 0.14f),
                                    topLeft = Offset(k.x - s * 3f * ring, k.y - s * 1.6f * ring),
                                    size = Size(s * 6f * ring, s * 3.2f * ring),
                                    style = Stroke(width = s * 0.3f)
                                )
                            }
                        }

                        drawRect(
                            color = FruitEdge.copy(alpha = 0.55f),
                            topLeft = Offset(s * 1.5f, s * 1.5f),
                            size = Size(size.width - s * 3f, size.height - s * 3f),
                            style = Stroke(width = s * 0.9f)
                        )

                        for (cut in world.cuts) {
                            val fade = (1f - cut.age / FruitWorld.CUT_SECONDS).coerceIn(0f, 1f)
                            val reach = FruitWorld.RADIUS * 1.5f * s
                            val cx = cut.x * s
                            val cy = cut.y * s

                            drawLine(
                                color = FruitEdge.copy(alpha = 0.55f * fade),
                                start = Offset(cx - cut.dx * reach, cy - cut.dy * reach),
                                end = Offset(cx + cut.dx * reach, cy + cut.dy * reach),
                                strokeWidth = s * 0.45f,
                                cap = StrokeCap.Round
                            )

                            drawLine(
                                color = Color.White.copy(alpha = 0.25f * fade),
                                start = Offset(cx - cut.dx * reach + s * 0.4f, cy - cut.dy * reach + s * 0.4f),
                                end = Offset(cx + cut.dx * reach + s * 0.4f, cy + cut.dy * reach + s * 0.4f),
                                strokeWidth = s * 0.2f,
                                cap = StrokeCap.Round
                            )
                        }

                        for (f in world.flyers) {
                            val c = Offset(f.x * s, f.y * s)
                            val emoji = when (f.kind) {
                                KIND_BOMB -> BOMB_EMOJI
                                KIND_BONUS -> BONUS_EMOJI
                                else -> fruitEmojis[f.kind]
                            }

                            drawEmoji(emojiPaint, emoji, c.x, c.y, FruitWorld.RADIUS * 2.1f * s, f.angle)
                        }

                        for (h in world.halves) {
                            drawEmoji(
                                emojiPaint,
                                fruitEmojis[h.kind],
                                h.x * s,
                                h.y * s,
                                FruitWorld.RADIUS * 2.1f * s,
                                h.angle,
                                h.side
                            )
                        }

                        for (sp in world.splashes) {
                            val t = (sp.age / 0.55f).coerceIn(0f, 1f)
                            val color = if (sp.kind >= 0) FruitDefs[sp.kind].color else Color.White

                            for (d in 0 until 8) {
                                val a = d * 0.785f + sp.kind * 0.3f
                                val dist = FruitWorld.RADIUS * (0.6f + 1.8f * t) * s

                                drawCircle(
                                    color = color.copy(alpha = (1f - t) * 0.95f),
                                    radius = (1.5f - 0.9f * t) * s,
                                    center = Offset(sp.x * s + cos(a) * dist, sp.y * s + sin(a) * dist)
                                )
                            }
                        }

                        val pts = world.trail

                        for (j in 1 until pts.size) {
                            val b = pts[j]
                            val a = pts[j - 1]

                            if (b.start) continue

                            val fade = (1f - (world.time - b.time) / 0.25f).coerceIn(0f, 1f)

                            drawLine(
                                color = Color(0xFFF4F7FB).copy(alpha = 0.95f * fade),
                                start = Offset(a.x * s, a.y * s),
                                end = Offset(b.x * s, b.y * s),
                                strokeWidth = (0.4f + 1.4f * fade) * s,
                                cap = StrokeCap.Round
                            )
                        }

                        if (world.flash > 0f) {
                            drawRect(color = Color.White.copy(alpha = 0.7f * world.flash))
                        }
                    }
                }

                if (comboUi >= 3 && phase == FruitPhase.PLAYING) {
                    Text(
                        text = "Combo x$comboUi!",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontFamily = FruitFont,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color(0x99000000),
                                offset = Offset(3f, 4f),
                                blurRadius = 6f
                            )
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(FruitTitle)
                            .border(3.dp, FruitEdge, RoundedCornerShape(12.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
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
                        if (phase == FruitPhase.PLAYING) {
                            paused = true
                            haptics.stop()
                        }
                    }
                )
            }
        }

        if (phase == FruitPhase.START) {
            PopupCard {
                Text(
                    text = "Fruit Slice",
                    color = FruitTitle,
                    fontSize = 36.sp,
                    fontFamily = FruitFont
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = fruitEmojis.joinToString(" "),
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Swipe to slice fruit. Don't miss them, and avoid the devil. Slice a few in one swipe for a combo.",
                    color = FruitInk,
                    fontSize = 15.sp,
                    fontFamily = FruitFont,
                    textAlign = TextAlign.Center
                )

                if (best > 0) {
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Best: $best",
                        color = FruitInk.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontFamily = FruitFont
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

        if (phase == FruitPhase.OVER) {
            PopupCard {
                Text(
                    text = "Game over",
                    color = FruitTitle,
                    fontSize = 34.sp,
                    fontFamily = FruitFont
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Score $scoreUi",
                    color = FruitInk,
                    fontSize = 22.sp,
                    fontFamily = FruitFont
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Best $best",
                    color = FruitInk.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontFamily = FruitFont
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

        if (paused && phase == FruitPhase.PLAYING) {
            PopupCard {
                Text(
                    text = "Paused",
                    color = FruitTitle,
                    fontSize = 32.sp,
                    fontFamily = FruitFont
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
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(BoardWoodLight, BoardWood)))
                .drawBehind {
                    for (i in 1..9) {
                        val y = size.height * i / 10f

                        drawLine(
                            color = BoardGrain.copy(alpha = 0.22f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y + 6f * (i % 3 - 1)),
                            strokeWidth = 2f
                        )
                    }
                }
                .border(5.dp, FruitEdge, RoundedCornerShape(14.dp))
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
    val shape = RoundedCornerShape(10.dp)
    val brush = if (primary) {
        Brush.verticalGradient(listOf(Color(0xFFF4F7FB), FruitBtnFace, FruitBtnDark))
    } else {
        Brush.verticalGradient(listOf(FruitBtnSoft, FruitBtnSoftDark))
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(2.dp, FruitEdge, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = FruitInk,
            fontSize = 18.sp,
            fontFamily = FruitFont,
            modifier = Modifier.padding(vertical = 11.dp)
        )
    }
}