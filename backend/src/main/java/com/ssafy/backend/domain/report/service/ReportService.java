package com.ssafy.backend.domain.report.service;

import com.ssafy.backend.domain.child.exception.ChildProfileErrorCode;
import com.ssafy.backend.domain.child.repository.ChildProfileRepository;
import com.ssafy.backend.domain.report.dto.ReportQuizAnswerResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionDetailResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionListResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionSearchCondition;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionSummaryResponse;
import com.ssafy.backend.domain.report.exception.ReportErrorCode;
import com.ssafy.backend.domain.report.repository.ReportQuizAnswerQueryRow;
import com.ssafy.backend.domain.report.repository.ReportQuizSessionQueryRepository;
import com.ssafy.backend.domain.report.repository.ReportQuizSessionQueryRow;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.ValidationErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

  private static final int DEFAULT_PAGE = 1;
  private static final int MAX_SIZE = 100;

  private final ChildProfileRepository childProfileRepository;
  private final ReportQuizSessionQueryRepository reportQuizSessionQueryRepository;

  public ReportService(
      ChildProfileRepository childProfileRepository,
      ReportQuizSessionQueryRepository reportQuizSessionQueryRepository) {
    this.childProfileRepository = childProfileRepository;
    this.reportQuizSessionQueryRepository = reportQuizSessionQueryRepository;
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

  private double roundOneDecimal(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
