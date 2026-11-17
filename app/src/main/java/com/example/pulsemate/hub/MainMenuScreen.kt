package com.example.pulsemate.hub

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import kotlinx.coroutines.delay
import com.example.pulsemate.games.brick.BrickBase
import com.example.pulsemate.games.brick.BrickFont
import com.example.pulsemate.games.fruit.FruitDefs
import com.example.pulsemate.games.fruit.FruitFont
import com.example.pulsemate.games.maze.MazeFont
import com.example.pulsemate.games.water.WaterColors
import com.example.pulsemate.games.water.WaterFont
import com.example.pulsemate.games.fit.FitColors
import com.example.pulsemate.games.lock.LockFont
import com.example.pulsemate.games.draw.DrawFont
import com.example.pulsemate.games.tile.TileFont
import com.example.pulsemate.games.chase.ChaseFont
import com.example.pulsemate.games.flip.FlipFont
import com.example.pulsemate.games.pop.PopFont
import com.example.pulsemate.games.dig.DigFont
import com.example.pulsemate.games.reel.ReelFont
import com.example.pulsemate.games.fit.FitFont
import com.example.pulsemate.games.battle.BattleFont
import com.example.pulsemate.games.battle.BattleShipColors
import com.example.pulsemate.games.four.FourAiColor
import com.example.pulsemate.games.four.FourFont
import com.example.pulsemate.games.four.FourHumanColor
import com.example.pulsemate.games.whack.WhackDirt
import com.example.pulsemate.games.whack.WhackFont
import com.example.pulsemate.games.memory.FeltDark
import com.example.pulsemate.games.memory.FeltLight
import com.example.pulsemate.games.memory.Gold
import com.example.pulsemate.games.memory.MemoryFont
import com.example.pulsemate.games.snake.SnakeFont
import com.example.pulsemate.games.snake.SnakeFruits

private val CardShape = RoundedCornerShape(24.dp)

private val HubTop = Color(0xFF0A0B1F)
private val HubMiddle = Color(0xFF1A1033)
private val HubBottom = Color(0xFF2D1245)

