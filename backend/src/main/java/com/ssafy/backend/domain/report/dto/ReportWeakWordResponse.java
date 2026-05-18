package com.ssafy.backend.domain.report.dto;

import java.time.LocalDateTime;

public record ReportWeakWordResponse(
    Long wordId,
    String word,
    String displayText,
    Long categoryId,
    String categoryName,
    Long attemptCount,
    Long wrongCount,
    Double accuracyRate,
    Double averageStar,
    LocalDateTime lastAnsweredAt) {}
