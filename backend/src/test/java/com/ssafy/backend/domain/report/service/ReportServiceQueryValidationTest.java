package com.ssafy.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.backend.domain.child.entity.ChildProfile;
import com.ssafy.backend.domain.child.repository.ChildProfileRepository;
import com.ssafy.backend.domain.comms.repository.CommunicationSessionAnalysisRepository;
import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;
import com.ssafy.backend.domain.report.dto.ReportCategoryListResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionListResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionSearchCondition;
import com.ssafy.backend.domain.report.dto.ReportSummaryResponse;
import com.ssafy.backend.domain.report.dto.ReportWeakWordListResponse;
import com.ssafy.backend.domain.report.dto.ReportWeakWordSearchCondition;
import com.ssafy.backend.domain.report.repository.ReportQuizSessionQueryRepository;
import com.ssafy.backend.domain.report.repository.ReportSummaryAggregateRow;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.ValidationErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReportServiceQueryValidationTest {

  private static final Long USER_ID = 1L;
  private static final Long CHILD_ID = 10L;
  private static final Long CATEGORY_ID = 20L;

  @Mock private ChildProfileRepository childProfileRepository;
  @Mock private ReportQuizSessionQueryRepository reportQuizSessionQueryRepository;
  @Mock private CommunicationSessionAnalysisRepository communicationSessionAnalysisRepository;

  private ReportService reportService;

  @BeforeEach
  void setUp() {
    reportService =
        new ReportService(
            childProfileRepository,
            reportQuizSessionQueryRepository,
            communicationSessionAnalysisRepository);
  }

  @Test
  @DisplayName("학습 기록이 없으면 리포트 요약은 0값과 빈 목록을 반환한다")
  void getSummaryReturnsZeroValuesWhenNoLearningRecordsExist() {
    givenOwnedActiveChild();
    when(reportQuizSessionQueryRepository.summarize(CHILD_ID, null, null))
        .thenReturn(new ReportSummaryAggregateRow(0L, 0L, 0L, 0L));
    when(reportQuizSessionQueryRepository.findLatestCategory(CHILD_ID, null, null))
        .thenReturn(Optional.empty());
    when(reportQuizSessionQueryRepository.findWeakWords(
            eq(CHILD_ID), eq(null), eq(null), eq(null), eq(1), any()))
        .thenReturn(Page.empty());

    ReportSummaryResponse response = reportService.getSummary(USER_ID, CHILD_ID, null, null);

    assertThat(response.childId()).isEqualTo(CHILD_ID);
    assertThat(response.completedSessionCount()).isZero();
    assertThat(response.totalQuestionCount()).isZero();
    assertThat(response.totalCorrectCount()).isZero();
    assertThat(response.accuracyRate()).isZero();
    assertThat(response.averageStar()).isZero();
    assertThat(response.latestSessionAt()).isNull();
    assertThat(response.latestCategory()).isNull();
    assertThat(response.weakWords()).isEmpty();
  }

  @Test
  @DisplayName("약한 단어 기록이 없으면 빈 목록과 페이징 정보를 반환한다")
  void getWeakWordsReturnsEmptyPageWhenNoRecordsExist() {
    givenOwnedActiveChild();
    when(reportQuizSessionQueryRepository.findWeakWords(
            eq(CHILD_ID), eq(null), eq(null), eq(null), eq(1), any(PageRequest.class)))
        .thenReturn(Page.empty(PageRequest.of(1, 10)));

    ReportWeakWordListResponse response =
        reportService.getWeakWords(
            USER_ID, CHILD_ID, new ReportWeakWordSearchCondition(null, null, null, null, 2, 10));

    assertThat(response.content()).isEmpty();
    assertThat(response.page()).isEqualTo(2);
    assertThat(response.size()).isEqualTo(10);
    assertThat(response.totalElements()).isZero();
    assertThat(response.totalPages()).isZero();
    verifyWeakWordsPageRequest(1, 10);
  }

  @Test
  @DisplayName("퀴즈 세션 기록이 없으면 빈 목록과 페이징 정보를 반환한다")
  void getQuizSessionsReturnsEmptyPageWhenNoRecordsExist() {
    givenOwnedActiveChild();
    when(reportQuizSessionQueryRepository.findSessions(
            eq(CHILD_ID),
            eq(null),
            eq(null),
            eq(null),
            eq(LearnDifficulty.NORMAL),
            eq(QuizSessionStatus.COMPLETED),
            any(PageRequest.class)))
        .thenReturn(Page.empty(PageRequest.of(2, 5)));

    ReportQuizSessionListResponse response =
        reportService.getQuizSessions(
            USER_ID,
            CHILD_ID,
            new ReportQuizSessionSearchCondition(
                null, null, null, LearnDifficulty.NORMAL, QuizSessionStatus.COMPLETED, 3, 5));

    assertThat(response.content()).isEmpty();
    assertThat(response.page()).isEqualTo(3);
    assertThat(response.size()).isEqualTo(5);
    assertThat(response.totalElements()).isZero();
    assertThat(response.totalPages()).isZero();
    verifyQuizSessionsPageRequest(2, 5);
  }

  @Test
  @DisplayName("카테고리별 기록이 없으면 빈 목록을 반환한다")
  void getCategoriesReturnsEmptyListWhenNoRecordsExist() {
    givenOwnedActiveChild();
    when(reportQuizSessionQueryRepository.findCategories(CHILD_ID, null, null))
        .thenReturn(List.of());

    ReportCategoryListResponse response =
        reportService.getCategories(USER_ID, CHILD_ID, null, null);

    assertThat(response.categories()).isEmpty();
  }

  @Test
  @DisplayName("조회 날짜는 시작일 00:00부터 종료일 다음날 00:00 전까지 전달한다")
  void getSummaryPassesParsedDateRangeToRepository() {
    givenOwnedActiveChild();
    LocalDateTime from = LocalDateTime.of(2026, 5, 1, 0, 0);
    LocalDateTime to = LocalDateTime.of(2026, 5, 8, 0, 0);
    when(reportQuizSessionQueryRepository.summarize(
            eq(CHILD_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(new ReportSummaryAggregateRow(0L, 0L, 0L, 0L));
    when(reportQuizSessionQueryRepository.findLatestCategory(
            eq(CHILD_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(Optional.empty());
    when(reportQuizSessionQueryRepository.findWeakWords(
            eq(CHILD_ID),
            any(LocalDateTime.class),
            any(LocalDateTime.class),
            eq(null),
            eq(1),
            any()))
        .thenReturn(Page.empty());

    reportService.getSummary(USER_ID, CHILD_ID, "2026-05-01", "2026-05-07");

    verify(reportQuizSessionQueryRepository).summarize(CHILD_ID, from, to);
    verify(reportQuizSessionQueryRepository).findLatestCategory(CHILD_ID, from, to);
    verify(reportQuizSessionQueryRepository)
        .findWeakWords(eq(CHILD_ID), eq(from), eq(to), eq(null), eq(1), any());
  }

  @Test
  @DisplayName("잘못된 날짜 형식이면 리포트 쿼리를 실행하지 않고 실패한다")
  void rejectsInvalidDateFormat() {
    givenOwnedActiveChild();

    assertValidationError(() -> reportService.getSummary(USER_ID, CHILD_ID, "2026/05/01", null));
    verifyNoInteractions(reportQuizSessionQueryRepository);
  }

  @Test
  @DisplayName("조회 시작일이 종료일보다 늦으면 실패한다")
  void rejectsInvalidDateRange() {
    givenOwnedActiveChild();

    assertValidationError(
        () -> reportService.getSummary(USER_ID, CHILD_ID, "2026-05-08", "2026-05-07"));
    verifyNoInteractions(reportQuizSessionQueryRepository);
  }

  @Test
  @DisplayName("childId와 categoryId는 1 이상이어야 한다")
  void rejectsInvalidIds() {
    assertValidationError(() -> reportService.getSummary(USER_ID, 0L, null, null));
    verifyNoInteractions(childProfileRepository, reportQuizSessionQueryRepository);

    ReportWeakWordSearchCondition condition =
        new ReportWeakWordSearchCondition(null, null, 0L, null, 1, 20);

    assertValidationError(() -> reportService.getWeakWords(USER_ID, CHILD_ID, condition));
    verifyNoInteractions(reportQuizSessionQueryRepository);
  }

  @Test
  @DisplayName("page는 1 이상이어야 한다")
  void rejectsInvalidPage() {
    givenOwnedActiveChild();
    ReportQuizSessionSearchCondition condition =
        new ReportQuizSessionSearchCondition(null, null, null, null, null, 0, 20);

    assertValidationError(() -> reportService.getQuizSessions(USER_ID, CHILD_ID, condition));
    verifyNoInteractions(reportQuizSessionQueryRepository);
  }

  @Test
  @DisplayName("size는 1 이상 100 이하여야 한다")
  void rejectsInvalidSize() {
    givenOwnedActiveChild();
    ReportQuizSessionSearchCondition tooSmall =
        new ReportQuizSessionSearchCondition(null, null, null, null, null, 1, 0);
    ReportQuizSessionSearchCondition tooLarge =
        new ReportQuizSessionSearchCondition(null, null, null, null, null, 1, 101);

    assertValidationError(() -> reportService.getQuizSessions(USER_ID, CHILD_ID, tooSmall));
    assertValidationError(() -> reportService.getQuizSessions(USER_ID, CHILD_ID, tooLarge));
    verifyNoInteractions(reportQuizSessionQueryRepository);
  }

  @Test
  @DisplayName("minAttemptCount는 1 이상이어야 한다")
  void rejectsInvalidMinAttemptCount() {
    givenOwnedActiveChild();
    ReportWeakWordSearchCondition condition =
        new ReportWeakWordSearchCondition(null, null, null, 0, 1, 20);

    assertValidationError(() -> reportService.getWeakWords(USER_ID, CHILD_ID, condition));
    verifyNoInteractions(reportQuizSessionQueryRepository);
  }

  private void givenOwnedActiveChild() {
    when(childProfileRepository.findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID))
        .thenReturn(Optional.of(activeChild()));
  }

  private ChildProfile activeChild() {
    return ChildProfile.create(
        User.create("guardian@example.com", "encoded-password", "보호자"),
        "민준",
        LocalDate.now().minusYears(6),
        "purple_diamond");
  }

  private void verifyWeakWordsPageRequest(int expectedPage, int expectedSize) {
    ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(reportQuizSessionQueryRepository)
        .findWeakWords(eq(CHILD_ID), eq(null), eq(null), eq(null), eq(1), pageCaptor.capture());
    Pageable pageable = pageCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(expectedPage);
    assertThat(pageable.getPageSize()).isEqualTo(expectedSize);
  }

  private void verifyQuizSessionsPageRequest(int expectedPage, int expectedSize) {
    ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(reportQuizSessionQueryRepository)
        .findSessions(
            eq(CHILD_ID),
            eq(null),
            eq(null),
            eq(null),
            eq(LearnDifficulty.NORMAL),
            eq(QuizSessionStatus.COMPLETED),
            pageCaptor.capture());
    Pageable pageable = pageCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(expectedPage);
    assertThat(pageable.getPageSize()).isEqualTo(expectedSize);
  }

  private void assertValidationError(ThrowingCall call) {
    assertThatThrownBy(call::invoke)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT));
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void invoke();
  }
}
