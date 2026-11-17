package com.example.pulsemate.games.whack.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pulsemate.games.whack.WhackBad
import com.example.pulsemate.games.whack.WhackBgBottom
import com.example.pulsemate.games.whack.WhackBgTop
import com.example.pulsemate.games.whack.WhackBtnDark
import com.example.pulsemate.games.whack.WhackBtnFace
import com.example.pulsemate.games.whack.WhackBtnSoft
import com.example.pulsemate.games.whack.WhackBtnSoftDark
import com.example.pulsemate.games.whack.BombBody
import com.example.pulsemate.games.whack.BombRim
import com.example.pulsemate.games.whack.CapRed
import com.example.pulsemate.games.whack.FuseColor
import com.example.pulsemate.games.whack.GoldBelly
import com.example.pulsemate.games.whack.GoldBody
import com.example.pulsemate.games.whack.GrassDark
import com.example.pulsemate.games.whack.GrassMid
import com.example.pulsemate.games.whack.MoleBelly
import com.example.pulsemate.games.whack.MoleBody
import com.example.pulsemate.games.whack.MoleNose
import com.example.pulsemate.games.whack.MoleSnout
import com.example.pulsemate.games.whack.WhackDirt
import com.example.pulsemate.games.whack.WhackEdge
import com.example.pulsemate.games.whack.WhackPanelDark
import com.example.pulsemate.games.whack.WhackDirtDark
import com.example.pulsemate.games.whack.WhackEvent
import com.example.pulsemate.games.whack.WhackEventType
import com.example.pulsemate.games.whack.WhackFont
import com.example.pulsemate.games.whack.WhackGold
import com.example.pulsemate.games.whack.WhackGood
import com.example.pulsemate.games.whack.WhackHaptics
import com.example.pulsemate.games.whack.WhackInk
import com.example.pulsemate.games.whack.WhackLawn
import com.example.pulsemate.games.whack.WhackLawnEdge
import com.example.pulsemate.games.whack.WhackPanel
import com.example.pulsemate.games.whack.WhackTitle
import com.example.pulsemate.games.whack.WhackWorld
import com.example.pulsemate.haptics.LoveSpouseController
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.delay

private const val PREFS_NAME = "pulsemate_whack"
private const val BEST_KEY = "best"

private enum class WhackPhase { START, PLAYING, PAUSED, OVER }

private class Grid(val x: Float, val y: Float, val cellW: Float, val cellH: Float)

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

private fun gridFor(width: Float, height: Float): Grid {
    val cellW = min(width * 0.94f / WhackWorld.COLS, height * 0.96f / (WhackWorld.ROWS * 0.95f))
    val cellH = cellW * 0.95f

    return Grid(
        (width - cellW * WhackWorld.COLS) / 2f,
        (height - cellH * WhackWorld.ROWS) / 2f,
        cellW,
        cellH
    )
}

private fun glyph(paint: Paint, preferred: String, fallback: String): String {
    return if (paint.hasGlyph(preferred)) preferred else fallback
}

private fun DrawScope.drawForest(width: Float, height: Float, tick: Int) {
    val trunk = Color(0xFF3B2A1A)

    drawRect(trunk.copy(alpha = 0.75f), Offset(width * 0.01f, 0f), Size(width * 0.06f, height))
    drawRect(trunk.copy(alpha = 0.75f), Offset(width * 0.93f, 0f), Size(width * 0.06f, height))

    for (i in 0..8) {
        val x = width * i / 8f
        val r = width * (0.11f + 0.02f * (i % 3))

        drawCircle(Color(0xFF1B3A24), r, Offset(x, -height * 0.01f))
        drawCircle(Color(0xFF2A5430).copy(alpha = 0.85f), r * 0.7f, Offset(x + width * 0.03f, height * 0.03f))
    }

    for (i in 0 until 7) {
        val fx = width * (0.1f + 0.13f * i)
        val fy = height * (0.12f + 0.11f * ((i * 5) % 7))
        val glow = 0.35f + 0.35f * kotlin.math.sin(tick * 0.05f + i * 1.3f)

        drawCircle(Color(0xFFFFF3A0).copy(alpha = glow * 0.35f), width * 0.018f, Offset(fx, fy))
        drawCircle(Color(0xFFFFF3A0).copy(alpha = glow), width * 0.007f, Offset(fx, fy))
    }
}

