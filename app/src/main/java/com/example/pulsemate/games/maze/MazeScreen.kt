package com.example.pulsemate.games.maze.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.inset
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
import com.example.pulsemate.games.maze.Cocoa
import com.example.pulsemate.games.maze.MazeFont
import com.example.pulsemate.games.maze.MazeHaptics
import com.example.pulsemate.games.maze.MazeHeatStops
import com.example.pulsemate.games.maze.MazeMode
import com.example.pulsemate.games.maze.MazeWorld
import com.example.pulsemate.games.maze.StarYellow
import com.example.pulsemate.games.maze.mazeHeat
import com.example.pulsemate.haptics.LoveSpouseController
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS_NAME = "pulsemate_maze"
private const val BEST_KEY = "best"
private const val MIN_IMPACT = 0.3f
private const val TILT_RANGE = 5.5f
private const val DEADZONE = 0.06f
private const val SMOOTHING = 0.25f
private const val NEXT_LEVEL_DELAY_MS = 1500L
private const val RELEASE_SECONDS = 0.08f
private const val FOG_HOLD = 0.5f
private const val FOG_TOUCH = 0.05f

private val MazeThemes = listOf(
    listOf(0xFFF9B8CF, 0xFFF083AA, 0xFFFFC7DB, 0xFFF8B0CB, 0xFF9E2F5C, 0xFFC9487D, 0xFFE889AE),
    listOf(0xFFDDF08A, 0xFF7FCB6B, 0xFF8FD672, 0xFF7FCB5E, 0xFF1B6B2C, 0xFF2E9444, 0xFF63BF6E),
    listOf(0xFF9FD0FF, 0xFF5FA3EB, 0xFF86BEF2, 0xFF74B0EA, 0xFF1F4E96, 0xFF2F6FC2, 0xFF6AA3E6),
    listOf(0xFFD1B5F5, 0xFFA47BE0, 0xFFBA9BEB, 0xFFA98AE0, 0xFF5B34A0, 0xFF7C52C4, 0xFFAE8CE6),
    listOf(0xFFFFD08A, 0xFFFF9F4D, 0xFFFFB873, 0xFFF5A55C, 0xFFA8480F, 0xFFD96A1F, 0xFFF59A5A),
    listOf(0xFFFFEA7A, 0xFFF5C530, 0xFFF9DC5C, 0xFFEFCB44, 0xFF9A7A05, 0xFFC79A10, 0xFFE8C547),
    listOf(0xFF8EE6D8, 0xFF3FB9A8, 0xFF6FD6C4, 0xFF5CC9B7, 0xFF12695E, 0xFF1F9488, 0xFF5CC2B6)
).map { row -> row.map { Color(it) } }

private fun nextTheme(current: Int): Int {
    var next = Random.nextInt(MazeThemes.size)
    while (next == current) next = Random.nextInt(MazeThemes.size)
    return next
}

private class MazeTint(
    val face: Color,
    val dark: Color,
    val panel: Color,
    val soft: Color,
    val softDark: Color
)

private class FogMemory {
    var n = 0
    var level = 0
    var h = FloatArray(0)
    var v = FloatArray(0)

    fun reset(size: Int, lvl: Int) {
        n = size
        level = lvl
        h = FloatArray((size + 1) * size)
        v = FloatArray(size * (size + 1))
    }
}

private fun touching(px: Float, py: Float, x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
    val dx = maxOf(x0 - px, 0f, px - x1)
    val dy = maxOf(y0 - py, 0f, py - y1)
    val reach = MazeWorld.BALL_R + FOG_TOUCH

    return dx * dx + dy * dy <= reach * reach
}

private fun collectContacts(world: MazeWorld, out: HashSet<Int>) {
    out.clear()

    val n = world.size

    if (n <= 0 || world.hWall.size != n + 1 || world.vWall.size != n) return

    val t = MazeWorld.WALL / 2f
    val bx = world.px
    val by2 = world.py

    for (j in 0..n) {
        for (i in 0 until n) {
            if (world.hWall[j][i] && touching(bx, by2, i - t, j - t, i + 1 + t, j + t)) {
                var start = i

                while (start > 0 && world.hWall[j][start - 1]) start--

                out.add((j * 32 + start) * 2)
            }
        }
    }

    for (j in 0 until n) {
        for (i in 0..n) {
            if (world.vWall[j][i] && touching(bx, by2, i - t, j - t, i + t, j + 1 + t)) {
                var start = j

                while (start > 0 && world.vWall[start - 1][i]) start--

                out.add((i * 32 + start) * 2 + 1)
            }
        }
    }
}

