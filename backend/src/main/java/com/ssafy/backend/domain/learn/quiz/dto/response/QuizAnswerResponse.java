package com.ssafy.backend.domain.learn.quiz.dto.response;

public record QuizAnswerResponse(
    Long sessionId,
    Long questionId,
    Long wordId,
    String targetText,
    String recognizedText,
    Boolean isCorrect,
    Integer star,
    String feedback,
    Boolean hasNext,
    Integer nextQuestionNumber) {}