private fun DrawScope.drawBlade(x: Float, y: Float, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(x - size * 0.18f, y)
        lineTo(x, y - size)
        lineTo(x + size * 0.18f, y)
        close()
    }

    drawPath(path, color)
}

private fun DrawScope.drawMushroom(x: Float, y: Float, size: Float) {
    drawRoundRect(
        color = Color(0xFFF3E6C8),
        topLeft = Offset(x - size * 0.14f, y - size * 0.5f),
        size = Size(size * 0.28f, size * 0.5f),
        cornerRadius = CornerRadius(size * 0.1f, size * 0.1f)
    )

    drawArc(
        color = CapRed,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(x - size * 0.45f, y - size * 0.95f),
        size = Size(size * 0.9f, size * 0.9f)
    )

    drawCircle(Color.White, size * 0.07f, Offset(x - size * 0.18f, y - size * 0.62f))
    drawCircle(Color.White, size * 0.06f, Offset(x + size * 0.14f, y - size * 0.7f))
}

private fun DrawScope.drawFlower(x: Float, y: Float, size: Float, color: Color) {
    drawLine(GrassDark, Offset(x, y), Offset(x, y - size * 1.6f), size * 0.12f, StrokeCap.Round)

    for (k in 0 until 5) {
        val a = k * 1.2566f

        drawCircle(
            color,
            size * 0.32f,
            Offset(x + kotlin.math.cos(a) * size * 0.4f, y - size * 1.6f + kotlin.math.sin(a) * size * 0.4f)
        )
    }

    drawCircle(Color(0xFFFFF3A0), size * 0.22f, Offset(x, y - size * 1.6f))
}

private fun DrawScope.drawStar(center: Offset, r: Float, color: Color) {
    drawLine(color, Offset(center.x - r, center.y), Offset(center.x + r, center.y), r * 0.3f, StrokeCap.Round)
    drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y + r), r * 0.3f, StrokeCap.Round)
    drawLine(color, Offset(center.x - r * 0.6f, center.y - r * 0.6f), Offset(center.x + r * 0.6f, center.y + r * 0.6f), r * 0.2f, StrokeCap.Round)
    drawLine(color, Offset(center.x - r * 0.6f, center.y + r * 0.6f), Offset(center.x + r * 0.6f, center.y - r * 0.6f), r * 0.2f, StrokeCap.Round)
}

