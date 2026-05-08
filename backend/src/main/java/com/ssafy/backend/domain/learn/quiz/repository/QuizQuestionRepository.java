package com.ssafy.backend.domain.learn.quiz.repository;

import com.ssafy.backend.domain.learn.quiz.entity.QuizQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

  Optional<QuizQuestion> findFirstBySessionIdAndAnsweredFalseOrderByQuestionNumberAsc(
      Long sessionId);

  boolean existsBySessionIdAndAnsweredFalse(Long sessionId);

  List<QuizQuestion> findBySessionIdOrderByQuestionNumberAsc(Long sessionId);
}