@Composable
fun MainMenuScreen(
    onQuizClick: () -> Unit,
    onMergeClick: () -> Unit,
    onMemoryClick: () -> Unit,
    onMazeClick: () -> Unit,
    onSnakeClick: () -> Unit,
    onBrickClick: () -> Unit,
    onFruitClick: () -> Unit,
    onWaterClick: () -> Unit,
    onWhackClick: () -> Unit,
    onFitClick: () -> Unit,
    onFourClick: () -> Unit,
    onBattleClick: () -> Unit,
    onLockClick: () -> Unit,
    onDrawClick: () -> Unit,
    onTileClick: () -> Unit,
    onChaseClick: () -> Unit,
    onFlipClick: () -> Unit,
    onPopClick: () -> Unit,
    onDigClick: () -> Unit,
    onReelClick: () -> Unit,
    onPrettyClick: () -> Unit,
    onRemoteClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HubTop, HubMiddle, HubBottom)))
            .drawBehind {
                val base = size.minDimension

                drawCircle(
                    color = Color(0xFF3B82F6).copy(alpha = 0.18f),
                    radius = base * 0.42f,
                    center = Offset(size.width * 0.05f, size.height * 0.10f)
                )
                drawCircle(
                    color = Color(0xFFC026D3).copy(alpha = 0.18f),
                    radius = base * 0.46f,
                    center = Offset(size.width * 1.0f, size.height * 0.55f)
                )
                drawCircle(
                    color = Color(0xFFFB923C).copy(alpha = 0.14f),
                    radius = base * 0.44f,
                    center = Offset(size.width * 0.15f, size.height * 1.0f)
                )
            }
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "PULSE",
                color = Color.White,
                fontSize = 46.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp
            )

            Text(
                text = "MATE",
                fontSize = 46.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                style = TextStyle(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF60A5FA), Color(0xFFE879F9), Color(0xFFFB923C))
                    )
                )
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "PLAY  ·  CONNECT  ·  FEEL",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HeroCard(title = "LOVE SPOUSE", onClick = onRemoteClick, modifier = Modifier.weight(1f))
                HeroCard(title = "PRETTYLOVE", onClick = onPrettyClick, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                QuizCard(onClick = onQuizClick, modifier = Modifier.weight(1f))
                MergeCard(onClick = onMergeClick, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MazeCard(onClick = onMazeClick, modifier = Modifier.weight(1f))
                SnakeCard(onClick = onSnakeClick, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MemoryCard(onClick = onMemoryClick, modifier = Modifier.weight(1f))
                BrickCard(onClick = onBrickClick, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FruitCard(onClick = onFruitClick, modifier = Modifier.weight(1f))
                WaterCard(onClick = onWaterClick, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WhackCard(onClick = onWhackClick, modifier = Modifier.weight(1f))
                FitCard(onClick = onFitClick, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FourCard(onClick = onFourClick, modifier = Modifier.weight(1f))
                BattleCard(onClick = onBattleClick, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LockCard(onClick = onLockClick, modifier = Modifier.weight(1f))
                DrawCard(onClick = onDrawClick, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TileCard(onClick = onTileClick, modifier = Modifier.weight(1f))
                ChaseCard(onClick = onChaseClick, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FlipCard(onClick = onFlipClick, modifier = Modifier.weight(1f))
                PopCard(onClick = onPopClick, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DigCard(onClick = onDigClick, modifier = Modifier.weight(1f))
                ReelCard(onClick = onReelClick, modifier = Modifier.weight(1f))
            }

        }
    }
}

private val MazeCardPastels = listOf(
    Color(0xFFF8A0BE) to Color(0xFFEC6F9C),
    Color(0xFFFFC27A) to Color(0xFFFF9440),
    Color(0xFFFFE56E) to Color(0xFFF7C12E),
    Color(0xFFB5E56F) to Color(0xFF6FC95B),
    Color(0xFF8FE3D5) to Color(0xFF45BFAE),
    Color(0xFFA5D2FF) to Color(0xFF5CA3EA),
    Color(0xFFCFB4F7) to Color(0xFF9F78DE)
)

private val cardEmojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

private val cardTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
}

private fun DrawScope.cardEmoji(text: String, x: Float, y: Float, size: Float) {
    drawIntoCanvas { canvas ->
        cardEmojiPaint.textSize = size

        val m = cardEmojiPaint.fontMetrics

        canvas.nativeCanvas.drawText(text, x, y - (m.ascent + m.descent) / 2f, cardEmojiPaint)
    }
}

private fun DrawScope.cardText(text: String, x: Float, y: Float, size: Float, color: Color) {
    drawIntoCanvas { canvas ->
        cardTextPaint.textSize = size
        cardTextPaint.color = color.toArgb()

        val m = cardTextPaint.fontMetrics

        canvas.nativeCanvas.drawText(text, x, y - (m.ascent + m.descent) / 2f, cardTextPaint)
    }
}

private val TitleShadow = Shadow(
    color = Color(0x88000000),
    offset = Offset(3f, 4f),
    blurRadius = 6f
)

@Composable
private fun CardBase(
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    art: (DrawScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    var card = modifier
        .height(104.dp)
        .clip(CardShape)
        .background(brush)

    if (art != null) {
        card = card.drawBehind { art(this) }
    }

    if (borderColor != null) {
        card = card.border(1.5.dp, borderColor, CardShape)
    }

    Box(
        modifier = card
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun QuizCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF3B82F6), Color(0xFFFB923C), Color(0xFFC026D3))
        ),
        onClick = onClick,
        modifier = modifier,
        art = {
            val w = size.width
            val h = size.height
            val bars = 13

            cardText("?", w * 0.9f, h * 0.4f, h * 0.7f, Color.White.copy(alpha = 0.25f))
            cardText("?", w * 0.1f, h * 0.55f, h * 0.45f, Color.White.copy(alpha = 0.2f))

            for (i in 0 until bars) {
                val tt = i / (bars - 1f)
                val level = 0.25f + 0.75f * kotlin.math.abs(kotlin.math.sin(tt * 6.2f + 0.6f))
                val bh = h * 0.34f * level
                val bw = w * 0.04f
                val x = w * (0.1f + 0.8f * tt)

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.65f),
                    topLeft = Offset(x - bw / 2f, h * 0.9f - bh),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(bw / 2f, bw / 2f)
                )
            }
        }
    ) {
        Text(
            text = "HAPTIC QUIZ",
            color = Color.White,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        )
    }
}

@Composable
private fun MergeCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFF6EC9FF), Color(0xFFFFB3D9))),
        onClick = onClick,
        modifier = modifier,
        art = {
            val h = size.height
            val w = size.width
            val balls = listOf(
                Triple(0.12f, 0.80f, 0.15f) to Color(0xFFE7546B),
                Triple(0.31f, 0.86f, 0.11f) to Color(0xFFF6B73C),
                Triple(0.50f, 0.90f, 0.08f) to Color(0xFFFF8FA3),
                Triple(0.68f, 0.85f, 0.12f) to Color(0xFF5BC17A),
                Triple(0.88f, 0.78f, 0.17f) to Color(0xFF8E6BD8)
            )

            for ((pos, color) in balls) {
                val c = Offset(w * pos.first, h * pos.second)
                val r = h * pos.third

                drawCircle(color, r, c)
                drawCircle(Color.White.copy(alpha = 0.4f), r * 0.25f, Offset(c.x - r * 0.35f, c.y - r * 0.4f))
            }

            drawRect(Color(0xFF9C5A2C), Offset(0f, h - 7.dp.toPx()), Size(w, 7.dp.toPx()))
        }
    ) {
        Text(
            text = "Merge Drop",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            style = TextStyle(
                fontFamily = FontFamily.Cursive,
                shadow = TitleShadow
            )
        )
    }
}

