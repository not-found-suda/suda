package com.ssafy.backend.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.backend.domain.child.entity.ChildProfile;
import com.ssafy.backend.domain.learn.entity.Learn;
import com.ssafy.backend.domain.learn.entity.LearnCategory;
import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizAnswer;
import com.ssafy.backend.domain.learn.quiz.entity.QuizQuestion;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSession;
import com.ssafy.backend.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReportQuizSessionQueryRepositoryTest {

  private static final Long UNKNOWN_CHILD_ID = Long.MAX_VALUE;

  @Autowired private EntityManager entityManager;

  @Autowired private ReportQuizSessionQueryRepository repository;

  @Test
  @DisplayName("요약 조회는 기간 내 완료된 기록이 없어도 0값과 빈 취약 단어 목록을 반환한다")
  void summaryQueriesReturnEmptyValuesWhenNoCompletedSessionsExistInDateRange() {
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
    LocalDateTime to = LocalDateTime.of(2027, 1, 1, 0, 0);

    ReportSummaryAggregateRow summary = repository.summarize(UNKNOWN_CHILD_ID, from, to);
    Page<ReportWeakWordQueryRow> weakWords =
        repository.findWeakWords(UNKNOWN_CHILD_ID, from, to, null, 1, PageRequest.of(0, 5));

    assertThat(summary.completedSessionCount()).isZero();
    assertThat(summary.totalQuestionCount()).isZero();
    assertThat(summary.totalCorrectCount()).isZero();
    assertThat(summary.totalStar()).isZero();
    assertThat(repository.findLatestCategory(UNKNOWN_CHILD_ID, from, to)).isEmpty();
    assertThat(weakWords.getContent()).isEmpty();
    assertThat(weakWords.getTotalElements()).isZero();
  }

  @Test
  @DisplayName("완료된 세션은 있지만 취약 단어가 없으면 요약 집계와 빈 취약 단어 목록을 반환한다")
  void summaryQueriesReturnEmptyWeakWordsWhenCompletedSessionHasNoWeakWords() {
    TestFixture fixture = persistFixture();
    QuizSession session =
        QuizSession.create(fixture.childId(), fixture.categoryId(), LearnDifficulty.NORMAL, 1);
    session.increaseCorrectCount();
    session.addStar(5);
    session.complete();
    entityManager.persist(session);
    QuizQuestion question = QuizQuestion.create(session, fixture.word(), 1);
    entityManager.persist(question);
    entityManager.persist(
        QuizAnswer.create(
            session, question, fixture.word(), "apple", "apple", true, 5, null, null, 1.0));
    entityManager.flush();
    entityManager.clear();

    ReportSummaryAggregateRow summary = repository.summarize(fixture.childId(), null, null);
    Page<ReportWeakWordQueryRow> weakWords =
        repository.findWeakWords(fixture.childId(), null, null, null, 1, PageRequest.of(0, 5));

    assertThat(summary.completedSessionCount()).isEqualTo(1L);
    assertThat(summary.totalQuestionCount()).isEqualTo(1L);
    assertThat(summary.totalCorrectCount()).isEqualTo(1L);
    assertThat(summary.totalStar()).isEqualTo(5L);
    assertThat(repository.findLatestCategory(fixture.childId(), null, null)).isPresent();
    assertThat(weakWords.getContent()).isEmpty();
    assertThat(weakWords.getTotalElements()).isZero();
  }

  @Test
  @DisplayName("취약 단어가 있으면 Summary용 취약 단어 쿼리가 정상 row를 반환한다")
  void findWeakWordsReturnsWeakWordRows() {
    TestFixture fixture = persistFixture();
    QuizSession session =
        QuizSession.create(fixture.childId(), fixture.categoryId(), LearnDifficulty.NORMAL, 1);
    session.addStar(2);
    session.complete();
    entityManager.persist(session);
    QuizQuestion question = QuizQuestion.create(session, fixture.word(), 1);
    entityManager.persist(question);
    entityManager.persist(
        QuizAnswer.create(
            session, question, fixture.word(), "apple", "appl", false, 2, null, null, 0.5));
    entityManager.flush();
    entityManager.clear();

    Page<ReportWeakWordQueryRow> weakWords =
        repository.findWeakWords(fixture.childId(), null, null, null, 1, PageRequest.of(0, 5));

    assertThat(weakWords.getTotalElements()).isEqualTo(1L);
    assertThat(weakWords.getContent())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.wordId()).isEqualTo(fixture.wordId());
              assertThat(row.attemptCount()).isEqualTo(1L);
              assertThat(row.wrongCount()).isEqualTo(1L);
              assertThat(row.averageStar()).isEqualTo(2.0);
            });
  }

  private TestFixture persistFixture() {
    User user = User.create("report-" + UUID.randomUUID() + "@example.com", "password", "guardian");
    entityManager.persist(user);
    ChildProfile child =
        ChildProfile.create(user, "child", LocalDate.of(2020, 1, 1), "purple_diamond");
    entityManager.persist(child);
    LearnCategory category = newEntity(LearnCategory.class);
    ReflectionTestUtils.setField(category, "name", "words");
    ReflectionTestUtils.setField(category, "active", true);
    ReflectionTestUtils.setField(category, "sortOrder", 1);
    entityManager.persist(category);
    Learn word = newEntity(Learn.class);
    ReflectionTestUtils.setField(word, "category", category);
    ReflectionTestUtils.setField(word, "difficulty", LearnDifficulty.NORMAL);
    ReflectionTestUtils.setField(word, "word", "apple");
    ReflectionTestUtils.setField(word, "displayText", "apple");
    ReflectionTestUtils.setField(word, "active", true);
    ReflectionTestUtils.setField(word, "sortOrder", 1);
    entityManager.persist(word);
    entityManager.flush();
    return new TestFixture(child.getId(), category.getId(), word.getId(), word);
  }

  private <T> T newEntity(Class<T> type) {
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private record TestFixture(Long childId, Long categoryId, Long wordId, Learn word) {}
}
