package com.ssafy.backend.domain.report.docs;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.quiz.entity.QuizSessionStatus;
import com.ssafy.backend.domain.report.dto.ReportCategoryListResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionDetailResponse;
import com.ssafy.backend.domain.report.dto.ReportQuizSessionListResponse;
import com.ssafy.backend.domain.report.dto.ReportSummaryResponse;
import com.ssafy.backend.domain.report.dto.ReportWeakWordListResponse;
import com.ssafy.backend.global.docs.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "리포트 API", description = "아이별 퀴즈 기록 리포트 API")
public interface ReportApiDocs {

  @Operation(
      summary = "리포트 요약 조회",
      description = "특정 아이의 퀴즈 결과 기반 요약과 최근 취약 단어 TOP 5를 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"VALIDATION_INVALID_INPUT", "COMMON_UNAUTHORIZED", "CHILD_PROFILE_NOT_FOUND"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = ReportSummaryResponse.class)))
  ResponseEntity<ReportSummaryResponse> getSummary(
      @Parameter(hidden = true) Authentication authentication,
      @Parameter(description = "아이 프로필 ID", example = "1") Long childId,
      @Parameter(description = "조회 시작일", example = "2026-05-01") String from,
      @Parameter(description = "조회 종료일", example = "2026-05-31") String to);

  @Operation(
      summary = "카테고리별 리포트 요약 조회",
      description = "특정 아이의 완료된 퀴즈 결과를 기준으로 카테고리별 진행도와 정답률을 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"VALIDATION_INVALID_INPUT", "COMMON_UNAUTHORIZED", "CHILD_PROFILE_NOT_FOUND"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = ReportCategoryListResponse.class)))
  ResponseEntity<ReportCategoryListResponse> getCategories(
      @Parameter(hidden = true) Authentication authentication,
      @Parameter(description = "아이 프로필 ID", example = "1") Long childId,
      @Parameter(description = "조회 시작일", example = "2026-05-01") String from,
      @Parameter(description = "조회 종료일", example = "2026-05-31") String to);

  @Operation(
      summary = "취약 단어 목록 조회",
      description = "특정 아이가 퀴즈에서 자주 틀리거나 낮은 별점을 받은 단어 목록을 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"VALIDATION_INVALID_INPUT", "COMMON_UNAUTHORIZED", "CHILD_PROFILE_NOT_FOUND"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = ReportWeakWordListResponse.class)))
  ResponseEntity<ReportWeakWordListResponse> getWeakWords(
      @Parameter(hidden = true) Authentication authentication,
      @Parameter(description = "아이 프로필 ID", example = "1") Long childId,
      @Parameter(description = "조회 시작일", example = "2026-05-01") String from,
      @Parameter(description = "조회 종료일", example = "2026-05-31") String to,
      @Parameter(description = "카테고리 ID", example = "1") Long categoryId,
      @Parameter(description = "최소 시도 횟수", example = "1") Integer minAttemptCount,
      @Parameter(description = "페이지 번호", example = "1") int page,
      @Parameter(description = "페이지 크기", example = "20") int size);

  @Operation(
      summary = "퀴즈 기록 목록 조회",
      description = "특정 아이의 퀴즈 기록 목록을 최신순으로 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"VALIDATION_INVALID_INPUT", "COMMON_UNAUTHORIZED", "CHILD_PROFILE_NOT_FOUND"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = ReportQuizSessionListResponse.class)))
  ResponseEntity<ReportQuizSessionListResponse> getQuizSessions(
      @Parameter(hidden = true) Authentication authentication,
      @Parameter(description = "아이 프로필 ID", example = "1") Long childId,
      String from,
      String to,
      Long categoryId,
      LearnDifficulty difficulty,
      QuizSessionStatus status,
      int page,
      int size);

  @Operation(
      summary = "퀴즈 기록 상세 조회",
      description = "특정 아이의 특정 퀴즈 기록 상세와 문제별 답변 결과를 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({
    "VALIDATION_INVALID_INPUT",
    "COMMON_UNAUTHORIZED",
    "CHILD_PROFILE_NOT_FOUND",
    "REPORT_NOT_FOUND"
  })
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = ReportQuizSessionDetailResponse.class)))
  ResponseEntity<ReportQuizSessionDetailResponse> getQuizSessionDetail(
      @Parameter(hidden = true) Authentication authentication,
      @Parameter(description = "아이 프로필 ID", example = "1") Long childId,
      @Parameter(description = "퀴즈 세션 ID", example = "10") Long sessionId);
}
