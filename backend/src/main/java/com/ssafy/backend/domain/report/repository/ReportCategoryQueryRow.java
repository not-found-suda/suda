package com.ssafy.backend.domain.report.repository;

import java.time.LocalDateTime;

public record ReportCategoryQueryRow(
    Long categoryId,
    String categoryName,
    Long totalWordCount,
    Long quizzedWordCount,
    Long correctWordCount,
    Long completedSessionCount,
    Long totalQuestionCount,
    Long correctCount,
    Long totalStar,
    LocalDateTime latestSessionAt) {}
