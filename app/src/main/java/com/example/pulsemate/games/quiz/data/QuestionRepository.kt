
package com.example.pulsemate.games.quiz.data

import com.example.pulsemate.games.quiz.data.categories.animalsAndNatureQuestions
import com.example.pulsemate.games.quiz.data.categories.foodAndDrinkQuestions
import com.example.pulsemate.games.quiz.data.categories.geographyQuestions
import com.example.pulsemate.games.quiz.data.categories.historyQuestions
import com.example.pulsemate.games.quiz.data.categories.moviesAndTVQuestions
import com.example.pulsemate.games.quiz.data.categories.musicQuestions
import com.example.pulsemate.games.quiz.data.categories.scienceQuestions
import com.example.pulsemate.games.quiz.data.categories.sportsQuestions

object QuestionRepository {

    val allQuestions: List<Question>
        get() = listOf(
            scienceQuestions,
            moviesAndTVQuestions,
            musicQuestions,
            geographyQuestions,
            historyQuestions,
            sportsQuestions,
            foodAndDrinkQuestions,
            animalsAndNatureQuestions
        ).flatten()

    fun getQuestions(category: String?): List<Question> {
        val filteredQuestions = if (category == null) {
            allQuestions
        } else {
            allQuestions.filter { it.category == category }
        }

        return filteredQuestions.shuffled()
    }
}
