package com.ssafy.backend.domain.report.repository;

import java.time.LocalDateTime;

public record ReportLatestCategoryQueryRow(
    Long categoryId, String categoryName, LocalDateTime latestSessionAt) {}
