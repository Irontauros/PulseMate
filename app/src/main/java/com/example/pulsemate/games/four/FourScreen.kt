package com.example.pulsemate.games.four.screens

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawBehind
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
import com.example.pulsemate.games.four.FourAiColor
import com.example.pulsemate.games.four.FourAiEngine
import com.example.pulsemate.games.four.FourBgBottom
import com.example.pulsemate.games.four.FourBgTop
import com.example.pulsemate.games.four.FourBoard
import com.example.pulsemate.games.four.FourBoardDark
import com.example.pulsemate.games.four.FourBtnDark
import com.example.pulsemate.games.four.FourBtnFace
import com.example.pulsemate.games.four.FourBtnSoft
import com.example.pulsemate.games.four.FourBtnSoftDark
import com.example.pulsemate.games.four.FourEdge
import com.example.pulsemate.games.four.FourEmojiPairs
import com.example.pulsemate.games.four.FourEvent
import com.example.pulsemate.games.four.FourEventType
import com.example.pulsemate.games.four.FourFont
import com.example.pulsemate.games.four.FourGood
import com.example.pulsemate.games.four.FourHaptics
import com.example.pulsemate.games.four.FourHumanColor
import com.example.pulsemate.games.four.FourInk
import com.example.pulsemate.games.four.FourPanel
import com.example.pulsemate.games.four.FourTitle
import com.example.pulsemate.games.four.FourWorld
import com.example.pulsemate.haptics.LoveSpouseController
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

private const val PREFS_NAME = "pulsemate_four"
private val LEVEL_NAMES = listOf("Easy", "Medium", "Hard", "Expert")

private enum class FourPhase { START, PLAYING, OVER }

private class Geo(val x: Float, val y: Float, val cell: Float)

private fun winsOf(context: Context, level: Int): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("w$level", 0)
}

private fun addWin(context: Context, level: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    prefs.edit().putInt("w$level", prefs.getInt("w$level", 0) + 1).apply()
}

private fun Context.findActivity(): Activity? {
    var current: Context = this

    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }

    return null
}

private fun geoFor(width: Float, height: Float): Geo {
    val cell = min(width * 0.94f / FourWorld.COLS, height * 0.96f / 7.3f)
    val x = (width - cell * FourWorld.COLS) / 2f
    val top = (height - cell * 7.3f) / 2f

    return Geo(x, top + cell * 1.3f, cell)
}

private fun darker(color: Color): Color {
    return Color(color.red * 0.8f, color.green * 0.8f, color.blue * 0.8f, color.alpha)
}

private fun DrawScope.drawDisc(
    center: Offset,
    radius: Float,
    color: Color,
    emoji: String,
    paint: Paint,
    alpha: Float = 1f
) {
    if (emoji.isEmpty()) {
        drawCircle(color = color.copy(alpha = alpha), radius = radius, center = center)

        return
    }

    drawIntoCanvas { canvas ->
        paint.textSize = radius * 1.75f
        paint.alpha = (255 * alpha).toInt()

        val m = paint.fontMetrics

        canvas.nativeCanvas.drawText(emoji, center.x, center.y - (m.ascent + m.descent) / 2f, paint)
    }
}

