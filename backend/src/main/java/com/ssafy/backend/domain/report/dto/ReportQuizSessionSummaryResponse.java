package com.ssafy.backend.domain.report.dto;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;
import java.time.LocalDateTime;

public record ReportQuizSessionSummaryResponse(
    Long sessionId,
    Long categoryId,
    String categoryName,
    LearnDifficulty difficulty,
    Integer totalQuestionCount,
    Integer correctCount,
    Double accuracyRate,
    Double averageStar,
    QuizSessionStatus status,
    LocalDateTime startedAt,
    LocalDateTime endedAt) {}
