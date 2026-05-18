package com.ssafy.backend.domain.child.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.backend.domain.child.dto.ChildProfileResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileUpdateRequestDto;
import com.ssafy.backend.domain.child.entity.ChildProfile;
import com.ssafy.backend.domain.child.exception.ChildProfileErrorCode;
import com.ssafy.backend.domain.child.repository.ChildProfileRepository;
import com.ssafy.backend.domain.comms.repository.CommunicationSessionAnalysisRepository;
import com.ssafy.backend.domain.comms.service.ClovaSttClient;
import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.dto.request.QuizSessionCreateRequest;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSession;
import com.ssafy.backend.domain.learn.quiz.repository.QuizAnswerRepository;
import com.ssafy.backend.domain.learn.quiz.repository.QuizQuestionRepository;
import com.ssafy.backend.domain.learn.quiz.repository.QuizSessionRepository;
import com.ssafy.backend.domain.learn.quiz.service.QuizGradingService;
import com.ssafy.backend.domain.learn.quiz.service.QuizService;
import com.ssafy.backend.domain.learn.repository.LearnRepository;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionSearchCondition;
import com.ssafy.backend.domain.report.dto.ReportSummaryResponse;
import com.ssafy.backend.domain.report.dto.ReportWeakWordSearchCondition;
import com.ssafy.backend.domain.report.repository.ReportQuizSessionQueryRepository;
import com.ssafy.backend.domain.report.repository.ReportSummaryAggregateRow;
import com.ssafy.backend.domain.report.service.ReportService;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.domain.user.repository.UserRepository;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.storage.AssetUrlResolver;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

@ExtendWith(MockitoExtension.class)
class ChildOwnershipServiceTest {

  private static final Long USER_ID = 1L;
  private static final Long CHILD_ID = 10L;
  private static final Long SESSION_ID = 100L;
  private static final Long CATEGORY_ID = 20L;

  @Mock private ChildProfileRepository childProfileRepository;
  @Mock private UserRepository userRepository;
  @Mock private QuizSessionRepository quizSessionRepository;
  @Mock private QuizQuestionRepository quizQuestionRepository;
  @Mock private QuizAnswerRepository quizAnswerRepository;
  @Mock private LearnRepository learnRepository;
  @Mock private ClovaSttClient clovaSttClient;
  @Mock private QuizGradingService quizGradingService;
  @Mock private AssetUrlResolver assetUrlResolver;
  @Mock private ReportQuizSessionQueryRepository reportQuizSessionQueryRepository;
  @Mock private CommunicationSessionAnalysisRepository communicationSessionAnalysisRepository;

  private ChildProfileService childProfileService;
  private QuizService quizService;
  private ReportService reportService;