private enum class MazePhase { START, PLAYING }

private fun bestKey(mode: MazeMode): String = "${BEST_KEY}_${mode.name}"

private fun loadBest(context: Context, mode: MazeMode): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(bestKey(mode), 0)
}

private fun saveBest(context: Context, mode: MazeMode, value: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(bestKey(mode), value)
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

private fun tiltInput(value: Float): Float {
    val normalized = (value / TILT_RANGE).coerceIn(-1f, 1f)
    return if (abs(normalized) < DEADZONE) 0f else normalized
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${if (s < 10) "0" else ""}$s"
}

@Composable
fun MazeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { MazeHaptics(loveSpouse) }
    val world = remember { MazeWorld() }
    val emojiPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    }

    var themeIndex by remember { mutableIntStateOf(Random.nextInt(MazeThemes.size)) }
    val theme = MazeThemes[themeIndex]
    val bgTop by animateColorAsState(theme[0], tween(900), label = "bgTop")
    val bgBottom by animateColorAsState(theme[1], tween(900), label = "bgBottom")
    val grassA by animateColorAsState(theme[2], tween(900), label = "grassA")
    val grassB by animateColorAsState(theme[3], tween(900), label = "grassB")
    val hedgeDark by animateColorAsState(theme[4], tween(900), label = "hedgeDark")
    val hedgeMid by animateColorAsState(theme[5], tween(900), label = "hedgeMid")
    val hedgeLight by animateColorAsState(theme[6], tween(900), label = "hedgeLight")

    val tint = MazeTint(
        face = hedgeMid,
        dark = hedgeDark,
        panel = lerp(Color.White, bgTop, 0.45f),
        soft = lerp(Color.White, bgTop, 0.75f),
        softDark = bgBottom
    )

    val fogMemory = remember { FogMemory() }
    val bumpState = remember { floatArrayOf(0f, 1f, 0f, 0f) }
    val seen = remember { HashMap<Int, Float>() }
    val contacts = remember { HashSet<Int>() }
    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(MazePhase.START) }
    var modeChoice by remember { mutableStateOf(MazeMode.CLASSIC) }
    var best by remember { mutableIntStateOf(loadBest(context, MazeMode.CLASSIC)) }
    var showQuit by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf(false) }
    var secondsUi by remember { mutableIntStateOf(0) }
    var levelUi by remember { mutableIntStateOf(1) }
    var hasSensor by remember { mutableStateOf(true) }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    val raw = remember { FloatArray(2) }
    val filtered = remember { FloatArray(2) }
    val base = remember { FloatArray(2) }
    val touch = remember { FloatArray(2) }

    fun commitScore() {
        if (world.completed > loadBest(context, world.mode)) {
            saveBest(context, world.mode, world.completed)
        }
    }

    fun recenter() {
        base[0] = raw[0]
        base[1] = raw[1]
        filtered[0] = raw[0]
        filtered[1] = raw[1]
    }

    fun startRun(mode: MazeMode) {
        world.start(mode)
        themeIndex = nextTheme(themeIndex)
        haptics.reset()
        recenter()
        levelUi = world.level
        secondsUi = 0
        banner = false
        showQuit = false
        phase = MazePhase.PLAYING
    }

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        onDispose {
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                raw[0] = -event.values[0]
                raw[1] = event.values[1]
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        hasSensor = sensor != null

        if (sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            manager.unregisterListener(listener)
        }
    }

    LaunchedEffect(haptics) {
        haptics.reset()
    }

    DisposableEffect(haptics) {
        onDispose {
            haptics.reset()
            commitScore()
        }
    }

    DisposableEffect(lifecycleOwner, haptics) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                haptics.stop()
                commitScore()
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

            if (phase == MazePhase.PLAYING && !showQuit && !banner) {
                val tx: Float
                val ty: Float

                if (hasSensor) {
                    filtered[0] += (raw[0] - filtered[0]) * SMOOTHING
                    filtered[1] += (raw[1] - filtered[1]) * SMOOTHING
                    tx = tiltInput(filtered[0] - base[0])
                    ty = tiltInput(filtered[1] - base[1])
                } else {
                    tx = touch[0]
                    ty = touch[1]
                }

                val beforeX = world.px
                val beforeY = world.py
                val impact = world.update(seconds, tx, ty)
                val dxm = world.px - beforeX
                val dym = world.py - beforeY
                val approach = maxOf(impact, bumpState[2])

                if (seconds > 0f) {
                    bumpState[2] = sqrt(dxm * dxm + dym * dym) / seconds
                }

                if (world.mode == MazeMode.FOG) {
                    val size = world.size

                    if (fogMemory.n != size || fogMemory.level != world.level) {
                        fogMemory.reset(size, world.level)
                    }

                    for (k in fogMemory.h.indices) {
                        fogMemory.h[k] = maxOf(0f, fogMemory.h[k] - seconds)
                    }

                    for (k in fogMemory.v.indices) {
                        fogMemory.v[k] = maxOf(0f, fogMemory.v[k] - seconds)
                    }

                    val t = MazeWorld.WALL / 2f
                    val bx = world.px
                    val by2 = world.py

                    if (world.hWall.size == size + 1 && world.vWall.size == size) {
                        for (j in 0..size) {
                            for (i in 0 until size) {
                                if (world.hWall[j][i] && touching(bx, by2, i - t, j - t, i + 1 + t, j + t)) {
                                    fogMemory.h[j * size + i] = FOG_HOLD
                                }
                            }
                        }

                        for (j in 0 until size) {
                            for (i in 0..size) {
                                if (world.vWall[j][i] && touching(bx, by2, i - t, j - t, i + t, j + 1 + t)) {
                                    fogMemory.v[j * (size + 1) + i] = FOG_HOLD
                                }
                            }
                        }
                    }
                }

                if (world.level != bumpState[3].toInt()) {
                    bumpState[3] = world.level.toFloat()
                    seen.clear()
                }

                bumpState[1] += seconds
                collectContacts(world, contacts)

                var fresh = false

                for (key in contacts) {
                    val last = seen[key]

                    if (last == null || bumpState[1] - last > RELEASE_SECONDS) fresh = true

                    seen[key] = bumpState[1]
                }

                if (fresh) {
                    haptics.bump(scope, maxOf(approach, 1.5f))
                }

                if (world.won) {
                    banner = true
                    haptics.win(scope)

                    launch {
                        delay(NEXT_LEVEL_DELAY_MS)
                        world.nextLevel()
                        themeIndex = nextTheme(themeIndex)
                        levelUi = world.level
                        secondsUi = 0
                        banner = false
                    }
                }

                val secs = world.time.toInt()
                if (secs != secondsUi) secondsUi = secs
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = size.minDimension * 0.35f,
                    center = Offset(size.width * 0.9f, size.height * 0.08f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f),
                    radius = size.minDimension * 0.25f,
                    center = Offset(size.width * 0.08f, size.height * 0.92f)
                )
            }
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
                    text = "Level $levelUi",
                    color = Cocoa,
                    fontSize = 26.sp,
                    fontFamily = MazeFont
                )

                Text(
                    text = formatTime(secondsUi),
                    color = Cocoa,
                    fontSize = 26.sp,
                    fontFamily = MazeFont
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Cocoa.copy(alpha = 0.15f))
                    .drawBehind {
                        val f = frame
                        val playing = phase == MazePhase.PLAYING && f >= 0
                        val fraction = if (playing) world.warmth.coerceIn(0f, 1f) else 0f
                        val corner = size.height / 2f

                        drawRoundRect(
                            brush = Brush.horizontalGradient(MazeHeatStops, endX = size.width),
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = CornerRadius(corner, corner)
                        )
                    }
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val boardSize = if (maxWidth < maxHeight) maxWidth else maxHeight

                Canvas(
                    modifier = Modifier
                        .size(boardSize)
                        .pointerInput(hasSensor) {
                            if (hasSensor) return@pointerInput

                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f

                                touch[0] = ((down.position.x - centerX) / centerX).coerceIn(-1f, 1f)
                                touch[1] = ((down.position.y - centerY) / centerY).coerceIn(-1f, 1f)

                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    touch[0] = ((change.position.x - centerX) / centerX).coerceIn(-1f, 1f)
                                    touch[1] = ((change.position.y - centerY) / centerY).coerceIn(-1f, 1f)
                                    change.consume()
                                } while (event.changes.any { it.pressed })

                                touch[0] = 0f
                                touch[1] = 0f
                            }
                        }
                ) {
                    val tick = frame
                    val n = world.size

                    if (n > 0 && world.hWall.size == n + 1 && world.vWall.size == n && tick >= 0) {
                        val fog = world.mode == MazeMode.FOG
                        val ballX = world.px
                        val ballY = world.py
                        val warmth = world.warmth
                        val heat = mazeHeat(warmth)

                        drawRoundRect(
                            color = grassA,
                            cornerRadius = CornerRadius(size.width * 0.05f, size.width * 0.05f)
                        )

                        inset(size.width * 0.03f) {
                            val unit = size.width / n
                            val hedge = MazeWorld.WALL * unit

                            val reveal = !fog || banner

                            fun hVis(j: Int, i: Int): Float {
                                if (reveal) return 1f
                                if (fogMemory.n != n) return 0f

                                return (fogMemory.h[j * n + i] / (FOG_HOLD * 0.5f)).coerceIn(0f, 1f)
                            }

                            fun vVis(j: Int, i: Int): Float {
                                if (reveal) return 1f
                                if (fogMemory.n != n) return 0f

                                return (fogMemory.v[j * (n + 1) + i] / (FOG_HOLD * 0.5f)).coerceIn(0f, 1f)
                            }

                            fun drawHedges(color: Color, widthFactor: Float, shiftX: Float, shiftY: Float) {
                                for (j in 0..n) {
                                    for (i in 0 until n) {
                                        if (world.hWall[j][i]) {
                                            val alpha = hVis(j, i)

                                            if (alpha > 0.02f) {
                                                drawLine(
                                                    color = color.copy(alpha = alpha),
                                                    start = Offset(i * unit + shiftX, j * unit + shiftY),
                                                    end = Offset((i + 1) * unit + shiftX, j * unit + shiftY),
                                                    strokeWidth = hedge * widthFactor,
                                                    cap = StrokeCap.Round
                                                )
                                            }
                                        }
                                    }
                                }

                                for (j in 0 until n) {
                                    for (i in 0..n) {
                                        if (world.vWall[j][i]) {
                                            val alpha = vVis(j, i)

                                            if (alpha > 0.02f) {
                                                drawLine(
                                                    color = color.copy(alpha = alpha),
                                                    start = Offset(i * unit + shiftX, j * unit + shiftY),
                                                    end = Offset(i * unit + shiftX, (j + 1) * unit + shiftY),
                                                    strokeWidth = hedge * widthFactor,
                                                    cap = StrokeCap.Round
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            for (j in 0 until n) {
                                for (i in 0 until n) {
                                    drawRect(
                                        color = if ((i + j) % 2 == 0) grassA else grassB,
                                        topLeft = Offset(i * unit, j * unit),
                                        size = Size(unit, unit)
                                    )
                                }
                            }

                            val starX = (world.exitX + 0.5f) * unit
                            val starY = (world.exitY + 0.5f) * unit
                            val starVisible = if (reveal) 1f else 0f
                            val pulse = 1f + 0.12f * sin(world.time * 4f)

                            drawCircle(
                                color = StarYellow.copy(alpha = 0.35f * starVisible),
                                radius = unit * 0.42f * pulse,
                                center = Offset(starX, starY)
                            )

                            drawIntoCanvas { canvas ->
                                emojiPaint.textSize = unit * 0.62f * pulse
                                emojiPaint.alpha = (255 * starVisible).toInt().coerceIn(0, 255)

                                val metrics = emojiPaint.fontMetrics
                                val baseline = starY - (metrics.ascent + metrics.descent) / 2f

                                canvas.nativeCanvas.drawText("⭐", starX, baseline, emojiPaint)
                            }

                            drawHedges(hedgeDark, 1.0f, 0f, hedge * 0.08f)
                            drawHedges(hedgeMid, 0.72f, 0f, 0f)
                            drawHedges(hedgeLight, 0.22f, -hedge * 0.12f, -hedge * 0.16f)

                            val radius = MazeWorld.BALL_R * unit
                            val center = Offset(ballX * unit, ballY * unit)

                            drawCircle(
                                color = Color.Black.copy(alpha = 0.22f),
                                radius = radius,
                                center = Offset(center.x + radius * 0.1f, center.y + radius * 0.2f)
                            )

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White, heat, lerp(heat, Color(0xFF7A3B1E), 0.35f)),
                                    center = Offset(center.x - radius * 0.35f, center.y - radius * 0.4f),
                                    radius = radius * 1.8f
                                ),
                                radius = radius,
                                center = center
                            )

                            drawCircle(
                                color = heat.copy(alpha = 0.75f),
                                radius = radius * 1.05f,
                                center = center
                            )

                            drawIntoCanvas { canvas ->
                                emojiPaint.textSize = radius * 2.1f
                                emojiPaint.alpha = 255

                                val metrics = emojiPaint.fontMetrics
                                val baseline = center.y - (metrics.ascent + metrics.descent) / 2f

                                canvas.nativeCanvas.drawText("😈", center.x, baseline, emojiPaint)
                            }
                        }

                        drawRoundRect(
                            color = hedgeDark.copy(alpha = 0.35f),
                            cornerRadius = CornerRadius(size.width * 0.05f, size.width * 0.05f),
                            style = Stroke(width = size.width * 0.012f)
                        )
                    }
                }
            }

            if (!hasSensor && phase == MazePhase.PLAYING) {
                Text(
                    text = "No tilt sensor found. Press and drag on the board to tilt.",
                    color = Cocoa.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontFamily = MazeFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                )
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
                    .padding(start = 22.dp, end = 22.dp, bottom = 14.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CuteButton(
                    tint = tint,
                    text = "Recenter",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = { recenter() }
                )

                CuteButton(
                    tint = tint,
                    text = "Quit",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (phase == MazePhase.PLAYING) showQuit = true
                    }
                )
            }
        }

        if (banner) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Level complete!",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontFamily = MazeFont,
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
                        .background(tint.face)
                        .border(3.dp, tint.dark, RoundedCornerShape(24.dp))
                        .padding(horizontal = 26.dp, vertical = 14.dp)
                )
            }
        }

        if (phase == MazePhase.START) {
            PopupCard(tint = tint) {
                Text(
                    text = "Tilt Maze",
                    color = tint.dark,
                    fontSize = 38.sp,
                    fontFamily = MazeFont
                )

                if (best > 0) {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Best: level $best",
                        color = Cocoa.copy(alpha = 0.8f),
                        fontSize = 18.sp,
                        fontFamily = MazeFont
                    )
                }

                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (mode in MazeMode.values()) {
                        CuteButton(
                            tint = tint,
                            text = mode.title,
                            primary = modeChoice == mode,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                modeChoice = mode
                                best = loadBest(context, mode)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = modeChoice.hint,
                    color = Cocoa,
                    fontSize = 15.sp,
                    fontFamily = MazeFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(54.dp)
                )

                Spacer(Modifier.height(8.dp))

                CuteButton(
                    tint = tint,
                    text = "Play",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startRun(modeChoice) }
                )

                Spacer(Modifier.height(10.dp))

                CuteButton(
                    tint = tint,
                    text = "Back",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }
        }

        if (showQuit) {
            PopupCard(tint = tint) {
                Text(
                    text = "Quit the maze?",
                    color = tint.dark,
                    fontSize = 30.sp,
                    fontFamily = MazeFont
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Your best level is kept.",
                    color = Cocoa,
                    fontSize = 16.sp,
                    fontFamily = MazeFont,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CuteButton(
                        tint = tint,
                        text = "No",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = { showQuit = false }
                    )

                    CuteButton(
                        tint = tint,
                        text = "Yes",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            commitScore()
                            haptics.stop()
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PopupCard(tint: MazeTint, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
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
                .background(tint.panel)
                .border(4.dp, tint.face, RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun CuteButton(
    tint: MazeTint,
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val face = if (primary) tint.face else tint.soft
    val edge = if (primary) tint.dark else tint.softDark
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
                color = if (primary) Color.White else Cocoa,
                fontSize = 18.sp,
                fontFamily = MazeFont
            )
        }
    }
}