@Composable
fun FourScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { FourHaptics(loveSpouse) }
    val world = remember { FourWorld() }
    val random = remember { Random(System.nanoTime()) }
    val thinking = remember { BooleanArray(1) }
    val humanFirst = remember { BooleanArray(1) { true } }
    val emojiPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    }
    val pairs = remember {
        FourEmojiPairs.filter { emojiPaint.hasGlyph(it.first) && emojiPaint.hasGlyph(it.second) }
            .ifEmpty { listOf(Pair("", "")) }
    }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(FourPhase.START) }
    var turnUi by remember { mutableIntStateOf(FourWorld.HUMAN) }
    var hoverCol by remember { mutableIntStateOf(-1) }
    var level by remember { mutableIntStateOf(2) }
    var humanEmoji by remember { mutableStateOf(pairs[0].first) }
    var aiEmoji by remember { mutableStateOf(pairs[0].second) }
    var winsUi by remember { mutableStateOf(List(4) { winsOf(context, it + 1) }) }
    var resultText by remember { mutableStateOf("") }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    fun handle(events: List<FourEvent>) {
        var strength = 0
        var millis = 0L

        for (e in events) {
            val human = e.player == FourWorld.HUMAN

            val pair: Pair<Int, Long> = when (e.type) {
                FourEventType.LAND ->
                    if (human) Pair((3 + e.value / 2).coerceAtMost(5), 130L)
                    else Pair((2 + e.value / 3).coerceAtMost(4), 110L)

                FourEventType.BLOCK -> if (human) Pair(6, 260L) else Pair(4, 200L)

                FourEventType.THREAT ->
                    if (human) Pair(if (e.value >= 2) 8 else 6, 380L)
                    else Pair(if (e.value >= 2) 9 else 7, 450L)

                FourEventType.WIN -> Pair(9, 1100L)
                FourEventType.LOSE -> Pair(7, 900L)
                FourEventType.DRAW -> Pair(5, 600L)
            }

            if (pair.first > strength || (pair.first == strength && pair.second > millis)) {
                strength = pair.first
                millis = pair.second
            }

            if (e.type == FourEventType.WIN || e.type == FourEventType.LOSE || e.type == FourEventType.DRAW) {
                resultText = when (e.type) {
                    FourEventType.WIN -> "You win!"
                    FourEventType.LOSE -> "AI wins"
                    else -> "Draw"
                }

                if (e.type == FourEventType.WIN) {
                    addWin(context, level)
                    winsUi = List(4) { winsOf(context, it + 1) }
                }

                val id = world.gameId

                scope.launch {
                    delay(1100)
                    if (world.gameId == id) phase = FourPhase.OVER
                }
            }
        }

        if (strength > 0) haptics.pulse(scope, strength, millis)
    }

    fun startGame(chosen: Int) {
        level = chosen

        val pair = pairs[random.nextInt(pairs.size)]

        humanEmoji = pair.first
        aiEmoji = pair.second
        world.start(chosen, humanFirst[0])
        humanFirst[0] = !humanFirst[0]
        haptics.reset()
        hoverCol = -1
        turnUi = world.turn
        phase = FourPhase.PLAYING
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

            if (phase == FourPhase.PLAYING) {
                handle(world.update(seconds))
                turnUi = world.turn

                if (world.wantsAi() && !thinking[0]) {
                    thinking[0] = true

                    val id = world.gameId
                    val snapshot = world.snapshot()
                    val chosenLevel = world.difficulty

                    scope.launch {
                        val started = System.currentTimeMillis()
                        val col = withContext(Dispatchers.Default) {
                            FourAiEngine.choose(snapshot, chosenLevel, random)
                        }
                        val wait = 550L - (System.currentTimeMillis() - started)

                        if (wait > 0) delay(wait)

                        thinking[0] = false

                        if (world.gameId == id && world.wantsAi()) world.aiDrop(col)
                    }
                }
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FourBgTop, FourBgBottom)))
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TurnChip("You", FourHumanColor, humanEmoji, turnUi == FourWorld.HUMAN)

                Text(
                    text = LEVEL_NAMES[(level - 1).coerceIn(0, 3)],
                    color = FourInk.copy(alpha = 0.75f),
                    fontSize = 16.sp,
                    fontFamily = FourFont
                )

                TurnChip("AI", FourAiColor, aiEmoji, turnUi == FourWorld.AI)
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()

                            if (phase != FourPhase.PLAYING || world.turn != FourWorld.HUMAN || !world.canPlay()) {
                                return@awaitEachGesture
                            }

                            val geo = geoFor(size.width.toFloat(), size.height.toFloat())

                            fun colOf(x: Float): Int {
                                return ((x - geo.x) / geo.cell).toInt().coerceIn(0, FourWorld.COLS - 1)
                            }

                            hoverCol = colOf(down.position.x)

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val col = colOf(change.position.x)

                                if (!change.pressed) {
                                    if (world.humanDrop(col)) {
                                        haptics.pulse(scope, 1, 40L)
                                    } else if (world.canPlay() && world.turn == FourWorld.HUMAN) {
                                        haptics.pulse(scope, 2, 120L)
                                    }

                                    break
                                }

                                if (col != hoverCol) {
                                    hoverCol = col
                                    haptics.pulse(scope, 1, 35L)
                                }

                                change.consume()
                            }

                            hoverCol = -1
                        }
                    }
            ) {
                val tick = frame

                if (tick >= 0) {
                    val geo = geoFor(size.width, size.height)
                    val c = geo.cell
                    val radius = c * 0.42f

                    fun center(row: Float, col: Int): Offset {
                        return Offset(geo.x + (col + 0.5f) * c, geo.y + (row + 0.5f) * c)
                    }

                    if (hoverCol in 0 until FourWorld.COLS) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.35f),
                            topLeft = Offset(geo.x + hoverCol * c, geo.y - c * 1.2f),
                            size = Size(c, c * 7.2f),
                            cornerRadius = CornerRadius(c * 0.3f, c * 0.3f)
                        )

                        drawDisc(center(-1f, hoverCol), radius, FourHumanColor, humanEmoji, emojiPaint, 0.7f)
                    }

                    for (r in 0 until FourWorld.ROWS) {
                        for (col in 0 until FourWorld.COLS) {
                            val v = world.board[r][col]

                            if (v != FourWorld.EMPTY) {
                                drawDisc(
                                    center(r.toFloat(), col),
                                    radius,
                                    if (v == FourWorld.HUMAN) FourHumanColor else FourAiColor,
                                    if (v == FourWorld.HUMAN) humanEmoji else aiEmoji,
                                    emojiPaint
                                )
                            }
                        }
                    }

                    world.drop?.let { d ->
                        drawDisc(
                            center(d.pos, d.col),
                            radius,
                            if (d.player == FourWorld.HUMAN) FourHumanColor else FourAiColor,
                            if (d.player == FourWorld.HUMAN) humanEmoji else aiEmoji,
                            emojiPaint
                        )
                    }

                    val pad = c * 0.1f
                    val path = Path().apply {
                        fillType = PathFillType.EvenOdd

                        addRoundRect(
                            RoundRect(
                                geo.x - pad,
                                geo.y - pad,
                                geo.x + c * FourWorld.COLS + pad,
                                geo.y + c * FourWorld.ROWS + pad,
                                CornerRadius(c * 0.35f, c * 0.35f)
                            )
                        )

                        for (r in 0 until FourWorld.ROWS) {
                            for (col in 0 until FourWorld.COLS) {
                                val p = center(r.toFloat(), col)

                                addOval(Rect(p.x - radius, p.y - radius, p.x + radius, p.y + radius))
                            }
                        }
                    }

                    drawPath(path, FourBoard)

                    for (r in 0 until FourWorld.ROWS) {
                        for (col in 0 until FourWorld.COLS) {
                            drawCircle(
                                color = FourBoardDark,
                                radius = radius,
                                center = center(r.toFloat(), col),
                                style = Stroke(width = c * 0.05f)
                            )
                        }
                    }

                    drawRoundRect(
                        color = FourBoardDark,
                        topLeft = Offset(geo.x - pad, geo.y - pad),
                        size = Size(c * FourWorld.COLS + pad * 2f, c * FourWorld.ROWS + pad * 2f),
                        cornerRadius = CornerRadius(c * 0.35f, c * 0.35f),
                        style = Stroke(width = c * 0.07f)
                    )

                    if (world.over) {
                        val pulse = 0.55f + 0.45f * sin(tick * 0.14f)

                        for ((r, col) in world.winCells) {
                            drawCircle(
                                color = Color.White.copy(alpha = pulse),
                                radius = radius * 1.04f,
                                center = center(r.toFloat(), col),
                                style = Stroke(width = c * 0.08f)
                            )
                        }
                    }
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
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CuteButton(
                    text = "Menu",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.stop()
                        onBack()
                    }
                )

                CuteButton(
                    text = "New game",
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (phase == FourPhase.PLAYING) startGame(level)
                    }
                )
            }
        }

        when (phase) {
            FourPhase.START -> PopupCard {
                Text(
                    text = "Connect 4",
                    color = FourTitle,
                    fontSize = 34.sp,
                    fontFamily = FourFont
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Line up four discs before the AI does. Pick how smart it is.",
                    color = FourInk,
                    fontSize = 15.sp,
                    fontFamily = FourFont,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                for (i in 0 until 4) {
                    val wins = winsUi[i]

                    CuteButton(
                        text = "${LEVEL_NAMES[i]}  -  $wins ${if (wins == 1) "win" else "wins"}",
                        primary = i == 1,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { startGame(i + 1) }
                    )

                    Spacer(Modifier.height(10.dp))
                }

                CuteButton(
                    text = "Back",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            FourPhase.OVER -> PopupCard {
                Text(
                    text = resultText,
                    color = if (resultText == "You win!") FourGood else FourTitle,
                    fontSize = 32.sp,
                    fontFamily = FourFont
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "AI level: ${LEVEL_NAMES[(level - 1).coerceIn(0, 3)]}",
                    color = FourInk.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    fontFamily = FourFont
                )

                Spacer(Modifier.height(18.dp))

                CuteButton(
                    text = "Play again",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame(level) }
                )

                Spacer(Modifier.height(10.dp))

                CuteButton(
                    text = "Change level",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        world.start(level, true)
                        humanFirst[0] = true
                        phase = FourPhase.START
                    }
                )

                Spacer(Modifier.height(10.dp))

                CuteButton(
                    text = "Menu",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            FourPhase.PLAYING -> Unit
        }
    }
}

@Composable
private fun TurnChip(label: String, color: Color, emoji: String, active: Boolean) {
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (active) FourBtnSoft else Color.Transparent)
            .border(2.dp, if (active) FourBtnDark else Color.Transparent, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 26.sp)

        Spacer(Modifier.width(8.dp))

        Text(
            text = label,
            color = FourInk,
            fontSize = 18.sp,
            fontFamily = FourFont
        )
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1330).copy(alpha = 0.55f))
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
                .background(FourPanel)
                .border(7.dp, FourBoard, shape)
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
    val shape = RoundedCornerShape(14.dp)
    val top = if (primary) Color(0xFF3B79F2) else Color(0xFF5B8FE8)
    val bottom = if (primary) FourBoard else FourBoardDark

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(2.dp, FourBoardDark, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 17.sp,
            fontFamily = FourFont,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 13.dp)
        )
    }
}