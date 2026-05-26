package com.ssafy.backend.domain.report.dto;

import com.ssafy.backend.domain.comms.entity.CommunicationAnalysisStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ReportCommunicationSessionSummaryResponse(
    Long sessionId,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    int utteranceCount,
    double averageSentenceLength,
    List<ReportWordCountResponse> frequentWords,
    ReportExpressionTypeCountsResponse expressionTypeCounts,
    String communicationLevel,
    String vocabularyDiversityLevel,
    String sentenceExpansionLevel,
    List<String> strengths,
    List<String> improvementPoints,
    List<String> parentGuide,
    List<String> recommendedActivities,
    String developmentReference,
    String cautionLevel,
    String consultationGuide,
    String summary,
    CommunicationAnalysisStatus analysisStatus,
    LocalDateTime analyzedAt) {}
