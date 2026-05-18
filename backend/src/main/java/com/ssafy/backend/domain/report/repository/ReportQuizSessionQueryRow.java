package com.ssafy.backend.domain.report.repository;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;
import java.time.LocalDateTime;

public record ReportQuizSessionQueryRow(
    Long sessionId,
    Long childId,
    Long categoryId,
    String categoryName,
    LearnDifficulty difficulty,
    Integer totalQuestionCount,
    Integer correctCount,
    Integer totalStar,
    QuizSessionStatus status,
    LocalDateTime startedAt,
    LocalDateTime endedAt) {}
