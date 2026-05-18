package com.ssafy.backend.domain.report.dto;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;

public record ReportQuizSessionSearchCondition(
    String from,
    String to,
    Long categoryId,
    LearnDifficulty difficulty,
    QuizSessionStatus status,
    int page,
    int size) {}
