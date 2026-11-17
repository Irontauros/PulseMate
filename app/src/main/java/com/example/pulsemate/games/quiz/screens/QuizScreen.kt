package com.example.pulsemate.games.quiz.screens

import com.example.pulsemate.games.quiz.QuizBodyFont
import com.example.pulsemate.games.quiz.QuizFont
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pulsemate.games.quiz.HeatBackground
import com.example.pulsemate.games.quiz.HeatCorrect
import com.example.pulsemate.games.quiz.HeatDanger
import com.example.pulsemate.games.quiz.HeatHeartRed
import com.example.pulsemate.games.quiz.HeatNavy
import com.example.pulsemate.games.quiz.HeatWarning
import com.example.pulsemate.games.quiz.HeatWrong
import com.example.pulsemate.games.quiz.QuizHaptics
import com.example.pulsemate.games.quiz.data.Question
import com.example.pulsemate.games.quiz.data.QuestionRepository
import com.example.pulsemate.games.quiz.heatColor
import com.example.pulsemate.haptics.LoveSpouseController
import kotlinx.coroutines.delay

private const val MAX_STRIKES = 3

@Composable
fun QuizScreen(
    category: String?,
    freePlay: Boolean,
    onBack: () -> Unit,
    onFinished: (score: Int, totalQuestions: Int) -> Unit
) {
    val context = LocalContext.current

    val questions = remember(category) {
        QuestionRepository.getQuestions(category)
    }

    val loveSpouse = remember {
        LoveSpouseController(context)
    }

    val hapticGame = remember {
        QuizHaptics(loveSpouse)
    }

    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var wrongAnswers by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableIntStateOf(-1) }
    var answered by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableIntStateOf(10) }
    var hapticLevel by remember { mutableIntStateOf(0) }
    var correctStreak by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    // Each question can only be processed once, whether answered or timed out.
    var handledQuestionIndex by remember { mutableIntStateOf(-1) }

    var paused by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    val questionCount = questions.size
    val lap = if (questionCount > 0) currentQuestionIndex / questionCount else 0

    // The first pass keeps the question bank order, later passes reshuffle it.
    val lapQuestions = remember(questions, lap) {
        if (lap == 0) questions else questions.shuffled()
    }

    val currentQuestion: Question? = if (questionCount > 0) {
        lapQuestions[currentQuestionIndex % questionCount]
    } else {
        null
    }

    val timerFraction by animateFloatAsState(
        targetValue = timeLeft / 10f,
        animationSpec = tween(
            durationMillis = if (timeLeft == 10) 0 else 1000,
            easing = LinearEasing
        ),
        label = "timer"
    )

    LaunchedEffect(hapticGame) {
        hapticGame.reset()
    }

    DisposableEffect(hapticGame) {
        onDispose {
            hapticGame.reset()
        }
    }

    DisposableEffect(lifecycleOwner, loveSpouse) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    paused = true
                    loveSpouse.stop()
                }

                Lifecycle.Event.ON_RESUME -> {
                    paused = false
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var deviceProblem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loveSpouse) {
        while (true) {
            deviceProblem = loveSpouse.deviceProblem()
            delay(1_000)
        }
    }

    fun finishQuestion() {
        if (finished) return

        finished = true
        loveSpouse.stop()
        onFinished(score, currentQuestionIndex + 1)
    }

    fun processAnswer(answerIndex: Int?) {
        if (finished || answered || currentQuestionIndex == handledQuestionIndex) {
            return
        }

        val question = currentQuestion ?: return

        handledQuestionIndex = currentQuestionIndex
        answered = true

        if (answerIndex == null) {
            wrongAnswers++
            hapticGame.timeout()
        } else if (answerIndex == question.correctAnswer) {
            score++
            hapticGame.correct()
        } else {
            wrongAnswers++
            hapticGame.wrong()
        }

        hapticLevel = hapticGame.getRawLevel()
        correctStreak = hapticGame.getCorrectStreak()
    }

    LaunchedEffect(currentQuestionIndex, answered, finished) {
        if (finished || answered || questions.isEmpty()) return@LaunchedEffect

        timeLeft = 10

        while (timeLeft > 0 && !answered && !finished) {
            delay(1_000)
            if (!answered && !finished && !paused) {
                timeLeft--
            }
        }

        if (timeLeft <= 0 && !answered && !finished) {
            processAnswer(null)
        }
    }

    LaunchedEffect(answered, finished) {
        if (!answered || finished) return@LaunchedEffect

        delay(800)

        if (!freePlay && wrongAnswers >= MAX_STRIKES) {
            finishQuestion()
        } else {
            currentQuestionIndex++
            selectedAnswer = -1
            answered = false
            timeLeft = 10
        }
    }

    if (questions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HeatNavy)
                .safeDrawingPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "NO QUESTIONS FOUND",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = QuizFont,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Check that this category has questions in your question bank.",
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                Button(onClick = onBack) {
                    Text("BACK")
                }
            }
        }
        return
    }

    val question: Question = currentQuestion ?: return

    HeatBackground(level = hapticLevel) { heat ->
        val timerColor = if (timeLeft <= 3) HeatDanger else heat

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (freePlay) {
                        Text(
                            text = "🔥 $correctStreak",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontFamily = QuizFont,
                            fontWeight = FontWeight.Black
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(MAX_STRIKES) { index ->
                                val lost = index < wrongAnswers

                                Text(
                                    text = if (lost) "♡" else "♥",
                                    color = if (lost) Color.White.copy(alpha = 0.3f) else HeatHeartRed,
                                    fontSize = 30.sp
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "SCORE",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            fontFamily = QuizFont,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        Text(
                            text = "$score",
                            color = Color.White,
                            fontSize = 34.sp,
                            fontFamily = QuizFont,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(timerFraction.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(timerColor, RoundedCornerShape(6.dp))
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = "$timeLeft",
                        color = timerColor,
                        fontSize = 24.sp,
                        fontFamily = QuizFont,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.width(34.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(Modifier.height(6.dp))

                LevelBar(level = hapticLevel)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 14.dp)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.32f),
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(2.dp, heat.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = question.category.uppercase(),
                                    color = heat,
                                    fontSize = 12.sp,
                                    fontFamily = QuizFont,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                )

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    text = question.question,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontFamily = QuizBodyFont,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 29.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            question.answers.forEachIndexed { index, answer ->
                                val isCorrect = answered && index == question.correctAnswer
                                val isWrong = answered && index == selectedAnswer && !isCorrect

                                Surface(
                                    onClick = {
                                        if (!answered && !finished) {
                                            selectedAnswer = index
                                            processAnswer(index)
                                        }
                                    },
                                    enabled = !answered && !finished,
                                    color = when {
                                        isCorrect -> HeatCorrect
                                        isWrong -> HeatWrong
                                        else -> Color.White.copy(alpha = 0.10f)
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (isCorrect || isWrong) {
                                            Color.White.copy(alpha = 0.8f)
                                        } else {
                                            Color.White.copy(alpha = 0.18f)
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 12.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(
                                                    if (isCorrect || isWrong) Color.White else heat,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${'A' + index}",
                                                color = HeatNavy,
                                                fontSize = 16.sp,
                                                fontFamily = QuizFont,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Text(
                                            text = answer,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontFamily = QuizBodyFont,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                deviceProblem?.let { problem ->
                    Text(
                        text = problem,
                        color = HeatWarning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                Button(
                    onClick = {
                        loveSpouse.stop()
                        onBack()
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(0.5f)
                        .height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.10f),
                        contentColor = Color.White.copy(alpha = 0.8f)
                    )
                ) {
                    Text(
                        text = "QUIT",
                        fontFamily = QuizFont,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelBar(level: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(7.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (segment in 1..9) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            color = if (segment <= level) heatColor(segment) else Color.White.copy(alpha = 0.09f),
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = "$level",
            color = if (level > 0) heatColor(level) else Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontFamily = QuizFont,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End
        )
    }
}