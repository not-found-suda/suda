package com.ssafy.backend.domain.learn.quiz.dto.response;

import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;
import java.time.LocalDateTime;

public record QuizSessionEndResponse(
    Long sessionId, QuizSessionStatus status, LocalDateTime endedAt) {}
