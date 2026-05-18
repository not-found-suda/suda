package com.ssafy.backend.domain.learn.quiz.repository;

import com.ssafy.backend.domain.learn.quiz.entity.QuizAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

  List<QuizAnswer> findBySessionIdOrderByQuestionQuestionNumberAsc(Long sessionId);

  Optional<QuizAnswer> findByQuestionId(Long questionId);

  boolean existsByQuestionId(Long questionId);
}