@Composable
private fun MazeCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFF6FCF97), Color(0xFF2F9E6B))),
        onClick = onClick,
        modifier = modifier,
        art = {
            val w = size.width
            val h = size.height
            val gx = w * 0.08f
            val gy = h * 0.5f
            val cw = w * 0.84f / 7f
            val ch = h * 0.42f / 3f
            val wall = Color.White.copy(alpha = 0.8f)

            fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
                drawLine(wall, Offset(gx + x1 * cw, gy + y1 * ch), Offset(gx + x2 * cw, gy + y2 * ch), 5f, StrokeCap.Round)
            }

            line(0f, 0f, 7f, 0f)
            line(0f, 3f, 7f, 3f)
            line(0f, 0f, 0f, 3f)
            line(7f, 0f, 7f, 3f)
            line(2f, 0f, 2f, 2f)
            line(2f, 2f, 4f, 2f)
            line(4f, 1f, 4f, 3f)
            line(4f, 1f, 6f, 1f)
            line(6f, 0f, 6f, 2f)

            cardEmoji("😈", gx + 0.5f * cw, gy + 0.5f * ch, ch * 0.95f)
            cardEmoji("⭐", gx + 6.5f * cw, gy + 2.5f * ch, ch * 0.95f)
        }
    ) {
        Text(
            text = "Tilt Maze",
            color = Color.White,
            fontSize = 26.sp,
            fontFamily = MazeFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            style = TextStyle(shadow = TitleShadow)
        )
    }
}

@Composable
private fun SnakeCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFFE4DCFF), Color(0xFFFFC9DD))),
        onClick = onClick,
        modifier = modifier,
        art = {
            val h = size.height
            val w = size.width
            val colors = listOf(Color(0xFFE7546B), Color(0xFFF6B73C), Color(0xFF5BC17A), Color(0xFF8E6BD8))
            val count = 13

            for (i in 0 until count) {
                val x = w * (0.07f + i * 0.068f)
                val y = h * (0.76f + 0.1f * kotlin.math.sin(i * 0.8f))
                val r = h * (if (i == count - 1) 0.1f else 0.072f)

                drawCircle(colors[i % colors.size], r, Offset(x, y))

                if (i == count - 1) {
                    drawCircle(Color.White, r * 0.3f, Offset(x + r * 0.25f, y - r * 0.3f))
                    drawCircle(Color(0xFF2B2140), r * 0.15f, Offset(x + r * 0.3f, y - r * 0.3f))
                }
            }

            drawCircle(Color(0xFFE7546B), h * 0.07f, Offset(w * 0.95f, h * 0.55f))
        }
    ) {
        Text(
            text = "Fruity Snake",
            color = Color.White,
            fontSize = 26.sp,
            fontFamily = SnakeFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            style = TextStyle(shadow = TitleShadow)
        )
    }
}

@Composable
private fun BrickCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFF2B3FB8), Color(0xFF7A3BC4))),
        onClick = onClick,
        modifier = modifier,
        art = {
            val w = size.width
            val h = size.height
            val colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFF1E88E5), Color(0xFF2EAA4F))
            val bw = w / 6f

            for (row in 0 until 2) {
                for (col in 0 until 6) {
                    drawRoundRect(
                        color = colors[(row * 2 + col) % colors.size],
                        topLeft = Offset(col * bw + 2f, row * h * 0.13f + 4f),
                        size = Size(bw - 4f, h * 0.11f),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }
            }

            cardEmoji("🔥", w * 0.64f, h * 0.7f, h * 0.24f)

            drawRoundRect(
                color = Color(0xFFFFC72C),
                topLeft = Offset(w * 0.32f, h * 0.88f),
                size = Size(w * 0.36f, h * 0.07f),
                cornerRadius = CornerRadius(h * 0.035f, h * 0.035f)
            )
        }
    ) {
        Text(
            text = "BRICK POP",
            color = Color(0xFFFFC72C),
            fontSize = 22.sp,
            fontFamily = BrickFont,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(top = 4.dp)
        )
    }
}

@Composable
private fun FruitCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFFE9C792), Color(0xFFCB9C60))),
        onClick = onClick,
        modifier = modifier,
        art = {
            val w = size.width
            val h = size.height

            for (i in 0 until 12) {
                val y = h * (i + 0.5f) / 12f

                drawLine(
                    color = Color(0xFF8F5F32).copy(alpha = 0.18f),
                    start = Offset(0f, y),
                    end = Offset(w, y + kotlin.math.sin(i * 1.7f) * 4f),
                    strokeWidth = 2f
                )
            }

            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(w * 0.08f, h * 0.92f),
                end = Offset(w * 0.92f, h * 0.3f),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )

            drawLine(
                color = Color(0xFF6B4423).copy(alpha = 0.5f),
                start = Offset(w * 0.1f, h * 0.95f),
                end = Offset(w * 0.94f, h * 0.33f),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )

            cardEmoji("🍓", w * 0.2f, h * 0.7f, h * 0.34f)
            cardEmoji("🍑", w * 0.8f, h * 0.68f, h * 0.34f)
        }
    ) {
        Text(
            text = "Fruit Slice",
            color = Color(0xFF3B2415),
            fontSize = 25.sp,
            fontFamily = FruitFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
        )
    }
}

