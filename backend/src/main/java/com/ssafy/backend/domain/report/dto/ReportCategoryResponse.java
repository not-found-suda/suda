package com.ssafy.backend.domain.report.dto;

import java.time.LocalDateTime;

public record ReportCategoryResponse(
    Long categoryId,
    String categoryName,
    Long totalWordCount,
    Long quizzedWordCount,
    Long correctWordCount,
    Double quizCoverageRate,
    Double correctWordRate,
    Long completedSessionCount,
    Long totalQuestionCount,
    Long correctCount,
    Double accuracyRate,
    Double averageStar,
    LocalDateTime latestSessionAt) {}
