package com.ssafy.backend.domain.report.repository;

public record ReportSummaryAggregateRow(
    Long completedSessionCount, Long totalQuestionCount, Long totalCorrectCount, Long totalStar) {}