@Composable
private fun WaterCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFF8FE6FF), Color(0xFF3F9BFF))),
        onClick = onClick,
        modifier = modifier,
        art = {
            val w = size.width
            val h = size.height
            val tubes = listOf(
                listOf(Color(0xFFFF3B3B), Color(0xFFFFD21F), Color(0xFF3DDC4A)),
                listOf(Color(0xFF2D7BFF), Color(0xFFFF4FA3), Color(0xFF2D7BFF)),
                listOf(Color(0xFFB14CFF), Color(0xFFFF8A1F), Color(0xFFFF3B3B)),
                listOf(Color(0xFF3DDC4A), Color(0xFFFFD21F), Color(0xFFB14CFF))
            )
            val tw = w * 0.15f
            val gap = w * 0.055f
            val startX = (w - (4 * tw + 3 * gap)) / 2f
            val th = h * 0.52f
            val ty = h - th - 7.dp.toPx()
            val lh = th / 3.6f

            for ((i, layers) in tubes.withIndex()) {
                val tx = startX + i * (tw + gap)
                val shape = Path().apply {
                    addRoundRect(RoundRect(Rect(tx, ty, tx + tw, ty + th), CornerRadius(tw * 0.45f, tw * 0.45f)))
                }

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(tx, ty),
                    size = Size(tw, th),
                    cornerRadius = CornerRadius(tw * 0.45f, tw * 0.45f)
                )

                clipPath(shape) {
                    for ((k, color) in layers.withIndex()) {
                        drawRect(color, Offset(tx, ty + th - (k + 1) * lh - 2f), Size(tw, lh))
                    }

                    drawRect(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(tx + tw * 0.16f, ty + th * 0.08f),
                        size = Size(tw * 0.12f, th * 0.7f)
                    )
                }

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(tx, ty),
                    size = Size(tw, th),
                    cornerRadius = CornerRadius(tw * 0.45f, tw * 0.45f),
                    style = Stroke(width = 3f)
                )
            }
        }
    ) {
        Text(
            text = "Water Sort",
            color = Color.White,
            fontSize = 26.sp,
            fontFamily = WaterFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            style = TextStyle(shadow = TitleShadow)
        )
    }
}

@Composable
private fun WhackCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFF1C3A28), Color(0xFF3B6B3F))),
        onClick = onClick,
        modifier = modifier,
        art = {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val dirt = Color(0xFF8A6240)

            drawOval(Color(0xFF2E1D12), Offset(cx - w * 0.26f, h * 0.8f), Size(w * 0.52f, h * 0.18f))
            drawCircle(Color(0xFF7C5636), h * 0.2f, Offset(cx, h * 0.72f))
            drawOval(Color(0xFFE8C9A2), Offset(cx - h * 0.1f, h * 0.7f), Size(h * 0.2f, h * 0.13f))
            drawCircle(Color(0xFFE9788F), h * 0.035f, Offset(cx, h * 0.72f))
            drawCircle(Color(0xFF1E1410), h * 0.025f, Offset(cx - h * 0.08f, h * 0.65f))
            drawCircle(Color(0xFF1E1410), h * 0.025f, Offset(cx + h * 0.08f, h * 0.65f))
            drawArc(dirt, 0f, 180f, true, Offset(cx - w * 0.26f, h * 0.8f), Size(w * 0.52f, h * 0.18f))

            drawRect(Color(0xFF3B2A1A).copy(alpha = 0.7f), Offset(0f, 0f), Size(w * 0.05f, h))
            drawRect(Color(0xFF3B2A1A).copy(alpha = 0.7f), Offset(w * 0.95f, 0f), Size(w * 0.05f, h))

            for ((fx, fy) in listOf(0.2f to 0.35f, 0.78f to 0.5f, 0.35f to 0.55f)) {
                drawCircle(Color(0xFFFFF3A0).copy(alpha = 0.3f), 8f, Offset(w * fx, h * fy))
                drawCircle(Color(0xFFFFF3A0), 2.5f, Offset(w * fx, h * fy))
            }
        }
    ) {
        Text(
            text = "Whack-a-Mole",
            color = Color(0xFFF4EBD0),
            fontSize = 17.sp,
            fontFamily = WhackFont,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
        )
    }
}

@Composable
private fun FitCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFF1E2235), Color(0xFF2B2A45))),
        onClick = onClick,
        modifier = modifier,
        borderColor = Color(0xFF8CCBE0).copy(alpha = 0.5f),
        art = {
            val h = size.height
            val w = size.width
            val u = h * 0.15f

            fun piece(cells: List<Pair<Int, Int>>, ox: Float, oy: Float, color: Color) {
                for ((cx, cy) in cells) {
                    val tl = Offset(ox + cx * u, oy + cy * u)

                    drawRect(color.copy(alpha = 0.0f), Offset(tl.x - 3f, tl.y - 3f), Size(u + 6f, u + 6f))
                    drawRect(color.copy(alpha = 0.35f), tl, Size(u, u))
                    drawRect(color, tl, Size(u, u), style = Stroke(width = 2f))
                }
            }

            piece(listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1), w * 0.08f, h * 0.62f, Color(0xFF8CCBE0))
            piece(listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1), w * 0.42f, h * 0.62f, Color(0xFFE8837A))
            piece(listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), w * 0.78f, h * 0.62f, Color(0xFF8CC084))
        }
    ) {
        Text(
            text = "SHAPE FIT",
            color = Color(0xFF8CCBE0),
            fontSize = 21.sp,
            fontFamily = FitFont,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
        )
    }
}

