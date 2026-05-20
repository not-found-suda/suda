package com.ssafy.mobile.feature.learning.data.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningQuizDtoTest {
    @Test
    fun answerResponseWithBlankRecognizedTextBecomesFailedZeroStar() {
        val result =
            LearningQuizAnswerResponseDto(
                targetText = "apple",
                recognizedText = "",
                isCorrect = false,
                star = 1,
            ).toDomain(
                fallbackSessionId = SESSION_ID,
                fallbackQuestionId = QUESTION_ID,
            )

        assertEquals("", result.recognizedText)
        assertFalse(result.isCorrect)
        assertEquals(0, result.star)
    }

    @Test
    fun answerResponseWithRecognizedTextKeepsReturnedScore() {
        val result =
            LearningQuizAnswerResponseDto(
                targetText = "apple",
                recognizedText = "apple",
                isCorrect = true,
                star = 2,
            ).toDomain(
                fallbackSessionId = SESSION_ID,
                fallbackQuestionId = QUESTION_ID,
            )

        assertEquals("apple", result.recognizedText)
        assertTrue(result.isCorrect)
        assertEquals(2, result.star)
    }

    @Test
    fun answerResponseWithClearlyUnrelatedRecognizedTextBecomesFailedZeroStar() {
        val result =
            LearningQuizAnswerResponseDto(
                targetText = "apple",
                recognizedText = "bus",
                isCorrect = false,
                star = 1,
            ).toDomain(
                fallbackSessionId = SESSION_ID,
                fallbackQuestionId = QUESTION_ID,
            )

        assertEquals("bus", result.recognizedText)
        assertFalse(result.isCorrect)
        assertEquals(0, result.star)
    }

    @Test
    fun resultResponseRecalculatesSummaryFromNormalizedAnswers() {
        val result =
            LearningQuizResultResponseDto(
                sessionId = SESSION_ID,
                totalQuestionCount = 3,
                correctCount = 3,
                totalStar = 5,
                answers =
                    listOf(
                        LearningQuizResultAnswerDto(
                            questionId = 1L,
                            wordId = 101L,
                            targetText = "apple",
                            recognizedText = "",
                            isCorrect = false,
                            star = 1,
                        ),
                        LearningQuizResultAnswerDto(
                            questionId = 2L,
                            wordId = 102L,
                            targetText = "banana",
                            recognizedText = "banana",
                            isCorrect = true,
                            star = 3,
                        ),
                        LearningQuizResultAnswerDto(
                            questionId = 3L,
                            wordId = 103L,
                            targetText = "apple",
                            recognizedText = "bus",
                            isCorrect = false,
                            star = 1,
                        ),
                    ),
            ).toDomain()

        assertEquals(1, result.correctCount)
        assertEquals(3, result.totalStar)
        assertEquals(0, result.answers.first().star)
        assertFalse(result.answers.first().isCorrect)
        assertEquals(0, result.answers.last().star)
    }
}

private const val SESSION_ID = 1L
private const val QUESTION_ID = 2L