private fun DrawScope.drawMole(cx: Float, cy: Float, u: Float, golden: Boolean, hit: Boolean, tick: Int) {
    val body = if (golden) GoldBody else MoleBody
    val belly = if (golden) GoldBelly else MoleBelly
    val snout = if (golden) Color(0xFFFFF0C2) else MoleSnout
    val squash = if (hit) 0.9f else 1f

    drawOval(
        color = body,
        topLeft = Offset(cx - u * 0.48f, cy - u * 0.52f * squash),
        size = Size(u * 0.96f, u * 1.04f * squash)
    )

    drawOval(
        color = belly,
        topLeft = Offset(cx - u * 0.3f, cy - u * 0.02f),
        size = Size(u * 0.6f, u * 0.5f)
    )

    drawOval(
        color = Color.White.copy(alpha = if (golden) 0.5f else 0.18f),
        topLeft = Offset(cx - u * 0.36f, cy - u * 0.44f),
        size = Size(u * 0.2f, u * 0.3f)
    )

    drawOval(
        color = snout,
        topLeft = Offset(cx - u * 0.24f, cy - u * 0.2f),
        size = Size(u * 0.48f, u * 0.32f)
    )

    drawOval(
        color = MoleNose,
        topLeft = Offset(cx - u * 0.09f, cy - u * 0.2f),
        size = Size(u * 0.18f, u * 0.12f)
    )

    drawRoundRect(
        color = Color.White,
        topLeft = Offset(cx - u * 0.07f, cy - u * 0.05f),
        size = Size(u * 0.065f, u * 0.1f),
        cornerRadius = CornerRadius(u * 0.015f, u * 0.015f)
    )

    drawRoundRect(
        color = Color.White,
        topLeft = Offset(cx + u * 0.005f, cy - u * 0.05f),
        size = Size(u * 0.065f, u * 0.1f),
        cornerRadius = CornerRadius(u * 0.015f, u * 0.015f)
    )

    drawRoundRect(
        color = Color(0xFF3A2A20).copy(alpha = 0.5f),
        topLeft = Offset(cx - u * 0.075f, cy - u * 0.05f),
        size = Size(u * 0.15f, u * 0.1f),
        cornerRadius = CornerRadius(u * 0.015f, u * 0.015f),
        style = Stroke(width = u * 0.012f)
    )

    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * u * 0.2f
        val ey = cy - u * 0.3f

        if (hit) {
            drawLine(Color(0xFF2A1A12), Offset(ex - u * 0.05f, ey - u * 0.05f), Offset(ex + u * 0.05f, ey + u * 0.05f), u * 0.025f, StrokeCap.Round)
            drawLine(Color(0xFF2A1A12), Offset(ex - u * 0.05f, ey + u * 0.05f), Offset(ex + u * 0.05f, ey - u * 0.05f), u * 0.025f, StrokeCap.Round)
        } else {
            drawCircle(Color(0xFF1E1410), u * 0.055f, Offset(ex, ey))
            drawCircle(Color.White, u * 0.02f, Offset(ex - u * 0.015f, ey - u * 0.02f))
        }

        drawLine(
            Color(0xFF2A1A12).copy(alpha = 0.55f),
            Offset(cx + side * u * 0.2f, cy - u * 0.1f),
            Offset(cx + side * u * 0.5f, cy - u * 0.14f),
            u * 0.01f,
            StrokeCap.Round
        )

        drawLine(
            Color(0xFF2A1A12).copy(alpha = 0.55f),
            Offset(cx + side * u * 0.2f, cy - u * 0.06f),
            Offset(cx + side * u * 0.5f, cy - u * 0.04f),
            u * 0.01f,
            StrokeCap.Round
        )

        val px = cx + side * u * 0.4f
        val py = cy + u * 0.32f

        drawOval(body, Offset(px - u * 0.13f, py - u * 0.1f), Size(u * 0.26f, u * 0.2f))

        for (c in -1..1) {
            drawLine(
                Color(0xFFF3E6C8),
                Offset(px + c * u * 0.06f, py - u * 0.02f),
                Offset(px + c * u * 0.07f, py - u * 0.14f),
                u * 0.025f,
                StrokeCap.Round
            )
        }
    }

    if (golden) {
        val crown = Path().apply {
            moveTo(cx - u * 0.2f, cy - u * 0.5f)
            lineTo(cx - u * 0.22f, cy - u * 0.74f)
            lineTo(cx - u * 0.1f, cy - u * 0.62f)
            lineTo(cx, cy - u * 0.8f)
            lineTo(cx + u * 0.1f, cy - u * 0.62f)
            lineTo(cx + u * 0.22f, cy - u * 0.74f)
            lineTo(cx + u * 0.2f, cy - u * 0.5f)
            close()
        }

        drawPath(crown, Color(0xFFFFD84D))
        drawPath(crown, Color(0xFFB8860B), style = Stroke(width = u * 0.02f))
        drawCircle(Color(0xFFE5484D), u * 0.03f, Offset(cx, cy - u * 0.56f))

        val glint = 0.5f + 0.5f * kotlin.math.sin(tick * 0.18f)

        drawStar(Offset(cx + u * 0.42f, cy - u * 0.5f), u * 0.09f * glint, Color.White)
        drawStar(Offset(cx - u * 0.45f, cy - u * 0.2f), u * 0.07f * (1f - glint * 0.5f), Color.White)
    } else {
        for (k in -1..1) {
            drawLine(
                body.copy(alpha = 0.9f),
                Offset(cx + k * u * 0.06f, cy - u * 0.5f),
                Offset(cx + k * u * 0.1f, cy - u * 0.62f),
                u * 0.035f,
                StrokeCap.Round
            )
        }
    }

    if (hit) {
        for (k in 0 until 3) {
            val a = tick * 0.25f + k * 2.09f

            drawStar(
                Offset(cx + kotlin.math.cos(a) * u * 0.4f, cy - u * 0.78f + kotlin.math.sin(a) * u * 0.1f),
                u * 0.09f,
                Color(0xFFFFE066)
            )
        }
    }
}

