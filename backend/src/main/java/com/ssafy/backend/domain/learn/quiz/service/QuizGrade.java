package com.ssafy.backend.domain.learn.quiz.service;

public record QuizGrade(
    boolean isCorrect, int star, String feedback, String reason, double confidence) {}
