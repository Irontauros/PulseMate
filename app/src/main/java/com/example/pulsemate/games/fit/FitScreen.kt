package com.example.pulsemate.games.fit.screens

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pulsemate.games.fit.FitBgBottom
import com.example.pulsemate.games.fit.FitBgTop
import com.example.pulsemate.games.fit.FitBtnDark
import com.example.pulsemate.games.fit.FitBtnFace
import com.example.pulsemate.games.fit.FitBtnSoft
import com.example.pulsemate.games.fit.FitBtnSoftDark
import com.example.pulsemate.games.fit.FitColors
import com.example.pulsemate.games.fit.FitEdge
import com.example.pulsemate.games.fit.FitEvent
import com.example.pulsemate.games.fit.FitEventType
import com.example.pulsemate.games.fit.FitFont
import com.example.pulsemate.games.fit.FitFrame
import com.example.pulsemate.games.fit.FitHaptics
import com.example.pulsemate.games.fit.FitInk
import com.example.pulsemate.games.fit.FitPanel
import com.example.pulsemate.games.fit.FitSlot
import com.example.pulsemate.games.fit.FitSlotLine
import com.example.pulsemate.games.fit.FitTitle
import com.example.pulsemate.games.fit.FitTray
import com.example.pulsemate.games.fit.FitWorld
import com.example.pulsemate.games.fit.Piece
import com.example.pulsemate.haptics.LoveSpouseController
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS_NAME = "pulsemate_fit"
private const val LEVEL_KEY = "level"
private const val LIFT_CELLS = 1.4f

private enum class FitPhase { START, PLAYING }

private class FitLayout(
    val bx: Float,
    val by: Float,
    val cell: Float,
    val trayCell: Float,
    val trayTop: Float,
    val trayBottom: Float,
    val width: Float,
    val slots: List<Rect>
)

private fun loadLevel(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(LEVEL_KEY, 1)
}

