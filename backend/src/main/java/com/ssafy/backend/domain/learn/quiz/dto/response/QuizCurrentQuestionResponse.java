package com.ssafy.backend.domain.learn.quiz.dto.response;

public record QuizCurrentQuestionResponse(
    Long sessionId,
    Long questionId,
    Long wordId,
    Integer questionNumber,
    Integer totalQuestionCount,
    String imageUrl) {}
