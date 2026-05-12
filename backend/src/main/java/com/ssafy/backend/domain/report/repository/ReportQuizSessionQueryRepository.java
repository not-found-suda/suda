package com.ssafy.backend.domain.report.repository;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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

  public ReportSummaryAggregateRow summarize(Long childId, LocalDateTime from, LocalDateTime to) {
    StringBuilder where = new StringBuilder(" WHERE s.childProfileId = :childId");
    appendDateConditions(where, from, to);
    where.append(" AND s.status = :completed");

    String jpql =
        """
        SELECT
          COUNT(s.id),
          COALESCE(SUM(s.totalQuestionCount), 0),
          COALESCE(SUM(s.correctCount), 0),
          COALESCE(SUM(s.totalStar), 0)
        FROM QuizSession s
        """
            + where;
    Query query = entityManager.createQuery(jpql);
    query.setParameter("childId", childId);
    query.setParameter("completed", QuizSessionStatus.COMPLETED);
    bindDateParameters(query, from, to);

    Object[] row = (Object[]) query.getSingleResult();
    return new ReportSummaryAggregateRow(
        toLong(row[0]), toLong(row[1]), toLong(row[2]), toLong(row[3]));
  }

  public Optional<ReportLatestCategoryQueryRow> findLatestCategory(
      Long childId, LocalDateTime from, LocalDateTime to) {
    StringBuilder where = new StringBuilder(" WHERE s.childProfileId = :childId");
    appendDateConditions(where, from, to);
    where.append(" AND s.status = :completed");

    String jpql =
        """
        SELECT new com.ssafy.backend.domain.report.repository.ReportLatestCategoryQueryRow(
          s.categoryId,
          c.name,
          s.startedAt
        )
        FROM QuizSession s
        JOIN LearnCategory c ON c.id = s.categoryId
        """
            + where
            + " ORDER BY s.startedAt DESC, s.id DESC";

    TypedQuery<ReportLatestCategoryQueryRow> query =
        entityManager.createQuery(jpql, ReportLatestCategoryQueryRow.class);
    query.setParameter("childId", childId);
    bindDateParameters(query, from, to);
    query.setMaxResults(1);

    return query.getResultList().stream().findFirst();
  }

  public Page<ReportWeakWordQueryRow> findWeakWords(
      Long childId,
      LocalDateTime from,
      LocalDateTime to,
      Long categoryId,
      int minAttemptCount,
      Pageable pageable) {
    StringBuilder where = new StringBuilder(" WHERE s.childProfileId = :childId");
    appendDateConditions(where, from, to);
    where.append(" AND s.status = :completed");
    if (categoryId != null) {
      where.append(" AND c.id = :categoryId");
    }

    String selectJpql =
        """
        SELECT new com.ssafy.backend.domain.report.repository.ReportWeakWordQueryRow(
          w.id,
          w.word,
          w.displayText,
          c.id,
          c.name,
          COUNT(a.id),
          COALESCE(SUM(CASE WHEN a.isCorrect = false THEN 1 ELSE 0 END), 0),
          AVG(a.star),
          MAX(a.answeredAt)
        )
        FROM QuizAnswer a
        JOIN a.session s
        JOIN a.word w
        JOIN w.category c
        """
            + where
            + """
        GROUP BY w.id, w.word, w.displayText, c.id, c.name
        HAVING COUNT(a.id) >= :minAttemptCount
          AND (
            COALESCE(SUM(CASE WHEN a.isCorrect = false THEN 1 ELSE 0 END), 0) > 0
            OR AVG(a.star) < :maxStar
          )
        ORDER BY
          COALESCE(SUM(CASE WHEN a.isCorrect = false THEN 1 ELSE 0 END), 0) DESC,
          AVG(a.star) ASC,
          MAX(a.answeredAt) DESC
        """;

    TypedQuery<ReportWeakWordQueryRow> query =
        entityManager.createQuery(selectJpql, ReportWeakWordQueryRow.class);
    bindWeakWordParameters(query, childId, from, to, categoryId, minAttemptCount);
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    String countJpql =
        """
        SELECT COUNT(DISTINCT w.id)
        FROM QuizAnswer a
        JOIN a.session s
        JOIN a.word w
        JOIN w.category c
        """
            + where
            + """
        AND (
          SELECT COUNT(a2.id)
          FROM QuizAnswer a2
          JOIN a2.session s2
          JOIN a2.word w2
          JOIN w2.category c2
          WHERE s2.childProfileId = :childId
            AND s2.status = :completed
            AND w2.id = w.id
        """
            + weakWordSubqueryFilters(from, to, categoryId)
            + """
        ) >= :minAttemptCount
          AND (
            (
              SELECT COALESCE(SUM(CASE WHEN a3.isCorrect = false THEN 1 ELSE 0 END), 0)
              FROM QuizAnswer a3
              JOIN a3.session s3
              JOIN a3.word w3
              JOIN w3.category c3
              WHERE s3.childProfileId = :childId
                AND s3.status = :completed
                AND w3.id = w.id
        """
            + weakWordSubqueryFilters("s3", "c3", from, to, categoryId)
            + """
            ) > 0
            OR (
              SELECT AVG(a4.star)
              FROM QuizAnswer a4
              JOIN a4.session s4
              JOIN a4.word w4
              JOIN w4.category c4
              WHERE s4.childProfileId = :childId
                AND s4.status = :completed
                AND w4.id = w.id
        """
            + weakWordSubqueryFilters("s4", "c4", from, to, categoryId)
            + """
            ) < :maxStar
          )
        """;
    TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
    bindWeakWordParameters(countQuery, childId, from, to, categoryId, minAttemptCount);

    return new PageImpl<>(query.getResultList(), pageable, countQuery.getSingleResult());
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

  private void appendDateConditions(StringBuilder where, LocalDateTime from, LocalDateTime to) {
    if (from != null) {
      where.append(" AND s.startedAt >= :from");
    }
    if (to != null) {
      where.append(" AND s.startedAt < :to");
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

  private void bindDateParameters(Query query, LocalDateTime from, LocalDateTime to) {
    if (from != null) {
      query.setParameter("from", from);
    }
    if (to != null) {
      query.setParameter("to", to);
    }
  }

  private void bindWeakWordParameters(
      Query query,
      Long childId,
      LocalDateTime from,
      LocalDateTime to,
      Long categoryId,
      int minAttemptCount) {
    query.setParameter("childId", childId);
    query.setParameter("completed", QuizSessionStatus.COMPLETED);
    query.setParameter("minAttemptCount", (long) minAttemptCount);
    query.setParameter("maxStar", 3.0);
    bindDateParameters(query, from, to);
    if (categoryId != null) {
      query.setParameter("categoryId", categoryId);
    }
  }

  private String weakWordSubqueryFilters(LocalDateTime from, LocalDateTime to, Long categoryId) {
    return weakWordSubqueryFilters("s2", "c2", from, to, categoryId);
  }

  private String weakWordSubqueryFilters(
      String sessionAlias,
      String categoryAlias,
      LocalDateTime from,
      LocalDateTime to,
      Long categoryId) {
    StringBuilder filters = new StringBuilder();
    if (from != null) {
      filters.append(" AND ").append(sessionAlias).append(".startedAt >= :from");
    }
    if (to != null) {
      filters.append(" AND ").append(sessionAlias).append(".startedAt < :to");
    }
    if (categoryId != null) {
      filters.append(" AND ").append(categoryAlias).append(".id = :categoryId");
    }
    return filters.toString();
  }

  private Long toLong(Object value) {
    if (value == null) {
      return 0L;
    }
    return ((Number) value).longValue();
  }

  private Double toDouble(Object value) {
    if (value == null) {
      return 0.0;
    }
    return ((Number) value).doubleValue();
  }
}
