package com.ssafy.backend.domain.report.dto;

import java.time.LocalDateTime;

public record ReportQuizAnswerResponse(
    Long questionId,
    Integer questionNumber,
    Long wordId,
    String targetText,
    String recognizedText,
    Boolean isCorrect,
    Integer star,
    String feedback,
    LocalDateTime answeredAt) {}
