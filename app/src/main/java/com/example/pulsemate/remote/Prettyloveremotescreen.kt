package com.example.pulsemate.remote

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulsemate.haptics.PrettyLoveLink
import kotlinx.coroutines.delay

private val WineTop = Color(0xFF1F1330)
private val WineBottom = Color(0xFF4A1D3D)
private val Blush = Color(0xFFF3C9D3)
private val Rose = Color(0xFFD9809A)
private val Champagne = Color(0xFFF6DCC6)
private val DeepPlum = Color(0xFF2A1233)
private val StopWine = Color(0xFF7A2A45)
private val WarningAmber = Color(0xFFFFC857)

private val ElegantFont = FontFamily.Serif

@Composable
fun PrettyLoveRemoteScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var selectedButton by remember {
        mutableIntStateOf(PrettyLoveLink.activePattern)
    }

    var deviceProblem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        PrettyLoveLink.init(context)

        while (true) {
            val problem = PrettyLoveLink.problem()

            deviceProblem = when {
                PrettyLoveLink.isConnected() -> null
                problem != null -> problem
                else -> "Looking for your Prettylove toy…"
            }

            selectedButton = PrettyLoveLink.activePattern
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(WineTop, WineBottom)))
            .drawBehind {
                drawCircle(
                    color = Rose.copy(alpha = 0.10f),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.85f, size.height * 0.95f)
                )
            }
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "PRETTYLOVE",
                color = Champagne,
                fontSize = 26.sp,
                fontFamily = ElegantFont,
                fontWeight = FontWeight.Normal,
                letterSpacing = 7.sp
            )

            Spacer(Modifier.height(40.dp))

            for (row in 0..3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    for (column in 1..3) {
                        val number = row * 3 + column
                        val selected = selectedButton == number

                        Button(
                            onClick = {
                                selectedButton = number
                                PrettyLoveLink.pattern(number)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(78.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Blush else Rose.copy(alpha = 0.22f),
                                contentColor = if (selected) DeepPlum else Blush
                            )
                        ) {
                            Text(
                                text = number.toString(),
                                fontSize = 28.sp,
                                fontFamily = ElegantFont,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (row < 3) {
                    Spacer(Modifier.height(14.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    PrettyLoveLink.stopNow()
                    selectedButton = 0
                },
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StopWine.copy(alpha = 0.7f),
                    contentColor = Champagne.copy(alpha = 0.85f)
                )
            ) {
                Text(
                    text = "STOP",
                    fontSize = 14.sp,
                    fontFamily = ElegantFont,
                    letterSpacing = 3.sp
                )
            }

            deviceProblem?.let { problem ->
                Spacer(Modifier.height(18.dp))

                Text(
                    text = problem,
                    color = WarningAmber,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}