  @BeforeEach
  void setUp() {
    childProfileService = new ChildProfileService(childProfileRepository, userRepository);
    quizService =
        new QuizService(
            quizSessionRepository,
            quizQuestionRepository,
            quizAnswerRepository,
            learnRepository,
            childProfileRepository,
            clovaSttClient,
            quizGradingService,
            assetUrlResolver);
    reportService =
        new ReportService(
            childProfileRepository,
            reportQuizSessionQueryRepository,
            communicationSessionAnalysisRepository);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아니면 아이 프로필을 조회할 수 없다")
  void getChildRejectsUnownedOrInactiveChild() {
    givenUnownedOrInactiveChild();

    assertChildNotFoundAndVerifyOwnership(() -> childProfileService.getChild(USER_ID, CHILD_ID));
  }

  @Test
  @DisplayName("본인 소유의 활성 아이이면 아이 프로필을 조회할 수 있다")
  void getChildAllowsOwnedActiveChild() {
    when(childProfileRepository.findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID))
        .thenReturn(Optional.of(activeChild()));

    ChildProfileResponseDto response = childProfileService.getChild(USER_ID, CHILD_ID);

    assertThat(response.name()).isEqualTo("민준");
    assertThat(response.active()).isTrue();
    verify(childProfileRepository).findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아니면 아이 프로필을 수정할 수 없다")
  void updateChildRejectsUnownedOrInactiveChild() {
    givenUnownedOrInactiveChild();

    assertChildNotFoundAndVerifyOwnership(
        () ->
            childProfileService.updateChild(
                USER_ID, CHILD_ID, new ChildProfileUpdateRequestDto("수정", null, null)));
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아니면 아이 프로필을 삭제할 수 없다")
  void deleteChildRejectsUnownedOrInactiveChild() {
    givenUnownedOrInactiveChild();

    assertChildNotFoundAndVerifyOwnership(() -> childProfileService.deleteChild(USER_ID, CHILD_ID));
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아니면 퀴즈 세션을 생성할 수 없다")
  void createQuizSessionRejectsUnownedOrInactiveChild() {
    givenUnownedOrInactiveChild();
    QuizSessionCreateRequest request =
        new QuizSessionCreateRequest(CHILD_ID, CATEGORY_ID, LearnDifficulty.NORMAL, 3);

    assertChildNotFoundAndVerifyOwnership(() -> quizService.createSession(USER_ID, request));
    verifyNoInteractions(learnRepository, quizSessionRepository, quizQuestionRepository);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아닌 퀴즈 세션은 현재 문제를 조회할 수 없다")
  void getCurrentQuestionRejectsSessionOfUnownedOrInactiveChild() {
    givenSessionOfUnownedOrInactiveChild();

    assertChildNotFoundAndVerifyOwnership(
        () -> quizService.getCurrentQuestion(USER_ID, SESSION_ID));
    verify(quizSessionRepository).findById(SESSION_ID);
    verifyNoInteractions(quizQuestionRepository);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아닌 퀴즈 세션에는 답변을 제출할 수 없다")
  void submitAnswerRejectsSessionOfUnownedOrInactiveChild() {
    givenSessionOfUnownedOrInactiveChild();

    assertChildNotFoundAndVerifyOwnership(
        () -> quizService.submitAnswer(USER_ID, SESSION_ID, 1L, null));
    verify(quizSessionRepository).findById(SESSION_ID);
    verifyNoInteractions(quizQuestionRepository, quizAnswerRepository);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아닌 퀴즈 세션 결과는 조회할 수 없다")
  void getQuizResultRejectsSessionOfUnownedOrInactiveChild() {
    givenSessionOfUnownedOrInactiveChild();

    assertChildNotFoundAndVerifyOwnership(() -> quizService.getResult(USER_ID, SESSION_ID));
    verify(quizSessionRepository).findById(SESSION_ID);
    verifyNoInteractions(quizAnswerRepository);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아닌 퀴즈 세션은 종료할 수 없다")
  void endQuizSessionRejectsSessionOfUnownedOrInactiveChild() {
    givenSessionOfUnownedOrInactiveChild();

    assertChildNotFoundAndVerifyOwnership(() -> quizService.endSession(USER_ID, SESSION_ID));
    verify(quizSessionRepository).findById(SESSION_ID);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아니면 리포트 요약을 조회할 수 없다")
  void getReportSummaryRejectsUnownedOrInactiveChild() {
    givenUnownedOrInactiveChild();

    assertChildNotFoundAndVerifyOwnership(
        () -> reportService.getSummary(USER_ID, CHILD_ID, null, null));
    verifyNoInteractions(reportQuizSessionQueryRepository);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아니면 리포트 약한 단어 목록을 조회할 수 없다")
  void getReportWeakWordsRejectsUnownedOrInactiveChild() {
    givenUnownedOrInactiveChild();
    ReportWeakWordSearchCondition condition =
        new ReportWeakWordSearchCondition(null, null, null, null, 1, 20);

    assertChildNotFoundAndVerifyOwnership(
        () -> reportService.getWeakWords(USER_ID, CHILD_ID, condition));
    verifyNoInteractions(reportQuizSessionQueryRepository);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아니면 리포트 카테고리 목록을 조회할 수 없다")
  void getReportCategoriesRejectsUnownedOrInactiveChild() {
    givenUnownedOrInactiveChild();

    assertChildNotFoundAndVerifyOwnership(
        () -> reportService.getCategories(USER_ID, CHILD_ID, null, null));
    verifyNoInteractions(reportQuizSessionQueryRepository);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아니면 리포트 퀴즈 세션 목록을 조회할 수 없다")
  void getReportQuizSessionsRejectsUnownedOrInactiveChild() {
    givenUnownedOrInactiveChild();
    ReportQuizSessionSearchCondition condition =
        new ReportQuizSessionSearchCondition(null, null, null, null, null, 1, 20);

    assertChildNotFoundAndVerifyOwnership(
        () -> reportService.getQuizSessions(USER_ID, CHILD_ID, condition));
    verifyNoInteractions(reportQuizSessionQueryRepository);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이가 아니면 리포트 퀴즈 세션 상세를 조회할 수 없다")
  void getReportQuizSessionDetailRejectsUnownedOrInactiveChild() {
    givenUnownedOrInactiveChild();

    assertChildNotFoundAndVerifyOwnership(
        () -> reportService.getQuizSessionDetail(USER_ID, CHILD_ID, SESSION_ID));
    verifyNoInteractions(reportQuizSessionQueryRepository);
  }

  @Test
  @DisplayName("본인 소유의 활성 아이이면 리포트 요약을 조회할 수 있다")
  void getReportSummaryAllowsOwnedActiveChild() {
    when(childProfileRepository.findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID))
        .thenReturn(Optional.of(activeChild()));
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
    assertThat(response.accuracyRate()).isZero();
    assertThat(response.averageStar()).isZero();
    verify(childProfileRepository).findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID);
  }

  private void givenUnownedOrInactiveChild() {
    when(childProfileRepository.findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID))
        .thenReturn(Optional.empty());
  }

  private void givenSessionOfUnownedOrInactiveChild() {
    QuizSession session = QuizSession.create(CHILD_ID, CATEGORY_ID, LearnDifficulty.NORMAL, 3);
    when(quizSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    givenUnownedOrInactiveChild();
  }

  private ChildProfile activeChild() {
    return ChildProfile.create(
        User.create("guardian@example.com", "encoded-password", "보호자"),
        "민준",
        LocalDate.now().minusYears(6),
        "purple_diamond");
  }

  private void assertChildNotFound(ThrowingCall call) {
    assertThatThrownBy(call::invoke)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ChildProfileErrorCode.NOT_FOUND));
  }

  private void assertChildNotFoundAndVerifyOwnership(ThrowingCall call) {
    assertChildNotFound(call);
    verify(childProfileRepository).findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID);
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void invoke();
  }
}
