package com.ssafy.backend.domain.learn.quiz.dto.response;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import java.util.List;

public record QuizResultResponse(
    Long sessionId,
    Long categoryId,
    LearnDifficulty difficulty,
    Long childProfileId,
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
