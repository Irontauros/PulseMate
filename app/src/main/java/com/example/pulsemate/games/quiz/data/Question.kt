
package com.example.pulsemate.games.quiz.data

data class Question(
    val category: String,
    val question: String,
    val answers: List<String>,
    val correctAnswer: Int
) {
    init {
        require(answers.size >= 2) {
            "A question must have at least two answers."
        }
        require(correctAnswer in answers.indices) {
            "correctAnswer must be a valid index in answers."
        }
    }
}
