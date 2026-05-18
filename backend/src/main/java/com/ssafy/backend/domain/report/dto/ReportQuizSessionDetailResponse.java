package com.ssafy.backend.domain.report.dto;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ReportQuizSessionDetailResponse(
    Long sessionId,
    Long childId,
    Long categoryId,
    String categoryName,
    LearnDifficulty difficulty,
    Integer totalQuestionCount,
    Integer correctCount,
    Double accuracyRate,
    Integer totalStar,
    Double averageStar,
    QuizSessionStatus status,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    List<ReportQuizAnswerResponse> answers) {}