@Composable
private fun FourCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFF2F6FF0), Color(0xFF123C94))),
        onClick = onClick,
        modifier = modifier,
        art = {
            val w = size.width
            val h = size.height
            val cols = 7
            val r = w / cols / 2f * 0.8f

            for (row in 0 until 2) {
                for (col in 0 until cols) {
                    val c = Offset(w * (col + 0.5f) / cols, h * (0.62f + row * 0.24f))

                    drawCircle(Color(0xFF0F2A70).copy(alpha = 0.7f), r, c)

                    val piece = when ((row * 3 + col) % 5) {
                        0, 3 -> "😈"
                        1, 4 -> "😇"
                        else -> ""
                    }

                    if (piece.isNotEmpty()) cardEmoji(piece, c.x, c.y, r * 1.7f)
                }
            }
        }
    ) {
        Text(
            text = "Connect 4",
            color = Color.White,
            fontSize = 25.sp,
            fontFamily = FourFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            style = TextStyle(shadow = TitleShadow)
        )
    }
}

@Composable
private fun BattleCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFFA9D4E8), Color(0xFF3F7FA6))),
        onClick = onClick,
        modifier = modifier,
        borderColor = Color(0xFF1F4261),
        art = {
            val w = size.width
            val h = size.height
            val sx = w * 0.5f
            val sy = h * 0.68f
            val hw = w * 0.26f
            val hh = h * 0.1f

            val hull = Path().apply {
                moveTo(sx - hw, sy - hh)
                lineTo(sx + hw * 1.2f, sy - hh)
                lineTo(sx + hw * 0.85f, sy + hh)
                lineTo(sx - hw * 0.85f, sy + hh)
                close()
            }

            drawPath(hull, Color(0xFF8FA3B5))
            drawLine(Color(0xFFE4572E), Offset(sx - hw * 0.8f, sy + hh * 0.5f), Offset(sx + hw * 0.8f, sy + hh * 0.5f), 4f, StrokeCap.Round)
            drawRoundRect(Color.White, Offset(sx - hw * 0.35f, sy - hh * 2.1f), Size(hw * 0.7f, hh * 1.1f), CornerRadius(5f, 5f))
            drawRect(Color(0xFFF08A24), Offset(sx - hw * 0.08f, sy - hh * 3.1f), Size(hw * 0.16f, hh * 1.0f))
            drawLine(Color(0xFF1F4261), Offset(sx + hw * 0.45f, sy - hh), Offset(sx + hw * 0.9f, sy - hh * 1.6f), 4f, StrokeCap.Round)

            for (k in 0 until 3) {
                val wy = h * (0.82f + 0.07f * k)
                val path = Path().apply {
                    moveTo(w * 0.05f, wy)

                    for (i in 1..24) {
                        lineTo(w * 0.05f + w * 0.9f * i / 24f, wy + kotlin.math.sin(i * 0.9f + k) * 3f)
                    }
                }

                drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            }
        }
    ) {
        Text(
            text = "BATTLESHIP",
            color = Color.White,
            fontSize = 19.sp,
            fontFamily = BattleFont,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            style = TextStyle(shadow = TitleShadow)
        )
    }
}

@Composable
private fun MemoryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(FeltLight, FeltDark)),
        onClick = onClick,
        modifier = modifier,
        borderColor = Gold.copy(alpha = 0.7f),
        art = {
            val w = size.width
            val h = size.height
            val cw = w * 0.2f
            val chh = h * 0.42f
            val y = h * 0.5f
            val angles = listOf(-9f, 0f, 9f)
            val xs = listOf(0.24f, 0.5f, 0.76f)

            for (i in 0 until 3) {
                val cx = w * xs[i]
                val cy = y + chh / 2f
                val tl = Offset(cx - cw / 2f, y)
                val corner = CornerRadius(8f, 8f)

                rotate(degrees = angles[i], pivot = Offset(cx, cy)) {
                    if (i == 1) {
                        drawRoundRect(Color(0xFFFFF8E8), tl, Size(cw, chh), corner)
                        drawRoundRect(Gold, tl, Size(cw, chh), corner, Stroke(3f))
                        cardEmoji("🍒", cx, cy, cw * 0.7f)
                    } else {
                        drawRoundRect(Color(0xFF7A1F3A), tl, Size(cw, chh), corner)
                        drawRoundRect(Gold, tl, Size(cw, chh), corner, Stroke(3f))

                        val diamond = Path().apply {
                            moveTo(cx, cy - chh * 0.28f)
                            lineTo(cx + cw * 0.26f, cy)
                            lineTo(cx, cy + chh * 0.28f)
                            lineTo(cx - cw * 0.26f, cy)
                            close()
                        }

                        drawPath(diamond, Gold.copy(alpha = 0.85f), style = Stroke(2.5f))
                    }
                }
            }
        }
    ) {
        Text(
            text = "Memory",
            color = Gold,
            fontSize = 26.sp,
            fontFamily = MemoryFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
        )
    }
}

@Composable
private fun HeroCard(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .height(104.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFF2E7E), Color(0xFFB5179E), Color(0xFF5A189A))
                )
            )
            .drawBehind {
                val c = Offset(size.width * 0.5f, size.height * 0.5f)

                for (k in 1..5) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.26f - k * 0.04f),
                        radius = size.height * 0.2f * k,
                        center = c,
                        style = Stroke(width = 3f)
                    )
                }
            }
            .border(
                3.dp,
                Brush.horizontalGradient(listOf(Color(0xFFFFD166), Color(0xFFFF6BCB), Color(0xFF9D4EDD))),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "💗", fontSize = 30.sp)

            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(shadow = TitleShadow)
            )
        }
    }
}

