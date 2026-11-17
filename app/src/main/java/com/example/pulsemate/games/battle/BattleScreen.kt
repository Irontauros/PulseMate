package com.example.pulsemate.games.battle.screens

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
import com.example.pulsemate.games.battle.BattleAiEngine
import com.example.pulsemate.games.battle.BattleBgBottom
import com.example.pulsemate.games.battle.BattleBgTop
import com.example.pulsemate.games.battle.BattleBtnDark
import com.example.pulsemate.games.battle.BattleBtnFace
import com.example.pulsemate.games.battle.BattleBtnSoft
import com.example.pulsemate.games.battle.BattleBtnSoftDark
import com.example.pulsemate.games.battle.BattleEdge
import com.example.pulsemate.games.battle.BattleEvent
import com.example.pulsemate.games.battle.BattleEventType
import com.example.pulsemate.games.battle.BattleFont
import com.example.pulsemate.games.battle.BattleFrame
import com.example.pulsemate.games.battle.BattleGold
import com.example.pulsemate.games.battle.BattleGood
import com.example.pulsemate.games.battle.BattleGrid
import com.example.pulsemate.games.battle.BattleHaptics
import com.example.pulsemate.games.battle.BattleHit
import com.example.pulsemate.games.battle.BattleInk
import com.example.pulsemate.games.battle.BattlePanel
import com.example.pulsemate.games.battle.BattleSelect
import com.example.pulsemate.games.battle.BattleShipAccents
import com.example.pulsemate.games.battle.BattleShipColors
import com.example.pulsemate.games.battle.BattleTitle
import com.example.pulsemate.games.battle.BattleWaterA
import com.example.pulsemate.games.battle.BattleWaterB
import com.example.pulsemate.games.battle.BattleWorld
import com.example.pulsemate.games.battle.Fleet
import com.example.pulsemate.games.battle.Ship
import com.example.pulsemate.haptics.LoveSpouseController
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "pulsemate_battle"
private const val FX_SECONDS = 0.6f
private val LEVEL_NAMES = listOf("Easy", "Medium", "Hard", "Expert")

private enum class BattlePhase { START, PLACING, PLAYING, OVER }

private class Geo(
    val bx: Float,
    val by: Float,
    val c1: Float,
    val mx: Float,
    val my: Float,
    val c2: Float
)

private class Fx(val board: Int, val x: Int, val y: Int, val kind: Int) {
    var age = 0f
}

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

private fun geoFor(width: Float, height: Float, battle: Boolean): Geo {
    val n = BattleWorld.N.toFloat()

    if (!battle) {
        val c = min(width * 0.94f / n, height * 0.94f / n)

        return Geo((width - c * n) / 2f, (height - c * n) / 2f, c, 0f, 0f, 0f)
    }

    val c1 = min(width * 0.94f / n, height * 0.97f / 14.6f)
    val c2 = c1 * 0.42f
    val total = n * c1 + c1 * 0.4f + n * c2
    val top = (height - total) / 2f

    return Geo(
        (width - c1 * n) / 2f,
        top,
        c1,
        (width - c2 * n) / 2f,
        top + n * c1 + c1 * 0.4f,
        c2
    )
}

private fun darker(color: Color): Color {
    return Color(color.red * 0.65f, color.green * 0.65f, color.blue * 0.65f, color.alpha)
}

private fun lighter(color: Color, amount: Float): Color {
    return Color(
        color.red + (1f - color.red) * amount,
        color.green + (1f - color.green) * amount,
        color.blue + (1f - color.blue) * amount,
        color.alpha
    )
}

