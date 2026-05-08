package com.ssafy.backend.domain.learn.quiz.dto.request;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QuizSessionCreateRequest(
    @NotNull Long childProfileId,
    @NotNull Long categoryId,
    @NotNull LearnDifficulty difficulty,
    @NotNull @Positive Integer totalQuestionCount) {}
