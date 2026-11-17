
package com.example.pulsemate.games.quiz.data

data class QuizCategory(
    val name: String
)

val quizCategories = listOf(
    QuizCategory("Science"),
    QuizCategory("Movies & TV"),
    QuizCategory("Music"),
    QuizCategory("Geography"),
    QuizCategory("History"),
    QuizCategory("Sports"),
    QuizCategory("Food & Drink"),
    QuizCategory("Animals & Nature")
)
