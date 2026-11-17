package com.example.pulsemate.games.quiz

import android.graphics.Typeface
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val QuizFont: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

val QuizBodyFont: FontFamily = FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))

val HeatNavy = Color(0xFF0A0B1F)
val HeatHeartRed = Color(0xFFFF4D6D)
val HeatCorrect = Color(0xFF22C55E)
val HeatWrong = Color(0xFFEF4444)
val HeatDanger = Color(0xFFFF5252)
val HeatWarning = Color(0xFFFFC857)

val HeatColors = listOf(
    Color(0xFF3B82F6),
    Color(0xFF06B6D4),
    Color(0xFF10B981),
    Color(0xFF84CC16),
    Color(0xFFFACC15),
    Color(0xFFFB923C),
    Color(0xFFEF4444),
    Color(0xFFF43F5E),
    Color(0xFFC026D3)
)

fun heatColor(level: Int): Color {
    return HeatColors[(level - 1).coerceIn(0, HeatColors.lastIndex)]
}

@Composable
fun rememberCyclingHeatLevel(): Int {
    var level by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            level = level % 9 + 1
        }
    }

    return level
}

@Composable
fun HeatBackground(
    level: Int,
    content: @Composable BoxScope.(heat: Color) -> Unit
) {
    val heat by animateColorAsState(
        targetValue = heatColor(level),
        animationSpec = tween(durationMillis = 700),
        label = "heat"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HeatNavy, lerp(HeatNavy, heat, 0.38f))))
            .drawBehind {
                drawCircle(
                    color = heat.copy(alpha = 0.28f),
                    radius = size.minDimension * 0.9f,
                    center = Offset(size.width * 0.5f, size.height * 1.05f)
                )
            }
            .safeDrawingPadding()
    ) {
        content(heat)
    }
}

@Composable
fun HeatPanel(
    heat: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.32f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(2.dp, heat.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
fun HeatPrimaryButton(
    text: String,
    heat: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = heat,
            contentColor = if (heat.luminance() > 0.3f) HeatNavy else Color.White
        )
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontFamily = QuizFont,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun HeatGhostButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White.copy(alpha = 0.8f)
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontFamily = QuizFont,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
    }
}