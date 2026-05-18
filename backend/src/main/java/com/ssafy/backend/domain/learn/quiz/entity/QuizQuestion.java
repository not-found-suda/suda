package com.ssafy.backend.domain.learn.quiz.entity;

import com.ssafy.backend.domain.learn.entity.Learn;
import jakarta.persistence.*;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private QuizSession session;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "word_id", nullable = false)
  private Learn word;

  @Column(name = "question_number", nullable = false)
  private Integer questionNumber;

  @Column(nullable = false)
  private Boolean answered = false;

  protected QuizQuestion() {}

  private QuizQuestion(QuizSession session, Learn word, Integer questionNumber) {
    this.session = session;
    this.word = word;
    this.questionNumber = questionNumber;
  }

  public static QuizQuestion create(QuizSession session, Learn word, Integer questionNumber) {
    return new QuizQuestion(session, word, questionNumber);
  }

  public void markAnswered() {
    this.answered = true;
  }

  public Long getId() {
    return id;
  }

  public QuizSession getSession() {
    return session;
  }

  public Learn getWord() {
    return word;
  }

  public Integer getQuestionNumber() {
    return questionNumber;
  }

  public Boolean getAnswered() {
    return answered;
  }

  public boolean isAnswered() {
    return Boolean.TRUE.equals(answered);
  }
}
