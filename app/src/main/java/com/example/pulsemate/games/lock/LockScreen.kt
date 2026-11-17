package com.example.pulsemate.games.lock

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pulsemate.games.lock.LockBad
import com.example.pulsemate.games.lock.LockBgBottom
import com.example.pulsemate.games.lock.LockBgTop
import com.example.pulsemate.games.lock.LockBrass
import com.example.pulsemate.games.lock.LockBrassDark
import com.example.pulsemate.games.lock.LockBrassLight
import com.example.pulsemate.games.lock.LockBtnDark
import com.example.pulsemate.games.lock.LockBtnFace
import com.example.pulsemate.games.lock.LockBtnSoft
import com.example.pulsemate.games.lock.LockBtnSoftDark
import com.example.pulsemate.games.lock.LockCold
import com.example.pulsemate.games.lock.LockFace
import com.example.pulsemate.games.lock.LockFont
import com.example.pulsemate.games.lock.LockGood
import com.example.pulsemate.games.lock.LockHaptics
import com.example.pulsemate.games.lock.LockHot
import com.example.pulsemate.games.lock.LockInk
import com.example.pulsemate.games.lock.LockPlate
import com.example.pulsemate.games.lock.LockResult
import com.example.pulsemate.games.lock.LockSteel
import com.example.pulsemate.games.lock.LockSteelDark
import com.example.pulsemate.games.lock.LockWarm
import com.example.pulsemate.games.lock.LockWorld
import com.example.pulsemate.haptics.LoveSpouseController
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val PREFS_NAME = "pulsemate_lockpick"
private const val BEST_KEY = "best"

private enum class LockPhase { START, PLAYING, CLEARED, OVER }

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

private fun heatColor(c: Float): Color {
    return if (c < 0.5f) {
        lerp(LockCold, LockWarm, c * 2f)
    } else {
        lerp(LockWarm, LockHot, (c - 0.5f) * 2f)
    }
}

private fun bandFor(c: Float, inWindow: Boolean): Int {
    return when {
        inWindow -> 4
        c < 0.12f -> 0
        c < 0.4f -> 1
        c < 0.7f -> 2
        else -> 3
    }
}

private val BandNames = listOf("Cold", "Cool", "Warm", "Hot", "Sweet spot!")

private fun DrawScope.drawDial(
    angle: Float,
    closeness: Float,
    inWindow: Boolean,
    flashJam: Float,
    flashSet: Float
) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = min(size.width, size.height) / 2f - 6f

    drawCircle(Color.Black.copy(alpha = 0.45f), r + 6f, Offset(c.x + 3f, c.y + 6f))

    drawCircle(
        brush = Brush.sweepGradient(
            listOf(LockBrassLight, LockBrassDark, LockBrass, LockBrassDark, LockBrassLight),
            c
        ),
        radius = r,
        center = c
    )

    drawCircle(LockFace, r * 0.8f, c)

    for (i in 0 until 72) {
        val a = i * 5f * PI.toFloat() / 180f
        val long = i % 6 == 0
        val inner = if (long) r * 0.82f else r * 0.88f
        val outer = r * 0.97f

        drawLine(
            color = LockBrassDark,
            start = Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
            end = Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
            strokeWidth = if (long) 4f else 2f,
            cap = StrokeCap.Round
        )
    }

    val heat = heatColor(closeness)

    drawCircle(
        color = heat.copy(alpha = 0.18f + 0.6f * closeness),
        radius = r * 0.7f,
        center = c,
        style = Stroke(width = r * 0.08f)
    )

    if (inWindow) {
        drawCircle(
            color = heat.copy(alpha = 0.3f),
            radius = r * 0.7f,
            center = c,
            style = Stroke(width = r * 0.16f)
        )
    }

    drawCircle(LockPlate, r * 0.52f, c)
    drawCircle(LockBrassDark, r * 0.52f, c, style = Stroke(width = 3f))
    drawCircle(LockSteelDark, r * 0.44f, c, style = Stroke(width = 2f))

    val a = angle * PI.toFloat() / 180f
    val dx = cos(a)
    val dy = sin(a)

    drawLine(
        color = Color.Black.copy(alpha = 0.4f),
        start = Offset(c.x - dx * r * 0.18f + 3f, c.y - dy * r * 0.18f + 4f),
        end = Offset(c.x + dx * r * 0.74f + 3f, c.y + dy * r * 0.74f + 4f),
        strokeWidth = r * 0.055f,
        cap = StrokeCap.Round
    )

    drawLine(
        color = LockSteel,
        start = Offset(c.x - dx * r * 0.18f, c.y - dy * r * 0.18f),
        end = Offset(c.x + dx * r * 0.74f, c.y + dy * r * 0.74f),
        strokeWidth = r * 0.055f,
        cap = StrokeCap.Round
    )

    drawLine(
        color = LockBrassLight,
        start = Offset(c.x + dx * r * 0.2f, c.y + dy * r * 0.2f),
        end = Offset(c.x + dx * r * 0.72f, c.y + dy * r * 0.72f),
        strokeWidth = r * 0.02f,
        cap = StrokeCap.Round
    )

    drawCircle(LockBrassLight, r * 0.05f, Offset(c.x + dx * r * 0.76f, c.y + dy * r * 0.76f))
    drawCircle(LockBrass, r * 0.1f, c)
    drawCircle(LockFace, r * 0.035f, c)

    if (flashJam > 0f) {
        drawCircle(LockBad.copy(alpha = 0.4f * flashJam), r * 0.8f, c)
    }

    if (flashSet > 0f) {
        drawCircle(LockGood.copy(alpha = 0.4f * flashSet), r * 0.8f, c)
    }
}

