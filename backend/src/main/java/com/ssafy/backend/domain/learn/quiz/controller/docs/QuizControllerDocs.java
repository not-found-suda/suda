package com.ssafy.backend.domain.learn.quiz.controller.docs;

import com.ssafy.backend.domain.learn.quiz.dto.request.QuizSessionCreateRequest;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizAnswerResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizCurrentQuestionResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizResultResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizSessionCreateResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizSessionEndResponse;
import com.ssafy.backend.global.docs.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Learn Quiz", description = "학습 퀴즈 API")
public interface QuizControllerDocs {

  @Operation(
      summary = "퀴즈 세션 생성",
      description =
          """
사용자가 선택한 카테고리와 난이도 기준으로 퀴즈 세션을 생성합니다.
생성 시 해당 조건의 단어를 랜덤으로 뽑아 퀴즈 문제 목록을 만듭니다.
응답에는 모바일이 세션 진입 즉시 전체 문제를 렌더링할 수 있도록 questions 목록이 포함됩니다.
""",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiResponse(responseCode = "200", description = "퀴즈 세션 생성 성공")
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "CHILD_PROFILE_NOT_FOUND", "LEARN_QUIZ_NOT_ENOUGH_WORDS"})
  QuizSessionCreateResponse createSession(
      @Parameter(hidden = true) Authentication authentication,
      @Valid @RequestBody QuizSessionCreateRequest request);

  @Operation(
      summary = "현재 퀴즈 문제 조회",
      description =
          """
진행 중인 퀴즈 세션에서 아직 답변하지 않은 현재 문제를 조회합니다.
응답에는 모바일 문제 표시와 답변 제출 기준 일관성을 위한 targetText가 포함됩니다.
""",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiResponse(responseCode = "200", description = "현재 문제 조회 성공")
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "QUIZ_SESSION_NOT_FOUND"})
  QuizCurrentQuestionResponse getCurrentQuestion(
      @Parameter(hidden = true) Authentication authentication,
      @Parameter(description = "퀴즈 세션 ID", example = "10") @PathVariable Long sessionId);

  @Operation(
      summary = "퀴즈 답변 제출",
      description =
          """
아이가 녹음한 음성 파일을 제출합니다.
백엔드에서 STT를 통해 음성을 텍스트로 변환하고,
정답 단어와 유사도를 비교하여 별점과 피드백을 반환합니다.

audioFile이 없거나, 음성 인식에 실패하거나, 인식 결과가 비어 있는 경우에도 오류로 막지 않고
recognizedText를 빈 문자열로 처리하여 1점 답변으로 저장합니다.
따라서 모바일은 실패 화면을 보여준 뒤 다음 문제로 진행할 수 있습니다.
""",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiResponse(responseCode = "200", description = "답변 제출 및 채점 성공")
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "QUIZ_SESSION_NOT_FOUND", "QUIZ_NOT_CURRENT_QUESTION"})
  QuizAnswerResponse submitAnswer(
      @Parameter(hidden = true) Authentication authentication,
      @Parameter(description = "퀴즈 세션 ID", example = "10") @PathVariable Long sessionId,
      @Parameter(description = "현재 문제 ID", example = "101") @RequestParam Long questionId,
      @Parameter(description = "아이 음성 파일. 무음/미전송 가능", content = @Content(mediaType = "audio/webm"))
          @RequestPart(value = "audioFile", required = false)
          MultipartFile audioFile,
      @Parameter(description = "STT 없이 저장할 인식 텍스트. 무음 오답 처리 시 빈 문자열 전달")
          @RequestPart(value = "recognizedText", required = false)
          String recognizedText);

  @Operation(
      summary = "퀴즈 결과 조회",
      description = "퀴즈 세션의 전체 결과와 문제별 답변 내역을 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiResponse(responseCode = "200", description = "퀴즈 결과 조회 성공")
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "QUIZ_SESSION_NOT_FOUND", "LEARN_QUIZ_NOT_COMPLETED"})
  QuizResultResponse getResult(
      @Parameter(hidden = true) Authentication authentication,
      @Parameter(description = "퀴즈 세션 ID", example = "10") @PathVariable Long sessionId);

  @Operation(
      summary = "퀴즈 세션 종료",
      description =
          """
퀴즈 세션을 종료 상태로 변경합니다.
모든 문제를 풀면 답변 제출 시 자동 종료되지만,
사용자가 중간에 종료하는 경우에도 사용할 수 있습니다.
""",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiResponse(responseCode = "200", description = "퀴즈 세션 종료 성공")
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "QUIZ_SESSION_NOT_FOUND"})
  QuizSessionEndResponse endSession(
      @Parameter(hidden = true) Authentication authentication,
      @Parameter(description = "퀴즈 세션 ID", example = "10") @PathVariable Long sessionId);
}
