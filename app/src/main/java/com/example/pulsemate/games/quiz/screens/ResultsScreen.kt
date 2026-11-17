package com.example.pulsemate.games.quiz.screens

import com.example.pulsemate.games.quiz.QuizBodyFont
import com.example.pulsemate.games.quiz.QuizFont
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulsemate.games.quiz.HeatBackground
import com.example.pulsemate.games.quiz.HeatGhostButton
import com.example.pulsemate.games.quiz.HeatPanel
import com.example.pulsemate.games.quiz.HeatPrimaryButton

@Composable
fun ResultsScreen(
    score: Int,
    totalQuestions: Int,
    onPlayAgain: () -> Unit,
    onCategories: () -> Unit,
    onHome: () -> Unit
) {
    val percentage = if (totalQuestions > 0) {
        score * 100 / totalQuestions
    } else {
        0
    }

    val title = when {
        totalQuestions > 0 && score == totalQuestions -> "PERFECT SCORE"
        percentage >= 80 -> "EXCELLENT"
        percentage >= 60 -> "GREAT JOB"
        percentage >= 40 -> "NOT BAD"
        else -> "KEEP GOING"
    }

    val emoji = when {
        percentage >= 100 -> "🏆"
        percentage >= 80 -> "🔥"
        percentage >= 60 -> "👏"
        percentage >= 40 -> "😎"
        else -> "💪"
    }

    val message = when {
        percentage >= 100 -> "Flawless. You got every question right!"
        percentage >= 80 -> "Brilliant work. You really know your stuff."
        percentage >= 60 -> "Solid performance. Keep building that knowledge."
        percentage >= 40 -> "You're getting there. A rematch might help."
        else -> "Every round is a chance to improve. Try again!"
    }

    val heatLevel = ((percentage * 9 + 99) / 100).coerceIn(1, 9)

    HeatBackground(level = heatLevel) { heat ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                fontSize = 64.sp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                color = heat,
                fontSize = 34.sp,
                fontFamily = QuizFont,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = message,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                fontFamily = QuizBodyFont,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            HeatPanel(
                heat = heat,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$score / $totalQuestions",
                    color = Color.White,
                    fontSize = 46.sp,
                    fontFamily = QuizFont,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "$percentage% correct",
                    color = heat,
                    fontSize = 16.sp,
                    fontFamily = QuizFont,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(24.dp))

            HeatPrimaryButton(
                text = "PLAY AGAIN",
                heat = heat,
                modifier = Modifier.fillMaxWidth(),
                onClick = onPlayAgain
            )

            HeatGhostButton(
                text = "CHOOSE CATEGORY",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                onClick = onCategories
            )

            HeatGhostButton(
                text = "PULSEMATE HOME",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                onClick = onHome
            )
        }
    }
}