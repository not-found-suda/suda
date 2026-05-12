package com.ssafy.backend.domain.report.controller;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;
import com.ssafy.backend.domain.report.docs.ReportApiDocs;
import com.ssafy.backend.domain.report.dto.ReportCategoryListResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionDetailResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionListResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionSearchCondition;
import com.ssafy.backend.domain.report.dto.ReportSummaryResponse;
import com.ssafy.backend.domain.report.dto.ReportWeakWordListResponse;
import com.ssafy.backend.domain.report.dto.ReportWeakWordSearchCondition;
import com.ssafy.backend.domain.report.service.ReportService;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.CommonErrorCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/children/{childId}/reports")
public class ReportController implements ReportApiDocs {

  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @Override
  @GetMapping("/summary")
  public ResponseEntity<ReportSummaryResponse> getSummary(
      Authentication authentication,
      @PathVariable Long childId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String to) {
    return ResponseEntity.ok(
        reportService.getSummary(resolveUserId(authentication), childId, from, to));
  }

  @Override
  @GetMapping("/categories")
  public ResponseEntity<ReportCategoryListResponse> getCategories(
      Authentication authentication,
      @PathVariable Long childId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String to) {
    return ResponseEntity.ok(
        reportService.getCategories(resolveUserId(authentication), childId, from, to));
  }

  @Override
  @GetMapping("/weak-words")
  public ResponseEntity<ReportWeakWordListResponse> getWeakWords(
      Authentication authentication,
      @PathVariable Long childId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String to,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) Integer minAttemptCount,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    ReportWeakWordSearchCondition condition =
        new ReportWeakWordSearchCondition(from, to, categoryId, minAttemptCount, page, size);
    return ResponseEntity.ok(
        reportService.getWeakWords(resolveUserId(authentication), childId, condition));
  }

  @Override
  @GetMapping("/sessions")
  public ResponseEntity<ReportQuizSessionListResponse> getQuizSessions(
      Authentication authentication,
      @PathVariable Long childId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String to,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) LearnDifficulty difficulty,
      @RequestParam(required = false) QuizSessionStatus status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    ReportQuizSessionSearchCondition condition =
        new ReportQuizSessionSearchCondition(from, to, categoryId, difficulty, status, page, size);
    return ResponseEntity.ok(
        reportService.getQuizSessions(resolveUserId(authentication), childId, condition));
  }

  @Override
  @GetMapping("/sessions/{sessionId}")
  public ResponseEntity<ReportQuizSessionDetailResponse> getQuizSessionDetail(
      Authentication authentication, @PathVariable Long childId, @PathVariable Long sessionId) {
    return ResponseEntity.ok(
        reportService.getQuizSessionDetail(resolveUserId(authentication), childId, sessionId));
  }

  private Long resolveUserId(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
    return userId;
  }
}
