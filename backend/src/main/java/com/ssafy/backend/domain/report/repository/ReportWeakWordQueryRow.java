package com.ssafy.backend.domain.report.repository;

import java.time.LocalDateTime;

public record ReportWeakWordQueryRow(
    Long wordId,
    String word,
    String displayText,
    Long categoryId,
    String categoryName,
    Long attemptCount,
    Long wrongCount,
    Double averageStar,
    LocalDateTime lastAnsweredAt) {}
