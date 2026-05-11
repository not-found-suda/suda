package com.ssafy.backend.domain.report.repository;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class ReportQuizSessionQueryRepository {

  private final EntityManager entityManager;

  public ReportQuizSessionQueryRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public Page<ReportQuizSessionQueryRow> findSessions(
      Long childId,
      LocalDateTime from,
      LocalDateTime to,
      Long categoryId,
      LearnDifficulty difficulty,
      QuizSessionStatus status,
      Pageable pageable) {
    StringBuilder where = new StringBuilder(" WHERE s.childProfileId = :childId");
    appendConditions(where, from, to, categoryId, difficulty, status);

    String selectJpql =
        """
        SELECT new com.ssafy.backend.domain.report.repository.ReportQuizSessionQueryRow(
          s.id,
          s.childProfileId,
          s.categoryId,
          c.name,
          s.difficulty,
          s.totalQuestionCount,
          s.correctCount,
          s.totalStar,
          s.status,
          s.startedAt,
          s.endedAt
        )
        FROM QuizSession s
        JOIN LearnCategory c ON c.id = s.categoryId
        """
            + where
            + " ORDER BY s.startedAt DESC, s.id DESC";

    TypedQuery<ReportQuizSessionQueryRow> query =
        entityManager.createQuery(selectJpql, ReportQuizSessionQueryRow.class);
    bindParameters(query, childId, from, to, categoryId, difficulty, status);
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    String countJpql =
        """
        SELECT COUNT(s.id)
        FROM QuizSession s
        """ + where;
    TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
    bindParameters(countQuery, childId, from, to, categoryId, difficulty, status);

    return new PageImpl<>(query.getResultList(), pageable, countQuery.getSingleResult());
  }

  public Optional<ReportQuizSessionQueryRow> findSession(Long childId, Long sessionId) {
    String jpql =
        """
        SELECT new com.ssafy.backend.domain.report.repository.ReportQuizSessionQueryRow(
          s.id,
          s.childProfileId,
          s.categoryId,
          c.name,
          s.difficulty,
          s.totalQuestionCount,
          s.correctCount,
          s.totalStar,
          s.status,
          s.startedAt,
          s.endedAt
        )
        FROM QuizSession s
        JOIN LearnCategory c ON c.id = s.categoryId
        WHERE s.childProfileId = :childId
          AND s.id = :sessionId
        """;
    List<ReportQuizSessionQueryRow> rows =
        entityManager
            .createQuery(jpql, ReportQuizSessionQueryRow.class)
            .setParameter("childId", childId)
            .setParameter("sessionId", sessionId)
            .getResultList();
    return rows.stream().findFirst();
  }

  public List<ReportQuizAnswerQueryRow> findAnswers(Long childId, Long sessionId) {
    String jpql =
        """
        SELECT new com.ssafy.backend.domain.report.repository.ReportQuizAnswerQueryRow(
          q.id,
          q.questionNumber,
          a.word.id,
          a.targetText,
          a.recognizedText,
          a.isCorrect,
          a.star,
          a.feedback,
          a.answeredAt
        )
        FROM QuizAnswer a
        JOIN a.question q
        WHERE a.session.id = :sessionId
          AND a.session.childProfileId = :childId
        ORDER BY q.questionNumber ASC
        """;
    return entityManager
        .createQuery(jpql, ReportQuizAnswerQueryRow.class)
        .setParameter("childId", childId)
        .setParameter("sessionId", sessionId)
        .getResultList();
  }

  private void appendConditions(
      StringBuilder where,
      LocalDateTime from,
      LocalDateTime to,
      Long categoryId,
      LearnDifficulty difficulty,
      QuizSessionStatus status) {
    if (from != null) {
      where.append(" AND s.startedAt >= :from");
    }
    if (to != null) {
      where.append(" AND s.startedAt < :to");
    }
    if (categoryId != null) {
      where.append(" AND s.categoryId = :categoryId");
    }
    if (difficulty != null) {
      where.append(" AND s.difficulty = :difficulty");
    }
    if (status != null) {
      where.append(" AND s.status = :status");
    }
  }

  private void bindParameters(
      TypedQuery<?> query,
      Long childId,
      LocalDateTime from,
      LocalDateTime to,
      Long categoryId,
      LearnDifficulty difficulty,
      QuizSessionStatus status) {
    query.setParameter("childId", childId);
    if (from != null) {
      query.setParameter("from", from);
    }
    if (to != null) {
      query.setParameter("to", to);
    }
    if (categoryId != null) {
      query.setParameter("categoryId", categoryId);
    }
    if (difficulty != null) {
      query.setParameter("difficulty", difficulty);
    }
    if (status != null) {
      query.setParameter("status", status);
    }
  }
}
