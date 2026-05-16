package com.ssafy.backend.domain.report.dto;

import com.ssafy.backend.domain.comms.entity.CommunicationAnalysisStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ReportCommunicationSessionSummaryResponse(
    Long sessionId,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    int utteranceCount,
    List<ReportWordCountResponse> frequentWords,
    String summary,
    CommunicationAnalysisStatus analysisStatus,
    LocalDateTime analyzedAt) {}
