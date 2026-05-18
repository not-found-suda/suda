package com.ssafy.backend.domain.report.dto;

import com.ssafy.backend.domain.comms.entity.CommunicationAnalysisStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ReportCommunicationSummaryResponse(
    Long childId,
    CommunicationAnalysisStatus analysisStatus,
    long totalSessionCount,
    long totalUtteranceCount,
    double averageSentenceLength,
    List<ReportWordCountResponse> frequentWords,
    ReportExpressionTypeCountsResponse expressionTypeCounts,
    List<ReportCommunicationSessionSummaryResponse> recentSessions,
    LocalDateTime generatedAt) {}
