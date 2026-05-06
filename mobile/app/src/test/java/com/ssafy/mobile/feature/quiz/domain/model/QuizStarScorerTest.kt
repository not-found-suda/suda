package com.ssafy.mobile.feature.quiz.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizStarScorerTest {
    @Test
    fun scoreReturnsThreeStarsWhenTextMatchesTargetWord() {
        val score = QuizStarScorer.score(targetWord = "밥", sttText = "밥")

        assertEquals(THREE_STARS, score)
    }

    @Test
    fun scoreReturnsThreeStarsWhenRecognizedSentenceContainsTargetWord() {
        val score = QuizStarScorer.score(targetWord = "밥", sttText = "밥 먹고 싶어요")

        assertEquals(THREE_STARS, score)
    }

    @Test
    fun scoreDoesNotReturnThreeStarsWhenSingleCharacterTargetIsOnlyInsideAnotherWord() {
        val score = QuizStarScorer.score(targetWord = "물", sttText = "선물")

        assertEquals(ONE_STAR, score)
    }

    @Test
    fun scoreReturnsTwoStarsWhenTextIsSimilarToTargetWord() {
        val score = QuizStarScorer.score(targetWord = "배부르다", sttText = "배부르")

        assertEquals(TWO_STARS, score)
    }

    @Test
    fun scoreReturnsOneStarWhenTextIsDifferentFromTargetWord() {
        val score = QuizStarScorer.score(targetWord = "밥", sttText = "물")

        assertEquals(ONE_STAR, score)
    }

    @Test
    fun scoreReturnsOneStarWhenTextIsBlank() {
        val score = QuizStarScorer.score(targetWord = "밥", sttText = "")

        assertEquals(ONE_STAR, score)
    }

    private companion object {
        const val ONE_STAR = 1
        const val TWO_STARS = 2
        const val THREE_STARS = 3
    }
}