@Composable
fun LockScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { LockHaptics(loveSpouse) }
    val world = remember { LockWorld() }

    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(LockPhase.START) }
    var levelUi by remember { mutableIntStateOf(1) }
    var pinsUi by remember { mutableIntStateOf(0) }
    var pinsTotalUi by remember { mutableIntStateOf(2) }
    var triesUi by remember { mutableIntStateOf(LockWorld.TRIES) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var bandUi by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var bestAtStart by remember { mutableIntStateOf(best) }
    var newBest by remember { mutableStateOf(false) }
    var flashJam by remember { mutableFloatStateOf(0f) }
    var flashSet by remember { mutableFloatStateOf(0f) }
    var lastMove by remember { mutableStateOf(0L) }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    fun sync() {
        levelUi = world.level
        pinsUi = world.pinsSet
        pinsTotalUi = world.pins
        triesUi = world.tries
        scoreUi = world.score
    }

    fun startGame() {
        world.newGame()
        haptics.reset()
        bestAtStart = best
        newBest = false
        flashJam = 0f
        flashSet = 0f
        sync()
        phase = LockPhase.PLAYING
    }

    fun nextLock() {
        world.nextLevel()
        flashJam = 0f
        flashSet = 0f
        sync()
        phase = LockPhase.PLAYING
    }

    fun touch(x: Float, y: Float, w: Float, h: Float) {
        if (phase != LockPhase.PLAYING) return

        val dx = x - w / 2f
        val dy = y - h / 2f

        if (hypot(dx, dy) < 16f) return

        world.turnTo(atan2(dy, dx) * 180f / PI.toFloat())
        lastMove = System.currentTimeMillis()
    }

    fun nudge(amount: Float) {
        if (phase != LockPhase.PLAYING) return

        world.turnTo(world.angle + amount)
        lastMove = System.currentTimeMillis()
    }

    fun setPin() {
        if (phase != LockPhase.PLAYING) return

        val result = world.setPin()
        sync()

        when (result) {
            LockResult.SET -> {
                flashSet = 1f
                haptics.pulse(scope, 7, 260L)
            }

            LockResult.JAMMED -> {
                flashJam = 1f
                haptics.pulse(scope, 4, 450L)
            }

            LockResult.OPENED -> {
                flashSet = 1f
                haptics.pulse(scope, 9, 700L)

                if (world.level > best) {
                    best = world.level
                    saveBest(context, best)
                }

                phase = LockPhase.CLEARED
            }

            LockResult.FAILED -> {
                flashJam = 1f
                haptics.pulse(scope, 6, 800L)
                newBest = (world.level - 1) > bestAtStart
                phase = LockPhase.OVER
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
            val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now

            if (flashJam > 0f) flashJam = (flashJam - seconds * 2.2f).coerceAtLeast(0f)
            if (flashSet > 0f) flashSet = (flashSet - seconds * 2.2f).coerceAtLeast(0f)

            if (phase == LockPhase.PLAYING) {
                val band = bandFor(world.closeness(), world.inWindow())

                if (band != bandUi) bandUi = band

                val active = System.currentTimeMillis() - lastMove < 700L

                haptics.follow(if (active) world.hapticLevel() else 0)
            } else if (phase != LockPhase.START) {
                haptics.follow(0)
            }

            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LockBgTop, LockBgBottom)))
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
                    text = "LOCK $levelUi",
                    color = LockBrassLight,
                    fontSize = 22.sp,
                    letterSpacing = 2.sp,
                    fontFamily = LockFont
                )

                Text(
                    text = "PINS " + "●".repeat(pinsUi) + "○".repeat((pinsTotalUi - pinsUi).coerceAtLeast(0)),
                    color = LockInk,
                    fontSize = 16.sp,
                    fontFamily = LockFont
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TRIES " + "●".repeat(triesUi) + "○".repeat((LockWorld.TRIES - triesUi).coerceAtLeast(0)),
                    color = if (triesUi <= 1) LockBad else LockInk.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontFamily = LockFont
                )

                Text(
                    text = "SCORE $scoreUi   BEST $best",
                    color = LockInk.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontFamily = LockFont
                )
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clipToBounds()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()

                            touch(down.position.x, down.position.y, size.width.toFloat(), size.height.toFloat())

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (!change.pressed) break

                                touch(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat())
                                change.consume()
                            }
                        }
                    }
            ) {
                val tick = frame

                if (tick >= 0) {
                    drawDial(
                        angle = world.angle,
                        closeness = world.closeness(),
                        inWindow = world.inWindow(),
                        flashJam = flashJam,
                        flashSet = flashSet
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = BandNames[bandUi].uppercase(),
                    color = heatColor(listOf(0.05f, 0.3f, 0.55f, 0.8f, 1f)[bandUi]),
                    fontSize = 18.sp,
                    letterSpacing = 3.sp,
                    fontFamily = LockFont
                )

                Spacer(Modifier.height(6.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                ) {
                    val tick = frame

                    if (tick >= 0) {
                        val c = world.closeness()

                        drawRoundRect(
                            color = LockSteelDark,
                            size = size,
                            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                        )

                        drawRoundRect(
                            color = heatColor(c),
                            size = Size(size.width * c.coerceAtLeast(0.03f), size.height),
                            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                        )
                    }
                }
            }

            deviceProblem?.let { problem ->
                Text(
                    text = problem,
                    color = Color(0xFFE0A030),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LockButton(
                    text = "Back",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.stop()
                        onBack()
                    }
                )

                LockButton(
                    text = "−1°",
                    primary = false,
                    modifier = Modifier.weight(0.8f),
                    onClick = { nudge(-1f) }
                )

                LockButton(
                    text = "+1°",
                    primary = false,
                    modifier = Modifier.weight(0.8f),
                    onClick = { nudge(1f) }
                )

                LockButton(
                    text = "Set pin",
                    primary = true,
                    modifier = Modifier.weight(1.4f),
                    onClick = { setPin() }
                )
            }
        }

        when (phase) {
            LockPhase.START -> PopupCard {
                Text(
                    text = "🔒",
                    fontSize = 40.sp
                )

                Text(
                    text = "LOCK PICK",
                    color = LockBrassLight,
                    fontSize = 30.sp,
                    letterSpacing = 3.sp,
                    fontFamily = LockFont
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Drag around the dial to turn the pick. The meter and the glowing ring show how close you are, and the toy buzzes harder as you warm up. Set each pin inside the sweet spot. Three wrong tries and the lock snaps.",
                    color = LockInk.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = LockFont
                )

                Spacer(Modifier.height(18.dp))

                LockButton(
                    text = "Start",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                LockButton(
                    text = "Back",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            LockPhase.CLEARED -> PopupCard {
                Text(
                    text = "🔓",
                    fontSize = 40.sp
                )

                Text(
                    text = "LOCK OPENED",
                    color = LockGood,
                    fontSize = 28.sp,
                    letterSpacing = 2.sp,
                    fontFamily = LockFont
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Score ${world.score}",
                    color = LockInk,
                    fontSize = 22.sp,
                    fontFamily = LockFont
                )

                Spacer(Modifier.height(18.dp))

                LockButton(
                    text = "Next lock",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { nextLock() }
                )

                Spacer(Modifier.height(10.dp))

                LockButton(
                    text = "Menu",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            LockPhase.OVER -> PopupCard {
                Text(
                    text = "LOCK SNAPPED",
                    color = LockBad,
                    fontSize = 28.sp,
                    letterSpacing = 2.sp,
                    fontFamily = LockFont
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Locks opened ${world.level - 1}",
                    color = LockInk,
                    fontSize = 22.sp,
                    fontFamily = LockFont
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Score ${world.score}",
                    color = LockInk.copy(alpha = 0.85f),
                    fontSize = 16.sp,
                    fontFamily = LockFont
                )

                Text(
                    text = if (newBest) "New best!" else "Best $best",
                    color = if (newBest) LockBrassLight else LockInk.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontFamily = LockFont
                )

                Spacer(Modifier.height(18.dp))

                LockButton(
                    text = "Try again",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startGame() }
                )

                Spacer(Modifier.height(10.dp))

                LockButton(
                    text = "Menu",
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }

            LockPhase.PLAYING -> Unit
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)

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
                .clip(shape)
                .background(Brush.verticalGradient(listOf(LockPlate, LockFace)))
                .border(3.dp, Brush.verticalGradient(listOf(LockBrassLight, LockBrassDark)), shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun LockButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val face = if (primary) LockBtnFace else LockBtnSoft
    val edge = if (primary) LockBtnDark else LockBtnSoftDark

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
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.18f),
                        topLeft = Offset(3f, 3f),
                        size = Size(size.width - 6f, 3f),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (primary) Color(0xFF1B1408) else LockInk,
                fontSize = 15.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                fontFamily = LockFont
            )
        }
    }
}