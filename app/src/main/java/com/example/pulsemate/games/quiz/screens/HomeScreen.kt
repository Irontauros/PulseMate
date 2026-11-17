package com.example.pulsemate.games.quiz.screens

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
import com.example.pulsemate.games.quiz.HeatPrimaryButton
import com.example.pulsemate.games.quiz.rememberCyclingHeatLevel

@Composable
fun HomeScreen(
    onPlay: () -> Unit,
    onBack: () -> Unit
) {
    val level = rememberCyclingHeatLevel()

    HeatBackground(level = level) { heat ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "HAPTIC QUIZ",
                color = Color.White,
                fontSize = 44.sp,
                letterSpacing = 2.sp,
                fontFamily = QuizFont,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            HeatPrimaryButton(
                text = "CHOOSE CATEGORY",
                heat = heat,
                modifier = Modifier.fillMaxWidth(),
                onClick = onPlay
            )

            Spacer(Modifier.height(20.dp))

            HeatGhostButton(
                text = "BACK",
                modifier = Modifier.fillMaxWidth(0.5f),
                onClick = onBack
            )
        }
    }
}