package com.ssafy.backend.domain.report.dto;

import java.util.List;

public record ReportWeakWordListResponse(
    List<ReportWeakWordResponse> content, int page, int size, long totalElements, int totalPages) {}
