package com.example.pulsemate.games.quiz.screens

import com.example.pulsemate.games.quiz.QuizBodyFont
import com.example.pulsemate.games.quiz.QuizFont
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulsemate.games.quiz.HeatBackground
import com.example.pulsemate.games.quiz.HeatGhostButton
import com.example.pulsemate.games.quiz.HeatNavy
import com.example.pulsemate.games.quiz.HeatPrimaryButton
import com.example.pulsemate.games.quiz.data.quizCategories
import com.example.pulsemate.games.quiz.rememberCyclingHeatLevel

@Composable
fun CategoryScreen(
    freePlay: Boolean,
    onFreePlayChange: (Boolean) -> Unit,
    onAllCategories: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val level = rememberCyclingHeatLevel()

    HeatBackground(level = level) { heat ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "CHOOSE CATEGORY",
                        color = Color.White,
                        fontSize = 30.sp,
                        letterSpacing = 1.sp,
                        fontFamily = QuizFont,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 18.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModeChip(
                            title = "CHALLENGE",
                            subtitle = "3 strikes and you're out",
                            selected = !freePlay,
                            heat = heat,
                            onClick = { onFreePlayChange(false) },
                            modifier = Modifier.weight(1f)
                        )

                        ModeChip(
                            title = "FREE PLAY",
                            subtitle = "No losses, just play",
                            selected = freePlay,
                            heat = heat,
                            onClick = { onFreePlayChange(true) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(quizCategories) { category ->
                            Surface(
                                onClick = { onCategorySelected(category.name) },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.5.dp, heat.copy(alpha = 0.55f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 22.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = category.name,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontFamily = QuizFont,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HeatPrimaryButton(
                text = "ALL CATEGORIES",
                heat = heat,
                modifier = Modifier.fillMaxWidth(),
                onClick = onAllCategories
            )

            HeatGhostButton(
                text = "BACK",
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .padding(top = 8.dp),
                onClick = onBack
            )
        }
    }
}

@Composable
private fun ModeChip(
    title: String,
    subtitle: String,
    selected: Boolean,
    heat: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onHeat = if (heat.luminance() > 0.3f) HeatNavy else Color.White

    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) heat else Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.5.dp,
            if (selected) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = if (selected) onHeat else Color.White,
                fontSize = 14.sp,
                fontFamily = QuizFont,
                fontWeight = FontWeight.Black
            )

            Text(
                text = subtitle,
                color = if (selected) onHeat.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontFamily = QuizBodyFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}