package com.ssafy.mobile.feature.quiz.domain.model

object MockQuizQuestions {
    const val QUESTION_COUNT = 5

    val items: List<QuizQuestion> =
        listOf(
            QuizQuestion(id = 1L, wordId = 101L, categoryId = 1L, word = "밥"),
            QuizQuestion(id = 2L, wordId = 102L, categoryId = 1L, word = "물"),
            QuizQuestion(id = 3L, wordId = 103L, categoryId = 1L, word = "엄마"),
            QuizQuestion(id = 4L, wordId = 104L, categoryId = 1L, word = "아빠"),
            QuizQuestion(id = 5L, wordId = 105L, categoryId = 1L, word = "고마워"),
        )
}
