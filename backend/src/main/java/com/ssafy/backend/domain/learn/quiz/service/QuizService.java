package com.ssafy.backend.domain.learn.quiz.service;

import com.ssafy.backend.domain.child.exception.ChildProfileErrorCode;
import com.ssafy.backend.domain.child.repository.ChildProfileRepository;
import com.ssafy.backend.domain.comms.service.ClovaSttClient;
import com.ssafy.backend.domain.learn.entity.Learn;
import com.ssafy.backend.domain.learn.quiz.dto.request.QuizSessionCreateRequest;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizAnswerResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizCurrentQuestionResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizResultResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizSessionCreateResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizSessionEndResponse;
import com.ssafy.backend.domain.learn.quiz.entity.QuizAnswer;
import com.ssafy.backend.domain.learn.quiz.entity.QuizQuestion;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSession;
import com.ssafy.backend.domain.learn.quiz.exception.QuizErrorCode;
import com.ssafy.backend.domain.learn.quiz.repository.QuizAnswerRepository;
import com.ssafy.backend.domain.learn.quiz.repository.QuizQuestionRepository;
import com.ssafy.backend.domain.learn.quiz.repository.QuizSessionRepository;
import com.ssafy.backend.domain.learn.repository.LearnRepository;
import com.ssafy.backend.global.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuizService {

  private static final String DEFAULT_LOCALE = "ko-KR";
  private static final String DEFAULT_AUDIO_MIME_TYPE = "audio/mpeg";

  private final QuizSessionRepository quizSessionRepository;
  private final QuizQuestionRepository quizQuestionRepository;
  private final QuizAnswerRepository quizAnswerRepository;
  private final LearnRepository learnRepository;
  private final ChildProfileRepository childProfileRepository;
  private final ClovaSttClient clovaSttClient;
  private final QuizGradingService quizGradingService;

  public QuizService(
      QuizSessionRepository quizSessionRepository,
      QuizQuestionRepository quizQuestionRepository,
      QuizAnswerRepository quizAnswerRepository,
      LearnRepository learnRepository,
      ChildProfileRepository childProfileRepository,
      ClovaSttClient clovaSttClient,
      QuizGradingService quizGradingService) {
    this.quizSessionRepository = quizSessionRepository;
    this.quizQuestionRepository = quizQuestionRepository;
    this.quizAnswerRepository = quizAnswerRepository;
    this.learnRepository = learnRepository;
    this.childProfileRepository = childProfileRepository;
    this.clovaSttClient = clovaSttClient;
    this.quizGradingService = quizGradingService;
  }

  @Transactional
  public QuizSessionCreateResponse createSession(Long userId, QuizSessionCreateRequest request) {
    validateChildProfileOwner(request.childProfileId(), userId);

    List<Learn> words =
        learnRepository.findRandomQuizWordsByCategoryAndDifficulty(
            request.categoryId(), request.difficulty().name(), request.totalQuestionCount());

    if (words.size() < request.totalQuestionCount()) {
      throw new BusinessException(QuizErrorCode.NOT_ENOUGH_WORDS);
    }

    QuizSession session =
        QuizSession.create(
            request.childProfileId(),
            request.categoryId(),
            request.difficulty(),
            request.totalQuestionCount());

    quizSessionRepository.save(session);

    for (int i = 0; i < words.size(); i++) {
      QuizQuestion question = QuizQuestion.create(session, words.get(i), i + 1);
      quizQuestionRepository.save(question);
    }

    return new QuizSessionCreateResponse(
        session.getId(),
        session.getCategoryId(),
        session.getDifficulty(),
        session.getTotalQuestionCount(),
        1,
        session.getStatus());
  }

  @Transactional(readOnly = true)
  public QuizCurrentQuestionResponse getCurrentQuestion(Long userId, Long sessionId) {
    QuizSession session = getOwnedSession(sessionId, userId);

    if (session.isCompleted()) {
      throw new BusinessException(QuizErrorCode.SESSION_ALREADY_COMPLETED);
    }

    QuizQuestion question =
        quizQuestionRepository
            .findFirstBySessionIdAndAnsweredFalseOrderByQuestionNumberAsc(sessionId)
            .orElseThrow(() -> new BusinessException(QuizErrorCode.NO_CURRENT_QUESTION));

    Learn word = question.getWord();

    return new QuizCurrentQuestionResponse(
        session.getId(),
        question.getId(),
        word.getId(),
        question.getQuestionNumber(),
        session.getTotalQuestionCount(),
        word.getImageUrl());
  }

  @Transactional
  public QuizAnswerResponse submitAnswer(
      Long userId, Long sessionId, Long questionId, MultipartFile audioFile) {
    QuizSession session = getOwnedSession(sessionId, userId);

    if (session.isCompleted()) {
      throw new BusinessException(QuizErrorCode.SESSION_ALREADY_COMPLETED);
    }

    QuizQuestion question =
        quizQuestionRepository
            .findById(questionId)
            .orElseThrow(() -> new BusinessException(QuizErrorCode.QUESTION_NOT_FOUND));

    validateQuestionBelongsToSession(question, sessionId);

    if (question.isAnswered() || quizAnswerRepository.existsByQuestionId(questionId)) {
      throw new BusinessException(QuizErrorCode.QUESTION_ALREADY_ANSWERED);
    }

    validateCurrentQuestion(question, sessionId);

    String recognizedText = transcribe(audioFile);

    Learn word = question.getWord();
    String targetText = word.getDisplayText();

    QuizGrade grade = quizGradingService.grade(targetText, recognizedText);

    QuizAnswer answer =
        QuizAnswer.create(
            session,
            question,
            word,
            targetText,
            recognizedText,
            grade.isCorrect(),
            grade.star(),
            grade.feedback(),
            grade.reason(),
            grade.confidence());

    quizAnswerRepository.save(answer);

    question.markAnswered();

    if (grade.isCorrect()) {
      session.increaseCorrectCount();
    }

    session.addStar(grade.star());

    boolean hasNext = quizQuestionRepository.existsBySessionIdAndAnsweredFalse(sessionId);

    Integer nextQuestionNumber = hasNext ? getNextQuestionNumber(sessionId) : null;

    if (!hasNext) {
      session.complete();
    }

    return new QuizAnswerResponse(
        session.getId(),
        question.getId(),
        word.getId(),
        targetText,
        recognizedText,
        grade.isCorrect(),
        grade.star(),
        grade.feedback(),
        hasNext,
        nextQuestionNumber);
  }

  @Transactional(readOnly = true)
  public QuizResultResponse getResult(Long userId, Long sessionId) {
    QuizSession session = getOwnedSession(sessionId, userId);

    List<QuizResultResponse.AnswerItem> answers =
        quizAnswerRepository.findBySessionIdOrderByQuestionQuestionNumberAsc(sessionId).stream()
            .map(
                answer ->
                    new QuizResultResponse.AnswerItem(
                        answer.getQuestion().getId(),
                        answer.getWord().getId(),
                        answer.getTargetText(),
                        answer.getRecognizedText(),
                        answer.isCorrect(),
                        answer.getStar(),
                        answer.getFeedback()))
            .toList();

    return new QuizResultResponse(
        session.getId(),
        session.getTotalQuestionCount(),
        session.getCorrectCount(),
        session.getTotalStar(),
        answers);
  }

  @Transactional
  public QuizSessionEndResponse endSession(Long userId, Long sessionId) {
    QuizSession session = getOwnedSession(sessionId, userId);

    if (!session.isCompleted()) {
      session.complete();
    }

    return new QuizSessionEndResponse(session.getId(), session.getStatus(), session.getEndedAt());
  }

  private QuizSession getSession(Long sessionId) {
    return quizSessionRepository
        .findById(sessionId)
        .orElseThrow(() -> new BusinessException(QuizErrorCode.SESSION_NOT_FOUND));
  }

  private QuizSession getOwnedSession(Long sessionId, Long userId) {
    QuizSession session = getSession(sessionId);
    validateChildProfileOwner(session.getChildProfileId(), userId);
    return session;
  }

  private void validateChildProfileOwner(Long childProfileId, Long userId) {
    childProfileRepository
        .findByIdAndUserIdAndActiveTrue(childProfileId, userId)
        .orElseThrow(() -> new BusinessException(ChildProfileErrorCode.NOT_FOUND));
  }

  private void validateQuestionBelongsToSession(QuizQuestion question, Long sessionId) {
    if (!question.getSession().getId().equals(sessionId)) {
      throw new BusinessException(QuizErrorCode.QUESTION_NOT_IN_SESSION);
    }
  }

  private void validateCurrentQuestion(QuizQuestion submittedQuestion, Long sessionId) {
    QuizQuestion currentQuestion =
        quizQuestionRepository
            .findFirstBySessionIdAndAnsweredFalseOrderByQuestionNumberAsc(sessionId)
            .orElseThrow(() -> new BusinessException(QuizErrorCode.NO_CURRENT_QUESTION));

    if (!currentQuestion.getId().equals(submittedQuestion.getId())) {
      throw new BusinessException(QuizErrorCode.NOT_CURRENT_QUESTION);
    }
  }

  private String transcribe(MultipartFile audioFile) {
    try {
      String audioMimeType = resolveAudioMimeType(audioFile);

      return clovaSttClient.transcribe(audioFile, DEFAULT_LOCALE, audioMimeType);
    } catch (RuntimeException e) {
      throw new BusinessException(QuizErrorCode.STT_FAILED);
    }
  }

  private String resolveAudioMimeType(MultipartFile audioFile) {
    String contentType = audioFile.getContentType();

    if (contentType != null
        && !contentType.isBlank()
        && !"application/octet-stream".equals(contentType)) {
      return contentType;
    }

    return guessMimeType(audioFile.getOriginalFilename());
  }

  private String guessMimeType(String filename) {
    if (filename == null || filename.isBlank()) {
      return DEFAULT_AUDIO_MIME_TYPE;
    }

    String lowerFilename = filename.toLowerCase();

    if (lowerFilename.endsWith(".mp3")) {
      return "audio/mpeg";
    }

    if (lowerFilename.endsWith(".wav")) {
      return "audio/wav";
    }

    if (lowerFilename.endsWith(".m4a")) {
      return "audio/mp4";
    }

    if (lowerFilename.endsWith(".webm")) {
      return "audio/webm";
    }

    if (lowerFilename.endsWith(".ogg")) {
      return "audio/ogg";
    }

    return DEFAULT_AUDIO_MIME_TYPE;
  }

  private Integer getNextQuestionNumber(Long sessionId) {
    return quizQuestionRepository
        .findFirstBySessionIdAndAnsweredFalseOrderByQuestionNumberAsc(sessionId)
        .map(QuizQuestion::getQuestionNumber)
        .orElse(null);
  }
}
