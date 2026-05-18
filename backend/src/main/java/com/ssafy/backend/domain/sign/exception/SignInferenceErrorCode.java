package com.ssafy.backend.domain.sign.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SignInferenceErrorCode implements ErrorCode {
  INVALID_FEATURE_SEQUENCE(
      "SIGN_INFERENCE_INVALID_FEATURE_SEQUENCE",
      HttpStatus.BAD_REQUEST,
      "수어 인식 feature sequence 규격이 올바르지 않습니다.",
      "수어 인식 입력 오류"),

  AI_SERVER_UNAVAILABLE(
      "SIGN_INFERENCE_AI_SERVER_UNAVAILABLE",
      HttpStatus.BAD_GATEWAY,
      "수어 인식 AI 서버 호출에 실패했습니다.",
      "수어 인식 AI 서버 오류"),

  AI_SERVER_TIMEOUT(
      "SIGN_INFERENCE_AI_SERVER_TIMEOUT",
      HttpStatus.GATEWAY_TIMEOUT,
      "수어 인식 AI 서버 응답 시간이 초과되었습니다.",
      "수어 인식 AI 서버 시간 초과"),

  AI_SERVER_REJECTED_REQUEST(
      "SIGN_INFERENCE_AI_SERVER_REJECTED_REQUEST",
      HttpStatus.BAD_GATEWAY,
      "수어 인식 AI 서버가 추론 요청을 거부했습니다.",
      "수어 인식 AI 서버 요청 거부"),

  AI_SERVER_INVALID_RESPONSE(
      "SIGN_INFERENCE_AI_SERVER_INVALID_RESPONSE",
      HttpStatus.BAD_GATEWAY,
      "수어 인식 AI 서버 응답 형식이 올바르지 않습니다.",
      "수어 인식 AI 서버 응답 오류");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;
  private final String title;

  SignInferenceErrorCode(String code, HttpStatus httpStatus, String message, String title) {
    this.code = code;
    this.httpStatus = httpStatus;
    this.message = message;
    this.title = title;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  @Override
  public String getTitleOverride() {
    return title;
  }

  @Override
  public String getDomainTitle() {
    return "수어 인식 오류";
  }

  @Override
  public String getMessage() {
    return message;
  }
}
