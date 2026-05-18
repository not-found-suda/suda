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
      "수어 인식 AI 서버 오류");

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
