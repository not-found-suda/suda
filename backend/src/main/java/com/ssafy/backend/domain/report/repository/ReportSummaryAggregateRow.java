package com.ssafy.backend.domain.report.repository;

public record ReportSummaryAggregateRow(
    Long totalSessionCount,
    Long completedSessionCount,
    Long totalQuestionCount,
    Long totalCorrectCount,
    Long totalStar) {}
