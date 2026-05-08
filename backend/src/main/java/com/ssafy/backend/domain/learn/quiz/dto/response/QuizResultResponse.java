package com.ssafy.backend.domain.learn.quiz.dto.response;

import java.util.List;

public record QuizResultResponse(
    Long sessionId,
    Integer totalQuestionCount,
    Integer correctCount,
    Integer totalStar,
    List<AnswerItem> answers) {

  public record AnswerItem(
      Long questionId,
      Long wordId,
      String targetText,
      String recognizedText,
      Boolean isCorrect,
      Integer star,
      String feedback) {}
}
