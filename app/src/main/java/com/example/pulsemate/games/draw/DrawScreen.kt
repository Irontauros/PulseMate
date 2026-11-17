package com.example.pulsemate.games.draw.screens

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
import androidx.compose.ui.graphics.StrokeJoin
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
import com.example.pulsemate.games.draw.DrawBad
import com.example.pulsemate.games.draw.DrawBall
import com.example.pulsemate.games.draw.DrawBallDark
import com.example.pulsemate.games.draw.DrawBtnDark
import com.example.pulsemate.games.draw.DrawBtnFace
import com.example.pulsemate.games.draw.DrawBtnSoft
import com.example.pulsemate.games.draw.DrawBtnSoftDark
import com.example.pulsemate.games.draw.DrawEvent
import com.example.pulsemate.games.draw.DrawEventType
import com.example.pulsemate.games.draw.DrawFont
import com.example.pulsemate.games.draw.DrawGoal
import com.example.pulsemate.games.draw.DrawGoalLight
import com.example.pulsemate.games.draw.DrawGraphite
import com.example.pulsemate.games.draw.DrawGrid
import com.example.pulsemate.games.draw.DrawHaptics
import com.example.pulsemate.games.draw.DrawHazard
import com.example.pulsemate.games.draw.DrawInk
import com.example.pulsemate.games.draw.DrawMargin
import com.example.pulsemate.games.draw.DrawPaper
import com.example.pulsemate.games.draw.DrawPaperDark
import com.example.pulsemate.games.draw.DrawText
import com.example.pulsemate.games.draw.DrawWorld
import com.example.pulsemate.haptics.LoveSpouseController
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val PREFS_NAME = "pulsemate_sketchdrop"
private const val BEST_KEY = "best"

private enum class DrawPhase { START, PLAYING, CLEARED }

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

