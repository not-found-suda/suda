package com.ssafy.backend.domain.learn.quiz.entity;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_sessions")
public class QuizSession extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "child_profile_id", nullable = false)
  private Long childProfileId;

  @Column(name = "category_id", nullable = false)
  private Long categoryId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private LearnDifficulty difficulty;

  @Column(name = "total_question_count", nullable = false)
  private Integer totalQuestionCount;

  @Column(name = "correct_count", nullable = false)
  private Integer correctCount = 0;

  @Column(name = "total_star", nullable = false)
  private Integer totalStar = 0;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private QuizSessionStatus status = QuizSessionStatus.IN_PROGRESS;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "ended_at")
  private LocalDateTime endedAt;

  protected QuizSession() {}

  private QuizSession(
      Long childProfileId,
      Long categoryId,
      LearnDifficulty difficulty,
      Integer totalQuestionCount) {
    this.childProfileId = childProfileId;
    this.categoryId = categoryId;
    this.difficulty = difficulty;
    this.totalQuestionCount = totalQuestionCount;
    this.startedAt = LocalDateTime.now();
  }

  public static QuizSession create(
      Long childProfileId,
      Long categoryId,
      LearnDifficulty difficulty,
      Integer totalQuestionCount) {
    return new QuizSession(childProfileId, categoryId, difficulty, totalQuestionCount);
  }

  public void increaseCorrectCount() {
    this.correctCount++;
  }

  public void addStar(int star) {
    this.totalStar += star;
  }

  public void complete() {
    this.status = QuizSessionStatus.COMPLETED;
    this.endedAt = LocalDateTime.now();
  }

  public boolean isCompleted() {
    return this.status == QuizSessionStatus.COMPLETED;
  }

  public Long getId() {
    return id;
  }

  public Long getChildProfileId() {
    return childProfileId;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public LearnDifficulty getDifficulty() {
    return difficulty;
  }

  public Integer getTotalQuestionCount() {
    return totalQuestionCount;
  }

  public Integer getCorrectCount() {
    return correctCount;
  }

  public Integer getTotalStar() {
    return totalStar;
  }

  public QuizSessionStatus getStatus() {
    return status;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public LocalDateTime getEndedAt() {
    return endedAt;
  }
}