@Composable
private fun LockCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFF17181D), Color(0xFF34303A))),
        onClick = onClick,
        modifier = modifier,
        borderColor = Color(0xFFC9A24B),
        art = {
            val w = size.width
            val h = size.height
            val c = Offset(w * 0.5f, h * 0.68f)
            val r = h * 0.3f

            drawCircle(Color(0xFFC9A24B), r, c)
            drawCircle(Color(0xFF14151A), r * 0.8f, c)

            for (i in 0 until 36) {
                val a = i * 10f * Math.PI.toFloat() / 180f

                drawLine(
                    color = Color(0xFF7E5F24),
                    start = Offset(c.x + kotlin.math.cos(a) * r * 0.84f, c.y + kotlin.math.sin(a) * r * 0.84f),
                    end = Offset(c.x + kotlin.math.cos(a) * r * 0.97f, c.y + kotlin.math.sin(a) * r * 0.97f),
                    strokeWidth = 2f
                )
            }

            drawCircle(Color(0xFFE0A030).copy(alpha = 0.7f), r * 0.62f, c, style = Stroke(width = r * 0.1f))
            drawLine(
                color = Color(0xFF8D97A3),
                start = Offset(c.x - r * 0.15f, c.y + r * 0.1f),
                end = Offset(c.x + r * 0.55f, c.y - r * 0.3f),
                strokeWidth = r * 0.07f,
                cap = StrokeCap.Round
            )
            drawCircle(Color(0xFFC9A24B), r * 0.12f, c)
        }
    ) {
        Text(
            text = "LOCK PICK",
            color = Color(0xFFE8CB7E),
            fontSize = 18.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false,
            fontFamily = LockFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            style = TextStyle(shadow = TitleShadow)
        )
    }
}

@Composable
private fun DrawCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFFF4EFE0), Color(0xFFE6DFC8))),
        onClick = onClick,
        modifier = modifier,
        borderColor = Color(0xFF45474D),
        art = {
            val w = size.width
            val h = size.height

            var gx = 0f

            while (gx < w) {
                drawLine(Color(0xFF9DB7D4).copy(alpha = 0.4f), Offset(gx, 0f), Offset(gx, h), 1.2f)
                gx += 22f
            }

            var gy = 0f

            while (gy < h) {
                drawLine(Color(0xFF9DB7D4).copy(alpha = 0.4f), Offset(0f, gy), Offset(w, gy), 1.2f)
                gy += 22f
            }

            val path = Path().apply {
                moveTo(w * 0.2f, h * 0.45f)
                cubicTo(w * 0.4f, h * 0.45f, w * 0.45f, h * 0.85f, w * 0.64f, h * 0.85f)
            }

            drawPath(path, Color(0xFF2A3560), style = Stroke(width = 5f, cap = StrokeCap.Round))

            drawLine(Color(0xFF3E9A62), Offset(w * 0.64f, h * 0.9f), Offset(w * 0.88f, h * 0.9f), 6f, StrokeCap.Round)
            drawLine(Color(0xFF3E9A62), Offset(w * 0.64f, h * 0.76f), Offset(w * 0.64f, h * 0.9f), 6f, StrokeCap.Round)
            drawLine(Color(0xFF3E9A62), Offset(w * 0.88f, h * 0.76f), Offset(w * 0.88f, h * 0.9f), 6f, StrokeCap.Round)

            drawCircle(Color(0xFFDB6B3A), h * 0.1f, Offset(w * 0.2f, h * 0.4f))
            drawCircle(Color(0xFF8E3F1F), h * 0.1f, Offset(w * 0.2f, h * 0.4f), style = Stroke(width = 2.5f))
        }
    ) {
        Text(
            text = "Sketch Drop",
            color = Color(0xFF2A3560),
            fontSize = 20.sp,
            maxLines = 1,
            softWrap = false,
            fontFamily = DrawFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
        )
    }
}

@Composable
private fun TileCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFFFFE9A8), Color(0xFFFFB9A3))),
        onClick = onClick,
        modifier = modifier,
        borderColor = Color(0xFFD9480F),
        art = {
            val w = size.width
            val h = size.height
            val cell = h * 0.17f
            val gx = (w - cell * 4f) / 2f
            val gy = h * 0.4f
            val litTiles = setOf(1, 6, 11, 12)
            val tones = listOf(Color(0xFF14B8A6), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899))

            for (i in 0 until 16) {
                val x = gx + (i % 4) * cell
                val y = gy + (i / 4) * cell * 0.8f
                val on = i in litTiles

                drawRoundRect(
                    color = if (on) tones[i / 4] else Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(x + 2f, y + 2f),
                    size = Size(cell - 4f, cell * 0.8f - 4f),
                    cornerRadius = CornerRadius(5f, 5f)
                )
            }
        }
    ) {
        Text(
            text = "TILE ECHO",
            color = Color(0xFF3B2A4A),
            fontSize = 17.sp,
            letterSpacing = 3.sp,
            maxLines = 1,
            softWrap = false,
            fontFamily = TileFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 9.dp)
        )
    }
}

