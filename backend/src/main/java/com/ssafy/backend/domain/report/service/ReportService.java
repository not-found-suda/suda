package com.ssafy.backend.domain.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.domain.child.exception.ChildProfileErrorCode;
import com.ssafy.backend.domain.child.repository.ChildProfileRepository;
import com.ssafy.backend.domain.comms.entity.CommunicationAnalysisStatus;
import com.ssafy.backend.domain.comms.repository.CommunicationSessionAnalysisRepository;
import com.ssafy.backend.domain.report.dto.ReportCategoryListResponse;
import com.ssafy.backend.domain.report.dto.ReportCategoryResponse;
import com.ssafy.backend.domain.report.dto.ReportCommunicationSessionSummaryResponse;
import com.ssafy.backend.domain.report.dto.ReportCommunicationSummaryResponse;
import com.ssafy.backend.domain.report.dto.ReportExpressionTypeCountsResponse;
import com.ssafy.backend.domain.report.dto.ReportLatestCategoryResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizAnswerResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionDetailResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionListResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionSearchCondition;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionSummaryResponse;
import com.ssafy.backend.domain.report.dto.ReportSummaryResponse;
import com.ssafy.backend.domain.report.dto.ReportWeakWordListResponse;
import com.ssafy.backend.domain.report.dto.ReportWeakWordResponse;
import com.ssafy.backend.domain.report.dto.ReportWeakWordSearchCondition;
import com.ssafy.backend.domain.report.dto.ReportWordCountResponse;
import com.ssafy.backend.domain.report.exception.ReportErrorCode;
import com.ssafy.backend.domain.report.repository.ReportCategoryQueryRow;
import com.ssafy.backend.domain.report.repository.ReportCommunicationAnalysisQueryRow;
import com.ssafy.backend.domain.report.repository.ReportLatestCategoryQueryRow;
import com.ssafy.backend.domain.report.repository.ReportQuizAnswerQueryRow;
import com.ssafy.backend.domain.report.repository.ReportQuizSessionQueryRepository;
import com.ssafy.backend.domain.report.repository.ReportQuizSessionQueryRow;
import com.ssafy.backend.domain.report.repository.ReportSummaryAggregateRow;
import com.ssafy.backend.domain.report.repository.ReportWeakWordQueryRow;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.ValidationErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_MIN_ATTEMPT_COUNT = 1;
  private static final int SUMMARY_WEAK_WORD_LIMIT = 5;
  private static final int MAX_COMMUNICATION_SESSION_LIMIT = 50;
  private static final int REPORT_GUIDE_LIMIT = 5;
  private static final int MAX_SIZE = 100;

  private final ChildProfileRepository childProfileRepository;
  private final ReportQuizSessionQueryRepository reportQuizSessionQueryRepository;
  private final CommunicationSessionAnalysisRepository communicationSessionAnalysisRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ReportService(
      ChildProfileRepository childProfileRepository,
      ReportQuizSessionQueryRepository reportQuizSessionQueryRepository,
      CommunicationSessionAnalysisRepository communicationSessionAnalysisRepository) {
    this.childProfileRepository = childProfileRepository;
    this.reportQuizSessionQueryRepository = reportQuizSessionQueryRepository;
    this.communicationSessionAnalysisRepository = communicationSessionAnalysisRepository;
  }

  public ReportSummaryResponse getSummary(Long userId, Long childId, String from, String to) {
    validatePositiveId(childId, "childId");
    validateChildProfileOwner(childId, userId);

    LocalDateTime fromDateTime = parseFrom(from);
    LocalDateTime toDateTime = parseToExclusive(to);
    validateDateRange(fromDateTime, toDateTime);

    ReportSummaryAggregateRow summary =
        reportQuizSessionQueryRepository.summarize(childId, fromDateTime, toDateTime);
    ReportLatestCategoryQueryRow latestCategory =
        reportQuizSessionQueryRepository
            .findLatestCategory(childId, fromDateTime, toDateTime)
            .orElse(null);

    PageRequest weakWordLimit = PageRequest.of(0, SUMMARY_WEAK_WORD_LIMIT);
    List<ReportWeakWordResponse> weakWords =
        reportQuizSessionQueryRepository
            .findWeakWords(
                childId, fromDateTime, toDateTime, null, DEFAULT_MIN_ATTEMPT_COUNT, weakWordLimit)
            .getContent()
            .stream()
            .map(this::toWeakWordResponse)
            .toList();

    return new ReportSummaryResponse(
        childId,
        summary.completedSessionCount(),
        summary.totalQuestionCount(),
        summary.totalCorrectCount(),
        calculateAccuracyRate(summary.totalCorrectCount(), summary.totalQuestionCount()),
        calculateAverageStar(summary.totalStar(), summary.totalQuestionCount()),
        latestCategory != null ? latestCategory.latestSessionAt() : null,
        latestCategory != null
            ? new ReportLatestCategoryResponse(
                latestCategory.categoryId(), latestCategory.categoryName())
            : null,
        weakWords);
  }

  public ReportCommunicationSummaryResponse getCommunicationSummary(
      Long userId, Long childId, String from, String to, int sessionLimit) {
    validatePositiveId(childId, "childId");
    validateChildProfileOwner(childId, userId);
    int resolvedSessionLimit = resolveCommunicationSessionLimit(sessionLimit);

    LocalDateTime fromDateTime = parseFrom(from);
    LocalDateTime toDateTime = parseToExclusive(to);
    validateDateRange(fromDateTime, toDateTime);

    if (fromDateTime == null) {
      fromDateTime = LocalDateTime.of(1900, 1, 1, 0, 0);
    }

    if (toDateTime == null) {
      toDateTime = LocalDateTime.of(9999, 12, 31, 23, 59, 59);
    }

    List<ReportCommunicationAnalysisQueryRow> rows =
        communicationSessionAnalysisRepository.findCommunicationAnalysisRows(
            childId, fromDateTime, toDateTime);

    long totalSessionCount = rows.size();
    long totalUtteranceCount = 0;
    double sentenceLengthSum = 0.0;
    long completedCount = 0;

    int communicationLevelScoreSum = 0;
    int communicationLevelValidCount = 0;
    int vocabularyDiversityLevelScoreSum = 0;
    int vocabularyDiversityLevelValidCount = 0;
    int sentenceExpansionLevelScoreSum = 0;
    int sentenceExpansionLevelValidCount = 0;

    Map<String, Integer> wordCounts = new LinkedHashMap<>();
    int requestCount = 0;
    int emotionCount = 0;
    int responseCount = 0;
    int playCount = 0;
    int questionCount = 0;
    int otherCount = 0;

    String developmentReference = "";
    String cautionLevel = "NONE";
    String consultationGuide = "";

    List<String> strengths = new ArrayList<>();
    List<String> improvementPoints = new ArrayList<>();
    List<String> parentGuide = new ArrayList<>();
    List<String> recommendedActivities = new ArrayList<>();
    List<ReportCommunicationSessionSummaryResponse> recentSessions = new ArrayList<>();

    for (ReportCommunicationAnalysisQueryRow row : rows) {
      JsonNode summary = parseSummaryJson(row.summaryJson());

      int utteranceCount = readInt(summary, "utteranceCount");
      double averageSentenceLength = readDouble(summary, "averageSentenceLength");

      String sessionCommunicationLevel = readLevel(summary, "communicationLevel");
      String sessionVocabularyDiversityLevel = readLevel(summary, "vocabularyDiversityLevel");
      String sessionSentenceExpansionLevel = readLevel(summary, "sentenceExpansionLevel");
      String sessionDevelopmentReference = readText(summary, "developmentReference");
      String sessionCautionLevel = readCautionLevel(summary, "cautionLevel");
      String sessionConsultationGuide = readText(summary, "consultationGuide");
      String sessionSummary = readText(summary, "summary");

      JsonNode expressionTypeCounts = summary.path("expressionTypeCounts");
      ReportExpressionTypeCountsResponse sessionExpressionTypeCounts =
          new ReportExpressionTypeCountsResponse(
              readInt(expressionTypeCounts, "REQUEST"),
              readInt(expressionTypeCounts, "EMOTION"),
              readInt(expressionTypeCounts, "RESPONSE"),
              readInt(expressionTypeCounts, "PLAY"),
              readInt(expressionTypeCounts, "QUESTION"),
              readInt(expressionTypeCounts, "OTHER"));

      List<String> sessionStrengths = readStringList(summary, "strengths");
      List<String> sessionImprovementPoints = readStringList(summary, "improvementPoints");
      List<String> sessionParentGuide = readStringList(summary, "parentGuide");
      List<String> sessionRecommendedActivities = readStringList(summary, "recommendedActivities");

      if (row.analysisStatus() == CommunicationAnalysisStatus.COMPLETED) {
        completedCount++;
        totalUtteranceCount += utteranceCount;
        sentenceLengthSum += averageSentenceLength;

        mergeFrequentWords(wordCounts, summary.path("frequentWords"));

        requestCount += sessionExpressionTypeCounts.request();
        emotionCount += sessionExpressionTypeCounts.emotion();
        responseCount += sessionExpressionTypeCounts.response();
        playCount += sessionExpressionTypeCounts.play();
        questionCount += sessionExpressionTypeCounts.question();
        otherCount += sessionExpressionTypeCounts.other();

        if (isValidLevel(sessionCommunicationLevel)) {
          communicationLevelScoreSum += levelScore(sessionCommunicationLevel);
          communicationLevelValidCount++;
        }

        if (isValidLevel(sessionVocabularyDiversityLevel)) {
          vocabularyDiversityLevelScoreSum += levelScore(sessionVocabularyDiversityLevel);
          vocabularyDiversityLevelValidCount++;
        }

        if (isValidLevel(sessionSentenceExpansionLevel)) {
          sentenceExpansionLevelScoreSum += levelScore(sessionSentenceExpansionLevel);
          sentenceExpansionLevelValidCount++;
        }

        if (developmentReference.isBlank() && !sessionDevelopmentReference.isBlank()) {
          developmentReference = sessionDevelopmentReference;
        }

        if (consultationGuide.isBlank() && !sessionConsultationGuide.isBlank()) {
          consultationGuide = sessionConsultationGuide;
        }

        cautionLevel = higherCautionLevel(cautionLevel, sessionCautionLevel);

        addDistinctUntilLimit(strengths, sessionStrengths, REPORT_GUIDE_LIMIT);
        addDistinctUntilLimit(improvementPoints, sessionImprovementPoints, REPORT_GUIDE_LIMIT);
        addDistinctUntilLimit(parentGuide, sessionParentGuide, REPORT_GUIDE_LIMIT);
        addDistinctUntilLimit(
            recommendedActivities, sessionRecommendedActivities, REPORT_GUIDE_LIMIT);
      }

      if (recentSessions.size() < resolvedSessionLimit) {
        recentSessions.add(
            new ReportCommunicationSessionSummaryResponse(
                row.sessionId(),
                row.startedAt(),
                row.endedAt(),
                utteranceCount,
                averageSentenceLength,
                toTopWordResponses(summary.path("frequentWords"), 5),
                sessionExpressionTypeCounts,
                sessionCommunicationLevel,
                sessionVocabularyDiversityLevel,
                sessionSentenceExpansionLevel,
                sessionStrengths,
                sessionImprovementPoints,
                sessionParentGuide,
                sessionRecommendedActivities,
                sessionDevelopmentReference,
                sessionCautionLevel,
                sessionConsultationGuide,
                sessionSummary,
                row.analysisStatus(),
                row.analyzedAt()));
      }
    }

    CommunicationAnalysisStatus status = resolveOverallAnalysisStatus(rows);
    double averageSentenceLength =
        completedCount == 0 ? 0.0 : roundOneDecimal(sentenceLengthSum / completedCount);

    String communicationLevel =
        resolveAverageLevel(communicationLevelScoreSum, communicationLevelValidCount);
    String vocabularyDiversityLevel =
        resolveAverageLevel(vocabularyDiversityLevelScoreSum, vocabularyDiversityLevelValidCount);
    String sentenceExpansionLevel =
        resolveAverageLevel(sentenceExpansionLevelScoreSum, sentenceExpansionLevelValidCount);

    return new ReportCommunicationSummaryResponse(
        childId,
        status,
        totalSessionCount,
        totalUtteranceCount,
        averageSentenceLength,
        toTopWordResponses(wordCounts, 5),
        new ReportExpressionTypeCountsResponse(
            requestCount, emotionCount, responseCount, playCount, questionCount, otherCount),
        communicationLevel,
        vocabularyDiversityLevel,
        sentenceExpansionLevel,
        strengths,
        improvementPoints,
        parentGuide,
        recommendedActivities,
        developmentReference,
        cautionLevel,
        consultationGuide,
        recentSessions,
        LocalDateTime.now());
  }

  public ReportWeakWordListResponse getWeakWords(
      Long userId, Long childId, ReportWeakWordSearchCondition condition) {
    validatePositiveId(childId, "childId");
    validatePositiveId(condition.categoryId(), "categoryId");
    validateChildProfileOwner(childId, userId);

    LocalDateTime from = parseFrom(condition.from());
    LocalDateTime to = parseToExclusive(condition.to());
    validateDateRange(from, to);

    int minAttemptCount = resolveMinAttemptCount(condition.minAttemptCount());
    int page = resolvePage(condition.page());
    int size = resolveSize(condition.size());
    PageRequest pageRequest = PageRequest.of(page - 1, size);

    Page<ReportWeakWordQueryRow> rows =
        reportQuizSessionQueryRepository.findWeakWords(
            childId, from, to, condition.categoryId(), minAttemptCount, pageRequest);

    return new ReportWeakWordListResponse(
        rows.getContent().stream().map(this::toWeakWordResponse).toList(),
        page,
        size,
        rows.getTotalElements(),
        rows.getTotalPages());
  }

  public ReportCategoryListResponse getCategories(
      Long userId, Long childId, String from, String to) {
    validatePositiveId(childId, "childId");
    validateChildProfileOwner(childId, userId);

    LocalDateTime fromDateTime = parseFrom(from);
    LocalDateTime toDateTime = parseToExclusive(to);
    validateDateRange(fromDateTime, toDateTime);

    List<ReportCategoryResponse> categories =
        reportQuizSessionQueryRepository.findCategories(childId, fromDateTime, toDateTime).stream()
            .map(this::toCategoryResponse)
            .toList();

    return new ReportCategoryListResponse(categories);
  }

  public ReportQuizSessionListResponse getQuizSessions(
      Long userId, Long childId, ReportQuizSessionSearchCondition condition) {
    validatePositiveId(childId, "childId");
    validatePositiveId(condition.categoryId(), "categoryId");
    validateChildProfileOwner(childId, userId);

    LocalDateTime from = parseFrom(condition.from());
    LocalDateTime to = parseToExclusive(condition.to());
    validateDateRange(from, to);

    int page = resolvePage(condition.page());
    int size = resolveSize(condition.size());
    PageRequest pageRequest = PageRequest.of(page - 1, size);

    Page<ReportQuizSessionQueryRow> rows =
        reportQuizSessionQueryRepository.findSessions(
            childId,
            from,
            to,
            condition.categoryId(),
            condition.difficulty(),
            condition.status(),
            pageRequest);

    return new ReportQuizSessionListResponse(
        rows.getContent().stream().map(this::toSummaryResponse).toList(),
        page,
        size,
        rows.getTotalElements(),
        rows.getTotalPages());
  }

  public ReportQuizSessionDetailResponse getQuizSessionDetail(
      Long userId, Long childId, Long sessionId) {
    validatePositiveId(childId, "childId");
    validatePositiveId(sessionId, "sessionId");
    validateChildProfileOwner(childId, userId);

    ReportQuizSessionQueryRow session =
        reportQuizSessionQueryRepository
            .findSession(childId, sessionId)
            .orElseThrow(() -> new BusinessException(ReportErrorCode.NOT_FOUND));

    return toDetailResponse(session);
  }

  private void validateChildProfileOwner(Long childId, Long userId) {
    childProfileRepository
        .findByIdAndUserIdAndActiveTrue(childId, userId)
        .orElseThrow(() -> new BusinessException(ChildProfileErrorCode.NOT_FOUND));
  }

  private void validatePositiveId(Long id, String fieldName) {
    if (id != null && id < 1) {
      throw new BusinessException(ValidationErrorCode.INVALID_INPUT, fieldName + "는 1 이상이어야 합니다.");
    }
  }

  private ReportQuizSessionSummaryResponse toSummaryResponse(ReportQuizSessionQueryRow row) {
    return new ReportQuizSessionSummaryResponse(
        row.sessionId(),
        row.categoryId(),
        row.categoryName(),
        row.difficulty(),
        row.totalQuestionCount(),
        row.correctCount(),
        calculateAccuracyRate(row.correctCount(), row.totalQuestionCount()),
        calculateAverageStar(row.totalStar(), row.totalQuestionCount()),
        row.status(),
        row.startedAt(),
        row.endedAt());
  }

  private ReportQuizSessionDetailResponse toDetailResponse(ReportQuizSessionQueryRow row) {
    return new ReportQuizSessionDetailResponse(
        row.sessionId(),
        row.childId(),
        row.categoryId(),
        row.categoryName(),
        row.difficulty(),
        row.totalQuestionCount(),
        row.correctCount(),
        calculateAccuracyRate(row.correctCount(), row.totalQuestionCount()),
        row.totalStar(),
        calculateAverageStar(row.totalStar(), row.totalQuestionCount()),
        row.status(),
        row.startedAt(),
        row.endedAt(),
        reportQuizSessionQueryRepository.findAnswers(row.childId(), row.sessionId()).stream()
            .map(this::toAnswerResponse)
            .toList());
  }

  private ReportQuizAnswerResponse toAnswerResponse(ReportQuizAnswerQueryRow row) {
    return new ReportQuizAnswerResponse(
        row.questionId(),
        row.questionNumber(),
        row.wordId(),
        row.targetText(),
        row.recognizedText(),
        row.isCorrect(),
        row.star(),
        row.feedback(),
        row.answeredAt());
  }

  private ReportWeakWordResponse toWeakWordResponse(ReportWeakWordQueryRow row) {
    return new ReportWeakWordResponse(
        row.wordId(),
        row.word(),
        row.displayText(),
        row.categoryId(),
        row.categoryName(),
        row.attemptCount(),
        row.wrongCount(),
        calculateAccuracyRate(row.attemptCount() - row.wrongCount(), row.attemptCount()),
        roundOneDecimal(row.averageStar()),
        row.lastAnsweredAt());
  }

  private ReportCategoryResponse toCategoryResponse(ReportCategoryQueryRow row) {
    return new ReportCategoryResponse(
        row.categoryId(),
        row.categoryName(),
        row.totalWordCount(),
        row.quizzedWordCount(),
        row.correctWordCount(),
        calculateAccuracyRate(row.quizzedWordCount(), row.totalWordCount()),
        calculateAccuracyRate(row.correctWordCount(), row.totalWordCount()),
        row.completedSessionCount(),
        row.totalQuestionCount(),
        row.correctCount(),
        calculateAccuracyRate(row.correctCount(), row.totalQuestionCount()),
        calculateAverageStar(row.totalStar(), row.totalQuestionCount()),
        row.latestSessionAt());
  }

  private CommunicationAnalysisStatus resolveOverallAnalysisStatus(
      List<ReportCommunicationAnalysisQueryRow> rows) {
    if (rows.isEmpty()) {
      return CommunicationAnalysisStatus.EMPTY;
    }

    boolean hasProcessing =
        rows.stream()
            .anyMatch(
                row ->
                    row.analysisStatus() == CommunicationAnalysisStatus.PENDING
                        || row.analysisStatus() == CommunicationAnalysisStatus.PROCESSING);

    if (hasProcessing) {
      return CommunicationAnalysisStatus.PROCESSING;
    }

    boolean hasCompleted =
        rows.stream()
            .anyMatch(row -> row.analysisStatus() == CommunicationAnalysisStatus.COMPLETED);

    if (hasCompleted) {
      return CommunicationAnalysisStatus.COMPLETED;
    }

    boolean allEmpty =
        rows.stream().allMatch(row -> row.analysisStatus() == CommunicationAnalysisStatus.EMPTY);

    if (allEmpty) {
      return CommunicationAnalysisStatus.EMPTY;
    }

    return CommunicationAnalysisStatus.FAILED;
  }

  private JsonNode parseSummaryJson(String summaryJson) {
    if (summaryJson == null || summaryJson.isBlank()) {
      return objectMapper.createObjectNode();
    }

    try {
      return objectMapper.readTree(summaryJson);
    } catch (Exception e) {
      return objectMapper.createObjectNode();
    }
  }

  private int readInt(JsonNode node, String fieldName) {
    JsonNode value = node.path(fieldName);
    return value.isNumber() ? value.asInt() : 0;
  }

  private double readDouble(JsonNode node, String fieldName) {
    JsonNode value = node.path(fieldName);
    return value.isNumber() ? value.asDouble() : 0.0;
  }

  private String readText(JsonNode node, String fieldName) {
    JsonNode value = node.path(fieldName);
    return value.isTextual() ? value.asText() : "";
  }

  private String readLevel(JsonNode node, String fieldName) {
    String value = readText(node, fieldName);

    if (isValidLevel(value)) {
      return value;
    }

    return "UNKNOWN";
  }

  private boolean isValidLevel(String level) {
    return "LOW".equals(level) || "NORMAL".equals(level) || "HIGH".equals(level);
  }

  private int levelScore(String level) {
    return switch (level) {
      case "HIGH" -> 3;
      case "NORMAL" -> 2;
      case "LOW" -> 1;
      default -> 0;
    };
  }

  private String resolveAverageLevel(int scoreSum, int validCount) {
    if (validCount == 0) {
      return "UNKNOWN";
    }

    double averageScore = scoreSum * 1.0 / validCount;

    if (averageScore >= 2.5) {
      return "HIGH";
    }

    if (averageScore >= 1.5) {
      return "NORMAL";
    }

    return "LOW";
  }

  private String readCautionLevel(JsonNode node, String fieldName) {
    String value = readText(node, fieldName);

    if ("NONE".equals(value) || "WATCH".equals(value) || "CONSULT".equals(value)) {
      return value;
    }

    return "NONE";
  }

  private String higherCautionLevel(String current, String candidate) {
    return cautionScore(candidate) > cautionScore(current) ? candidate : current;
  }

  private int cautionScore(String cautionLevel) {
    return switch (cautionLevel) {
      case "CONSULT" -> 2;
      case "WATCH" -> 1;
      default -> 0;
    };
  }

  private List<String> readStringList(JsonNode node, String fieldName) {
    JsonNode values = node.path(fieldName);
    if (values == null || !values.isArray()) {
      return List.of();
    }

    List<String> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!value.isTextual()) {
        continue;
      }

      String text = value.asText();
      if (text == null || text.isBlank()) {
        continue;
      }

      result.add(text);
    }

    return result;
  }

  private void addDistinctUntilLimit(List<String> target, List<String> source, int limit) {
    for (String value : source) {
      if (target.size() >= limit) {
        return;
      }

      if (value == null || value.isBlank() || target.contains(value)) {
        continue;
      }

      target.add(value);
    }
  }

  private void mergeFrequentWords(Map<String, Integer> wordCounts, JsonNode frequentWords) {
    if (frequentWords == null || !frequentWords.isArray()) {
      return;
    }

    for (JsonNode wordNode : frequentWords) {
      String word = readText(wordNode, "word");
      int count = readInt(wordNode, "count");

      if (word == null || word.isBlank() || count <= 0) {
        continue;
      }

      wordCounts.merge(word, count, Integer::sum);
    }
  }

  private List<ReportWordCountResponse> toTopWordResponses(
      Map<String, Integer> wordCounts, int limit) {
    return wordCounts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
        .limit(limit)
        .map(entry -> new ReportWordCountResponse(entry.getKey(), entry.getValue()))
        .toList();
  }

  private List<ReportWordCountResponse> toTopWordResponses(JsonNode frequentWords, int limit) {
    if (frequentWords == null || !frequentWords.isArray()) {
      return List.of();
    }

    List<ReportWordCountResponse> words = new ArrayList<>();

    for (JsonNode wordNode : frequentWords) {
      String word = readText(wordNode, "word");
      int count = readInt(wordNode, "count");

      if (word == null || word.isBlank() || count <= 0) {
        continue;
      }

      words.add(new ReportWordCountResponse(word, count));
    }

    return words.stream()
        .sorted(Comparator.comparingInt(ReportWordCountResponse::count).reversed())
        .limit(limit)
        .toList();
  }

  private LocalDateTime parseFrom(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return parseDate(value).atStartOfDay();
  }

  private LocalDateTime parseToExclusive(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return parseDate(value).plusDays(1).atStartOfDay();
  }

  private LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException exception) {
      throw new BusinessException(ValidationErrorCode.INVALID_INPUT, "날짜는 YYYY-MM-DD 형식이어야 합니다.");
    }
  }

  private void validateDateRange(LocalDateTime from, LocalDateTime to) {
    if (from != null && to != null && !from.isBefore(to)) {
      throw new BusinessException(ValidationErrorCode.INVALID_INPUT, "조회 시작일은 종료일보다 늦을 수 없습니다.");
    }
  }

  private int resolvePage(int page) {
    if (page < DEFAULT_PAGE) {
      throw new BusinessException(ValidationErrorCode.INVALID_INPUT, "page는 1 이상이어야 합니다.");
    }
    return page;
  }

  private int resolveSize(int size) {
    if (size < 1 || size > MAX_SIZE) {
      throw new BusinessException(ValidationErrorCode.INVALID_INPUT, "size는 1 이상 100 이하여야 합니다.");
    }
    return size;
  }

  private int resolveCommunicationSessionLimit(int sessionLimit) {
    if (sessionLimit < 1 || sessionLimit > MAX_COMMUNICATION_SESSION_LIMIT) {
      throw new BusinessException(
          ValidationErrorCode.INVALID_INPUT, "sessionLimit은 1 이상 50 이하여야 합니다.");
    }
    return sessionLimit;
  }

  private int resolveMinAttemptCount(Integer minAttemptCount) {
    if (minAttemptCount == null) {
      return DEFAULT_MIN_ATTEMPT_COUNT;
    }

    if (minAttemptCount < 1) {
      throw new BusinessException(
          ValidationErrorCode.INVALID_INPUT, "minAttemptCount는 1 이상이어야 합니다.");
    }

    return minAttemptCount;
  }

  private double calculateAccuracyRate(Integer correctCount, Integer totalQuestionCount) {
    if (correctCount == null || totalQuestionCount == null || totalQuestionCount == 0) {
      return 0.0;
    }
    return roundOneDecimal(correctCount * 100.0 / totalQuestionCount);
  }

  private double calculateAverageStar(Integer totalStar, Integer totalQuestionCount) {
    if (totalStar == null || totalQuestionCount == null || totalQuestionCount == 0) {
      return 0.0;
    }
    return roundOneDecimal(totalStar * 1.0 / totalQuestionCount);
  }

  private double calculateAccuracyRate(Long correctCount, Long totalQuestionCount) {
    if (correctCount == null || totalQuestionCount == null || totalQuestionCount == 0) {
      return 0.0;
    }
    return roundOneDecimal(correctCount * 100.0 / totalQuestionCount);
  }

  private double calculateAverageStar(Long totalStar, Long totalQuestionCount) {
    if (totalStar == null || totalQuestionCount == null || totalQuestionCount == 0) {
      return 0.0;
    }
    return roundOneDecimal(totalStar * 1.0 / totalQuestionCount);
  }

  private double roundOneDecimal(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
