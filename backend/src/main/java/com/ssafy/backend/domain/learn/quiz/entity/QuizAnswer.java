package com.ssafy.backend.domain.learn.quiz.entity;

import com.ssafy.backend.domain.learn.entity.Learn;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_answers")
public class QuizAnswer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private QuizSession session;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_id", nullable = false)
  private QuizQuestion question;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "word_id", nullable = false)
  private Learn word;

  @Column(name = "target_text", nullable = false, length = 100)
  private String targetText;

  @Column(name = "recognized_text", length = 100)
  private String recognizedText;

  @Column(name = "is_correct", nullable = false)
  private Boolean isCorrect;

  @Column(nullable = false)
  private Integer star;

  @Column(length = 255)
  private String feedback;

  @Column(name = "grading_reason", length = 500)
  private String gradingReason;

  @Column private Double confidence;

  @Column(name = "answered_at", nullable = false)
  private LocalDateTime answeredAt;

  protected QuizAnswer() {}

  private QuizAnswer(
      QuizSession session,
      QuizQuestion question,
      Learn word,
      String targetText,
      String recognizedText,
      Boolean isCorrect,
      Integer star,
      String feedback,
      String gradingReason,
      Double confidence) {
    this.session = session;
    this.question = question;
    this.word = word;
    this.targetText = targetText;
    this.recognizedText = recognizedText;
    this.isCorrect = isCorrect;
    this.star = star;
    this.feedback = feedback;
    this.gradingReason = gradingReason;
    this.confidence = confidence;
    this.answeredAt = LocalDateTime.now();
  }

  public static QuizAnswer create(
      QuizSession session,
      QuizQuestion question,
      Learn word,
      String targetText,
      String recognizedText,
      Boolean isCorrect,
      Integer star,
      String feedback,
      String gradingReason,
      Double confidence) {
    return new QuizAnswer(
        session,
        question,
        word,
        targetText,
        recognizedText,
        isCorrect,
        star,
        feedback,
        gradingReason,
        confidence);
  }

  public Long getId() {
    return id;
  }

  public QuizSession getSession() {
    return session;
  }

  public QuizQuestion getQuestion() {
    return question;
  }

  public Learn getWord() {
    return word;
  }

  public String getTargetText() {
    return targetText;
  }

  public String getRecognizedText() {
    return recognizedText;
  }

  public Boolean getIsCorrect() {
    return isCorrect;
  }

  public boolean isCorrect() {
    return Boolean.TRUE.equals(isCorrect);
  }

  public Integer getStar() {
    return star;
  }

  public String getFeedback() {
    return feedback;
  }

  public String getGradingReason() {
    return gradingReason;
  }

  public Double getConfidence() {
    return confidence;
  }

  public LocalDateTime getAnsweredAt() {
    return answeredAt;
  }
}
