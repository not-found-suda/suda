package com.ssafy.backend.domain.report.dto;

import java.util.List;

public record ReportQuizSessionListResponse(
    List<ReportQuizSessionSummaryResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {}
