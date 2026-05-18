package com.ssafy.backend.domain.learn.quiz.controller;

import com.ssafy.backend.domain.learn.quiz.controller.docs.QuizControllerDocs;
import com.ssafy.backend.domain.learn.quiz.dto.request.QuizSessionCreateRequest;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizAnswerResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizCurrentQuestionResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizResultResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizSessionCreateResponse;
import com.ssafy.backend.domain.learn.quiz.dto.response.QuizSessionEndResponse;
import com.ssafy.backend.domain.learn.quiz.service.QuizService;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/learn/quizzes")
public class QuizController implements QuizControllerDocs {

  private final QuizService quizService;

  public QuizController(QuizService quizService) {
    this.quizService = quizService;
  }

  @Override
  @PostMapping("/sessions")
  public QuizSessionCreateResponse createSession(
      Authentication authentication, @Valid @RequestBody QuizSessionCreateRequest request) {
    return quizService.createSession(resolveUserId(authentication), request);
  }

  @Override
  @GetMapping("/sessions/{sessionId}/questions/current")
  public QuizCurrentQuestionResponse getCurrentQuestion(
      Authentication authentication, @PathVariable Long sessionId) {
    return quizService.getCurrentQuestion(resolveUserId(authentication), sessionId);
  }

  @Override
  @PostMapping(
      value = "/sessions/{sessionId}/answers",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public QuizAnswerResponse submitAnswer(
      Authentication authentication,
      @PathVariable Long sessionId,
      @RequestParam Long questionId,
      @RequestPart(value = "audioFile", required = false) MultipartFile audioFile) {
    return quizService.submitAnswer(
        resolveUserId(authentication), sessionId, questionId, audioFile);
  }

  @Override
  @GetMapping("/sessions/{sessionId}/result")
  public QuizResultResponse getResult(Authentication authentication, @PathVariable Long sessionId) {
    return quizService.getResult(resolveUserId(authentication), sessionId);
  }

  @Override
  @PatchMapping("/sessions/{sessionId}")
  public QuizSessionEndResponse endSession(
      Authentication authentication, @PathVariable Long sessionId) {
    return quizService.endSession(resolveUserId(authentication), sessionId);
  }

  private Long resolveUserId(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
    return userId;
  }
}