@Composable
private fun ChaseCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFF3B5BDB), Color(0xFF8E44C8))),
        onClick = onClick,
        modifier = modifier,
        borderColor = Color(0xFF1B1B4D),
        art = {
            val w = size.width
            val h = size.height
            val y = h * 0.72f
            val r = h * 0.14f

            drawRoundRect(Color(0xFFFFD23F), Offset(w * 0.08f, h * 0.46f), Size(w * 0.84f, h * 0.07f), CornerRadius(8f, 8f))
            drawRoundRect(Color(0xFFFFD23F), Offset(w * 0.08f, h * 0.9f), Size(w * 0.84f, h * 0.07f), CornerRadius(8f, 8f))

            for (i in 0 until 5) {
                drawCircle(Color(0xFFF1E6C8), h * 0.03f, Offset(w * (0.36f + i * 0.09f), y))
            }

            drawCircle(Color(0xFFFF6B35), r, Offset(w * 0.2f, y))
            drawCircle(Color.White, r * 0.28f, Offset(w * 0.2f + r * 0.35f, y - r * 0.35f))
            drawCircle(Color.White, r * 0.28f, Offset(w * 0.2f + r * 0.35f, y + r * 0.35f))

            val gx = w * 0.82f

            for (k in 0 until 8) {
                val a = k * Math.PI.toFloat() / 4f

                drawLine(
                    color = Color(0xFF2EC4B6),
                    start = Offset(gx + kotlin.math.cos(a) * r * 0.8f, y + kotlin.math.sin(a) * r * 0.8f),
                    end = Offset(gx + kotlin.math.cos(a) * r * 1.25f, y + kotlin.math.sin(a) * r * 1.25f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }

            drawCircle(Color(0xFF2EC4B6), r, Offset(gx, y))
            drawCircle(Color.White, r * 0.28f, Offset(gx - r * 0.35f, y - r * 0.3f))
            drawCircle(Color.White, r * 0.28f, Offset(gx + r * 0.1f, y - r * 0.3f))
        }
    ) {
        Text(
            text = "CHASE",
            color = Color.White,
            fontSize = 24.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false,
            fontFamily = ChaseFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            style = TextStyle(shadow = TitleShadow)
        )
    }
}

@Composable
private fun FlipCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFF7CEBDD), Color(0xFF58B4F5))),
        onClick = onClick,
        modifier = modifier,
        borderColor = Color(0xFF1C3D7A),
        art = {
            val w = size.width
            val h = size.height
            val bandH = h * 0.14f
            val navy = Color(0xFF1C3D7A)

            drawRect(navy, Offset(0f, h * 0.3f), Size(w, bandH))
            drawRect(navy, Offset(0f, h - bandH), Size(w, bandH))
            drawRect(Color(0xFFFFC145), Offset(0f, h * 0.3f + bandH - 4f), Size(w, 4f))
            drawRect(Color(0xFFFFC145), Offset(0f, h - bandH), Size(w, 4f))

            val spike = Path().apply {
                moveTo(w * 0.52f, h * 0.3f + bandH)
                lineTo(w * 0.58f, h * 0.3f + bandH + h * 0.16f)
                lineTo(w * 0.64f, h * 0.3f + bandH)
                close()
            }

            drawPath(spike, Color(0xFFE0489A))
            drawPath(spike, navy, style = Stroke(width = 2.5f))

            val up = Path().apply {
                moveTo(w * 0.74f, h - bandH)
                lineTo(w * 0.8f, h - bandH - h * 0.16f)
                lineTo(w * 0.86f, h - bandH)
                close()
            }

            drawPath(up, Color(0xFFE0489A))
            drawPath(up, navy, style = Stroke(width = 2.5f))

            val s = h * 0.2f

            drawRoundRect(Color(0xFFFF6B5E), Offset(w * 0.2f, h - bandH - s), Size(s, s), CornerRadius(s * 0.3f, s * 0.3f))
            drawRoundRect(navy, Offset(w * 0.2f, h - bandH - s), Size(s, s), CornerRadius(s * 0.3f, s * 0.3f), style = Stroke(width = 2.5f))
            drawCircle(Color.White, s * 0.14f, Offset(w * 0.2f + s * 0.35f, h - bandH - s * 0.62f))
            drawCircle(Color.White, s * 0.14f, Offset(w * 0.2f + s * 0.72f, h - bandH - s * 0.62f))
        }
    ) {
        Text(
            text = "GRAVITY FLIP",
            color = Color(0xFF0F2A5C),
            fontSize = 17.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false,
            fontFamily = FlipFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 9.dp)
        )
    }
}