private fun DrawScope.drawBomb(cx: Float, cy: Float, u: Float, tick: Int) {
    val r = u * 0.42f

    drawCircle(BombBody, r, Offset(cx, cy))
    drawCircle(Color.White.copy(alpha = 0.18f), r * 0.55f, Offset(cx - r * 0.35f, cy - r * 0.4f))
    drawOval(
        color = Color.White.copy(alpha = 0.55f),
        topLeft = Offset(cx - r * 0.62f, cy - r * 0.62f),
        size = Size(r * 0.38f, r * 0.24f)
    )

    drawRoundRect(
        color = BombRim,
        topLeft = Offset(cx - u * 0.13f, cy - r - u * 0.1f),
        size = Size(u * 0.26f, u * 0.16f),
        cornerRadius = CornerRadius(u * 0.04f, u * 0.04f)
    )

    val fuse = Path().apply {
        moveTo(cx, cy - r - u * 0.08f)
        quadraticBezierTo(cx + u * 0.1f, cy - r - u * 0.3f, cx + u * 0.26f, cy - r - u * 0.24f)
    }

    drawPath(fuse, FuseColor, style = Stroke(width = u * 0.05f, cap = StrokeCap.Round))

    val flicker = 0.7f + 0.3f * kotlin.math.sin(tick * 0.7f)
    val sx = cx + u * 0.26f
    val sy = cy - r - u * 0.24f

    drawCircle(Color(0xFFFF8A00), u * 0.1f * flicker, Offset(sx, sy))
    drawCircle(Color(0xFFFFE066), u * 0.06f * flicker, Offset(sx, sy))
    drawStar(Offset(sx, sy), u * 0.15f * flicker, Color(0xFFFFB020))
}

