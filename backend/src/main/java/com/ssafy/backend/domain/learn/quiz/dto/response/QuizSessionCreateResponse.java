package com.ssafy.backend.domain.learn.quiz.dto.response;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;
import java.util.List;

public record QuizSessionCreateResponse(
    Long sessionId,
    Long categoryId,
    LearnDifficulty difficulty,
    Integer totalQuestionCount,
    Integer currentQuestionNumber,
    QuizSessionStatus status,
    List<QuestionItem> questions) {

  public record QuestionItem(
      Long questionId, Long wordId, Integer questionNumber, String targetText, String imageUrl) {}
}
