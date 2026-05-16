package com.ssafy.backend.domain.report.repository;

import com.ssafy.backend.domain.comms.entity.CommunicationAnalysisStatus;
import java.time.LocalDateTime;

public record ReportCommunicationAnalysisQueryRow(
    Long sessionId,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    int messageCount,
    CommunicationAnalysisStatus analysisStatus,
    String summaryJson,
    LocalDateTime analyzedAt) {}
