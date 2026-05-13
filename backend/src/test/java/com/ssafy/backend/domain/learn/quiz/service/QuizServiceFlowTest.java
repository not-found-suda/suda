package com.ssafy.backend.domain.learn.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.backend.domain.child.entity.ChildProfile;
import com.ssafy.backend.domain.child.repository.ChildProfileRepository;
import com.ssafy.backend.domain.comms.service.ClovaSttClient;
import com.ssafy.backend.domain.learn.entity.Learn;
import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.dto.request.QuizSessionCreateRequest;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizAnswerResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizCurrentQuestionResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizResultResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizSessionCreateResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizSessionEndResponse;
import com.ssafy.backend.domain.learn.quiz.entity.QuizAnswer;
import com.ssafy.backend.domain.learn.quiz.entity.QuizQuestion;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSession;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;
import com.ssafy.backend.domain.learn.quiz.exception.QuizErrorCode;
import com.ssafy.backend.domain.learn.quiz.repository.QuizAnswerRepository;
import com.ssafy.backend.domain.learn.quiz.repository.QuizQuestionRepository;
import com.ssafy.backend.domain.learn.quiz.repository.QuizSessionRepository;
import com.ssafy.backend.domain.learn.repository.LearnRepository;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.storage.AssetUrlResolver;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class QuizServiceFlowTest {

  private static final Long USER_ID = 1L;
  private static final Long CHILD_ID = 10L;
  private static final Long CATEGORY_ID = 20L;
  private static final Long SESSION_ID = 100L;
  private static final Long QUESTION_ID = 1000L;
  private static final Long WORD_ID = 2000L;

  @Mock private QuizSessionRepository quizSessionRepository;
  @Mock private QuizQuestionRepository quizQuestionRepository;
  @Mock private QuizAnswerRepository quizAnswerRepository;
  @Mock private LearnRepository learnRepository;
  @Mock private ChildProfileRepository childProfileRepository;
  @Mock private ClovaSttClient clovaSttClient;
  @Mock private QuizGradingService quizGradingService;
  @Mock private AssetUrlResolver assetUrlResolver;

  private QuizService quizService;

  @BeforeEach
  void setUp() {
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
  }

  @Test
  @DisplayName("퀴즈 세션 생성 시 요청한 문제 수만큼 세션과 문제를 저장한다")
  void createSessionSavesSessionAndQuestions() {
    givenOwnedActiveChild();
    List<Learn> words = List.of(learn(1L, "사과"), learn(2L, "바나나"), learn(3L, "우유"));
    QuizSessionCreateRequest request = newCreateRequest(3);
    when(learnRepository.findRandomQuizWordsByCategoryAndDifficulty(
            CATEGORY_ID, LearnDifficulty.NORMAL.name(), 3))
        .thenReturn(words);
    when(quizSessionRepository.save(any(QuizSession.class)))
        .thenAnswer(
            invocation -> {
              QuizSession session = invocation.getArgument(0);
              setId(session, SESSION_ID);
              return session;
            });

    QuizSessionCreateResponse response = quizService.createSession(USER_ID, request);

    assertThat(response.sessionId()).isEqualTo(SESSION_ID);
    assertThat(response.categoryId()).isEqualTo(CATEGORY_ID);
    assertThat(response.difficulty()).isEqualTo(LearnDifficulty.NORMAL);
    assertThat(response.totalQuestionCount()).isEqualTo(3);
    assertThat(response.currentQuestionNumber()).isEqualTo(1);
    assertThat(response.status()).isEqualTo(QuizSessionStatus.IN_PROGRESS);

    ArgumentCaptor<QuizQuestion> questionCaptor = ArgumentCaptor.forClass(QuizQuestion.class);
    verify(quizQuestionRepository, times(3)).save(questionCaptor.capture());
    List<QuizQuestion> questions = questionCaptor.getAllValues();
    assertThat(questions).extracting(QuizQuestion::getWord).containsExactlyElementsOf(words);
    assertThat(questions).extracting(QuizQuestion::getQuestionNumber).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("요청한 문제 수보다 단어가 부족하면 퀴즈 세션을 생성하지 않는다")
  void createSessionRejectsNotEnoughWords() {
    givenOwnedActiveChild();
    QuizSessionCreateRequest request = newCreateRequest(5);
    when(learnRepository.findRandomQuizWordsByCategoryAndDifficulty(
            CATEGORY_ID, LearnDifficulty.NORMAL.name(), 5))
        .thenReturn(List.of(learn(1L, "사과"), learn(2L, "바나나"), learn(3L, "우유")));

    assertBusinessError(
        () -> quizService.createSession(USER_ID, request), QuizErrorCode.NOT_ENOUGH_WORDS);
    verify(quizSessionRepository, never()).save(any());
    verify(quizQuestionRepository, never()).save(any());
  }

  @Test
  @DisplayName("현재 문제 조회 시 아직 풀지 않은 첫 문제를 반환한다")
  void getCurrentQuestionReturnsFirstUnansweredQuestion() {
    QuizSession session = quizSession(3);
    QuizQuestion question = question(session, learn(WORD_ID, "사과"), 1);
    setId(question, QUESTION_ID);
    givenOwnedSession(session);
    when(quizQuestionRepository.findFirstBySessionIdAndAnsweredFalseOrderByQuestionNumberAsc(
            SESSION_ID))
        .thenReturn(Optional.of(question));
    when(assetUrlResolver.toUrl("/images/apple.png")).thenReturn("https://cdn.test/apple.png");

    QuizCurrentQuestionResponse response = quizService.getCurrentQuestion(USER_ID, SESSION_ID);

    assertThat(response.sessionId()).isEqualTo(SESSION_ID);
    assertThat(response.questionId()).isEqualTo(QUESTION_ID);
    assertThat(response.wordId()).isEqualTo(WORD_ID);
    assertThat(response.questionNumber()).isEqualTo(1);
    assertThat(response.totalQuestionCount()).isEqualTo(3);
    assertThat(response.targetText()).isEqualTo("사과");
    assertThat(response.imageUrl()).isEqualTo("https://cdn.test/apple.png");
  }

  @Test
  @DisplayName("완료된 세션에는 답변을 제출할 수 없다")
  void submitAnswerRejectsCompletedSession() {
    QuizSession session = quizSession(1);
    session.complete();
    givenOwnedSession(session);

    assertBusinessError(
        () -> quizService.submitAnswer(USER_ID, SESSION_ID, QUESTION_ID, audioFile()),
        QuizErrorCode.SESSION_ALREADY_COMPLETED);
    verifyNoInteractions(quizQuestionRepository, quizAnswerRepository, clovaSttClient);
  }

  @Test
  @DisplayName("다른 세션의 문제에는 답변을 제출할 수 없다")
  void submitAnswerRejectsQuestionFromOtherSession() {
    QuizSession session = quizSession(1);
    QuizSession otherSession = quizSession(1);
    setId(otherSession, SESSION_ID + 1);
    QuizQuestion question = question(otherSession, learn(WORD_ID, "사과"), 1);
    setId(question, QUESTION_ID);
    givenOwnedSession(session);
    when(quizQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));

    assertBusinessError(
        () -> quizService.submitAnswer(USER_ID, SESSION_ID, QUESTION_ID, audioFile()),
        QuizErrorCode.QUESTION_NOT_IN_SESSION);
    verifyNoInteractions(clovaSttClient, quizGradingService);
  }

  @Test
  @DisplayName("이미 답변한 문제에는 다시 답변을 제출할 수 없다")
  void submitAnswerRejectsAlreadyAnsweredQuestion() {
    QuizSession session = quizSession(1);
    QuizQuestion question = question(session, learn(WORD_ID, "사과"), 1);
    question.markAnswered();
    setId(question, QUESTION_ID);
    givenOwnedSession(session);
    when(quizQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));

    assertBusinessError(
        () -> quizService.submitAnswer(USER_ID, SESSION_ID, QUESTION_ID, audioFile()),
        QuizErrorCode.QUESTION_ALREADY_ANSWERED);
    verifyNoInteractions(clovaSttClient, quizGradingService);
  }

  @Test
  @DisplayName("현재 풀 차례가 아닌 문제에는 답변을 제출할 수 없다")
  void submitAnswerRejectsNotCurrentQuestion() {
    QuizSession session = quizSession(2);
    QuizQuestion submittedQuestion = question(session, learn(WORD_ID, "바나나"), 2);
    QuizQuestion currentQuestion = question(session, learn(WORD_ID + 1, "사과"), 1);
    setId(submittedQuestion, QUESTION_ID);
    setId(currentQuestion, QUESTION_ID + 1);
    givenOwnedSession(session);
    when(quizQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(submittedQuestion));
    when(quizAnswerRepository.existsByQuestionId(QUESTION_ID)).thenReturn(false);
    when(quizQuestionRepository.findFirstBySessionIdAndAnsweredFalseOrderByQuestionNumberAsc(
            SESSION_ID))
        .thenReturn(Optional.of(currentQuestion));

    assertBusinessError(
        () -> quizService.submitAnswer(USER_ID, SESSION_ID, QUESTION_ID, audioFile()),
        QuizErrorCode.NOT_CURRENT_QUESTION);
    verifyNoInteractions(clovaSttClient, quizGradingService);
  }

  @Test
  @DisplayName("답변 제출 성공 시 답안 저장 후 다음 문제 번호를 반환한다")
  void submitAnswerSavesAnswerAndReturnsNextQuestion() {
    QuizSession session = quizSession(2);
    QuizQuestion question = question(session, learn(WORD_ID, "사과"), 1);
    QuizQuestion nextQuestion = question(session, learn(WORD_ID + 1, "바나나"), 2);
    setId(question, QUESTION_ID);
    setId(nextQuestion, QUESTION_ID + 1);
    givenOwnedSession(session);
    when(quizQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
    when(quizAnswerRepository.existsByQuestionId(QUESTION_ID)).thenReturn(false);
    when(quizQuestionRepository.findFirstBySessionIdAndAnsweredFalseOrderByQuestionNumberAsc(
            SESSION_ID))
        .thenReturn(Optional.of(question), Optional.of(nextQuestion));
    when(clovaSttClient.transcribe(any(MultipartFile.class), eq("ko-KR"), eq("audio/wav")))
        .thenReturn("사과");
    when(quizGradingService.grade("사과", "사과"))
        .thenReturn(new QuizGrade(true, 3, "정확하게 말했어요!", "완전 일치", 1.0));
    when(quizQuestionRepository.existsBySessionIdAndAnsweredFalse(SESSION_ID)).thenReturn(true);

    QuizAnswerResponse response =
        quizService.submitAnswer(USER_ID, SESSION_ID, QUESTION_ID, audioFile());

    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextQuestionNumber()).isEqualTo(2);
    assertThat(response.isCorrect()).isTrue();
    assertThat(response.star()).isEqualTo(3);
    assertThat(question.isAnswered()).isTrue();
    assertThat(session.getCorrectCount()).isEqualTo(1);
    assertThat(session.getTotalStar()).isEqualTo(3);
    assertThat(session.getStatus()).isEqualTo(QuizSessionStatus.IN_PROGRESS);

    verify(quizGradingService).grade("사과", "사과");
    ArgumentCaptor<QuizAnswer> answerCaptor = ArgumentCaptor.forClass(QuizAnswer.class);
    verify(quizAnswerRepository).save(answerCaptor.capture());
    assertThat(answerCaptor.getValue().getTargetText()).isEqualTo("사과");
    assertThat(answerCaptor.getValue().getRecognizedText()).isEqualTo("사과");
    assertThat(answerCaptor.getValue().isCorrect()).isTrue();
  }

  @Test
  @DisplayName("마지막 문제 답변 제출 시 세션을 완료한다")
  void submitAnswerCompletesSessionAfterLastQuestion() {
    QuizSession session = quizSession(1);
    QuizQuestion question = question(session, learn(WORD_ID, "사과"), 1);
    setId(question, QUESTION_ID);
    givenOwnedSession(session);
    when(quizQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
    when(quizAnswerRepository.existsByQuestionId(QUESTION_ID)).thenReturn(false);
    when(quizQuestionRepository.findFirstBySessionIdAndAnsweredFalseOrderByQuestionNumberAsc(
            SESSION_ID))
        .thenReturn(Optional.of(question));
    when(clovaSttClient.transcribe(any(MultipartFile.class), eq("ko-KR"), eq("audio/wav")))
        .thenReturn("사과");
    when(quizGradingService.grade("사과", "사과"))
        .thenReturn(new QuizGrade(true, 3, "정확하게 말했어요!", "완전 일치", 1.0));
    when(quizQuestionRepository.existsBySessionIdAndAnsweredFalse(SESSION_ID)).thenReturn(false);

    QuizAnswerResponse response =
        quizService.submitAnswer(USER_ID, SESSION_ID, QUESTION_ID, audioFile());

    assertThat(response.hasNext()).isFalse();
    assertThat(response.nextQuestionNumber()).isNull();
    assertThat(session.getStatus()).isEqualTo(QuizSessionStatus.COMPLETED);
    assertThat(session.getEndedAt()).isNotNull();
    verify(quizGradingService).grade("사과", "사과");
  }

  @Test
  @DisplayName("음성 인식 실패 시 답변 제출을 실패 처리한다")
  void submitAnswerRejectsSttFailure() {
    QuizSession session = quizSession(1);
    QuizQuestion question = question(session, learn(WORD_ID, "사과"), 1);
    setId(question, QUESTION_ID);
    givenOwnedSession(session);
    when(quizQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
    when(quizAnswerRepository.existsByQuestionId(QUESTION_ID)).thenReturn(false);
    when(quizQuestionRepository.findFirstBySessionIdAndAnsweredFalseOrderByQuestionNumberAsc(
            SESSION_ID))
        .thenReturn(Optional.of(question));
    when(clovaSttClient.transcribe(any(MultipartFile.class), eq("ko-KR"), eq("audio/wav")))
        .thenThrow(new RuntimeException("stt failed"));

    assertBusinessError(
        () -> quizService.submitAnswer(USER_ID, SESSION_ID, QUESTION_ID, audioFile()),
        QuizErrorCode.STT_FAILED);
    verify(quizAnswerRepository, never()).save(any());
    verifyNoInteractions(quizGradingService);
  }

  @Test
  @DisplayName("완료되지 않은 세션의 결과는 조회할 수 없다")
  void getResultRejectsInProgressSession() {
    QuizSession session = quizSession(1);
    givenOwnedSession(session);

    assertBusinessError(
        () -> quizService.getResult(USER_ID, SESSION_ID), QuizErrorCode.SESSION_NOT_COMPLETED);
    verifyNoInteractions(quizAnswerRepository);
  }

  @Test
  @DisplayName("완료된 세션의 결과와 답변 목록을 반환한다")
  void getResultReturnsCompletedSessionAnswers() {
    QuizSession session = quizSession(1);
    session.increaseCorrectCount();
    session.addStar(3);
    session.complete();
    QuizQuestion question = question(session, learn(WORD_ID, "사과"), 1);
    setId(question, QUESTION_ID);
    QuizAnswer answer =
        QuizAnswer.create(
            session, question, question.getWord(), "사과", "사과", true, 3, "정확해요", "완전 일치", 1.0);
    givenOwnedSession(session);
    when(quizAnswerRepository.findBySessionIdOrderByQuestionQuestionNumberAsc(SESSION_ID))
        .thenReturn(List.of(answer));

    QuizResultResponse response = quizService.getResult(USER_ID, SESSION_ID);

    assertThat(response.sessionId()).isEqualTo(SESSION_ID);
    assertThat(response.childProfileId()).isEqualTo(CHILD_ID);
    assertThat(response.correctCount()).isEqualTo(1);
    assertThat(response.totalStar()).isEqualTo(3);
    assertThat(response.answers()).hasSize(1);
    assertThat(response.answers().get(0).questionId()).isEqualTo(QUESTION_ID);
    assertThat(response.answers().get(0).recognizedText()).isEqualTo("사과");
  }

  @Test
  @DisplayName("진행 중인 세션 종료 시 완료 상태로 변경한다")
  void endSessionCompletesInProgressSession() {
    QuizSession session = quizSession(1);
    givenOwnedSession(session);

    QuizSessionEndResponse response = quizService.endSession(USER_ID, SESSION_ID);

    assertThat(response.sessionId()).isEqualTo(SESSION_ID);
    assertThat(response.status()).isEqualTo(QuizSessionStatus.COMPLETED);
    assertThat(response.endedAt()).isNotNull();
    assertThat(session.getStatus()).isEqualTo(QuizSessionStatus.COMPLETED);
  }

  private void givenOwnedActiveChild() {
    when(childProfileRepository.findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID))
        .thenReturn(Optional.of(activeChild()));
  }

  private void givenOwnedSession(QuizSession session) {
    when(quizSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    givenOwnedActiveChild();
  }

  private QuizSessionCreateRequest newCreateRequest(int totalQuestionCount) {
    return new QuizSessionCreateRequest(
        CHILD_ID, CATEGORY_ID, LearnDifficulty.NORMAL, totalQuestionCount);
  }

  private QuizSession quizSession(int totalQuestionCount) {
    QuizSession session =
        QuizSession.create(CHILD_ID, CATEGORY_ID, LearnDifficulty.NORMAL, totalQuestionCount);
    setId(session, SESSION_ID);
    return session;
  }

  private QuizQuestion question(QuizSession session, Learn word, int questionNumber) {
    return QuizQuestion.create(session, word, questionNumber);
  }

  private Learn learn(Long id, String displayText) {
    Learn learn = newInstance(Learn.class);
    setId(learn, id);
    ReflectionTestUtils.setField(learn, "difficulty", LearnDifficulty.NORMAL);
    ReflectionTestUtils.setField(learn, "word", displayText);
    ReflectionTestUtils.setField(learn, "displayText", displayText);
    ReflectionTestUtils.setField(learn, "imageUrl", "/images/apple.png");
    ReflectionTestUtils.setField(learn, "audioUrl", "/audios/apple.mp3");
    ReflectionTestUtils.setField(learn, "active", true);
    ReflectionTestUtils.setField(learn, "sortOrder", 1);
    return learn;
  }

  private <T> T newInstance(Class<T> type) {
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("테스트 엔티티를 생성할 수 없습니다.", exception);
    }
  }

  private ChildProfile activeChild() {
    return ChildProfile.create(
        User.create("guardian@example.com", "encoded-password", "보호자"),
        "민준",
        LocalDate.now().minusYears(6));
  }

  private MockMultipartFile audioFile() {
    return new MockMultipartFile("audioFile", "answer.wav", "audio/wav", "fake-audio".getBytes());
  }

  private void setId(Object target, Long id) {
    ReflectionTestUtils.setField(target, "id", id);
  }

  private void assertBusinessError(ThrowingCall call, QuizErrorCode expectedErrorCode) {
    assertThatThrownBy(call::invoke)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void invoke();
  }
}
