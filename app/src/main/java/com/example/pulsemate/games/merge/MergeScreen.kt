package com.example.pulsemate.games.merge.screens

import androidx.compose.foundation.layout.safeDrawingPadding
import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pulsemate.games.merge.MERGE_SHOW_ICONS
import com.example.pulsemate.games.merge.MergeHaptics
import com.example.pulsemate.games.merge.MergeWorld
import com.example.pulsemate.games.merge.mergePieceColor
import com.example.pulsemate.games.merge.mergePieceIcon
import com.example.pulsemate.haptics.LoveSpouseController
import kotlinx.coroutines.delay
import kotlin.math.max

private const val BOARD_ASPECT = MergeWorld.HEIGHT / MergeWorld.WIDTH

private const val PREFS_NAME = "pulsemate_merge"
private const val PREFS_BEST = "best_score"

private val BASE_THICKNESS = 16.dp
private val HEADER_HEIGHT = 46.dp

private val SkyTop = Color(0xFF6EC9FF)
private val SkyBottom = Color(0xFFFFB3D9)
private val WoodBase = Color(0xFF9C5A2C)
private val WoodGrain = Color(0xFF6E3A15)
private val WoodLight = Color(0xFFC98650)
private val InsideTop = Color(0xFFF8E3B8)
private val InsideBottom = Color(0xFFEFCB93)
private val LineRed = Color(0xFFE5303F)
private val CandyOrange = Color(0xFFFF9F1C)
private val CandyRed = Color(0xFFEF476F)
private val CandyGreen = Color(0xFF3BB273)
private val Cream = Color(0xFFFFF4E0)
private val BrownText = Color(0xFF5A3213)

private val FunFont = FontFamily.Cursive

private val TitleStyle = TextStyle(
    fontFamily = FunFont,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    color = Color.White,
    shadow = Shadow(
        color = Color(0x66000000),
        offset = Offset(3f, 4f),
        blurRadius = 6f
    )
)

private enum class Phase {
    START,
    PLAYING,
    GAME_OVER
}

private fun loadBest(context: Context): Int {
    return context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(PREFS_BEST, 0)
}

private fun saveBest(context: Context, value: Int) {
    context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(PREFS_BEST, value)
        .apply()
}

