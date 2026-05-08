package com.ssafy.backend.domain.learn.quiz.dto.response;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;

public record QuizSessionCreateResponse(
    Long sessionId,
    Long categoryId,
    LearnDifficulty difficulty,
    Integer totalQuestionCount,
    Integer currentQuestionNumber,
    QuizSessionStatus status) {}
