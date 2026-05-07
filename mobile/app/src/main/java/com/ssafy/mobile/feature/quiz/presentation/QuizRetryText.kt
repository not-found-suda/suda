package com.ssafy.mobile.feature.quiz.presentation

internal fun quizNextButtonText(
    canSkipQuestion: Boolean,
    hasAnswered: Boolean,
    hasSuccessfulAnswer: Boolean,
    retryLimitReached: Boolean,
    isLastQuestion: Boolean,
): String =
    when {
        canSkipQuestion -> "이번 문제 넘어가기"
        retryLimitReached -> if (isLastQuestion) "결과 보기" else "다음 문제"
        hasAnswered && !hasSuccessfulAnswer -> "별 3개에 도전해요"
        isLastQuestion -> "결과 보기"
        else -> "다음 문제"
    }

internal fun quizRetryButtonText(remainingRetryCount: Int): String =
    if (remainingRetryCount > 0) {
        "다시 말하기(${remainingRetryCount}번 남음)"
    } else {
        "다시 말하기"
    }
