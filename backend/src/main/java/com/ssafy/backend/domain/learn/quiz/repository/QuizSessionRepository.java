package com.ssafy.backend.domain.learn.quiz.repository;

import com.ssafy.backend.domain.learn.quiz.entity.QuizSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {}