private fun DrawScope.drawBurst(cx: Float, cy: Float, r: Float, tick: Int) {
    val path = Path()
    val points = 12

    for (k in 0 until points * 2) {
        val radius = if (k % 2 == 0) r else r * 0.55f
        val a = k * 3.14159f / points + tick * 0.05f
        val x = cx + kotlin.math.cos(a) * radius
        val y = cy + kotlin.math.sin(a) * radius

        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    path.close()

    drawPath(path, Color(0xFFFF8A00))
    drawCircle(Color(0xFFFFE066), r * 0.55f, Offset(cx, cy))
    drawCircle(Color.White, r * 0.28f, Offset(cx, cy))
}

@Composable
fun WhackScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { WhackHaptics(loveSpouse) }
    val world = remember { WhackWorld() }

    val emojiPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    }
    val popPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif-monospace", Typeface.BOLD)
        }
    }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(WhackPhase.START) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var comboUi by remember { mutableIntStateOf(0) }
    var timeUi by remember { mutableIntStateOf(0) }
    var timeFrac by remember { mutableFloatStateOf(1f) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var newBest by remember { mutableStateOf(false) }
    var bestAtStart by remember { mutableIntStateOf(best) }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    fun handle(events: List<WhackEvent>) {
        var level = 0
        var millis = 0L

        for (e in events) {
            val pair: Pair<Int, Long>? = when (e.type) {
                WhackEventType.HIT -> Pair((3 + e.value / 3).coerceAtMost(6), 140L)
                WhackEventType.GOLD -> Pair(8, 380L)
                WhackEventType.BOMB -> Pair(9, 320L)
                WhackEventType.GAME_OVER -> Pair(5, 700L)
                else -> null
            }

            if (pair != null && (pair.first > level || (pair.first == level && pair.second > millis))) {
                level = pair.first
                millis = pair.second
            }

            if (e.type == WhackEventType.GAME_OVER) {
                newBest = world.score > bestAtStart && world.score > 0

                if (world.score > best) {
                    best = world.score
                    saveBest(context, best)
                }

                phase = WhackPhase.OVER
            }
        }

        if (level > 0) haptics.pulse(scope, level, millis)
    }

    fun startRound() {
        world.start()
        haptics.reset()
        bestAtStart = best
        newBest = false
        scoreUi = 0
        comboUi = 0
        timeUi = WhackWorld.ROUND_SECONDS.toInt()
        timeFrac = 1f
        phase = WhackPhase.PLAYING
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
                haptics.stop()

                if (phase == WhackPhase.PLAYING) {
                    world.running = false
                    phase = WhackPhase.PAUSED
                }
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

            if (phase == WhackPhase.PLAYING) {
                handle(world.update(seconds))
                scoreUi = world.score
                comboUi = world.combo

                if (world.score > best) {
                    best = world.score
                    saveBest(context, best)
                }

                timeUi = ceil(world.timeLeft).toInt()
                timeFrac = world.timeLeft / WhackWorld.ROUND_SECONDS
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(WhackBgTop, WhackBgBottom)))
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Score $scoreUi",
                    color = WhackInk,
                    fontSize = 24.sp,
                    fontFamily = WhackFont
                )

                Text(
                    text = "${timeUi}s",
                    color = WhackInk,
                    fontSize = 24.sp,
                    fontFamily = WhackFont
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (comboUi >= 3) "Combo x$comboUi" else " ",
                    color = WhackBtnDark,
                    fontSize = 15.sp,
                    fontFamily = WhackFont
                )

                Text(
                    text = "Best $best",
                    color = WhackInk.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    fontFamily = WhackFont
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(WhackBtnSoftDark.copy(alpha = 0.6f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(timeFrac.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (timeFrac > 0.25f) WhackBtnFace else WhackBad)
                )
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()

                            if (phase != WhackPhase.PLAYING) return@awaitEachGesture

                            val g = gridFor(size.width.toFloat(), size.height.toFloat())
                            val px = down.position.x - g.x
                            val py = down.position.y - g.y

                            if (px < 0f || py < 0f) return@awaitEachGesture

                            val col = (px / g.cellW).toInt()
                            val row = (py / g.cellH).toInt()

                            if (col >= WhackWorld.COLS || row >= WhackWorld.ROWS) return@awaitEachGesture

                            handle(world.tap(row * WhackWorld.COLS + col))
                        }
                    }
            ) {
                val tick = frame

                if (tick >= 0) {
                    val g = gridFor(size.width, size.height)
                    val gridW = g.cellW * WhackWorld.COLS
                    val gridH = g.cellH * WhackWorld.ROWS

                    drawForest(size.width, size.height, tick)

                    drawRoundRect(
                        color = WhackLawnEdge,
                        topLeft = Offset(g.x - g.cellW * 0.05f, g.y - g.cellW * 0.05f),
                        size = Size(gridW + g.cellW * 0.1f, gridH + g.cellW * 0.1f),
                        cornerRadius = CornerRadius(g.cellW * 0.26f, g.cellW * 0.26f)
                    )

                    drawRoundRect(
                        color = WhackLawn,
                        topLeft = Offset(g.x, g.y),
                        size = Size(gridW, gridH),
                        cornerRadius = CornerRadius(g.cellW * 0.22f, g.cellW * 0.22f)
                    )

                    val unit = g.cellW * 0.62f

                    for (i in 0 until WhackWorld.COUNT) {
                        val row = i / WhackWorld.COLS
                        val col = i % WhackWorld.COLS
                        val left = g.x + col * g.cellW
                        val top = g.y + row * g.cellH
                        val cx = left + g.cellW / 2f
                        val cy = top + g.cellH * 0.74f
                        val rx = g.cellW * 0.36f
                        val ry = g.cellW * 0.12f
                        val hole = world.holes[i]
                        val h = world.height(i)

                        drawOval(
                            color = WhackDirt,
                            topLeft = Offset(cx - rx * 1.14f, cy - ry * 1.2f),
                            size = Size(rx * 2.28f, ry * 2.4f)
                        )

                        drawOval(
                            color = WhackDirtDark,
                            topLeft = Offset(cx - rx, cy - ry),
                            size = Size(rx * 2f, ry * 2f)
                        )

                        for (k in 0 until 7) {
                            val t = 0.12f + k * 0.126f
                            val bx = cx - rx * 1.1f + t * rx * 2.2f
                            val by = cy - ry * (1.05f + 0.25f * kotlin.math.sin(t * 3.14f))

                            drawBlade(bx, by, unit * (0.22f + 0.05f * (k % 3)), if (k % 2 == 0) GrassDark else GrassMid)
                        }

                        if (hole.kind != WhackWorld.KIND_NONE && h > 0f) {
                            clipRect(left, top - g.cellH, left + g.cellW, cy) {
                                val centerY = cy - unit * 0.42f + (1f - h) * unit * 1.15f

                                when {
                                    hole.kind == WhackWorld.KIND_BOMB && hole.hit ->
                                        drawBurst(cx, centerY - unit * 0.1f, unit * 0.85f, tick)

                                    hole.kind == WhackWorld.KIND_BOMB ->
                                        drawBomb(cx, centerY, unit, tick)

                                    else ->
                                        drawMole(cx, centerY, unit, hole.kind == WhackWorld.KIND_GOLD, hole.hit, tick)
                                }
                            }
                        }

                        drawArc(
                            color = WhackDirt,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(cx - rx * 1.14f, cy - ry * 1.2f),
                            size = Size(rx * 2.28f, ry * 2.4f)
                        )

                        drawArc(
                            color = WhackDirtDark.copy(alpha = 0.4f),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(cx - rx * 1.14f, cy - ry * 1.2f),
                            size = Size(rx * 2.28f, ry * 2.4f),
                            style = Stroke(width = g.cellW * 0.02f)
                        )

                        if ((row + col) % 2 == 0) {
                            drawMushroom(left + g.cellW * 0.12f, cy + ry * 1.2f, unit * 0.34f)
                        } else {
                            drawFlower(left + g.cellW * 0.88f, cy + ry * 1.1f, unit * 0.2f, if (i % 3 == 0) Color(0xFFF7A8C4) else Color(0xFFFFE06E))
                        }
                    }

                    drawIntoCanvas { canvas ->
                        for (p in world.pops) {
                            val row = p.hole / WhackWorld.COLS
                            val col = p.hole % WhackWorld.COLS
                            val cx = g.x + col * g.cellW + g.cellW / 2f
                            val cy = g.y + row * g.cellH + g.cellH * 0.74f
                            val t = p.age / WhackWorld.POP_SECONDS
                            val y = cy - g.cellW * 0.7f - t * g.cellW * 0.4f
                            val alpha = (1f - t).coerceIn(0f, 1f)
                            val color = when (p.kind) {
                                WhackWorld.KIND_GOLD -> WhackGold
                                WhackWorld.KIND_BOMB -> WhackBad
                                else -> WhackGood
                            }

                            popPaint.textSize = g.cellW * 0.3f
                            popPaint.style = Paint.Style.STROKE
                            popPaint.strokeWidth = g.cellW * 0.06f
                            popPaint.color = Color.White.copy(alpha = alpha).toArgb()
                            canvas.nativeCanvas.drawText(p.text, cx, y, popPaint)

                            popPaint.style = Paint.Style.FILL
                            popPaint.color = color.copy(alpha = alpha).toArgb()
                            canvas.nativeCanvas.drawText(p.text, cx, y, popPaint)
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
                    text = "Pause",
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (phase == WhackPhase.PLAYING) {
                            haptics.stop()
                            world.running = false
                            phase = WhackPhase.PAUSED
                        }
                    }
                )
            }
        }

        when (phase) {
            WhackPhase.START -> PopupCard {
                Text(
                    text = "Whack-a-Mole",
                    color = WhackTitle,
                    fontSize = 30.sp,
                    fontFamily = WhackFont
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Tap the moles as they pop up.\nGolden ones are worth 5.\nAvoid the bombs!",
                    color = WhackInk,
                    fontSize = 15.sp,
                    fontFamily = WhackFont,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))

                CuteButton(
                    text = "Play",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startRound() }
                )

                Spacer(Modifier.height(10.dp))

                CuteButton(
                    text = "Back",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            WhackPhase.PAUSED -> PopupCard {
                Text(
                    text = "Paused",
                    color = WhackTitle,
                    fontSize = 30.sp,
                    fontFamily = WhackFont
                )

                Spacer(Modifier.height(18.dp))

                CuteButton(
                    text = "Resume",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        world.running = true
                        phase = WhackPhase.PLAYING
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

            WhackPhase.OVER -> PopupCard {
                Text(
                    text = "Time's up!",
                    color = WhackTitle,
                    fontSize = 30.sp,
                    fontFamily = WhackFont
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Score ${world.score}",
                    color = WhackInk,
                    fontSize = 24.sp,
                    fontFamily = WhackFont
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = if (newBest) "New best!" else "Best $best",
                    color = if (newBest) WhackGold else WhackInk.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontFamily = WhackFont
                )

                Text(
                    text = "Longest combo ${world.bestCombo}",
                    color = WhackInk.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontFamily = WhackFont
                )

                Spacer(Modifier.height(18.dp))

                CuteButton(
                    text = "Play again",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startRound() }
                )

                Spacer(Modifier.height(10.dp))

                CuteButton(
                    text = "Menu",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            WhackPhase.PLAYING -> Unit
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 8.dp, bottomEnd = 30.dp, bottomStart = 8.dp)

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
                .background(Brush.verticalGradient(listOf(WhackPanel, WhackPanelDark)))
                .drawBehind {
                    for (i in 1..14) {
                        val x = size.width * i / 15f

                        drawLine(
                            color = Color.Black.copy(alpha = 0.18f),
                            start = Offset(x, 0f),
                            end = Offset(x + 5f * (i % 3 - 1), size.height),
                            strokeWidth = 2f
                        )
                    }

                    drawRect(
                        color = GrassMid,
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, 14f)
                    )
                }
                .border(4.dp, WhackEdge, shape)
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
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomEnd = 20.dp, bottomStart = 4.dp)
    val face = if (primary) WhackBtnFace else WhackBtnSoft
    val edge = if (primary) WhackBtnDark else WhackBtnSoftDark

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
                .background(face)
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(size.width * 0.12f, size.height * 0.2f),
                        end = Offset(size.width * 0.88f, size.height * 0.2f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (primary) Color.White else WhackInk,
                fontSize = 17.sp,
                fontFamily = WhackFont
            )
        }
    }
}