@Composable
fun MergeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val loveSpouse = remember { LoveSpouseController(context) }
    val haptics = remember { MergeHaptics(loveSpouse) }
    val world = remember { MergeWorld() }
    val emojiPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    }

    var frame by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(loadBest(context)) }
    var newRecord by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.START) }
    var showQuit by remember { mutableStateOf(false) }
    var aimX by remember { mutableFloatStateOf(MergeWorld.WIDTH / 2f) }
    var deviceProblem by remember { mutableStateOf<String?>(null) }

    fun commitScore() {
        if (world.score > loadBest(context)) {
            saveBest(context, world.score)
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
            val seconds = (now - last) / 1_000_000_000f
            last = now

            if (phase == Phase.PLAYING && !showQuit && !world.gameOver) {
                val events = world.update(seconds)

                for (event in events) {
                    haptics.pulse(scope, MergeWorld.hapticLevelFor(event))
                }

                score = world.score

                if (world.gameOver) {
                    haptics.stop()
                    newRecord = world.score > best

                    if (newRecord) {
                        best = world.score
                        saveBest(context, best)
                    }

                    phase = Phase.GAME_OVER
                }
            }

            frame++
        }
    }

    fun startRound() {
        haptics.stop()
        world.reset()
        score = 0
        newRecord = false
        showQuit = false
        phase = Phase.PLAYING
    }

    fun leave() {
        haptics.stop()
        commitScore()
        best = max(best, world.score)
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))
            .drawBehind {
                val bubble = Color.White.copy(alpha = 0.14f)
                val base = size.minDimension
                drawCircle(bubble, base * 0.20f, Offset(size.width * 0.12f, size.height * 0.10f))
                drawCircle(bubble, base * 0.12f, Offset(size.width * 0.88f, size.height * 0.20f))
                drawCircle(bubble, base * 0.26f, Offset(size.width * 0.92f, size.height * 0.62f))
                drawCircle(bubble, base * 0.16f, Offset(size.width * 0.06f, size.height * 0.72f))
                drawCircle(bubble, base * 0.10f, Offset(size.width * 0.50f, size.height * 0.95f))
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                val baseThicknessPx = with(density) { BASE_THICKNESS.toPx() }
                val headerPx = with(density) { HEADER_HEIGHT.toPx() }
                val areaWidth = constraints.maxWidth.toFloat()
                val areaHeight = constraints.maxHeight.toFloat()
                val boardWidth = minOf(
                    areaWidth,
                    (areaHeight - baseThicknessPx - headerPx) / BOARD_ASPECT
                ).coerceAtLeast(1f)
                val boardHeight = boardWidth * BOARD_ASPECT
                val boardLeft = (areaWidth - boardWidth) / 2f
                val boardWidthDp = with(density) { boardWidth.toDp() }
                val boardHeightDp = with(density) { boardHeight.toDp() }
                val frameHeightDp = with(density) { (boardHeight + baseThicknessPx).toDp() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(boardWidth, boardLeft) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                aimX = (down.position.x - boardLeft) / boardWidth * MergeWorld.WIDTH

                                var released = false

                                while (!released) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }

                                    if (change == null) {
                                        released = true
                                    } else {
                                        aimX = (change.position.x - boardLeft) / boardWidth * MergeWorld.WIDTH
                                        change.consume()
                                        if (!change.pressed) released = true
                                    }
                                }

                                if (phase == Phase.PLAYING && !showQuit) {
                                    world.drop(aimX)
                                }
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier
                                .width(boardWidthDp)
                                .height(HEADER_HEIGHT)
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Merge Drop", style = TitleStyle)
                            Text(text = "$score", style = TitleStyle)
                        }

                        Box(
                            modifier = Modifier
                                .size(boardWidthDp, frameHeightDp)
                                .drawBehind { drawWoodBase(baseThicknessPx) }
                        ) {
                            Canvas(modifier = Modifier.size(boardWidthDp, boardHeightDp)) {
                                if (frame < 0) return@Canvas

                                val unit = size.width / MergeWorld.WIDTH

                                drawRect(
                                    brush = Brush.verticalGradient(listOf(InsideTop, InsideBottom))
                                )

                                drawRect(
                                    color = WoodGrain.copy(alpha = 0.45f),
                                    style = Stroke(width = 3.dp.toPx())
                                )

                                val danger = world.lineDanger
                                val lineY = MergeWorld.LINE_Y * unit

                                drawLine(
                                    color = LineRed.copy(alpha = 0.75f + 0.25f * danger),
                                    start = Offset(0f, lineY),
                                    end = Offset(size.width, lineY),
                                    strokeWidth = (2f + 3f * danger).dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 14f))
                                )

                                if (!world.gameOver) {
                                    val held = world.heldStage

                                    drawBall(
                                        stage = held,
                                        cx = world.clampX(aimX, held) * unit,
                                        cy = world.spawnY(held) * unit,
                                        r = MergeWorld.radius(held) * unit,
                                        alpha = if (world.canDrop) 1f else 0.35f,
                                        paint = emojiPaint
                                    )
                                }

                                for (piece in world.pieces) {
                                    drawBall(
                                        stage = piece.stage,
                                        cx = piece.x * unit,
                                        cy = piece.y * unit,
                                        r = MergeWorld.radius(piece.stage) * unit,
                                        alpha = 1f,
                                        paint = emojiPaint
                                    )
                                }
                            }
                        }
                    }
                }
            }

            deviceProblem?.let { problem ->
                Surface(
                    color = Color.White.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = problem,
                        color = Color(0xFFB3261E),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            CandyButton(
                text = "Quit",
                color = CandyRed,
                modifier = Modifier.fillMaxWidth(0.6f),
                onClick = {
                    if (phase == Phase.PLAYING) {
                        haptics.stop()
                        showQuit = true
                    } else {
                        leave()
                    }
                }
            )
        }

        if (showQuit) {
            PopupCard {
                Text(
                    text = "Quit?",
                    color = CandyRed,
                    fontSize = 38.sp,
                    fontFamily = FunFont,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Your score is kept if it beats your high score.",
                    color = BrownText,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CandyButton(
                        text = "Yes",
                        color = CandyRed,
                        modifier = Modifier.width(110.dp),
                        onClick = { leave() }
                    )

                    CandyButton(
                        text = "No",
                        color = CandyGreen,
                        modifier = Modifier.width(110.dp),
                        onClick = { showQuit = false }
                    )
                }
            }
        } else if (phase == Phase.START) {
            PopupCard {
                Text(
                    text = "Merge Drop",
                    color = CandyRed,
                    fontSize = 40.sp,
                    fontFamily = FunFont,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "High score",
                    color = BrownText.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    fontFamily = FunFont,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "$best",
                    color = BrownText,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(20.dp))

                CandyButton(
                    text = "Play",
                    color = CandyOrange,
                    modifier = Modifier.width(180.dp),
                    onClick = { startRound() }
                )
            }
        } else if (phase == Phase.GAME_OVER) {
            PopupCard {
                Text(
                    text = "Game over!",
                    color = CandyRed,
                    fontSize = 38.sp,
                    fontFamily = FunFont,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "$score",
                    color = BrownText,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = if (newRecord) "New high score!" else "High score $best",
                    color = if (newRecord) CandyOrange else BrownText.copy(alpha = 0.7f),
                    fontSize = 20.sp,
                    fontFamily = FunFont,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(20.dp))

                CandyButton(
                    text = "Play again",
                    color = CandyOrange,
                    modifier = Modifier.width(180.dp),
                    onClick = { startRound() }
                )
            }
        }
    }
}