private fun saveLevel(context: Context, value: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(LEVEL_KEY, value)
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

private fun layoutFor(width: Float, height: Float, world: FitWorld): FitLayout {
    val boardH = height * 0.56f
    val cell = min(width * 0.92f / max(world.gw, 1), boardH * 0.94f / max(world.gh, 1))
    val bx = (width - cell * world.gw) / 2f
    val by = (boardH - cell * world.gh) / 2f + height * 0.01f
    val trayTop = height * 0.6f
    val trayBottom = height * 0.985f
    val n = max(world.pieces.size, 1)
    val perRow = if (n <= 4) n else (n + 1) / 2
    val rows = (n + perRow - 1) / perRow
    val slotW = width * 0.94f / perRow
    val slotH = (trayBottom - trayTop) / rows
    val span = if (world.pieces.isEmpty()) 4 else world.pieces.maxOf { max(it.width, it.height) }
    val trayCell = min(min(slotW, slotH) * 0.9f / span, cell * 0.9f)
    val slots = ArrayList<Rect>()

    for (i in 0 until world.pieces.size) {
        val row = i / perRow
        val col = i % perRow
        val inRow = min(perRow, n - row * perRow)
        val startX = (width - inRow * slotW) / 2f
        val left = startX + col * slotW
        val top = trayTop + row * slotH

        slots.add(Rect(left, top, left + slotW, top + slotH))
    }

    return FitLayout(bx, by, cell, trayCell, trayTop, trayBottom, width, slots)
}

private fun snapOf(layout: FitLayout, piece: Piece, x: Float, y: Float): Pair<Int, Int> {
    val left = x - piece.width * layout.cell / 2f
    val top = y - layout.cell * LIFT_CELLS - piece.height * layout.cell / 2f

    return Pair(
        ((left - layout.bx) / layout.cell).roundToInt(),
        ((top - layout.by) / layout.cell).roundToInt()
    )
}

private fun lighter(color: Color, alpha: Float): Color {
    return Color(color.red * 0.72f, color.green * 0.72f, color.blue * 0.72f, alpha)
}

private fun DrawScope.drawPiece(
    cells: List<Pair<Int, Int>>,
    left: Float,
    top: Float,
    cell: Float,
    color: Color,
    alpha: Float
) {
    val set = cells.toHashSet()
    val fill = color.copy(alpha = alpha)
    val edge = lighter(color, alpha)
    val line = cell * 0.07f

    for ((cx, cy) in cells) {
        val x = left + cx * cell
        val y = top + cy * cell

        drawRect(color = fill, topLeft = Offset(x, y), size = Size(cell, cell))
        drawRect(
            color = Color.White.copy(alpha = 0.14f * alpha),
            topLeft = Offset(x + cell * 0.12f, y + cell * 0.12f),
            size = Size(cell * 0.76f, cell * 0.12f)
        )
    }

    for ((cx, cy) in cells) {
        val x = left + cx * cell
        val y = top + cy * cell

        if (!set.contains(Pair(cx, cy - 1))) {
            drawLine(edge, Offset(x, y), Offset(x + cell, y), line, StrokeCap.Round)
        }

        if (!set.contains(Pair(cx, cy + 1))) {
            drawLine(edge, Offset(x, y + cell), Offset(x + cell, y + cell), line, StrokeCap.Round)
        }

        if (!set.contains(Pair(cx - 1, cy))) {
            drawLine(edge, Offset(x, y), Offset(x, y + cell), line, StrokeCap.Round)
        }

        if (!set.contains(Pair(cx + 1, cy))) {
            drawLine(edge, Offset(x + cell, y), Offset(x + cell, y + cell), line, StrokeCap.Round)
        }
    }
}

@Composable
fun FitScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { FitHaptics(loveSpouse) }
    val world = remember { FitWorld() }
    val lastKey = remember { IntArray(1) { -1 } }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(FitPhase.START) }
    var levelUi by remember { mutableIntStateOf(1) }
    var shapeUi by remember { mutableStateOf("") }
    var placedUi by remember { mutableIntStateOf(0) }
    var totalUi by remember { mutableIntStateOf(0) }
    var savedLevel by remember { mutableIntStateOf(loadLevel(context)) }
    var banner by remember { mutableStateOf(false) }
    var dragId by remember { mutableIntStateOf(-1) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    fun handle(events: List<FitEvent>) {
        if (events.isEmpty()) return

        var level = 0
        var millis = 0L

        for (e in events) {
            val pair: Pair<Int, Long> = when (e.type) {
                FitEventType.PLACE -> {
                    val total = max(world.pieces.size, 1)
                    Pair((3 + e.value * 4 / total).coerceIn(3, 7), 220L)
                }

                FitEventType.INVALID -> Pair(2, 150L)
                FitEventType.LIFT -> Pair(1, 60L)
                FitEventType.LEVEL_CLEAR -> Pair(9, 1000L)
            }

            if (pair.first > level || (pair.first == level && pair.second > millis)) {
                level = pair.first
                millis = pair.second
            }

            if (e.type == FitEventType.LEVEL_CLEAR) {
                banner = true
                saveLevel(context, world.level + 1)
                savedLevel = world.level + 1

                scope.launch {
                    delay(1600)
                    world.nextLevel()
                    banner = false
                }
            }
        }

        haptics.pulse(scope, level, millis)
    }

    fun startAt(level: Int) {
        world.startLevel(level)
        haptics.reset()
        banner = false
        dragId = -1
        phase = FitPhase.PLAYING
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
        while (true) {
            withFrameNanos { it }

            if (phase == FitPhase.PLAYING) {
                levelUi = world.level
                shapeUi = world.shapeName
                placedUi = world.placedCount
                totalUi = world.pieces.size
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FitBgTop, FitBgBottom)))
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Level $levelUi",
                        color = FitInk,
                        fontSize = 24.sp,
                        fontFamily = FitFont
                    )

                    Text(
                        text = shapeUi,
                        color = FitInk.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        fontFamily = FitFont
                    )
                }

                Text(
                    text = "$placedUi / $totalUi",
                    color = FitInk,
                    fontSize = 22.sp,
                    fontFamily = FitFont
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

                                if (phase != FitPhase.PLAYING || banner) return@awaitEachGesture

                                val layout = layoutFor(size.width.toFloat(), size.height.toFloat(), world)
                                val cx = Math.floor(((down.position.x - layout.bx) / layout.cell).toDouble()).toInt()
                                val cy = Math.floor(((down.position.y - layout.by) / layout.cell).toDouble()).toInt()
                                var id = -1
                                var fromBoard = false

                                if (cx in 0 until world.gw && cy in 0 until world.gh && world.grid[cy][cx] >= 0) {
                                    id = world.grid[cy][cx]
                                    fromBoard = true
                                } else {
                                    for (i in layout.slots.indices) {
                                        if (!world.pieces[i].placed && layout.slots[i].contains(down.position)) {
                                            id = i
                                            break
                                        }
                                    }
                                }

                                if (id < 0) return@awaitEachGesture

                                var moved = false

                                lastKey[0] = -1

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (!change.pressed) {
                                        if (moved) {
                                            val piece = world.pieces[id]
                                            val snap = snapOf(layout, piece, change.position.x, change.position.y)

                                            if (world.canPlace(piece, snap.first, snap.second)) {
                                                handle(world.place(id, snap.first, snap.second))
                                            } else if (change.position.y < layout.trayTop) {
                                                handle(listOf(FitEvent(FitEventType.INVALID)))
                                            }
                                        } else if (fromBoard) {
                                            handle(world.lift(id))
                                        }

                                        break
                                    }

                                    val pos = change.position

                                    if (!moved && (pos - down.position).getDistance() > viewConfiguration.touchSlop) {
                                        moved = true

                                        if (fromBoard) {
                                            handle(world.lift(id))
                                        } else {
                                            handle(listOf(FitEvent(FitEventType.LIFT)))
                                        }

                                        dragId = id
                                    }

                                    if (moved) {
                                        dragX = pos.x
                                        dragY = pos.y

                                        val piece = world.pieces[id]
                                        val snap = snapOf(layout, piece, pos.x, pos.y)
                                        val valid = world.canPlace(piece, snap.first, snap.second)
                                        val key = if (valid) snap.second * 100 + snap.first else -1

                                        if (valid && key != lastKey[0]) haptics.pulse(scope, 3, 60L)

                                        lastKey[0] = key
                                        change.consume()
                                    }
                                }

                                dragId = -1
                                lastKey[0] = -1
                            }
                        }
                ) {
                    val tick = frame

                    if (tick >= 0 && world.gw > 0) {
                        val layout = layoutFor(size.width, size.height, world)
                        val cell = layout.cell

                        drawRoundRect(
                            color = FitTray,
                            topLeft = Offset(size.width * 0.03f, layout.trayTop),
                            size = Size(size.width * 0.94f, layout.trayBottom - layout.trayTop),
                            cornerRadius = CornerRadius(cell * 0.4f, cell * 0.4f)
                        )

                        for (y in 0 until world.gh) {
                            for (x in 0 until world.gw) {
                                if (world.grid[y][x] == FitWorld.VOID) continue

                                drawRect(
                                    color = FitSlot,
                                    topLeft = Offset(layout.bx + x * cell, layout.by + y * cell),
                                    size = Size(cell, cell)
                                )

                                drawRect(
                                    color = FitSlotLine,
                                    topLeft = Offset(layout.bx + x * cell, layout.by + y * cell),
                                    size = Size(cell, cell),
                                    style = Stroke(width = 1.5f)
                                )
                            }
                        }

                        for (p in world.pieces) {
                            if (p.placed) {
                                drawPiece(
                                    p.cells,
                                    layout.bx + p.px * cell,
                                    layout.by + p.py * cell,
                                    cell,
                                    FitColors[p.color % FitColors.size],
                                    1f
                                )
                            }
                        }

                        for (y in 0 until world.gh) {
                            for (x in 0 until world.gw) {
                                if (world.grid[y][x] == FitWorld.VOID) continue

                                val l = layout.bx + x * cell
                                val t = layout.by + y * cell
                                val w = cell * 0.09f

                                fun isVoid(xx: Int, yy: Int): Boolean {
                                    return xx < 0 || yy < 0 || xx >= world.gw || yy >= world.gh ||
                                            world.grid[yy][xx] == FitWorld.VOID
                                }

                                for (pass in 0..1) {
                                    val col = if (pass == 0) FitFrame.copy(alpha = 0.06f) else FitFrame
                                    val wd = if (pass == 0) w * 2.6f else w * 0.7f

                                    if (isVoid(x, y - 1)) drawLine(col, Offset(l, t), Offset(l + cell, t), wd, StrokeCap.Round)
                                    if (isVoid(x, y + 1)) drawLine(col, Offset(l, t + cell), Offset(l + cell, t + cell), wd, StrokeCap.Round)
                                    if (isVoid(x - 1, y)) drawLine(col, Offset(l, t), Offset(l, t + cell), wd, StrokeCap.Round)
                                    if (isVoid(x + 1, y)) drawLine(col, Offset(l + cell, t), Offset(l + cell, t + cell), wd, StrokeCap.Round)
                                }
                            }
                        }

                        for (i in world.pieces.indices) {
                            val p = world.pieces[i]

                            if (p.placed || i == dragId || i >= layout.slots.size) continue

                            val slot = layout.slots[i]
                            val tc = layout.trayCell

                            drawPiece(
                                p.cells,
                                slot.center.x - p.width * tc / 2f,
                                slot.center.y - p.height * tc / 2f,
                                tc,
                                FitColors[p.color % FitColors.size],
                                1f
                            )
                        }

                        if (dragId in world.pieces.indices) {
                            val p = world.pieces[dragId]
                            val color = FitColors[p.color % FitColors.size]
                            val snap = snapOf(layout, p, dragX, dragY)

                            if (world.canPlace(p, snap.first, snap.second)) {
                                drawPiece(
                                    p.cells,
                                    layout.bx + snap.first * cell,
                                    layout.by + snap.second * cell,
                                    cell,
                                    color,
                                    0.4f
                                )
                            }

                            val left = dragX - p.width * cell / 2f
                            val top = dragY - cell * LIFT_CELLS - p.height * cell / 2f

                            drawPiece(p.cells, left + 5f, top + 8f, cell, Color.Black, 0.12f)
                            drawPiece(p.cells, left, top, cell, color, 0.95f)
                        }
                    }
                }

                if (banner) {
                    Text(
                        text = "Level clear!",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontFamily = FitFont,
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
                            .background(FitBtnFace)
                            .border(3.dp, FitBtnDark, RoundedCornerShape(24.dp))
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
                    text = "Hint",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (phase == FitPhase.PLAYING && !banner && dragId < 0) handle(world.hint())
                    }
                )

                CuteButton(
                    text = "Restart",
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (phase == FitPhase.PLAYING && !banner && dragId < 0) world.restart()
                    }
                )
            }
        }

        if (phase == FitPhase.START) {
            PopupCard {
                Text(
                    text = "Shape Fit",
                    color = FitTitle,
                    fontSize = 34.sp,
                    fontFamily = FitFont
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (color in FitColors.take(6)) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(color)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Drag the pieces into the shape until it is full. Every piece fits exactly one way.",
                    color = FitInk,
                    fontSize = 15.sp,
                    fontFamily = FitFont,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))

                if (savedLevel > 1) {
                    CuteButton(
                        text = "Continue (Level $savedLevel)",
                        primary = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { startAt(savedLevel) }
                    )

                    Spacer(Modifier.height(10.dp))

                    CuteButton(
                        text = "New game",
                        primary = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            saveLevel(context, 1)
                            savedLevel = 1
                            startAt(1)
                        }
                    )
                } else {
                    CuteButton(
                        text = "Play",
                        primary = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { startAt(1) }
                    )
                }

                Spacer(Modifier.height(10.dp))

                CuteButton(
                    text = "Back",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)

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
                .drawBehind {
                    drawRoundRect(
                        color = FitEdge.copy(alpha = 0.05f),
                        topLeft = Offset(-10f, -10f),
                        size = Size(size.width + 20f, size.height + 20f),
                        cornerRadius = CornerRadius(22f, 22f)
                    )
                }
                .clip(shape)
                .background(FitPanel)
                .border(2.dp, FitEdge, shape)
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
    val shape = RoundedCornerShape(6.dp)
    val neon = if (primary) FitBtnFace else FitBtnSoftDark

    Box(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = neon.copy(alpha = 0.05f),
                    topLeft = Offset(-5f, -5f),
                    size = Size(size.width + 10f, size.height + 10f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
            }
            .clip(shape)
            .background(if (primary) neon.copy(alpha = 0.18f) else FitBtnSoft)
            .border(2.dp, neon, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = neon,
            fontSize = 15.sp,
            letterSpacing = 1.sp,
            fontFamily = FitFont,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
}