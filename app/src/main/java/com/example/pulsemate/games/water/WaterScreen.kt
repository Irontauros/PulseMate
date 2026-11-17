package com.example.pulsemate.games.water.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.drawBehind
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
import com.example.pulsemate.games.water.WaterBgBottom
import com.example.pulsemate.games.water.WaterBgTop
import com.example.pulsemate.games.water.WaterBtnDark
import com.example.pulsemate.games.water.WaterBtnFace
import com.example.pulsemate.games.water.WaterBtnSoft
import com.example.pulsemate.games.water.WaterBtnSoftDark
import com.example.pulsemate.games.water.WaterColors
import com.example.pulsemate.games.water.WaterEdge
import com.example.pulsemate.games.water.WaterEvent
import com.example.pulsemate.games.water.WaterEventType
import com.example.pulsemate.games.water.WaterFont
import com.example.pulsemate.games.water.WaterHaptics
import com.example.pulsemate.games.water.WaterInk
import com.example.pulsemate.games.water.WaterPanel
import com.example.pulsemate.games.water.WaterTitle
import com.example.pulsemate.games.water.WaterWorld
import com.example.pulsemate.haptics.LoveSpouseController
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS_NAME = "pulsemate_water"
private const val LEVEL_KEY = "level"
private const val LAYER_FACTOR = 0.72f
private const val LIFT_FACTOR = 0.7f

private enum class WaterPhase { START, PLAYING }