@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Cream,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(5.dp, WoodBase),
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

@Composable
private fun CandyButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontFamily = FunFont,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun DrawScope.drawWoodBase(thickness: Float) {
    val topLeft = Offset(0f, size.height - thickness)
    val plank = Size(size.width, thickness)
    val corner = CornerRadius(thickness * 0.35f, thickness * 0.35f)

    drawRoundRect(color = WoodBase, topLeft = topLeft, size = plank, cornerRadius = corner)

    val grainWidth = thickness * 0.07f

    for (i in 1..3) {
        val y = topLeft.y + plank.height * i / 4f

        drawLine(
            color = WoodGrain.copy(alpha = 0.35f),
            start = Offset(thickness * 0.4f, y),
            end = Offset(size.width - thickness * 0.4f, y),
            strokeWidth = grainWidth
        )
    }

    drawLine(
        color = WoodLight.copy(alpha = 0.6f),
        start = Offset(thickness * 0.4f, topLeft.y + thickness * 0.12f),
        end = Offset(size.width - thickness * 0.4f, topLeft.y + thickness * 0.12f),
        strokeWidth = grainWidth * 1.5f
    )

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.3f),
        topLeft = topLeft,
        size = plank,
        cornerRadius = corner,
        style = Stroke(width = 2f)
    )
}

private fun DrawScope.drawBall(
    stage: Int,
    cx: Float,
    cy: Float,
    r: Float,
    alpha: Float,
    paint: Paint
) {
    val base = mergePieceColor(stage)
    val center = Offset(cx, cy)

    drawCircle(color = base.copy(alpha = alpha), radius = r, center = center)

    drawCircle(
        color = Color.White.copy(alpha = 0.2f * alpha),
        radius = r * 0.38f,
        center = Offset(cx - r * 0.32f, cy - r * 0.34f)
    )

    drawCircle(
        color = Color.Black.copy(alpha = 0.25f * alpha),
        radius = r,
        center = center,
        style = Stroke(width = r * 0.08f)
    )

    if (MERGE_SHOW_ICONS) {
        paint.textSize = r * 1.1f
        paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)

        val metrics = paint.fontMetrics
        val baseline = cy - (metrics.ascent + metrics.descent) / 2f

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(mergePieceIcon(stage), cx, baseline, paint)
        }
    }
}