@Composable
private fun PopCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFFFFF3B0), Color(0xFFA8F0C6))),
        onClick = onClick,
        modifier = modifier,
        borderColor = Color(0xFFE8590C),
        art = {
            val w = size.width
            val h = size.height
            val ink = Color(0xFF1F4D3A).copy(alpha = 0.55f)

            val bubbles = listOf(
                Triple(0.2f, 0.72f, 0.11f) to Color(0xFF5BC0EB),
                Triple(0.4f, 0.74f, 0.15f) to Color(0xFFFF7EB3),
                Triple(0.62f, 0.72f, 0.19f) to Color(0xFFFFA630),
                Triple(0.84f, 0.74f, 0.11f) to Color(0xFF5BC0EB)
            )

            for ((pos, color) in bubbles) {
                val c = Offset(w * pos.first, h * pos.second)
                val r = h * pos.third

                drawCircle(color, r, c)
                drawCircle(ink, r, c, style = Stroke(width = 2.5f))
                drawCircle(Color.White.copy(alpha = 0.6f), r * 0.22f, Offset(c.x - r * 0.38f, c.y - r * 0.42f))
            }

            drawCircle(Color(0xFFE8590C).copy(alpha = 0.7f), h * 0.3f, Offset(w * 0.62f, h * 0.72f), style = Stroke(width = 3f))
        }
    ) {
        Text(
            text = "CHAIN POP",
            color = Color(0xFF1F4D3A),
            fontSize = 20.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false,
            fontFamily = PopFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 9.dp)
        )
    }
}

@Composable
private fun DigCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFFFF9A76), Color(0xFFE85D75))),
        onClick = onClick,
        modifier = modifier,
        borderColor = Color(0xFF4A2231),
        art = {
            val w = size.width
            val h = size.height
            val cell = h * 0.19f
            val gx = (w - cell * 5f) / 2f
            val gy = h * 0.44f
            val holes = mapOf(0 to Color(0xFF6EC1E4), 3 to Color(0xFFFFD166), 7 to Color(0xFFFF9F43))

            for (i in 0 until 10) {
                val x = gx + (i % 5) * cell
                val y = gy + (i / 5) * cell * 1.05f
                val hole = holes[i]

                drawRoundRect(
                    color = hole ?: Color(0xFFF2D49B),
                    topLeft = Offset(x + 2f, y + 2f),
                    size = Size(cell - 4f, cell - 4f),
                    cornerRadius = CornerRadius(6f, 6f)
                )

                if (hole != null) {
                    drawRoundRect(Color(0xFF4A2231).copy(alpha = 0.6f), Offset(x + 2f, y + 2f), Size(cell - 4f, cell - 4f), CornerRadius(6f, 6f), style = Stroke(width = 2f))
                }

                if (i == 8) {
                    drawRoundRect(Color(0xFF8B5A2B), Offset(x + cell * 0.12f, y + cell * 0.3f), Size(cell * 0.76f, cell * 0.5f), CornerRadius(4f, 4f))
                    drawRect(Color(0xFFFFC233), Offset(x + cell * 0.12f, y + cell * 0.45f), Size(cell * 0.76f, cell * 0.1f))
                }
            }
        }
    ) {
        Text(
            text = "TREASURE DIG",
            color = Color.White,
            fontSize = 16.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false,
            fontFamily = DigFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 9.dp),
            style = TextStyle(shadow = TitleShadow)
        )
    }
}

@Composable
private fun ReelCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CardBase(
        brush = Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFFFC7A0))),
        onClick = onClick,
        modifier = modifier,
        borderColor = Color(0xFF14345E),
        art = {
            val w = size.width
            val h = size.height
            val waterTop = h * 0.5f

            drawRect(
                Brush.verticalGradient(listOf(Color(0xFF5AB0F0), Color(0xFF1F5FBF)), startY = waterTop, endY = h),
                Offset(0f, waterTop),
                Size(w, h - waterTop)
            )

            for (k in 0 until 2) {
                drawLine(Color.White.copy(alpha = 0.4f), Offset(w * 0.05f, waterTop + k * 8f), Offset(w * 0.95f, waterTop + k * 8f), 2.5f, StrokeCap.Round)
            }

            drawLine(Color(0xFF14345E), Offset(w * 0.2f, waterTop - 2f), Offset(w * 0.5f, h * 0.32f), 4f, StrokeCap.Round)
            drawLine(Color.White, Offset(w * 0.5f, h * 0.32f), Offset(w * 0.55f, h * 0.76f), 2.5f, StrokeCap.Round)

            val fx = w * 0.62f
            val fy = h * 0.78f
            val bw = w * 0.2f
            val bh = bw * 0.5f
            val tail = Path().apply {
                moveTo(fx + bw * 0.4f, fy)
                lineTo(fx + bw * 0.8f, fy - bh * 0.5f)
                lineTo(fx + bw * 0.8f, fy + bh * 0.5f)
                close()
            }

            drawPath(tail, Color(0xFFFF8A65))
            drawOval(Color(0xFFFF8A65), Offset(fx - bw / 2f, fy - bh / 2f), Size(bw, bh))
            drawOval(Color(0xFF14345E), Offset(fx - bw / 2f, fy - bh / 2f), Size(bw, bh), style = Stroke(width = 2.5f))
            drawCircle(Color.White, bh * 0.13f, Offset(fx - bw * 0.3f, fy - bh * 0.1f))
        }
    ) {
        Text(
            text = "REEL IN",
            color = Color(0xFF14345E),
            fontSize = 22.sp,
            letterSpacing = 2.sp,
            maxLines = 1,
            softWrap = false,
            fontFamily = ReelFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
        )
    }
}