private fun DrawScope.drawShip(
    s: Ship,
    left: Float,
    top: Float,
    c: Float,
    color: Color,
    alpha: Float,
    sunk: Boolean = false
) {
    val horizontal = s.horizontal
    val length = s.len * c
    val width = c * 0.78f
    val off = (c - width) / 2f
    val base = if (sunk) Color(0xFF3A3F45) else color
    val hull = base.copy(alpha = alpha)
    val outline = darker(base).copy(alpha = alpha)

    fun pt(u: Float, v: Float): Offset {
        return if (horizontal) {
            Offset(left + s.x * c + u, top + s.y * c + off + v)
        } else {
            Offset(left + s.x * c + off + v, top + s.y * c + u)
        }
    }

    fun quad(u0: Float, v0: Float, u1: Float, v1: Float, fill: Color) {
        val path = Path().apply {
            val p0 = pt(u0, v0)
            val p1 = pt(u1, v0)
            val p2 = pt(u1, v1)
            val p3 = pt(u0, v1)

            moveTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            close()
        }

        drawPath(path, fill)
    }

    if (s.id == 3) {
        val a = pt(c * 0.06f, width * 0.12f)
        val b = pt(length - c * 0.06f, width * 0.88f)
        val tl = Offset(minOf(a.x, b.x), minOf(a.y, b.y))
        val sz = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y))
        val radius = CornerRadius(width * 0.4f, width * 0.4f)

        drawRoundRect(hull, tl, sz, radius)
        drawRoundRect(outline, tl, sz, radius, Stroke(c * 0.05f))

        quad(length * 0.42f, width * 0.3f, length * 0.62f, width * 0.7f, lighter(base, 0.12f).copy(alpha = alpha))
        quad(length * 0.46f, width * 0.36f, length * 0.58f, width * 0.64f, darker(base).copy(alpha = alpha))

        val m0 = pt(length * 0.6f, width * 0.5f)
        val m1 = pt(length * 0.6f + c * 0.22f, width * 0.5f)

        drawLine(outline, m0, m1, c * 0.04f, StrokeCap.Round)

        return
    }

    val bow = length - width * 0.95f
    val hullPath = Path().apply {
        val p0 = pt(c * 0.05f, width * 0.12f)
        val p1 = pt(bow, 0f)
        val p2 = pt(length - c * 0.05f, width * 0.5f)
        val p3 = pt(bow, width)
        val p4 = pt(c * 0.05f, width * 0.88f)

        moveTo(p0.x, p0.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        lineTo(p4.x, p4.y)
        close()
    }

    drawPath(hullPath, hull)
    drawPath(hullPath, outline, style = Stroke(c * 0.05f))

    val accent = BattleShipAccents[s.id % BattleShipAccents.size].copy(alpha = alpha)

    drawLine(accent, pt(c * 0.12f, width * 0.17f), pt(bow, width * 0.05f), c * 0.06f, StrokeCap.Round)
    drawLine(accent, pt(c * 0.12f, width * 0.83f), pt(bow, width * 0.95f), c * 0.06f, StrokeCap.Round)

    val deck = lighter(base, 0.14f).copy(alpha = alpha)
    val deckPath = Path().apply {
        val p0 = pt(c * 0.14f, width * 0.26f)
        val p1 = pt(bow, width * 0.16f)
        val p2 = pt(length - c * 0.24f, width * 0.5f)
        val p3 = pt(bow, width * 0.84f)
        val p4 = pt(c * 0.14f, width * 0.74f)

        moveTo(p0.x, p0.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        lineTo(p4.x, p4.y)
        close()
    }

    drawPath(deckPath, deck)

    val detail = darker(base).copy(alpha = alpha)

    if (s.id == 0) {
        quad(length * 0.1f, width * 0.3f, length * 0.9f, width * 0.7f, lighter(base, 0.22f).copy(alpha = alpha))

        for (k in 0 until 6) {
            val u = length * (0.16f + 0.13f * k)

            drawLine(
                Color.White.copy(alpha = 0.7f * alpha),
                pt(u, width * 0.5f),
                pt(u + length * 0.06f, width * 0.5f),
                c * 0.035f,
                StrokeCap.Round
            )
        }

        quad(length * 0.3f, width * 0.08f, length * 0.46f, width * 0.3f, detail)
    } else {
        val turrets = when (s.len) {
            2 -> listOf(0.72f)
            3 -> listOf(0.78f, 0.22f)
            else -> listOf(0.8f, 0.2f)
        }

        for (t in turrets) {
            val center = pt(length * t, width * 0.5f)
            val barrel = pt(length * t + width * 0.42f, width * 0.5f)

            drawLine(detail, center, barrel, c * 0.07f, StrokeCap.Round)
            drawCircle(detail, width * 0.18f, center)
            drawCircle(lighter(base, 0.25f).copy(alpha = alpha), width * 0.09f, center)
        }

        val bridgeU = if (s.len == 2) 0.34f else 0.5f

        quad(
            length * bridgeU - c * 0.18f,
            width * 0.3f,
            length * bridgeU + c * 0.18f,
            width * 0.7f,
            detail
        )

        quad(
            length * bridgeU - c * 0.1f,
            width * 0.38f,
            length * bridgeU + c * 0.1f,
            width * 0.62f,
            lighter(base, 0.3f).copy(alpha = alpha)
        )

        if (s.len >= 4) {
            drawCircle(detail, width * 0.1f, pt(length * 0.36f, width * 0.5f))
        }
    }
}

private fun DrawScope.drawSea(
    fleet: Fleet,
    left: Float,
    top: Float,
    c: Float,
    showAll: Boolean,
    dimShips: Boolean
) {
    val n = BattleWorld.N

    for (y in 0 until n) {
        for (x in 0 until n) {
            drawRect(
                color = if ((x + y) % 2 == 0) BattleWaterA else BattleWaterB,
                topLeft = Offset(left + x * c, top + y * c),
                size = Size(c, c)
            )

            drawLine(
                color = Color.White.copy(alpha = 0.07f),
                start = Offset(left + x * c + c * 0.2f, top + y * c + c * 0.7f),
                end = Offset(left + x * c + c * 0.5f, top + y * c + c * 0.62f),
                strokeWidth = c * 0.03f,
                cap = StrokeCap.Round
            )
        }
    }

    for (i in 0..n) {
        drawLine(BattleGrid, Offset(left + i * c, top), Offset(left + i * c, top + n * c), 1.5f)
        drawLine(BattleGrid, Offset(left, top + i * c), Offset(left + n * c, top + i * c), 1.5f)
    }

    for (s in fleet.ships) {
        val sunk = fleet.isSunk(s)

        if (showAll || sunk) {
            drawShip(
                s,
                left,
                top,
                c,
                BattleShipColors[s.id % BattleShipColors.size],
                if (dimShips && !sunk) 0.6f else 1f,
                sunk
            )
        }
    }

    for (y in 0 until n) {
        for (x in 0 until n) {
            val v = fleet.shots[y][x]
            val cx = left + (x + 0.5f) * c
            val cy = top + (y + 0.5f) * c

            if (v == 1) {
                drawCircle(Color.White.copy(alpha = 0.85f), c * 0.2f, Offset(cx, cy), style = Stroke(c * 0.05f))
                drawCircle(Color.White.copy(alpha = 0.85f), c * 0.07f, Offset(cx, cy))
            } else if (v == 2) {
                drawCircle(Color(0xFF1A1A1A).copy(alpha = 0.35f), c * 0.3f, Offset(cx, cy - c * 0.14f))
                drawCircle(BattleHit, c * 0.24f, Offset(cx, cy))
                drawCircle(Color(0xFFFFC107), c * 0.14f, Offset(cx, cy + c * 0.02f))
                drawCircle(Color.White.copy(alpha = 0.9f), c * 0.06f, Offset(cx, cy + c * 0.03f))
            }
        }
    }

    drawRoundRect(
        color = BattleFrame,
        topLeft = Offset(left, top),
        size = Size(n * c, n * c),
        cornerRadius = CornerRadius(c * 0.1f, c * 0.1f),
        style = Stroke(width = c * 0.1f)
    )
}

@Composable
fun BattleScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { BattleHaptics(loveSpouse) }
    val world = remember { BattleWorld() }
    val random = remember { Random(System.nanoTime()) }
    val fxList = remember { ArrayList<Fx>() }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(BattlePhase.START) }
    var level by remember { mutableIntStateOf(2) }
    var turnUi by remember { mutableIntStateOf(BattleWorld.PLAYER) }
    var selX by remember { mutableIntStateOf(-1) }
    var selY by remember { mutableIntStateOf(-1) }
    var dragShip by remember { mutableIntStateOf(-1) }
    var dragTx by remember { mutableIntStateOf(0) }
    var dragTy by remember { mutableIntStateOf(0) }
    var dragValid by remember { mutableStateOf(true) }
    var enemyLeft by remember { mutableIntStateOf(5) }
    var youLeft by remember { mutableIntStateOf(5) }
    var winsUi by remember { mutableStateOf(List(4) { winsOf(context, it + 1) }) }
    var resultText by remember { mutableStateOf("") }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    fun handle(events: List<BattleEvent>) {
        var strength = 0
        var millis = 0L

        for (e in events) {
            val mine = e.shooter == BattleWorld.PLAYER

            val pair: Pair<Int, Long> = when (e.type) {
                BattleEventType.MISS -> if (mine) Pair(1, 70L) else Pair(1, 40L)
                BattleEventType.HIT -> if (mine) Pair(6, 250L) else Pair(7, 300L)
                BattleEventType.SUNK ->
                    if (mine) Pair((4 + e.len).coerceAtMost(9), 450L + e.len * 80L)
                    else Pair((4 + e.len).coerceAtMost(9), 900L)

                BattleEventType.WIN -> Pair(9, 1200L)
                BattleEventType.LOSE -> Pair(7, 1000L)
            }

            if (pair.first > strength || (pair.first == strength && pair.second > millis)) {
                strength = pair.first
                millis = pair.second
            }

            if (e.type == BattleEventType.MISS || e.type == BattleEventType.HIT || e.type == BattleEventType.SUNK) {
                val kind = when (e.type) {
                    BattleEventType.MISS -> 0
                    BattleEventType.HIT -> 1
                    else -> 2
                }

                fxList.add(Fx(if (mine) 0 else 1, e.x, e.y, kind))
            }

            if (e.type == BattleEventType.WIN || e.type == BattleEventType.LOSE) {
                resultText = if (e.type == BattleEventType.WIN) "Victory!" else "Defeat"

                if (e.type == BattleEventType.WIN) {
                    addWin(context, level)
                    winsUi = List(4) { winsOf(context, it + 1) }
                }

                val id = world.gameId

                scope.launch {
                    delay(1500)
                    if (world.gameId == id) phase = BattlePhase.OVER
                }
            }
        }

        enemyLeft = world.enemy.shipsLeft()
        youLeft = world.player.shipsLeft()

        if (strength > 0) haptics.pulse(scope, strength, millis)
    }

    fun enemyTurn() {
        if (world.over) return

        val id = world.gameId

        scope.launch {
            delay(1000)

            val shot = withContext(Dispatchers.Default) {
                BattleAiEngine.choose(world.player, world.difficulty, random)
            }

            if (world.gameId == id && phase == BattlePhase.PLAYING) {
                handle(world.aiFire(shot.first, shot.second))
                turnUi = world.turn
            }
        }
    }

    fun fireSelected() {
        if (phase != BattlePhase.PLAYING || world.turn != BattleWorld.PLAYER || world.over) return
        if (selX < 0 || selY < 0) return

        val events = world.fire(selX, selY)

        if (events.isEmpty()) return

        selX = -1
        selY = -1
        handle(events)
        turnUi = world.turn
        enemyTurn()
    }

    fun newGame(chosen: Int) {
        level = chosen
        world.newGame(chosen)
        haptics.reset()
        fxList.clear()
        selX = -1
        selY = -1
        dragShip = -1
        enemyLeft = 5
        youLeft = 5
        turnUi = BattleWorld.PLAYER
        phase = BattlePhase.PLACING
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

            val iterator = fxList.iterator()

            while (iterator.hasNext()) {
                val fx = iterator.next()

                fx.age += seconds

                if (fx.age >= FX_SECONDS) iterator.remove()
            }

            frame++
        }
    }

    val status = when (phase) {
        BattlePhase.PLACING -> "Drag ships to move, tap to rotate"
        BattlePhase.PLAYING ->
            if (turnUi != BattleWorld.PLAYER) "Enemy is aiming..."
            else if (selX >= 0) "Tap again or press Fire"
            else "Pick a target"

        else -> " "
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BattleBgTop, BattleBgBottom)))
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Enemy ships $enemyLeft",
                    color = BattleInk,
                    fontSize = 17.sp,
                    fontFamily = BattleFont
                )

                Text(
                    text = "Your ships $youLeft",
                    color = BattleInk,
                    fontSize = 17.sp,
                    fontFamily = BattleFont
                )
            }

            Text(
                text = status,
                color = BattleInk.copy(alpha = 0.75f),
                fontSize = 14.sp,
                fontFamily = BattleFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val battle = phase == BattlePhase.PLAYING
                            val geo = geoFor(size.width.toFloat(), size.height.toFloat(), battle)
                            val c = geo.c1
                            val cx = Math.floor(((down.position.x - geo.bx) / c).toDouble()).toInt()
                            val cy = Math.floor(((down.position.y - geo.by) / c).toDouble()).toInt()
                            val inside = cx in 0 until BattleWorld.N && cy in 0 until BattleWorld.N

                            if (phase == BattlePhase.PLAYING) {
                                waitForUpOrCancellation() ?: return@awaitEachGesture

                                if (!inside || world.turn != BattleWorld.PLAYER || world.over) return@awaitEachGesture
                                if (world.enemy.shots[cy][cx] != 0) return@awaitEachGesture

                                if (selX == cx && selY == cy) {
                                    fireSelected()
                                } else {
                                    selX = cx
                                    selY = cy
                                    haptics.pulse(scope, 1, 35L)
                                }

                                return@awaitEachGesture
                            }

                            if (phase != BattlePhase.PLACING) return@awaitEachGesture

                            val ship = if (inside) world.player.shipAt(cx, cy) else null

                            if (ship == null) {
                                waitForUpOrCancellation()
                                return@awaitEachGesture
                            }

                            val offX = cx - ship.x
                            val offY = cy - ship.y
                            var moved = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (!change.pressed) {
                                    if (moved) {
                                        if (world.moveShip(ship.id, dragTx, dragTy)) {
                                            haptics.pulse(scope, 2, 50L)
                                        }
                                    } else if (world.rotateShip(ship.id)) {
                                        haptics.pulse(scope, 2, 50L)
                                    } else {
                                        haptics.pulse(scope, 1, 90L)
                                    }

                                    break
                                }

                                val pos = change.position

                                if (!moved && (pos - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    moved = true
                                    dragShip = ship.id
                                }

                                if (moved) {
                                    dragTx = Math.floor(((pos.x - geo.bx) / c).toDouble()).toInt() - offX
                                    dragTy = Math.floor(((pos.y - geo.by) / c).toDouble()).toInt() - offY
                                    dragValid = world.canPlace(ship, dragTx, dragTy, ship.horizontal)
                                    change.consume()
                                }
                            }

                            dragShip = -1
                        }
                    }
            ) {
                val tick = frame

                if (tick >= 0 && phase != BattlePhase.START) {
                    val battle = phase == BattlePhase.PLAYING || phase == BattlePhase.OVER
                    val geo = geoFor(size.width, size.height, battle)

                    if (!battle) {
                        val c = geo.c1

                        drawSea(world.player, geo.bx, geo.by, c, false, false)

                        for (s in world.player.ships) {
                            if (s.id == dragShip) continue

                            drawShip(s, geo.bx, geo.by, c, BattleShipColors[s.id % BattleShipColors.size], 1f)
                        }

                        if (dragShip >= 0) {
                            val s = world.player.ships[dragShip]
                            val ghost = Ship(s.id, s.len, dragTx, dragTy, s.horizontal)
                            val color = if (dragValid) BattleShipColors[s.id % BattleShipColors.size] else BattleHit

                            drawShip(ghost, geo.bx, geo.by, c, color, 0.8f)
                        }
                    } else {
                        drawSea(world.enemy, geo.bx, geo.by, geo.c1, world.over, true)
                        drawSea(world.player, geo.mx, geo.my, geo.c2, true, false)

                        if (selX >= 0 && phase == BattlePhase.PLAYING) {
                            val c = geo.c1
                            val pulse = 0.6f + 0.4f * sin(tick * 0.15f)
                            val x = geo.bx + selX * c
                            val y = geo.by + selY * c

                            drawRoundRect(
                                color = BattleSelect.copy(alpha = pulse),
                                topLeft = Offset(x + c * 0.06f, y + c * 0.06f),
                                size = Size(c * 0.88f, c * 0.88f),
                                cornerRadius = CornerRadius(c * 0.2f, c * 0.2f),
                                style = Stroke(c * 0.08f)
                            )

                            drawLine(BattleSelect.copy(alpha = pulse), Offset(x + c * 0.5f, y + c * 0.2f), Offset(x + c * 0.5f, y + c * 0.8f), c * 0.05f, StrokeCap.Round)
                            drawLine(BattleSelect.copy(alpha = pulse), Offset(x + c * 0.2f, y + c * 0.5f), Offset(x + c * 0.8f, y + c * 0.5f), c * 0.05f, StrokeCap.Round)
                        }

                        for (fx in fxList) {
                            val big = fx.board == 0
                            val c = if (big) geo.c1 else geo.c2
                            val left = if (big) geo.bx else geo.mx
                            val top = if (big) geo.by else geo.my
                            val t = fx.age / FX_SECONDS
                            val color = when (fx.kind) {
                                0 -> Color.White
                                1 -> BattleHit
                                else -> BattleGold
                            }
                            val reach = if (fx.kind == 2) 1.6f else 1.0f

                            drawCircle(
                                color = color.copy(alpha = (1f - t).coerceIn(0f, 1f)),
                                radius = c * (0.25f + reach * t),
                                center = Offset(left + (fx.x + 0.5f) * c, top + (fx.y + 0.5f) * c),
                                style = Stroke(c * 0.1f)
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

                if (phase == BattlePhase.PLACING) {
                    CuteButton(
                        text = "Shuffle",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            world.shuffle()
                            haptics.pulse(scope, 2, 60L)
                        }
                    )

                    CuteButton(
                        text = "Ready!",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            world.begin()
                            turnUi = world.turn
                            phase = BattlePhase.PLAYING
                        }
                    )
                } else if (phase == BattlePhase.PLAYING) {
                    CuteButton(
                        text = "Fire!",
                        primary = true,
                        modifier = Modifier.weight(2f),
                        onClick = { fireSelected() }
                    )
                }
            }
        }

        when (phase) {
            BattlePhase.START -> PopupCard {
                Text(
                    text = "Battleship",
                    color = BattleTitle,
                    fontSize = 32.sp,
                    fontFamily = BattleFont
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Sink the enemy fleet before it sinks yours. Choose how clever the enemy is.",
                    color = BattleInk,
                    fontSize = 15.sp,
                    fontFamily = BattleFont,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                for (i in 0 until 4) {
                    val wins = winsUi[i]

                    CuteButton(
                        text = "${LEVEL_NAMES[i]}  -  $wins ${if (wins == 1) "win" else "wins"}",
                        primary = i == 1,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { newGame(i + 1) }
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

            BattlePhase.OVER -> PopupCard {
                Text(
                    text = resultText,
                    color = if (resultText == "Victory!") BattleGood else BattleTitle,
                    fontSize = 32.sp,
                    fontFamily = BattleFont
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Enemy level: ${LEVEL_NAMES[(level - 1).coerceIn(0, 3)]}",
                    color = BattleInk.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    fontFamily = BattleFont
                )

                Spacer(Modifier.height(18.dp))

                CuteButton(
                    text = "Play again",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { newGame(level) }
                )

                Spacer(Modifier.height(10.dp))

                CuteButton(
                    text = "Change level",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { phase = BattlePhase.START }
                )

                Spacer(Modifier.height(10.dp))

                CuteButton(
                    text = "Menu",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B2233).copy(alpha = 0.6f))
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
                .background(Brush.verticalGradient(listOf(Color(0xFFFFF8E6), BattlePanel)))
                .drawBehind {
                    for (p in listOf(
                        Offset(14f, 14f),
                        Offset(size.width - 14f, 14f),
                        Offset(14f, size.height - 14f),
                        Offset(size.width - 14f, size.height - 14f)
                    )) {
                        drawCircle(BattleEdge, 6f, p)
                        drawCircle(Color(0xFFB9C7D3), 4f, p)
                    }
                }
                .border(3.dp, BattleEdge, shape)
                .padding(26.dp),
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
        Brush.verticalGradient(listOf(Color(0xFFF9A64A), BattleBtnFace, BattleBtnDark))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF4C87B3), BattleBtnSoft, BattleBtnSoftDark))
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(2.dp, BattleEdge, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = Color.White,
            fontSize = 15.sp,
            letterSpacing = 1.5.sp,
            fontFamily = BattleFont,
            modifier = Modifier.padding(vertical = 13.dp, horizontal = 22.dp)
        )
    }
}