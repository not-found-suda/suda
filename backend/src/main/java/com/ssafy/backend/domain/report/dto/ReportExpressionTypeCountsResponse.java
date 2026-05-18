package com.ssafy.backend.domain.report.dto;

public record ReportExpressionTypeCountsResponse(
    int request, int emotion, int response, int play, int other) {}
