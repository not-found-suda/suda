package com.ssafy.backend.domain.report.repository;

import java.time.LocalDateTime;

public record ReportQuizAnswerQueryRow(
    Long questionId,
    Integer questionNumber,
    Long wordId,
    String targetText,
    String recognizedText,
    Boolean isCorrect,
    Integer star,
    String feedback,
    LocalDateTime answeredAt) {}
