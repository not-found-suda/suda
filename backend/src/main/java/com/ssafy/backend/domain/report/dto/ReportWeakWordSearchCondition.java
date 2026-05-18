package com.ssafy.backend.domain.report.dto;

public record ReportWeakWordSearchCondition(
    String from, String to, Long categoryId, Integer minAttemptCount, int page, int size) {}