private fun DrawScope.drawScene(world: DrawWorld) {
    drawRect(DrawPaper)

    val s = min(size.width / DrawWorld.W, size.height / DrawWorld.H)
    val ox = (size.width - DrawWorld.W * s) / 2f
    val oy = (size.height - DrawWorld.H * s) / 2f

    fun px(x: Float) = ox + x * s
    fun py(y: Float) = oy + y * s

    drawRect(
        color = DrawPaperDark.copy(alpha = 0.55f),
        topLeft = Offset.Zero,
        size = size
    )

    drawRoundRect(
        color = DrawPaper,
        topLeft = Offset(ox, oy),
        size = Size(DrawWorld.W * s, DrawWorld.H * s),
        cornerRadius = CornerRadius(6f, 6f)
    )

    var gx = 0f

    while (gx <= DrawWorld.W) {
        drawLine(DrawGrid.copy(alpha = 0.35f), Offset(px(gx), oy), Offset(px(gx), oy + DrawWorld.H * s), 1.2f)
        gx += 10f
    }

    var gy = 0f

    while (gy <= DrawWorld.H) {
        drawLine(DrawGrid.copy(alpha = 0.35f), Offset(ox, py(gy)), Offset(ox + DrawWorld.W * s, py(gy)), 1.2f)
        gy += 10f
    }

    drawLine(DrawMargin.copy(alpha = 0.7f), Offset(px(6f), oy), Offset(px(6f), oy + DrawWorld.H * s), 2f)

    drawRoundRect(
        color = DrawGraphite,
        topLeft = Offset(ox, oy),
        size = Size(DrawWorld.W * s, DrawWorld.H * s),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 3f)
    )

    val level = world.level
    val thick = DrawWorld.HALF * 2f * s

    for (w in level.walls) {
        drawLine(DrawGraphite, Offset(px(w[0]), py(w[1])), Offset(px(w[2]), py(w[3])), thick, StrokeCap.Round)
    }

    for (h in level.hazards) {
        val x1 = px(h[0])
        val y1 = py(h[1])
        val x2 = px(h[2])
        val y2 = py(h[3])

        drawLine(DrawHazard, Offset(x1, y1), Offset(x2, y2), thick, StrokeCap.Round)

        val steps = (kotlin.math.hypot(x2 - x1, y2 - y1) / (6f * s)).toInt().coerceAtLeast(1)

        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val cx = x1 + (x2 - x1) * t
            val cy = y1 + (y2 - y1) * t

            drawLine(DrawHazard, Offset(cx - 3f * s, cy + 3f * s), Offset(cx + 3f * s, cy - 3f * s), 2f)
        }
    }

    val gx1 = px(level.goalX1)
    val gx2 = px(level.goalX2)
    val gyy = py(level.goalY)

    drawLine(DrawGoal, Offset(gx1, gyy - 5f * s), Offset(gx1, gyy), thick, StrokeCap.Round)
    drawLine(DrawGoal, Offset(gx2, gyy - 5f * s), Offset(gx2, gyy), thick, StrokeCap.Round)
    drawLine(DrawGoal, Offset(gx1, gyy), Offset(gx2, gyy), thick, StrokeCap.Round)

    val p = world.progress()

    if (p > 0f) {
        drawLine(
            DrawGoalLight,
            Offset(gx1, gyy),
            Offset(gx1 + (gx2 - gx1) * p, gyy),
            thick * 0.6f,
            StrokeCap.Round
        )
    }

    for (st in world.strokes) {
        if (st.xs.size < 2) continue

        val path = Path()
        path.moveTo(px(st.xs[0]), py(st.ys[0]))

        for (i in 1 until st.xs.size) path.lineTo(px(st.xs[i]), py(st.ys[i]))

        drawPath(
            path = path,
            color = DrawInk,
            style = Stroke(width = thick, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    if (!world.released && !world.won) {
        drawCircle(
            color = DrawBall.copy(alpha = 0.5f),
            radius = (DrawWorld.R + 2f) * s,
            center = Offset(px(level.ballX), py(level.ballY)),
            style = Stroke(width = 2f)
        )
    }

    val bc = Offset(px(world.x), py(world.y))
    val br = DrawWorld.R * s

    drawCircle(Color.Black.copy(alpha = 0.18f), br, Offset(bc.x + 3f, bc.y + 4f))
    drawCircle(DrawBall, br, bc)
    drawCircle(DrawBallDark, br, bc, style = Stroke(width = 2.5f))

    val a = world.rot

    drawLine(
        color = Color.White.copy(alpha = 0.65f),
        start = Offset(bc.x - cos(a) * br * 0.85f, bc.y - sin(a) * br * 0.85f),
        end = Offset(bc.x + cos(a) * br * 0.85f, bc.y + sin(a) * br * 0.85f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    drawCircle(Color.White.copy(alpha = 0.35f), br * 0.22f, Offset(bc.x - br * 0.4f, bc.y - br * 0.4f))
}

@Composable
fun DrawScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { DrawHaptics(loveSpouse) }
    val world = remember { DrawWorld() }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(DrawPhase.START) }
    var levelUi by remember { mutableIntStateOf(1) }
    var inkFrac by remember { mutableFloatStateOf(1f) }
    var holdTenths by remember { mutableIntStateOf(0) }
    var releasedUi by remember { mutableStateOf(false) }
    var starsUi by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    fun startLevel(index: Int) {
        world.load(index)
        haptics.reset()
        levelUi = world.levelIndex + 1
        inkFrac = 1f
        holdTenths = 0
        releasedUi = false
        phase = DrawPhase.PLAYING
    }

    fun handle(events: List<DrawEvent>) {
        for (e in events) {
            when (e.type) {
                DrawEventType.IMPACT -> haptics.pulse(scope, 3, 70L)
                DrawEventType.HAZARD -> haptics.pulse(scope, 8, 350L)
                DrawEventType.FELL -> haptics.pulse(scope, 2, 120L)
                DrawEventType.WON -> {
                    haptics.pulse(scope, 9, 900L)
                    starsUi = world.stars()

                    val cleared = world.levelIndex + 1

                    if (cleared > best) {
                        best = cleared
                        saveBest(context, best)
                    }

                    phase = DrawPhase.CLEARED
                }
            }
        }
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
            val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.033f)
            last = now

            if (phase == DrawPhase.PLAYING) {
                handle(world.update(seconds))

                val frac = (world.inkLeft / world.level.ink).coerceIn(0f, 1f)

                if (frac != inkFrac) inkFrac = frac

                val tenths = (world.hold * 10f).toInt()

                if (tenths != holdTenths) holdTenths = tenths
                if (world.released != releasedUi) releasedUi = world.released

                if (phase == DrawPhase.PLAYING) {
                    val level = if (world.touching && world.released) {
                        (3 + world.progress() * 6f).toInt().coerceIn(3, 9)
                    } else {
                        0
                    }

                    haptics.follow(level)
                }
            } else {
                haptics.follow(0)
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DrawPaperDark, DrawPaper)))
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
                    text = "Level $levelUi",
                    color = DrawText,
                    fontSize = 24.sp,
                    fontFamily = DrawFont
                )

                Text(
                    text = "Hold ${holdTenths / 10}.${holdTenths % 10} / 3s",
                    color = if (holdTenths > 0) DrawGoal else DrawText.copy(alpha = 0.6f),
                    fontSize = 20.sp,
                    fontFamily = DrawFont
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Ink",
                    color = DrawText.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    fontFamily = DrawFont
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(DrawBtnSoftDark.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(inkFrac)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (inkFrac > 0.2f) DrawInk else DrawBad)
                    )
                }

                Text(
                    text = "Best $best",
                    color = DrawText.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                    fontFamily = DrawFont
                )
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .clipToBounds()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()

                            if (phase != DrawPhase.PLAYING) return@awaitEachGesture

                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val s = min(w / DrawWorld.W, h / DrawWorld.H)
                            val ox = (w - DrawWorld.W * s) / 2f
                            val oy = (h - DrawWorld.H * s) / 2f

                            val stroke = world.beginStroke(
                                ((down.position.x - ox) / s).coerceIn(0f, DrawWorld.W),
                                ((down.position.y - oy) / s).coerceIn(0f, DrawWorld.H)
                            ) ?: return@awaitEachGesture

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (!change.pressed) break

                                world.extendStroke(
                                    stroke,
                                    ((change.position.x - ox) / s).coerceIn(0f, DrawWorld.W),
                                    ((change.position.y - oy) / s).coerceIn(0f, DrawWorld.H)
                                )

                                change.consume()
                            }

                            world.endStroke(stroke)
                        }
                    }
            ) {
                val tick = frame

                if (tick >= 0) drawScene(world)
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
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SketchButton(
                    text = "Back",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.stop()
                        onBack()
                    }
                )

                SketchButton(
                    text = "Undo",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = { if (phase == DrawPhase.PLAYING) world.undo() }
                )

                SketchButton(
                    text = "Clear",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (phase == DrawPhase.PLAYING) {
                            haptics.stop()
                            world.clearStrokes()
                        }
                    }
                )

                SketchButton(
                    text = if (releasedUi) "Reset" else "Drop",
                    primary = true,
                    modifier = Modifier.weight(1.2f),
                    onClick = {
                        if (phase == DrawPhase.PLAYING) {
                            if (world.released) {
                                haptics.stop()
                                world.resetBall()
                            } else {
                                world.release()
                            }
                        }
                    }
                )
            }
        }

        when (phase) {
            DrawPhase.START -> PopupCard {
                Text(
                    text = "Sketch Drop",
                    color = DrawInk,
                    fontSize = 30.sp,
                    fontFamily = DrawFont
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Draw lines with your finger, then press Drop. Get the ball onto the green cup and keep it touching for 3 seconds. Avoid the red lines. The toy buzzes harder the longer you hold, and the green bar fills up too.",
                    color = DrawText.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = DrawFont
                )

                Spacer(Modifier.height(18.dp))

                SketchButton(
                    text = "Play",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startLevel(0) }
                )

                if (best in 1 until DrawWorld.LEVELS.size) {
                    Spacer(Modifier.height(10.dp))

                    SketchButton(
                        text = "Continue level ${best + 1}",
                        primary = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { startLevel(best) }
                    )
                }

                Spacer(Modifier.height(10.dp))

                SketchButton(
                    text = "Back",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            DrawPhase.CLEARED -> PopupCard {
                val last = world.levelIndex >= DrawWorld.LEVELS.size - 1

                Text(
                    text = if (last) "All levels done!" else "Level cleared!",
                    color = DrawGoal,
                    fontSize = 28.sp,
                    fontFamily = DrawFont
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "⭐".repeat(starsUi) + "☆".repeat((3 - starsUi).coerceAtLeast(0)),
                    fontSize = 28.sp
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Less ink gives more stars",
                    color = DrawText.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontFamily = DrawFont
                )

                Spacer(Modifier.height(18.dp))

                SketchButton(
                    text = if (last) "Play again" else "Next level",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startLevel(if (last) 0 else world.levelIndex + 1) }
                )

                Spacer(Modifier.height(10.dp))

                SketchButton(
                    text = "Retry level",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startLevel(world.levelIndex) }
                )

                Spacer(Modifier.height(10.dp))

                SketchButton(
                    text = "Menu",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            DrawPhase.PLAYING -> Unit
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(6.dp)

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
                .background(DrawPaper)
                .drawBehind {
                    var y = 36f

                    while (y < size.height) {
                        drawLine(DrawGrid.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), 1.5f)
                        y += 36f
                    }

                    drawLine(DrawMargin.copy(alpha = 0.7f), Offset(36f, 0f), Offset(36f, size.height), 2f)
                }
                .border(3.dp, DrawGraphite, shape)
                .padding(start = 44.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun SketchButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(4.dp)
    val face = if (primary) DrawBtnFace else DrawBtnSoft
    val edge = if (primary) DrawBtnDark else DrawBtnSoftDark

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
                color = if (primary) Color.White else DrawText,
                fontSize = 16.sp,
                maxLines = 1,
                fontFamily = DrawFont
            )
        }
    }
}