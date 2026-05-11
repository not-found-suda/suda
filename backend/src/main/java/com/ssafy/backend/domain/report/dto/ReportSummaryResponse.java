package com.ssafy.backend.domain.report.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReportSummaryResponse(
    Long childId,
    Long totalSessionCount,
    Long completedSessionCount,
    Long totalQuestionCount,
    Long totalCorrectCount,
    Double accuracyRate,
    Double averageStar,
    LocalDateTime latestSessionAt,
    ReportLatestCategoryResponse latestCategory,
    List<ReportWeakWordResponse> weakWords) {}