private class TubeRect(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
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

private fun layoutTubes(width: Float, height: Float, n: Int): List<TubeRect> {
    if (n <= 0 || width <= 0f || height <= 0f) return emptyList()

    val perRow = if (n <= 6) n else (n + 1) / 2
    val rows = (n + perRow - 1) / perRow
    val gapX = width * 0.03f
    val marginX = width * 0.05f
    val gapY = height * 0.06f
    val heightFactor = WaterWorld.CAP * LAYER_FACTOR + 0.5f

    var tw = (width - 2f * marginX - (perRow - 1) * gapX) / perRow
    val maxTw = (height * 0.8f - (rows - 1) * gapY) / rows / heightFactor

    tw = min(min(tw, maxTw), width * 0.2f)

    val th = tw * heightFactor
    val totalHeight = rows * th + (rows - 1) * gapY
    val top = (height - totalHeight) / 2f + tw * 0.25f
    val result = ArrayList<TubeRect>()

    for (i in 0 until n) {
        val row = i / perRow
        val col = i % perRow
        val inRow = min(perRow, n - row * perRow)
        val rowWidth = inRow * tw + (inRow - 1) * gapX
        val startX = (width - rowWidth) / 2f

        result.add(TubeRect(startX + col * (tw + gapX), top + row * (th + gapY), tw, th))
    }

    return result
}

@Composable
fun WaterScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { WaterHaptics(loveSpouse) }
    val world = remember { WaterWorld() }
    val sparklePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(WaterPhase.START) }
    var levelUi by remember { mutableIntStateOf(1) }
    var movesUi by remember { mutableIntStateOf(0) }
    var savedLevel by remember { mutableIntStateOf(loadLevel(context)) }
    var banner by remember { mutableStateOf(false) }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    fun handle(events: List<WaterEvent>) {
        if (events.isEmpty()) return

        var level = 0
        var millis = 0L

        for (e in events) {
            val pair: Pair<Int, Long> = when (e.type) {
                WaterEventType.POUR -> Pair(1 + e.value, 380L)
                WaterEventType.INVALID -> Pair(1, 90L)
                WaterEventType.TUBE_DONE -> Pair(7, 450L)
                WaterEventType.LEVEL_CLEAR -> Pair(9, 1000L)
                WaterEventType.UNDO -> Pair(2, 100L)
            }

            if (pair.first > level || (pair.first == level && pair.second > millis)) {
                level = pair.first
                millis = pair.second
            }

            if (e.type == WaterEventType.LEVEL_CLEAR) {
                banner = true
                saveLevel(context, world.level + 1)
                savedLevel = world.level + 1

                scope.launch {
                    delay(1600)
                    world.nextLevel()
                    levelUi = world.level
                    movesUi = 0
                    banner = false
                }
            }
        }

        if (level > 0) haptics.pulse(scope, level, millis)
    }

    fun startAt(level: Int) {
        world.startLevel(level)
        haptics.reset()
        levelUi = world.level
        movesUi = 0
        banner = false
        phase = WaterPhase.PLAYING
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

            if (phase == WaterPhase.PLAYING) {
                handle(world.update(seconds))
                if (movesUi != world.moves && !banner) movesUi = world.moves
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(WaterBgTop, WaterBgBottom)))
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
                Text(
                    text = "Level $levelUi",
                    color = WaterInk,
                    fontSize = 26.sp,
                    fontFamily = WaterFont
                )

                Text(
                    text = "Moves $movesUi",
                    color = WaterInk.copy(alpha = 0.75f),
                    fontSize = 20.sp,
                    fontFamily = WaterFont
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
                            detectTapGestures(
                                onTap = { offset ->
                                    if (phase != WaterPhase.PLAYING || banner) return@detectTapGestures

                                    val rects = layoutTubes(
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                        world.tubes.size
                                    )

                                    for (i in rects.indices) {
                                        val r = rects[i]
                                        val pad = r.w * 0.12f
                                        val lift = r.w * LAYER_FACTOR * LIFT_FACTOR

                                        if (offset.x >= r.x - pad &&
                                            offset.x <= r.x + r.w + pad &&
                                            offset.y >= r.y - lift - pad &&
                                            offset.y <= r.y + r.h + pad
                                        ) {
                                            handle(world.tap(i))
                                            break
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    val tick = frame

                    if (tick >= 0) {
                        val rects = layoutTubes(size.width, size.height, world.tubes.size)
                        val pour = world.pour

                        for (i in rects.indices) {
                            val r = rects[i]
                            val layerH = r.w * LAYER_FACTOR
                            val tube = world.tubes[i]

                            val lift = when {
                                pour != null && pour.from == i -> layerH * 1.1f
                                world.selected == i -> layerH * LIFT_FACTOR
                                else -> 0f
                            }

                            val top = r.y - lift
                            val corner = r.w * 0.3f
                            val shape = Path().apply {
                                addRoundRect(
                                    RoundRect(r.x, top, r.x + r.w, top + r.h, CornerRadius(corner, corner))
                                )
                            }

                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.4f),
                                topLeft = Offset(r.x, top),
                                size = Size(r.w, r.h),
                                cornerRadius = CornerRadius(corner, corner)
                            )

                            val bottomY = top + r.h - layerH * 0.15f

                            clipPath(shape) {
                                var y = bottomY

                                for (k in tube.indices) {
                                    val shrink = if (pour != null && pour.from == i && k >= tube.size - pour.count) {
                                        1f - pour.progress
                                    } else {
                                        1f
                                    }

                                    val h = layerH * shrink

                                    if (h > 0.5f) {
                                        drawRect(
                                            color = WaterColors[tube[k]],
                                            topLeft = Offset(r.x, y - h),
                                            size = Size(r.w, h)
                                        )

                                        drawRect(
                                            color = Color.White.copy(alpha = 0.28f),
                                            topLeft = Offset(r.x, y - h),
                                            size = Size(r.w, h * 0.14f)
                                        )
                                    }

                                    y -= h
                                }

                                if (pour != null && pour.to == i) {
                                    val h = layerH * pour.progress * pour.count

                                    drawRect(
                                        color = WaterColors[pour.color],
                                        topLeft = Offset(r.x, y - h),
                                        size = Size(r.w, h)
                                    )

                                    drawRect(
                                        color = Color.White.copy(alpha = 0.28f),
                                        topLeft = Offset(r.x, y - h),
                                        size = Size(r.w, layerH * 0.14f)
                                    )
                                }

                                drawRect(
                                    color = Color.White.copy(alpha = 0.35f),
                                    topLeft = Offset(r.x + r.w * 0.14f, top + r.h * 0.08f),
                                    size = Size(r.w * 0.1f, r.h * 0.7f)
                                )
                            }

                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.95f),
                                topLeft = Offset(r.x, top),
                                size = Size(r.w, r.h),
                                cornerRadius = CornerRadius(corner, corner),
                                style = Stroke(width = r.w * 0.05f)
                            )

                            if (tube.size == WaterWorld.CAP && tube.all { it == tube[0] }) {
                                drawIntoCanvas { canvas ->
                                    sparklePaint.textSize = r.w * 0.5f

                                    val m = sparklePaint.fontMetrics
                                    val baseline = top - r.w * 0.22f - (m.ascent + m.descent) / 2f

                                    canvas.nativeCanvas.drawText("✨", r.x + r.w / 2f, baseline, sparklePaint)
                                }
                            }
                        }

                        if (pour != null && pour.progress < 0.92f) {
                            val src = rects[pour.from]
                            val dst = rects[pour.to]
                            val layerH = src.w * LAYER_FACTOR
                            val srcTop = src.y - layerH * 1.1f
                            val dstTop = dst.y
                            val dstBottom = dst.y + dst.h - layerH * 0.15f
                            val dstLevel = world.tubes[pour.to].size + pour.count * pour.progress
                            val surface = dstBottom - dstLevel * layerH

                            drawLine(
                                color = WaterColors[pour.color].copy(alpha = 0.95f),
                                start = Offset(src.x + src.w / 2f, srcTop + layerH * 0.1f),
                                end = Offset(dst.x + dst.w / 2f, maxOf(surface, dstTop)),
                                strokeWidth = layerH * 0.26f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                if (banner) {
                    Text(
                        text = "Level clear!",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontFamily = WaterFont,
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
                            .background(WaterBtnFace)
                            .border(3.dp, WaterBtnDark, RoundedCornerShape(24.dp))
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
                    text = "Undo",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (phase == WaterPhase.PLAYING && !banner) handle(world.undo())
                    }
                )

                CuteButton(
                    text = "Restart",
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (phase == WaterPhase.PLAYING && !banner) {
                            world.restart()
                            movesUi = 0
                        }
                    }
                )
            }
        }

        if (phase == WaterPhase.START) {
            PopupCard {
                Text(
                    text = "Water Sort",
                    color = WaterTitle,
                    fontSize = 36.sp,
                    fontFamily = WaterFont
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (color in WaterColors.take(7)) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Tap a tube, then tap another to pour. Fill every tube with a single color.",
                    color = WaterInk,
                    fontSize = 15.sp,
                    fontFamily = WaterFont,
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
    val shape = RoundedCornerShape(38.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06284D).copy(alpha = 0.45f))
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
                .background(Brush.verticalGradient(listOf(Color(0xFFF6FDFF), Color(0xFFB9E6FF))))
                .drawBehind {
                    drawCircle(Color.White.copy(alpha = 0.6f), 16f, Offset(size.width * 0.9f, size.height * 0.12f))
                    drawCircle(Color.White.copy(alpha = 0.5f), 9f, Offset(size.width * 0.82f, size.height * 0.07f))
                    drawCircle(Color.White.copy(alpha = 0.45f), 12f, Offset(size.width * 0.08f, size.height * 0.9f))
                }
                .border(4.dp, WaterEdge, shape)
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
    val shape = RoundedCornerShape(50)
    val brush = if (primary) {
        Brush.verticalGradient(listOf(Color(0xFF66CFFF), WaterBtnFace, WaterBtnDark))
    } else {
        Brush.verticalGradient(listOf(Color.White, WaterBtnSoft))
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(brush)
            .border(2.dp, if (primary) WaterBtnDark else WaterBtnSoftDark, shape)
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(alpha = if (primary) 0.38f else 0.7f),
                    topLeft = Offset(size.width * 0.07f, size.height * 0.07f),
                    size = Size(size.width * 0.86f, size.height * 0.4f),
                    cornerRadius = CornerRadius(size.height * 0.2f, size.height * 0.2f)
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (primary) Color.White else WaterInk,
            fontSize = 17.sp,
            fontFamily = WaterFont
        )
    }
}